package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Implements a custom collector that converts a stream of {@link
 * CompletableFuture} objects into a single {@link CompletableFuture}
 * that is triggered when all {@link CompletableFuture} objects in the
 * stream complete.
 *
 * See
 * <a href="http://www.nurkiewicz.com/2013/05/java-8-completablefuture-in-action.html">this link</a>
 * for more info.
 */
public class FuturesCollector<T>
      implements Collector<CompletableFuture<T>,
                           List<CompletableFuture<T>>,
                           CompletableFuture<List<T>>> {
    /**
     * A method that creates and returns a new mutable result
     * container that will hold all the {@link CompletableFuture}
     * objects in the stream.
     *
     * @return A {@link Supplier} that returns a new, mutable result
     *         container
     */
    @Override
    public Supplier<List<CompletableFuture<T>>> supplier() {
        return ArrayList::new;
    }

    /**
     * A method that folds a {@link CompletableFuture} into the
     * mutable result container.
     *
     * @return A {@link BiConsumer} that folds a value into a mutable
     *         result container
     */
    @Override
    public BiConsumer<List<CompletableFuture<T>>,
                      CompletableFuture<T>> accumulator() {
        return List::add;
    }

    /**
     * A method that accepts two partial results and merges them.  The
     * {@code combiner()} may fold state from one argument into the
     * other and return that, or may return a new result container.
     *
     * @return A {@link BinaryOperator} that combines two partial
     *         results into a combined result
     */
    @Override
    public BinaryOperator<List<CompletableFuture<T>>> combiner() {
        return (List<CompletableFuture<T>> one,
                List<CompletableFuture<T>> another) -> {
            one.addAll(another);
            return one;
        };
    }

    /**
     * Perform the final transformation from the intermediate
     * accumulation type {@code A} to the final result type {@code R}.
     *
     * @return a {@link Function} that transforms the intermediate
     *         result to the final result
     */
    @Override
    public Function<List<CompletableFuture<T>>,
                    CompletableFuture<List<T>>> finisher() {
        return futures
            -> CompletableFuture
            // Use CompletableFuture.allOf() to obtain a
            // CompletableFuture that will itself complete when all
            // CompletableFutures in futures have completed.
            .allOf(futures.toArray(new CompletableFuture[0]))

            // When all futures have completed get a CompletableFuture
            // to an array of joined elements of type T.
            .thenApply(v -> futures
                       // Convert futures into a stream of completable
                       // futures.
                       .stream()

                       // Use map() to join() all completablefutures
                       // and yield objects of type T.  Note that
                       // join() should never block.
                       .map(CompletableFuture::join)

                       // Collect to a List.
                       .toList());
    }

    /**
     * Returns a {@code Set} of {@code Collector.Characteristics}
     * indicating the characteristics of this Collector.  This set
     * should be immutable.
     *
     * @return An {@link Set} of collector characteristics, which in
     *         this case is simply UNORDERED
     */
    @Override
    public Set<Characteristics> characteristics() {
        return Collections.singleton(Characteristics.UNORDERED);
    }

    /**
     * This static factory method creates a new {@link
     * FuturesCollector}.
     *
     * @return A new {@link FuturesCollector}
     */
    public static <T> Collector<CompletableFuture<T>, ?, CompletableFuture<List<T>>>
        toFuture() {
        return new FuturesCollector<>();
    }
}
