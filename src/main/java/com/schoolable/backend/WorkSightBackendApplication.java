package com.schoolable.backend;

import com.schoolable.backend.auth.JwtProperties;
import com.schoolable.backend.config.RequiredEnvironmentValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
@EnableScheduling
public class WorkSightBackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WorkSightBackendApplication.class);
        application.addInitializers(new RequiredEnvironmentValidator());
        application.run(args);
    }
}
