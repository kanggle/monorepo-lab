package com.example.account.domain.identity;

import com.example.account.domain.account.Email;
import com.example.account.domain.tenant.TenantId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Aggregate root for the central identity registry (ADR-MONO-034 U1-A).
 *
 * <p>An {@code Identity} is the canonical "this is one person" record that the
 * account/credential unification links the consumer account (and, in later
 * steps, the operator extension) to. It is pure identity correlation: it holds
 * NO roles, NO permissions, NO aud dimension (ADR-034 U5 — identity unification
 * is orthogonal to authorization; the JWT {@code roles} claim stays sourced from
 * {@code account_roles} per ADR-MONO-033).
 *
 * <p>In ADR-034 step 3a the registry is populated by a one-identity-per-account
 * backfill (V0023); creation paths are wired to it in step 3d.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Identity {

    private String identityId;
    private TenantId tenantId;
    private String primaryEmail;
    private IdentityStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private int version;

    /**
     * Create a new identity under the given tenant. The email is validated and
     * normalized by {@link Email}.
     */
    public static Identity create(TenantId tenantId, String primaryEmail) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        Email validated = new Email(primaryEmail);

        Identity identity = new Identity();
        identity.identityId = IdentityId.generate().value();
        identity.tenantId = tenantId;
        identity.primaryEmail = validated.value();
        identity.status = IdentityStatus.ACTIVE;
        identity.createdAt = Instant.now();
        identity.updatedAt = Instant.now();
        identity.version = 0;
        return identity;
    }

    /**
     * Reconstitute an Identity from persisted state. Used by infrastructure mappers.
     */
    public static Identity reconstitute(String identityId, TenantId tenantId, String primaryEmail,
                                        IdentityStatus status, Instant createdAt, Instant updatedAt,
                                        int version) {
        Identity identity = new Identity();
        identity.identityId = identityId;
        identity.tenantId = tenantId;
        identity.primaryEmail = primaryEmail;
        identity.status = status;
        identity.createdAt = createdAt;
        identity.updatedAt = updatedAt;
        identity.version = version;
        return identity;
    }
}
