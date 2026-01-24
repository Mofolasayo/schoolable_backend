package com.schoolable.backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public class RequiredEnvironmentValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String[] REQUIRED_KEYS = {
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "JWT_SECRET"
    };

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        List<String> missing = new ArrayList<>();

        for (String key : REQUIRED_KEYS) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variables: " + String.join(", ", missing));
        }
    }
}
