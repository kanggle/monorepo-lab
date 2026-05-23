# Task ID

TASK-MONO-132

# Title

platform-console e2e — split `seed.sql` section 6 (`finance_db.accounts` + `balances` INSERT) into a new `seed-finance.sql` and wire a *phase 2.5* workflow step that applies it AFTER `finance-account-service` Flyway completes. The current monolithic `seed.sql` runs at *phase 1.5* (after GAP Flyway, before `finance-account-service` is even started) and section 6's INSERT into `finance_db.accounts` fails with `ERROR 1146 (42S02) at line 209: Table 'finance_db.accounts' doesn't exist` — surfaced by the first post-PC-FE-024 nightly job (`26319887335` job `77486781283`, step `Apply seed.sql (operators, finance accounts, OIDC client tweaks)`) which advanced past the kafka-DNS gap (PC-FE-023+PC-FE-024 fix held green: step 9 `Start docker compose stack` + step 10 `Wait for admin-service health` both SUCCESS) and then revealed this schema-readiness phase-ordering gap on the next layer down.

# Status

ready

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- fix
- test

---

# Dependency Markers

- **depends on**: TASK-PC-FE-024 (workflow `up <names>` selective seam — kafka placeholder `depends_on` wiring, **DONE** 2026-05-22, PR #762 spec / #763 impl `b2e27d00` / #764 close `a22725bc`). PC-FE-024 fixed the kafka-DNS-loop integration gap; this task closes the *next layer* gap PC-FE-024's § Goal explicitly predicted ("After this fix lands, the next gap surfaced by the nightly will be the *next* legitimate step in the boot sequence — likely either `auth_db.credentials` schema readiness, **`console-bff` health, or the first Playwright assertion**"). The actual surface — `finance_db.accounts` schema-readiness — is a *variant of the schema-readiness prediction*: not `auth_db.credentials` (PC-FE-024 fix unblocked auth-service Flyway) but the *finance* schema, because `seed.sql` was structured assuming a phase ordering that the workflow never actually realizes.
- **depends on (indirect)**: TASK-PC-FE-023 (kafka DNS placeholder, DONE 2026-05-22, PR #758/#760/#761) — grandparent in the TASK-MONO-014 cycle pattern chain.
- **prerequisite of**: any future `PC-FE` task that depends on nightly full-stack GREEN signal (same dependency profile as PC-FE-023+PC-FE-024; Operator Overview finance card production-parity nightly regression guard).

---

# Goal

Make the `Platform Console E2E full-stack` nightly job advance past the seed-finance-INSERT failure. After this fix lands, the next gap surfaced by the nightly will be the *next* legitimate step in the boot sequence — `console-bff` health (step 12 `Start remaining containers` and step 13 `Wait for console-web health`) or the first Playwright assertion (step 15 `Run Playwright e2e (2 specs)`). The schema-readiness streak from this gap ends; downstream gaps surface one at a time per the TASK-MONO-014 prior-art fix-task pattern (PC-FE-023 메타 ⑤ + PC-FE-024 메타 ① already documented).

## Root cause (verbatim quotes from CI artifacts)

- Nightly run `26319887335` (push trigger after `a22725bc` = PC-FE-024 close, 2026-05-23T01:31:47Z) job `Platform Console E2E full-stack (Playwright + docker compose)` (job id `77486781283`) — step conclusions:
  - Step 9 *Start docker compose stack (5 services + MySQL + Redis)* — **success** (PC-FE-024 fix held: `mysql` + `redis` + `auth-service` + `account-service` + `admin-service` + `kafka` placeholder pulled in transitively via PC-FE-024's `depends_on: kafka` wiring; the kafka-DNS-loop ended).
  - Step 10 *Wait for admin-service health (gates seed.sql application)* — **success** at 2026-05-23T01:35:28.2675582Z: `admin-service healthy after 50s` (proves auth-service Flyway ran successfully — admin-service's Flyway depends on auth_db; this step would have timed out under PC-FE-024's predecessor gap).
  - Step 11 *Apply seed.sql (operators, finance accounts, OIDC client tweaks)* — **failure** at 2026-05-23T01:35:28.4113992Z:
    ```
    ERROR 1146 (42S02) at line 209: Table 'finance_db.accounts' doesn't exist
    ##[error]Process completed with exit code 1.
    ```
- Workflow source [.github/workflows/nightly-e2e.yml:707-741](../../.github/workflows/nightly-e2e.yml#L707-L741) (current phase ordering, top-to-bottom):
  1. Step 9 (line 707-711) — `docker compose up -d --build mysql redis auth-service account-service admin-service` (Phase 1: 5 GAP services + datastores).
  2. Step 10 (line 713-729) — `Wait for admin-service health` (gates seed.sql).
  3. Step 11 (line 731-735) — `Apply seed.sql` (the entire monolithic seed.sql).
  4. Step 12 (line 737-741) — `docker compose up -d --build finance-account-service console-bff console-web` (Phase 2: finance + BFF + web).
- Fixture source [projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql:197-233](../../projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql#L197-L233) — section 6 (`finance_db.accounts` + `balances` INSERTs). The fixture's own comment at line 197-205 is self-contradictory:
  ```
  -- Note: account-service Flyway V1__init.sql runs at finance container
  -- boot, which depends on finance_db existing — section 1 above creates
  -- it BEFORE finance-account-service is started (see workflow ordering).
  ```
  The comment correctly describes the *intent* (finance Flyway runs at finance container boot, finance_db must exist), but section 1 only `CREATE DATABASE IF NOT EXISTS finance_db` — it does NOT create the `accounts` / `balances` tables (those are finance-service Flyway's job). So section 6's INSERT runs before Flyway can create the tables.
- Inside [docker-compose.e2e.yml:329-332](../../projects/platform-console/docker-compose.e2e.yml#L329-L332) (`finance-account-service` env block):
  ```yaml
  # finance_db / finance / finance — created by seed.sql preamble before
  # account-service first boot (CI workflow runs seed.sql AFTER MySQL
  # healthy + BEFORE finance-account-service is started — see the workflow
  # step ordering).
  ```
  The comment confirms the design intent — `seed.sql` preamble (section 1) creates only the `finance_db` database + user; `finance-account-service` boot runs its Flyway V1__init.sql to create `accounts` / `balances` tables. The phase ordering is *correct for section 1* and *wrong for section 6*. Section 6's data must be inserted AFTER finance Flyway completes.

## Decision authority — Option A (`seed.sql` section 6 split into `seed-finance.sql`, workflow phase-2.5 step)

Four real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — split section 6 into `seed-finance.sql`, add workflow phase-2.5 step (chosen)** | Move section 6 (line 197-233) of `seed.sql` to a new sibling fixture `seed-finance.sql`. Add two workflow steps between current step 12 (`Start remaining containers`) and step 13 (`Wait for console-web health`): (a) `Wait for finance-account-service health` (mirrors the existing `Wait for admin-service health` shape), (b) `Apply seed-finance.sql` (mirrors the existing `Apply seed.sql` shape). | Honors the design intent the fixture's own comments + the docker-compose env block both *already document* (section 1 = finance_db CREATE before finance boot; section 6 = finance data INSERT after finance Flyway). Self-documenting: 2 fixtures × 2 workflow steps make the 2-phase contract visible at the workflow + fixture layers. Each fixture stays idempotent (`INSERT IGNORE`) so re-application during fixture iteration is safe. Adds ~25 LOC to the workflow (one shape-mirrored block). | Workflow byte-diff (= root MONO task per CLAUDE.md decision table line 90-91 + `tasks/INDEX.md` line 96-98). PC-FE-024 메타 ② ("resist `.github/workflows/` edit when the cause can be expressed at the docker-compose layer") is inapplicable here because phase scheduling — *when* a SQL fixture runs relative to a service's Flyway — is a workflow-layer responsibility; docker-compose `depends_on` only sequences container start, not external `docker compose exec` calls. |
| **B — promote `finance-account-service` into the workflow's Phase 1 `up` command** | Edit `.github/workflows/nightly-e2e.yml:707-711` to add `finance-account-service` to the named service list, so its Flyway runs before `seed.sql`. Then `Wait for admin-service health` step (or a new `Wait for finance-account-service health` step) gates `seed.sql`. | Trivial 1-line workflow edit. Single seed.sql; no fixture split. | Discards the deliberate Phase 1 / Phase 2 separation the workflow comment at line 737 (`Start remaining containers (finance + console-bff + console-web)`) and finance-account-service env-block comment line 329-332 ("BEFORE finance-account-service is started") *both* explicitly describe. The design intent of "GAP first, finance + BFF + web later" was that finance-related fixture data lands AFTER finance Flyway completes — Option B inverts the intent rather than realizing it. Also forces finance-service boot onto the critical path for *every* spec, even though only PC-FE-016/017/018 specs exercise the finance card. |
| **C — make section 6 idempotent + add a retry shim** | Wrap section 6 in a polling loop that retries on `ERROR 1146` until the table exists (e.g. bash `until docker compose exec mysql mysql -e 'SELECT 1 FROM finance_db.accounts LIMIT 1' ; do sleep 5 ; done`). | No fixture split. | Conflates schema-readiness with data-readiness; the workflow has to know about `finance_db.accounts` table name (leaks finance-platform-internal schema into platform-console workflow). Doesn't compose: future finance schemas would each need a parallel polling shim. Polling masks the real ordering bug. |
| **D — drop the explicit Phase 1 service list (`docker compose up -d --build` with no names)** | Same as PC-FE-024's rejected Option C — drop the selective `up` to bring up everything at once. | Simplest workflow edit. | Same reasons PC-FE-024 rejected this: changes more workflow semantics than necessary; finance-account-service + console-bff + console-web start before `seed.sql`, which means console-bff's startup probes against admin-service's `/admin/operators` would fail because the seed operators aren't there yet → cascading nondeterminism. |

**Chosen — Option A.** Rationale:

1. **Honors the documented design intent.** Both `seed.sql` line 197-205 ("section 1 creates finance_db BEFORE finance-account-service") and `docker-compose.e2e.yml` line 329-332 ("seed.sql AFTER MySQL healthy + BEFORE finance-account-service is started") describe the *correct* phase ordering for section 1, but section 6's INSERT was implicitly assumed to also happen in that pre-finance window — which it cannot, because the `accounts` table only exists after finance Flyway runs. Option A makes the unspoken second half of the design (finance data INSERT *after* finance Flyway) explicit at the workflow layer.

2. **PC-FE-024 boundary metaphor inapplicable.** PC-FE-024 메타 ② said "resist `.github/workflows/` edit when the cause can be expressed at the docker-compose layer." Here the cause *cannot* be expressed at the docker-compose layer — `docker compose exec mysql mysql < seed-finance.sql` is not a service in the compose graph, it's a workflow-orchestrated external action. The phase scheduling of *external* SQL apply calls relative to *service-internal* Flyway runs is intrinsically a workflow concern.

3. **Self-documenting at two layers.** Two named fixtures (`seed.sql` for GAP-side + finance_db preamble, `seed-finance.sql` for finance schema data) + two named workflow steps (`Apply seed.sql` at phase 1.5, `Apply seed-finance.sql` at phase 2.5) make the phase contract visible without leaking schema names into the workflow.

4. **Mirrors existing shape.** The new `Wait for finance-account-service health` step is a structural copy of the existing `Wait for admin-service health` step (same retry pattern, same timeout, same log-dump-on-failure). The new `Apply seed-finance.sql` step is a structural copy of the existing `Apply seed.sql` step. ~25 LOC added; zero new bash/shell idioms.

5. **Project-internal data + monorepo-level orchestration is the natural seam.** The data (what to seed) lives where it always did — `projects/platform-console/apps/console-web/tests/e2e/fixtures/`. The orchestration (when to seed) lives in the workflow — `.github/workflows/nightly-e2e.yml`. Each layer owns its responsibility.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` — delete section 6 (line 197-233 inclusive: the section-6 comment block + `USE finance_db;` + `INSERT IGNORE INTO accounts (...)` + `INSERT IGNORE INTO balances (...)`). The trailing `--- ` divider line at line 233 remains as the file's last line.
- `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed-finance.sql` — new file containing the deleted content + a top-of-file header comment block (~15 lines) explaining: (1) why this is split out (phase 2.5 ordering, fixture comment from `seed.sql` line 197-205 verbatim), (2) re-runnable (same `INSERT IGNORE` discipline), (3) cross-ref to TASK-MONO-132 + PC-FE-024 + PC-FE-023.
- `.github/workflows/nightly-e2e.yml` — insert two new steps between current step 12 (`Start remaining containers (finance + console-bff + console-web)`, line 737-741) and current step 13 (`Wait for console-web health`, line 743-756):
  - **New step 12.5** — `Wait for finance-account-service health` (mirror of `Wait for admin-service health` line 713-729; targets `finance-account-service` instead of `admin-service`; same 30 × 10s retries = ~5 min budget; on timeout, `docker compose logs --tail=50 finance-account-service` then exit 1).
  - **New step 12.7** — `Apply seed-finance.sql` (mirror of `Apply seed.sql` line 731-735; reads `apps/console-web/tests/e2e/fixtures/seed-finance.sql`).
- This task md + root `tasks/INDEX.md` ready entry.

## Out of Scope

- `projects/platform-console/apps/console-bff/` byte-diff (AC-4 — ADR-MONO-017 D4 HARD INVARIANT).
- `projects/platform-console/docker-compose.e2e.yml` byte-diff (AC-6 — compose graph is already correct per PC-FE-023 + PC-FE-024; phase ordering is a workflow concern).
- `projects/global-account-platform/` byte-diff (AC-3 — GAP source byte-unchanged; PC-FE-023's zero-retrofit invariant continues).
- `projects/finance-platform/` byte-diff (AC-7 — finance source byte-unchanged; seed data lives in platform-console e2e fixtures per ADR-MONO-017 + the existing seed.sql AC-3/AC-5 invariant).
- `projects/{wms,scm,erp,fan,ecommerce}-platform/` byte-diff (AC-5 — zero-retrofit other producers, **20회째** if landed; PC-FE-024's running count was `19회째`).
- Any change to `projects/platform-console/specs/contracts/` or `apps/console-web/src/lib/parity/` (AC-8 — no contract / parity drift; parity matrix count unchanged at 18).
- Any change to PR-time smoke (`platform-console-e2e-smoke` job in nightly-e2e.yml's smoke region or any other workflow) — the smoke job intentionally uses a different fixture/skip set (PC-FE-021).
- Resolving the *next* gap that surfaces after this fix lands (e.g., if `console-bff` health becomes the next bottleneck, or Playwright assertion drift). Separate fix-tasks per TASK-MONO-014 cycle pattern.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — `Platform Console E2E full-stack` nightly job's *step 11 `Apply seed.sql`* and *step 12.7 `Apply seed-finance.sql`* both **success**. The `Apply seed.sql` step's exit code is 0 with no `ERROR 1146` line in its log; the new `Apply seed-finance.sql` step exits 0 after the new `Wait for finance-account-service health` step reports healthy. Verified by the next nightly cron after merge, or by `workflow_dispatch` on the fix branch.
- [ ] **AC-2 (functional, local)** — From `projects/platform-console/`:
  ```bash
  docker compose -f docker-compose.e2e.yml up -d --build mysql redis auth-service account-service admin-service
  # wait for admin-service health (manual or scripted)
  docker compose -f docker-compose.e2e.yml exec -T mysql mysql -uroot -prootpass < apps/console-web/tests/e2e/fixtures/seed.sql
  # NO 'ERROR 1146 at line 209' — seed.sql no longer touches finance_db.accounts
  docker compose -f docker-compose.e2e.yml up -d --build finance-account-service console-bff console-web
  # wait for finance-account-service health
  docker compose -f docker-compose.e2e.yml exec -T mysql mysql -uroot -prootpass < apps/console-web/tests/e2e/fixtures/seed-finance.sql
  # 1 account + 1 balance INSERT, both IGNORE-safe on re-run
  docker compose -f docker-compose.e2e.yml exec -T mysql mysql -ufinance -pfinance -e 'SELECT COUNT(*) FROM finance_db.accounts; SELECT COUNT(*) FROM finance_db.balances'
  # Each returns 1
  ```
- [ ] **AC-3 (hard invariant — GAP byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/` = empty.
- [ ] **AC-4 (hard invariant — console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty.
- [ ] **AC-5 (hard invariant — zero-retrofit other producers, 20회째)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/'` = empty.
- [ ] **AC-6 (hard invariant — docker-compose.e2e.yml byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/docker-compose.e2e.yml` = empty. PC-FE-023's `kafka` service block + PC-FE-024's `depends_on: kafka` additions remain byte-unchanged.
- [ ] **AC-7 (hard invariant — finance-platform byte-unchanged)** — `git diff --stat origin/main -- projects/finance-platform/` = empty.
- [ ] **AC-8 (no contract / parity drift)** — `git diff --stat origin/main -- projects/platform-console/specs/contracts/ projects/platform-console/apps/console-web/src/lib/parity/` = empty. Parity matrix count unchanged at 18.
- [ ] **AC-9 (seed.sql section 6 cleanly removed)** — `git grep -n 'finance_db.accounts\|finance_db.balances' projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` returns 0 matches (the `accounts` and `balances` table names appear only in `seed-finance.sql` after this fix). `git grep -n '^USE \`finance_db\`' projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` returns 0 matches (the `USE finance_db;` directive is moved to `seed-finance.sql`).
- [ ] **AC-10 (seed-finance.sql is idempotent)** — `docker compose exec mysql mysql < seed-finance.sql` twice in a row yields the same row counts (1 row per table). Achieved via `INSERT IGNORE` on both INSERT statements (same idempotency discipline as `seed.sql`).
- [ ] **AC-11 (workflow byte-diff scoped to platform-console-e2e-fullstack job)** — `git diff origin/main -- .github/workflows/nightly-e2e.yml` shows changes confined to the `platform-console-e2e-fullstack` job (line ~650-770 range); other workflow jobs (`ecommerce-frontend-e2e-fullstack`, `wms-platform-e2e-full`, `fan-platform-e2e-full`, `scm-platform-e2e-full`, `gap-e2e-full`) byte-unchanged.
- [ ] **AC-12 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes (PR `state=MERGED` + `mergeCommit` matches `git log origin/main` tip + pre-merge `gh pr checks` snapshot `failing=0`). Same protocol PC-FE-024 + MONO-131 just used.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — root MONO task; no project `PROJECT.md` resolution needed (this task touches root paths + project fixtures, but the rule layer that governs root-level workflow changes is the root `CLAUDE.md` + `tasks/INDEX.md` shared layer).

- [`tasks/INDEX.md`](../INDEX.md) — root lifecycle + "Root vs Project Tasks" decision table (line 86-98). This task is root-level because `.github/workflows/` is a shared path (line 91).
- [`projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql`](../../projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql) — the source file split by this task. Section 6 (line 197-233) moves out; sections 1-5 stay (finance_db CREATE in section 1 stays at phase 1.5 — that part of the design is correct).
- [`projects/platform-console/docker-compose.e2e.yml`](../../projects/platform-console/docker-compose.e2e.yml) — read-only reference. `finance-account-service` block (line 320-351) has the env-block comment that motivates this fix; `healthcheck:` block (line 346-351) defines the readiness signal the new workflow step waits on.
- [`.github/workflows/nightly-e2e.yml`](../../.github/workflows/nightly-e2e.yml) — the workflow file modified by this task. `platform-console-e2e-fullstack` job is line ~650-770.
- [`projects/platform-console/tasks/done/TASK-PC-FE-024-e2e-kafka-placeholder-depends-on.md`](../../projects/platform-console/tasks/done/TASK-PC-FE-024-e2e-kafka-placeholder-depends-on.md) — the predecessor; this task closes the gap PC-FE-024's § Goal predicted as "the next legitimate step in the boot sequence — likely either `auth_db.credentials` schema readiness, `console-bff` health, or the first Playwright assertion".
- [`projects/platform-console/tasks/done/TASK-PC-FE-023-e2e-account-service-kafka-dns-gap.md`](../../projects/platform-console/tasks/done/TASK-PC-FE-023-e2e-account-service-kafka-dns-gap.md) — the grandparent.

# Related Contracts

- None. This task does not touch any HTTP or event contract, parity matrix, ADR, or domain spec.

# Related Skills

- None additional. CLAUDE.md standard workflow applies (spec PR → impl PR → close-chore PR with BE-303 3-dim verification).

---

# Edge Cases

- **`docker compose down -v` between runs** — both fixtures are idempotent (`INSERT IGNORE`); re-applying after a clean teardown produces identical row counts.
- **`workflow_dispatch` on fix branch** — workflow file edits take effect immediately on the dispatched branch; this is the recommended verification path before relying on the next nightly cron.
- **`finance-account-service` health check timeout (~4 min)** — `start_period=60s` + `retries=12 * interval=15s = 180s` ≈ 4 min budget per the compose file's existing healthcheck. The new `Wait for finance-account-service health` step uses the same 30 × 10s = 5 min budget the existing `Wait for admin-service health` uses, giving a small grace margin.
- **`seed-finance.sql` runs but `finance-account-service` Flyway is still mid-run** — guarded by `Wait for finance-account-service health` (compose healthcheck hits `/actuator/health`, which returns UP only after Flyway has applied all pending migrations on a fresh DB).
- **Future finance schema additions** — additional finance-internal tables in `seed-finance.sql` would be naturally accommodated by the new phase 2.5 step. Adding new GAP-side tables to `seed.sql` (phase 1.5) is also unaffected.
- **Future `finance-account-service` Flyway migration that requires data BEFORE its own migration** — would need a *new* fixture between Phase 1 boot and finance-service start. Same workflow-step-addition pattern would apply; not surfaced by this task.

# Failure Scenarios

- **`finance-account-service` fails to become healthy** — `Wait for finance-account-service health` step times out at 5 min, dumps `docker compose logs --tail=50 finance-account-service`, exits 1. The downstream `Apply seed-finance.sql` step is skipped (job already failed). Investigation path: finance Flyway error or container start failure in the log dump.
- **`seed-finance.sql` syntax error** — `mysql` client exits non-zero; current `set -e` step semantics propagate the failure. AC-2 local verification catches this before merge.
- **Phase ordering regression by a future workflow edit** — if a future edit moves `Apply seed.sql` after Phase 2 by mistake, GAP-side seeds would race finance services. Mitigation: AC-1 + AC-2 are regression tests for the *current* ordering; the comment block at the top of each fixture states the phase explicitly.
- **`USE finance_db;` directive accidentally left in `seed.sql`** — would cause `seed.sql` to switch DB context, but no subsequent `seed.sql` statement uses `finance_db` after section 6's removal, so it would be a harmless no-op. AC-9 grep guards against this anyway.

---

# Test Requirements

- AC-2 local one-shot reproduction (the workflow's exact command sequence, with both fixture applies and the row-count assertion).
- CI verification = the next nightly firing (or `workflow_dispatch` on the fix branch — strongly recommended for faster signal per PC-FE-024 메타 ⑤).
- No new automated test needed; the existing `Platform Console E2E full-stack` job IS the regression test.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + root `tasks/INDEX.md` ready entry), (2) impl PR (seed.sql section 6 deletion + new seed-finance.sql + workflow 2-step insertion), (3) close-chore PR (`git mv ready/ → done/` + Status flip + root INDEX move + BE-303 3-dim verification documented; BE-299 re-stage check).
- [ ] AC-1 through AC-12 all checked off in the close-chore PR description.
- [ ] PC-FE-024 INDEX entry (project-level `projects/platform-console/tasks/INDEX.md` done section) updated with one cross-ref line under its existing closure block: "**PC-FE-024 § Goal next-layer prediction realized by TASK-MONO-132** (root MONO task, 2026-05-23~, PR #N spec / #M impl — seed.sql section 6 phase-2.5 split; `finance_db.accounts` schema-readiness gap closed)".
- [ ] Memory `project_operator_overview_finance_card_resolution_complete.md` "First nightly cron result observation" line cross-ref extended (currently points to PC-FE-023 + PC-FE-024; add MONO-132 alongside as the third surface in the streak).

---

# 메타 (intended)

① **TASK-MONO-014 cycle pattern works as designed (third cycle in the streak)** — PC-FE-023 closed the kafka-DNS gap *in design*; PC-FE-024 closed the integration gap (workflow-vs-compose `up <names>` selective seam); MONO-132 closes the schema-readiness phase-ordering gap PC-FE-024's § Goal explicitly predicted. Each cron cycle surfaces the next layer; each fix-task is narrower and surgical than the last. Three cycles in three days (2026-05-21 → 2026-05-22 → 2026-05-23).

② **Root vs project boundary works as the decision table prescribes** — PC-FE-023 + PC-FE-024 were project-internal (compose graph + service block edits); MONO-132 is root-level because phase scheduling of `docker compose exec mysql mysql < ...` calls is intrinsically a workflow concern. The `tasks/INDEX.md` line 96-98 rule ("If any path lies outside `projects/<name>/`, use the root task lifecycle") makes the lifecycle choice mechanical. PC-FE-024 메타 ② ("resist `.github/workflows/` edit") was scoped to docker-compose-expressible causes; not a blanket prohibition.

③ **Documented design intent realized, not invented** — the fix executes what `seed.sql` line 197-205 + `docker-compose.e2e.yml` line 329-332 *already say* the system should do (section 1 creates finance_db before finance boot; finance Flyway runs at finance boot). The hidden second half (section 6 must run AFTER finance Flyway) was implicit and untested; this task makes it explicit + tested.

④ **Self-documenting two-fixture / two-step shape** — naming `seed.sql` + `seed-finance.sql` (vs. `seed-phase1.sql` + `seed-phase2.sql`) keeps the semantics readable: `seed-finance.sql` contains *what only finance can apply after its own Flyway*. Workflow step names mirror the fixture names. A future reader can locate the phase ordering from either layer without round-tripping git blame.

⑤ **Mirror existing shape over invent new patterns** — the new `Wait for finance-account-service health` and `Apply seed-finance.sql` steps are structural copies of the existing `Wait for admin-service health` and `Apply seed.sql` steps. Same retry pattern, same timeout, same log-dump-on-failure. ~25 LOC added; zero new bash/shell idioms; reviewer cognitive load minimized.

⑥ **PC-FE-024 메타 ② boundary metaphor refined** — "resist workflow edit when docker-compose layer can express the cause" remains valid as a *project-internal preservation heuristic*, but `docker compose exec <svc> mysql < file` is a workflow-orchestrated action, not a compose graph member. Future PC-FE / MONO triage: if the fix is *between* container start events (or between a container event and an external action), the workflow is the natural seam.

⑦ **AC-1 verification still nightly-cron-bound (or `workflow_dispatch`)** — same as PC-FE-023 메타 ⑤ + PC-FE-024 메타 ⑤; the authoritative signal is the post-merge nightly cron. `workflow_dispatch` on fix branch optional but recommended for faster iteration (already exercised by this task's spec authoring — dispatcher will run it before the close chore).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (Decision-Authority-bearing — choice between Option A (split + workflow phase-2.5) vs Option B (Phase 1 promotion) vs Option C (polling shim) vs Option D (drop selective up); fixture split + workflow 2-step is mechanical but the option choice is Opus-shaped per CLAUDE.md scope rule).
