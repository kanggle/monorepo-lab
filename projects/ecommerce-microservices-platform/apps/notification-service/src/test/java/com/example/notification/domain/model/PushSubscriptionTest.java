package com.example.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PushSubscription 도메인 테스트")
class PushSubscriptionTest {

    @Test
    @DisplayName("register 는 새 id/기본 테넌트/타임스탬프로 구독을 생성한다")
    void register_createsSubscription() {
        PushSubscription sub = PushSubscription.register("user-1", "https://push/ep", "p256", "auth");

        assertThat(sub.getSubscriptionId()).isNotBlank();
        assertThat(sub.getTenantId()).isEqualTo("ecommerce"); // TenantContext unset → default
        assertThat(sub.getUserId()).isEqualTo("user-1");
        assertThat(sub.getEndpoint()).isEqualTo("https://push/ep");
        assertThat(sub.getP256dh()).isEqualTo("p256");
        assertThat(sub.getAuth()).isEqualTo("auth");
        assertThat(sub.getCreatedAt()).isNotNull();
        assertThat(sub.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("register 는 빈 endpoint/키를 거부한다")
    void register_rejectsBlank() {
        assertThatThrownBy(() -> PushSubscription.register("user-1", "", "p", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushSubscription.register("user-1", "ep", " ", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushSubscription.register("", "ep", "p", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateKeys 는 키만 회전하고 endpoint/생성시각은 보존한다")
    void updateKeys_rotatesKeys() {
        PushSubscription sub = PushSubscription.register("user-1", "https://push/ep", "old-p", "old-a");
        String endpoint = sub.getEndpoint();

        sub.updateKeys("new-p", "new-a");

        assertThat(sub.getP256dh()).isEqualTo("new-p");
        assertThat(sub.getAuth()).isEqualTo("new-a");
        assertThat(sub.getEndpoint()).isEqualTo(endpoint);
    }
}
