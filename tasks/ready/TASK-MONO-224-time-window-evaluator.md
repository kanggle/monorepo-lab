# Task ID

TASK-MONO-224

# Title

ADR-MONO-028 step 2 — `TimeWindowCondition` shared evaluator + contract flip. Add the `com.example.security.access.TimeWindowCondition` evaluator to `libs/java-security` (sibling to `SourceIpCondition`: framework-agnostic, fail-safe, net-zero; a same-day `[start,end)` window over an IANA zone + days-of-week; midnight-wrap deferred) with unit tests, and flip `platform/access-conditions.md` § 1 `TIME_WINDOW` reserved → implemented + add the AND-only multi-condition composition note (the iam pilot composes `SOURCE_IP` AND `TIME_WINDOW`). Producer untouched (D3-B). No enforcement yet — that is the follow-up iam BE task.

# Status

ready

# Owner

backend

# Task Tags

- access-conditions
- conditional-policy
- shared-library
- security
- abac

---

# Dependency Markers

- **executes**: ADR-MONO-028 § 3.3 step 2 (the `TimeWindowCondition` evaluator + contract flip) on the ACCEPTED base (TASK-MONO-223, #1311).
- **mirrors**: TASK-MONO-218 (the `SourceIpCondition` evaluator + contract for the `SOURCE_IP` type) — same blueprint, 2nd condition type.
- **blocks**: the iam pilot enforcement task (composes `SOURCE_IP` AND `TIME_WINDOW` in `RequiresPermissionAspect`) + the optional federation-e2e composition proof.

# Goal

Provide the canonical `TIME_WINDOW` evaluator every domain reuses (or replicates exactly), so the 2nd access-condition type exists as code with the same three invariants (restriction-only / fail-safe / net-zero), and record in the contract that the iam pilot is the first AND-only multi-condition composition — without touching the producer (D3-B domain guard-config).

# Scope

- NEW `libs/java-security/src/main/java/com/example/security/access/TimeWindowCondition.java` — `fromConfig(zone, days, start, end)` / `isConfigured()` / `isSatisfiedBy(Instant)`; fail-safe (bad zone/time/non-same-day/null time → deny), net-zero (undeclared → no gate), same-day `[start,end)` in the declared IANA zone (DST via `java.time`); day tokens accept full names + ≥3-letter abbreviations.
- NEW `libs/java-security/src/test/java/com/example/security/access/TimeWindowConditionTest.java` — net-zero / in-window / zone-offset / day-token parsing / partially-valid days / fail-safe (null time) / fail-closed (bad zone, bad time, non-same-day, no days).
- `platform/access-conditions.md` — § 1 `TIME_WINDOW` reserved → implemented (`TimeWindowCondition`); the **AND-only multi-condition composition** note (input-specific evaluators composed at the seam; any unsatisfied configured condition → 403); References add ADR-028 + `TimeWindowCondition`.
- This task file.

**Out of scope** (the follow-up iam BE task): the `RequiresPermissionAspect` multi-condition generalisation, the `TIME_WINDOW` domain config props, any enforcement, IT; (the optional fed-e2e task): the composition proof. No producer / token-customizer change (D3-B).

# Acceptance Criteria

- **AC-1** `TimeWindowCondition.fromConfig(...)` returns a net-zero condition (`isConfigured()` false, `isSatisfiedBy` true for any time) when no window is declared.
- **AC-2** A declared same-day window gates: `isSatisfiedBy(Instant)` is true iff the request's local date-time (in the declared IANA zone) is on an allowed day AND within `[start, end)` (start inclusive, end exclusive); zone offset + day-roll are respected.
- **AC-3** Fail-safe: a `null` request time, an unparseable zone/time, a non-same-day window (`start >= end`, midnight-wrap deferred), or no valid days ⟹ deny, with `isConfigured()` still true (fail-closed, not net-zero).
- **AC-4** Day tokens parse full names + ≥3-letter abbreviations case-insensitively; unparseable tokens are dropped (partially-valid days still gate).
- **AC-5** `platform/access-conditions.md` § 1 marks `TIME_WINDOW` implemented and documents the AND-only multi-condition composition (iam pilot = `SOURCE_IP` AND `TIME_WINDOW`); producer untouched.
- **AC-6** `:libs:java-security:test` GREEN (the unit tests above) + the monorepo Build & Test job GREEN.

# Related Specs

- `docs/adr/ADR-MONO-028-time-window-access-condition.md` § 3.3 step 2 + § D3 (the `TIME_WINDOW` semantics/schema) + § D2 (the iam compose-AND pilot)
- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the framework + the `SOURCE_IP` sibling)

# Related Contracts

- `platform/access-conditions.md` (the contract being updated — § 1 flip + composition note)

# Edge Cases

- **Net-zero** must hold: an undeclared window (`isConfigured()` false) returns `true` for every time — the gate never bites until configured (mirrors `SourceIpCondition`'s empty allowlist).
- **Fail-closed** vs net-zero: a declared-but-invalid window (bad zone / bad time / `start >= end` / no valid days) is `isConfigured()` true but matches nothing — a misconfiguration denies, it does not fall open.
- **Same-day only**: `start >= end` (cross-midnight) is treated as invalid (fail-closed) — midnight-wrap is the documented ADR-028 § D3 fast-follow, not silently allowed.
- **DST / zone**: evaluation uses `Instant.atZone(IANA)` so DST is handled by `java.time`; no manual offset math.
- Each evaluator stays **input-specific** (`SourceIpCondition`/String, `TimeWindowCondition`/Instant) — composition is done by the consuming domain at the seam, NOT by a unifying interface (keeps the libs evaluators clean; the AND is in the aspect).

# Failure Scenarios

- If an undeclared/empty window were treated as fail-closed (deny) instead of net-zero, every existing path would regress — AC-1 pins net-zero.
- If a declared-but-invalid window fell open (allow), the gate could leak — AC-3 pins fail-closed.
- If a cross-midnight window silently evaluated as two intervals, the pilot would exceed its scope (midnight-wrap deferred) — AC-3 pins `start >= end` as invalid.
- If the contract were not flipped, a future adopter would still read `TIME_WINDOW` as "reserved" and re-derive it — AC-5 flips § 1 and records the composition pattern.
