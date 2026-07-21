# TASK-FAN-BE-024 — Reconcile fan-platform outbox-relay class names in event contracts + service architectures

**Status:** ready

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (doc-only class-name corrections, no behavior change)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`). Audit numbers corrected on
> re-measurement: only **2 of 3** event contracts actually name a scheduler class (artist-events.md names none — REFUTED for it);
> and the audit's "Identity-events missing" claim is **REFUTED** (no fan-platform service consumes IAM Kafka events — only
> `notification-service` has a `@KafkaListener`, on fan-platform's own membership events). Scope is the class-name drift only.

---

## Goal

The outbox relay was migrated v1 (`*OutboxPollingScheduler extends OutboxPollingScheduler`) → v2
(`*OutboxPublisher extends AbstractOutboxPublisher`) per ADR-MONO-004 §5 / TASK-MONO-049, but five spec locations still name
the retired v1 class. Correct them to match the shipped classes.

## Scope

**In scope (all doc-only):**

1. **Event contracts (2):**
   - `specs/contracts/events/fan-membership-events.md:6` — `MembershipOutboxPollingScheduler` → `MembershipOutboxPublisher`
     (code: `apps/membership-service/.../infrastructure/outbox/MembershipOutboxPublisher.java:51`, `extends AbstractOutboxPublisher`).
   - `specs/contracts/events/community-events.md:4` — `CommunityOutboxPollingScheduler` → `CommunityOutboxPublisher`
     (code: `apps/community-service/.../infrastructure/outbox/CommunityOutboxPublisher.java:50`).
2. **Service architecture "Forbidden Dependencies" bullets (3)** — each file's Directory-Structure section already names the
   correct v2 class, but the Forbidden-Dependencies bullet a few lines below still mandates routing through the retired v1 class:
   - `specs/services/community-service/architecture.md:116` (`... → CommunityOutboxPollingScheduler`) → `CommunityOutboxPublisher`
     (cf. same file `:98` already correct).
   - `specs/services/membership-service/architecture.md:125` (`... → MembershipOutboxPollingScheduler`) → `MembershipOutboxPublisher`
     (cf. `:108`).
   - `specs/services/artist-service/architecture.md:122` (`... → ArtistOutboxPollingScheduler`) → `ArtistOutboxPublisher`
     (code: `apps/artist-service/.../adapter/out/messaging/ArtistOutboxPublisher.java:50`; cf. `:84,203-204` already correct).

**Out of scope:** `artist-events.md` (names no relay class — nothing to fix); any Identity-event consumption (none exists); the
per-service Redis allowed/forbidden differences (legitimate, not a contradiction); any code change.

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm each of the 5 locations still names a `*OutboxPollingScheduler` and each
  corresponding code class is still `*OutboxPublisher extends AbstractOutboxPublisher` at current `main` (line numbers may shift).
- **AC-1** — The 2 event contracts and 3 architecture Forbidden-Dependencies bullets name the v2 `*OutboxPublisher` classes,
  consistent with the Directory-Structure sections already in those same files.
- **AC-2** — No behavior change; only the named `.md` files touched. Grep `OutboxPollingScheduler` across `projects/fan-platform/`
  after editing — result must be empty (or every remaining hit explicitly justified as historical task-record text).

## Related Specs
- `projects/fan-platform/specs/contracts/events/{fan-membership-events,community-events}.md`
- `projects/fan-platform/specs/services/{community,membership,artist}-service/architecture.md`
- `docs/adr/ADR-MONO-004` §5 (outbox relay v2), TASK-MONO-049 (migration)

## Related Contracts
- None (event payload/topic contracts unchanged; only the relay implementation class name in prose).

## Edge Cases
- Each architecture file already documents the correct class in its Directory-Structure section — align the Forbidden-Deps
  bullet to that, do not "reconcile" downward to the stale name.
- artist-service's architecture Forbidden-Deps bullet IS stale even though `artist-events.md` is clean — all three service docs need the same one-line swap.

## Failure Scenarios
- **F1 — fixing the 2 event contracts and missing the 3 architecture bullets** (the audit's original claim only covered the
  contracts). Guarded by AC-2's repo-wide grep census.
