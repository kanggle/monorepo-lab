package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.Account;
import com.example.account.domain.tenant.TenantId;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("ProfileJpaRepository 쿼리 슬라이스 테스트")
class ProfileJpaRepositoryTest {

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
    private ProfileJpaRepository profileRepo;

    @Autowired
    private AccountJpaRepository accountRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.update("DELETE FROM profiles");
        jdbc.update("DELETE FROM accounts");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    // ── findByAccountId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAccountId — 프로필이 있는 계정 → 반환")
    void findByAccountId_existing_returnsProfile() {
        String accountId = saveAccount("profile-a@example.com");
        insertProfile(accountId, "테스트 유저");

        Optional<ProfileJpaEntity> result = profileRepo.findByAccountId(accountId);

        assertThat(result).isPresent();
        assertThat(result.get().getAccountId()).isEqualTo(accountId);
        assertThat(result.get().getDisplayName()).isEqualTo("테스트 유저");
    }

    @Test
    @DisplayName("findByAccountId — 프로필 없는 계정 → empty")
    void findByAccountId_noProfile_returnsEmpty() {
        assertThat(profileRepo.findByAccountId(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    @DisplayName("findByAccountId — 다른 계정의 프로필은 반환하지 않음")
    void findByAccountId_differentAccountId_returnsEmpty() {
        String accountA = saveAccount("profile-b@example.com");
        String accountB = saveAccount("profile-c@example.com");
        insertProfile(accountA, "A 유저");

        assertThat(profileRepo.findByAccountId(accountB)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String saveAccount(String email) {
        Account account = Account.create(TenantId.FAN_PLATFORM, email);
        AccountJpaEntity entity = AccountJpaEntity.fromDomain(account);
        return accountRepo.saveAndFlush(entity).getId();
    }

    private void insertProfile(String accountId, String displayName) {
        jdbc.update(
                "INSERT INTO profiles (tenant_id, account_id, display_name, locale, timezone, updated_at)" +
                " VALUES ('fan-platform', ?, ?, 'ko', 'Asia/Seoul', NOW(6))",
                accountId, displayName);
    }
}
