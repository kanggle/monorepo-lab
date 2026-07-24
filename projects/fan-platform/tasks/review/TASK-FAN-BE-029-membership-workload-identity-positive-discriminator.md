# Task ID

TASK-FAN-BE-029

# Title

membership /internal workload-identity: replace negative tenant_id discriminator with a positive scope check

# Status

review

# Owner

backend

# Task Tags

- code
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

# Goal

Supersedes TASK-FAN-INT-004, whose premise (a local docker-compose S2S credential wiring gap) was **falsified by live diagnosis**. The credentials are correct and IAM DOES mint a token; the real defect is a token-shape contract conflict in membership-service.

`membership-service`'s `WorkloadIdentityAuthoritiesConverter` (the `/internal/**` chain) grants `ROLE_INTERNAL` only to a token that carries **no** `tenant_id` claim (a **negative** discriminator). But the real IAM `client_credentials` token minted for `community-service-client` is tenant-scoped and **carries** `tenant_id="fan-platform"` (the `TenantClaimTokenCustomizer` stamps it fail-closed on every grant — it cannot be suppressed). So the converter rejects the real token → `403 "Workload identity required"` → community-service's `HttpMembershipChecker` fail-closes → **every `MEMBERS_ONLY`/`PREMIUM` community post read returns 403, even for an active PREMIUM member.** This is a latent production defect (not local-only); it shipped green because `AccessCheckIntegrationTest`/`WorkloadIdentityAuthoritiesConverterTest` minted a fabricated `tenant_id`-less token that never matched the real IAM shape.

After this task: the converter recognizes the workload token by a **positive** discriminator — the required `membership.read` scope — regardless of `tenant_id`, and the tests exercise the real IAM token shape (`tenant_id` present). An end-user token (user scopes, no `membership.read`) is still rejected with 403.

Live-verified root cause: the minted cc token claims are `{sub=aud=community-service-client, scope=["membership.read"], tenant_id="fan-platform", tenant_type="B2C"}`, no `client_id`/`azp`.

---

# Scope

## In Scope

- `WorkloadIdentityAuthoritiesConverter.isWorkloadIdentity`: grant `ROLE_INTERNAL` by the **positive** marker — presence of the required workload scope `membership.read` (read from `scope` as array or space-delimited string, or `scp` array). Drop the `tenant_id`-absence check entirely.
- Correct the tests that encoded the wrong (no-`tenant_id`) contract so CI exercises the real IAM shape: `JwtTestHelper.signWorkloadToken` (add `tenant_id`/`tenant_type`, `scope=["membership.read"]`, `sub==aud`, no `client_id`), `WorkloadIdentityAuthoritiesConverterTest`, and the misleading premise comment in `FanTenantGatePolicyTest` (mechanism preserved).
- Verify no fail-open: an end-user fan token (scopes `openid/profile/email/tenant.read`, no `membership.read`) still gets no `ROLE_INTERNAL` → 403; no-token → 401.

## Out of Scope

- **No IAM (iam-platform) change.** The canonical shape MANDATES `tenant_id` on `client_credentials` (`jwt-standard-claims.md`) and the customizer fail-closes on blank tenant — removing it would violate the spec + break every `TenantClaimValidator` at the edge. The receiver, not the issuer, is the defect.
- No change to the `/internal/**` chain wiring, the internal JWT decoder, or the end-user chain.
- No docker-compose / local-overlay change (INT-004's falsified approach).
- Sibling services (artist/community/notification) have no `/internal` receiver chain → untouched.

---

# Acceptance Criteria

- [ ] A real-shaped IAM cc token (`tenant_id` present + `scope` containing `membership.read`) receives `ROLE_INTERNAL` → `GET /internal/membership/access` returns 200.
- [ ] An end-user fan token (tenant-pinned, no `membership.read` scope) receives NO `ROLE_INTERNAL` → 403 `FORBIDDEN`; no token → 401 (fail-closed preserved, ADR-MONO-005 AC-5).
- [ ] `WorkloadIdentityAuthoritiesConverterTest` asserts the real contract: tenant-scoped cc token with `membership.read` → role granted; user-scoped token → no role. Scope accepted as JSON array, space-delimited string, and `scp`.
- [ ] `AccessCheckIntegrationTest` / `InternalAuthIntegrationTest` pass on the fan Integration (Testcontainers) CI lane using the real-shaped (`tenant_id`-bearing) workload token.
- [ ] End-to-end (local demo, once membership-service redeployed): an active PREMIUM member reads a PREMIUM post → 200; a non-member → 403.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/security-rules.md` (internal-only surface MUST require exactly one of: subject allow-list OR required scope — never tenant_id absence)
- `platform/contracts/jwt-standard-claims.md` (tenant_id issued on every grant incl. client_credentials; machine tokens authorize on the scope axis)
- `specs/services/membership-service/architecture.md`
- `specs/integration/iam-integration.md`

# Related Skills

- `.claude/skills/backend/` (see `.claude/skills/INDEX.md`)

---

# Related Contracts

- `specs/contracts/http/membership-api.md` — internal `GET /internal/membership/access` (workload identity, fail-closed)

---

# Target Service

- `membership-service`

---

# Architecture

Follow:

- `specs/services/membership-service/architecture.md`
- Precedent: ecommerce order-service `SystemClientSubjectValidator` — positive marker, tenant_id-agnostic.

---

# Implementation Notes

- Positive discriminator = required scope `membership.read` (what IAM V0009 grants `community-service-client`). Read scopes robustly (`scope` array / space-delimited string / `scp` array).
- Do NOT add a subject allow-list AND a scope check both — `security-rules.md` says "exactly one of". Scope is the machine-token axis; keep it single.
- The internal JWT decoder does not tenant-pin, so a tenant-bearing token is valid there; only the converter's authority grant needed fixing.

---

# Edge Cases

- cc token with `tenant_id` present (the real shape) → MUST be accepted (the regression).
- scope as JSON array vs space-delimited string vs `scp` array → all honored.
- end-user token with `tenant_id` but user scopes (no `membership.read`) → rejected.
- token with no scope claim at all → rejected.
- author/operator reads short-circuit before the HTTP call (unaffected).

---

# Failure Scenarios

- Fail-open regression: relaxing so a user token gets `ROLE_INTERNAL` — the end-user-token test is the guard.
- Wrong scope: requiring a scope IAM does not grant (`internal.membership.read` — the old test's fabricated value) → all calls 403. Required scope MUST match V0009 (`membership.read`).
- Reintroducing any `tenant_id`-based gate → rejects the real token again.

---

# Test Requirements

- Unit: `WorkloadIdentityAuthoritiesConverterTest` (real cc shape → role; user token → no role; scope array/string/scp forms).
- Integration (fan Testcontainers): `AccessCheckIntegrationTest` + `InternalAuthIntegrationTest` via the corrected real-shaped workload token.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added / corrected to the real IAM contract
- [ ] Tests passing (fan Integration Testcontainers CI lane authoritative)
- [ ] Contracts updated if needed (none expected)
- [ ] Specs updated first if required (none expected)
- [ ] Ready for review
