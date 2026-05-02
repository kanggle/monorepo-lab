package com.example.security.consumer;

import com.example.security.application.pii.PiiMaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountDeletedAnonymizedConsumerUnitTest {

    @Mock
    private PiiMaskingService piiMaskingService;

    private AccountDeletedAnonymizedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountDeletedAnonymizedConsumer(new ObjectMapper(), piiMaskingService);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("account.deleted", 0, 0L, "key", value);
    }

    // ─── Filter: anonymized=false must be skipped ─────────────────────────

    @Test
    @DisplayName("anonymized=false event is skipped — maskPii never called")
    void anonymizedFalse_skipped() {
        String json = """
                {
                  "eventId": "evt-grace-001",
                  "eventType": "account.deleted",
                  "payload": {
                    "accountId": "acc-001",
                    "tenantId": "fan-platform",
                    "anonymized": false,
                    "deletedAt": "2026-05-01T10:00:00Z",
                    "gracePeriodEndsAt": "2026-06-01T10:00:00Z"
                  }
                }
                """;

        consumer.onMessage(record(json));

        verifyNoInteractions(piiMaskingService);
    }

    @Test
    @DisplayName("anonymized field missing (default=false) is skipped — maskPii never called")
    void anonymizedMissing_treatedAsFalse_skipped() {
        String json = """
                {
                  "eventId": "evt-no-anon",
                  "payload": {
                    "accountId": "acc-002",
                    "tenantId": "fan-platform"
                  }
                }
                """;

        consumer.onMessage(record(json));

        verifyNoInteractions(piiMaskingService);
    }

    // ─── Happy path: anonymized=true ─────────────────────────────────────

    @Test
    @DisplayName("anonymized=true triggers maskPii with correct tenantId and accountId")
    void anonymizedTrue_delegatesToMaskPii() {
        String json = """
                {
                  "eventId": "evt-anon-001",
                  "eventType": "account.deleted",
                  "payload": {
                    "accountId": "acc-003",
                    "tenantId": "wms",
                    "anonymized": true,
                    "deletedAt": "2026-05-02T10:00:00Z"
                  }
                }
                """;

        when(piiMaskingService.maskPii("evt-anon-001", "wms", "acc-003")).thenReturn(true);

        consumer.onMessage(record(json));

        verify(piiMaskingService).maskPii("evt-anon-001", "wms", "acc-003");
    }

    @Test
    @DisplayName("Flat payload form (no nested payload key) is also supported")
    void flatPayloadForm_anonymizedTrue_works() {
        String json = """
                {
                  "eventId": "evt-flat-001",
                  "accountId": "acc-flat",
                  "tenantId": "fan-platform",
                  "anonymized": true
                }
                """;

        when(piiMaskingService.maskPii("evt-flat-001", "fan-platform", "acc-flat")).thenReturn(true);

        consumer.onMessage(record(json));

        verify(piiMaskingService).maskPii("evt-flat-001", "fan-platform", "acc-flat");
    }

    // ─── Idempotency via PiiMaskingService ────────────────────────────────

    @Test
    @DisplayName("Duplicate event — PiiMaskingService returns false, no exception thrown")
    void duplicateEvent_maskPiiReturnsFalse_noException() {
        String json = """
                {
                  "eventId": "evt-dup-001",
                  "payload": {
                    "accountId": "acc-dup",
                    "tenantId": "fan-platform",
                    "anonymized": true
                  }
                }
                """;

        when(piiMaskingService.maskPii("evt-dup-001", "fan-platform", "acc-dup")).thenReturn(false);

        // Must not throw — duplicate is treated as success.
        consumer.onMessage(record(json));

        verify(piiMaskingService).maskPii("evt-dup-001", "fan-platform", "acc-dup");
    }

    // ─── Validation failures → DLQ ────────────────────────────────────────

    @Test
    @DisplayName("Missing eventId throws IllegalArgumentException (routes to DLQ)")
    void missingEventId_throws() {
        String json = """
                {
                  "payload": {
                    "accountId": "acc-004",
                    "tenantId": "fan-platform",
                    "anonymized": true
                  }
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(record(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");

        verifyNoInteractions(piiMaskingService);
    }

    @Test
    @DisplayName("Missing tenantId (anonymized=true) throws MissingTenantIdException (routes to DLQ)")
    void missingTenantId_anonymizedTrue_throws() {
        String json = """
                {
                  "eventId": "evt-no-tenant",
                  "payload": {
                    "accountId": "acc-005",
                    "anonymized": true
                  }
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(record(json)))
                .isInstanceOf(MissingTenantIdException.class);

        verifyNoInteractions(piiMaskingService);
    }

    @Test
    @DisplayName("Missing accountId (anonymized=true) throws IllegalArgumentException (routes to DLQ)")
    void missingAccountId_anonymizedTrue_throws() {
        String json = """
                {
                  "eventId": "evt-no-account",
                  "payload": {
                    "tenantId": "fan-platform",
                    "anonymized": true
                  }
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(record(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountId");

        verifyNoInteractions(piiMaskingService);
    }

    @Test
    @DisplayName("Malformed JSON throws RuntimeException (routes to DLQ)")
    void malformedJson_throws() {
        assertThatThrownBy(() -> consumer.onMessage(record("not-json{{{")))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(piiMaskingService);
    }
}
