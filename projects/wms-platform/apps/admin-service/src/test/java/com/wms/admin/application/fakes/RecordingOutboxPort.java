package com.wms.admin.application.fakes;

import com.wms.admin.application.port.OutboxPort;
import java.util.ArrayList;
import java.util.List;

public class RecordingOutboxPort implements OutboxPort {

    public record Row(String aggregateType, String aggregateId, String eventType,
                      String payload, String partitionKey) {}

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void append(String aggregateType, String aggregateId, String eventType,
                       String payload, String partitionKey) {
        rows.add(new Row(aggregateType, aggregateId, eventType, payload, partitionKey));
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    public List<String> eventTypes() {
        return rows.stream().map(Row::eventType).toList();
    }
}
