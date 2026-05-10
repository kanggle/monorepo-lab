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
    @DisplayName("SUCCESS 결과는 row 를 PUBLISHED 로 표시하고 batch 를 계속 진행한다")
    void publishPendingEvents_success_marksPublishedAndContinues() {
        OutboxJpaEntity entry1 = OutboxJpaEntity.create("Order", "order-1", "OrderPlaced", "{\"test\":1}");
        OutboxJpaEntity entry2 = OutboxJpaEntity.create("Order", "order-2", "OrderPlaced", "{\"test\":2}");
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of(entry1, entry2));

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);
        given(sender.send(any(), any(), any())).willReturn(OutboxPublisher.SendOutcome.SUCCESS);

        outboxPublisher.publishPendingEvents(sender);

        verify(sender).send("OrderPlaced", "order-1", "{\"test\":1}");
        verify(sender).send("OrderPlaced", "order-2", "{\"test\":2}");
        assertThat(entry1.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entry2.getStatus()).isEqualTo("PUBLISHED");
        assertThat(entry1.getPublishedAt()).isNotNull();
        assertThat(entry2.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("FAILURE_TRANSIENT 결과는 row 를 PENDING 으로 유지하고 batch 를 break 한다 (retry storm 회피)")
    void publishPendingEvents_transientFailure_keepsPendingAndBreaks() {
        OutboxJpaEntity entry1 = OutboxJpaEntity.create("Order", "order-1", "OrderPlaced", "{}");
        OutboxJpaEntity entry2 = OutboxJpaEntity.create("Order", "order-2", "OrderPlaced", "{}");
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of(entry1, entry2));

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);
        given(sender.send("OrderPlaced", "order-1", "{}"))
                .willReturn(OutboxPublisher.SendOutcome.FAILURE_TRANSIENT);

        outboxPublisher.publishPendingEvents(sender);

        verify(sender).send("OrderPlaced", "order-1", "{}");
        verify(sender, never()).send("OrderPlaced", "order-2", "{}");
        assertThat(entry1.getStatus()).isEqualTo("PENDING");
        assertThat(entry2.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("FAILURE_PERMANENT 결과는 row 를 FAILED 로 표시하고 batch drain 을 계속한다 (poison-pill 격리)")
    void publishPendingEvents_permanentFailure_marksFailedAndContinues() {
        OutboxJpaEntity entry1 = OutboxJpaEntity.create("Order", "order-1", "OrderPlaced", "{}");
        OutboxJpaEntity entry2 = OutboxJpaEntity.create("Order", "order-2", "OrderPlaced", "{}");
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of(entry1, entry2));

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);
        given(sender.send("OrderPlaced", "order-1", "{}"))
                .willReturn(OutboxPublisher.SendOutcome.FAILURE_PERMANENT);
        given(sender.send("OrderPlaced", "order-2", "{}"))
                .willReturn(OutboxPublisher.SendOutcome.SUCCESS);

        outboxPublisher.publishPendingEvents(sender);

        verify(sender).send("OrderPlaced", "order-1", "{}");
        verify(sender).send("OrderPlaced", "order-2", "{}");
        assertThat(entry1.getStatus()).isEqualTo("FAILED");
        assertThat(entry1.getPublishedAt()).as("FAILED row 에도 terminal timestamp 가 기록된다").isNotNull();
        assertThat(entry2.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("PERMANENT 후속 TRANSIENT 혼합 — PERMANENT row 는 FAILED, 후속 TRANSIENT 에서 break")
    void publishPendingEvents_permanentThenTransient_marksFailedThenBreaks() {
        OutboxJpaEntity entry1 = OutboxJpaEntity.create("Order", "order-1", "Bogus", "{}");
        OutboxJpaEntity entry2 = OutboxJpaEntity.create("Order", "order-2", "OrderPlaced", "{}");
        OutboxJpaEntity entry3 = OutboxJpaEntity.create("Order", "order-3", "OrderPlaced", "{}");
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of(entry1, entry2, entry3));

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);
        given(sender.send("Bogus", "order-1", "{}"))
                .willReturn(OutboxPublisher.SendOutcome.FAILURE_PERMANENT);
        given(sender.send("OrderPlaced", "order-2", "{}"))
                .willReturn(OutboxPublisher.SendOutcome.FAILURE_TRANSIENT);

        outboxPublisher.publishPendingEvents(sender);

        verify(sender).send("Bogus", "order-1", "{}");
        verify(sender).send("OrderPlaced", "order-2", "{}");
        verify(sender, never()).send("OrderPlaced", "order-3", "{}");
        assertThat(entry1.getStatus()).isEqualTo("FAILED");
        assertThat(entry2.getStatus()).isEqualTo("PENDING");
        assertThat(entry3.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 sender 를 호출하지 않는다")
    void publishPendingEvents_noPending_doesNotCallSender() {
        given(outboxJpaRepository.findPendingWithLock(any(PageRequest.class)))
                .willReturn(List.of());

        OutboxPublisher.EventSender sender = mock(OutboxPublisher.EventSender.class);

        outboxPublisher.publishPendingEvents(sender);

        verifyNoInteractions(sender);
    }
}
