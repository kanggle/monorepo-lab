# TASK-ERP-BE-019 — Re-point erp data-scope claim parsing onto the shared `AbacDataScope` reader

**Status:** done

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (security-sensitive authorization code — net-zero refactor)

---

## Goal

Consolidate erp's three **inline** `org_scope`/`data_scope` JWT-claim parsing sites onto the shared canonical reader `com.example.security.jwt.AbacDataScope` (`libs/java-security`, introduced by ADR-MONO-025 / TASK-MONO-214), **without any behaviour change** (net-zero refactor).

ADR-MONO-025 § D5 / § D7 step 2 deliberately deferred the erp re-point as OPTIONAL — erp's inline reader was the verified behavioural *reference* the shared helper was built to match, so re-pointing carried regression risk for no behaviour change and was left out of 1단계. The shared reader is now proven across two more domains (wms enforcement TASK-MONO-215/BE-349/BE-350; the access-condition lib family). This task closes the duplication: erp's claim **parsing** becomes the canonical reader, so a future fix to claim parsing (new delimiter, new alias) is made once in `libs/java-security` rather than copy-pasted in three erp files.

This re-points **parsing only**. Each site's surrounding *domain interpretation* (masterdata subtree containment, read-model platform/zero-scope distinction, the `client_credentials → ["*"]` default) is preserved verbatim — `AbacDataScope` owns parsing; the domain owns meaning (per `platform/abac-data-scope.md` § 3).

## Scope

**In scope** — three erp parsing sites:

1. `masterdata-service` `infrastructure/security/ActorContextJwtAuthenticationConverter` — the `extractClaim(jwt, "org_scope", "data_scope")` invocation (line ~38) that lifts the scope token set into `ActorContext`. Replace with `AbacDataScope.fromClaimValues(jwt.getClaim("org_scope"), jwt.getClaim("data_scope")).tokens()`. The `client_credentials`-empty→`["*"]` default and roles `extractClaim(...)` (a separate RBAC concern) stay unchanged.
2. `approval-service` `infrastructure/security/ActorContextJwtAuthenticationConverter` — identical change (mirror of masterdata).
3. `read-model-service` `presentation/security/ReadAuthorizationGate.orgScope(jwt)` — replace the private `extractOrgScope(jwt)` token-parsing loop with `AbacDataScope.fromClaimValues(...)`. **Preserve** the `claimPresent` (`jwt.hasClaim`) distinction so explicit empty `org_scope=[]` → zero-scope (`OrgScope.of(Set.of())`) and absent → `OrgScope.platform()` — semantics `AbacDataScope` does not carry. Use `AbacDataScope.isUnrestricted()` for the `"*"` → platform branch and `.tokens()` for the bounded branch.

**Out of scope (unchanged):**
- The token PRODUCER (`TenantClaimTokenCustomizer` — still emits `org_scope`; ADR-025 § D5).
- Domain interpretation logic: `RoleScopeAuthorizationAdapter` subtree walk, `JwtBackedAuthorizationAdapter` flat match, `QueryEmployeeOrgViewUseCase` / `QueryApprovalFactUseCase` / `QueryDelegationFactUseCase` `expandOrgScope`, `OrgScope` VO. These consume the parsed token set; they are not re-implemented.
- Roles/scopes (RBAC) claim parsing (`extractClaim(jwt,"roles",…)`, `extractScopesAndRoles`, `extractEntitledDomains`) — a different claim concern, left as-is.
- `notification-service` — reads no `org_scope` (nothing to re-point).
- `libs/java-security` `AbacDataScope` itself — used as-is, no change.

## Acceptance Criteria

- **AC-1** — The three sites parse the scope claim via `AbacDataScope.fromClaimValues(...)`; no erp file retains an inline `org_scope`/`data_scope` token-parsing loop (the roles/entitled_domains loops may remain — different claims).
- **AC-2 (net-zero)** — All existing erp tests pass unchanged in behaviour: `ActorContextJwtAuthenticationConverterTest` (both services), `ReadAuthorizationGateTest`, `RoleScopeAuthorizationAdapterTest`, `QueryEmployeeOrgViewUseCaseTest`, `QueryApprovalFactUseCaseTest`, and the Testcontainers ITs (`DataScopeSubtreeIntegrationTest`, `OrgScopeReadFilterIntegrationTest`, `DelegationIntegrationTest`). No test assertion is weakened to accommodate the change.
- **AC-3** — `client_credentials` machine token with no scope still resolves to `["*"]` (platform); unrestricted `["*"]`, bounded set, absent (net-zero platform), and explicit empty `[]` (read-model zero-scope) all behave exactly as before.
- **AC-4** — "Integration (erp-platform, Testcontainers)" CI job is GREEN (authoritative behavioural gate — the Docker-free `:check` does not exercise the full token→authz path end-to-end).
- **AC-5** — No producer change, no contract change, no ADR decision change (this is execution of the ADR-025 § D7 optional step; the doc clarification is TASK-MONO-229).

## Related Specs

- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (3-stage authz)
- `projects/erp-platform/specs/services/read-model-service/architecture.md` (§ data-scope read filter)
- `projects/erp-platform/specs/services/approval-service/architecture.md`

## Related Contracts

- `platform/abac-data-scope.md` — § 1 (claim + aliases), § 2 (semantics), § 3 (per-domain interpretation; erp = department subtree-root ids). The canonical reader requirement this task fulfils.
- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § D5 / § D7 (the deferred-optional re-point this executes).

## Edge Cases

- **Trim divergence** — the inline `extractClaim` adds collection elements un-trimmed (only blank-filtered); `AbacDataScope` trims each token. Scope tokens are department-ids copied verbatim from `operator_tenant_assignment.org_scope` (no surrounding whitespace in practice), so this is net-zero; trimming is the stricter/more-correct behaviour. Confirm no existing test asserts an un-trimmed token.
- **Token ordering** — inline uses `HashSet` (unordered); `AbacDataScope.tokens()` is a `Set.copyOf` of a `LinkedHashSet`. Downstream uses set membership (`contains`/subtree walk) — order-independent. Net-zero.
- **Set mutability** — the converter previously passed a mutable `HashSet` into `ActorContext`; wrap `AbacDataScope.tokens()` in a `new HashSet<>(...)` (or reassign for the `client_credentials` default) so no downstream mutation hits the immutable `Set.copyOf`.
- **read-model `claimPresent`** — must NOT be lost: `AbacDataScope.isEmpty()` is true for both absent and explicit-empty; the `jwt.hasClaim("org_scope") || jwt.hasClaim("data_scope")` check stays to distinguish them (domain-local fail-closed hardening per contract § 2 note).

## Failure Scenarios

- **F1 — silently widening a scoped operator** — if the re-point dropped a token or mis-classified `[]` as platform, a scoped operator would see out-of-scope rows. Guarded by AC-2/AC-3 + the ITs (scoped list narrowing, out-of-scope 404).
- **F2 — breaking the `client_credentials` machine path** — if the empty-default branch were removed, machine tokens would lose platform scope and reads would deny. Guarded by AC-3 + the converter unit tests.
- **F3 — net-zero violation surfacing only in Testcontainers** — local `:check` (Docker-free) runs the converter/gate UNIT tests (which directly exercise parsing) but not the end-to-end token→authz IT path; AC-4 CI Integration is the authoritative gate (per `feedback_spring_boot_diagnostic_patterns` § 14).
