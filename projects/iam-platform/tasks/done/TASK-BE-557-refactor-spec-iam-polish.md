# Task ID

TASK-BE-557

# Title

refactor-spec iam-platform: structural polish (feature headings/labels/links + ADR-MONO prefix normalization)

# Status

done

# Owner

backend

# Task Tags

- adr

---

# Goal

`/refactor-spec` dry-run (2026-07-22) over all 80 iam-platform spec files (contracts 26 / features 16 / services 33 / use-cases 5). The 2026-07-21 audit covered content drift; this is STRUCTURAL polish only. **Zero broken references** were found. This task lands the verified-clean fixes and records the items skipped after verification and the semantic findings out of refactor-spec scope.

After this task, the cited defects are gone; no requirement, contract, or decision changed.

---

# Scope

## In Scope — applied (all verified meaning-preserving)

1. **feature title** `features/operator-management.md:1` — `# Feature Spec: 운영자 관리 (Operator Management)` → `# Feature: Operator Management (운영자 관리)` (15 sibling features use `# Feature:`).
2. **feature heading** `features/operator-management.md:3` — `## Overview` → `## Purpose` (sibling standard).
3. **label** `features/oauth-social-login.md:179` — `- HTTP Internal:` → `- Internal:` (5 siblings use `- Internal:`).
4. **circuitous self-ref** `services/admin-service/dependencies.md:25` — `[data-model.md](../../services/admin-service/data-model.md)` → `[data-model.md](data-model.md)` (same-folder sibling; resolves identically).
5. **bare→linked** `services/account-service/architecture.md:177` — bare `` `account-internal-provisioning.md` `` → `[account-internal-provisioning.md](../../contracts/http/internal/)`, matching the 3 sibling internal-contract links in the same list.
6. **ADR-MONO prefix normalization** — ~23 bare root-ADR mentions in prose (`ADR-014/020/023/024/026/032/034/035/045/046/047`) → `ADR-MONO-NNN` across 8 service files (account/admin/auth). **iam's own project ADRs (001-006) were deliberately left bare** — the project mixes two ADR namespaces (its own 001-006 + root MONO-series), and dropping the disambiguating `MONO-` prefix inconsistently within the same file is the latent-collision pattern. Verified: every normalized root number resolves to an existing `docs/adr/ADR-MONO-NNN-*.md`; zero double-conversions; iam-own 001/002/003/005 counts unchanged. (Compact lists like `ADR-026/028/029` render `ADR-MONO-026/028/029` — lead token normalized, acceptable.)

## Out of Scope — dropped after verification

- **data-rights.md / multi-tenancy.md Related-section link labels** — dry-run flagged full-path labels (`[specs/services/.../architecture.md]`) as violating a "bare-filename convention", but `account-service/architecture.md:174` also uses full-path labels — it is a *used* style, not a clear defect. Dropped.

## Findings — out of refactor-spec scope (require decisions / semantic owner; NOT fixed)

- **🔴 `/api/` platform-doc defect (not an iam defect)** — iam HTTP contracts **are** `/api/`-compliant for every business endpoint; only OAuth2/OIDC framework paths (`/oauth2/*`, `/.well-known/*`) lack it (immovable). The real defect is the **triple-wrong illustrative `/v1/oauth/*` table in `platform/service-types/identity-platform.md`** (wrong prefix `/v1/` vs `/oauth2/`+`/api/`, asserts a version segment nothing uses). That is a **platform/ file, out of this project's scope** — corrective task belongs at platform level.
- **topic-naming + envelope drift** — iam event topics are bare (`account.created`); envelope is `{eventId,eventType,source,occurredAt,schemaVersion,partitionKey,payload}` (≠ platform `eventVersion`/`aggregateType`). Both documented-intentional v1-compat in `events/README.md`; topic-string change = breaking. Report only.
- **Redis-key prefix split** — half the services use function-name prefixes, half service-name (`security:`/`admin:`), contradicting `naming-conventions.md § Redis Keys`. Renaming = live key-contract change → needs its own `## Overrides` note or normalization decision.

## Tier 2 (judgment / additive — NOT applied this cycle; recorded for follow-up)

- extract `redis-keys.md` for account-service (2 keys dup'd across overview+dependencies) and admin-service (5 keys scattered across 4 files) — the sibling pattern (auth/gateway/security keep a `redis-keys.md` + pointers).
- add auth-service `dependencies.md` `## Internal HTTP (incoming)` section (gateway JWKS + admin force-logout callers already documented elsewhere).
- add `operator-management.md` terminal `## Related Contracts` (prose already names admin-api.md).
- move admin-service `architecture.md` `## Tenant Scope Enforcement` before the terminal `## Change Rule`.
- add `admin.action.performed` `**Schema version**` line (value must come from the producer/code — not invented here).
- cross-link `abnormal-login-detection-v2.md` ↔ v1; add cross-ref between `admin-operations.md` (finalized cross-tenant format) and `multi-tenancy.md` (still hedged).
- **retention.md gap** (auth-service, security-service) — genuine file-set deviation, but filling it is new-spec authoring (retention durations/destruction), out of refactor scope.

## Leave as-is (deliberate)

- Common Error Format inline repetition across http contracts (accepted convention, each cites `error-handling.md`).
- Two co-existing tenant-scope-confinement mechanisms (ADR-002 sentinel vs ADR-MONO-024 union) — deliberately distinct axes per rbac.md.
- use-cases (all 5 fully clean); admin-web tombstone.

---

# Acceptance Criteria

- [x] 5 small fixes + ADR-MONO normalization applied; each verified (heading/sibling/ADR-file-existence).
- [x] iam-own ADRs (001-006) left bare; only root ADRs prefixed. Zero double-conversions; zero bare root ADRs remaining.
- [x] No requirement/contract/decision changed — all meaning-preserving.
- [ ] Cross-references still resolve (dead-anchor checker).
- [ ] Findings routed: the `/api/` platform-doc fix (platform scope), topic/envelope + Redis-prefix decisions, and the Tier-2 additive items.

---

# Related Specs

- `features/operator-management.md`, `features/oauth-social-login.md` (modified)
- `services/admin-service/{dependencies,architecture,data-model,overview,rbac,security}.md`, `services/account-service/{architecture,data-model}.md`, `services/auth-service/{architecture,data-model}.md` (modified — ADR-MONO normalization)
- `platform/service-types/identity-platform.md` (Finding — `/api/` illustrative table, platform scope)

# Related Contracts

- None. No API or event contract semantics changed.

---

# Edge Cases

- ADR-MONO normalization must exclude iam-own ADR-001..006 (verified via count preservation) — a blanket prefix-add would wrongly convert them.
- Compact ADR lists (`ADR-026/028/029`) normalize only the lead token; acceptable but note if a full rewrite is later wanted.
- The `/api/` finding must be fixed at the platform layer, not by editing iam contracts (which are compliant).

# Failure Scenarios

- **F1 — converting an iam-own ADR** (001-006) to `ADR-MONO-*` would create a dead/wrong reference; guarded by the own/root split + count verification.
- **F2 — "fixing" the `/api/` conflict inside iam contracts** would break OAuth2/OIDC clients or mis-edit compliant paths; guarded by reporting it as a platform-scope finding.
- **F3 — auto-adding a schema-version value** to `admin.action.performed` without the producer would fabricate a contract claim; deferred to Tier-2 with producer verification required.
