# Task ID

TASK-MONO-297

# Title

Testing-strategy H2 auxiliary-slice exception — reconcile the absolute "no H2" rule with the wms master-service `@DataJpaTest` ORM-path slice pattern

# Status

review

# Task Tags

- onboarding

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

Close a documented gap between `platform/testing-strategy.md` (which states
**absolutely** "Do not use H2 or in-memory substitutes") and an existing,
intentional in-tree pattern in `wms-platform` master-service: 6 paired
`*RepositoryImplH2Test.java` files that run the JPA/ORM path on an H2
`MODE=PostgreSQL` in-memory database **as a `@DataJpaTest` slice**, alongside a
Testcontainers `*RepositoryImplTest.java` that remains the authoritative
integration test (Flyway + real Postgres).

A 2026-06-18 portfolio-wide testing-strategy conformance check found the
codebase otherwise **fully compliant** (E2E smoke/full tagging 18/18 concrete
classes; Testcontainers-only at the IT layer across iam/erp/finance/scm — those
IT bases even carry explicit `H2 forbidden` / `NO H2` comments; no
`@BeforeAll`-container bootstrap-pitfall). The wms H2 slices are the **only**
divergence, and they are a *reasonable* response to the
`project_testcontainers_docker_desktop_blocker` reality (Testcontainers blocked
on the Windows/Rancher dev host) — they keep the ORM mapping exercised on every
Docker-less CI run. The strategy document just never sanctioned the pattern.

Resolve by **documenting a narrow exception** so the standard matches reality:
H2 is permitted for a supplementary `@DataJpaTest` ORM slice **iff** an
authoritative Testcontainers integration test for the same persistence adapter
exists and remains the source of truth. The integration-test layer itself stays
Testcontainers-only (no relaxation) — the exception covers only the
non-authoritative slice tier.

Monorepo-level because the only edit target is shared
`platform/testing-strategy.md` (Source-of-Truth layer 5). No `projects/<name>/`
code changes — the wms slices are already conformant under the new exception
and are NOT modified by this task.

---

# Scope

## In Scope

**WI-1 — testing-strategy.md exception clause.** In
`platform/testing-strategy.md`, add a narrowly-scoped exception that permits an
H2 `@DataJpaTest` ORM-path slice **only** when ALL of the following hold:

- the test is a **slice** (`@DataJpaTest`), **not** an `*IntegrationTest`
  (does not boot the full Spring context);
- an authoritative Testcontainers integration test covering the **same**
  persistence adapter exists and remains the source of truth for the
  Flyway-migrated real-Postgres behavior;
- the H2 test is named to make its non-authoritative status obvious
  (the in-tree convention is the `*H2Test` suffix);
- the H2 slice asserts only ORM/JPA-mapping behavior that is portable across the
  H2-`MODE=PostgreSQL` dialect and real Postgres (no Postgres-specific SQL,
  no Flyway-migration assertions — those belong to the Testcontainers test).

Place the canonical exception text once (under § Testcontainers Conventions),
and add a one-line pointer at the two absolute prohibitions it qualifies
(§ Integration Tests bullet and the § Testcontainers Conventions opening bullet)
so a reader at either prohibition is directed to the exception.

## Out of Scope

- Any change to the wms master-service H2 tests or their Testcontainers
  counterparts (they are already conformant under WI-1; this is a doc-only task).
- Relaxing the **integration-test layer** prohibition — `*IntegrationTest`
  remains Testcontainers-only with no H2 fallback (unchanged).
- Adding H2 slices to any other service (the exception *permits*, it does not
  mandate; on-demand — do not retrofit).
- Any `projects/<name>/` file.

---

# Acceptance Criteria

- [ ] WI-1: `platform/testing-strategy.md` contains a single canonical
      exception clause permitting an H2 `@DataJpaTest` slice under the 4 stated
      conditions, located under § Testcontainers Conventions.
- [ ] The § Integration Tests "Must not use H2…" line and the § Testcontainers
      Conventions "Do not use H2…" line each carry a pointer to the exception
      (a reader at either prohibition can find it).
- [ ] The exception explicitly states the **integration-test layer is not
      relaxed** (Testcontainers-only at the IT tier).
- [ ] The wms master-service `*H2Test.java` files are **unmodified** by this
      task (doc-only; verified by diff — only `platform/testing-strategy.md` +
      `tasks/` change).
- [ ] `./gradlew check` not required (docs-only). Verification = read-through +
      `git diff --stat` confined to `platform/testing-strategy.md` and `tasks/`.
- [ ] No HARDSTOP-03 regression — no project-specific names (service/app/entity)
      added to the shared `platform/testing-strategy.md`. The exception is
      written generically (the wms pattern is the *motivation*, named only in
      this task file, not in the platform doc).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> monorepo-level task touching shared `platform/`; read `rules/common.md` and
> `platform/shared-library-policy.md`. No `PROJECT.md` applies (repo-root scope).

- `platform/testing-strategy.md` — WI-1 edit target (§ Integration Tests,
  § Testcontainers Conventions).
- `platform/shared-library-policy.md` — HARDSTOP-03 guard (keep the doc
  project-agnostic).
- `.claude/skills/backend/testing-backend/SKILL.md` — referenced by
  testing-strategy as the implementation-detail home; confirm the exception does
  not contradict it.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — doc-consistency edit, no meaning
  change to the surviving rules.

---

# Related Contracts

- None. No HTTP/event contract under any `projects/<name>/specs/contracts/` is
  touched.

---

# Target Service

- N/A (shared `platform/` documentation — monorepo-level)

---

# Architecture

No service architecture change. The integration-test tier remains
Testcontainers-only; only the supplementary slice tier gains a sanctioned H2
option.

---

# Edge Cases

- A future service adds an H2 slice **without** an authoritative Testcontainers
  counterpart → fails condition 2 → still a violation (the exception requires
  the real-Postgres IT to exist).
- An H2 slice asserting Postgres-specific behavior (e.g. a native upsert,
  JSONB operator) → fails condition 4 → must move that assertion to the
  Testcontainers IT.
- Someone reads only the § Integration Tests prohibition and misses the
  exception → mitigated by the mandatory pointer at both prohibition sites.

# Failure Scenarios

- Exception written too broadly (e.g. permitting H2 at the IT layer) → silently
  legalizes in-memory persistence integration tests, defeating the strategy's
  core intent. The clause MUST scope to `@DataJpaTest` slices only.
- Wms service/entity names leaked into `platform/testing-strategy.md` →
  HARDSTOP-03 (project-specific content in a shared file). Keep the doc generic.

---

# Test Requirements

- Docs-only; no unit/integration test. Verification: read-through that the
  exception's 4 conditions are stated, the IT-layer non-relaxation is explicit,
  both pointers exist, and `git diff --stat` is confined to
  `platform/testing-strategy.md` + `tasks/`.

---

# Definition of Done

- [ ] WI-1 exception clause added under § Testcontainers Conventions with the
      4 conditions + explicit IT-layer non-relaxation
- [ ] Pointers added at both absolute-prohibition sites
- [ ] Diff confined to `platform/testing-strategy.md` + `tasks/` (wms tests
      untouched)
- [ ] No HARDSTOP-03 regression (no project-specific names in the shared doc)
- [ ] Branch: `task/mono-297-testing-strategy-h2-aux-slice-exception`
      (substring `master` 금지 — uses `ms-`-free noun phrasing)
- [ ] Ready for review
