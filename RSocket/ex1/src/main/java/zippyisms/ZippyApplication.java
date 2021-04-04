package zippyisms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

/**
 * Provides access to the Zippy th' Pinhead microservice.
 */
@SpringBootApplication
public class ZippyApplication {
    public static void main(String[] args) {
        // Disable the verbose/annoying Spring "debug" logging.
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
            LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.toLevel("error"));

        SpringApplication.run(ZippyApplication.class, args);
    }

}