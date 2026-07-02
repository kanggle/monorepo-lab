package com.example.notification.application.service;

import com.example.notification.application.command.RegisterPushSubscriptionCommand;
import com.example.notification.application.port.out.PushSubscriptionRepository;
import com.example.notification.application.port.out.WebPushGateway;
import com.example.notification.application.result.RegisterSubscriptionResult;
import com.example.notification.domain.exception.PushNotConfiguredException;
import com.example.notification.domain.model.PushSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushSubscriptionService 단위 테스트")
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private WebPushGateway webPushGateway;

    @InjectMocks
    private PushSubscriptionService service;

    private RegisterPushSubscriptionCommand cmd(String endpoint) {
        return new RegisterPushSubscriptionCommand("user-1", endpoint, null, "p256", "auth");
    }

    @Test
    @DisplayName("신규 endpoint 등록 시 새 구독을 저장하고 created=true 를 반환한다")
    void register_new_created() {
        given(subscriptionRepository.findByEndpoint("https://push/new")).willReturn(Optional.empty());
        given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegisterSubscriptionResult result = service.register(cmd("https://push/new"));

        assertThat(result.created()).isTrue();
        assertThat(result.subscriptionId()).isNotBlank();
        verify(subscriptionRepository).save(any(PushSubscription.class));
    }

    @Test
    @DisplayName("기존 endpoint 재등록 시 키만 갱신하고 created=false 를 반환한다(중복행 없음)")
    void register_existing_refreshed() {
        PushSubscription existing = PushSubscription.register("user-1", "https://push/ep", "old-p", "old-a");
        given(subscriptionRepository.findByEndpoint("https://push/ep")).willReturn(Optional.of(existing));
        given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegisterSubscriptionResult result = service.register(cmd("https://push/ep"));

        assertThat(result.created()).isFalse();
        assertThat(existing.getP256dh()).isEqualTo("p256"); // rotated
        verify(subscriptionRepository).save(existing);
    }

    @Test
    @DisplayName("소유자가 해지하면 endpoint 를 삭제한다")
    void unregister_owner_deletes() {
        PushSubscription sub = PushSubscription.register("user-1", "https://push/ep", "p", "a");
        given(subscriptionRepository.findByEndpoint("https://push/ep")).willReturn(Optional.of(sub));

        service.unregister("user-1", "https://push/ep");

        verify(subscriptionRepository).deleteByEndpoint("https://push/ep");
    }

    @Test
    @DisplayName("다른 사용자의 endpoint 해지는 no-op(삭제 안 함)")
    void unregister_nonOwner_noop() {
        PushSubscription sub = PushSubscription.register("owner", "https://push/ep", "p", "a");
        given(subscriptionRepository.findByEndpoint("https://push/ep")).willReturn(Optional.of(sub));

        service.unregister("intruder", "https://push/ep");

        verify(subscriptionRepository, never()).deleteByEndpoint(any());
    }

    @Test
    @DisplayName("없는 endpoint 해지는 no-op(멱등)")
    void unregister_absent_noop() {
        given(subscriptionRepository.findByEndpoint("https://push/gone")).willReturn(Optional.empty());

        service.unregister("user-1", "https://push/gone");

        verify(subscriptionRepository, never()).deleteByEndpoint(any());
    }

    @Test
    @DisplayName("VAPID 설정 시 공개키를 반환한다")
    void vapidPublicKey_configured() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(webPushGateway.publicKey()).willReturn("BPk...");

        assertThat(service.getVapidPublicKey()).isEqualTo("BPk...");
    }

    @Test
    @DisplayName("VAPID 미설정 시 PushNotConfiguredException")
    void vapidPublicKey_notConfigured_throws() {
        given(webPushGateway.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> service.getVapidPublicKey())
                .isInstanceOf(PushNotConfiguredException.class);
    }
}
