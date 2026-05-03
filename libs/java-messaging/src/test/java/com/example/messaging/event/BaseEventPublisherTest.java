package com.example.messaging.event;

import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BaseEventPublisher 단위 테스트")
class BaseEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private BaseEventPublisher publisher() {
        return new BaseEventPublisher(outboxWriter, objectMapper) {};
    }

    @Test
    @DisplayName("writeEvent — 표준 엔벨로프 7개 필드 모두 포함")
    void writeEvent_buildsStandardEnvelope() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", "value");

        publisher().writeEvent("myAggregate", "agg-1", "my.event", "my-service", payload);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("myAggregate"), eq("agg-1"), eq("my.event"), jsonCaptor.capture());

        Map<String, Object> envelope = objectMapper.readValue(jsonCaptor.getValue(), new TypeReference<>() {});
        assertThat(envelope).containsKey("eventId");
        assertThat(envelope.get("eventId").toString()).hasSize(36);
        assertThat(envelope).containsEntry("eventType", "my.event");
        assertThat(envelope).containsEntry("source", "my-service");
        assertThat(envelope).containsKey("occurredAt");
        assertThat(envelope).containsEntry("schemaVersion", 1);
        assertThat(envelope).containsEntry("partitionKey", "agg-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> innerPayload = (Map<String, Object>) envelope.get("payload");
        assertThat(innerPayload).containsEntry("key", "value");
    }

    @Test
    @DisplayName("writeEvent — eventId 는 UUID v7 형식 (version=7, variant=2)")
    void writeEvent_eventIdIsUuidV7() throws Exception {
        publisher().writeEvent("agg", "agg-1", "evt", "svc", new LinkedHashMap<>());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("agg"), eq("agg-1"), eq("evt"), captor.capture());

        String eventId = objectMapper.readValue(captor.getValue(), new TypeReference<Map<String, Object>>() {})
                .get("eventId").toString();

        UUID uuid = UUID.fromString(eventId);
        // RFC 9562 §5.7 — version nibble must be 7
        assertThat(uuid.version()).isEqualTo(7);
        // variant bits "10" — java UUID.variant() = 2
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("writeEvent — 연속 호출 시 eventId 가 lexicographic 오름차순 (시간 정렬)")
    void writeEvent_consecutiveEventIdsAreLexicographicallyOrdered() throws Exception {
        publisher().writeEvent("agg", "id-1", "evt", "svc", new LinkedHashMap<>());
        publisher().writeEvent("agg", "id-2", "evt", "svc", new LinkedHashMap<>());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter, times(2)).save(eq("agg"), any(), eq("evt"), captor.capture());

        String id1 = objectMapper.readValue(captor.getAllValues().get(0), new TypeReference<Map<String, Object>>() {})
                .get("eventId").toString();
        String id2 = objectMapper.readValue(captor.getAllValues().get(1), new TypeReference<Map<String, Object>>() {})
                .get("eventId").toString();

        // UUID v7 strings are lexicographically ordered by timestamp prefix
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.compareTo(id2)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("writeEvent — 호출마다 다른 eventId 생성")
    void writeEvent_generatesUniqueEventIdPerCall() throws Exception {
        publisher().writeEvent("agg", "id-1", "evt", "svc", new LinkedHashMap<>());
        publisher().writeEvent("agg", "id-2", "evt", "svc", new LinkedHashMap<>());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter, times(2)).save(eq("agg"), any(), eq("evt"), captor.capture());

        String id1 = objectMapper.readValue(captor.getAllValues().get(0), new TypeReference<Map<String, Object>>() {})
                .get("eventId").toString();
        String id2 = objectMapper.readValue(captor.getAllValues().get(1), new TypeReference<Map<String, Object>>() {})
                .get("eventId").toString();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("saveEvent — 엔벨로프 없이 페이로드 직접 직렬화")
    void saveEvent_savesPayloadWithoutEnvelope() throws Exception {
        Map<String, Object> payload = Map.of("accountId", "acc-1", "status", "DELETED");

        publisher().saveEvent("Account", "acc-1", "account.deleted", payload);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Account"), eq("acc-1"), eq("account.deleted"), jsonCaptor.capture());

        Map<String, Object> saved = objectMapper.readValue(jsonCaptor.getValue(), new TypeReference<>() {});
        assertThat(saved).containsEntry("accountId", "acc-1");
        assertThat(saved).containsEntry("status", "DELETED");
        assertThat(saved).doesNotContainKey("eventId");
        assertThat(saved).doesNotContainKey("payload");
    }

    @Test
    @DisplayName("toJson — 순환 참조 직렬화 실패 시 EventSerializationException 발생")
    void toJson_circularReference_throwsEventSerializationException() {
        Map<String, Object> circular = new LinkedHashMap<>();
        circular.put("self", circular);

        assertThatThrownBy(() -> publisher().saveEvent("X", "x-1", "x.event", circular))
                .isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("Failed to serialize event payload");
    }
}
