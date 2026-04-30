package com.example.community.integration;

import com.example.community.domain.feed.FeedSubscription;
import com.example.community.domain.feed.FeedSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice integration tests for {@link FeedSubscriptionRepository} (TASK-BE-149).
 */
@SpringBootTest
@DisplayName("FeedSubscriptionRepository 통합 테스트")
class FeedSubscriptionRepositoryIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private FeedSubscriptionRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("save 후 find 로 조회된다")
    void save_and_find_succeeds() {
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s ->
                repository.save(FeedSubscription.create(fanId, artistId, Instant.now())));

        Optional<FeedSubscription> found = repository.find(fanId, artistId);
        assertThat(found).isPresent();
        assertThat(found.get().getFanAccountId()).isEqualTo(fanId);
        assertThat(found.get().getArtistAccountId()).isEqualTo(artistId);
    }

    @Test
    @DisplayName("save 후 exists 가 true 를 반환한다")
    void exists_returns_true_after_save() {
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s ->
                repository.save(FeedSubscription.create(fanId, artistId, Instant.now())));

        assertThat(repository.exists(fanId, artistId)).isTrue();
    }

    @Test
    @DisplayName("save 전에는 exists 가 false 를 반환한다")
    void exists_returns_false_before_save() {
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        assertThat(repository.exists(fanId, artistId)).isFalse();
        assertThat(repository.find(fanId, artistId)).isEmpty();
    }

    @Test
    @DisplayName("delete 후 더 이상 조회되지 않는다")
    void delete_removes_subscription() {
        String fanId = "fan-" + UUID.randomUUID();
        String artistId = "artist-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(s ->
                repository.save(FeedSubscription.create(fanId, artistId, Instant.now())));

        assertThat(repository.exists(fanId, artistId)).isTrue();

        transactionTemplate.executeWithoutResult(s -> {
            FeedSubscription fs = repository.find(fanId, artistId).orElseThrow();
            repository.delete(fs);
        });

        assertThat(repository.exists(fanId, artistId)).isFalse();
        assertThat(repository.find(fanId, artistId)).isEmpty();
    }
}
