package com.example.messaging.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxWriter 단위 테스트")
class OutboxWriterTest {

    @InjectMocks
    private OutboxWriter outboxWriter;

    @Mock
    private OutboxJpaRepository outboxJpaRepository;

    @Captor
    private ArgumentCaptor<OutboxJpaEntity> entityCaptor;

    @Test
    @DisplayName("save 호출 시 PENDING 상태의 outbox 엔트리가 저장된다")
    void save_createsOutboxEntryWithPendingStatus() {
        outboxWriter.save("Order", "order-1", "OrderPlaced", "{\"orderId\":\"order-1\"}");

        verify(outboxJpaRepository).save(entityCaptor.capture());
        OutboxJpaEntity saved = entityCaptor.getValue();

        assertThat(saved.getAggregateType()).isEqualTo("Order");
        assertThat(saved.getAggregateId()).isEqualTo("order-1");
        assertThat(saved.getEventType()).isEqualTo("OrderPlaced");
        assertThat(saved.getPayload()).isEqualTo("{\"orderId\":\"order-1\"}");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
    }
}
