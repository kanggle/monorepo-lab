package com.example.messaging.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher 단위 테스트")
class OutboxPublisherTest {

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Mock
    private OutboxJpaRepository outboxJpaRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 50);
    }

    @Test
    @DisplayName("PENDING 이벤트가 있으면 sender를 호출하고 발행 완료로 표시한다")
    void publishPendingEvents_pendingExists_sendsAndMarksPublished() {
        OutboxJpaEntity entry = OutboxJpaEntity.create("Order", "order-1", "OrderPlaced", "{\"test\":true}");
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of(entry));

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);
        given(sender.send("OrderPlaced", "order-1", "{\"test\":true}")).willReturn(true);

        outboxPublisher.publishPendingEvents(sender);

        verify(sender).send("OrderPlaced", "order-1", "{\"test\":true}");
        assertThat(entry.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entry.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("sender가 실패하면 이벤트를 PENDING 상태로 유지하고 나머지를 건너뛴다")
    void publishPendingEvents_senderFails_stopsAndKeepsPending() {
        OutboxJpaEntity entry1 = OutboxJpaEntity.create("Order", "order-1", "OrderPlaced", "{}");
        OutboxJpaEntity entry2 = OutboxJpaEntity.create("Order", "order-2", "OrderPlaced", "{}");
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of(entry1, entry2));

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);
        given(sender.send("OrderPlaced", "order-1", "{}")).willReturn(false);

        outboxPublisher.publishPendingEvents(sender);

        verify(sender).send("OrderPlaced", "order-1", "{}");
        verify(sender, never()).send("OrderPlaced", "order-2", "{}");
        assertThat(entry1.getStatus()).isEqualTo("PENDING");
        assertThat(entry2.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 sender를 호출하지 않는다")
    void publishPendingEvents_noPending_doesNotCallSender() {
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of());

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);

        outboxPublisher.publishPendingEvents(sender);

        verifyNoInteractions(sender);
    }
}
