package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.PushSubscriptionJpaEntity;
import com.example.notification.domain.model.PushSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PushSubscriptionPersistenceMapper 단위 테스트")
class PushSubscriptionPersistenceMapperTest {

    private final PushSubscriptionPersistenceMapper mapper = new PushSubscriptionPersistenceMapper();

    @Test
    @DisplayName("도메인 → 엔티티 → 도메인 왕복이 모든 필드를 보존한다")
    void roundTrip_preservesFields() {
        PushSubscription original = PushSubscription.register("user-1", "https://push/ep", "p256", "auth", "Mozilla/5.0 Chrome");

        PushSubscriptionJpaEntity entity = mapper.toEntity(original);
        assertThat(entity.getId()).isEqualTo(original.getSubscriptionId());
        assertThat(entity.getTenantId()).isEqualTo(original.getTenantId());
        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getEndpoint()).isEqualTo("https://push/ep");
        assertThat(entity.getP256dh()).isEqualTo("p256");
        assertThat(entity.getAuth()).isEqualTo("auth");
        assertThat(entity.getUserAgent()).isEqualTo("Mozilla/5.0 Chrome");

        PushSubscription back = mapper.toDomain(entity);
        assertThat(back.getSubscriptionId()).isEqualTo(original.getSubscriptionId());
        assertThat(back.getTenantId()).isEqualTo(original.getTenantId());
        assertThat(back.getUserId()).isEqualTo(original.getUserId());
        assertThat(back.getEndpoint()).isEqualTo(original.getEndpoint());
        assertThat(back.getP256dh()).isEqualTo(original.getP256dh());
        assertThat(back.getAuth()).isEqualTo(original.getAuth());
        assertThat(back.getUserAgent()).isEqualTo(original.getUserAgent());
        assertThat(back.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(back.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Test
    @DisplayName("User-Agent 가 null 이어도 왕복이 null 을 보존한다")
    void roundTrip_nullUserAgent() {
        PushSubscription original = PushSubscription.register("user-1", "https://push/ep", "p256", "auth", null);

        PushSubscriptionJpaEntity entity = mapper.toEntity(original);
        assertThat(entity.getUserAgent()).isNull();

        PushSubscription back = mapper.toDomain(entity);
        assertThat(back.getUserAgent()).isNull();
    }
}
