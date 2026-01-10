package com.schoolable.backend.config;

import com.schoolable.backend.profile.Profile;
import com.schoolable.backend.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Configuration
public class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    @Bean
    CommandLineRunner seedSuperAdmin(ProfileRepository profileRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String email = "schoolableadmin@gmail.com";
            String password = "schoolableadmin123";

            profileRepository.findByEmailIgnoreCase(email).ifPresentOrElse(
                    existing -> {
                        boolean needsUpdate = false;
                        if (existing.getPasswordHash() == null) {
                            existing.setPasswordHash(passwordEncoder.encode(password));
                            needsUpdate = true;
                        }
                        // Ensure admin has job_title set
                        if (existing.getJobTitle() == null || existing.getJobTitle().isEmpty()) {
                            existing.setJobTitle("System Administrator");
                            needsUpdate = true;
                        }
                        if (needsUpdate) {
                            existing.setUpdatedAt(OffsetDateTime.now());
                            profileRepository.save(existing);
                            log.info("Admin user updated with job_title");
                        }
                    },
                    () -> {
                        Profile admin = new Profile();
                        admin.setId(UUID.randomUUID());
                        admin.setEmail(email);
                        admin.setFullName("Schoolable Admin");
                        admin.setRole("admin");
                        admin.setJobTitle("System Administrator");
                        admin.setStatus("active");
                        admin.setEmailVerifiedAt(OffsetDateTime.now());
                        admin.setProfileCompletedAt(OffsetDateTime.now());
                        admin.setPasswordHash(passwordEncoder.encode(password));
                        admin.setCreatedAt(OffsetDateTime.now());
                        admin.setUpdatedAt(OffsetDateTime.now());
                        profileRepository.save(admin);
                        log.info("Admin user created with job_title");
                    }
            );
        };
    }
}
