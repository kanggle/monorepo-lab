package com.example.account.application.service;

import com.example.account.application.command.BulkProvisionAccountCommand;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.BulkLimitExceededException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.result.BulkProvisionAccountResult;
import com.example.account.application.result.ProvisionAccountResult;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("BulkProvisionAccountUseCase — TASK-BE-257")
class BulkProvisionAccountUseCaseTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @Mock private RowProvisioningHelper rowProvisioningHelper;

    @InjectMocks private BulkProvisionAccountUseCase useCase;

    private static final String TENANT_ID = "wms";

    private Tenant activeTenant() {
        return Tenant.reconstitute(new TenantId(TENANT_ID), "WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private Tenant suspendedTenant() {
        return Tenant.reconstitute(new TenantId(TENANT_ID), "WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.SUSPENDED, Instant.now(), Instant.now());
    }

    private BulkProvisionAccountCommand.Item item(String email) {
        return new BulkProvisionAccountCommand.Item("ext-" + email, email, null, "Test User",
                List.of("WAREHOUSE_ADMIN"), "ACTIVE");
    }

    private ProvisionAccountResult successResult(String email) {
        return new ProvisionAccountResult("acc-" + email, TENANT_ID, email, "ACTIVE",
                List.of("WAREHOUSE_ADMIN"), Instant.now());
    }

    // ── Tenant guard tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("테넌트가 없으면 TenantNotFoundException 발생, 행 처리 없음")
    void execute_tenantNotFound_throwsAndNoRowsProcessed() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.empty());

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item("a@example.com")), null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TenantNotFoundException.class);

        verify(rowProvisioningHelper, never()).provisionRow(any(), any(), any());
    }

    @Test
    @DisplayName("테넌트 SUSPENDED이면 TenantSuspendedException 발생")
    void execute_tenantSuspended_throwsTenantSuspendedException() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(suspendedTenant()));

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item("a@example.com")), null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(TenantSuspendedException.class);

        verify(rowProvisioningHelper, never()).provisionRow(any(), any(), any());
    }

    // ── Limit guard test ──────────────────────────────────────────────────────

    @Test
    @DisplayName("1001건 요청 시 BulkLimitExceededException 발생")
    void execute_over1000Items_throwsBulkLimitExceeded() {
        List<BulkProvisionAccountCommand.Item> items = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            items.add(item("user" + i + "@example.com"));
        }

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(TENANT_ID, items, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BulkLimitExceededException.class)
                .hasMessageContaining("1001")
                .hasMessageContaining("1000");

        verify(tenantRepository, never()).findById(any());
        verify(rowProvisioningHelper, never()).provisionRow(any(), any(), any());
    }

    @Test
    @DisplayName("정확히 1000건은 허용된다")
    void execute_exactly1000Items_doesNotThrowLimit() {
        List<BulkProvisionAccountCommand.Item> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add(item("user" + i + "@example.com"));
        }

        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), any(), any()))
                .willAnswer(inv -> {
                    BulkProvisionAccountCommand.Item item = inv.getArgument(1);
                    return successResult(item.email());
                });
        given(historyRepository.save(any())).willReturn(null);

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(TENANT_ID, items, null);
        BulkProvisionAccountResult result = useCase.execute(command);

        assertThat(result.summary().requested()).isEqualTo(1000);
        assertThat(result.summary().created()).isEqualTo(1000);
        assertThat(result.summary().failed()).isEqualTo(0);
    }

    // ── Empty items ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 items 배열이면 200 + 빈 결과 반환, audit 기록 없음")
    void execute_emptyItems_returnsEmptySummary() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(), null);

        BulkProvisionAccountResult result = useCase.execute(command);

        assertThat(result.summary().requested()).isEqualTo(0);
        assertThat(result.summary().created()).isEqualTo(0);
        assertThat(result.summary().failed()).isEqualTo(0);
        assertThat(result.created()).isEmpty();
        assertThat(result.failed()).isEmpty();
        // No audit row written for empty calls
        verify(historyRepository, never()).save(any());
        verify(rowProvisioningHelper, never()).provisionRow(any(), any(), any());
    }

    // ── Partial success ───────────────────────────────────────────────────────

    @Test
    @DisplayName("3건 중 1건 EMAIL_DUPLICATE → created=2, failed=1, summary 정확")
    void execute_oneEmailDuplicate_partialSuccess() {
        BulkProvisionAccountCommand.Item item1 = item("a@example.com");
        BulkProvisionAccountCommand.Item item2 = item("b@example.com");
        BulkProvisionAccountCommand.Item item3 = item("c@example.com");

        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item1), any()))
                .willReturn(successResult("a@example.com"));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item2), any()))
                .willThrow(new AccountAlreadyExistsException("b@example.com"));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item3), any()))
                .willReturn(successResult("c@example.com"));
        given(historyRepository.save(any())).willReturn(null);

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item1, item2, item3), "sys-wms");

        BulkProvisionAccountResult result = useCase.execute(command);

        assertThat(result.summary().requested()).isEqualTo(3);
        assertThat(result.summary().created()).isEqualTo(2);
        assertThat(result.summary().failed()).isEqualTo(1);

        assertThat(result.created()).hasSize(2);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).errorCode()).isEqualTo("EMAIL_DUPLICATE");
        assertThat(result.failed().get(0).externalId()).isEqualTo(item2.externalId());
    }

    @Test
    @DisplayName("모든 행 성공 시 N건 created, 0건 failed, 1건 audit 기록")
    void execute_allSuccess_auditWrittenOnce() {
        BulkProvisionAccountCommand.Item item1 = item("a@example.com");
        BulkProvisionAccountCommand.Item item2 = item("b@example.com");

        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item1), any()))
                .willReturn(successResult("a@example.com"));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item2), any()))
                .willReturn(successResult("b@example.com"));
        given(historyRepository.save(any())).willReturn(null);

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item1, item2), null);

        BulkProvisionAccountResult result = useCase.execute(command);

        assertThat(result.summary().created()).isEqualTo(2);
        assertThat(result.summary().failed()).isEqualTo(0);
        // One audit row per bulk call, not one per row
        verify(historyRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("행 처리 순서가 입력 순서와 동일하다")
    void execute_preservesOrderOfInputItems() {
        BulkProvisionAccountCommand.Item item1 = item("first@example.com");
        BulkProvisionAccountCommand.Item item2 = item("second@example.com");
        BulkProvisionAccountCommand.Item item3 = item("third@example.com");

        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item1), any()))
                .willReturn(successResult("first@example.com"));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item2), any()))
                .willReturn(successResult("second@example.com"));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item3), any()))
                .willReturn(successResult("third@example.com"));
        given(historyRepository.save(any())).willReturn(null);

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item1, item2, item3), null);

        BulkProvisionAccountResult result = useCase.execute(command);

        assertThat(result.created()).extracting(BulkProvisionAccountResult.CreatedItem::externalId)
                .containsExactly(item1.externalId(), item2.externalId(), item3.externalId());
    }

    // ── IllegalArgumentException (role name) ─────────────────────────────────

    @Test
    @DisplayName("역할명 검증 실패는 INVALID_ROLE errorCode로 failed에 기록된다")
    void execute_invalidRoleName_classifiedAsInvalidRole() {
        BulkProvisionAccountCommand.Item item1 = item("a@example.com");

        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item1), any()))
                .willThrow(new IllegalArgumentException("roleName must match pattern ^[A-Z][A-Z0-9_]*$: invalid-role"));
        given(historyRepository.save(any())).willReturn(null);

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item1), null);

        BulkProvisionAccountResult result = useCase.execute(command);

        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).errorCode()).isEqualTo("INVALID_ROLE");
    }

    // ── Regression: TASK-MONO-023a — bulk audit details must be valid JSON ────

    @Test
    @DisplayName("bulk audit 엔트리의 details 필드가 유효한 JSON 형식이다 (MySQL JSON 컬럼 호환)")
    void execute_bulkAuditEntry_detailsIsValidJson() {
        BulkProvisionAccountCommand.Item item1 = item("a@example.com");
        BulkProvisionAccountCommand.Item item2 = item("b@example.com");

        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item1), any()))
                .willReturn(successResult("a@example.com"));
        given(rowProvisioningHelper.provisionRow(eq(TENANT_ID), eq(item2), any()))
                .willReturn(successResult("b@example.com"));

        ArgumentCaptor<AccountStatusHistoryEntry> historyCaptor =
                ArgumentCaptor.forClass(AccountStatusHistoryEntry.class);
        given(historyRepository.save(historyCaptor.capture())).willReturn(null);

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                TENANT_ID, List.of(item1, item2), "sys-wms");

        useCase.execute(command);

        AccountStatusHistoryEntry captured = historyCaptor.getValue();
        String details = captured.getDetails();
        assertThat(details).isNotNull();
        // Must be a JSON object
        assertThat(details.trim()).startsWith("{");
        assertThat(details.trim()).endsWith("}");
        // Must contain action key and targetCount
        assertThat(details).contains("\"action\"");
        assertThat(details).contains("ACCOUNT_BULK_CREATE");
        assertThat(details).contains("\"targetCount\"");
        assertThat(details).contains("2");
    }
}
