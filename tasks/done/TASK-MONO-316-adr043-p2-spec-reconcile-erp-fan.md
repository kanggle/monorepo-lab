# Task ID

TASK-MONO-316

# Title

ADR-MONO-043 P2 spec reconcile — sync erp + fan notification specs to the shipped `sourceDomain`/`deepLink`/`unread` conformance (code-ahead drift)

# Status

done

# Owner

architect

# Task Tags

- docs
- contracts

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

ADR-MONO-043 **P2 shape conformance** (TASK-ERP-BE-027 #2009, TASK-FAN-BE-023 #2010) shipped the cross-domain notification-inbox-contract § 1 fields into the erp + fan notification-service response DTOs, but the **specs were not reconciled** — a code-ahead drift surfaced by the 2026-06-29 discovery sweep (each finding grep-verified against the merged DTO + controller):

- **erp** `specs/contracts/http/notification-api.md § Common shape` lists `{id, type, title, body, sourceType, sourceId, read, createdAt, readAt}` — missing the now-serialized **`sourceDomain`** (constant `"erp"`) and **`deepLink`** (nullable, NON_NULL-omitted) that `NotificationResponse.java` emits. The `POST …/read` 200 example likewise omits `sourceDomain`.
- **fan** `specs/services/notification-service/architecture.md` Inbox Read API shows `GET …?status={UNREAD|READ}`, but the shipped `NotificationInboxController` accepts **`unread`** as the normative cross-domain filter (contract § 2.1) with `status` retained only as a back-compat alias; the response shape now carries `sourceDomain="fan"` + nullable `deepLink`.

This task makes both specs additive-consistent with the merged code + the authoritative `platform/contracts/notification-inbox-contract.md`. **Doc-only, no code** — the implementation already merged; this closes the spec gap. RG-3 (platform-console `console-integration-contract.md` aggregator-binding rewrite) is deliberately **excluded** — it overlaps the active ADR-043 console workstream and is left to that owner.

**근거**: 2026-06-29 spec-vs-impl drift sweep (RG-1 erp, RG-2 fan), grep-verified.

---

# Scope

## In Scope

| reconcile | location | authoritative source |
|---|---|---|
| Add `sourceDomain` + `deepLink` to the `Notification` Common-shape JSON + bullets; add `sourceDomain` to the `POST …/read` 200 example | erp `specs/contracts/http/notification-api.md` | `NotificationResponse.java` + `notification-inbox-contract.md § 1` |
| Change `status=` → `unread=` as normative GET filter (retain `status` as documented back-compat alias); note `sourceDomain="fan"` + nullable `deepLink` § 1 conformance fields | fan `specs/services/notification-service/architecture.md` Inbox Read API | `NotificationInboxController.java` + `notification-inbox-contract.md § 1/§ 2.1` |

## Out of Scope

- **RG-3** platform-console `console-integration-contract.md` "Notification inbox binding" rewrite (still describes the retired erp-direct bell binding pre-P3b) — belongs to the active ADR-043 console workstream; not reconciled here to avoid collision.
- Any **code** change (DTOs/controllers already shipped P2; this is spec-catch-up only).
- ADR-043 P2 ecommerce shape conformance, wms inbox-vs-delivery decision, aggregator Phase-2 — separate user-gated tasks per ADR-043 D7.

---

# Acceptance Criteria

- [ ] **AC-1** — erp `notification-api.md § Common shape` JSON includes `sourceDomain` (after `id`) and `deepLink` (after `body`), each with an explanatory bullet referencing `notification-inbox-contract.md § 1`, matching `NotificationResponse.java` field order/semantics.
- [ ] **AC-2** — erp `POST …/read` 200 example body includes `"sourceDomain": "erp"` (deepLink stays absent — null, NON_NULL-omitted).
- [ ] **AC-3** — fan `architecture.md` GET row shows `unread` as the normative param; a bullet documents `status` as the retained back-compat alias (applied only when `unread` absent) + the `sourceDomain`/`deepLink` § 1 conformance fields.
- [ ] **AC-4** — no source/code file changed (doc-only `git diff` confined to the two spec `.md` files + this task + INDEX).
- [ ] **AC-5** — spec claims match merged code: `unread`/`status` both present in fan controller; `sourceDomain`/`deepLink` present in both DTOs (already grep-verified at authoring).

---

# Related Specs

> Doc-only spec reconcile across two projects (erp + fan) → single atomic PR per `CLAUDE.md § Cross-Project Changes`. No rule-layer load needed beyond the contract below.

- `platform/contracts/notification-inbox-contract.md` (§ 1 envelope, § 2.1 read-filter alias) — authoritative
- `projects/erp-platform/specs/contracts/http/notification-api.md`
- `projects/fan-platform/specs/services/notification-service/architecture.md`
- `docs/adr/ADR-MONO-043` (D2/D7 P2 conformance)

# Related Contracts

- `platform/contracts/notification-inbox-contract.md` (the shape both specs now conform to)

---

# Edge Cases

- erp `deepLink` is always `null` today → it is **ABSENT** in JSON (NON_NULL); the Common-shape doc must describe it as nullable/absent, and the 200 example must NOT show it (showing `deepLink: null` would contradict the NON_NULL convention).
- fan `status` alias must remain documented (removing it from the spec would imply a breaking change the code did not make).
- HARDSTOP-03: edits are in project specs only; `platform/contracts/` is referenced, not modified — no project-specific content added to shared regulation.

---

# Failure Scenarios

- Documenting `unread` while dropping `status` from the fan spec → falsely implies the back-compat alias was removed (it was not) → consumer confusion.
- Adding `deepLink` to the erp 200 example as `"deepLink": null` → contradicts the shipped NON_NULL absent-field behavior.

---

# Test Requirements

- Doc-only; no automated test. Verification = grep cross-check that spec fields/params match the merged `NotificationResponse.java` + `NotificationInboxController.java` (both projects), per AC-5.

---

# Definition of Done

- [ ] AC-1…AC-5 satisfied
- [ ] Doc-only diff (two spec files + task + INDEX), HARDSTOP-03 clean
- [ ] RG-3 left out of scope (noted)
- [ ] Ready for review
