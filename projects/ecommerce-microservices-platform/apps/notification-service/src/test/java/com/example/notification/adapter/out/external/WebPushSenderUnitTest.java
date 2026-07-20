package com.example.notification.adapter.out.external;

import com.example.notification.application.port.out.PushSubscriptionRepository;
import com.example.notification.application.port.out.WebPushGateway;
import com.example.notification.application.port.out.WebPushSendResult;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.PushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebPushSender 단위 테스트")
class WebPushSenderUnitTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private WebPushGateway webPushGateway;

    private WebPushSender sender;

    @BeforeEach
    void setUp() {
        sender = new WebPushSender(subscriptionRepository, webPushGateway, new ObjectMapper());
    }

    private PushSubscription sub(String endpoint) {
        return PushSubscription.register("user-1", endpoint, "p256", "auth", "test-agent");
    }

    @Test
    @DisplayName("지원 채널은 PUSH")
    void supportedChannel_push() {
        assertThat(sender.supportedChannel()).isEqualTo(NotificationChannel.PUSH);
    }

    @Test
    @DisplayName("VAPID 미설정 시 구독 조회조차 하지 않고 skip")
    void notConfigured_skips() {
        given(webPushGateway.isConfigured()).willReturn(false);

        sender.send("user-1", "제목", "본문");

        verify(subscriptionRepository, never()).findByUserId(any());
        verify(webPushGateway, never()).send(any(), any());
    }

    @Test
    @DisplayName("구독 0건이면 no-op(발송 없음)")
    void noSubscriptions_noop() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1")).willReturn(List.of());

        sender.send("user-1", "제목", "본문");

        verify(webPushGateway, never()).send(any(), any());
    }

    @Test
    @DisplayName("성공(2xx) 시 각 구독에 발송하고 prune 하지 않는다")
    void success_delivers_noPrune() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1"))
                .willReturn(List.of(sub("https://push/a"), sub("https://push/b")));
        given(webPushGateway.send(any(), any())).willReturn(new WebPushSendResult(201));

        sender.send("user-1", "제목", "본문");

        verify(webPushGateway, org.mockito.Mockito.times(2)).send(any(), any());
        verify(subscriptionRepository, never()).delete(any());
    }

    /**
     * TASK-BE-540 — the send-path prune is where the endpoint-keyed delete actually did damage.
     *
     * <p>Unlike the HTTP unregister path there is NO owner filter here, so
     * {@code deleteByEndpoint(endpoint)} removed every tenant's row on that endpoint — and with
     * one VAPID key behind one origin, two tenants sharing an endpoint is the reachable case
     * (TASK-BE-540 AC-0). It also cannot be fixed by tenant-scoping: this runs on a Kafka thread
     * with no {@code TenantContext}, so a context-scoped delete resolves to the default tenant
     * and removes the wrong row. The fix deletes by row identity.
     *
     * <p>Asserted on the identity of the argument, not on its endpoint: an endpoint-keyed delete
     * would carry the same endpoint string and pass a laxer assertion.
     */
    @Test
    @DisplayName("BE-540: prune 은 endpoint 가 아니라 그 행 자체를 지운다 (같은 endpoint 의 다른 행 보존)")
    void expired_prunesThatRowOnly_notTheEndpoint() {
        PushSubscription dead = sub("https://push/shared");
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1")).willReturn(List.of(dead));
        given(webPushGateway.send(any(), any())).willReturn(new WebPushSendResult(410));

        sender.send("user-1", "제목", "본문");

        // Same object identity — the row the sender resolved, not "whatever holds this endpoint".
        verify(subscriptionRepository).delete(same(dead));
    }

    @Test
    @DisplayName("410 Gone 응답 시 해당 구독을 prune 한다")
    void expired_pruned() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1")).willReturn(List.of(sub("https://push/dead")));
        given(webPushGateway.send(any(), any())).willReturn(new WebPushSendResult(410));

        sender.send("user-1", "제목", "본문");

        verify(subscriptionRepository).delete(argThat(s -> s.getEndpoint().equals("https://push/dead")));
    }

    @Test
    @DisplayName("한 구독 발송이 예외를 던져도 나머지 구독 발송은 계속된다")
    void oneFailure_othersContinue() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1"))
                .willReturn(List.of(sub("https://push/bad"), sub("https://push/good")));
        when(webPushGateway.send(any(), any()))
                .thenThrow(new WebPushDeliveryException("boom", null))
                .thenReturn(new WebPushSendResult(201));

        sender.send("user-1", "제목", "본문");

        verify(webPushGateway, org.mockito.Mockito.times(2)).send(any(), any());
    }

    /**
     * TASK-BE-533 AC-2 — the fail-soft swallow above is what makes push failures invisible: before
     * this escalation a push that reached NOBODY still returned normally, so
     * {@code NotificationSendService} marked the row SENT and the failure counter never moved.
     * Instrumenting only the service layer would have reproduced the original defect one layer in.
     */
    @Test
    @DisplayName("BE-533: 모든 구독 발송이 실패하면 예외를 던져 실패가 계측 가능해진다")
    void allSubscriptionsFail_throwsSoTheFailureIsCountable() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1"))
                .willReturn(List.of(sub("https://push/a"), sub("https://push/b")));
        given(webPushGateway.send(any(), any())).willThrow(new WebPushDeliveryException("boom", null));

        assertThatThrownBy(() -> sender.send("user-1", "제목", "본문"))
                .isInstanceOf(WebPushDeliveryException.class)
                .hasMessageContaining("all 2 subscription(s)");
    }

    @Test
    @DisplayName("BE-533: 일부라도 전달됐으면 예외를 던지지 않는다 (부분 성공은 발송으로 집계)")
    void partialSuccess_doesNotThrow() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1"))
                .willReturn(List.of(sub("https://push/bad"), sub("https://push/good")));
        when(webPushGateway.send(any(), any()))
                .thenThrow(new WebPushDeliveryException("boom", null))
                .thenReturn(new WebPushSendResult(201));

        sender.send("user-1", "제목", "본문");

        verify(webPushGateway, org.mockito.Mockito.times(2)).send(any(), any());
    }

    /**
     * A pruned-expired subscription is housekeeping, not an outage — it must not be escalated as a
     * delivery failure, or every natural subscription expiry would page on-call.
     */
    @Test
    @DisplayName("BE-533: 전부 410(만료)이면 prune 만 하고 예외를 던지지 않는다")
    void allExpired_prunesWithoutThrowing() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1")).willReturn(List.of(sub("https://push/dead")));
        given(webPushGateway.send(any(), any())).willReturn(new WebPushSendResult(410));

        sender.send("user-1", "제목", "본문");

        verify(subscriptionRepository).delete(any());
    }
}
