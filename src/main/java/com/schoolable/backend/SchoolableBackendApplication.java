package com.schoolable.backend;

import com.schoolable.backend.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class SchoolableBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolableBackendApplication.class, args);
    }
}
