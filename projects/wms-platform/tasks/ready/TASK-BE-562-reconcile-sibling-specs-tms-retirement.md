# Task ID

TASK-BE-562

# Title

Reconcile sibling-service specs and project docs still describing the retired TMS push as live

# Status

ready

# Owner

backend

# Task Tags

- code

<!-- docs-only change; "code" tag kept per template's binary choice, no api/event/deploy/test/adr/onboarding surface touched -->

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Reconcile the 15 sibling-spec and project-doc references that still describe the outbound-service TMS push as a live integration, retired by `TASK-BE-560` (ADR-MONO-053 §D8, impl PR #2958). Each becomes either a past-tense retirement note or repoints the "marquee `integration-heavy`" designation to a surviving surface (ERP order webhook or notification-service Slack). `PROJECT.md`'s `integration-heavy` trait itself stays valid — only the TMS bullet inside it is stale.

---

# Scope

## In Scope

- `projects/wms-platform/specs/services/master-service/external-integrations.md:24,93`
- `projects/wms-platform/specs/services/admin-service/external-integrations.md:29,100`
- `projects/wms-platform/specs/services/gateway-service/external-integrations.md:31,45,97`
- `projects/wms-platform/specs/services/gateway-service/overview.md:73`
- `projects/wms-platform/specs/services/inventory-service/external-integrations.md:24,34,94`
- `projects/wms-platform/specs/services/notification-service/external-integrations.md:124,525` (rewrite the JDK-HttpClient-vs-`RestClient` rationale so it no longer cites a deleted sibling class)
- `projects/wms-platform/specs/services/master-service/idempotency.md:125` (correct the 255-char cap's rationale to the surviving saga-level layer — **the cap value itself must not change**, it is a live runtime constant)
- `projects/wms-platform/PROJECT.md:33` (correct the TMS clause inside the `integration-heavy` trait bullet; do not change the declared trait itself)
- `projects/wms-platform/README.md:293,482`
- Settle on one single "marquee `integration-heavy`" exemplar across all edited files before editing (grep for `marquee` repo-wide first) — do not let different files repoint to different replacements.

## Out of Scope

- `libs/`, `platform/`, `rules/`, `.claude/`, root `docs/` — shared paths, out of a wms-only task.
- `projects/wms-platform/specs/services/outbound-service/*` — already correct (updated by `TASK-BE-560`); do not re-edit.
- `PROJECT.md` frontmatter (`domain`/`traits` declarations) — trait stays `integration-heavy`; only the prose bullet under it changes.
- Any Java, YAML, or contract file — this is a docs-only task (see `TASK-BE-561` for the build/compose cleanup).
- Historical narrative in `database-design.md` and in `tasks/done/*` — records of what happened; must not be rewritten.
- `specs/contracts/webhooks/erp-order-webhook.md:427` ("handled via TMS only") — describes an ERP-side business fact about the counterpart system, not a wms adapter. Verify intent before touching; leave alone if it is about ERP's own transport, not wms's.

---

# Acceptance Criteria

- [ ] `grep -rniE "\btms\b" projects/wms-platform/specs projects/wms-platform/PROJECT.md projects/wms-platform/README.md` returns only (a) explicit past-tense retirement notes and (b) outbound-service's own already-correct sections — each hit enumerated in the PR body.
- [ ] No file gains or loses a Markdown heading; no cross-reference link target changes (dead-ref / anchor check GREEN).
- [ ] `PROJECT.md` frontmatter (`domain`, `traits`) bytes unchanged — `git diff` shows only the §33 prose bullet.
- [ ] `master-service/idempotency.md`'s outbound key-length cap stays exactly 255 chars; only the justifying prose changes.
- [ ] `admin-service` and `inventory-service` "marquee" references are fixed in **both** locations each (body + Cross-References footer) so the two halves of each file agree.
- [ ] All edited files repoint "marquee" to the **same** surviving exemplar (settled by the repo-wide `marquee` grep before editing).
- [ ] wms doc-lint / dead-ref / INDEX-queue-drift CI checks GREEN.
- [ ] Zero files under `apps/` in the diff.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` (§ Rules #6 — grep consumers of what a decommission leaves behind, extended here to documentation consumers)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` (§2, lines 12/24/136-148 — the authoritative post-retirement statement all siblings must agree with)
- The 8 sibling spec files listed under In Scope above

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

None. `specs/contracts/http/tms-shipment-api.md` was already deleted by `TASK-BE-560`; `outbound-service-api.md` already records the endpoint removal. No contract file is touched by this task.

---

# Target Service

- Cross-cutting docs: `admin-service`, `gateway-service`, `inventory-service`, `master-service`, `notification-service` specs + `PROJECT.md` + `README.md`. No application code.

---

# Architecture

Follow:

- `projects/wms-platform/specs/services/outbound-service/architecture.md` (the source-of-truth statement being reconciled against)

---

# Implementation Notes

Describe important implementation constraints only.

- `README.md` and `PROJECT.md` sections in scope are Korean-language — preserve the language when editing.
- Fix each duplicated "marquee" reference in full — several files carry it twice (body prose + a Cross-References footer summary); missing the second half re-introduces the same drift this task exists to close.

---

# Edge Cases

- `admin-service`/`inventory-service` "marquee" lines each appear twice (main body + Cross-References footer) — both must be fixed or the two halves disagree.
- `gateway-service/overview.md:73` phrasing implies outbound/inbound "own" TMS adapters — outbound owns none post-retirement; inbound-service was never in scope of `TASK-BE-560` and must be checked before assuming its half of that sentence is also stale.
- `notification-service/external-integrations.md`'s rationale for choosing JDK `HttpClient` over `RestClient` explicitly cites outbound-service's (now-deleted) `RestClient` usage as the comparison point — the rewritten rationale must still justify notification-service's own choice, not just delete the comparison.

---

# Failure Scenarios

- Repointing "marquee" to one exemplar in some files while another edited file still asserts a different exemplar creates a **new** contradiction — settle on one before editing, per the repo-wide `marquee` grep in Scope.
- If a sibling spec's document *structure* (not just prose) turns out to depend on the TMS section existing (e.g., a required subsection a downstream tool parses), that is a HARDSTOP-06 spec conflict — stop and report rather than inventing a replacement section.
- Editing `PROJECT.md` frontmatter instead of only the prose bullet would trigger HARDSTOP-02 (unparseable/altered classification) — restrict the diff to the prose line.

---

# Test Requirements

- No code tests apply (docs-only). Run the wms doc-lint / dead-ref / anchor-check tooling and the INDEX-queue-drift guard.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Doc-lint / dead-ref / anchor checks passing
- [ ] Contracts unchanged (verified)
- [ ] `PROJECT.md` frontmatter unchanged (verified)
- [ ] Ready for review
