package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxRateOverrideView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateOverrideInvalidException;
import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.journal.repository.FxRateOverrideRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Upsert the tenant's FX contract-rate override (28th increment — TASK-FIN-BE-042, ADR-002
 * § 3.1 per-tenant override / 특수 계약환율). One {@code @Transactional} boundary:
 *
 * <ol>
 *   <li>validate the currency pair + the rate BEFORE any persist — an unknown currency or a
 *       null / non-positive {@code rate} throws {@link FxRateOverrideInvalidException}
 *       ({@code VALIDATION_ERROR}, 400) and nothing is written (mirrors
 *       {@code SetFxCostFlowConfigUseCase} / {@code SetFxToleranceUseCase});</li>
 *   <li>upsert the per-tenant override row (last-write-wins on the
 *       {@code (tenant_id, base, foreign)} PK), stamping the audit fields {@code updated_by}
 *       (the {@link ActorContext} identity) / {@code updated_at} (the clock);</li>
 *   <li>write an audit row in the SAME transaction (regulated/audit-heavy — a partial commit is
 *       impossible).</li>
 * </ol>
 *
 * <p><b>Effect</b>: once a row exists, {@code ResolveEffectiveFxRate} returns this contract rate
 * (with {@code source=override:contract}, {@code fromFeed=false}) ahead of the market feed when no
 * manual rate is supplied — see {@code SettleForeignPositionUseCase} /
 * {@code RevalueForeignBalanceUseCase}. Tenant-scoped: this row is invisible to other tenants.
 */
@Service
@RequiredArgsConstructor
public class SetFxRateOverrideUseCase {

    private static final String AGGREGATE_TYPE = "FxRateOverride";

    private final FxRateOverrideRepository fxRateOverrideRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClockPort clock;

    @Transactional
    public FxRateOverrideView set(SetFxRateOverrideCommand command) {
        // Validate BEFORE constructing the domain object — an unknown currency or a null /
        // non-positive rate must raise VALIDATION_ERROR (400) and write nothing.
        Currency base = parseCurrency(command.base());
        Currency foreign = parseCurrency(command.foreign());
        BigDecimal rate = command.rate();
        if (rate == null || rate.signum() <= 0) {
            throw new FxRateOverrideInvalidException(
                    "contract rate must be a strictly-positive decimal — got: " + rate);
        }
        if (base == foreign) {
            throw new FxRateOverrideInvalidException(
                    "base and foreign currency must differ — got both " + base.code());
        }

        return AuditedUpsert.run(clock, auditLogRepository,
                now -> fxRateOverrideRepository.save(
                        FxRateOverride.of(command.tenantId(), base, foreign, rate, command.actor(), now)),
                (saved, now) -> AuditLog.of(
                        command.tenantId(), AGGREGATE_TYPE, command.tenantId(),
                        "FX_RATE_OVERRIDE_SET", command.actor(),
                        "pair=" + base.code() + "/" + foreign.code() + " rate=" + rate.toPlainString(),
                        "set fx contract-rate override", now),
                FxRateOverrideView::from);
    }

    /** Map an unknown/unsupported currency code to {@code VALIDATION_ERROR} (400). */
    private static Currency parseCurrency(String code) {
        return Currency.ofOrThrow(code, c -> new FxRateOverrideInvalidException(
                "unknown currency: " + c + " — supported: KRW, USD, EUR, JPY"));
    }
}
