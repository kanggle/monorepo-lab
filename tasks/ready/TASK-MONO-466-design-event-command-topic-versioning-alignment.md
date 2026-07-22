# TASK-MONO-466 — Align the `/design-event` command's topic-naming + breaking-change guidance with canonical platform docs

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (3 one-line doc corrections in a shared `.claude/commands/` file; no code)

> **Root-level task** — the destination is a shared path (`.claude/commands/design-event.md`), which forces root per `CLAUDE.md` § Task Rules. The command is guidance loaded into agent context; when it contradicts the canonical platform docs it actively instructs agents toward a convention the platform says is wrong.

---

## Goal

`.claude/commands/design-event.md` gave event-topic guidance that drifted from the canonical source of truth (`platform/versioning-policy.md` § Event Versioning + `platform/event-driven-policy.md`). Bring the command back into alignment — no new rule is introduced; the command is corrected to match the docs it is supposed to route agents to.

## Scope — 3 edits (all alignment, no new decision)

| # | Line | Before | After | Canonical source |
|---|---|---|---|---|
| 1 | Contract Format · Topic | `Topic: {service}.{entity}.{event}` | `Topic: {service}.{entity}.{event}.v{n}` (+ version-suffix pointer) | `versioning-policy.md` § Event Versioning: "publishes on a new topic version `<topic>.v{n}`" |
| 2 | Rules · Topic naming | `{service}.{entity}.{event}` (kebab-case) | `{service}.{entity}.{event}.v{n}` (dot-separated, lowercase; `.v{n}` mandatory) | same — the live convention across all 8 projects carries `.v1` (`wms.master.warehouse.v1`, `finance.account.opened.v1`, …) |
| 3 | Rules · Breaking changes | `create new version as {EventName}V{n}` | bump `eventVersion` envelope field **and** publish new topic `<topic>.v{n}` with a coexistence period — **both, never either**; do **not** use the `{EventName}V{n}` event-type suffix | `versioning-policy.md` L44/L46: "both, never either"; the `{EventName}V{n}` suffix "was removed as wrong … no live contract implements it (TASK-MONO-411)" |

The command's **Standard Envelope** block (10 fields: `eventId/eventType/eventVersion/occurredAt/source/aggregateType/aggregateId/traceId/actorId/payload`) already matches `event-driven-policy.md` § Event Envelope Format byte-for-byte — **left unchanged**.

## Acceptance Criteria

- [x] Edit #3 removes the `{EventName}V{n}` breaking-change form — the exact form `versioning-policy.md` records as removed-because-wrong (TASK-MONO-411). No agent following `/design-event` will be told to use it again.
- [x] Edits #1/#2 add the mandatory `.v{n}` topic version suffix, matching the live convention in every project's `specs/contracts/events/`.
- [x] No requirement/contract/schema change — envelope shape untouched; this corrects **guidance text only** to match existing canonical rules.
- [x] Verified against the canonical docs (not memory): `versioning-policy.md` § Event Versioning (L40-47) + `event-driven-policy.md` § Event Envelope Format / § Schema Versioning.

## Related Specs

- `platform/versioning-policy.md` § Event Versioning (canonical — the `<topic>.v{n}` + `eventVersion` "both" rule)
- `platform/event-driven-policy.md` § Event Envelope Format + § Contract Rule + § Schema Versioning
- `platform/naming-conventions.md` (topic case)

## Edge Cases

- The command keeps its generic `{service}.{entity}.{event}` segment structure (event-driven-policy does not mandate service-vs-domain in the topic); only the mandatory `.v{n}` suffix and the breaking-change mechanism are corrected — the minimal alignment, no broader restructuring of the segment scheme.

## Failure Scenarios

- Had the `{EventName}V{n}` guidance stayed, a new event designed via `/design-event` would introduce an event-type-suffix versioning form that no consumer/tooling in the repo supports — a contract that drifts from `versioning-policy.md` on day one.
