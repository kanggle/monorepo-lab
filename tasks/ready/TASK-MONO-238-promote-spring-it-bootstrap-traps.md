# Task ID

TASK-MONO-238

# Title

Promote two repo-wide Spring integration-test bootstrap rules from project memory to `platform/testing-strategy.md` — (1) start Testcontainers containers in a `static {}` block (not `@BeforeAll`); (2) multi-constructor `@Component`/`@Service` requires `@Autowired` on the injection constructor. Both are full-context-only failures invisible to Docker-free `:check`.

# Status

ready

# Owner

backend

# Task Tags

- docs
- monorepo
- governance

---

# Dependency Markers

- **source**: `/audit-memory` run 2026-06-13 § 5 (promote candidates P1 + P2). User approved promotion of the two HIGH candidates. The detail companion is `feedback_spring_boot_diagnostic_patterns` (§19(a) container `static {}` start; §21 multi-constructor `@Autowired`) — the diagnostic catalog (§1~§21) stays in memory as the worked-example detail; this task lifts the two reusable rules into the canonical platform spec (catalog + detail split, same convention as the §10 → CLAUDE.md promotion).
- **why repo-wide**: both apply to every Spring Boot service across all 5 active projects; any new IT base or multi-dependency bean hits them. Currently only an agent that recalls the diagnostic memory would know — promotion makes them discoverable from the always-relevant `platform/testing-strategy.md`.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (docs promotion, single shared spec).

---

# Goal

Make the two highest-value Spring IT bootstrap traps part of the canonical testing spec so any developer/AI session bootstrapping a service IT inherits them, keeping `feedback_spring_boot_diagnostic_patterns` as the detailed worked-example companion (not deleted).

# Scope

## In scope (`platform/testing-strategy.md` only — repo-root shared spec)

Add a subsection **"Integration-test bootstrap pitfalls (full-context-only failures)"** under `# Testcontainers Conventions`, with the two rules:

1. **`static {}` container start** — `@DynamicPropertySource` suppliers evaluate at context-refresh, before `@BeforeAll`; a container started in `@BeforeAll` is not yet running when its mapped port is read (`IllegalStateException: Mapped port ...` → `DataSourceAutoConfiguration` fail → all IT `initializationError`). Start containers in a `static {}` block. Plus the caveat that a `disabledWithoutDocker` IT base with no CI job has likely never actually run.
2. **Multi-constructor `@Autowired`** — a `@Component`/`@Service` with 2+ constructors must mark the injection one `@Autowired` (or keep one constructor + move the test-only one out); else `UnsatisfiedDependencyException`/`NoSuchMethodException: <init>()` at context load. Common trigger: a secondary deterministic-test constructor.
3. Both framed as "invisible to Docker-free `:check`; only Testcontainers `@SpringBootTest` IT catches them — do not conclude wiring safe from `:check` alone."

## Out of scope
- Deleting or trimming `feedback_spring_boot_diagnostic_patterns` (kept as detail companion; only annotated as promoted).
- The other audit promote candidates (P3 robocopy worktree cleanup → user CLAUDE.md; P4 `git branch -f main` recovery → CLAUDE.md; P5 `saveAndFlush`) — deferred, not in this task.

# Acceptance Criteria

- **AC-1**: `platform/testing-strategy.md` § Testcontainers Conventions contains the two rules, each stating the symptom, root cause (context-refresh timing / constructor selection), the fix, and that `:check` cannot catch them.
- **AC-2**: Edits are additive — no existing testing rule removed or weakened; markdown well-formed.
- **AC-3**: `feedback_spring_boot_diagnostic_patterns` §19(a)/§21 annotated as promoted (detail retained).
- **AC-4**: HARDSTOP-03 N/A — `platform/testing-strategy.md` is shared/project-agnostic; the rules are generic Spring/Testcontainers behavior (no project-specific content introduced).

# Related Specs / Code

- `platform/testing-strategy.md` § Testcontainers Conventions (the new subsection).
- Memory: `feedback_spring_boot_diagnostic_patterns` (§19(a), §21 — detail companions).

# Related Contracts

- None (governance/docs — no API or event contract touched).

# Edge Cases / Failure Scenarios

- **Catalog/detail split preserved** — the diagnostic memory keeps the full §1~§21 catalog; this promotion lifts only the two reusable rules (no memory deletion).
- **Generic only** — the platform spec states the generic rule; project-specific incident IDs (TASK-FAN-BE-009/TASK-ERP-BE-020 etc.) stay in the memory detail.
- **Self-dogfooding** — authored in a dedicated `mlab-mono238` worktree off `origin/main`.

# Notes

- audit-memory 2026-06-13 § 5 HIGH promotions (P1 + P2). P3/P4/P5 deferred. Docs-only; merge via PR per shared-file lifecycle.
