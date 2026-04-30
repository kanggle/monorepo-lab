package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.infrastructure.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("GdprAdminUseCase 단위 테스트")
class GdprAdminUseCaseTest {

    @Mock
    AccountServiceClient accountServiceClient;

    @Mock
    AdminActionAuditor auditor;

    @InjectMocks
    GdprAdminUseCase useCase;

    private OperatorContext operator() {
        return new OperatorContext("op-1", "jti-1");
    }

    @Nested
    @DisplayName("gdprDelete")
    class GdprDelete {

        @Test
        @DisplayName("정상 처리 시 recordStart → downstream 호출 → SUCCESS 완료가 순서대로 기록되고 결과를 반환한다")
        void gdprDelete_success_records_start_then_success_completion_and_returns_result() {
            when(auditor.newAuditId()).thenReturn("audit-1");
            Instant maskedAt = Instant.parse("2026-04-25T10:00:00Z");
            when(accountServiceClient.gdprDelete(anyString(), anyString(), anyString()))
                    .thenReturn(new AccountServiceClient.GdprDeleteResponse(
                            "acc-1", "DELETED", "hash-abc", maskedAt));

            GdprDeleteResult r = useCase.gdprDelete(new GdprDeleteCommand(
                    "acc-1", "user requested deletion", "T-1", "idemp-1", operator()));

            assertThat(r.accountId()).isEqualTo("acc-1");
            assertThat(r.status()).isEqualTo("DELETED");
            assertThat(r.maskedAt()).isEqualTo(maskedAt);
            assertThat(r.auditId()).isEqualTo("audit-1");

            // Critical: recordStart must run BEFORE downstream call, then recordCompletion(SUCCESS).
            InOrder order = inOrder(auditor, accountServiceClient);
            order.verify(auditor).recordStart(any());
            order.verify(accountServiceClient).gdprDelete(anyString(), anyString(), anyString());
            ArgumentCaptor<AdminActionAuditor.CompletionRecord> captor =
                    ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
            order.verify(auditor).recordCompletion(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.GDPR_DELETE);
            assertThat(captor.getValue().downstreamDetail()).isNull();
        }

        @Test
        @DisplayName("downstream maskedAt 이 null 이면 useCase 가 completedAt 을 사용한다")
        void gdprDelete_downstream_maskedAt_null_uses_completedAt() {
            when(auditor.newAuditId()).thenReturn("audit-mask-null");
            when(accountServiceClient.gdprDelete(anyString(), anyString(), anyString()))
                    .thenReturn(new AccountServiceClient.GdprDeleteResponse(
                            "acc-1", "DELETED", "hash-abc", null));

            GdprDeleteResult r = useCase.gdprDelete(new GdprDeleteCommand(
                    "acc-1", "user requested deletion", null, "idemp-mask-null", operator()));

            assertThat(r.maskedAt()).isNotNull();
        }

        @Test
        @DisplayName("reason 이 null 이면 ReasonRequiredException 을 던지고 downstream 을 호출하지 않는다")
        void gdprDelete_missing_reason_throws_reason_required_before_any_audit() {
            assertThatThrownBy(() -> useCase.gdprDelete(new GdprDeleteCommand(
                    "acc-1", null, null, "idemp-2", operator())))
                    .isInstanceOf(ReasonRequiredException.class);

            verify(accountServiceClient, never())
                    .gdprDelete(anyString(), anyString(), anyString());
            verify(auditor, never()).recordStart(any());
            verify(auditor, never()).recordCompletion(any());
        }

        @Test
        @DisplayName("reason 이 공백 문자열이면 ReasonRequiredException 을 던진다")
        void gdprDelete_blank_reason_throws_reason_required() {
            assertThatThrownBy(() -> useCase.gdprDelete(new GdprDeleteCommand(
                    "acc-1", "   ", null, "idemp-blank", operator())))
                    .isInstanceOf(ReasonRequiredException.class);

            verify(accountServiceClient, never())
                    .gdprDelete(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("DownstreamFailureException 발생 시 FAILURE 완료를 기록하고 재throw 한다")
        void gdprDelete_downstream_failure_records_failure_completion_and_rethrows() {
            when(auditor.newAuditId()).thenReturn("audit-3");
            doThrow(new DownstreamFailureException("account-service unavailable"))
                    .when(accountServiceClient).gdprDelete(anyString(), anyString(), anyString());

            assertThatThrownBy(() -> useCase.gdprDelete(new GdprDeleteCommand(
                    "acc-1", "user requested deletion", null, "idemp-3", operator())))
                    .isInstanceOf(DownstreamFailureException.class);

            ArgumentCaptor<AdminActionAuditor.CompletionRecord> captor =
                    ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
            verify(auditor, times(1)).recordStart(any());
            verify(auditor, times(1)).recordCompletion(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
            assertThat(captor.getValue().downstreamDetail()).isEqualTo("account-service unavailable");
        }

        @Test
        @DisplayName("CallNotPermittedException(circuit OPEN) 발생 시 CIRCUIT_OPEN prefix 의 FAILURE 완료를 기록하고 재throw 한다")
        void gdprDelete_circuit_open_records_failure_completion_with_circuit_open_prefix() {
            when(auditor.newAuditId()).thenReturn("audit-cb");
            CallNotPermittedException cbEx = CallNotPermittedException.createCallNotPermittedException(
                    CircuitBreaker.of("accountService", CircuitBreakerConfig.ofDefaults()));
            doThrow(cbEx).when(accountServiceClient)
                    .gdprDelete(anyString(), anyString(), anyString());

            assertThatExceptionOfType(CallNotPermittedException.class)
                    .isThrownBy(() -> useCase.gdprDelete(new GdprDeleteCommand(
                            "acc-1", "user requested deletion", null, "idemp-cb", operator())));

            ArgumentCaptor<AdminActionAuditor.CompletionRecord> captor =
                    ArgumentCaptor.forClass(AdminActionAuditor.CompletionRecord.class);
            verify(auditor, times(1)).recordStart(any());
            verify(auditor, times(1)).recordCompletion(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
            assertThat(captor.getValue().downstreamDetail()).startsWith("CIRCUIT_OPEN: ");
        }
    }

    @Nested
    @DisplayName("dataExport")
    class DataExport {

        @Test
        @DisplayName("정상 처리 시 SUCCESS meta-audit 를 기록하고 DataExportResult 를 반환한다")
        void dataExport_success_records_meta_audit_and_returns_result() {
            when(auditor.newAuditId()).thenReturn("audit-export-1");
            Instant exportedAt = Instant.parse("2026-04-25T10:00:00Z");
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
            AccountServiceClient.DataExportProfile profile =
                    new AccountServiceClient.DataExportProfile(
                            "Jane", "+82-10-0000-0000", "1990-01-15", "ko-KR", "Asia/Seoul");
            when(accountServiceClient.export(anyString(), anyString()))
                    .thenReturn(new AccountServiceClient.DataExportResponse(
                            "acc-1", "user@example.com", "ACTIVE", createdAt, profile, exportedAt));

            DataExportResult r = useCase.dataExport("acc-1", operator(), "subject access request");

            assertThat(r.accountId()).isEqualTo("acc-1");
            assertThat(r.email()).isEqualTo("user@example.com");
            assertThat(r.status()).isEqualTo("ACTIVE");
            assertThat(r.createdAt()).isEqualTo(createdAt);
            assertThat(r.exportedAt()).isEqualTo(exportedAt);
            assertThat(r.profile()).isNotNull();
            assertThat(r.profile().displayName()).isEqualTo("Jane");
            assertThat(r.profile().phoneNumber()).isEqualTo("+82-10-0000-0000");
            assertThat(r.profile().birthDate()).isEqualTo("1990-01-15");
            assertThat(r.profile().locale()).isEqualTo("ko-KR");
            assertThat(r.profile().timezone()).isEqualTo("Asia/Seoul");

            ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                    ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
            verify(auditor).record(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.DATA_EXPORT);
            assertThat(captor.getValue().downstreamDetail()).isNull();
        }

        @Test
        @DisplayName("downstream profile 이 null 이면 결과의 profile 도 null 로 반환된다")
        void dataExport_null_profile_returns_null_profile() {
            when(auditor.newAuditId()).thenReturn("audit-export-null-profile");
            Instant exportedAt = Instant.parse("2026-04-25T10:00:00Z");
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
            when(accountServiceClient.export(anyString(), anyString()))
                    .thenReturn(new AccountServiceClient.DataExportResponse(
                            "acc-1", "user@example.com", "ACTIVE", createdAt, null, exportedAt));

            DataExportResult r = useCase.dataExport("acc-1", operator(), "subject access request");

            assertThat(r.profile()).isNull();
        }

        @Test
        @DisplayName("reason 이 null 이면 ReasonRequiredException 을 던지고 downstream 을 호출하지 않는다")
        void dataExport_missing_reason_throws_reason_required() {
            assertThatThrownBy(() -> useCase.dataExport("acc-1", operator(), null))
                    .isInstanceOf(ReasonRequiredException.class);

            verify(accountServiceClient, never()).export(anyString(), anyString());
            verify(auditor, never()).record(any());
        }

        @Test
        @DisplayName("reason 이 공백 문자열이면 ReasonRequiredException 을 던진다")
        void dataExport_blank_reason_throws_reason_required() {
            assertThatThrownBy(() -> useCase.dataExport("acc-1", operator(), "   "))
                    .isInstanceOf(ReasonRequiredException.class);

            verify(accountServiceClient, never()).export(anyString(), anyString());
        }

        @Test
        @DisplayName("DownstreamFailureException 발생 시 FAILURE meta-audit 를 기록하고 재throw 한다")
        void dataExport_downstream_failure_records_failure_meta_audit_and_rethrows() {
            when(auditor.newAuditId()).thenReturn("audit-export-fail");
            doThrow(new DownstreamFailureException("account-service unavailable"))
                    .when(accountServiceClient).export(anyString(), anyString());

            assertThatThrownBy(() -> useCase.dataExport("acc-1", operator(), "subject access request"))
                    .isInstanceOf(DownstreamFailureException.class);

            ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                    ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
            verify(auditor, times(1)).record(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
            assertThat(captor.getValue().downstreamDetail()).isEqualTo("account-service unavailable");
        }

        @Test
        @DisplayName("CallNotPermittedException(circuit OPEN) 발생 시 CIRCUIT_OPEN prefix 의 FAILURE meta-audit 를 기록하고 재throw 한다")
        void dataExport_circuit_open_records_failure_meta_audit_with_circuit_open_prefix() {
            when(auditor.newAuditId()).thenReturn("audit-export-cb");
            CallNotPermittedException cbEx = CallNotPermittedException.createCallNotPermittedException(
                    CircuitBreaker.of("accountService", CircuitBreakerConfig.ofDefaults()));
            doThrow(cbEx).when(accountServiceClient).export(anyString(), anyString());

            assertThatExceptionOfType(CallNotPermittedException.class)
                    .isThrownBy(() -> useCase.dataExport("acc-1", operator(), "subject access request"));

            ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                    ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
            verify(auditor, times(1)).record(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
            assertThat(captor.getValue().downstreamDetail()).startsWith("CIRCUIT_OPEN: ");
        }
    }
}
