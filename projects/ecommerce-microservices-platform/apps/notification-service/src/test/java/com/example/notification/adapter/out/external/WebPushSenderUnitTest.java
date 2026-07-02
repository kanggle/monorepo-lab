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
import static org.mockito.ArgumentMatchers.any;
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
        return PushSubscription.register("user-1", endpoint, "p256", "auth");
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
        verify(subscriptionRepository, never()).deleteByEndpoint(any());
    }

    @Test
    @DisplayName("410 Gone 응답 시 해당 구독을 prune 한다")
    void expired_pruned() {
        given(webPushGateway.isConfigured()).willReturn(true);
        given(subscriptionRepository.findByUserId("user-1")).willReturn(List.of(sub("https://push/dead")));
        given(webPushGateway.send(any(), any())).willReturn(new WebPushSendResult(410));

        sender.send("user-1", "제목", "본문");

        verify(subscriptionRepository).deleteByEndpoint("https://push/dead");
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
}
