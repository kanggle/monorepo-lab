package com.example.community.infrastructure.persistence;

import com.example.community.domain.feed.FeedSubscription;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("FeedSubscriptionJpaRepository 쿼리 슬라이스 테스트")
class FeedSubscriptionJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("community_db")
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
    private FeedSubscriptionJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── findByFanAccountIdAndArtistAccountId ──────────────────────────────────

    @Test
    @DisplayName("findByFanAccountIdAndArtistAccountId — 구독 반환")
    void findByFanAccountIdAndArtistAccountId_existing_returnsSubscription() {
        String fan = uuid(), artist = uuid();
        repo.saveAndFlush(FeedSubscription.create(fan, artist, Instant.now()));

        Optional<FeedSubscription> result =
                repo.findByFanAccountIdAndArtistAccountId(fan, artist);

        assertThat(result).isPresent();
        assertThat(result.get().getFanAccountId()).isEqualTo(fan);
        assertThat(result.get().getArtistAccountId()).isEqualTo(artist);
    }

    @Test
    @DisplayName("findByFanAccountIdAndArtistAccountId — 미구독 → empty")
    void findByFanAccountIdAndArtistAccountId_notFound_returnsEmpty() {
        assertThat(repo.findByFanAccountIdAndArtistAccountId(uuid(), uuid())).isEmpty();
    }

    // ── existsByFanAccountIdAndArtistAccountId ────────────────────────────────

    @Test
    @DisplayName("existsByFanAccountIdAndArtistAccountId — 구독 존재 → true")
    void existsByFanAccountIdAndArtistAccountId_existing_returnsTrue() {
        String fan = uuid(), artist = uuid();
        repo.saveAndFlush(FeedSubscription.create(fan, artist, Instant.now()));

        assertThat(repo.existsByFanAccountIdAndArtistAccountId(fan, artist)).isTrue();
    }

    @Test
    @DisplayName("existsByFanAccountIdAndArtistAccountId — 미구독 → false")
    void existsByFanAccountIdAndArtistAccountId_notFound_returnsFalse() {
        assertThat(repo.existsByFanAccountIdAndArtistAccountId(uuid(), uuid())).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
