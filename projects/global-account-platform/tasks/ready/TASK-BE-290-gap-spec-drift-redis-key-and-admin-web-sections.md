# Task ID

TASK-BE-290

# Title

GAP spec drift bundle — auth-service login:fail Redis key shape conflict (G7) + admin-web architecture.md missing Change Rule / Integration Rules (G19)

# Status

ready

# Owner

backend

# Task Tags

- adr

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

Close two GENUINE GAP spec-vs-spec drift findings from the 2026-05-15
portfolio audit, reconciled against the current tree (stale GAP items G1 /
G5 were verified already-closed and are NOT in scope).

After this task: (WI-1) the `login:fail` Redis key shape is identical across
all GAP auth-service specs; (WI-2) `specs/services/admin-web/architecture.md`
has the standard `## Integration Rules` and `## Change Rule` sections that
every other GAP service `architecture.md` already carries (completing the
partial TASK-BE-275 application).

Project-internal — all paths under `projects/global-account-platform/specs/`
(CLAUDE.md → that project's `tasks/`).

---

# Scope

## In Scope

**WI-1 — G7 (Redis key shape conflict, spec-only).**
`specs/services/auth-service/architecture.md:207` defines the login-failure
counter key as `login:fail:{tenant_id}:{email}` (plaintext email,
tenant-prefixed). Four other auth-service specs define it as
`login:fail:{email_hash}`:
- `specs/services/auth-service/redis-keys.md:13` (`login:fail:{email_hash}`,
  example `login:fail:a1b2c3d4e5` = `SHA256(email)[:10]`)
- `specs/features/rate-limiting.md:37`
- `specs/services/auth-service/overview.md:33`
- `specs/services/auth-service/dependencies.md:29`

`architecture.md:207` is the single outlier and also leaks plaintext email
into a Redis key (regulated/pii-sensitive — `PROJECT.md` GDPR/PIPA). Resolve by
making `architecture.md:207` consistent with the `{email_hash}` canonical form
the other four specs agree on. **Confirm the canonical shape against
`redis-keys.md`** (the dedicated key-registry spec — highest specificity) before
editing; if tenant-scoping is genuinely required for multi-tenant isolation,
the correct fix may be `login:fail:{tenant_id}:{email_hash}` — decide against
`redis-keys.md` + `rules/traits/multi-tenant.md` and update `redis-keys.md`
first if the registry itself needs the tenant segment (specs win over the
single outlier; the *registry* is the SoT among the specs).

**WI-2 — G19 (admin-web missing standard sections, spec-only).**
`specs/services/admin-web/architecture.md` (203 lines, last section
`## References` L194, TASK-BE-275 noted L202) has **no** `## Integration Rules`
and **no** `## Change Rule` section. Every other GAP backend service
`architecture.md` has both (e.g. `admin-service/architecture.md:202`
`## Integration Rules`, `:225` `## Change Rule`; `auth-service/architecture.md:201`
/ `:221`). Author both sections for `admin-web` consistent with the sibling
form and `admin-web`'s actual role (frontend-app service type — Integration
Rules should describe its admin-api consumption + GAP OIDC dependency; Change
Rule its versioning/compat policy). This completes the partial TASK-BE-275.

## Out of Scope

- G1 (auth-service Service Type vs PROJECT.md) — reconciled STALE:
  `PROJECT.md:5` `service_types:` already includes `identity-platform`.
- G5 (GAP architecture.md 3-up relative links) — reconciled STALE: all 8
  GAP `architecture.md` already use the correct 5-up `../../../../../` form
  (ADR-MONO-012 D3 cycle). The original audit + an earlier grep were a
  substring false-positive.
- Any production code, migration, or test change (both WIs are spec-only —
  no `apps/` touch). The Redis key is not yet read/written by code in a way
  this task changes; WI-1 aligns the *spec* so the eventual implementer has a
  single source.
- Any HTTP/event contract envelope change.

---

# Acceptance Criteria

- [ ] WI-1: `grep -rn "login:fail" specs/` shows exactly one key shape across
      `architecture.md`, `redis-keys.md`, `rate-limiting.md`, `overview.md`,
      `dependencies.md` (no `{tenant_id}:{email}` plaintext-email outlier
      remains). If tenant-scoping is adopted, `redis-keys.md` is updated first
      and all five agree on the new shape.
- [ ] WI-1: no plaintext `{email}` appears in any `login:fail` Redis key in
      specs (regulated/pii — only `{email_hash}` or hashed form).
- [ ] WI-1 (code-state gate): verify whether `auth-service` code already
      builds the `login:fail` key in the plaintext `{tenant_id}:{email}` form.
      If it does, the spec-only alignment is **insufficient** — a code
      follow-up task to hash the key MUST be filed (a live plaintext-PII Redis
      key is a regulated/GDPR-PIPA exposure, not just a spec drift). Record the
      code-state finding in the impl PR; if the leak is live, link the
      follow-up. Spec-only closure is valid only if the code does not yet emit
      the plaintext form.
- [ ] WI-2: `specs/services/admin-web/architecture.md` contains
      `## Integration Rules` and `## Change Rule` sections, structurally
      consistent with `admin-service/architecture.md`'s equivalents and
      accurate for a `frontend-app` service.
- [ ] WI-2: the ADR-MONO-012 canonical `architecture.md` form
      (`### Service Type Composition` H3 present, Identity table) is preserved
      — the new sections are appended without disturbing canonical structure.
- [ ] No new broken links / orphans introduced (re-run `validate-rules`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy,
> integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`,
> the 5 trait files. `data_sensitivity: pii-sensitive` — WI-1 is a PII-in-key
> concern.

- `specs/services/auth-service/redis-keys.md` — **key registry, WI-1 SoT among
  specs** (L13 canonical `login:fail:{email_hash}`).
- `specs/services/auth-service/architecture.md` — WI-1 edit target (L207).
- `specs/features/rate-limiting.md` (L37), `auth-service/overview.md` (L33),
  `auth-service/dependencies.md` (L29) — WI-1 corroborating canonical shape.
- `specs/services/admin-web/architecture.md` — WI-2 edit target.
- `specs/services/admin-service/architecture.md` (L202/L225) — WI-2 sibling
  form reference.
- `rules/traits/multi-tenant.md` — WI-1 tenant-scoping decision input.
- `rules/traits/regulated.md` — WI-1 PII-in-key constraint.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary.
- `.claude/skills/validate-rules/SKILL.md` — post-check.

---

# Related Contracts

- `specs/contracts/http/admin-api.md` — referenced by WI-2 Integration Rules
  prose (admin-web consumes it); no envelope change.
- No event contract touched.

---

# Target Service

- `auth-service` (WI-1), `admin-web` (WI-2)

---

# Architecture

No architecture-style change. WI-2 *adds the missing standard sections* to an
existing `architecture.md` while preserving its ADR-MONO-012 canonical form.

---

# Implementation Notes

1. WI-1 and WI-2 are independent spec edits; may bundle in one spec PR
   (`feedback_pr_bundling`).
2. WI-1 decision gate: read `redis-keys.md` first. The registry is the
   authority among specs — align the outlier to it, OR (if multi-tenant
   isolation demands a tenant segment) amend the registry first then propagate.
   Never keep plaintext `{email}` (regulated).
3. WI-2: mirror `admin-service/architecture.md` section *structure*, but write
   content true to `admin-web` being a `frontend-app` (it consumes admin-api +
   GAP OIDC; it does not own a DB). Do not copy admin-service prose verbatim.
4. "(writing) → ready" stage: this spec PR only adds the task to `ready/` +
   INDEX. Implementation is a separate impl PR.
5. WI-1 is scoped spec-only, but the impl MUST first verify the
   `auth-service` code state for the `login:fail` key (see the AC code-state
   gate). The spec-only assumption ("not yet read/written by code in a way
   this task changes") is a hypothesis to confirm, not a given — if the
   plaintext `{tenant_id}:{email}` form is already emitted by code, the
   regulated/PII exposure is live and a code follow-up is mandatory; do not
   close WI-1 as spec-only in that case.

---

# Edge Cases

- WI-1: if `redis-keys.md` itself is internally inconsistent (key pattern vs
  example), fix the registry's internal consistency as part of WI-1 and treat
  the corrected registry as canonical.
- WI-1: SUPER_ADMIN / platform-sentinel tenant (`"*"`) — if tenant-scoping is
  adopted, define the key segment for the sentinel explicitly (consistent with
  ADR-002 / `admin-service` tenant rules).
- WI-2: admin-web has no outbox/DB — its Change Rule must not assert DB-level
  invariants; scope it to UI/contract-compat versioning.

# Failure Scenarios

- WI-1 aligns `architecture.md` to `{tenant_id}:{email}` instead of the
  4-spec `{email_hash}` consensus → propagates the PII leak and the minority
  shape; the direction must be toward the registry/hash form.
- WI-1 spec aligned to `{email_hash}` but `auth-service` code already emits the
  plaintext `{tenant_id}:{email}` key → spec/code now agree on paper while a
  live plaintext-PII Redis key persists in production; a spec-only close here
  silently leaves a regulated exposure open. The code-state gate (AC) must
  catch this and force a code follow-up.
- WI-2 sections authored by copying admin-service verbatim → asserts DB/outbox
  rules false for a frontend-app; reviewer rejects on accuracy.
- WI-2 appends sections in a way that breaks the ADR-MONO-012 canonical H3
  ordering → cross-project architecture.md form regression.

---

# Test Requirements

- Spec-only; no unit/integration test. Verification:
  - WI-1: `grep` single-shape across the 5 specs + no plaintext `{email}`.
  - WI-2: section presence + structural parity with `admin-service` + canonical
    form preserved.
  - `validate-rules` clean.

---

# Definition of Done

- [ ] WI-1: single `login:fail` key shape across all 5 specs, no plaintext email
- [ ] WI-2: `## Integration Rules` + `## Change Rule` added to
      `admin-web/architecture.md`, sibling-consistent, canonical form preserved
- [ ] `validate-rules` no new broken-link/orphan
- [ ] Branch: `task/be-290-gap-spec-drift` (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + GAP INDEX ready list only (no impl)
- [ ] Ready for review
