# TASK-INT-024: web-store real-GAP e2e — assert account_type=CONSUMER claim end-to-end

> **Status: ready**

## Goal

ADR-MONO-021 § 3.3 **step 3 (D4 step 3)** — extend the TASK-INT-023 GAP-backed web-store e2e to assert that the `account_type=CONSUMER` claim (now emitted by GAP as of TASK-BE-329, set explicitly at provisioning by TASK-BE-330) survives the **full OIDC round-trip** into the web-store NextAuth session, exposed on `GET /api/auth/session`.

This is the e2e verification layer on top of the deterministic auth-service `FormLoginIntegrationTest` (real-MySQL, proves the claim is on the access + id token) — it proves the claim further propagates `GAP id_token → NextAuth profile() → jwt() → session() → /api/auth/session`.

## Background

- TASK-INT-023 stood up a lean real-GAP stack (`docker-compose.gap-e2e.yml` + consumer seed + `loginAsSeededConsumer`) and verified the RP-initiated logout AC-1. Its background note recorded that the GAP token carried **no** `account_type` claim — that is exactly what BE-329 changed.
- web-store `auth.ts` maps `profile.account_type → session.accountType` and rejects a non-CONSUMER at the `signIn`/`session` callbacks. With the claim now present and CONSUMER, a positive assertion closes the contract.

## Scope

- `apps/web-store/e2e/account-type-claim.spec.ts` (NEW): login as the seeded consumer → assert `GET /api/auth/session` returns `accountType === 'CONSUMER'` + a truthy `accountId`. Gated by `shouldSkipGap()` (default `SKIP_GAP_E2E=1` CI skips it — no regression).
- `apps/web-store/e2e/fixtures/gap-consumer-seed.sql`: set `account_type='CONSUMER'` explicitly on the seeded credential row (BE-330 D2 semantics) + refresh the now-outdated "no account_type claim is emitted" comment block.
- Verification requires a GAP image built from BE-329+ (the running TASK-INT-023 / federation-e2e GAP predates account_type — it must be rebuilt from current `main`).

Out of scope: un-gating an authenticated gateway path (the ADR's *optional* extension — deferred; the 403-on-absent is already resolved by BE-329, and the gateway filters are unchanged). No CI workflow edit here (the existing `CI-GAP-E2E-HANDOFF.md` nightly job runs the whole `SKIP_GAP_E2E=0` web-store suite, which now includes this spec).

## Acceptance Criteria

- **AC-1** Against a real GAP built from BE-329+, the new spec passes: seeded-consumer login → `/api/auth/session` `accountType === 'CONSUMER'`.
- **AC-2** The spec is gated by `SKIP_GAP_E2E` so the default CI run (=1) skips it — no regression to the nightly frontend-e2e job.
- **AC-3** web-store `tsc --noEmit` stays clean; `docker compose -f docker-compose.gap-e2e.yml config` validates.
- **AC-4** The seed sets `account_type='CONSUMER'` explicitly (the column default would also yield CONSUMER, but the explicit value documents the contract and exercises BE-330 D2).

## Related Specs

- `docs/adr/ADR-MONO-021-account-type-claim-source.md` § 3.3 step 3
- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md`
- `platform/contracts/jwt-standard-claims.md` § account_type

## Related Contracts

- GAP V0012 `ecommerce-web-store-client` (scopes incl. `ecommerce.consumer`).
- `platform/contracts/jwt-standard-claims.md` § account_type (CONSUMER|OPERATOR).

## Edge Cases

- The running TASK-INT-023 GAP image predates account_type — verification MUST rebuild the GAP auth-service bootJar from current `main` (BE-329 + BE-330 merged).
- If the GAP token omitted account_type (pre-BE-329 image), `session.accountType` would be `null` and AC-1 would fail loudly — the spec is a true regression gate.
- An OPERATOR-typed credential would be anonymized by the `session` callback + bounced to `/login?error=account_type_mismatch` — covered by the existing `account-type-guard.spec.ts`.

## Failure Scenarios

- GAP image not rebuilt → `account_type` absent → `session.accountType` null → AC-1 fails (correct signal that the emission is missing).
- Seed missing the row → login fails (no credential) → AC-1 fails before the assertion.

## Dependency Markers

- **depends on**: TASK-BE-329 (`ebd3d908`, claim emission) + TASK-BE-330 (`18bf38d3`, provisioning explicit-set) on `origin/main`; TASK-INT-023 (the lean GAP e2e stack + `loginAsSeededConsumer`).
- **completes**: ADR-MONO-021 § 3.3 execution roadmap (steps 1–3).
