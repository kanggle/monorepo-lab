# Task ID

TASK-FAN-BE-007

# Title

Fan-platform spec drift bundle — v1-e2e-scenarios.md artist path off-by-one (F16) + OAuth internal-services client name disagreement (F21)

# Status

review

# Owner

backend

# Task Tags

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

Close two GENUINE fan-platform spec-vs-spec drift findings from the 2026-05-15
portfolio audit, verified against the current tree.

After this task: (WI-1) the v1 e2e scenarios doc uses artist API paths that
actually exist in the artist contract / gateway; (WI-2) the internal-services
OAuth client is named identically everywhere, matching the GAP V0011 seed.

Project-internal — `projects/fan-platform/specs/`.

---

# Scope

## In Scope

**WI-1 — F16 (e2e path off-by-one + URL drift, spec-only).**
`specs/integration/v1-e2e-scenarios.md:133` uses `POST /api/v1/artist/artists`
and `:138` `PATCH /api/v1/artist/artists/{id}/status` — an extra `/artist/`
path segment. The canonical external path is `/api/v1/artists/**`:
- `specs/contracts/http/artist-api.md:16-19` route table → external
  `/api/v1/artists/**` → internal `/api/artists/**` (no `/artist/`
  intermediate segment)
- `specs/services/gateway-service/architecture.md:147` →
  `/api/v1/artists/** → /api/artists/** → artist-service:8080`
Fix the e2e scenarios doc paths to the canonical `/api/v1/artists` /
`/api/v1/artists/{id}/status` (3 occurrences per the audit; grep for all
`/api/v1/artist/` to catch every instance, not only the two cited lines).
The artist-api contract + gateway architecture are the route authority — the
e2e doc is wrong and is the only edit target.

**WI-2 — F21 (OAuth client name disagreement, spec-only).**
`specs/integration/gap-integration.md:48` uses
`fan-platform-internal-services-client`; `specs/services/fan-platform-web/architecture.md:150`
uses `fan-platform-realm-internal-services-client` (extra `realm-` infix) for
the same v2-DEFERRED internal-services client. The GAP V0011 seed
(`projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0011__seed_fan_platform_oidc_clients.sql:84`)
uses **`fan-platform-internal-services-client`** — that is the canonical name.
Correct `fan-platform-web/architecture.md:150` (and any other occurrence of the
`realm-` infix variant — grep both strings repo-wide within fan-platform specs)
to the seed-canonical `fan-platform-internal-services-client`.

## Out of Scope

- Any `apps/` production code / test, and the GAP V0011 migration itself (it is
  the canonical authority — read-only reference; do NOT edit a shipped
  migration).
- Changing the actual v2-DEFERRED status of the internal-services client
  (only the *name string* is reconciled).
- Other fan-platform e2e scenarios beyond the artist path drift.

---

# Acceptance Criteria

- [ ] WI-1: `grep -rn "/api/v1/artist/" specs/` returns 0 (no extra-segment
      form remains); `v1-e2e-scenarios.md` artist paths match
      `artist-api.md` + `gateway-service/architecture.md` exactly.
- [ ] WI-2: `grep -rn "fan-platform-realm-internal-services-client" specs/`
      returns 0; every occurrence is `fan-platform-internal-services-client`,
      matching `V0011__seed_fan_platform_oidc_clients.sql:84`.
- [ ] No GAP file modified (V0011 read-only); no `apps/` diff.
- [ ] `validate-rules` clean.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> read `projects/fan-platform/PROJECT.md` and load `rules/common.md` + declared
> domain/trait files.

- `specs/integration/v1-e2e-scenarios.md` (L133/138 + any other `/api/v1/artist/`)
  — WI-1 edit target.
- `specs/contracts/http/artist-api.md` (L16-19) — WI-1 route authority.
- `specs/services/gateway-service/architecture.md` (L147) — WI-1 route
  authority.
- `specs/integration/gap-integration.md` (L48) — WI-2 canonical name reference.
- `specs/services/fan-platform-web/architecture.md` (L150) — WI-2 edit target.
- `projects/global-account-platform/apps/auth-service/.../V0011__seed_fan_platform_oidc_clients.sql`
  (L84) — WI-2 canonical name authority (READ-ONLY).

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary.
- `.claude/skills/validate-rules/SKILL.md` — post-check.

---

# Related Contracts

- `specs/contracts/http/artist-api.md` — WI-1 authority (no change).
- OAuth client registration is GAP-owned (V0011) — WI-2 references it, no
  change to the seed.

---

# Target Service

- `gateway-service` / `artist-service` (WI-1 e2e doc), `fan-platform-web`
  (WI-2)

---

# Architecture

No architecture change — pure spec/contract-reference reconciliation.

---

# Implementation Notes

1. Both WIs are mechanical string/path corrections toward an unambiguous
   authority (artist-api/gateway for WI-1; V0011 seed for WI-2). One spec PR.
2. Grep for the *patterns* (`/api/v1/artist/`, `realm-internal-services`) not
   just the two cited lines — the audit cited examples, not necessarily the
   full set.
3. Do not touch the GAP V0011 migration — it is the authority and is a shipped
   migration; only fan-platform specs change.
4. "(writing) → ready" stage — this spec PR adds the task to `ready/` + fan
   INDEX only.

---

# Edge Cases

- WI-1: an e2e step that intentionally tests a 404 on a deliberately-wrong path
  → do not "fix" a negative-test path; verify each occurrence is meant to be a
  valid call before rewriting.
- WI-2: if the `realm-` infix appears in a context describing a *Keycloak realm
  name* (not the client_id) it may be correct there — confirm each occurrence
  is the client_id before normalizing.

# Failure Scenarios

- WI-1 blindly replaces `/api/v1/artist/` everywhere including a legitimate
  different endpoint (e.g. a singular `/api/v1/artist/{id}` if one exists) →
  introduces a new wrong path; cross-check each against artist-api.md.
- WI-2 renames a Keycloak realm reference thinking it is the client_id →
  breaks a correct realm mention; disambiguate first.

---

# Test Requirements

- Spec-only. Verification: `grep` for `/api/v1/artist/` == 0 and
  `realm-internal-services-client` == 0; e2e paths match artist-api/gateway;
  client name matches V0011; `validate-rules` clean; no `apps/` / no GAP diff.

---

# Definition of Done

- [ ] WI-1 all artist e2e paths canonical (`/api/v1/artists…`), 0 extra-segment
- [ ] WI-2 single client name `fan-platform-internal-services-client` matching
      V0011 seed, 0 `realm-` variant
- [ ] No GAP/`apps/` diff; `validate-rules` clean
- [ ] Branch: `task/fan-be-007-e2e-path-oauth-name` (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + fan INDEX ready list only
- [ ] Ready for review
