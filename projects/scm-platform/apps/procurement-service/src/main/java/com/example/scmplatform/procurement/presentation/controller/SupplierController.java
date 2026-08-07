package com.example.scmplatform.procurement.presentation.controller;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.IdempotencyExecutor;
import com.example.scmplatform.procurement.application.IdempotencyHasher;
import com.example.scmplatform.procurement.application.SupplierApplicationService;
import com.example.scmplatform.procurement.application.SupplierRegistration;
import com.example.scmplatform.procurement.application.SupplierView;
import com.example.scmplatform.procurement.application.command.RegisterSupplierCommand;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.presentation.dto.ApiEnvelope;
import com.example.scmplatform.procurement.presentation.dto.PageResponse;
import com.example.scmplatform.procurement.presentation.dto.RegisterSupplierRequest;
import com.example.scmplatform.procurement.presentation.dto.SupplierResponse;
import com.example.security.servlet.actor.ActorContextResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supplier master REST endpoints (TASK-SCM-BE-059 / ADR-SCM-001 option A), per
 * {@code projects/scm-platform/specs/contracts/http/procurement-api.md}.
 *
 * <p>Registration answers <b>two</b> status lines on purpose, and the split is
 * the point of the endpoint rather than a detail of it:
 *
 * <ul>
 *   <li><b>201</b> — a row was inserted (including an {@code Idempotency-Key}
 *       replay of that same insert, which returns the cached 201);</li>
 *   <li><b>200</b> — the tenant already had this {@code code}, reached with a
 *       <em>different</em> key, and no second row was written.</li>
 * </ul>
 *
 * <p>A caller that lost its idempotency key — a re-run seed, a fresh CI job —
 * must converge rather than duplicate, and 409 would make that caller's normal
 * second run look like a failure.
 */
@RestController
@RequestMapping("/api/procurement/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private static final String ENDPOINT = "POST /api/procurement/suppliers";

    private final SupplierApplicationService service;
    private final IdempotencyExecutor idempotency;
    private final IdempotencyHasher hasher;

    @PostMapping
    public ResponseEntity<ApiEnvelope<SupplierResponse>> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegisterSupplierRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        RegisterSupplierCommand cmd = new RegisterSupplierCommand(
                actor, req.code(), req.name(), req.contractStartedAt(), req.contractExpiresAt());
        // successStatus is the value cached ON the idempotency record, not the
        // wire status — the controller still owns that, and derives it below
        // from the (also cached) `created` flag so a replay answers exactly what
        // the first call did.
        SupplierRegistration result = idempotency.execute(
                actor.tenantId(), ENDPOINT, idempotencyKey,
                hasher.hash(req), HttpStatus.CREATED.value(), SupplierRegistration.class,
                () -> service.register(cmd));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiEnvelope.of(SupplierResponse.from(result.supplier())));
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<ApiEnvelope<SupplierResponse>> get(@PathVariable String supplierId) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return ResponseEntity.ok(
                ApiEnvelope.of(SupplierResponse.from(service.get(supplierId, actor))));
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<PageResponse<SupplierResponse>>> search(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        PageResult<SupplierView> result = service.search(actor, code, status,
                PageQuery.of(page, size, "createdAt", "DESC"));
        return ResponseEntity.ok(
                ApiEnvelope.of(PageResponse.from(result.map(SupplierResponse::from))));
    }
}
