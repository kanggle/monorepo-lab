package com.example.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-wire deserialization of the IAM {@code account.created} / {@code account.deleted}
 * events as consumed by user-service (ADR-MONO-037). The JSON here is the EXACT FLAT shape
 * the IAM producer emits (top-level fields, NO {@code payload} wrapper, NO {@code eventId})
 * per {@code iam-platform/specs/contracts/events/account-events.md}. This is the test that
 * would have caught the nested-DTO defect (TASK-BE-422): a nested DTO parses the whole
 * message to a {@code null} payload here.
 */
@DisplayName("IAM account.created / account.deleted 역직렬화 — FLAT wire (TASK-BE-422)")
class AccountLifecycleEventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("account.created: FLAT camelCase + unknown field 무시 → root-level accountId/emailHash 파싱")
    void deserialize_accountCreated_flatCamelCase_ignoresUnknown() throws Exception {
        // EXACT flat shape from account-events.md § account.created (top-level, no wrapper).
        String json = """
                {
                  "accountId": "550e8400-e29b-41d4-a716-446655440001",
                  "tenantId": "ecommerce",
                  "emailHash": "a1b2c3d4e5",
                  "status": "ACTIVE",
                  "locale": "ko-KR",
                  "createdAt": "2026-06-15T10:00:00Z"
                }
                """;

        AccountCreatedEvent event = objectMapper.readValue(json, AccountCreatedEvent.class);

        assertThat(event.accountId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(event.tenantId()).isEqualTo("ecommerce");
        assertThat(event.emailHash()).isEqualTo("a1b2c3d4e5");
        assertThat(event.status()).isEqualTo("ACTIVE");
        assertThat(event.locale()).isEqualTo("ko-KR");
        assertThat(event.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("account.created: snake_case alias 도 역직렬화된다 (forward-compat)")
    void deserialize_accountCreated_snakeCase() throws Exception {
        String json = """
                {
                  "account_id": "550e8400-e29b-41d4-a716-446655440001",
                  "tenant_id": "ecommerce",
                  "email_hash": "a1b2c3d4e5",
                  "status": "ACTIVE",
                  "locale": "ko-KR",
                  "created_at": "2026-06-15T10:00:00Z"
                }
                """;

        AccountCreatedEvent event = objectMapper.readValue(json, AccountCreatedEvent.class);

        assertThat(event.accountId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(event.tenantId()).isEqualTo("ecommerce");
        assertThat(event.emailHash()).isEqualTo("a1b2c3d4e5");
    }

    @Test
    @DisplayName("account.deleted: FLAT anonymized 플래그 + grace 필드를 root 에서 파싱한다")
    void deserialize_accountDeleted_flat_parsesAnonymizedAndGrace() throws Exception {
        // EXACT flat shape from account-events.md § account.deleted — note NO eventId field.
        String json = """
                {
                  "accountId": "550e8400-e29b-41d4-a716-446655440002",
                  "tenantId": "ecommerce",
                  "reasonCode": "REGULATED_DELETION",
                  "actorType": "user",
                  "actorId": null,
                  "deletedAt": "2026-06-15T10:00:00Z",
                  "gracePeriodEndsAt": "2026-07-15T10:00:00Z",
                  "anonymized": true
                }
                """;

        AccountDeletedEvent event = objectMapper.readValue(json, AccountDeletedEvent.class);

        assertThat(event.accountId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"));
        assertThat(event.tenantId()).isEqualTo("ecommerce");
        assertThat(event.reasonCode()).isEqualTo("REGULATED_DELETION");
        assertThat(event.actorType()).isEqualTo("user");
        assertThat(event.anonymized()).isTrue();
        assertThat(event.deletedAt()).isNotNull();
        assertThat(event.gracePeriodEndsAt()).isNotNull();
    }
}
