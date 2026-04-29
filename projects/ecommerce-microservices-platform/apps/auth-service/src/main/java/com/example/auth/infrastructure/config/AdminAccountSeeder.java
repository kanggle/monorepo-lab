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
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 개발·단독 실행 환경 전용 관리자 계정 씨더.
 *
 * <p>활성 프로파일: {@code local}, {@code standalone} 에서만 Bean이 등록된다.
 * prod 환경에서는 이 Bean이 생성되지 않는다.
 *
 * <p><b>prod 최초 관리자 계정 생성 절차</b><br>
 * DB에 직접 INSERT하거나, 별도 bootstrap 스크립트를 실행한다.
 * 예: {@code INSERT INTO users (id, email, password, name, role, ...) VALUES (...)}
 * 비밀번호는 BCrypt로 인코딩한 뒤 저장할 것. 기존 prod DB에 {@code admin@admin.com}이
 * 이미 존재하는 경우, 운영 절차에 따라 비밀번호를 반드시 변경하라.
 */
@Slf4j
@Configuration
@Profile({"local", "standalone"})
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
