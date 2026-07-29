# Task ID

TASK-FAN-BE-035

# Title

notification-service: extract the shared Kafka envelope-parsing helper duplicated by the community-event parser

# Status

ready

# Owner

backend

# Task Tags

- code
- test

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

Collapse the verbatim-duplicated envelope-parsing code that `TASK-FAN-BE-026` (impl PR #3026) created when `CommunityEventParser` was cloned from `MembershipEventParser`, so the canonical fan-platform event envelope (`eventId` / `eventType` / `schemaVersion` / `payload` / `tenantId`) is validated in exactly one place inside `notification-service`, and the JSON field accessors (`requireText` / `requireInt` / `optionalText` / `optionalTextArray` / `requireInstant`) exist once instead of twice. A literal diff of the two parser files shows ~47 of ~125 lines byte-identical (envelope preamble, `requireText`, `optionalText`, `requireInstant`, the `SUPPORTED_SCHEMA_VERSION` field + constructor). After this task, a third event family added to notification-service inherits envelope validation and DLQ classification (`MalformedEventException` vs `UnsupportedSchemaVersionException`) for free instead of by copy-paste, and the two existing parsers contain only their event-specific `switch` plus record construction. No wire format, DLQ routing decision, exception type, or exception message changes.

---

# Scope

## In Scope

- New package-private helper(s) in the existing `com.example.fanplatform.notification.application.consumer` package (the package the architecture spec already documents as shared by both parsers), e.g. an `EventEnvelope` record (`eventId`, `eventType`, `payload` JsonNode) with a `static EventEnvelope parse(ObjectMapper, String raw, int supportedSchemaVersion)` factory, plus a `JsonFields` static-accessor helper.
- Rewriting `MembershipEventParser.parse` and `CommunityEventParser.parse` to delegate the preamble + field accessors to the helper, keeping each parser's own `switch` and record construction verbatim.
- Both parsers keep their public class name, public constructor signature `(ObjectMapper)`, and public `parse(String)` signature unchanged.
- Updating `specs/services/notification-service/architecture.md` § Package Layout to list the new helper file(s) — one line, spec-first.

## Out of Scope

- The duplicated private `handle(ConsumerRecord)` bodies in `CommunityEventConsumer` and `MembershipEventConsumer` — below the 3+ occurrence threshold, `@KafkaListener` methods must stay per-class regardless, and explicitness on the DLQ-critical rethrow path is worth more than the ~10 saved lines. Leave both untouched.
- `HandleCommunityEventUseCase` / `HandleMembershipEventUseCase` — their overlap is deliberate (documented in `HandleCommunityEventUseCase`'s own class javadoc: different recipient-resolution rules per event family).
- Any change to `libs/` — the helper stays inside `notification-service`; the envelope shape is fan-platform-specific, not a platform primitive (`platform/shared-library-policy.md`).
- Any change to test code, test assertions, event contracts, topics, consumer groups, DB schema, or the `TASK-FAN-BE-026` V3 migration.
- `membership-service` payment/DI code and the `SubscribeUseCase`/`RenewMembershipUseCase` idempotency-block overlap — a separate, already-identified and deliberately-deferred item (M5 from an earlier membership refactor sweep); not part of this task.
- The sibling `community-service` / `artist-service` event publishers.

---

# Acceptance Criteria

- [ ] `MembershipEventParser.java` and `CommunityEventParser.java` each contain zero private static `requireText` / `optionalText` / `requireInstant` definitions; those bodies exist once in the new helper.
- [ ] The envelope preamble (JSON parse → root-is-object → `eventId` → `eventType` → schemaVersion gate → payload-is-object) exists exactly once in `src/main/java` (`grep -c 'Envelope is not a JSON object'` returns `1`).
- [ ] Both parsers still expose `public XEventParser(ObjectMapper)` and `public XEvent parse(String)` — `MembershipEventParserTest` and `CommunityEventParserTest` compile and run without any edit.
- [ ] `MembershipEventParserTest` and `CommunityEventParserTest` pass unmodified (0 lines changed in `src/test`), including malformed-JSON, missing-required-field, unsupported-`schemaVersion`, absent-`mentionedAccountIds`, and absent-`postAuthorAccountId` cases.
- [ ] Exception types and message strings are byte-identical for every failure path (`"Unparseable envelope JSON: "`, `"Envelope is not a JSON object"`, `"Missing required field: "`, `"Missing or non-integer field: "`, `"Malformed timestamp in field "`, `"Missing payload for event "`, `"Unsupported eventType: "`), and `UnsupportedSchemaVersionException` still carries the same `(schemaVersion, eventType)` pair.
- [ ] `MembershipEventConsumeIntegrationTest`, `CommunityEventConsumeIntegrationTest`, `IdempotentConsumeIntegrationTest`, and `DlqRoutingIntegrationTest` pass unmodified on the CI Linux Integration lane (authoritative — local Windows Testcontainers is not).
- [ ] `./gradlew :projects:fan-platform:apps:notification-service:check` GREEN before the first edit (baseline test count recorded) and GREEN with the same test count after.
- [ ] No new compiler warnings; the new helper is package-private (not `public`) and is not registered as a Spring bean.
- [ ] `specs/services/notification-service/architecture.md` § Package Layout lists the new file(s); no other spec or contract file is modified.
- [ ] Single refactoring category only (Reduce Duplication) — no dead-code removal, no rename, no feature work in the same commit.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` (Reduce Duplication category; preconditions: green baseline, no behavior change, no test edits in the same change)
- `platform/service-types/event-consumer.md` (declared Service Type of notification-service)
- `platform/testing-strategy.md`
- `platform/dependency-rules.md`, `platform/naming-conventions.md`
- `projects/fan-platform/specs/services/notification-service/architecture.md` (§ Identity — Architecture Style = Layered; § Package Layout — "shared by BOTH parsers"; § Consume Semantics — `schemaVersion=1` → DLQ on unsupported; test-requirement section)
- `projects/fan-platform/PROJECT.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

Read-only inputs — must not change:

- `projects/fan-platform/specs/contracts/events/community-events.md` (§ recipient-routing fields — `postAuthorAccountId` optional, `mentionedAccountIds` optional-empty; skip-and-dedupe, not DLQ)
- `projects/fan-platform/specs/contracts/events/fan-membership-events.md` (activated / canceled / expired v1 envelopes)
- `projects/fan-platform/specs/contracts/events/README.md` (envelope census)

No contract edit is permitted by this task; if the refactor appears to require one, stop — that is proof it is not behavior-preserving.

---

# Target Service

- `notification-service`

---

# Architecture

Follow:

- `projects/fan-platform/specs/services/notification-service/architecture.md`

---

# Implementation Notes

- Both parsers declare `SUPPORTED_SCHEMA_VERSION = 1` — if the helper takes it as a parameter, each parser must keep its own constant so the two event families can diverge later; do not hard-code `1` inside the helper.
- Extract as a static helper taking arguments (not a Spring-managed collaborator) — the existing tests construct each parser directly (`new MembershipEventParser(new ObjectMapper())`), and injecting a bean would force test edits, which the refactoring policy prohibits in this change.

---

# Edge Cases

- `schemaVersion` absent entirely — current code is `root.path("schemaVersion").asInt(-1)`, so `-1 != 1` → `UnsupportedSchemaVersionException` (DLQ), not `MalformedEventException`. Preserve this exact classification, including passing `eventType` into the exception (read before the version gate).
- `schemaVersion` present but non-numeric (e.g. `"v1"`) — `asInt(-1)` coerces to `-1` → same `UnsupportedSchemaVersionException` path; do not "improve" this into a malformed-envelope error.
- Non-JSON / empty / `null` / JSON-array root — must still yield `MalformedEventException` with the same two distinct messages.
- Blank-string required field (`""` or whitespace-only) — `requireText` treats blank as missing; preserve.
- `optionalText` on a non-textual node (number/object) → `null`, not an exception; preserve.
- `optionalTextArray` on absent / `null` / non-array → empty list (rollout tolerance for pre-enrichment `community.comment.added.v1` events); blank entries inside the array are filtered out — preserve exactly, this is the difference between a skipped notification and a DLQ'd event.
- `requireInt` accepts only `isInt()` (a JSON `1.0` or `"1"` is rejected) — membership-only accessor; keep the strictness even though the shared helper now offers it to both parsers.
- Unknown `eventType` reaching a parser — still `MalformedEventException("Unsupported eventType: " + eventType)` from the parser's own `switch`, not from the helper.

---

# Failure Scenarios

- Envelope validation order changes — reading `payload` before the `schemaVersion` gate (or `tenantId` before `eventType`) silently reclassifies a message from `UnsupportedSchemaVersionException` to `MalformedEventException`. Both route to DLQ so no test would notice, but the DLQ record's diagnostic and the § Consume Semantics contract change. Keep statement order identical and assert message strings.
- Helper made a Spring `@Component` and injected — breaks the unit tests' direct `new XEventParser(new ObjectMapper())` construction, forcing test edits the policy prohibits. Keep it a static helper with arguments passed in.
- Helper "promoted" to `libs/` — Hard Stop per `platform/shared-library-policy.md` / CLAUDE.md HARDSTOP-03; the envelope shape and exception types are fan-platform-specific.
- Over-generalisation into a generic `<T>` parser base class — adds a type hierarchy for two implementations and drags the event-specific `switch` into the abstraction; extract collaborators, not an inheritance spine.
- Scope creep into the consumers' `handle()` — a distinct refactoring category/target touching the DLQ rethrow path; explicitly out of scope here.
- No green baseline — local Windows Testcontainers is flaky and not authoritative; reproduce any local RED on clean `origin/main` before attributing it to this change, and treat the CI Linux Integration lane as the verdict.

---

# Test Requirements

- No new tests required — behaviour-preserving extraction. `MembershipEventParserTest` and `CommunityEventParserTest` must pass unmodified. `MembershipEventConsumeIntegrationTest`, `CommunityEventConsumeIntegrationTest`, `IdempotentConsumeIntegrationTest`, `DlqRoutingIntegrationTest` must pass unmodified on CI.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing unmodified (same count as baseline)
- [ ] Contracts unchanged (verified)
- [ ] `specs/services/notification-service/architecture.md` § Package Layout updated
- [ ] Ready for review
