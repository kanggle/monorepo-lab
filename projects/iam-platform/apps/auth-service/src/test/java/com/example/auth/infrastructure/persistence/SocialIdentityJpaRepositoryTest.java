package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.social.SocialIdentity;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("SocialIdentityJpaRepository 쿼리 슬라이스 테스트")
class SocialIdentityJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("auth_db")
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
    private SocialIdentityJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── findByTenantIdAndProviderAndProviderUserId (TASK-BE-611) ──────────────

    @Test
    @DisplayName("findByTenantIdAndProviderAndProviderUserId — 같은 테넌트의 일치하는 소셜 계정 반환")
    void findByTenant_existing_returnsEntity() {
        String accountId = uuid();
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(accountId, "fan-platform", "google", "google-uid-123", "user@gmail.com")));

        Optional<SocialIdentityJpaEntity> result =
                repo.findByTenantIdAndProviderAndProviderUserId("fan-platform", "google", "google-uid-123");

        assertThat(result).isPresent();
        assertThat(result.get().getAccountId()).isEqualTo(accountId);
        assertThat(result.get().getProvider()).isEqualTo("google");
    }

    @Test
    @DisplayName("findByTenantIdAndProviderAndProviderUserId — 없는 조합 → empty")
    void findByTenant_notFound_returnsEmpty() {
        assertThat(repo.findByTenantIdAndProviderAndProviderUserId("fan-platform", "google", "unknown-uid"))
                .isEmpty();
    }

    @Test
    @DisplayName("findByTenantIdAndProviderAndProviderUserId — provider가 같아도 providerUserId가 다르면 제외")
    void findByTenant_sameProviderDifferentUserId_returnsEmpty() {
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(uuid(), "fan-platform", "google", "uid-a", null)));

        assertThat(repo.findByTenantIdAndProviderAndProviderUserId("fan-platform", "google", "uid-b")).isEmpty();
    }

    @Test
    @DisplayName("TASK-BE-611: 다른 테넌트의 같은 신원은 보이지 않는다 — ecommerce 행만 있으면 fan-platform 조회는 empty")
    void findByTenant_identityOnlyInAnotherTenant_returnsEmpty() {
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(uuid(), "ecommerce", "kakao", "60218261625", "k@kakao.com")));

        assertThat(repo.findByTenantIdAndProviderAndProviderUserId("fan-platform", "kakao", "60218261625"))
                .isEmpty();
    }

    @Test
    @DisplayName("TASK-BE-611: 같은 신원이 두 테넌트에 있어도 조회는 각자 자기 행 하나 (전역 조회는 결과가 둘이었다 — BE-602 후속 ①)")
    void findByTenant_sameIdentityInTwoTenants_eachLookupReturnsItsOwnRow() {
        String storeAccount = uuid();
        String fanAccount = uuid();
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(storeAccount, "ecommerce", "kakao", "60218261625", "k@kakao.com")));
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(fanAccount, "fan-platform", "kakao", "60218261625", "k@kakao.com")));

        assertThat(repo.findByTenantIdAndProviderAndProviderUserId("ecommerce", "kakao", "60218261625"))
                .get().extracting(SocialIdentityJpaEntity::getAccountId).isEqualTo(storeAccount);
        assertThat(repo.findByTenantIdAndProviderAndProviderUserId("fan-platform", "kakao", "60218261625"))
                .get().extracting(SocialIdentityJpaEntity::getAccountId).isEqualTo(fanAccount);
    }

    // ── findByAccountId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAccountId — 단일 계정의 소셜 연동 목록 반환")
    void findByAccountId_existing_returnsLinkedIdentities() {
        String accountId = uuid();
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(accountId, null, "google", uuid(), "g@gmail.com")));
        repo.saveAndFlush(SocialIdentityJpaEntity.fromDomain(
                SocialIdentity.create(accountId, null, "kakao", uuid(), null)));

        List<SocialIdentityJpaEntity> result = repo.findByAccountId(accountId);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(SocialIdentityJpaEntity::getProvider)
                .containsExactlyInAnyOrder("google", "kakao");
    }

    @Test
    @DisplayName("findByAccountId — 소셜 연동 없는 계정 → 빈 목록")
    void findByAccountId_noIdentities_returnsEmpty() {
        assertThat(repo.findByAccountId(uuid())).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
