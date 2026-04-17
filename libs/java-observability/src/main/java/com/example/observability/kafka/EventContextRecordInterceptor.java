package com.example.observability.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Populates MDC and the current OpenTelemetry span with {@code event.id} and
 * {@code event.type} from each consumed Kafka record, so that logs can be
 * filtered by event in Loki and traces can be searched by tag in Jaeger.
 *
 * Lookup order for each field:
 *   1. Kafka record header ("event.id", "event.type")
 *   2. Top-level JSON field of the record value ("eventId", "eventType")
 */
public class EventContextRecordInterceptor implements RecordInterceptor<String, String> {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_EVENT_ID = "eventId";
    public static final String MDC_EVENT_TYPE = "eventType";
    public static final String SPAN_ATTR_EVENT_ID = "event.id";
    public static final String SPAN_ATTR_EVENT_TYPE = "event.type";

    private static final String HEADER_EVENT_ID = "event.id";
    private static final String HEADER_EVENT_TYPE = "event.type";

    private final ObjectMapper objectMapper;

    public EventContextRecordInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Nullable
    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
                                                    Consumer<String, String> consumer) {
        Span currentSpan = Span.current();
        SpanContext spanContext = currentSpan.getSpanContext();
        if (spanContext.isValid()) {
            MDC.put(MDC_TRACE_ID, spanContext.getTraceId());
        }

        String eventId = extract(record, HEADER_EVENT_ID, "eventId");
        String eventType = extract(record, HEADER_EVENT_TYPE, "eventType");

        if (eventId != null) {
            MDC.put(MDC_EVENT_ID, eventId);
            currentSpan.setAttribute(SPAN_ATTR_EVENT_ID, eventId);
        }
        if (eventType != null) {
            MDC.put(MDC_EVENT_TYPE, eventType);
            currentSpan.setAttribute(SPAN_ATTR_EVENT_TYPE, eventType);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_EVENT_ID);
        MDC.remove(MDC_EVENT_TYPE);
    }

    @Nullable
    private String extract(ConsumerRecord<String, String> record, String headerKey, String jsonField) {
        Header header = record.headers().lastHeader(headerKey);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        String value = record.value();
        if (value == null || value.isEmpty() || value.charAt(0) != '{') {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode node = root.get(jsonField);
            return (node != null && !node.isNull()) ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
