# Task ID

TASK-BE-561

# Title

Remove dead build dependencies and env vars left behind by the TMS side-channel retirement

# Status

done

# Owner

backend

# Task Tags

- code

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

Remove the four build dependencies and two container env vars that `TASK-BE-560` (ADR-MONO-053 §D8, impl PR #2958) orphaned when it retired the outbound-service TMS side-channel. `httpclient5`, `resilience4j-spring-boot3`, `resilience4j-micrometer`, and `wiremock-standalone` have zero references anywhere in `apps/outbound-service/src/` (main and test) as of that retirement; `TMS_BASE_URL` / `TMS_API_KEY` bind to a config block that no longer exists in the tracked `application.yml`. This is pure dead-code removal (`platform/refactoring-policy.md` category "Remove Dead Code") — no production behaviour changes, no contract changes. It closes the grep-consumers gap that `TASK-BE-560` left on its own decommission (`feedback_deletion_leaves_survivors_grep_the_consumers`).

---

# Scope

## In Scope

- `projects/wms-platform/apps/outbound-service/build.gradle` — delete the `// TASK-BE-049` comment + `httpclient5` + both `io.github.resilience4j:*` artifact declarations, and the `// TASK-BE-049` comment + `wiremock-standalone` test dependency.
- `projects/wms-platform/docker-compose.e2e.yml` — delete the `TMS_BASE_URL` / `TMS_API_KEY` env lines from the `outbound-service` service block.
- Re-running the grep-consumers check for each removed artifact/var and recording the result (artifact → consumers found → verdict) in the PR body per `platform/refactoring-policy.md § Rules #6`.

## Out of Scope

- Any file under `apps/outbound-service/src/` — no Java, `application*.yml`, or Flyway migration changes.
- `notification-service/build.gradle`'s resilience4j + wiremock declarations — those are live (Slack circuit breaker via `AlertRoutingService`); do not touch or "harmonise".
- `libs/java-common`'s resilience4j declaration (`implementation`-scoped, backs its own `ResilienceClientFactory`) — shared path, out of a wms-only task's boundary.
- Repo-root `infra/demo/demo.env` (`WMS_TMS_BASE_URL` / `WMS_TMS_API_KEY`) — repo-root path is monorepo-level; note it as a known remaining survivor in the PR body rather than editing it here.
- Historical Flyway files (`V4`, `V8`, `V11`, `V13`) and the historical narrative in `specs/services/outbound-service/database-design.md` — immutable applied-migration record; `V18` already drops the objects.
- Sibling-spec cross-references still describing the TMS push as live (tracked separately as `TASK-BE-562`).

---

# Acceptance Criteria

- [ ] `grep -nE "httpclient5|resilience4j|wiremock|BE-049" projects/wms-platform/apps/outbound-service/build.gradle` returns 0 lines.
- [ ] `grep -rn "TMS_BASE_URL\|TMS_API_KEY" projects/wms-platform/` returns 0 tracked matches (untracked `bin/`/`build/` artefacts excluded, verified via `git ls-files`).
- [ ] `./gradlew :projects:wms-platform:apps:outbound-service:build` succeeds with no new compiler warnings and no unresolved-import failures.
- [ ] `./gradlew :projects:wms-platform:apps:outbound-service:unitTest` and `:integrationTest` pass with zero test-source modifications (`git diff --stat -- apps/outbound-service/src/test` is empty).
- [ ] `./gradlew :projects:wms-platform:apps:outbound-service:bootJar` produces a runnable jar; the container starts and `/actuator/health` reports `UP` in the wms e2e compose stack (confirms removing `httpclient5` did not break JWKS/JWT decoding at boot).
- [ ] `git diff --stat` touches exactly 2 files, none under `apps/outbound-service/src/`.
- [ ] wms CI lanes GREEN: Build & Test, Integration (master + notification + outbound, Testcontainers), wms E2E, Package boot jars. CI Linux is the authority for Testcontainers (Windows local cannot run them) — read the JUnit XML, not the exit code.
- [ ] PR body contains the grep-consumers table, including the deliberately-retained root `infra/demo/demo.env` survivor.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` (§ Remove Dead Code, § Rules #6 — grep consumers before removing)
- `platform/testing-strategy.md` (CI-Linux-is-authority for Testcontainers lanes)
- `projects/wms-platform/specs/services/outbound-service/architecture.md` (lines documenting outbound-service "no longer holds any TMS dependency, config, or metrics" — this task makes the build file agree with that statement; no spec edit required)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` (§2 records the retirement)
- `projects/wms-platform/specs/services/outbound-service/database-design.md` (V18 record — read-only reference)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

None. `specs/contracts/http/outbound-service-api.md` and `specs/contracts/events/outbound-events.md` were already updated by `TASK-BE-560`; no API/event surface changes here.

---

# Target Service

- `outbound-service`

---

# Architecture

Follow:

- `projects/wms-platform/specs/services/outbound-service/architecture.md`

---

# Implementation Notes

- Verify with a grep on a **clean checkout** or excluding `apps/outbound-service/bin/` and `apps/outbound-service/build/` — those untracked build artefacts still contain the pre-`TASK-BE-560` `application.yml` with a `tms:` block and will produce a false "still referenced" result if included.
- `resilience4j-spring-boot3` transitively pulls `spring-boot-starter-aop`; so do `spring-boot-starter-data-jpa` and `spring-boot-starter-security` (both retained), so AOP support is not lost.
- `httpclient5` is BOM-managed by `spring-boot-dependencies` (not pinned by wiremock/resilience4j), so removing the explicit declaration removes it from outbound's runtime classpath entirely — confirm nothing else on the classpath re-adds it transitively before concluding the boot-health AC.

---

# Edge Cases

- **`httpclient5` removal silently swaps HTTP transports.** Spring Security's `NimbusJwtDecoder` builds its own `RestOperations` for the JWKS fetch; Spring Boot's auto-config prefers Apache HC5 when present on the classpath. No bean in outbound-service declares `RestClient`/`RestTemplate` directly, but assert the boot-and-health AC rather than reasoning about it.
- **Micrometer registry unaffected.** `resilience4j-micrometer` only registered TMS meters (deleted with `TmsMetrics`); confirm `/actuator/prometheus` still serves the saga counters (`outbound.saga.failed.count{reason=reserve_failed}`).
- **Gradle version-catalog pinning.** `resilience4j` and `wiremock` are declared with explicit versions (not BOM-managed) so their removal cannot shift any other module's resolved version.

---

# Failure Scenarios

- Service fails to start after `httpclient5` removal (JWKS/TLS transport regression) → STOP, do not patch around it. Restore only `httpclient5` (keep the resilience4j/wiremock removals) and document the live consumer in `specs/services/outbound-service/architecture.md` — this is the Rule-#6 outcome where a survivor turns out to be load-bearing.
- Any test requires modification to stay green → the dependency was not actually dead; abort that specific removal rather than editing the test (no production+test changes in the same refactoring change).
- Integration lane RED on the Testcontainers outbound suite → treat as a hypothesis, not a verdict; read the JUnit XML for the real failure before attributing it to infra flake.
- e2e compose fails to start after env-var removal → re-grep across `projects/wms-platform/*.yml` **and** repo root before concluding — another compose layer or override may still reference the vars.

---

# Test Requirements

- No new tests required (dead-code removal). Existing `outbound-service` unit + integration suites must pass unmodified with an identical test count to the pre-change baseline.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing unmodified (same count as baseline)
- [ ] Contracts unchanged (verified)
- [ ] Specs unchanged (statement already matched pre-change; verified, no edit needed)
- [ ] Ready for review
