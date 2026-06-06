# Task ID

TASK-MONO-184

# Title

Fix **14 broken relative links in README files** surfaced by a README-layer dead-reference audit — the README analog of TASK-MONO-181 (whose checker covered `platform/` + `projects/*/specs/` only, **explicitly excluding READMEs**). Link-only corrections (stale `tasks/ready/`→`done/` after lifecycle moves, missing `../../` monorepo-relative prefix, one wrong shared-policy path). No requirement/contract/decision change.

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level README dead-ref batch across hub + 4 projects (wms/iam/ecommerce/fan/platform-console). One atomic PR (CLAUDE.md § Cross-Project Changes; precedent TASK-MONO-181 / TASK-MONO-085 dead-ref batches).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행/맥락**: README-layer dead-ref audit (2026-06-07) — 40 tracked READMEs (`git ls-files '*README.md'`), 272 relative links, **14 broken**. Surfaced by MONO-183 (hub README L114 dead-link found + fixed there) which revealed the README layer is **not** covered by MONO-181's spec-only dead-ref checker. 13 of 14 are pre-existing backlog; 0 are rename-introduced (MONO-179/180 left README link URLs already `iam-*`).
- **no requirement/contract change** — pure markdown link-target corrections; README prose byte-unchanged except the link tokens (+ 1 display-text fix where the old text named a non-existent path).

# Goal

After this task, a relative-link existence sweep across all git-tracked README files returns **0 broken**. Every README cross-reference resolves **in the monorepo** (the active source-of-truth / primary viewing context). No README prose, requirement, contract, or decision changes.

# Scope

## In Scope (14 link fixes, 6 files)

**A. Stale `tasks/ready/` → `tasks/done/` (task moved after lifecycle close):**
1. `README.md` L22 (hub, fan row) — `[TASK-FAN-BE-001](projects/fan-platform/tasks/ready/)` → **plain text `TASK-FAN-BE-001`** (de-link). The bare-dir link to empty `ready/` is dead; the hub README convention links project **roots only** (`[fan-platform](projects/fan-platform/)` — every other row links the project root, never deep into `tasks/`), and the HARDSTOP-03 hook (`hardstop-detect.ps1`) correctly blocks writing a deep `projects/<name>/tasks/...` path-token into the root `README.md`. De-linking to plain text resolves the dead-ref **and** aligns with the hub convention (the project root is already linked on the same line).
2. `projects/fan-platform/README.md` L85 — `tasks/ready/` → `tasks/done/TASK-FAN-BE-001-gateway-service-bootstrap.md`.
3. `projects/platform-console/README.md` L59 — `../iam-platform/tasks/ready/TASK-BE-296-...` → `../iam-platform/tasks/done/TASK-BE-296-platform-console-oidc-client-and-product-registry.md`.
4. `projects/platform-console/README.md` L82 — `../../tasks/ready/TASK-MONO-108-...` → `../../tasks/done/TASK-MONO-108-adr-mono-013-accepted-platform-console-bootstrap.md`.

**B. Missing `../../` monorepo-relative prefix (`projects/wms-platform/README.md` uses standalone-relative `rules/...`/`docs/...` paths that resolve in the extracted standalone repo but break in the monorepo):**
5–6. L295 — `rules/traits/transactional.md`, `rules/traits/integration-heavy.md` → `../../rules/traits/...`.
7. L512 — `rules/domains/wms.md` → `../../rules/domains/wms.md`.
8. L563 — `docs/guides/development-process.md` → `../../docs/guides/development-process.md`.
9–12. L601–604 — `rules/common.md`, `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md` → `../../rules/...`.

**C. Missing `../../` prefix (single):**
13. `projects/iam-platform/README.md` L216 — `[GitHub Actions CI](.github/workflows/ci.yml)` → `(../../.github/workflows/ci.yml)` (workflows live at repo root).

**D. Wrong shared-policy path (file moved to repo-root `platform/`):**
14. `projects/ecommerce-microservices-platform/infra/minio/README.md` L4 — `[specs/platform/object-storage-policy.md](../../specs/platform/object-storage-policy.md)` → `[platform/object-storage-policy.md](../../../../platform/object-storage-policy.md)` (the policy is the shared repo-root `platform/object-storage-policy.md`; both link text and path corrected).

## Out of Scope

- **Semantically-stale-but-resolving links** — e.g. erp/finance README `[TASK-MONO-119](../../tasks/ready/)` point to the `tasks/ready/` **directory** (which exists with `.gitkeep`, so the link resolves) while the task is in `done/`. Not broken (resolves) → matches MONO-181's broken-only scope; not touched.
- **Standalone-vs-monorepo path duality** — B/C/D fixes make links resolve **in the monorepo**; the extracted standalone repos keep the pre-existing `../../`-to-vendored-shared-paths imperfection (accepted per portfolio submission strategy — the 4 newer project READMEs erp/scm/finance/iam already use this monorepo-relative form, so this aligns wms + ecommerce-minio + iam-ci to the majority convention). A `sync-portfolio.sh` rewrite that strips `../../` for shared paths during extraction would fix both contexts — noted as a possible future enhancement, NOT done here.
- Any code/spec/contract/ADR/migration; README prose (only link tokens + the 1 display-text fix in D).
- `tasks/INDEX.md` / spec dead-refs — already 0 (MONO-181).

# Acceptance Criteria

- AC-1: README relative-link existence sweep (all `git ls-files '*README.md'`, resolve each `](relative)` minus `#anchor`/`?query`, skip `http(s)`/`mailto`/anchor-only) returns **0 broken** (was 14).
- AC-2: `git diff` shows ONLY link-target token changes + the single D display-text change — no prose/requirement/contract/schema/decision change.
- AC-3: Each corrected link resolves to an existing file in the monorepo (verified by re-running the sweep).

# Related Specs

- None (READMEs are portfolio/onboarding surfaces, not specs).

# Related Contracts

- None.

# Edge Cases

- **wms README is the convention outlier** — it used root-relative `rules/common.md` (works in standalone, breaks in monorepo); the other 4 project READMEs use `../../rules/...` (works in monorepo). Aligning wms to `../../` makes 5/5 consistent and fixes the monorepo dead-refs.
- **D path depth**: `infra/minio/README.md` → repo root = 4× `../` (`minio`→`infra`→`ecommerce`→`projects`→root), then `platform/object-storage-policy.md`.
- **Bare-dir `tasks/ready/` links** (A1/A2) point to an empty `ready/` dir that doesn't exist on disk (git doesn't track empty dirs) — repointed to the specific `done/` file for precision.

# Failure Scenarios

- **Over-correcting resolving-but-stale dir pointers** → scope creep + churn. Only the 14 genuinely-broken (non-resolving) links are touched.
- **Introducing wrong `../` depth** → re-run the sweep (AC-1) to confirm 0.
- **Editing a link URL that was already correct** → diff confined to the 14 listed links.
