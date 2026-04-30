package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.TenantStatus;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("TenantJpaRepository 슬라이스 테스트")
class TenantJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("account_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TenantJpaRepository tenantJpaRepository;

    // ── V0009 seed row 검증 ──────────────────────────────────────────────────

    @Test
    @DisplayName("V0009 마이그레이션으로 fan-platform 시드 행이 존재한다")
    void fanPlatformSeedRow_existsAfterMigration() {
        Optional<TenantJpaEntity> tenant = tenantJpaRepository.findById("fan-platform");

        assertThat(tenant).isPresent();
        assertThat(tenant.get().getTenantId()).isEqualTo("fan-platform");
        assertThat(tenant.get().getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    // ── existsByTenantIdAndStatus ────────────────────────────────────────────

    @Test
    @DisplayName("existsByTenantIdAndStatus — fan-platform ACTIVE → true")
    void existsByTenantIdAndStatus_fanPlatformActive_returnsTrue() {
        assertThat(tenantJpaRepository.existsByTenantIdAndStatus("fan-platform", TenantStatus.ACTIVE))
                .isTrue();
    }

    @Test
    @DisplayName("existsByTenantIdAndStatus — 존재하지 않는 테넌트 → false")
    void existsByTenantIdAndStatus_nonExistent_returnsFalse() {
        assertThat(tenantJpaRepository.existsByTenantIdAndStatus("nonexistent", TenantStatus.ACTIVE))
                .isFalse();
    }

    @Test
    @DisplayName("existsByTenantIdAndStatus — fan-platform SUSPENDED → false (상태 불일치)")
    void existsByTenantIdAndStatus_fanPlatformSuspended_returnsFalse() {
        // fan-platform seed row is ACTIVE; querying with SUSPENDED must return false
        assertThat(tenantJpaRepository.existsByTenantIdAndStatus("fan-platform", TenantStatus.SUSPENDED))
                .isFalse();
    }
}
