package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;

import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Shared "stamp clock → save aggregate → write audit → return view" upsert
 * skeleton for the FX config use-cases (TASK-FIN-BE-061 F3). Each {@code Set*}
 * use-case repeated the same tail: capture {@code clock.now()} once, save the
 * aggregate stamped with that instant, write an {@link AuditLog} row stamped with
 * the SAME instant in the same transaction, and return the view.
 *
 * <p>The collaborators are passed as arguments (not injected) so each use-case
 * keeps its existing constructor and fields — and therefore its existing unit
 * tests — unchanged. Each caller retains its own pre-validation, {@code
 * AGGREGATE_TYPE}, audit action / afterState, saved aggregate, and returned view;
 * only the mechanical clock/save/audit/return ordering is single-sourced here.
 * Runs inside the caller's {@code @Transactional} boundary (same thread → same tx).
 *
 * <p>NOTE: the delete variant ({@code DeleteFxCostFlowAccountConfigUseCase} —
 * conditional audit, no validate, boolean return) deliberately does NOT use this.
 */
final class AuditedUpsert {

    private AuditedUpsert() {
    }

    /**
     * @param clock              the caller's clock — invoked exactly once, here
     * @param auditLogRepository the caller's audit sink — one save, here
     * @param save               builds + persists the aggregate, stamped with {@code now}
     * @param audit              builds the audit row from the saved aggregate + {@code now}
     * @param view               projects the saved aggregate to the returned view
     */
    static <A, V> V run(ClockPort clock, AuditLogRepository auditLogRepository,
                        Function<Instant, A> save,
                        BiFunction<A, Instant, AuditLog> audit,
                        Function<A, V> view) {
        Instant now = clock.now();
        A saved = save.apply(now);
        auditLogRepository.save(audit.apply(saved, now));
        return view.apply(saved);
    }
}
