package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Injects {@code tenant_id} and {@code tenant_type} claims into every access token
 * and ID token issued by Spring Authorization Server.
 *
 * <p><b>Grant-type specific behaviour</b>
 * <ul>
 *   <li>{@code client_credentials}: reads {@code tenant_id} / {@code tenant_type} from
 *       {@link ClientSettings} custom keys ({@link OAuthClientMapper#SETTING_TENANT_ID},
 *       {@link OAuthClientMapper#SETTING_TENANT_TYPE}) injected by
 *       {@link com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper}
 *       during client lookup. This is the Option B implementation (TASK-BE-252).
 *       If the client was built without the JPA mapper (e.g. in unit tests that still
 *       use the old {@code clientName = "tenantId|tenantType"} format), falls back to
 *       the clientName split for backward compatibility.
 *   <li>{@code authorization_code}: reads tenant context from the authenticated
 *       principal's JWT attributes. Falls back to ClientSettings if absent.
 *   <li>{@code refresh_token}: TASK-BE-274 cycle 3 — {@link SasRefreshTokenAuthenticationProvider}
 *       generates a brand-new JWT for the rotated access token using a fresh
 *       {@link org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext}
 *       whose {@code authorizationGrantType} is {@code refresh_token}. The previous
 *       assumption that SAS built-in reuses claims does not apply to our custom
 *       provider. Reuses {@code customizeForAuthorizationCode} logic: principal
 *       details map first, ClientSettings Option B fallback second.
 * </ul>
 *
 * <p><b>Token types covered</b>
 * <ul>
 *   <li>{@link OAuth2TokenType#ACCESS_TOKEN} — always customized</li>
 *   <li>{@code id_token} (OIDC ID token) — also customized when {@code openid} scope present</li>
 *   <li>{@link OAuth2TokenType#REFRESH_TOKEN} — no-op (opaque, no claims)</li>
 * </ul>
 *
 * <p>TASK-BE-251 — Phase 2a initial implementation.
 * TASK-BE-252 — Option B: reads tenant info from ClientSettings instead of clientName.
 * TASK-BE-369 (ADR-MONO-033 S4 base + S3) — adds the signed {@code roles} claim on the
 * {@code authorization_code}/{@code refresh_token} path: stored {@code account_roles}
 * (via {@link AccountServicePort#listAccountRoles}) emitted verbatim if present, else the
 * aud-default seed ({@link RoleSeedPolicy}) keyed on the client-platform. Fail-soft +
 * recursion-safe, mirroring {@link #populateEntitledDomains} — NEVER reachable on
 * {@code client_credentials}.
 *
 * <p>TASK-MONO-263 (ADR-MONO-035 4b-2b / ADR-032 D5 step 4) — stops emitting the
 * {@code account_type} claim on every grant (the column is dropped, no consumer remains)
 * and decouples {@link RoleSeedPolicy} from {@code account_type} (consumer seed keyed on
 * platform only). Operators get domain roles at assume-tenant (BE-376), consumers carry
 * {@code CUSTOMER}/{@code FAN} (seed) — neither needs {@code account_type}.
 */
@Slf4j
@Component
public class TenantClaimTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /** Separator used in legacy clientName encoding (backward-compat fallback only). */
    private static final String METADATA_SEPARATOR = "|";

    /** SAS uses the string "id_token" as the token type value for ID tokens. */
    private static final String ID_TOKEN_TYPE_VALUE = "id_token";

    /**
     * TASK-BE-324: signed claim carrying the tenant's ACTIVE entitled domainKeys.
     * Must match the domain-side gates' {@code CLAIM_ENTITLED_DOMAINS} exactly.
     */
    private static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";

    /**
     * TASK-BE-337 / TASK-BE-338: the {@code org_scope} data-scope claim the erp
     * masterdata-service reads (the department subtree-root ids the actor may act
     * under; {@code "*"} = whole tenant). Injected ONLY on the assume-tenant
     * operator token.
     *
     * <p>TASK-BE-338 (ADR-MONO-020 D3 amendment) replaced the TASK-BE-337 hardcoded
     * {@code ["*"]} v1 bridge with the <b>membership-derived</b> value: the
     * per-assignment {@code org_scope} carried on the grant from the admin-service
     * assignment-check ({@code operator_tenant_assignment.org_scope}). NET-ZERO: a
     * null/empty org_scope → {@code ["*"]} = whole tenant (byte-identical to BE-337);
     * a present non-empty list is injected verbatim. The tenant gate already
     * isolates cross-tenant, so org_scope governs only the department subtree WITHIN
     * the assumed tenant. Only erp consumes this claim (no cross-domain effect — it
     * expands the subtree roots → descendants for its containment check,
     * TASK-ERP-BE-008).
     */
    private static final String CLAIM_ORG_SCOPE = "org_scope";

    /**
     * TASK-BE-369 (ADR-MONO-033 S4 base + S3 / ADR-MONO-032 D5 step 2): the signed
     * {@code roles} claim. Emitted on account-bearing grants
     * ({@code authorization_code} / {@code refresh_token}) — sourced from the stored
     * {@code account_roles} if present, else the aud-default seed
     * ({@link RoleSeedPolicy}). Omitted for {@code client_credentials} (a workload is
     * not an identity — recursion guard). TASK-MONO-263: the {@code account_type} claim
     * is no longer emitted (ADR-032 D5 step 4); {@code roles} is the sole authorization
     * surface.
     *
     * <p>TASK-BE-370 (ADR-MONO-033 S4 assume-tenant — completes ADR-MONO-032 D5 step 2):
     * also emitted on the {@code token_exchange} (assume-tenant) path.
     *
     * <p>TASK-BE-376 (ADR-MONO-035 O1 / step 4a): on the assume-tenant path the
     * {@code roles} are now DERIVED from the SELECTED tenant's ACTIVE entitled domains
     * ({@link OperatorRoleDerivation}) rather than preserved from the operator's base
     * token — the base operator token has no domain-role set to preserve (it is
     * {@code aud=platform-console}, {@code tenant_id='gap'}). See
     * {@link #customizeForAssumeTenant}.
     */
    private static final String CLAIM_ROLES = "roles";

    /**
     * TASK-BE-324: account-service port used to resolve {@code entitled_domains} at
     * issuance time. Autowired by constructor injection.
     */
    private final AccountServicePort accountServicePort;

    public TenantClaimTokenCustomizer(AccountServicePort accountServicePort) {
        this.accountServicePort = accountServicePort;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        OAuth2TokenType tokenType = context.getTokenType();

        boolean isAccessToken = OAuth2TokenType.ACCESS_TOKEN.equals(tokenType);
        boolean isIdToken = ID_TOKEN_TYPE_VALUE.equals(tokenType.getValue());

        if (!isAccessToken && !isIdToken) {
            return;
        }

        AuthorizationGrantType grantType = context.getAuthorizationGrantType();

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            customizeForClientCredentials(context);
        } else if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(grantType)) {
            customizeForAuthorizationCode(context);
        } else if (AuthorizationGrantType.TOKEN_EXCHANGE.equals(grantType)) {
            // TASK-BE-327 (ADR-MONO-020 D2+D3): assume-tenant exchange. The
            // selected tenant + tenant_type are carried on the token context by
            // AssumeTenantAuthenticationProvider; inject them + the SELECTED
            // tenant's entitled_domains (D3, least-privilege, fail-soft).
            customizeForAssumeTenant(context);
        } else if (AuthorizationGrantType.REFRESH_TOKEN.equals(grantType)) {
            // TASK-BE-274 cycle 3: SasRefreshTokenAuthenticationProvider generates a
            // brand-new JWT for the rotated access token using a fresh TokenContext whose
            // authorizationGrantType is REFRESH_TOKEN. The previous no-op assumption
            // (SAS built-in reuse) does not apply to our custom provider — it calls
            // tokenGenerator.generate() which invokes this customizer. Without handling
            // REFRESH_TOKEN here the tenant_id / tenant_type claims are absent from the
            // rotated access token.
            //
            // Strategy: reuse customizeForAuthorizationCode logic which already handles
            // the dual fallback (principal.getDetails() map → ClientSettings Option B →
            // clientName legacy). The principal in the REFRESH_TOKEN context is the same
            // Authentication stored in the OAuth2Authorization at authorization_code time,
            // and the registeredClient carries custom.tenant_id / custom.tenant_type via
            // OAuthClientMapper (Option B). Either path resolves tenant_id correctly.
            customizeForAuthorizationCode(context);
        }
    }

    private void customizeForClientCredentials(JwtEncodingContext context) {
        RegisteredClient client = context.getRegisteredClient();
        String clientId = client.getClientId();

        // Option B: prefer ClientSettings custom keys (set by JPA mapper)
        TenantInfo tenantInfo = extractTenantFromClientSettings(client);

        if (tenantInfo == null) {
            // Fallback: legacy clientName = "tenantId|tenantType" (backward compat for unit tests
            // and any RegisteredClient not built via the JPA mapper)
            tenantInfo = extractTenantFromClientNameOrFail(client, clientId);
        }

        context.getClaims()
                .claim("tenant_id", tenantInfo.tenantId())
                .claim("tenant_type", tenantInfo.tenantType());

        log.debug("TenantClaimTokenCustomizer: injected tenant_id={}, tenant_type={} for clientId={}",
                tenantInfo.tenantId(), tenantInfo.tenantType(), clientId);
    }

    /**
     * Reads tenant info from {@code clientName = "tenantId|tenantType"} with specific
     * fail-closed error messages for blank tenantId / tenantType.
     * Used when the ClientSettings path found no custom keys.
     */
    private TenantInfo extractTenantFromClientNameOrFail(RegisteredClient client, String clientId) {
        String clientName = client.getClientName();
        if (clientName == null || !clientName.contains(METADATA_SEPARATOR)) {
            log.error("SECURITY: client_credentials token issued without tenant metadata. " +
                    "clientId={}, clientName={}", clientId, clientName);
            throw new IllegalStateException(
                    "tenant_id is required for token issuance (fail-closed); " +
                            "clientId=" + clientId + " has no tenant metadata in ClientSettings or clientName. " +
                            "Expected format: '<tenantId>|<tenantType>'");
        }

        String[] parts = clientName.split("\\|", 2);
        String tenantId = parts[0].trim();
        String tenantType = parts.length > 1 ? parts[1].trim() : "";

        if (tenantId.isBlank()) {
            log.error("SECURITY: client_credentials token issued with blank tenant_id. clientId={}", clientId);
            throw new IllegalStateException(
                    "tenant_id must not be blank (fail-closed); clientId=" + clientId);
        }
        if (tenantType.isBlank()) {
            log.error("SECURITY: client_credentials token issued with blank tenant_type. clientId={}", clientId);
            throw new IllegalStateException(
                    "tenant_type must not be blank (fail-closed); clientId=" + clientId);
        }
        return new TenantInfo(tenantId, tenantType);
    }

    private void customizeForAuthorizationCode(JwtEncodingContext context) {
        Authentication principal = context.getPrincipal();
        String clientId = context.getRegisteredClient().getClientId();

        // ADR-MONO-040 Phase 2 (TASK-MONO-295) → Phase 3 part B (TASK-MONO-299):
        // OVERRIDE `sub` to the account UUID — fully satisfying jwt-standard-claims.md
        // (`sub` = account UUID, immutable) so X-User-Id ← sub is restored downstream.
        // Phase 3 part B removes the transitional additive `account_id` claim: every
        // gateway now reads `sub` directly (it has been the account UUID since Phase 2),
        // so the belt-and-suspenders claim is redundant.
        alignSubToAccountId(context, principal);

        String tenantId = extractTenantAttribute(principal, "tenant_id");
        String tenantType = extractTenantAttribute(principal, "tenant_type");

        if (tenantId != null && tenantType != null) {
            context.getClaims()
                    .claim("tenant_id", tenantId)
                    .claim("tenant_type", tenantType);
            log.debug("TenantClaimTokenCustomizer: authorization_code — injected tenant_id={}, " +
                    "tenant_type={} from principal for clientId={}", tenantId, tenantType, clientId);
            populateEntitledDomains(context, tenantId);
            // TASK-BE-369 (ADR-MONO-033 S4 base + S3): roles leg. Platform = the
            // CLIENT's tenant_id (ClientSettings); claim tenant = the resolved
            // tenant_id used for the account_roles lookup key.
            TenantInfo clientInfo = extractTenantFromClientSettings(context.getRegisteredClient());
            String platform = clientInfo != null ? clientInfo.tenantId() : tenantId;
            populateRoles(context, principal, tenantId, platform);
        } else {
            // Fallback: client metadata from ClientSettings (Option B) or clientName (legacy)
            RegisteredClient client = context.getRegisteredClient();
            TenantInfo tenantInfo = extractTenantFromClientSettings(client);
            if (tenantInfo == null) {
                tenantInfo = extractTenantFromClientName(client);
            }

            if (tenantInfo != null && !tenantInfo.tenantId().isBlank() && !tenantInfo.tenantType().isBlank()) {
                context.getClaims()
                        .claim("tenant_id", tenantInfo.tenantId())
                        .claim("tenant_type", tenantInfo.tenantType());
                log.debug("TenantClaimTokenCustomizer: authorization_code — fallback to client " +
                        "tenant metadata tenant_id={} for clientId={}", tenantInfo.tenantId(), clientId);
                populateEntitledDomains(context, tenantInfo.tenantId());
                // TASK-BE-369: roles leg on the client-metadata fallback path. The
                // platform = the client's tenant_id (== tenantInfo.tenantId() here,
                // since this branch resolved tenant from client metadata). With no
                // principal account_id, populateRoles finds stored empty → seeds by
                // platform; a non-consumer platform → seed [] → roles omitted (the
                // correct graceful behaviour).
                populateRoles(context, principal, tenantInfo.tenantId(), tenantInfo.tenantId());
            } else {
                log.error("SECURITY: authorization_code token issued without tenant metadata. " +
                        "clientId={}, principal={}", clientId, principal.getName());
                throw new IllegalStateException(
                        "tenant_id is required for token issuance (fail-closed); " +
                                "neither principal attributes nor client metadata contain tenant context. " +
                                "clientId=" + clientId);
            }
        }
    }

    /**
     * ADR-MONO-040 Phase 2 (TASK-MONO-295) → Phase 3 part B (TASK-MONO-299): align
     * the access/id-token {@code sub} to the account UUID.
     *
     * <p>The account UUID already rides on the principal {@code details} map (set
     * by {@code CredentialAuthenticationProvider}, carried through refresh). When
     * present, <b>override {@code sub} = account UUID</b> — the SAS framework default
     * set {@code sub} = the login principal (email), violating
     * {@code jwt-standard-claims.md} ({@code sub} = account UUID, immutable). Overriding
     * it here restores {@code X-User-Id ← sub} downstream (the contract letter).
     *
     * <p>Phase 3 part B removes the transitional additive {@code account_id} claim
     * that Phase 1 introduced and Phase 2 retained for the redeploy window: every
     * gateway now reads {@code sub} directly, so the claim is redundant.
     *
     * <p>When the principal carries no {@code account_id} (e.g. the client-metadata
     * fallback path, or a non-credential principal), the override is not applied —
     * {@code sub} keeps the framework default. This is the graceful net-zero branch
     * (no UUID to substitute).
     *
     * <p>Operator safety: operator resolution is account_id-only (TASK-MONO-299) and
     * {@code admin_operators.oidc_subject} is backfilled to account_id (part A), so the
     * {@code sub}=account_id token resolves every operator directly.
     */
    private void alignSubToAccountId(JwtEncodingContext context, Authentication principal) {
        String accountId = extractTenantAttribute(principal, "account_id");
        if (accountId != null && !accountId.isBlank()) {
            // sub = account UUID (full jwt-standard-claims.md compliance).
            context.getClaims().subject(accountId);
        }
    }

    /**
     * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2+D3): assume-tenant exchange
     * branch. The SELECTED tenant + its {@code tenant_type} are carried on the
     * {@link AssumeTenantAuthenticationToken} (the context's authorizationGrant),
     * which {@code JwtGenerator} copies verbatim into the encoding context.
     * Injects {@code tenant_id=<selected>} + {@code tenant_type} and reuses the
     * keystone {@link #populateEntitledDomains} <b>verbatim</b> keyed on the
     * SELECTED tenant — least-privilege (NO union across the operator's other
     * assignments, D3) + fail-soft (account down → claim omitted) +
     * recursion-safe (this branch never runs on {@code client_credentials}).
     *
     * <p>Fail-closed on a missing selected tenant: the provider always sets the
     * context attributes, so a blank value here is a wiring bug — reject rather
     * than mint a tenant-less token (auth-service fails closed on missing tenant).
     */
    private void customizeForAssumeTenant(JwtEncodingContext context) {
        // The selected tenant is carried on the authorizationGrant (the
        // AssumeTenantAuthenticationToken), which JwtGenerator copies verbatim into
        // the JwtEncodingContext (unlike arbitrary context.put() attributes, which
        // it does NOT copy).
        String selectedTenantId = null;
        String selectedTenantType = null;
        java.util.List<String> orgScope = null;
        if (context.getAuthorizationGrant() instanceof AssumeTenantAuthenticationToken grant) {
            selectedTenantId = grant.getSelectedTenantId();
            selectedTenantType = grant.getSelectedTenantType();
            orgScope = grant.getOrgScope();
        }

        if (selectedTenantId == null || selectedTenantId.isBlank()
                || selectedTenantType == null || selectedTenantType.isBlank()) {
            log.error("SECURITY: assume-tenant token issued without selected tenant context. "
                    + "tenant_id={}, tenant_type={}", selectedTenantId, selectedTenantType);
            throw new IllegalStateException(
                    "selected tenant_id/tenant_type is required for assume-tenant issuance (fail-closed)");
        }

        context.getClaims()
                .claim("tenant_id", selectedTenantId)
                .claim("tenant_type", selectedTenantType);
        log.debug("TenantClaimTokenCustomizer: assume-tenant — injected tenant_id={}, tenant_type={}",
                selectedTenantId, selectedTenantType);

        // TASK-BE-338 (ADR-MONO-020 D3 amendment): membership-derived data-scope —
        // the v2 replacement for the TASK-BE-337 hardcoded ["*"] bridge. The
        // per-assignment org_scope (department subtree-root ids) is carried on the
        // grant from the admin-service assignment-check result. NET-ZERO: a
        // null/empty org_scope (unset assignment, legacy home-tenant, platform-scope,
        // or an older admin that omits the field) injects ["*"] = whole tenant —
        // byte-identical to the BE-337 behavior. A present non-empty list is injected
        // verbatim (erp expands the subtree roots → descendants for its containment
        // check — TASK-ERP-BE-008). The tenant gate already isolates cross-tenant, so
        // org_scope governs only the department subtree WITHIN the assumed tenant.
        // Only the assume-tenant (operator) token gets org_scope — the base
        // authorization_code token still carries none (least-privilege). Only erp
        // consumes the claim (no cross-domain effect).
        java.util.List<String> effectiveOrgScope =
                (orgScope == null || orgScope.isEmpty()) ? java.util.List.of("*") : orgScope;
        context.getClaims().claim(CLAIM_ORG_SCOPE, effectiveOrgScope);
        log.debug("TenantClaimTokenCustomizer: assume-tenant — injected org_scope={} "
                + "(membership-derived; null/empty → [*] net-zero)", effectiveOrgScope);

        // D3: SELECTED tenant's ACTIVE subscriptions ONLY (no union). Reused verbatim.
        // The fetch ALSO drives the operator `roles` derivation below (one call:
        // entitled_domains + roles ride the same account-service result).
        java.util.List<String> entitled = populateEntitledDomains(context, selectedTenantId);

        // TASK-BE-376 (ADR-MONO-035 O1 / step 4a): DERIVE the operator's domain roles
        // from the SELECTED tenant's ACTIVE entitled domains (the operator-role mirror
        // of RoleSeedPolicy). Replaces TASK-BE-370's preserve-from-base: the base
        // operator token (aud=platform-console, tenant_id='gap') structurally has no
        // domain-role set to preserve — domain roles are meaningful only per assumed
        // tenant. The operator (already fail-closed-verified as assigned) gets the
        // operator role(s) for everything the selected tenant is entitled to, so the
        // domain gateway's `roles` leg admits them. Derives ONLY from entitled domains
        // (never admin_operator_roles — ADR-033 S2 / ADR-034 U5 disjointness). NET-ZERO:
        // empty/all-unknown/failed fetch → both entitled_domains AND roles omitted (the
        // gateway then 403s — least-privilege; ADR-033 S5 fail-soft). Only ADD the roles
        // claim; the tenant_id/tenant_type/org_scope injection above is untouched
        // (BE-338).
        java.util.List<String> derived = OperatorRoleDerivation.fromEntitledDomains(entitled);
        if (!derived.isEmpty()) {
            // The claim value flows into the JdbcOAuth2AuthorizationService store,
            // which serializes via SecurityJackson2Modules' strict allowlist. An
            // ImmutableCollections list (List.of/List.copyOf) is NOT allowlisted and
            // breaks the authorization read-back (userinfo/refresh/revoke). Wrap in a
            // mutable ArrayList (allowlisted) — same reasoning as the HashMap details
            // map in CredentialAuthenticationProvider.
            context.getClaims().claim(CLAIM_ROLES, new java.util.ArrayList<>(derived));
            log.debug("TenantClaimTokenCustomizer: assume-tenant — derived operator roles={} "
                    + "from the selected tenant's entitled_domains={}", derived, entitled);
        }
    }

    /**
     * TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): populates the signed
     * {@code entitled_domains} claim from the tenant's ACTIVE domain subscriptions
     * (queried from account-service at issuance time). Only invoked from the
     * {@code authorization_code}/{@code refresh_token} path — never from
     * {@code client_credentials} (recursion safety: a cc issuance is what mints the
     * Bearer used to call account-service; if the cc path called account it would
     * re-invoke this customizer → infinite recursion).
     *
     * <p><b>fail-soft</b>: any failure (account-service down / circuit-open / timeout /
     * exception) OR an empty result → the claim is omitted and issuance proceeds with
     * the legacy {@code tenant_id} gate (net-zero). Token issuance must never depend on
     * account-service availability, so the exception is swallowed (logged at WARN).
     */
    private java.util.List<String> populateEntitledDomains(JwtEncodingContext context, String tenantId) {
        try {
            java.util.List<String> entitled = accountServicePort.listEntitledDomains(tenantId);
            if (entitled != null && !entitled.isEmpty()) {
                context.getClaims().claim(CLAIM_ENTITLED_DOMAINS, entitled);
                log.debug("TenantClaimTokenCustomizer: injected entitled_domains={} for tenant_id={}",
                        entitled, tenantId);
                return entitled;
            }
        } catch (RuntimeException e) {
            // fail-soft (ADR-MONO-019 keystone): token issuance must not depend on
            // account-service availability. Omit entitled_domains → legacy tenant_id
            // gate still applies (net-zero). Do NOT propagate.
            log.warn("TenantClaimTokenCustomizer: entitled_domains lookup failed for tenant_id={}, "
                            + "omitting claim (fail-soft): {}",
                    tenantId, e.toString());
        }
        // TASK-BE-376: return the fetched list so the assume-tenant branch can derive
        // the operator `roles` from the SAME fetch (entitled_domains + roles ride one
        // call). Empty on failure/empty (the authorization_code callers ignore it).
        return java.util.List.of();
    }

    /**
     * TASK-BE-369 (ADR-MONO-033 S4 base + S3 / ADR-MONO-032 D5 step 2): populates the
     * signed {@code roles} claim. Sourced from the authoritative {@code account_roles}
     * store (via {@link AccountServicePort#listAccountRoles}) when present, else the
     * aud-default {@link RoleSeedPolicy} seed keyed on the client-platform (TASK-MONO-263:
     * consumer-only, decoupled from {@code account_type}). Stored roles are emitted
     * <b>verbatim</b> — never unioned with the seed.
     *
     * <p>Invoked ONLY from {@code customizeForAuthorizationCode} (which also serves
     * {@code refresh_token}). It is NEVER reachable from {@code client_credentials} —
     * same recursion guard as {@link #populateEntitledDomains}: a cc issuance is what
     * mints the Bearer used to call account-service, so a cc-path lookup would
     * re-invoke this customizer → infinite recursion. A workload is also not an
     * identity, so it must carry no domain roles (ADR-033 S4).
     *
     * <p><b>fail-soft</b> (ADR-033 S5): any failure (account-service down / circuit-open /
     * timeout / exception) → fall to the seed; if the seed is also empty → the claim is
     * omitted and issuance proceeds. Token issuance must never depend on account-service
     * availability — the exception is swallowed (logged at WARN), never rethrown.
     *
     * @param context          the encoding context whose claims receive {@code roles}
     * @param principal        the authenticated principal (carries {@code account_id}
     *                         in its details map)
     * @param claimTenantId    the resolved tenant_id (the {@code account_roles} lookup key)
     * @param platformTenantId the registered client's tenant_id (the platform, for the seed)
     */
    private void populateRoles(JwtEncodingContext context, Authentication principal,
                               String claimTenantId, String platformTenantId) {
        String accountId = extractTenantAttribute(principal, "account_id");

        java.util.List<String> stored = java.util.List.of();
        try {
            if (accountId != null) {
                stored = accountServicePort.listAccountRoles(claimTenantId, accountId);
            }
        } catch (RuntimeException e) {
            // fail-soft (ADR-MONO-033 S5): token issuance must not depend on
            // account-service availability. Fall to the seed below. Do NOT propagate.
            log.warn("TenantClaimTokenCustomizer: account_roles lookup failed for tenant_id={}, "
                            + "account_id={}, falling to seed (fail-soft): {}",
                    claimTenantId, accountId, e.toString());
            stored = java.util.List.of();
        }

        java.util.List<String> roles = (stored != null && !stored.isEmpty())
                ? stored
                : RoleSeedPolicy.seed(platformTenantId);

        if (roles != null && !roles.isEmpty()) {
            // SecurityJackson2Modules allowlist: the seed returns an
            // ImmutableCollections list (List.of), which is NOT allowlisted by the
            // JdbcOAuth2AuthorizationService store and breaks the authorization
            // read-back (userinfo/refresh/revoke). Wrap in a mutable ArrayList
            // (allowlisted) — mirrors the HashMap details map in
            // CredentialAuthenticationProvider. (Stored account_roles are already an
            // ArrayList from Jackson; the seed path is the one that needs this.)
            context.getClaims().claim(CLAIM_ROLES, new java.util.ArrayList<>(roles));
            log.debug("TenantClaimTokenCustomizer: injected roles={} (platform={}, source={})",
                    roles, platformTenantId,
                    (stored != null && !stored.isEmpty()) ? "stored" : "seed");
        }
    }

    /**
     * Reads tenant info from the client's {@link ClientSettings} custom keys.
     * Returns null if either key is missing, signalling the caller to try the fallback.
     */
    private TenantInfo extractTenantFromClientSettings(RegisteredClient client) {
        ClientSettings cs = client.getClientSettings();
        Object rawTenantId = cs.getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        Object rawTenantType = cs.getSetting(OAuthClientMapper.SETTING_TENANT_TYPE);

        if (rawTenantId instanceof String tid && rawTenantType instanceof String ttype
                && !tid.isBlank() && !ttype.isBlank()) {
            return new TenantInfo(tid.trim(), ttype.trim());
        }
        return null;
    }

    /**
     * Legacy fallback: reads tenant info from {@code clientName = "tenantId|tenantType"}.
     * Used for RegisteredClient instances built without the JPA mapper (e.g. some unit tests).
     * Returns null if the format is absent or malformed.
     */
    private TenantInfo extractTenantFromClientName(RegisteredClient client) {
        String clientName = client.getClientName();
        if (clientName != null && clientName.contains(METADATA_SEPARATOR)) {
            String[] parts = clientName.split("\\|", 2);
            String tid = parts[0].trim();
            String ttype = parts.length > 1 ? parts[1].trim() : "";
            if (!tid.isBlank() && !ttype.isBlank()) {
                return new TenantInfo(tid, ttype);
            }
        }
        return null;
    }

    /**
     * Extracts a tenant attribute from the authenticated principal's details map.
     */
    private String extractTenantAttribute(Authentication principal, String attributeName) {
        if (principal == null) {
            return null;
        }
        Object details = principal.getDetails();
        if (details instanceof java.util.Map<?, ?> detailsMap) {
            Object value = detailsMap.get(attributeName);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    /** Value object carrying the two required tenant fields. */
    private record TenantInfo(String tenantId, String tenantType) {}
}
