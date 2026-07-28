# TASK-MONO-487 — `/validate-rules` (2026-07-29) skill-tier Warning fixes

**Status:** review

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (skill content corrections, no design
decisions — each fix aligns a skill with an already-established contract/library API)

> Surfaced by the 2026-07-29 `/validate-rules` sweep, Warning tier. Sibling of `TASK-MONO-485`/`486`. Excludes
> `.claude/skills/backend/external-http-integration/SKILL.md`'s `catch (Exception e)` — the sweep flagged this,
> but `TASK-MONO-413` § AC-5 already reviewed this exact file and explicitly decided not to narrow it
> (legitimate best-effort outbound-delivery boundary, already commented as such).

---

## Goal

Five independent skill-content fixes plus one command fix, all confirmed live via direct grep/read before
filing (not taken on faith from the sweep report).

## Scope

### In Scope

1. **`.claude/skills/backend/standalone-profile/SKILL.md`** — disables Flyway (`flyway.enabled: false`,
   `ddl-auto: update`) for the `standalone` profile with no documented relationship to `platform/coding-rules.md`
   § Database ("Use Flyway for all schema migrations"). Add an explicit note: this is a deliberate, scoped
   exception for local-dev-only H2, does not apply to any other profile including tests.
2. **`.claude/skills/backend/testing-backend/SKILL.md`** — example class `RedisUserSessionRegistryUnitTest`
   doesn't match `platform/naming-conventions.md` § Test Files pattern. Rename to
   `RedisUserSessionRegistryTest`.
3. **`.claude/skills/messaging/idempotent-consumer/SKILL.md`** — the "Processed Event Table" pattern hand-rolls
   `processedEventRepository.existsById()`/`save()` instead of using the shared
   `com.example.messaging.dedupe.EventDedupePort` (`libs/java-messaging`) the skill's own prerequisite section
   names as the canonical dedupe contract. Rewrite the pattern to call `EventDedupePort.process(...)`.
4. **`.claude/skills/service-types/event-consumer-setup/SKILL.md`** — § Idempotency Wiring table references
   three anchors in `idempotent-consumer/SKILL.md` (`§natural`, `§idempotency-table`, `§version-check`) that
   don't exist as headings there. Point the first two at the real section names; the third
   ("optimistic concurrency on aggregate") has no matching pattern in that skill at all — say so honestly
   rather than inventing an anchor, with a one-line fallback (standard JPA `@Version`).
5. **`.claude/commands/write-tests.md`** — hardcodes `.claude/skills/backend/testing-backend/SKILL.md` and a
   backend-only Test Levels table, despite the command's generic `/write-tests <service>` usage covering
   `frontend-app` services too. Make the skill-read step and Test Levels table conditional on the target
   service's declared `Service Type`, adding the `frontend/testing-frontend/SKILL.md` path for `frontend-app`.

### Out of Scope / Blocked

- **`.claude/skills/frontend/auth-client/SKILL.md`'s CSRF gap** (the skill mandates HttpOnly cookies without
  ever addressing CSRF, despite `cross-cutting/security-hardening/SKILL.md` § CSRF requiring a token for
  cookie-based sessions). **Attempted and blocked**: every Edit/Write to this file — including one that only
  fixed the triggering line — is rejected by the `rule-consistency-check.ps1` hook's `RULE-CONSISTENCY-04`
  check, citing the file's pre-existing (unrelated to this fix) `specs/contracts/http/auth-api.md` reference
  as unresolved. The hook appears to validate against on-disk content rather than the proposed new content
  (same failure class `TASK-MONO-410`'s Implementation Notes documented for `RULE-CONSISTENCY-01`/CRLF). Per
  that task's precedent: do not shell-out to bypass a hook — log it and move on. **Follow-up candidate**: fix
  the hook, then land the CSRF section (drafted in this task's PR discussion for reuse).
- `.claude/skills/backend/external-http-integration/SKILL.md` — already reviewed and accepted by `TASK-MONO-413`.
- Critical-tier (`TASK-MONO-485`, merged) and platform-tier Warning findings (`TASK-MONO-486`, merged).
- Agent-tier Warning findings (`TASK-MONO-488`).

---

## Acceptance Criteria

- **AC-0 (gate)** — re-confirm each item live before editing (already done at filing time via grep/read).
- **AC-1** — `standalone-profile/SKILL.md` documents the Flyway exception with a citation to
  `coding-rules.md § Database` and an explicit "does not apply outside `standalone`" scope statement.
- **AC-2** — `testing-backend/SKILL.md`'s example class name matches `naming-conventions.md` § Test Files.
- **AC-3** — `idempotent-consumer/SKILL.md`'s Processed Event Table pattern calls `EventDedupePort.process(...)`
  and no longer teaches a hand-rolled repository check for this case.
- **AC-4** — `event-consumer-setup/SKILL.md`'s Idempotency Wiring table has zero dangling anchors.
- **AC-5** — `write-tests.md` reads the correct testing skill and Test Levels table based on the target
  service's declared Service Type, for both a backend and a `frontend-app` target.
- **AC-6** — the `auth-client/SKILL.md` CSRF item is explicitly logged as blocked (not silently dropped) with
  the hook diagnosis in the PR body, matching this file's Scope note.

## Related Specs

- `platform/coding-rules.md` § Database, `platform/naming-conventions.md` § Test Files,
  `platform/event-driven-policy.md` (ADR-MONO-004, `EventDedupePort`), `docs/adr/ADR-MONO-004-*`
- Prior art: `tasks/done/TASK-MONO-410-security-skills-teach-the-opposite-of-the-contract.md`
  (`rule-consistency-check.ps1` CRLF hook bug precedent), `tasks/done/TASK-MONO-413-skill-spec-drift-sweep.md`

## Related Contracts

- None.

## Edge Cases

- Item 3's rewritten example must match `EventDedupePort`'s actual method signature and `Outcome` enum
  (verified from `libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java` before
  writing the example — do not invent an API, per `TASK-MONO-410`'s own near-miss on this exact mistake).
- `.claude/skills/**` and `.claude/commands/**` are not classifier-blocked; the `auth-client` block is a
  content-validation hook issue, not a classifier block — do not conflate the two failure modes.

## Failure Scenarios

- **F1** — inventing a library API in the `EventDedupePort` example instead of reading the real interface.
  Guarded by the Edge Cases note.
- **F2** — silently dropping the blocked `auth-client` item without recording why. Guarded by AC-6.
