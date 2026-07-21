package com.example.account.presentation.internal.identity;

import com.example.account.presentation.internal.TenantScopeGuard;
import com.example.account.application.service.ResolveOrCreateIdentityUseCase;
import com.example.account.application.service.ResolveOrCreateIdentityUseCase.ResolveOrCreateIdentityResult;
import com.example.account.presentation.dto.request.ResolveOrCreateIdentityRequest;
import com.example.account.presentation.dto.response.ResolveOrCreateIdentityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d): internal resolve-or-create endpoint
 * for the central {@code identities} registry (step 3a). The provisioning primitive
 * unified new-operator creation (admin-service {@code CreateOperatorUseCase}) calls
 * so every operator born after step 3 is linked to a central identity.
 *
 * <p>It IS a mutating EP (may create an identity) — a normal {@code /internal/**}
 * write on the {@code X-Internal-Token}/client_credentials chain (validated by
 * {@code InternalApiFilter}). No silent merge (ADR-034 U3): an existing identity is
 * only reused on explicit {@code reuseExisting=true}, else {@code identityId} is
 * {@code null} ({@code EXISTS_NOT_REUSED}) and nothing is mutated.
 *
 * <p>Authorization: tenant scope re-checked at the controller as defense-in-depth
 * (gateway is the primary check), mirroring {@code AccountIdentityController} /
 * {@code AccountRoleController}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}")
public class ResolveOrCreateIdentityController {

    private final ResolveOrCreateIdentityUseCase resolveOrCreateIdentityUseCase;

    /**
     * POST /internal/tenants/{tenantId}/identities:resolveOrCreate (AIP-136 colon-verb).
     *
     * <p>The verb path carries a leading slash and the colon in a literal segment
     * (mirroring {@code AccountRoleController#addRole}'s {@code /roles:add}) so the
     * {@code PathPatternParser} matches it rather than falling through to static-
     * resource handling.
     */
    @PostMapping("/identities:resolveOrCreate")
    public ResponseEntity<ResolveOrCreateIdentityResponse> resolveOrCreate(
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody ResolveOrCreateIdentityRequest request) {

        TenantScopeGuard.validate(callerTenantId, tenantId);

        ResolveOrCreateIdentityResult result = resolveOrCreateIdentityUseCase.execute(
                tenantId, request.email(), request.reuseExistingOrFalse());
        return ResponseEntity.ok(ResolveOrCreateIdentityResponse.from(result));
    }

}
