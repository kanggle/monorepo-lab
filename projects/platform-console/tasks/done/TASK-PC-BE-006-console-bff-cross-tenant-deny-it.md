# Task ID

TASK-PC-BE-006

# Title

console-bff cross-tenant pass-through deny IT (ADR-MONO-018 D5 — federation isolation regression, console-bff slice)

# Status

done

# Owner

platform-console (console-bff)

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

- **depends on**: ADR-MONO-018 D5 (ACCEPTED) + the existing console-bff IT harness (`AbstractConsoleBffIntegrationTest` + `OperatorOverviewIntegrationTest`, AC-12 / PC-BE-001..005). No code dependency — test-only.
- **origin**: ADR-MONO-018 D5 lean gap-fill (the producer-side per-domain ITs already exist — wms `OidcAuthIntegrationTest`, scm `MultiTenantIsolationIntegrationTest`, finance `CrossTenantHttpIntegrationTest`; GAP admin tenant-scope ITs; the console-bff cross-tenant pass-through deny IT is the one genuine gap on the console side). See § Related Specs.
- **prerequisite for**: closes the console-bff slice of the ADR-018 D5 isolation regression surface. The erp HTTP cross-tenant IT (erp-platform) is the sibling gap-fill, independent.
- **model**: Opus (ADR-MONO-013 § D6 row 8 — "isolation → Opus").

---

# Goal

Add an integration test asserting the ADR-MONO-018 D5 / ADR-MONO-017 D6 **producer-side authority invariant** at the console-bff slice: when a **forged cross-tenant** request flows through the BFF (an operator JWT whose `tenant_id` differs from the requested `X-Tenant-Id`, with every downstream producer rejecting the foreign tenant), the BFF

1. forwards the inbound `X-Tenant-Id` (and the credential per the D4 dispatch table) **verbatim** to every downstream domain — **no central BFF tenant re-scoping or interceptor**; and
2. surfaces each producer's denial as a per-card `PERMISSION_DENIED` degrade **inside a 200 composition envelope** — the BFF never masks, re-scopes, or short-circuits the fan-out on a tenant basis.

This attests that tenant authority is **producer-side** (each producer's `TenantClaimValidator` is the gate), which is the Phase 8 federation-hardening invariant ADR-018 D5 exists to regression-protect.

# Scope

## In Scope

**Spec PR**: this task md + `projects/platform-console/tasks/INDEX.md` ready entry.

**Impl PR**:

- **`projects/platform-console/apps/console-bff/src/test/java/com/kanggle/platformconsole/bff/integration/CrossTenantDenyIntegrationTest.java`** (new) — extends `AbstractConsoleBffIntegrationTest`, reuses the `OperatorOverviewIntegrationTest` 5-MockWebServer-stub pattern (gap/wms/scm/finance/erp + JWKS). One forged-cross-tenant scenario:
  - inbound operator JWT `tenant_id="gap"` + `X-Tenant-Id="scm"` (foreign relative to the token home) + `X-Operator-Token`;
  - all 5 producer stubs return `403`;
  - assert the BFF forwards `X-Tenant-Id: scm` **verbatim** to each stub that fires (RecordedRequest header equality) — no rewrite to the token's `gap`;
  - assert the composition returns `200` with each fired card degraded `PERMISSION_DENIED` (producer-side denial surfaced, not masked);
  - assert no central BFF short-circuit (the outbound calls actually fire — the BFF is a faithful pass-through, not a tenant gate).

**Verification**: `./gradlew :projects:platform-console:apps:console-bff:test` green locally + the "Integration (platform-console console-bff, Testcontainers + WireMock JWKS)" CI job green on the impl PR.

## Out of Scope

- **Producer-side ITs** — already exist (wms/scm/finance/GAP); this task is the console-bff slice only. (erp HTTP cross-tenant IT = sibling gap-fill task in erp-platform.)
- **Production code change** — test-only. `console-bff` `src/main` byte-unchanged.
- **New tenant gate / interceptor** — the invariant being attested is precisely that the BFF has NONE; adding one would be a contract defect (ADR-017 D6.A).
- **Contract / ADR change** — `console-integration-contract.md` § 2.4.9 + all ADRs byte-unchanged.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this md + INDEX ready entry, no impl code.
- **AC-2 (IT authored)**: `CrossTenantDenyIntegrationTest` exists, `@Tag("integration")`, extends `AbstractConsoleBffIntegrationTest`, one forged-cross-tenant fan-out scenario.
- **AC-3 (verbatim pass-through asserted)**: each fired downstream `RecordedRequest` carries `X-Tenant-Id` equal to the inbound value (`scm`), NOT the token's `tenant_id` (`gap`) — proves no BFF re-scoping.
- **AC-4 (producer-side denial surfaced)**: all-producer `403` → `200` composition envelope with each fired card `PERMISSION_DENIED` (mirrors the existing per-leg-forbidden mapping); the BFF does not mask or short-circuit.
- **AC-5 (no central gate)**: the outbound calls actually fire for the foreign tenant (the BFF is not itself a tenant gate) — asserted via stub request receipt.
- **AC-6 (no prod change)**: `git diff --stat origin/main -- 'projects/platform-console/apps/console-bff/src/main/**'` = empty.
- **AC-7 (CI green)**: the console-bff Integration CI job passes on the impl PR.

# Related Specs

- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D5 — the isolation regression surface this attests (console-bff slice).
- `docs/adr/ADR-MONO-017-*` D6 — operator-token + `X-Tenant-Id` pass-through (D6.A "forwarded verbatim"); producer-side authority.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 / 2.4.9.1 — the operator-overview composition + credential dispatch table the IT exercises.
- Existing producer-side D5 coverage (attestation — already green, no work here): wms `apps/master-service/.../OidcAuthIntegrationTest` (`tenant_id=fan-platform → 403 TENANT_FORBIDDEN`), scm `apps/procurement-service/.../MultiTenantIsolationIntegrationTest`, finance `apps/account-service/.../CrossTenantHttpIntegrationTest` (`tenant_id=wms → 403 TENANT_FORBIDDEN`), GAP admin `AdminAuditTenantScopeIntegrationTest`.
- Pattern source: `apps/console-bff/.../integration/OperatorOverviewIntegrationTest` (5-stub fan-out + JWT mint + RecordedRequest header assertions + per-leg degrade mapping).

# Related Contracts

- None changed. `console-integration-contract.md` § 2.4.9 is exercised read-only; traceparent/tenant pass-through is transport, not a contract change.

# Edge Cases

- **Finance MVP short-circuit** — the existing harness shows finance does not fire without `X-Finance-Default-Account-Id` (option (b) MISSING_PREREQUISITE). Assert verbatim pass-through + denial only on the legs that actually fire (gap/wms/scm/erp); treat finance per its existing MVP behaviour (do not assert a finance outbound unless the header is supplied).
- **MockWebServer counter lifetime-accumulation** — use snapshot-and-diff for request counts (PC-FE-013 lesson), not absolute zero.
- **`X-Tenant-Id` rewrite regression** — if a future change makes the BFF rewrite `X-Tenant-Id` to the token's `tenant_id`, AC-3 must fail (that is the regression this IT guards).

# Failure Scenarios

- **BFF re-scopes the tenant** → AC-3 fails (downstream sees `gap` not `scm`). This is the regression the IT exists to catch.
- **BFF adds a central tenant gate that short-circuits before fan-out** → AC-5 fails (no outbound fires). Producer-side authority violated.
- **`console-bff/src/main` edited** → AC-6 fails. Test-only task.
- **ADR / contract edited** → reject; byte-unchanged.

# Verification

1. Spec PR: this md + INDEX.
2. Impl PR: `CrossTenantDenyIntegrationTest` present; AC-6 grep zero; console-bff Integration CI job green.
3. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Opus 4.7 (isolation → Opus, ADR-013 § D6 row 8) / 리뷰=Opus 4.7 (BE-303 3-dim + AC-3 verbatim-pass-through + AC-5 no-central-gate + AC-6 prod byte-unchanged).
