package com.schoolable.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ObservabilityStartupLogger implements ApplicationRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(ObservabilityStartupLogger.class);

    private final Environment environment;

    public ObservabilityStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String sentryDsn = environment.getProperty("SENTRY_DSN");
        if (sentryDsn == null || sentryDsn.isBlank()) {
            logger.warn("SENTRY_DSN is not set; error tracing is disabled.");
        } else {
            logger.info("Sentry DSN detected; error tracing is enabled.");
        }
    }
}
