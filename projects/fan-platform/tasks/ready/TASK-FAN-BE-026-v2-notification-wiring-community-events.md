# TASK-FAN-BE-026 — v2 notification wiring: connect the produced-but-unconsumed community.* events (+ membership welcome/cancellation)

**Status:** ready

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (event-contract enrichment + new consumers + notification-type design — not a mechanical change; a wrong contract shape reintroduces sync coupling)

---

## Goal

Wire the **forward-declared v2 notification consumers** so that the community.* events that are currently **produced-but-unconsumed** actually drive in-app / push alerts. This is a **feature** (roadmap-declared v2 work), not a defect fix — the dangling events are intentional forward-compat per [`specs/contracts/events/README.md:39`](../../specs/contracts/events/README.md) ("Produced-but-unconsumed events are real, not a gap"). This task begins consuming them.

The interesting surface is the community feed alerts (reply, reaction) plus the two zero-contract-change membership notices. The **follower-fanout** alerts (broadcast a new post / newly-published artist to a fan's followers) are **explicitly out of scope** — they require a follow graph that does not exist yet (see § Out of scope).

## Design decision (must be honoured — the load-bearing constraint)

The community events do **not** currently carry enough to route a notification without calling back into community-service:

- [`community.comment.added.v1`](../../specs/contracts/events/community-events.md#L83-L97) payload = `{postId, tenantId, commentId, authorAccountId, occurredAt}` — **no post author, no parent-comment author, no mention targets** → notification cannot address a "reply/mention alert" from the event alone.
- [`community.reaction.added.v1`](../../specs/contracts/events/community-events.md#L99-L120) payload = `{postId, tenantId, reactorAccountId, reactionType, occurredAt}` — **no post author** → notification cannot address the "interaction badge" recipient.

Two ways to close this:

1. **Enrich the event contract** (RECOMMENDED) — add the recipient-routing fields to the community events at emit time (community-service already holds this data in the same transaction). notification then routes purely from the event. **Preserves fan-platform's no-sync-coupling-for-notification property.**
2. notification-service **synchronously queries community-service** for post author / mentions. **REJECTED** — this reintroduces exactly the runtime coupling the platform deliberately confines to the one authorization gate (`community → membership`, TASK-FAN-BE-010). notification-service is a terminal consumer with **no event-producer / no outbound sync** today; adding a sync dependency here is a regression.

→ **This task takes option 1.** The contract change lands first (contracts win — HARDSTOP-08 / CLAUDE.md § Contracts), then the consumers.

## Scope

### A. Membership welcome / cancellation notices (no contract change — the clean slice)
- `fan.membership.activated.v1` → **WELCOME** in-app notification to the subscribing member.
- `fan.membership.canceled.v1` → **CANCELLATION** notice to the member.
- Both topics are **already subscribed** by `MembershipEventConsumer` (currently for `EXPIRY_REMINDER` only — [`notification-service/architecture.md:188-190`](../../specs/services/notification-service/architecture.md)); the member (recipient) is already resolvable on this path. Add the two notification types + handlers; no event-contract change.

### B. Community reply + reaction alerts (contract enrichment first)
1. **Enrich contracts** ([`specs/contracts/events/community-events.md`](../../specs/contracts/events/community-events.md)):
   - `community.comment.added.v1`: add `postAuthorAccountId` (reply target) and `mentionedAccountIds: []` (mention targets) — routing fields, additive/backward-compatible.
   - `community.reaction.added.v1`: add `postAuthorAccountId` (badge recipient).
   - Update the producer adapters in community-service (`CommunityOutboxPublisher` / the emit sites in `AddCommentUseCase` / reaction use-case) to populate them within the existing business TX.
2. **Add notification consumer** in notification-service — a new `CommunityEventConsumer` (`@KafkaListener`, own consumer group `notification-service-community-events`, per the `<service>-<purpose>` convention) following the existing `MembershipEventConsumer` pattern:
   - `community.comment.added.v1` → **REPLY** alert to `postAuthorAccountId` (skip if commenter == post author) + **MENTION** alert to each `mentionedAccountIds` entry.
   - `community.reaction.added.v1` → **REACTION_BADGE** alert to `postAuthorAccountId` (skip if reactor == post author).
3. Add the new notification types + eventId dedupe (reuse the membership consumer's dedupe path).
4. Update [`specs/contracts/events/README.md`](../../specs/contracts/events/README.md) census (community.* → "consumed") and [`notification-service/architecture.md`](../../specs/services/notification-service/architecture.md) Subscribed-Topics table + v1→v2 scope note.

## Out of scope (deferred — separate prerequisite epics)

- **Follower-fanout alerts** — `community.post.published.v1` "push fanout to followers" ([community-events.md:62-63](../../specs/contracts/events/community-events.md)) and `artist.published.v1` "broadcast push to existing followers" ([artist-events.md:83-86](../../specs/contracts/events/artist-events.md)). **Blocked**: both need a follow graph + a `community.follow.added` event that **does not exist** (artist-events.md:86 says so explicitly). File a prerequisite task for the follow relationship first.
- All other `artist.*` planned consumers (search-service indexing, analytics) — different consumer, not notification.
- `community.post.status_changed.v1` → search-service (not notification).
- DLQ (`outbox_dead_letter`) — separately marked v2 in the event specs.

## Acceptance Criteria

- **AC-1 (no sync coupling introduced)** — notification-service acquires **zero** outbound synchronous calls to community-service. `grep` for RestClient/WebClient/Feign in notification-service main = still 0 after this task. Routing is purely from enriched event payloads.
- **AC-2 (contract-first)** — the community-event payload additions land in `specs/contracts/events/community-events.md` (and the producer adapters) **before/with** the consumer; additions are backward-compatible (new optional fields, same topic/version — v1 stays v1 per the events README's additive-compat note).
- **AC-3 (self-notify suppression)** — REPLY/REACTION_BADGE alerts are NOT sent when the actor == the post author (verified by unit test).
- **AC-4 (idempotent)** — the new consumer dedupes by `eventId` (reaction repeat-PUT is already a producer no-op per community-events.md:101-108; the consumer still guards).
- **AC-5 (census truth)** — `specs/contracts/events/README.md` no longer lists `community.*` as "No live consumers"; the notification-service Subscribed-Topics table lists the new topics with a real handler.
- **AC-6 (membership slice)** — WELCOME + CANCELLATION notices fire on the existing membership topics with no event-contract change.

## Related Specs

- `specs/services/notification-service/architecture.md` (v1 membership-only scope → extend to community; Subscribed-Topics table)
- `specs/services/community-service/architecture.md` (producer of the enriched events; event consumption stays "none")
- `specs/services/membership-service/architecture.md` (welcome/cancellation are downstream of its existing events — no change)

## Related Contracts

- `specs/contracts/events/community-events.md` — **enriched** (`community.comment.added.v1`, `community.reaction.added.v1` gain recipient-routing fields). Additive, backward-compatible.
- `specs/contracts/events/fan-membership-events.md` — welcome/cancellation consumers referenced ([:80](../../specs/contracts/events/fan-membership-events.md), [:100](../../specs/contracts/events/fan-membership-events.md)); no change.
- `specs/contracts/events/README.md` — census updated.

## Edge Cases

- **Missing recipient field on old in-flight events** — during rollout, community events emitted before the enrichment lack `postAuthorAccountId`. Consumer must treat missing routing fields as "no addressable recipient → skip, log, dedupe" (not crash / not DLT).
- **Mention list empty / self-mention** — empty `mentionedAccountIds` → no mention alert; a user mentioning themselves → suppressed (AC-3 spirit).
- **Reaction type-change** — `LIKE→LOVE` re-emits; consumer should not spam a fresh badge per type flip (dedupe window / collapse per (post, reactor); confirm desired product behavior in impl).
- **Deleted post / deleted account** — post author or recipient account no longer active → skip gracefully (notification-service already synthesises addresses without cross-service reads).

## Failure Scenarios

- **F1 — sync-coupling regression** — if the impl reaches for community-service synchronously instead of enriching the event, AC-1 fails. Guarded by AC-1 grep + the § Design decision.
- **F2 — contract break** — changing the event in a non-additive way (renaming/removing fields, bumping to `.v2`) would break the census's additive-compat promise. Guarded by AC-2 (additive only).
- **F3 — fanout scope creep** — attempting post.published / artist.published follower fanout without the follow graph → half-built dead code. Guarded by § Out of scope (blocked on `community.follow.added`).

## Provenance

Filed 2026-07-22 after a design-review verification of fan-platform MSA autonomy. The "produced-but-unconsumed events" were **verified as intentional v1 forward-compat** (events/README.md:39), so this is v2 feature work — NOT a defect fix. The sibling "community→membership sync REST" finding was verified as an intentional, documented v1 authorization-query design (membership/community architecture.md) and is deliberately **not** touched here.
