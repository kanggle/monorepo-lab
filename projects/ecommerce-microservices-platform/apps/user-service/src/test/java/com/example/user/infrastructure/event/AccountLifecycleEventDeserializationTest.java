package com.example.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IAM account.created / account.deleted 역직렬화 (user-service consumers, ADR-MONO-037)")
class AccountLifecycleEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("account.created: camelCase envelope + unknown field 무시 → accountId/emailHash 파싱")
    void deserialize_accountCreated_camelCase_ignoresUnknown() throws Exception {
        String json = """
                {
                  "eventId": "550e8400-e29b-41d4-a716-446655440000",
                  "eventType": "account.created",
                  "occurredAt": "2026-06-15T10:00:00Z",
                  "source": "account-service",
                  "schemaVersion": 2,
                  "payload": {
                    "accountId": "550e8400-e29b-41d4-a716-446655440001",
                    "tenantId": "ecommerce",
                    "emailHash": "a1b2c3d4e5",
                    "status": "ACTIVE",
                    "locale": "ko-KR",
                    "createdAt": "2026-06-15T10:00:00Z"
                  }
                }
                """;

        AccountCreatedEvent event = objectMapper.readValue(json, AccountCreatedEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.payload().accountId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(event.payload().tenantId()).isEqualTo("ecommerce");
        assertThat(event.payload().emailHash()).isEqualTo("a1b2c3d4e5");
    }

    @Test
    @DisplayName("account.created: snake_case envelope 도 역직렬화된다 (forward-compat)")
    void deserialize_accountCreated_snakeCase() throws Exception {
        String json = """
                {
                  "event_id": "550e8400-e29b-41d4-a716-446655440000",
                  "event_type": "account.created",
                  "occurred_at": "2026-06-15T10:00:00Z",
                  "source": "account-service",
                  "payload": {
                    "accountId": "550e8400-e29b-41d4-a716-446655440001",
                    "tenant_id": "ecommerce",
                    "emailHash": "a1b2c3d4e5"
                  }
                }
                """;

        AccountCreatedEvent event = objectMapper.readValue(json, AccountCreatedEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(event.payload().tenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("account.deleted: anonymized 플래그 + grace 필드를 파싱한다")
    void deserialize_accountDeleted_parsesAnonymizedAndGrace() throws Exception {
        String json = """
                {
                  "eventId": "550e8400-e29b-41d4-a716-446655440000",
                  "eventType": "account.deleted",
                  "occurredAt": "2026-06-15T10:00:00Z",
                  "source": "account-service",
                  "schemaVersion": 2,
                  "payload": {
                    "accountId": "550e8400-e29b-41d4-a716-446655440002",
                    "tenantId": "ecommerce",
                    "reasonCode": "REGULATED_DELETION",
                    "actorType": "user",
                    "deletedAt": "2026-06-15T10:00:00Z",
                    "gracePeriodEndsAt": "2026-07-15T10:00:00Z",
                    "anonymized": true
                  }
                }
                """;

        AccountDeletedEvent event = objectMapper.readValue(json, AccountDeletedEvent.class);

        assertThat(event.payload().accountId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"));
        assertThat(event.payload().reasonCode()).isEqualTo("REGULATED_DELETION");
        assertThat(event.payload().anonymized()).isTrue();
        assertThat(event.payload().gracePeriodEndsAt()).isNotNull();
    }
}
