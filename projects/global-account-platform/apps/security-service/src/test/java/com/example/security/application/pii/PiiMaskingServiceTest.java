package com.example.security.application.pii;

import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.pii.PiiMaskingRecord;
import com.example.security.infrastructure.persistence.PiiMaskingLogJpaEntity;
import com.example.security.infrastructure.persistence.PiiMaskingLogJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PiiMaskingServiceTest {

    private static final String TEST_SALT = "test-fingerprint-salt-TASK-BE-270";

    @Mock
    private PiiMaskingLogJpaRepository piiMaskingLogRepository;

    @Mock
    private SecurityEventPublisher eventPublisher;

    private PiiMaskingService service;

    @BeforeEach
    void setUp() {
        service = new PiiMaskingService(piiMaskingLogRepository, eventPublisher, new ObjectMapper(), TEST_SALT);
    }

    // ─── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("maskPii executes all three table UPDATEs and saves the log entry")
    void maskPii_happyPath_executesAllUpdates() {
        String eventId  = "evt-001";
        String tenantId = "fan-platform";
        String accountId = "acc-001";

        when(piiMaskingLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(piiMaskingLogRepository.maskLoginHistory(tenantId, accountId, TEST_SALT)).thenReturn(3);
        when(piiMaskingLogRepository.maskSuspiciousEvents(tenantId, accountId)).thenReturn(1);
        when(piiMaskingLogRepository.touchAccountLockHistory(tenantId, accountId)).thenReturn(2);
        when(piiMaskingLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.maskPii(eventId, tenantId, accountId);

        assertThat(result).isTrue();
        verify(piiMaskingLogRepository).maskLoginHistory(tenantId, accountId, TEST_SALT);
        verify(piiMaskingLogRepository).maskSuspiciousEvents(tenantId, accountId);
        verify(piiMaskingLogRepository).touchAccountLockHistory(tenantId, accountId);
    }

    @Test
    @DisplayName("maskLoginHistory is invoked with the configured fingerprint salt — TASK-BE-270")
    void maskPii_passesFingerprintSaltToRepository() {
        String eventId  = "evt-salt";
        String tenantId = "fan-platform";
        String accountId = "acc-salt";

        when(piiMaskingLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(piiMaskingLogRepository.maskLoginHistory(tenantId, accountId, TEST_SALT)).thenReturn(1);
        when(piiMaskingLogRepository.maskSuspiciousEvents(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.touchAccountLockHistory(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.maskPii(eventId, tenantId, accountId);

        verify(piiMaskingLogRepository).maskLoginHistory(eq(tenantId), eq(accountId), eq(TEST_SALT));
        verify(piiMaskingLogRepository, never()).maskLoginHistory(eq(tenantId), eq(accountId), eq(""));
    }

    @Test
    @DisplayName("Constructor rejects blank fingerprint salt — TASK-BE-270 fail-fast")
    void constructor_blankSalt_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new PiiMaskingService(piiMaskingLogRepository, eventPublisher, new ObjectMapper(), ""));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new PiiMaskingService(piiMaskingLogRepository, eventPublisher, new ObjectMapper(), "  "));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new PiiMaskingService(piiMaskingLogRepository, eventPublisher, new ObjectMapper(), null));
    }

    @Test
    @DisplayName("maskPii saves log entry with correct fields")
    void maskPii_savesLogEntryWithCorrectFields() {
        String eventId   = "evt-002";
        String tenantId  = "wms";
        String accountId = "acc-002";

        when(piiMaskingLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(piiMaskingLogRepository.maskLoginHistory(tenantId, accountId, TEST_SALT)).thenReturn(0);
        when(piiMaskingLogRepository.maskSuspiciousEvents(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.touchAccountLockHistory(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.maskPii(eventId, tenantId, accountId);

        ArgumentCaptor<PiiMaskingLogJpaEntity> captor =
                ArgumentCaptor.forClass(PiiMaskingLogJpaEntity.class);
        verify(piiMaskingLogRepository).save(captor.capture());
        PiiMaskingLogJpaEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getMaskedAt()).isNotNull();
        assertThat(saved.getTableNames()).contains("login_history");
    }

    @Test
    @DisplayName("maskPii publishes security.pii.masked event via eventPublisher")
    void maskPii_publishesPiiMaskedEvent() {
        String eventId   = "evt-003";
        String tenantId  = "fan-platform";
        String accountId = "acc-003";

        when(piiMaskingLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(piiMaskingLogRepository.maskLoginHistory(tenantId, accountId, TEST_SALT)).thenReturn(1);
        when(piiMaskingLogRepository.maskSuspiciousEvents(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.touchAccountLockHistory(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.maskPii(eventId, tenantId, accountId);

        ArgumentCaptor<PiiMaskingRecord> recordCaptor =
                ArgumentCaptor.forClass(PiiMaskingRecord.class);
        verify(eventPublisher).publishPiiMasked(recordCaptor.capture(), eq(eventId));
        PiiMaskingRecord published = recordCaptor.getValue();
        assertThat(published.accountId()).isEqualTo(accountId);
        assertThat(published.tenantId()).isEqualTo(tenantId);
        assertThat(published.tableNames()).containsExactlyElementsOf(PiiMaskingService.MASKED_TABLES);
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate eventId returns false and skips masking (idempotency)")
    void maskPii_duplicateEvent_skipped() {
        String eventId = "evt-dup";

        when(piiMaskingLogRepository.existsByEventId(eventId)).thenReturn(true);

        boolean result = service.maskPii(eventId, "fan-platform", "acc-dup");

        assertThat(result).isFalse();
        verify(piiMaskingLogRepository, never()).maskLoginHistory(any(), any(), any());
        verify(piiMaskingLogRepository, never()).maskSuspiciousEvents(any(), any());
        verify(piiMaskingLogRepository, never()).touchAccountLockHistory(any(), any());
        verify(piiMaskingLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishPiiMasked(any(), any());
    }

    @Test
    @DisplayName("DataIntegrityViolation on log insert (race) returns false without rethrowing")
    void maskPii_raceConditionOnLogInsert_returnsFalse() {
        String eventId   = "evt-race";
        String tenantId  = "fan-platform";
        String accountId = "acc-race";

        when(piiMaskingLogRepository.existsByEventId(eventId)).thenReturn(false);
        when(piiMaskingLogRepository.maskLoginHistory(tenantId, accountId, TEST_SALT)).thenReturn(1);
        when(piiMaskingLogRepository.maskSuspiciousEvents(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.touchAccountLockHistory(tenantId, accountId)).thenReturn(0);
        when(piiMaskingLogRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_pii_masking_log_event_id"));

        boolean result = service.maskPii(eventId, tenantId, accountId);

        assertThat(result).isFalse();
        // Masking UPDATEs were still executed (the race was on the log insert).
        verify(piiMaskingLogRepository).maskLoginHistory(tenantId, accountId, TEST_SALT);
        // Event was NOT published because the log insert was treated as duplicate.
        verify(eventPublisher, never()).publishPiiMasked(any(), any());
    }

    // ─── Cross-tenant safety (mock level) ────────────────────────────────────

    @Test
    @DisplayName("Cross-tenant: tenantA event does not trigger tenantB UPDATE calls")
    void maskPii_crossTenant_onlyCorrectTenantUpdated() {
        String eventIdA   = "evt-a";
        String tenantIdA  = "fan-platform";
        String accountIdA = "acc-cross";

        String tenantIdB  = "wms";
        String accountIdB = "acc-cross"; // same accountId, different tenant

        when(piiMaskingLogRepository.existsByEventId(eventIdA)).thenReturn(false);
        when(piiMaskingLogRepository.maskLoginHistory(tenantIdA, accountIdA, TEST_SALT)).thenReturn(2);
        when(piiMaskingLogRepository.maskSuspiciousEvents(tenantIdA, accountIdA)).thenReturn(1);
        when(piiMaskingLogRepository.touchAccountLockHistory(tenantIdA, accountIdA)).thenReturn(0);
        when(piiMaskingLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.maskPii(eventIdA, tenantIdA, accountIdA);

        // tenantB UPDATE methods must never be called.
        verify(piiMaskingLogRepository, never()).maskLoginHistory(eq(tenantIdB), eq(accountIdB), any());
        verify(piiMaskingLogRepository, never()).maskSuspiciousEvents(tenantIdB, accountIdB);
        verify(piiMaskingLogRepository, never()).touchAccountLockHistory(tenantIdB, accountIdB);
    }
}
