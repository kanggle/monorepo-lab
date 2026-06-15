package com.example.user.infrastructure.config;

import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.service.ProductInfoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;
import java.util.UUID;

/**
 * Standalone 프로파일용 설정.
 * Docker 없이 user-service를 로컬에서 실행할 때 사용한다.
 * Kafka / product-service 의존성이 없으며, 테스트 사용자 프로파일을 자동 생성한다.
 */
@Slf4j
@Configuration
@Profile("standalone")
public class StandaloneConfig {

    @Bean
    ProductInfoProvider noOpProductInfoProvider() {
        return productIds -> {
            log.debug("[standalone] ProductInfoProvider called with {} product IDs, returning empty map", productIds.size());
            return Map.of();
        };
    }

    @Bean
    CommandLineRunner standaloneDataInitializer(UserProfileRepository userProfileRepository) {
        return args -> {
            UUID testUserId = UUID.fromString("56431938-da42-42e6-bb85-98bfaaebfb94");
            String testEmail = "test@example.com";
            String testName = "테스트유저";

            if (userProfileRepository.existsByUserId(testUserId)) {
                log.info("[standalone] Test user profile already exists: userId={}", testUserId);
                return;
            }

            // Standalone seeds a full profile (with email/name) for local read/update
            // testing. The live onboarding path creates a minimal profile from
            // account.created (ADR-MONO-037 P1); this dev seed deliberately uses the
            // full-profile factory so the standalone UI has data to render.
            log.info("[standalone] Creating test user profile: userId={}, email={}", testUserId, testEmail);
            userProfileRepository.save(UserProfile.create(testUserId, testEmail, testName));
            log.info("[standalone] Test user profile created successfully");
        };
    }
}
