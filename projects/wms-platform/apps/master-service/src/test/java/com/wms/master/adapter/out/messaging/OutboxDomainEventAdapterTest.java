package com.wms.master.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wms.master.adapter.out.persistence.outbox.MasterOutboxEntity;
import com.wms.master.adapter.out.persistence.outbox.MasterOutboxRepository;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import com.wms.master.domain.model.Warehouse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxDomainEventAdapterTest {

    private final MasterOutboxRepository repository = mock(MasterOutboxRepository.class);
    private final EventEnvelopeSerializer serializer = mock(EventEnvelopeSerializer.class);
    private final OutboxDomainEventAdapter adapter =
            new OutboxDomainEventAdapter(repository, serializer);

    @Test
    void publish_writesOneV2OutboxRowPerEvent_reusingTheSerializerEventId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(serializer.serialize(any()))
                .thenReturn(new EventEnvelopeSerializer.Serialised(id1, "{\"envelope\":1}"))
                .thenReturn(new EventEnvelopeSerializer.Serialised(id2, "{\"envelope\":2}"));

        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
        WarehouseCreatedEvent created = WarehouseCreatedEvent.from(wh);
        WarehouseUpdatedEvent updated = WarehouseUpdatedEvent.from(wh, List.of("name"));

        adapter.publish(List.of(created, updated));

        ArgumentCaptor<MasterOutboxEntity> rows = ArgumentCaptor.forClass(MasterOutboxEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(rows.capture());

        MasterOutboxEntity row1 = rows.getAllValues().get(0);
        // event_id is the SAME id the serializer generated and embedded in the
        // envelope JSON — so the Kafka eventId header matches the payload id.
        assertThat(row1.getEventId()).isEqualTo(id1);
        assertThat(row1.getEventType()).isEqualTo("master.warehouse.created");
        assertThat(row1.getAggregateType()).isEqualTo("warehouse");
        assertThat(row1.getAggregateId()).isEqualTo(wh.getId().toString());
        assertThat(row1.getPayload()).isEqualTo("{\"envelope\":1}");
        assertThat(row1.getOccurredAt()).isEqualTo(created.occurredAt());
        // partition_key left null — the publisher falls back to aggregateId as
        // the Kafka record key (preserves v1 per-aggregate ordering).
        assertThat(row1.getPartitionKey()).isNull();
        assertThat(row1.getPublishedAt()).isNull();
        assertThat(row1.getRetries()).isZero();

        MasterOutboxEntity row2 = rows.getAllValues().get(1);
        assertThat(row2.getEventId()).isEqualTo(id2);
        assertThat(row2.getEventType()).isEqualTo("master.warehouse.updated");
        assertThat(row2.getPayload()).isEqualTo("{\"envelope\":2}");
    }

    @Test
    void publish_emptyList_doesNothing() {
        adapter.publish(List.of());
        verifyNoInteractions(repository, serializer);
    }
}
