package com.example.order.infrastructure.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("UserWithdrawnEvent 역직렬화 테스트 (order-service consumer)")
class UserWithdrawnEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("snake_case envelope JSON에서 역직렬화된다")
    void deserialize_snakeCaseEnvelope_succeeds() throws Exception {
        String json = """
                {
                  "event_id": "evt-789",
                  "event_type": "UserWithdrawn",
                  "occurred_at": "2026-03-25T00:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "user-1",
                    "withdrawnAt": "2026-03-25T00:00:00Z"
                  }
                }
                """;

        UserWithdrawnEvent event = objectMapper.readValue(json, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-789");
        assertThat(event.eventType()).isEqualTo("UserWithdrawn");
        assertThat(event.payload().userId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("camelCase envelope JSON에서도 역직렬화된다 (하위 호환)")
    void deserialize_camelCaseEnvelope_backwardsCompatible() throws Exception {
        String json = """
                {
                  "eventId": "evt-789",
                  "eventType": "UserWithdrawn",
                  "occurredAt": "2026-03-25T00:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "user-1",
                    "withdrawnAt": "2026-03-25T00:00:00Z"
                  }
                }
                """;

        UserWithdrawnEvent event = objectMapper.readValue(json, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-789");
        assertThat(event.eventType()).isEqualTo("UserWithdrawn");
    }

    private static final String REAL_ENVELOPE = """
            {
              "event_id": "evt-789",
              "event_type": "UserWithdrawn",
              "occurred_at": "2026-03-25T00:00:00Z",
              "source": "user-service",
              "tenant_id": "ecommerce",
              "payload": {
                "userId": "user-1",
                "withdrawnAt": "2026-03-25T00:00:00Z"
              }
            }
            """;

    /**
     * TASK-BE-545 correction — evidence the "production DLQ outage" claim is false.
     *
     * <p>The real envelope always carries {@code tenant_id}
     * (specs/contracts/events/user-events.md § Event Envelope; ADR-MONO-030 Step 4 / TASK-BE-367 M5).
     * The consumer is injected with Spring Boot's auto-configured {@code ObjectMapper}, on which
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} is disabled by default — so the real envelope deserialises
     * regardless of whether {@code UserWithdrawnEvent} declares {@code @JsonIgnoreProperties}. This
     * models that production mapper (feature off) and shows the field is accepted: real messages
     * were never rejected, never routed to {@code user.user.withdrawn.dlq}. The original claim came
     * from a bare {@code new ObjectMapper()}, which enables the strict feature — see the strict-mapper
     * test below for the configuration under which the annotation actually matters.
     */
    @Test
    @DisplayName("운영 mapper(FAIL_ON_UNKNOWN 비활성)에서는 어노테이션 없이도 tenant_id envelope 를 받아들인다")
    void deserialize_realEnvelope_defaultMapper_acceptsRegardlessOfAnnotation() throws Exception {
        ObjectMapper productionLikeMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        UserWithdrawnEvent event = productionLikeMapper.readValue(REAL_ENVELOPE, UserWithdrawnEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-789");
        assertThat(event.payload().userId()).isEqualTo("user-1");
    }

    /**
     * TASK-BE-545 correction — the guard the {@code @JsonIgnoreProperties} annotation actually
     * provides. A <em>strict</em> mapper models the only configuration under which the annotation
     * changes behavior: a hypothetical global
     * {@code spring.jackson.deserialization.fail-on-unknown-properties=true}. Under strict handling
     * the unknown {@code tenant_id} would throw {@code UnrecognizedPropertyException} — unless the
     * record declares {@code @JsonIgnoreProperties(ignoreUnknown = true)}. This is the test that
     * fails if the annotation is removed (verified by mutation); it does not claim any production
     * outage, because the shipped mapper does not enable this feature.
     */
    @Test
    @DisplayName("strict mapper(FAIL_ON_UNKNOWN 활성)에서도 받아들인다 — @JsonIgnoreProperties 가 지키는 유일한 경우")
    void deserialize_realEnvelope_strictMapper_survivesBecauseOfAnnotation() {
        ObjectMapper strictMapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        assertThatCode(() -> {
            UserWithdrawnEvent event = strictMapper.readValue(REAL_ENVELOPE, UserWithdrawnEvent.class);
            assertThat(event.eventId()).isEqualTo("evt-789");
            assertThat(event.payload().userId()).isEqualTo("user-1");
        }).doesNotThrowAnyException();
    }
}
