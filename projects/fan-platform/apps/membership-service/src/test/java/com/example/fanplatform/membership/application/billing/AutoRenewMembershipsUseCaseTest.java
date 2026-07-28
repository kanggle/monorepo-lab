package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.application.RenewCommand;
import com.example.fanplatform.membership.application.RenewMembershipUseCase;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PgGatewayUnavailableException;
import com.example.libs.payment.RecurringBillingGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AutoRenewMembershipsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final long PREMIUM_1M = 17_900L;

    @Mock BillingKeyEnrollmentRepository enrollmentRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock RecurringBillingGateway recurringBillingGateway;
    @Mock PaymentGatewayPort paymentGateway;
    @Mock RenewMembershipUseCase renewMembershipUseCase;
    @Captor ArgumentCaptor<RenewCommand> renewCaptor;

    private AutoRenewMembershipsUseCase useCase() {
        return new AutoRenewMembershipsUseCase(enrollmentRepository, membershipRepository,
                recurringBillingGateway, paymentGateway, renewMembershipUseCase, () -> NOW, 1L);
    }

    private static BillingKeyEnrollment enrollment(String account) {
        return BillingKeyEnrollment.enroll("enr-" + account, "fan-platform", account,
                MembershipTier.PREMIUM, "bk_" + account, NOW.minus(60, ChronoUnit.DAYS));
    }

    private static Membership premium(String id, String account, Instant validTo) {
        return Membership.activate(id, "fan-platform", account, MembershipTier.PREMIUM,
                validTo.minus(30, ChronoUnit.DAYS), validTo, 1, "pgmock_" + id, validTo.minus(30, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("due candidate + charge approved → drives RenewMembershipUseCase with the same paymentId")
    void approvedDrivesRenew() {
        BillingKeyEnrollment enr = enrollment("acc1");
        Membership current = premium("m1", "acc1", NOW); // validTo == now → due (lookahead 1d)
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of(current));
        when(recurringBillingGateway.chargeBillingKey(eq("bk_acc1"), anyString(), eq(PREMIUM_1M), eq("KRW"), anyString()))
                .thenAnswer(inv -> PaymentAuthorization.approved(inv.getArgument(1), null, null));

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isEqualTo(1);
        verify(renewMembershipUseCase).execute(renewCaptor.capture());
        RenewCommand cmd = renewCaptor.getValue();
        assertThat(cmd.priorMembershipId()).isEqualTo("m1");
        assertThat(cmd.actor().accountId()).isEqualTo("acc1");
        assertThat(cmd.actor().tenantId()).isEqualTo("fan-platform");
        assertThat(cmd.planMonths()).isEqualTo(1);
        assertThat(cmd.paymentId()).startsWith("pay-");
        assertThat(cmd.idempotencyKey()).startsWith("auto-");
    }

    @Test
    @DisplayName("candidate selection picks the greatest-validTo row per tier — never the superseded old row")
    void picksCurrentNotSupersededRow() {
        BillingKeyEnrollment enr = enrollment("acc1");
        Membership superseded = premium("old", "acc1", NOW.minus(20, ChronoUnit.DAYS)); // long past
        Membership current = premium("current", "acc1", NOW); // due
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform"))
                .thenReturn(List.of(superseded, current));
        when(recurringBillingGateway.chargeBillingKey(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> PaymentAuthorization.approved(inv.getArgument(1), null, null));

        useCase().runOnce(100);

        verify(renewMembershipUseCase).execute(renewCaptor.capture());
        // The renewed prior is the max-validTo (current) row, NOT the superseded past one.
        assertThat(renewCaptor.getValue().priorMembershipId()).isEqualTo("current");
    }

    @Test
    @DisplayName("not-due candidate (validTo beyond look-ahead) → no charge, no renew")
    void notDueSkipped() {
        BillingKeyEnrollment enr = enrollment("acc1");
        Membership current = premium("m1", "acc1", NOW.plus(10, ChronoUnit.DAYS)); // beyond 1d lookahead
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of(current));

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isZero();
        verify(recurringBillingGateway, never()).chargeBillingKey(any(), any(), anyLong(), any(), any());
        verify(renewMembershipUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("no membership for the enrolled tier → skip (never subscribed / canceled)")
    void noMembershipSkipped() {
        BillingKeyEnrollment enr = enrollment("acc1");
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of());

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isZero();
        verify(recurringBillingGateway, never()).chargeBillingKey(any(), any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("charge declined → no renewal (fail-closed)")
    void declinedNoRenew() {
        BillingKeyEnrollment enr = enrollment("acc1");
        Membership current = premium("m1", "acc1", NOW);
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of(current));
        when(recurringBillingGateway.chargeBillingKey(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(PaymentAuthorization.declined());

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isZero();
        verify(renewMembershipUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("ambiguous charge → reconcile via verify; approved → renew with the SAME paymentId")
    void ambiguousReconcileApprovedRenews() {
        BillingKeyEnrollment enr = enrollment("acc1");
        Membership current = premium("m1", "acc1", NOW);
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of(current));
        when(recurringBillingGateway.chargeBillingKey(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new PgGatewayUnavailableException("lost response"));
        // Reconcile: verify with the same paymentId now reports PAID.
        when(paymentGateway.verify(any())).thenAnswer(inv ->
                PaymentAuthorization.approved(inv.getArgument(0).toString(), null, null));

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isEqualTo(1);
        verify(paymentGateway).verify(any());
        verify(renewMembershipUseCase).execute(any());
    }

    @Test
    @DisplayName("ambiguous charge → reconcile inconclusive (still not paid) → no renew, defer to next tick")
    void ambiguousReconcileNotPaidDefers() {
        BillingKeyEnrollment enr = enrollment("acc1");
        Membership current = premium("m1", "acc1", NOW);
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(enr));
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of(current));
        when(recurringBillingGateway.chargeBillingKey(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new PgGatewayUnavailableException("lost response"));
        when(paymentGateway.verify(any())).thenReturn(PaymentAuthorization.declined());

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isZero();
        verify(renewMembershipUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("one candidate's exception never aborts the tick — the next candidate still renews")
    void perCandidateExceptionIsolation() {
        BillingKeyEnrollment bad = enrollment("accBad");
        BillingKeyEnrollment good = enrollment("accGood");
        Membership badCurrent = premium("mb", "accBad", NOW);
        Membership goodCurrent = premium("mg", "accGood", NOW);
        when(enrollmentRepository.findAllActive(100)).thenReturn(List.of(bad, good));
        when(membershipRepository.findActiveByAccount("accBad", "fan-platform")).thenReturn(List.of(badCurrent));
        when(membershipRepository.findActiveByAccount("accGood", "fan-platform")).thenReturn(List.of(goodCurrent));
        when(recurringBillingGateway.chargeBillingKey(eq("bk_accBad"), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));
        when(recurringBillingGateway.chargeBillingKey(eq("bk_accGood"), anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> PaymentAuthorization.approved(inv.getArgument(1), null, null));

        int renewed = useCase().runOnce(100);

        assertThat(renewed).isEqualTo(1);
        verify(renewMembershipUseCase).execute(renewCaptor.capture());
        assertThat(renewCaptor.getValue().priorMembershipId()).isEqualTo("mg");
    }
}
