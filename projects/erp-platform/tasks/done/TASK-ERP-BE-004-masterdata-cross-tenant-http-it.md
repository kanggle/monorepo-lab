# Task ID

TASK-ERP-BE-004

# Title

masterdata-service HTTP-layer cross-tenant isolation IT (ADR-MONO-018 D5 — federation isolation regression, erp slice)

# Status

done

# Owner

erp-platform (masterdata-service)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: ADR-MONO-018 D5 (ACCEPTED) + the existing masterdata-service IT harness (`AbstractMasterdataIntegrationTest`, Testcontainers MySQL + Kafka + JWKS MockWebServer, TASK-ERP-BE-001). No code dependency — test-only.
- **origin**: ADR-MONO-018 D5 lean gap-fill. The D5 audit found producer-side cross-tenant ITs already exist for wms (`OidcAuthIntegrationTest`), scm (`MultiTenantIsolationIntegrationTest`), finance (`CrossTenantHttpIntegrationTest`), and GAP (`AdminAuditTenantScopeIntegrationTest`); erp had only the `TenantClaimValidator` **unit** test + a controller **slice** test — no HTTP-layer IT exercising the `TenantClaimEnforcer` filter + `onAuthenticationFailure` mapping end-to-end. This task + the console-bff IT (TASK-PC-BE-006, DONE) are the two genuine D5 gaps.
- **prerequisite for**: closes the erp slice of the ADR-018 D5 isolation regression surface (with TASK-PC-BE-006 = console-bff slice done, this completes the D5 lean gap-fill).
- **model**: Opus (ADR-MONO-013 § D6 row 8 — "isolation → Opus").

---

# Goal

Add an HTTP-layer integration test asserting masterdata-service's fail-closed tenant gate end-to-end (the `TenantClaimValidator` decode-time check + `SecurityConfig.onAuthenticationFailure` 403 mapping + `TenantClaimEnforcer` defense-in-depth filter, exercised through the real `SecurityFilterChain` + a Testcontainers-backed context). erp's tenant model accepts the `*` wildcard (platform-scope) in addition to the domain tenant, so the IT pins **both** edges:

1. a valid RS256 token with a **foreign** `tenant_id` (e.g. `scm`) → `403 TENANT_FORBIDDEN`;
2. the `*` **wildcard** tenant → accepted (request reaches the controller, `2xx`);
3. the matching `erp` tenant → accepted (`2xx`) — sanity;
4. no token → `401`.

This guards the erp producer-side authority invariant that ADR-018 D5 regression-protects (and that the wms/scm/finance producer ITs already cover for their domains).

# Scope

## In Scope

**Spec PR**: this task md + `projects/erp-platform/tasks/INDEX.md` ready entry.

**Impl PR**:

- **`projects/erp-platform/apps/masterdata-service/src/test/java/com/example/erp/masterdata/integration/CrossTenantHttpIntegrationTest.java`** (new) — extends `AbstractMasterdataIntegrationTest`, `@AutoConfigureMockMvc`, mirrors the finance `CrossTenantHttpIntegrationTest` pattern (generate an RSA key in `@BeforeAll` + `publishJwks(...)`; mint RS256 tokens with `tenant_id` against `http://test-issuer`). Drives `GET /api/erp/masterdata/employees` (a v1-live read endpoint, `authenticated()`):
  - `tenant_id=scm` → `403`, body `$.code == TENANT_FORBIDDEN`;
  - `tenant_id=*` → `2xx` (wildcard accepted — erp-specific edge);
  - `tenant_id=erp` → `2xx` (matching tenant, sanity);
  - no `Authorization` → `401`.

**Verification**: `./gradlew :projects:erp-platform:apps:masterdata-service:test` green + the "Integration (erp-platform, Testcontainers)" CI job green on the impl PR.

## Out of Scope

- **Other-domain ITs** — wms/scm/finance/GAP already covered; console-bff slice = TASK-PC-BE-006 (DONE).
- **Production code change** — test-only. `masterdata-service/src/main` byte-unchanged.
- **New endpoint / contract / ADR change** — exercises the existing `/api/erp/masterdata/employees` read surface; `masterdata-api.md` + all ADRs byte-unchanged.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this md + INDEX ready entry, no impl code.
- **AC-2 (IT authored)**: `CrossTenantHttpIntegrationTest` exists, `@Tag("integration")` (inherited), extends `AbstractMasterdataIntegrationTest`, `@AutoConfigureMockMvc`, RS256-JWT helper.
- **AC-3 (cross-tenant 403)**: `tenant_id=scm` → `403` + `$.code == TENANT_FORBIDDEN`.
- **AC-4 (wildcard accepted)**: `tenant_id=*` → `2xx` (the request passes the tenant gate and reaches the controller — erp-specific platform-scope edge).
- **AC-5 (matching accepted + no-token 401)**: `tenant_id=erp` → `2xx`; no token → `401`.
- **AC-6 (no prod change)**: `git diff --stat origin/main -- 'projects/erp-platform/apps/masterdata-service/src/main/**'` = empty.
- **AC-7 (CI green)**: the "Integration (erp-platform, Testcontainers)" CI job passes on the impl PR.

# Related Specs

- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D5 — the isolation regression surface this attests (erp slice).
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` — Service Type `rest-api`; § Multi-tenancy (tenant_id ∈ {erp, *}); the read endpoints exercised.
- `projects/erp-platform/specs/contracts/masterdata-api.md` — the `/api/erp/masterdata/employees` read surface (exercised read-only).
- Sibling producer-side D5 coverage (attestation — already green): wms `OidcAuthIntegrationTest`, scm `MultiTenantIsolationIntegrationTest`, finance `CrossTenantHttpIntegrationTest` (the pattern mirrored here), GAP `AdminAuditTenantScopeIntegrationTest`; console-bff `CrossTenantDenyIntegrationTest` (TASK-PC-BE-006).

# Related Contracts

- None changed. `masterdata-api.md` read surface exercised read-only; no API/event contract change.

# Edge Cases

- **Empty DB → list returns 200 empty** — the wildcard/matching legs assert `2xx`, robust to an empty employees table (no seed required).
- **Decode-time vs filter rejection** — cross-tenant is rejected at TWO layers (the `TenantClaimValidator` decode check mapped to 403 by `onAuthenticationFailure`, and the `TenantClaimEnforcer` filter). Either path yields `403 TENANT_FORBIDDEN`; AC-3 asserts the HTTP outcome, not which layer fired.
- **Converter required claims** — `ActorContextJwtAuthenticationConverter` requires `sub` + `tenant_id`; the token helper sets both (roles optional).

# Failure Scenarios

- **erp tenant gate regresses to allow a foreign tenant** → AC-3 fails. This is the regression the IT guards.
- **wildcard stops being accepted** → AC-4 fails (platform-scope `*` operator tokens would break).
- **`masterdata-service/src/main` edited** → AC-6 fails. Test-only.
- **ADR / contract edited** → reject; byte-unchanged.

# Verification

1. Spec PR: this md + INDEX.
2. Impl PR: `CrossTenantHttpIntegrationTest` present; AC-6 grep zero; "Integration (erp-platform, Testcontainers)" CI job green.
3. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Opus 4.7 (isolation → Opus, ADR-013 § D6 row 8) / 리뷰=Opus 4.7 (BE-303 3-dim + AC-3 403 TENANT_FORBIDDEN + AC-4 wildcard-accept + AC-6 prod byte-unchanged).
