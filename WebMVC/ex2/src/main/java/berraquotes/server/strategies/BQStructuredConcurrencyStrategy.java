package berraquotes.server.strategies;

import berraquotes.common.Quote;
import berraquotes.utils.FutureUtils;
import jdk.incubator.concurrent.StructuredTaskScope;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

/**
 * This strategy uses the Java structured concurrency framework to
 * provide Berra quotes.
 */
public class BQStructuredConcurrencyStrategy
       extends BQAbstractStrategy {
    /**
     * Get a {@link List} that contains the requested quotes.
     *
     * @param quotes The {@link List} of {@link Quote} objects
     * @param quoteIds A {@link List} containing the given random
     *                 {@code quoteIds}
     * @return A {@link List} of all requested {@link Quote} objects
     */
    public List<Quote> getQuotes(List<Quote> quotes,
                                 List<Integer> quoteIds) {
        try (var scope =
             // Create a new StructuredTaskScope that shuts down on
             // failure.
             new StructuredTaskScope.ShutdownOnFailure()) {
            // Get a List of Futures to Quote objects that are being
            // processed in parallel.
            var results = quoteIds
                // Convert the List to a Stream.
                .stream()

                // Asynchronously determine if the quote matches any of
                // the search queries.
                .map(quoteId ->
                     scope.fork(() -> quotes
                                // Get the quote associated with the
                                // quoteId.
                                .get(quoteId)))

                // Convert the Stream to a List.
                .toList();

            // Perform a barrier synchronization that waits for all
            // the tasks to complete.
            scope.join();

            // Throw an Exception upon failure of any tasks.
            scope.throwIfFailed();

            // Return a List of Quote objects.
            return FutureUtils
                // Convert the List of Future<Quote> objects to
                // a Stream of Quote objects.
                .futures2Stream(results)
                
                // Convert the Stream to a List.
                .toList();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Search for quotes containing the given {@link String} {@code
     * query} and return a {@link List} of matching {@link Quote}
     * objects.
     *
     * @param quotes The {@link List} of {@link Quote} objects
     * @param query The search query
     * @return A {@code List} of quotes containing {@link Quote}
     *         objects matching the given {@code query}
     */
    @Override
    public List<Quote> search(List<Quote> quotes,
                              String query) {
        return search(quotes, List.of(query));
    }

    /**
     * Search for quotes containing the given {@link String} {@code
     * queries} and return a {@link List} of matching {@link Quote}
     * objects.
     *
     * @param quotes The {@link List} of {@link Quote} objects
     * @param queries The search queries
     * @return A {@code List} of quotes containing {@link Quote}
     *         objects matching the given {@code queries}
     */
    public List<Quote> search(List<Quote> quotes,
                              List<String> queries) {
        // Use Java structured concurrency to locate all quotes whose
        // 'quote' field matches the List of 'queries' and return them
        // as a List of Quote objects.
        try (var scope =
             // Create a new StructuredTaskScope that shuts down on
             // failure.
             new StructuredTaskScope.ShutdownOnFailure()) {

            // Get a List of Futures to Optional<Quote> objects that
            // are being processed in parallel.
            var results =
                getFutureList(quotes,
                              queries,
                              scope);

            // Perform a barrier synchronization that waits for all
            // the tasks to complete.
            scope.join();

            // Throw an Exception upon failure of any tasks.
            scope.throwIfFailed();

            // Return a List of Quote objects.
            return getQuoteList(results);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Asynchronously determine which {@link Quote} {@link String}
     * match any of the {@code queries}.
     *
     * @param quotes The {@link List} of {@link Quote} objects
     * @param queries The queries to search for
     * @param scope The {@link StructuredTaskScope} used to {@code
     *              fork()} a virtual thread
     * @return A {@link List} of {@link Future} objects containing an
     *         {@link Optional} with a {@link Quote} if there's a
     *         match or an empty {@link Optional} if there's no match
     */
    @NotNull
    private List<Future<Optional<Quote>>> getFutureList
        (List<Quote> quotes,
         List<String> queries,
         StructuredTaskScope.ShutdownOnFailure scope) {
        return quotes
            // Convert the List to a Stream.
            .stream()

            // Asynchronously determine if the quote matches any of
            // the search queries.
            .map(quote ->
                 findMatchAsync(quote, queries, scope))

            // Convert the Stream to a List.
            .toList();
    }

    /**
     * Get a {@link List} of {@link Quote} objects that matched at
     * least one query.
     *
     * @param results A {@link List} of {@link Future} objects
     *                containing an {@link Optional} with a {@link
     *                Quote} if there's a match or an empty {@link
     *                Optional} if there's no match
     * @return A {@link List} of {@link Quote} objects
     */
    @NotNull
    private List<Quote> getQuoteList
        (List<Future<Optional<Quote>>> results) {
        return FutureUtils
            // Convert the List of Future<Optional<Quote>> objects to
            // a Stream of Optional<Quote> objects.
            .futures2Stream(results)

            // Eliminate empty Optional objects.
            .flatMap(Optional::stream)

            // Convert the Stream to a List.
            .toList();
    }

    /**
     * Asynchronously determine if the {@code quote} matches with the
     * {@link List} of {@code queries}.
     *
     * @param quote The {@link Quote} to match against
     * @param queries The search queries
     * @param scope The {@link StructuredTaskScope} used to {@code
     *              fork()} a virtual thread
     * @return A {@link Future} to an {@link Optional} containing a
     *         {@link Quote} if there's a match or an empty {@link
     *         Optional} if there's no match
     */
    private Future<Optional<Quote>> findMatchAsync(Quote quote,
                                                   List<String> queries,
                                                   StructuredTaskScope.ShutdownOnFailure scope) {
        return scope
            // Create a virtual thread.
            .fork(() -> Optional
                  // Create an empty Optional if there's no match,
                  // else the Optional contains the movie that
                  // matches.
                  .ofNullable(queries
                              // Convert the List to a Stream.
                              .stream()
                              // Determine if there's any match.
                              .anyMatch(query ->
                                        findMatch(query,
                                                  quote.quote()))
                              ? quote
                              : null));

    }

    /**
     * Find a match between the {@code query} and the {@code quote}.
     *
     * @param query The query 
     * @param quote The quote to match with
     * @return True if there's a match, else false
     */
    private boolean findMatch(String query, String quote) {
        return quote.toLowerCase().contains(query.toLowerCase());
    }
}