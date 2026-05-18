package com.example.finance.account.application;

import com.example.common.id.UuidV7;
import com.example.finance.account.application.command.CaptureHoldCommand;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.command.ReleaseHoldCommand;
import com.example.finance.account.application.command.TransferCommand;
import com.example.finance.account.application.command.UpgradeKycCommand;
import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.finance.account.application.port.outbound.ClockPort;
import com.example.finance.account.application.port.outbound.CompliancePort;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.application.view.BalanceView;
import com.example.finance.account.application.view.HoldView;
import com.example.finance.account.application.view.TransactionView;
import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.ActorType;
import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.account.repository.AccountRepository;
import com.example.finance.account.domain.account.status.AccountStatus;
import com.example.finance.account.domain.account.status.AccountStatusHistory;
import com.example.finance.account.domain.account.status.AccountStatusHistoryRepository;
import com.example.finance.account.domain.audit.AuditLog;
import com.example.finance.account.domain.audit.AuditLogRepository;
import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.balance.repository.BalanceRepository;
import com.example.finance.account.domain.compliance.KycGate;
import com.example.finance.account.domain.compliance.ScreeningDecision;
import com.example.finance.account.domain.error.DomainErrors.AccountNotFoundException;
import com.example.finance.account.domain.error.DomainErrors.AmlScreeningRequiredException;
import com.example.finance.account.domain.error.DomainErrors.HoldNotFoundException;
import com.example.finance.account.domain.error.DomainErrors.SanctionHitException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Account application service — the SINGLE {@code @Transactional} command
 * boundary and the SINGLE fund-movement path (architecture.md § Layer
 * Structure / § KYC-AML Compliance Gate, fintech F1/F2/F4).
 *
 * <p><b>F1</b> — every fund mutation runs the balance change + transaction
 * state transition + outbox event write inside ONE {@code @Transactional}
 * method; no partial fund state can commit. Endpoint-level idempotency is
 * enforced by the controller via {@code IdempotencyStore} before the command
 * reaches here.
 *
 * <p><b>F2</b> — {@code Balance} is mutated ONLY here (via the domain VO);
 * controllers never touch repositories. {@code available = ledger − held}
 * is preserved by the domain.
 *
 * <p><b>F4</b> — {@link #applyGate} (KYC limit + AML/sanction screen) runs
 * BEFORE any balance mutation in every fund operation. A SANCTION_HIT marks
 * the txn FAILED, inserts an operator-queue row (no auto-clear), emits the
 * sanction event, and aborts — funds never move.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountApplicationService {

    private static final String AGG_ACCOUNT = "account";
    private static final String AGG_BALANCE = "balance";
    private static final String AGG_TRANSACTION = "transaction";

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final CompliancePort compliancePort;
    private final ComplianceFailureRecorder complianceFailureRecorder;
    private final ClockPort clock;
    private final AccountEventPublisher eventPublisher;

    // --------------------------------------------------------------------
    // Account lifecycle
    // --------------------------------------------------------------------

    @Transactional
    public AccountView openAccount(OpenAccountCommand cmd) {
        ActorContext actor = cmd.actor();
        Instant now = clock.now();
        Currency currency = Currency.of(cmd.currency());
        KycLevel kyc = cmd.kycLevel() == null || cmd.kycLevel().isBlank()
                ? KycLevel.NONE : KycLevel.valueOf(cmd.kycLevel().trim().toUpperCase());

        String accountId = UuidV7.randomString();
        Account account = Account.open(accountId, actor.tenantId(), cmd.ownerRef(),
                currency, kyc, now);
        Account saved = accountRepository.save(account);

        Balance balance = Balance.open(UuidV7.randomString(), accountId,
                actor.tenantId(), currency, now);
        Balance savedBalance = balanceRepository.save(balance);

        auditLogRepository.save(AuditLog.of(actor.tenantId(), AGG_ACCOUNT, accountId,
                "OPEN_ACCOUNT", actor.accountId(), actor.actorType(),
                null, "{\"status\":\"PENDING_KYC\"}", null, now));

        eventPublisher.publishAccountOpened(saved);
        return AccountView.of(saved, List.of(BalanceView.from(savedBalance)));
    }

    @Transactional(readOnly = true)
    public AccountView getAccount(String accountId, ActorContext actor) {
        Account account = loadAccount(accountId, actor.tenantId());
        return AccountView.of(account, balanceViews(accountId, actor.tenantId()));
    }

    @Transactional(readOnly = true)
    public List<BalanceView> getBalances(String accountId, ActorContext actor) {
        loadAccount(accountId, actor.tenantId());
        return balanceViews(accountId, actor.tenantId());
    }

    @Transactional
    public AccountView upgradeKyc(UpgradeKycCommand cmd) {
        ActorContext actor = cmd.actor();
        if (!actor.isOperator()) {
            throw new com.example.finance.account.domain.error.DomainErrors
                    .PermissionDeniedException("KYC upgrade is operator-only");
        }
        Instant now = clock.now();
        Account account = loadAccount(cmd.accountId(), actor.tenantId());
        KycLevel toLevel = KycLevel.valueOf(cmd.toLevel().trim().toUpperCase());

        AccountStatus statusBefore = account.getStatus();
        KycLevel from = account.raiseKycLevel(toLevel, now);
        AccountStatus statusAfter = statusBefore;

        // PENDING_KYC + KYC now ≥ BASIC → activate.
        if (statusBefore == AccountStatus.PENDING_KYC && toLevel.isAtLeast(KycLevel.BASIC)) {
            account.transitionTo(AccountStatus.ACTIVE, now);
            statusAfter = AccountStatus.ACTIVE;
            accountStatusHistoryRepository.save(AccountStatusHistory.record(
                    account.getId(), account.getTenantId(), statusBefore, statusAfter,
                    ActorType.OPERATOR, actor.accountId(), cmd.reason(), now));
            eventPublisher.publishStatusChanged(account, statusBefore, statusAfter,
                    ActorType.OPERATOR, cmd.reason());
        }
        Account saved = accountRepository.save(account);

        auditLogRepository.save(AuditLog.of(actor.tenantId(), AGG_ACCOUNT, cmd.accountId(),
                "UPGRADE_KYC", actor.accountId(), ActorType.OPERATOR,
                "{\"kycLevel\":\"" + from + "\",\"status\":\"" + statusBefore + "\"}",
                "{\"kycLevel\":\"" + toLevel + "\",\"status\":\"" + statusAfter + "\"}",
                cmd.reason(), now));
        eventPublisher.publishKycUpgraded(saved, from, toLevel, statusAfter);
        return AccountView.of(saved, balanceViews(cmd.accountId(), actor.tenantId()));
    }

    // --------------------------------------------------------------------
    // Fund movement — the single gated path (F1/F2/F4)
    // --------------------------------------------------------------------

    @Transactional
    public HoldResult placeHold(PlaceHoldCommand cmd) {
        ActorContext actor = cmd.actor();
        Instant now = clock.now();
        Account account = loadAccount(cmd.accountId(), actor.tenantId());
        account.ensureFundMovementAllowed();

        Money amount = money(cmd.amountMinor(), cmd.currency());
        ensureAccountCurrency(account, amount);
        Balance balance = loadBalance(cmd.accountId(), actor.tenantId());

        Transaction txn = newTxn(actor, cmd.accountId(), TransactionType.HOLD,
                amount, null, null, cmd.reason(), now);

        // ---- F4 GATE (KYC + AML/sanction) BEFORE any balance mutation ----
        gateOrFail(account, txn, TransactionType.HOLD, amount, null, now);

        // ---- F2 balance mutation (single writer) ----
        int expiry = cmd.expiresInSeconds() == null ? 3600 : cmd.expiresInSeconds();
        Hold hold = Hold.place(UuidV7.randomString(), cmd.accountId(), actor.tenantId(),
                amount, cmd.reason(), now, now.plusSeconds(expiry));
        balance.placeHold(amount, now);
        Balance savedBalance = balanceRepository.save(balance);
        Hold savedHold = balanceRepository.saveHold(hold);

        settleAndComplete(txn);
        Transaction savedTxn = transactionRepository.save(txn);

        audit(actor, AGG_BALANCE, cmd.accountId(), "HOLD",
                "{\"available\":\"" + (balance.getLedgerMinor()) + "\"}",
                "{\"held\":\"" + amount.toMinorString() + "\"}", cmd.reason(), now);
        eventPublisher.publishBalanceHeld(savedHold, savedTxn.getId(), savedBalance);
        eventPublisher.publishTransactionSettled(savedTxn);
        eventPublisher.publishTransactionCompleted(savedTxn);
        return new HoldResult(HoldView.from(savedHold), savedTxn.getId());
    }

    @Transactional
    public CaptureResult captureHold(CaptureHoldCommand cmd) {
        ActorContext actor = cmd.actor();
        Instant now = clock.now();
        Account account = loadAccount(cmd.accountId(), actor.tenantId());
        account.ensureFundMovementAllowed();

        Hold hold = loadHold(cmd.holdId(), actor.tenantId());
        Money captureAmount = money(cmd.amountMinor(), cmd.currency());
        Balance balance = loadBalance(cmd.accountId(), actor.tenantId());

        Transaction txn = newTxn(actor, cmd.accountId(), TransactionType.CAPTURE,
                captureAmount, null, hold.getId(), null, now);

        // ---- F4 GATE before balance mutation ----
        gateOrFail(account, txn, TransactionType.CAPTURE, captureAmount, null, now);

        Money holdAmount = hold.amount();
        Money released = hold.capture(captureAmount, now);
        balance.captureHold(holdAmount, captureAmount, now);
        Balance savedBalance = balanceRepository.save(balance);
        Hold savedHold = balanceRepository.saveHold(hold);

        settleAndComplete(txn);
        Transaction savedTxn = transactionRepository.save(txn);

        audit(actor, AGG_BALANCE, cmd.accountId(), "CAPTURE",
                "{\"hold\":\"" + holdAmount.toMinorString() + "\"}",
                "{\"captured\":\"" + captureAmount.toMinorString()
                        + "\",\"released\":\"" + released.toMinorString() + "\"}",
                null, now);
        eventPublisher.publishBalanceCaptured(savedHold, savedTxn.getId(),
                savedBalance, captureAmount, released);
        eventPublisher.publishTransactionSettled(savedTxn);
        eventPublisher.publishTransactionCompleted(savedTxn);
        return new CaptureResult(savedHold.getId(), captureAmount, released,
                savedHold.getStatus().name(), savedTxn.getId());
    }

    @Transactional
    public ReleaseResult releaseHold(ReleaseHoldCommand cmd) {
        ActorContext actor = cmd.actor();
        Instant now = clock.now();
        Account account = loadAccount(cmd.accountId(), actor.tenantId());
        account.ensureFundMovementAllowed();

        Hold hold = loadHold(cmd.holdId(), actor.tenantId());
        Money holdAmount = hold.amount();
        Balance balance = loadBalance(cmd.accountId(), actor.tenantId());

        Transaction txn = newTxn(actor, cmd.accountId(), TransactionType.RELEASE,
                holdAmount, null, hold.getId(), null, now);

        // RELEASE returns held funds; KycGate permits release at any level, but
        // we still traverse the single gate (F4 — no bypass path exists).
        gateOrFail(account, txn, TransactionType.RELEASE, holdAmount, null, now);

        hold.release(now);
        balance.releaseHold(holdAmount, now);
        Balance savedBalance = balanceRepository.save(balance);
        Hold savedHold = balanceRepository.saveHold(hold);

        settleAndComplete(txn);
        Transaction savedTxn = transactionRepository.save(txn);

        audit(actor, AGG_BALANCE, cmd.accountId(), "RELEASE",
                "{\"held\":\"" + holdAmount.toMinorString() + "\"}",
                "{\"released\":\"" + holdAmount.toMinorString() + "\"}", null, now);
        eventPublisher.publishBalanceReleased(savedHold, savedTxn.getId(), savedBalance);
        eventPublisher.publishTransactionSettled(savedTxn);
        eventPublisher.publishTransactionCompleted(savedTxn);
        return new ReleaseResult(savedHold.getId(), holdAmount,
                savedHold.getStatus().name(), savedTxn.getId());
    }

    @Transactional
    public TransactionView transfer(TransferCommand cmd) {
        ActorContext actor = cmd.actor();
        Instant now = clock.now();
        Account from = loadAccount(cmd.fromAccountId(), actor.tenantId());
        Account to = loadAccount(cmd.toAccountId(), actor.tenantId());
        from.ensureFundMovementAllowed();
        to.ensureFundMovementAllowed();

        Money amount = money(cmd.amountMinor(), cmd.currency());
        ensureAccountCurrency(from, amount);
        ensureAccountCurrency(to, amount);

        Balance fromBalance = loadBalance(cmd.fromAccountId(), actor.tenantId());
        Balance toBalance = loadBalance(cmd.toAccountId(), actor.tenantId());

        Transaction txn = newTxn(actor, cmd.fromAccountId(), TransactionType.TRANSFER,
                amount, cmd.toAccountId(), null, cmd.reason(), now);

        // ---- F4 GATE before any balance mutation ----
        gateOrFail(from, txn, TransactionType.TRANSFER, amount, to.getOwnerRef(), now);

        // Atomic hold-on-source + capture + credit-target in this one Tx (F1).
        fromBalance.debit(amount, now);
        toBalance.credit(amount, now);
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);

        settleAndComplete(txn);
        Transaction savedTxn = transactionRepository.save(txn);

        audit(actor, AGG_TRANSACTION, savedTxn.getId(), "TRANSFER",
                "{\"from\":\"" + cmd.fromAccountId() + "\"}",
                "{\"to\":\"" + cmd.toAccountId() + "\",\"amount\":\""
                        + amount.toMinorString() + "\"}", cmd.reason(), now);
        eventPublisher.publishTransactionSettled(savedTxn);
        eventPublisher.publishTransactionCompleted(savedTxn);
        return TransactionView.from(savedTxn);
    }

    /**
     * v1 internal/stub funding source (architecture.md § Balance Model —
     * "topup / v1 = internal/stub funding source"). Credits confirmed ledger
     * funds into an ACTIVE account. Still single-Tx + audited + a TOPUP txn
     * (F1/F6); used by the funding side of transfers and test seeding. There
     * is no real external bank adapter in v1 (that is v2).
     */
    @Transactional
    public TransactionView topUp(ActorContext actor, String accountId, long amountMinor) {
        Instant now = clock.now();
        Account account = loadAccount(accountId, actor.tenantId());
        account.ensureFundMovementAllowed();
        Money amount = Money.of(amountMinor, account.getCurrency());
        Balance balance = loadBalance(accountId, actor.tenantId());

        Transaction txn = newTxn(actor, accountId, TransactionType.TOPUP,
                amount, null, null, "internal funding (v1 stub)", now);
        // TOPUP still traverses the single gated path (F4 — no bypass).
        gateOrFail(account, txn, TransactionType.TOPUP, amount, null, now);

        balance.credit(amount, now);
        balanceRepository.save(balance);
        settleAndComplete(txn);
        Transaction savedTxn = transactionRepository.save(txn);

        audit(actor, AGG_BALANCE, accountId, "TOPUP", null,
                "{\"credited\":\"" + amount.toMinorString() + "\"}",
                "internal funding (v1 stub)", now);
        eventPublisher.publishTransactionSettled(savedTxn);
        eventPublisher.publishTransactionCompleted(savedTxn);
        return TransactionView.from(savedTxn);
    }

    @Transactional(readOnly = true)
    public com.example.finance.account.application.view.TransactionPageView
            listTransactions(String accountId,
                             ActorContext actor,
                             String type,
                             String status,
                             int page,
                             int size) {
        loadAccount(accountId, actor.tenantId());
        TransactionType t = type == null || type.isBlank()
                ? null : TransactionType.valueOf(type.trim().toUpperCase());
        com.example.finance.account.domain.transaction.status.TransactionStatus s =
                status == null || status.isBlank() ? null
                        : com.example.finance.account.domain.transaction.status
                        .TransactionStatus.valueOf(status.trim().toUpperCase());
        TransactionRepository.Page p = transactionRepository.findByAccountId(
                accountId, actor.tenantId(), t, s, page, size);
        return new com.example.finance.account.application.view.TransactionPageView(
                p.content().stream()
                        .map(com.example.finance.account.application.view
                                .TransactionView::from)
                        .toList(),
                p.page(), p.size(), p.totalElements(), p.totalPages());
    }

    // --------------------------------------------------------------------
    // F4 compliance gate — the ONLY screening call site
    // --------------------------------------------------------------------

    private void gateOrFail(Account account,
                            Transaction txn,
                            TransactionType type,
                            Money amount,
                            String counterpartyOwnerRef,
                            Instant now) {
        txn.markValidated();
        // 1) KYC ceiling/level (pure policy). A KYC/limit failure throws a
        //    domain exception → outer Tx rolls back, nothing persisted.
        KycGate.ensurePermitted(account.getKycLevel(), type, amount);
        // 2) AML/sanction screen (port — v1 stub adapter).
        ScreeningDecision decision = compliancePort.screen(
                account.getOwnerRef(), counterpartyOwnerRef, amount);
        if (decision.isSanctionHit()) {
            // Durable in its OWN Tx — survives the fund-movement rollback (F4).
            complianceFailureRecorder.recordSanctionHit(txn, account.getTenantId(),
                    account.getId(), decision.screeningRef(), now);
            throw new SanctionHitException(
                    "Sanction/watchlist match — transaction " + txn.getId()
                            + " routed to operator review (no auto-clear)");
        }
        if (decision.outcome() == ScreeningDecision.Outcome.SCREENING_UNRESOLVED) {
            complianceFailureRecorder.recordAmlUnresolved(txn);
            throw new AmlScreeningRequiredException(
                    "AML screening unresolved — transaction not authorized");
        }
        txn.markAuthorized();
    }

    private void settleAndComplete(Transaction txn) {
        txn.markSettled(clock.now());
        txn.markCompleted();
    }

    // --------------------------------------------------------------------
    // helpers
    // --------------------------------------------------------------------

    /**
     * Build the transaction in REQUESTED state WITHOUT persisting. It is
     * persisted only after the F4 gate passes (success path) or by the
     * {@link ComplianceFailureRecorder} in its own Tx (rejection path) — so
     * a rolled-back fund-movement Tx never leaves an orphan REQUESTED row.
     */
    private Transaction newTxn(ActorContext actor, String accountId,
                               TransactionType type, Money amount,
                               String counterparty, String holdId,
                               String reason, Instant now) {
        return Transaction.request(UuidV7.randomString(), actor.tenantId(),
                accountId, type, amount, counterparty, holdId, reason, now);
    }

    private void audit(ActorContext actor, String aggType, String aggId,
                       String action, String before, String after,
                       String reason, Instant now) {
        auditLogRepository.save(AuditLog.of(actor.tenantId(), aggType, aggId, action,
                actor.accountId(), actor.actorType(), before, after, reason, now));
    }

    private Account loadAccount(String accountId, String tenantId) {
        return accountRepository.findById(accountId, tenantId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountId));
    }

    private Balance loadBalance(String accountId, String tenantId) {
        return balanceRepository.findByAccountId(accountId, tenantId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Balance not found for account: " + accountId));
    }

    private Hold loadHold(String holdId, String tenantId) {
        return balanceRepository.findHoldById(holdId, tenantId)
                .orElseThrow(() -> new HoldNotFoundException("Hold not found: " + holdId));
    }

    private List<BalanceView> balanceViews(String accountId, String tenantId) {
        return balanceRepository.findAllByAccountId(accountId, tenantId).stream()
                .map(BalanceView::from).toList();
    }

    private Money money(String minorUnits, String currencyCode) {
        return Money.of(minorUnits, Currency.of(currencyCode));
    }

    private void ensureAccountCurrency(Account account, Money amount) {
        if (account.getCurrency() != amount.currency()) {
            throw new com.example.finance.account.domain.error.DomainErrors
                    .CurrencyMismatchException("operation currency "
                    + amount.currency() + " != account currency "
                    + account.getCurrency());
        }
    }

    // ---- result records ----

    public record HoldResult(HoldView hold, String transactionId) {
    }

    public record CaptureResult(String holdId, Money captured, Money released,
                                String status, String transactionId) {
    }

    public record ReleaseResult(String holdId, Money released,
                                String status, String transactionId) {
    }
}
