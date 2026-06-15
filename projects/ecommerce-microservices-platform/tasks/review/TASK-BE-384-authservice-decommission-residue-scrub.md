# TASK-BE-384 — Scrub decommissioned auth-service residue from ecommerce specs (post TASK-BE-132 / IAM delegation)

**Status:** review
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (net-zero doc alignment — scrub stale references to a decommissioned service)

---

## Goal

The ecommerce in-tree `auth-service` was **decommissioned 2026-05-04 (TASK-BE-132)** — its app code is excluded from the Gradle build and IAM (iam-platform) is now the identity authority via OIDC (`specs/integration/iam-integration.md`). The NextAuth cutover (TASK-FE-067) and IAM integration (TASK-MONO-027) are merged/done; `admin-dashboard` is RETIRED (ADR-MONO-031 Phase 6 / TASK-MONO-259). Multiple ecommerce specs still reference `auth-service` as a **live** system, carry **stale migration/checklist statuses**, or lack **deprecation banners** — this task scrubs that residue so no spec presents the decommissioned service (or completed migrations) as current. Net-zero: documentation only, no code/contract semantics change.

## Scope

**In scope** (spec edits):

1. `specs/services/auth-service/architecture.md` — add a top DEPRECATED banner (decommissioned TASK-BE-132, code excluded from build, replacement = IAM); fix the dead link `auth-service-api.md` → `auth-api.md`.
2. `specs/contracts/events/user-events.md` — remove `auth-service` from the live consumers of `UserWithdrawn` (annotate decommissioned).
3. `specs/services/batch-worker/architecture.md` — annotate "Expired session cleanup (auth-service…)" as REMOVED (TASK-BE-132), matching `overview.md`. *(Key Jobs prose region only — the Identity/Published-Interfaces region is owned by TASK-BE-385.)*
4. `specs/services/web-store/overview.md` — update the Auth-flows line from "IAM 전환 진행 중" to completed (IAM OIDC via NextAuth v5; TASK-FE-067 + TASK-BE-132 done).
5. `specs/integration/iam-integration.md` — Migration Path table rows 2/3/4 → `✅ done`; row 4 description note admin-dashboard RETIRED; ops-checklist items for TASK-FE-067 + TASK-BE-132 → `[x]`.
6. `specs/services/auth-service-deprecated/{dependencies,observability,redis-keys,overview}.md` — add per-file DEPRECATED banners (README/architecture already have them).
7. `specs/services/admin-dashboard/{overview,architecture}.md` — add a "body is historical reference only" note under the RETIRED banner; annotate the stale `account_type=OPERATOR` (architecture.md) as historical (superseded by roles, ADR-032/035); annotate the "planned in order-api.md" phrasing (overview.md) as platform-console-owned now.

**Out of scope (unchanged):**

- `specs/services/user-service/architecture.md` + `specs/services/notification-service/architecture.md` — their auth-service references are folded into sibling tasks (TASK-BE-385 owns user-service; TASK-BE-383 owns notification-service) to keep the per-cluster PRs file-disjoint.
- All code under `apps/**` (auth-service is already excluded from build; no code change here).
- `account_type → roles` live-spec framing — already completed (PR #1603 / #1606). Only the **retired admin-dashboard** body's historical account_type mention is annotated here.

## Acceptance Criteria

- **AC-1** — No ecommerce spec presents `auth-service` as a **live/current** system. Every surviving reference is either inside a retired/deprecated tombstone (with banner) or explicitly annotated "decommissioned TASK-BE-132".
- **AC-2** — `specs/services/auth-service/architecture.md` carries a top DEPRECATED banner and has no dead link (`auth-service-api.md` → `auth-api.md`).
- **AC-3** — `iam-integration.md` Migration Path + ops checklist reflect TASK-MONO-027 / TASK-BE-132 / TASK-FE-067 as done, and admin-dashboard as retired.
- **AC-4** — All four `auth-service-deprecated/*.md` files carry a per-file DEPRECATED banner.
- **AC-5 (net-zero)** — `git diff origin/main -- 'projects/ecommerce-microservices-platform/apps/**'` is empty. No contract semantics changed.

## Related Specs

- `specs/services/auth-service/architecture.md`, `specs/services/auth-service-deprecated/*`, `specs/services/admin-dashboard/*`
- `specs/integration/iam-integration.md`, `specs/services/web-store/overview.md`, `specs/services/batch-worker/architecture.md`
- `specs/contracts/events/user-events.md`

## Related Contracts

- `docs/adr/ADR-MONO-031-...` (admin-dashboard consolidation), `docs/adr/ADR-MONO-032/035` (account_type removal — context for the retired-body annotation).
- TASK-BE-132 (auth-service decommission), TASK-MONO-027 (IAM integration), TASK-FE-067 (NextAuth cutover), TASK-MONO-259 (admin-dashboard retirement).

## Edge Cases

- **Retired-body content** — admin-dashboard / auth-service-deprecated bodies are frozen historical records; this task adds banners/notes, it does NOT rewrite their substance.
- **File-disjointness with sibling tasks** — user-service/architecture.md and notification-service/architecture.md are deliberately NOT touched here (owned by TASK-BE-385 / TASK-BE-383) so the three sweep PRs never conflict.
- **batch-worker shared file** — only the Key-Jobs prose line is edited here; the Identity-table reconcile is TASK-BE-385's (non-overlapping region → 3-way mergeable).

## Failure Scenarios

- **F1 — stale reference misleads** — leaving `auth-service` as a live consumer/owner could cause a future change to re-wire a decommissioned service. Guarded by AC-1.
- **F2 — dead link** — the `auth-service-api.md` reference 404s for any reader. Guarded by AC-2.
- **F3 — over-edit changes retired-body meaning** — only banners/annotations added; substance preserved. Guarded by the Edge Cases.
