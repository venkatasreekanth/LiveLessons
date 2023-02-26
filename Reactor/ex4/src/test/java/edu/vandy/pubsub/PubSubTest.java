package edu.vandy.pubsub;

import edu.vandy.pubsub.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import edu.vandy.pubsub.publisher.PublisherApplication;
import edu.vandy.pubsub.subscriber.HybridBackpressureSubscriber;
import edu.vandy.pubsub.subscriber.PublisherProxy;
import edu.vandy.pubsub.utils.Memoizer;
import edu.vandy.pubsub.utils.ReactorUtils;
import edu.vandy.pubsub.utils.RunTimer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.core.Disposables;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import edu.vandy.pubsub.common.Options;
import edu.vandy.pubsub.utils.PrimeUtils;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

/**
 * This program applies WebFlux and Project Reactor features to
 * implement various types of backpressure strategies (e.g., ignore,
 * buffer, error, latest, drop, and push/pull) between a publisher
 * that runs as a microservice in one process and produces a flux
 * stream of random integers and a subscriber that runs in one or more
 * threads in a different process and consumes this stream of
 * integers.  This program also measures the performance of checking
 * these random numbers for primality with and without various types
 * of memoizers (e.g., untimed and timed) based on Java
 * ConcurrentHashMap.  In addition, it demonstrates the use of slicing
 * with the Flux takeWhile() and skipWhile() operations.
 *
 * The {@code @SpringBootTest} annotation tells Spring to look for a
 * main configuration class (a {@code @SpringBootApplication}, e.g.,
 * {@code ZippyApplication}) and use that to start a Spring
 * application context to serve as the target of the tests.
 *
 * The {@code @SpringBootConfiguration} annotation indicates that a
 * class provides a Spring Boot application {@code @Configuration}.
 */
@SuppressWarnings("ALL")
@SpringBootConfiguration
@SpringBootTest(classes = PublisherApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class PubSubTest {
    /**
     * Debugging tag used by the logger.
     */
    private final String TAG =
        getClass().getSimpleName();

    /**
     * The scheduler used to locally publish random integers received
     * from the Publisher microservice.
     */
    private Scheduler mPublisherScheduler;

    /**
     * The scheduler used to consume random integers by checking if
     * they are prime or not.
     */
    private Scheduler mSubscriberScheduler;

    /**
     * A subscriber that applies hybrid push/pull backpressure.
     */
    private HybridBackpressureSubscriber mSubscriber;

    /**
     * Track all disposables to dispose them all at once.
     */
    private Disposable.Composite mDisposables;

    /**
     * Automatically inject a proxy to the {@link Publisher}.
     */
    @Autowired
    PublisherProxy mPublisherProxy;

    /**
     * Emulate the "command-line" arguments for the tests.
     */
    private final String[] mArgv = new String[]{
            "-d",
            "true", // Enable/disable debugging messages.
            // "-T",
            // "PubSubTest,HybridBackpressureSubscriber",
            "-c",
            "50" // Generate and test 50 random large Integer objects.
    };

    /**
     * The main entry point into the Spring applicaition.
     */
    @Test
    public void runTests() {
        // Run the Spring application.
        System.out.println("Entering PubSubDriver main()");

        // Process any command-line arguments.
        Options.instance().parseArgs(mArgv);

        // Run the publisher in a single thread.
        mPublisherScheduler = Schedulers
            .newParallel("publisher", 1);

        // Maybe run the subscriber in a different single thread.
        mSubscriberScheduler = Options.instance().parallel()
            // Choose a different scheduler if we're running in parallel.
            ? Schedulers.newParallel("subscriber",
                                     Options.instance().parallelism())

            // Run everything in the publisher scheduler.
            : mPublisherScheduler;

        // This subscriber implements hybrid push/pull backpressure.
        mSubscriber = new HybridBackpressureSubscriber();

        // Track all disposables to dispose them all at once.
        mDisposables = Disposables
            .composite(mPublisherScheduler,
                       mSubscriberScheduler,
                       mSubscriber);

        // Run the tests and print the results.
        run();

        System.out.println("Leaving PubSubDriver run()");
    }

    /**
     * Run the tests and print the results.
     */
    private void run() {
        Memoizer<Integer, Integer> memoizer =
            Options.makeMemoizer(PrimeUtils::isPrime);

        // Create and time prime checking with a memoizer.
        timeTest(memoizer,
                 "test with memoizer");

        // Get a copy of the memoizer's map.
        Map<Integer, Integer> memoizerCopy = memoizer.getCache();

        // Shutdown the memoizer.
        memoizer.shutdown();

        // Create and time prime checking without a memoizer.
        timeTest(PrimeUtils::isPrime,
                 "test without memoizer");

        // Dispose of all schedulers and subscribers.
        mDisposables.dispose();

        // Print the results.
        Options.print(RunTimer.getTimingResults());

        // Demonstrate slicing on the concurrent hash map.
        demonstrateSlicing(memoizerCopy);
    }

    /**
     * Time {@code testName} using the given {@code hashMap}.
     *
     * @param primeChecker The prime checker used evaluate prime candidates.
     * @param testName The name of the test.
     */
    private void timeTest(Function<Integer, Integer> primeChecker,
                          String testName) {
        RunTimer
            // Time how long this test takes to run.
            .timeRun(() ->
                     // Run the test using the given prime checker.
                     runTest(primeChecker, testName),
                     testName);
    }

    /**
     * Run the prime number test.
     * 
     * @param primeChecker A function that maps candidate primes to their
     * smallest factor (if they aren't prime) or 0 if they are prime
     * @param testName Name of the test
     * @return The prime checker (which may be updated during the test).
     */
    private Function<Integer, Integer> runTest
        (Function<Integer, Integer> primeChecker,
         String testName) {
        Options.print("Starting "
                      + testName
                      + " with count = "
                      + Options.instance().count());

        // Reset the counters.
        PrimeUtils.sPrimeCheckCounter.set(0);

        // Create a remote publisher that runs on its own scheduler.
        Flux<Integer> publisher = mPublisherProxy
            .start(Options.instance().count(),
                    Options.instance().maxValue(),
                    Options.instance().backPressureEnabled());

        // This Function determines if a random # is prime or not.
        Function<Integer, Flux<Result>> determinePrimality = number -> Flux
            .just(number)

            // Subscriber may run in different thread(s).
            .publishOn(mSubscriberScheduler)

            // Check if the # is prime.
            .map(__ -> PrimeUtils
                 .checkIfPrime(number, primeChecker));

        // Run the main flux pipeline.
        publisher
            // Enable transformation at instantiation time.
            .transform(ReactorUtils
                       // Conditionally enable logging.
                       .logIf(Options.instance().loggingEnabled()))

            // Use the flatMap() idiom to concurrently (maybe) check
            // each random # to see if it's prime.
            .flatMap(determinePrimality)

            // The subscriber starts all the wheels in motion!
            .subscribe(mSubscriber);

        Options.debug(TAG, "waiting in the main thread");

        // Wait for all processing to complete.
        mSubscriber.await();

        // Cleverly print out the results.
        Options.print(makeExitString(testName, primeChecker));

        // Stop publishing.
        mPublisherProxy.stop();

        // Return prime checker (which may update during the test).
        return primeChecker;
    }

    /**
     * Demonstrate how to slice by applying the Project Reactor Flux
     * {@code skipWhile()} and {@code takeWhile()} operations to the
     * {@code map} parameter.
     */
    private void demonstrateSlicing(Map<Integer, Integer> map) {
        // Sort the map by its values.
        Map<Integer, Integer> sortedMap = sortMap(map, comparingByValue());

        // Print out the entire contents of the sorted map.
        Options.print("map with "
                      + sortedMap.size()
                      + " elements sorted by value = \n" + sortedMap);

        // Print out the prime #'s using takeWhile().
        PrimeUtils.printPrimes(sortedMap);

        // Print out the non-prime #'s using skipWhile().
        PrimeUtils.printNonPrimes(sortedMap);
    }

    /**
     * Sort {@code map} via the {@code comparator} and {@code LinkedHashMap}
     * @param map The map to sort
     * @param comparator The comparator to compare map entries.
     * @return The sorted map
     */
    private Map<Integer, Integer> sortMap
        (Map<Integer, Integer> map,
         Comparator<Map.Entry<Integer, Integer>> comparator) {
        // Create a map that's sorted by the value in map.
        return Flux
            // Convert EntrySet of the map into a flux stream.
            .fromIterable(map.entrySet())

            // Sort the elements in the stream using the comparator.
            .sort(comparator)

            // Trigger intermediate processing and collect key/value
            // pairs in the stream into a LinkedHashMap, which
            // preserves the sorted order.
            .collect(toMap(Map.Entry::getKey,
                           Map.Entry::getValue,
                           (e1, e2) -> e2,
                           LinkedHashMap::new))

            // Block until processing is done.
            .block();
    }

    /**
     * Create and return the proper exit string based on various conditions.
     * @return The proper exit string.
     */
    private String makeExitString(String testName,
                                  Function<Integer, Integer> primeChecker) {
        String prefix = "Leaving "
                + testName
                + " with "
                + PrimeUtils.sPrimeCheckCounter.get()
                + " prime checks ";

        if (Options.instance().overflowStrategy() == FluxSink.OverflowStrategy.DROP
                || Options.instance().overflowStrategy() == FluxSink.OverflowStrategy.LATEST)
            return prefix;
        else if (primeChecker instanceof Memoizer)
            return prefix
                    + "(" + (Options.instance().count()
                             - PrimeUtils.sPrimeCheckCounter.get())
                    + " duplicates)";
        else
            return prefix;
    }
}
    