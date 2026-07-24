# Task ID

TASK-FAN-INT-004

> **⛔ SUPERSEDED (2026-07-24) by [`TASK-FAN-BE-029`](../review/TASK-FAN-BE-029-membership-workload-identity-positive-discriminator.md).**
> This task's premise — a local docker-compose S2S credential wiring gap — was **falsified by live diagnosis**. The credentials ARE correct (`community-service-client`/`secret` matches IAM V0009) and IAM DOES mint a token. The real 403 cause is a **token-shape contract conflict**: the IAM `client_credentials` token is tenant-scoped and carries `tenant_id`, but membership-service's `WorkloadIdentityAuthoritiesConverter` rejected any token with `tenant_id` (an unsanctioned negative discriminator). That is a product defect in the receiver, fixed by BE-029 (positive `membership.read` scope discriminator). No local overlay change is needed.

# Title

Provision community→membership S2S workload identity in the iam.local local/demo bring-up (SUPERSEDED by BE-029)

# Status

done

# Owner

integration

# Task Tags

- deploy
- api

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

In the local full-stack demo bring-up (`docker-compose.iamlocal.yml` overlay against the `iam.local` minimal IAM stack), reading a `MEMBERS_ONLY` or `PREMIUM` community post returns **403 `MEMBERSHIP_REQUIRED` even for a user with an ACTIVE PREMIUM membership**.

This is **not a product-code defect** — the fail-closed behavior is the intended, hard-won design (`TASK-FAN-BE-010` AC-2: "false on any error → fail-closed"; `TASK-FAN-BE-019` for the feed path). The gap is that the **service-to-service (S2S) workload identity is not provisioned in the local bring-up**:

- community-service calls `GET http://membership-service:8080/internal/membership/access` with a `client_credentials` bearer from `IamClientCredentialsTokenProvider`, configured via `iam.internal-client.{token-uri,client-id,client-secret}` (`community-service/application.yml`).
- membership-service `/internal/**` (`@Order(1)` chain) grants `ROLE_INTERNAL` only to a token with **no `tenant_id`** claim plus a machine-client marker (`WorkloadIdentityAuthoritiesConverter`).
- Neither `docker-compose.yml` nor the `docker-compose.iamlocal.yml` overlay sets `COMMUNITY_SERVICE_CLIENT_ID` / `COMMUNITY_SERVICE_CLIENT_SECRET` / `IAM_TOKEN_URI`, so community-service falls back to literal defaults (`community-service-client` / `secret`), and the minimal `iam.local` instance is not guaranteed to have seeded the matching client (IAM migration `V0009__seed_community_membership_oauth_clients.sql`, scope `membership.read`) with a matching secret. The observed failure is a connect timeout on the first attempt and `403 "Workload identity required for /internal/**"` on the second (token minted but not accepted).

After this task: with the local demo stack up, a demo user holding an ACTIVE PREMIUM membership can read PREMIUM/MEMBERS_ONLY posts end-to-end (community → S2S token → membership access-check → allow), and the wiring is captured in a committed overlay (the overlay is currently untracked).

---

# Scope

## In Scope

- Wire the S2S credentials for community-service in the local demo overlay(s): set `COMMUNITY_SERVICE_CLIENT_ID`, `COMMUNITY_SERVICE_CLIENT_SECRET`, and `IAM_TOKEN_URI` (→ `http://iam.local/oauth2/token`) so the minted `client_credentials` token matches a client the `iam.local` instance actually serves.
- Ensure the `iam.local` minimal bring-up (`projects/iam-platform/docker-compose.iamlocal.yml` + base) applies/loads IAM migration `V0009` (or an equivalent local seed) for `community-service-client` with the expected secret and `membership.read` scope, so `WorkloadIdentityAuthoritiesConverter` grants `ROLE_INTERNAL`.
- Confirm membership-service is reachable within community-service's connect/read timeout window during the demo bring-up sequence (ordering / readiness), so the first-attempt timeout does not occur.
- Commit the `fan-platform` demo overlay wiring (`docker-compose.iamlocal.yml` is presently untracked) and document the local demo membership-gate path in the bring-up notes.

## Out of Scope

- Any change to the fail-closed access-check logic (`HttpMembershipChecker`), the `WorkloadIdentityAuthoritiesConverter`, or the `/internal/**` security chain — those are correct and stay untouched.
- Production / CI wiring — the real `fan-integration-tests` Testcontainers lane and production already provision this; this task is the **local iam.local demo** path only.
- Rotating or hardening the demo client secret beyond what the local demo needs.
- The artist-directory 500 bug (separate ticket `TASK-FAN-BE-028`).

---

# Acceptance Criteria

- [ ] With the local demo stack up (fan `docker-compose.iamlocal.yml` + iam.local minimal), a demo user with an ACTIVE PREMIUM membership gets **200** on `GET /api/v1/community/posts/{premiumPostId}` and `GET /api/v1/community/feed` includes the PREMIUM/MEMBERS_ONLY posts.
- [ ] A user with **no** active membership still gets **403 `MEMBERSHIP_REQUIRED`** on the same PREMIUM post (fail-closed behavior preserved — this must NOT become fail-open).
- [ ] community-service logs show the `/internal/membership/access` call returning 2xx (no `Request timed out`, no `403 Workload identity required`) for the member case.
- [ ] The overlay credential wiring is committed (no reliance on an untracked `docker-compose.iamlocal.yml`), and the bring-up docs/notes describe the S2S provisioning step.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/entrypoint.md`
- `specs/services/membership-service/architecture.md` (internal access-check + workload identity)
- `specs/services/community-service/architecture.md` (HttpMembershipChecker adapter)
- `specs/integration/iam-integration.md`

# Related Skills

- `.claude/skills/` (see `.claude/skills/INDEX.md`) — deploy/compose wiring

---

# Related Contracts

- `specs/contracts/http/membership-api.md` — internal `GET /internal/membership/access` (fail-closed, deny-as-200)

---

# Participating Components

- community-service (`HttpMembershipChecker`, `IamClientCredentialsTokenProvider`)
- membership-service (`/internal/membership/access`, `WorkloadIdentityAuthoritiesConverter`)
- iam.local IAM (auth/account) — `client_credentials` token endpoint + `community-service-client` seed (`V0009`)
- compose overlays: `projects/fan-platform/docker-compose.iamlocal.yml`, `projects/iam-platform/docker-compose.iamlocal.yml`

---

# Trigger

A demo user reads a `MEMBERS_ONLY`/`PREMIUM` community post through the gateway; community-service performs the S2S membership access-check.

---

# Expected Flow

1. Demo user (ACTIVE PREMIUM) requests a PREMIUM post via `/api/v1/community/posts/{id}`.
2. community-service mints a `client_credentials` token from `iam.local` (`IAM_TOKEN_URI`, `COMMUNITY_SERVICE_CLIENT_ID/SECRET`).
3. community-service calls `GET membership-service:8080/internal/membership/access?accountId&tier&tenantId` with that bearer.
4. membership-service accepts the token (no `tenant_id` + machine-client marker → `ROLE_INTERNAL`), evaluates the active PREMIUM membership → `{allowed:true}`.
5. community-service returns the post (200). A non-member yields `{allowed:false}` → 403 `MEMBERSHIP_REQUIRED` (fail-closed unchanged).

---

# Edge Cases

- No active membership → still 403 (fail-closed correctness must be re-verified, not just the happy path).
- Token endpoint reachable but client secret mismatch → still 403; the task must make the seeded secret and the overlay env agree.
- membership-service slow to start → first-attempt connect timeout; ensure ordering/readiness so the member path does not intermittently 403.
- Author/operator reads short-circuit before the HTTP call (no S2S needed) — must remain unaffected.

---

# Failure Scenarios

- Upstream (iam.local token endpoint) down → community-service cannot mint a token → fail-closed deny (by design; document, do not "fix" by fail-open).
- Downstream (membership-service) down/timeout → fail-closed deny (by design).
- Contract mismatch: minted token carries a `tenant_id` claim (user-token shape) → `ROLE_INTERNAL` denied → 403; the overlay must mint a genuine machine token.
- Regression risk: accidentally relaxing `/internal/**` to `permitAll` or making the checker fail-open to "fix" the demo — explicitly forbidden.

---

# Test Requirements

- Manual/e2e local verification with the demo stack: member → 200, non-member → 403 (both directions), community logs show 2xx internal call.
- No new automated CI test required (production/CI already covers the S2S path via `fan-integration-tests`); if a lightweight scripted check is added, it must assert both the allow and the fail-closed deny.

---

# Definition of Done

- [ ] Integration flow implemented (local demo S2S provisioning)
- [ ] Contracts updated first if needed (none expected)
- [ ] Failure handling covered (fail-closed preserved and re-verified)
- [ ] Overlay wiring committed + bring-up notes updated
- [ ] Local end-to-end verification passing (member 200 / non-member 403)
- [ ] Ready for review
