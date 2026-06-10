package com.example.account.presentation.internal;

import com.example.account.application.result.SubscriptionMutationResult;
import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.application.service.TenantDomainSubscriptionMutationUseCase;
import com.example.account.application.service.TenantDomainSubscriptionQueryUseCase;
import com.example.account.domain.tenant.SubscriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-322 (ADR-MONO-019 D2): internal read surface exposing ACTIVE
 * tenant↔domain subscriptions for the admin-service console catalog projection
 * (ADR-019 D4).
 *
 * <p>URL prefix: {@code /internal/tenant-domain-subscriptions}
 * <p>Authentication: under the {@code /internal/} gate — GAP
 * {@code client_credentials} Bearer JWT (TASK-BE-319b receiver, fail-closed;
 * the test/standalone bypass profile populates an authenticated principal).
 * No extra security wiring is needed: the {@code /internal/} prefix is already
 * covered by {@code InternalApiFilter} + the OAuth2 resource-server
 * {@code .authenticated()} gate.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenant-domain-subscriptions")
public class TenantDomainSubscriptionController {

    private final TenantDomainSubscriptionQueryUseCase queryUseCase;
    private final TenantDomainSubscriptionMutationUseCase mutationUseCase;

    /**
     * GET /internal/tenant-domain-subscriptions[?domainKey=][&tenantId=]
     * Returns all ACTIVE tenant↔domain subscriptions, optionally filtered to a
     * single {@code domainKey} and/or {@code tenantId} (both compose with AND).
     *
     * <p>TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): {@code tenantId} reverse-lookup
     * backs the auth-service issuance-time {@code entitled_domains} claim populate.
     */
    @GetMapping
    public ResponseEntity<SubscriptionListResponse> listSubscriptions(
            @RequestParam(required = false) String domainKey,
            @RequestParam(required = false) String tenantId) {
        List<TenantDomainSubscriptionResult> results = queryUseCase.listActive(domainKey, tenantId);
        List<SubscriptionItem> items = results.stream()
                .map(r -> new SubscriptionItem(r.tenantId(), r.domainKey()))
                .toList();
        return ResponseEntity.ok(new SubscriptionListResponse(items));
    }

    /**
     * POST /internal/tenant-domain-subscriptions — subscribe (create) a new
     * subscription (TASK-BE-342, ADR-MONO-023 D3). {@code status} defaults to
     * ACTIVE; only creatable states (PENDING/ACTIVE) are accepted.
     */
    @PostMapping
    public ResponseEntity<SubscriptionMutationResponse> subscribe(
            @Valid @RequestBody SubscribeRequest body) {
        SubscriptionMutationResult result = mutationUseCase.subscribe(
                body.tenantId(), body.domainKey(), body.status(),
                body.actorType(), body.actorId(), body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionMutationResponse.from(result));
    }

    /**
     * PATCH /internal/tenant-domain-subscriptions/{tenantId}/{domainKey} —
     * transition an existing subscription (suspend/resume/cancel). The
     * {@link SubscriptionStatus} guard rejects illegal transitions (409).
     */
    @PatchMapping("/{tenantId}/{domainKey}")
    public ResponseEntity<SubscriptionMutationResponse> changeStatus(
            @PathVariable String tenantId,
            @PathVariable String domainKey,
            @Valid @RequestBody ChangeStatusRequest body) {
        SubscriptionMutationResult result = mutationUseCase.changeStatus(
                tenantId, domainKey, body.status(),
                body.actorType(), body.actorId(), body.reason());
        return ResponseEntity.ok(SubscriptionMutationResponse.from(result));
    }

    // ---- DTOs ----------------------------------------------------------------

    public record SubscriptionItem(
            String tenantId,
            String domainKey
    ) {}

    public record SubscriptionListResponse(
            List<SubscriptionItem> items
    ) {}

    /** A non-creatable {@code status} (SUSPENDED/CANCELLED) → 400 from the domain factory. */
    public record SubscribeRequest(
            @NotBlank String tenantId,
            @NotBlank String domainKey,
            SubscriptionStatus status,
            String actorType,
            String actorId,
            String reason
    ) {}

    public record ChangeStatusRequest(
            @NotNull SubscriptionStatus status,
            String actorType,
            String actorId,
            String reason
    ) {}

    public record SubscriptionMutationResponse(
            String tenantId,
            String domainKey,
            String previousStatus,
            String currentStatus,
            String occurredAt
    ) {
        static SubscriptionMutationResponse from(SubscriptionMutationResult r) {
            return new SubscriptionMutationResponse(
                    r.tenantId(), r.domainKey(),
                    r.previousStatus() == null ? null : r.previousStatus().name(),
                    r.currentStatus().name(),
                    r.occurredAt().toString());
        }
    }
}
