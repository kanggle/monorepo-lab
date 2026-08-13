package com.example.scmplatform.procurement.application;

import com.example.common.id.UuidV7;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.application.command.RegisterSupplierCommand;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.audit.AuditLogRepository;
import com.example.scmplatform.procurement.domain.error.PermissionDeniedException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Supplier master use cases (TASK-SCM-BE-059 / ADR-SCM-001 option A).
 *
 * <p>The v1 supplier master is an <em>operated</em> object, so it gets an
 * operator-facing write surface instead of being filled by migrations. Before
 * this class existed there was no API path to a {@code suppliers} row at all:
 * {@code POST /po} answered {@code SUPPLIER_NOT_FOUND} and both the e2e suite
 * and the demo seed wrote the row with direct SQL.
 *
 * <p><b>No credentials.</b> Nothing here reads or writes
 * {@code supplier_credentials}; v1 accepts supplier credentials on no path at
 * all (ADR-SCM-001 ACCEPT rider). A supplier with none is the normal state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierApplicationService {

    private static final String AGGREGATE_SUPPLIER = "supplier";

    private final SupplierRepository supplierRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Register a supplier, idempotently on {@code (tenantId, code)}.
     *
     * <p><b>Idempotency lives here, not only in the header wrapper.</b> The
     * {@code Idempotency-Key} wrapper covers a retry that still holds its key;
     * this natural-key check covers the caller that <em>lost</em> it — a re-run
     * seed, a fresh CI job. Without the second mechanism a rerun duplicates the
     * row, and V6's {@code ux_suppliers_tenant_code} would turn that into a
     * 409 rather than convergence.
     */
    @Transactional
    public SupplierRegistration register(RegisterSupplierCommand cmd) {
        ActorContext actor = cmd.actor();
        requireOperator(actor, "register a supplier");

        Optional<Supplier> existing = supplierRepository.findByCode(cmd.code(), actor.tenantId());
        if (existing.isPresent()) {
            // Converge — deliberately NOT a 409. Judge this path by row count:
            // it returns 2xx exactly like a creation does, which is how
            // seed-scm.sh once logged a replay as a fresh "created".
            return new SupplierRegistration(SupplierView.from(existing.get()), false);
        }

        Supplier supplier = Supplier.create(
                UuidV7.randomString(),
                actor.tenantId(),
                cmd.code(),
                cmd.name(),
                SupplierStatus.ACTIVE,
                cmd.contractStartedAt(),
                cmd.contractExpiresAt());
        Supplier saved = supplierRepository.save(supplier);

        auditLogRepository.save(AuditLog.of(
                actor.tenantId(),
                AGGREGATE_SUPPLIER,
                saved.getId(),
                "SUPPLIER_REGISTERED",
                actor.accountId(),
                actor.actorType(),
                null,
                null));

        log.info("supplier registered: tenant={} code={} id={}",
                actor.tenantId(), saved.getCode(), saved.getId());
        return new SupplierRegistration(SupplierView.from(saved), true);
    }

    @Transactional(readOnly = true)
    public SupplierView get(String supplierId, ActorContext actor) {
        return supplierRepository.findById(supplierId, actor.tenantId())
                .map(SupplierView::from)
                // Cross-tenant lookups answer NOT_FOUND, not FORBIDDEN — same
                // no-enumeration-leak convention as GET /po/{poId}.
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found: " + supplierId));
    }

    @Transactional(readOnly = true)
    public PageResult<SupplierView> search(ActorContext actor, String code, SupplierStatus status,
                                           PageQuery pageQuery) {
        return supplierRepository.search(actor.tenantId(), code, status, pageQuery)
                .map(SupplierView::from);
    }

    /**
     * Fail-closed operator gate.
     *
     * <p><b>Role, not scope</b> — and that was measured, not assumed. The only
     * identity that reaches this endpoint in the demo/console path is the
     * assume-tenant operator token, whose {@code scope} claim is the
     * {@code platform-console-web} client's registered scope set
     * ({@code openid profile email tenant.read erp.write}, V0015 + V0023) — it
     * does <b>not</b> contain {@code scm.write}. Requiring that scope would have
     * made supplier registration unreachable for the only operator there is,
     * which is precisely the trap wms master-data writes fell into
     * ({@code MASTER_WRITE} was granted to nobody — TASK-MONO-514). That trap has
     * since been sprung on the wms side: ADR-MONO-061 lets a workload client carry
     * roles, and the wms workload client now receives {@code MASTER_WRITE} on the
     * {@code wms.master.write} scope. The lesson this sentence was citing is
     * unchanged, and the reasoning below is unaffected — no scm client is enumerated
     * for any role, so scm's operator gate still has exactly one identity that
     * reaches it.
     *
     * <p>The role side does resolve: a {@code demo-corp} tenant carries an ACTIVE
     * {@code scm} subscription, {@code OperatorRoleDerivation} maps that domain
     * to {@code SCM_OPERATOR}, and {@link ActorContext#isOperator()} already
     * accepts it.
     */
    private void requireOperator(ActorContext actor, String action) {
        if (actor == null || !actor.isOperator()) {
            throw new PermissionDeniedException(
                    "OPERATOR actor required to " + action);
        }
    }
}
