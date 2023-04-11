package primechecker.common;

/**
 * Static class used to centralize all constants used by this project.
 */
public class Constants {
    public static final String SERVER_BASE_URL = "http://localhost:8081";

    /**
     * All supported HTTP request endpoints.
     */
    public static class EndPoint {
        public static final String CHECK_IF_PRIME = "/checkIfPrime";
        public static final String CHECK_IF_PRIME_FLUX = "/checkIfPrimeFlux";
    }

    public static class Strategies {
        public static final int CONCURRENT_FLUX = 0;
        public static final int PARALLEL_FLUX = 1;
    }
}
