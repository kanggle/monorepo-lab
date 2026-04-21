package com.example.auth.infrastructure.config;

import com.example.auth.domain.entity.Role;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.service.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminAccountSeeder {

    private static final String ADMIN_EMAIL = "admin@admin.com";
    private static final String ADMIN_PASSWORD = "admin1234";
    private static final String ADMIN_NAME = "Administrator";

    @Bean
    ApplicationRunner seedAdminAccount(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> seed(userRepository, passwordEncoder);
    }

    @Transactional
    void seed(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("[seed] admin account already exists: {}", ADMIN_EMAIL);
            return;
        }
        Instant now = Instant.now();
        User admin = User.reconstitute(
                UUID.randomUUID(),
                ADMIN_EMAIL,
                passwordEncoder.encode(ADMIN_PASSWORD),
                ADMIN_NAME,
                Role.ADMIN,
                null,
                now,
                now,
                true
        );
        userRepository.save(admin);
        log.info("[seed] admin account created: {}", ADMIN_EMAIL);
    }
}
