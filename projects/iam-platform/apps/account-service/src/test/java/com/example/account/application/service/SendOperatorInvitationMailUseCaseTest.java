package com.example.account.application.service;

import com.example.account.application.command.SendOperatorInvitationMailCommand;
import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.exception.InvitationEmailSendFailedException;
import com.example.account.application.port.OperatorInvitationNotifier;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/** TASK-MONO-772 S2 — the invitation mail: company name, failure kinds, nothing stored. */
@ExtendWith(MockitoExtension.class)
@DisplayName("SendOperatorInvitationMailUseCase — 운영자 초대 메일 (TASK-MONO-772 S2)")
class SendOperatorInvitationMailUseCaseTest {

    private static final Instant EXP = Instant.parse("2026-10-17T10:00:00Z");

    @Mock OperatorInvitationNotifier notifier;
    @Mock TenantRepository tenantRepository;
    @InjectMocks SendOperatorInvitationMailUseCase useCase;

    private static SendOperatorInvitationMailCommand cmd() {
        return new SendOperatorInvitationMailCommand("person@example.com", "raw-token-xyz", "acme-corp", "김관리", EXP);
    }

    @Test
    @DisplayName("회사 이름 = tenants.display_name, 토큰은 notifier 에 그대로 · 명령의 toString 은 토큰·주소를 감춘다")
    void sends_withCompanyName() {
        given(tenantRepository.findById(new TenantId("acme-corp"))).willReturn(Optional.of(Tenant.reconstitute(
                new TenantId("acme-corp"), "에이크미", TenantType.B2B_ENTERPRISE, TenantStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH)));
        useCase.send(cmd());
        verify(notifier).sendOperatorInvitation("person@example.com", "raw-token-xyz", "에이크미", "김관리", EXP);
        assertThat(cmd().toString()).doesNotContain("raw-token-xyz").doesNotContain("person@");
    }

    @Test
    @DisplayName("테넌트 없음 · 조회 실패 → 회사 이름 = tenantId (메일은 나간다)")
    void companyName_fallsBack() {
        given(tenantRepository.findById(new TenantId("acme-corp"))).willReturn(Optional.empty());
        assertThat(useCase.companyName("acme-corp")).isEqualTo("acme-corp");
        given(tenantRepository.findById(new TenantId("acme-corp"))).willThrow(new IllegalStateException("db down"));
        assertThat(useCase.companyName("acme-corp")).isEqualTo("acme-corp");
        assertThat(useCase.companyName("NOT A VALID ID")).isEqualTo("NOT A VALID ID");
    }

    @Test
    @DisplayName("영구 실패 → PERMANENT · 일시 실패 → TRANSIENT · 분류 못 하는 예외 → TRANSIENT")
    void failureKinds() {
        given(tenantRepository.findById(any())).willReturn(Optional.empty());

        willThrow(new EmailDeliveryException(EmailDeliveryException.Kind.PERMANENT, "x")).given(notifier)
                .sendOperatorInvitation(anyString(), anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> useCase.send(cmd())).isInstanceOfSatisfying(InvitationEmailSendFailedException.class,
                e -> assertThat(e.getKind()).isEqualTo(EmailDeliveryException.Kind.PERMANENT));

        willThrow(new EmailDeliveryException(EmailDeliveryException.Kind.TRANSIENT, "x")).given(notifier)
                .sendOperatorInvitation(anyString(), anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> useCase.send(cmd())).isInstanceOfSatisfying(InvitationEmailSendFailedException.class,
                e -> assertThat(e.getKind()).isEqualTo(EmailDeliveryException.Kind.TRANSIENT));

        willThrow(new IllegalStateException("broken adapter")).given(notifier)
                .sendOperatorInvitation(anyString(), anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> useCase.send(cmd())).isInstanceOfSatisfying(InvitationEmailSendFailedException.class,
                e -> assertThat(e.getKind()).isEqualTo(EmailDeliveryException.Kind.TRANSIENT));
    }
}
