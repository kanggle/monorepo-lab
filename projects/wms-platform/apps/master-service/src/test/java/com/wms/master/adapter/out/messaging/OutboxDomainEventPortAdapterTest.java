package com.wms.master.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.messaging.outbox.OutboxWriter;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import com.wms.master.domain.model.Warehouse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxDomainEventPortAdapterTest {

    private final OutboxWriter writer = mock(OutboxWriter.class);
    private final EventEnvelopeSerializer serializer = mock(EventEnvelopeSerializer.class);
    private final OutboxDomainEventPortAdapter adapter =
            new OutboxDomainEventPortAdapter(writer, serializer);

    @Test
    void publish_writesOneOutboxRowPerEvent_withEnvelopePayload() {
        when(serializer.serialize(any())).thenReturn("{\"envelope\":true}");
        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");

        adapter.publish(List.of(
                WarehouseCreatedEvent.from(wh),
                WarehouseUpdatedEvent.from(wh, List.of("name"))));

        ArgumentCaptor<String> aggregateType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        verify(writer, org.mockito.Mockito.times(2)).save(
                aggregateType.capture(), aggregateId.capture(),
                eventType.capture(), payload.capture());

        assertThat(aggregateType.getAllValues()).containsOnly("warehouse");
        assertThat(aggregateId.getAllValues()).containsOnly(wh.getId().toString());
        assertThat(eventType.getAllValues())
                .containsExactly("master.warehouse.created", "master.warehouse.updated");
        assertThat(payload.getAllValues()).containsOnly("{\"envelope\":true}");
    }

    @Test
    void publish_emptyList_doesNothing() {
        adapter.publish(List.of());
        org.mockito.Mockito.verifyNoInteractions(writer, serializer);
    }
}
