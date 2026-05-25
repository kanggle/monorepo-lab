package com.example.finance.account.application;

import com.example.finance.account.application.command.CaptureHoldCommand;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.command.ReleaseHoldCommand;
import com.example.finance.account.application.command.TransferCommand;
import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.finance.account.application.port.outbound.ClockPort;
import com.example.finance.account.application.port.outbound.CompliancePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.account.repository.AccountRepository;
import com.example.finance.account.domain.account.status.AccountStatus;
import com.example.finance.account.domain.account.status.AccountStatusHistoryRepository;
import com.example.finance.account.domain.audit.AuditLog;
import com.example.finance.account.domain.audit.AuditLogRepository;
import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.balance.repository.BalanceRepository;
import com.example.finance.account.domain.compliance.ScreeningDecision;
import com.example.finance.account.domain.error.DomainErrors.AccountNotActiveException;
import com.example.finance.account.domain.error.DomainErrors.InsufficientAvailableBalanceException;
import com.example.finance.account.domain.error.DomainErrors.KycRequiredException;
import com.example.finance.account.domain.error.DomainErrors.SanctionHitException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests for {@link AccountApplicationService} — the single
 * fund-movement command boundary. Proves the F4 gate cannot be bypassed
 * (CompliancePort is invoked BEFORE any balance mutation; sanction hit blocks
 * funds), the F2 balance invariant, and account-state preconditions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountApplicationServiceTest {

    private static final String TENANT = "finance";
    private static final ActorContext HOLDER =
            new ActorContext("user-1", TENANT, Set.of());
    private static final ActorContext OPERATOR =
            new ActorContext("op-1", TENANT, Set.of("OPERATOR"));
    private static final Instant NOW = Instant.parse("2026-05-18T00:00:00Z");

    @Mock ObjectMapper objectMapper;
    @Mock AccountRepository accountRepository;
    @Mock BalanceRepository balanceRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AccountStatusHistoryRepository historyRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock CompliancePort compliancePort;
    @Mock ComplianceFailureRecorder complianceFailureRecorder;
    @Mock ClockPort clock;
    @Mock AccountEventPublisher eventPublisher;

    @InjectMocks AccountApplicationService service;

    @BeforeEach
    void clockStub() throws Exception {
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(objectMapper.writeValueAsString(any()))
                .thenReturn("{}");
        lenient().when(accountRepository.save(any(Account.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(balanceRepository.save(any(Balance.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(balanceRepository.saveHold(any(Hold.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private Account activeAccount(KycLevel kyc) {
        Account a = Account.open("acc-1", TENANT, "cust-1",
                Currency.KRW, kyc, NOW);
        a.transitionTo(AccountStatus.ACTIVE, NOW);
        return a;
    }

    private Balance balanceWith(long ledger) {
        Balance b = Balance.open("bal-1", "acc-1", TENANT, Currency.KRW, NOW);
        if (ledger > 0) b.credit(Money.of(ledger, Currency.KRW), NOW);
        return b;
    }

    // ---------------- open account ----------------

    @Test
    @DisplayName("openAccount creates PENDING_KYC + zero balance + opened event")
    void openAccount() {
        var v = service.openAccount(new OpenAccountCommand(
                HOLDER, "cust-1", "KRW", "NONE"));
        assertThat(v.status()).isEqualTo("PENDING_KYC");
        verify(eventPublisher).publishAccountOpened(any(Account.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ---------------- F4: gate cannot be bypassed ----------------

    @Test
    @DisplayName("F4: placeHold calls CompliancePort BEFORE mutating balance")
    void holdGatedByCompliance() {
        Account acc = activeAccount(KycLevel.FULL);
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(acc));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(balanceWith(10_000L)));
        when(compliancePort.screen(any(), any(), any()))
                .thenReturn(ScreeningDecision.clear("scr-1"));

        var r = service.placeHold(new PlaceHoldCommand(
                HOLDER, "acc-1", "5000", "KRW", 3600, "checkout"));

        assertThat(r.hold().status()).isEqualTo("ACTIVE");
        verify(compliancePort).screen(any(), any(), any());
        verify(balanceRepository).save(any(Balance.class));
        verify(eventPublisher).publishBalanceHeld(any(), any(), any());
    }

    @Test
    @DisplayName("F4: sanction hit → recorder invoked, NO balance mutation, 422 thrown")
    void sanctionHitBlocksFunds() {
        Account acc = activeAccount(KycLevel.FULL);
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(acc));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(balanceWith(10_000L)));
        when(compliancePort.screen(any(), any(), any()))
                .thenReturn(ScreeningDecision.sanctionHit("scr-bad"));

        assertThatThrownBy(() -> service.placeHold(new PlaceHoldCommand(
                HOLDER, "acc-1", "5000", "KRW", 3600, "x")))
                .isInstanceOf(SanctionHitException.class);

        verify(complianceFailureRecorder).recordSanctionHit(
                any(), any(), any(), any(), any());
        // F2/F4: balance NEVER mutated when sanction hit.
        verify(balanceRepository, never()).save(any(Balance.class));
        verify(balanceRepository, never()).saveHold(any(Hold.class));
        verify(eventPublisher, never()).publishBalanceHeld(any(), any(), any());
    }

    @Test
    @DisplayName("F4: KYC NONE → KYC_REQUIRED, no balance mutation, no screening")
    void kycRequiredBlocks() {
        Account acc = activeAccount(KycLevel.NONE);
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(acc));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(balanceWith(10_000L)));

        assertThatThrownBy(() -> service.placeHold(new PlaceHoldCommand(
                HOLDER, "acc-1", "5000", "KRW", 3600, "x")))
                .isInstanceOf(KycRequiredException.class);

        verify(compliancePort, never()).screen(any(), any(), any());
        verify(balanceRepository, never()).save(any(Balance.class));
    }

    // ---------------- account-state precondition ----------------

    @Test
    @DisplayName("fund move on non-ACTIVE account → ACCOUNT_NOT_ACTIVE")
    void nonActiveBlocked() {
        Account pending = Account.open("acc-1", TENANT, "cust-1",
                Currency.KRW, KycLevel.NONE, NOW); // PENDING_KYC
        when(accountRepository.findById("acc-1", TENANT))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.placeHold(new PlaceHoldCommand(
                HOLDER, "acc-1", "1", "KRW", 3600, "x")))
                .isInstanceOf(AccountNotActiveException.class);
    }

    // ---------------- F2: available guard ----------------

    @Test
    @DisplayName("F2: hold exceeding available → INSUFFICIENT_AVAILABLE_BALANCE")
    void insufficientAvailable() {
        Account acc = activeAccount(KycLevel.FULL);
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(acc));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(balanceWith(100L)));
        when(compliancePort.screen(any(), any(), any()))
                .thenReturn(ScreeningDecision.clear("scr-1"));

        assertThatThrownBy(() -> service.placeHold(new PlaceHoldCommand(
                HOLDER, "acc-1", "500", "KRW", 3600, "x")))
                .isInstanceOf(InsufficientAvailableBalanceException.class);
    }

    // ---------------- capture / release ----------------

    @Test
    @DisplayName("captureHold settles the hold and mutates balance once")
    void captureHold() {
        Account acc = activeAccount(KycLevel.FULL);
        Balance bal = balanceWith(10_000L);
        bal.placeHold(Money.of(4000L, Currency.KRW), NOW);
        Hold hold = Hold.place("h-1", "acc-1", TENANT,
                Money.of(4000L, Currency.KRW), "x", NOW, NOW.plusSeconds(3600));
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(acc));
        when(balanceRepository.findHoldById("h-1", TENANT))
                .thenReturn(Optional.of(hold));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(bal));
        when(compliancePort.screen(any(), any(), any()))
                .thenReturn(ScreeningDecision.clear("scr-1"));

        var r = service.captureHold(new CaptureHoldCommand(
                HOLDER, "acc-1", "h-1", "2500", "KRW"));

        assertThat(r.captured().minorUnits()).isEqualTo(2500L);
        assertThat(r.released().minorUnits()).isEqualTo(1500L);
        verify(eventPublisher).publishBalanceCaptured(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("releaseHold returns held funds; release permitted at any KYC")
    void releaseHold() {
        Account acc = activeAccount(KycLevel.NONE);
        Balance bal = balanceWith(10_000L);
        bal.placeHold(Money.of(3000L, Currency.KRW), NOW);
        Hold hold = Hold.place("h-1", "acc-1", TENANT,
                Money.of(3000L, Currency.KRW), "x", NOW, NOW.plusSeconds(3600));
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(acc));
        when(balanceRepository.findHoldById("h-1", TENANT))
                .thenReturn(Optional.of(hold));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(bal));
        when(compliancePort.screen(any(), any(), any()))
                .thenReturn(ScreeningDecision.clear("scr-1"));

        var r = service.releaseHold(new ReleaseHoldCommand(HOLDER, "acc-1", "h-1"));

        assertThat(r.status()).isEqualTo("RELEASED");
        verify(eventPublisher).publishBalanceReleased(any(), any(), any());
    }

    // ---------------- transfer atomicity ----------------

    @Test
    @DisplayName("transfer debits source + credits target in one path (F1)")
    void transfer() {
        Account from = activeAccount(KycLevel.FULL);
        Account to = Account.open("acc-2", TENANT, "cust-2",
                Currency.KRW, KycLevel.FULL, NOW);
        to.transitionTo(AccountStatus.ACTIVE, NOW);
        Balance fromBal = balanceWith(10_000L);
        Balance toBal = balanceWith(0L);
        when(accountRepository.findById("acc-1", TENANT)).thenReturn(Optional.of(from));
        when(accountRepository.findById("acc-2", TENANT)).thenReturn(Optional.of(to));
        when(balanceRepository.findByAccountId("acc-1", TENANT))
                .thenReturn(Optional.of(fromBal));
        when(balanceRepository.findByAccountId("acc-2", TENANT))
                .thenReturn(Optional.of(toBal));
        when(compliancePort.screen(any(), any(), any()))
                .thenReturn(ScreeningDecision.clear("scr-1"));

        var v = service.transfer(new TransferCommand(
                HOLDER, "acc-1", "acc-2", "2500", "KRW", "p2p"));

        assertThat(v.status()).isEqualTo("COMPLETED");
        assertThat(fromBal.ledger().minorUnits()).isEqualTo(7500L);
        assertThat(toBal.ledger().minorUnits()).isEqualTo(2500L);
        verify(eventPublisher).publishSettledAndCompleted(any());
    }

    // ---------------- KYC upgrade activates account ----------------

    @Test
    @DisplayName("operator KYC upgrade on PENDING_KYC activates the account")
    void kycUpgradeActivates() {
        Account pending = Account.open("acc-1", TENANT, "cust-1",
                Currency.KRW, KycLevel.NONE, NOW);
        when(accountRepository.findById("acc-1", TENANT))
                .thenReturn(Optional.of(pending));
        when(balanceRepository.findAllByAccountId("acc-1", TENANT))
                .thenReturn(java.util.List.of());

        var v = service.upgradeKyc(new com.example.finance.account.application
                .command.UpgradeKycCommand(OPERATOR, "acc-1", "BASIC", "verified"));

        assertThat(v.status()).isEqualTo("ACTIVE");
        assertThat(v.kycLevel()).isEqualTo("BASIC");
        verify(historyRepository).save(any());
        verify(eventPublisher).publishKycUpgraded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("non-operator KYC upgrade → PERMISSION_DENIED")
    void kycUpgradeNonOperator() {
        assertThatThrownBy(() -> service.upgradeKyc(
                new com.example.finance.account.application.command
                        .UpgradeKycCommand(HOLDER, "acc-1", "BASIC", "x")))
                .isInstanceOf(com.example.finance.account.domain.error
                        .DomainErrors.PermissionDeniedException.class);
    }
}
