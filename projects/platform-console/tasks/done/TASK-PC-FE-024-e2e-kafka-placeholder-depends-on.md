# Task ID

TASK-PC-FE-024

# Title

platform-console e2e — wire the TASK-PC-FE-023 `kafka` DNS placeholder into the workflow's `docker compose up` graph. The placeholder service was added correctly (PR #760 `98904251`) but the nightly workflow brings up only 5 specific services by name (`mysql redis auth-service account-service admin-service`); the placeholder is never started, `getent hosts kafka` keeps failing, and the three GAP service Dockerfiles' BE-048 ENTRYPOINT pre-flight (`waiting for DNS: mysql/kafka/redis`) still loops indefinitely — surfaced by the first post-PC-FE-023 nightly job (`26317428307` job `77479646291`) which advanced past the spec gap (kafka DNS) and then revealed this workflow / docker-compose seam gap when `seed.sql` ran before `auth_db.credentials` was created (auth-service Flyway never ran because auth-service entrypoint never exited).

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- fix

---

# Dependency Markers

- **depends on**: TASK-PC-FE-023 (DNS-only `kafka` placeholder, **DONE** 2026-05-22, PR #758 spec / #760 impl / #761 close). PC-FE-023 designed the placeholder correctly — `alpine:3.19` + `sleep infinity` service named `kafka` in the `pc-e2e` network. This task closes the *integration* gap PC-FE-023 left open: the placeholder is defined in `docker-compose.e2e.yml` but the workflow's selective `docker compose up <names>` command does not pull it in, so the placeholder container is never created and DNS resolution still fails. PC-FE-023's AC-1 verification fired the gap: kafka-loop ended for `account-service` whose Dockerfile entrypoint waits on `mysql/kafka` (the workflow runs `mysql` explicitly), but `auth-service` / `admin-service` / `security-service` whose Dockerfiles wait on `mysql/kafka/redis` are still stuck because `kafka` is not in the workflow's start set.
- **prerequisite of**: any future `PC-FE` task that depends on nightly full-stack GREEN signal. Same dependency profile as PC-FE-023 (Operator Overview finance card production-parity nightly regression guard).

---

# Goal

Make the `Platform Console E2E full-stack` nightly job advance past the `getent hosts kafka` pre-flight for **all three** GAP services in the overlay (`auth-service` / `account-service` / `admin-service`), not just `account-service`. After this fix lands, the next gap surfaced by the nightly will be the *next* legitimate step in the boot sequence — likely either `auth_db.credentials` schema readiness, `console-bff` health, or the first Playwright assertion. The streak from this gap ends; downstream gaps surface one at a time per the TASK-MONO-014 prior-art fix-task pattern PC-FE-023 메타 ⑤ already documented.

## Root cause (verbatim quotes from CI artifacts)

- Nightly run `26317428307` (push trigger after `be85bb2e` = PC-FE-023 close, 2026-05-22T23:49:56Z) job `Platform Console E2E full-stack (Playwright + docker compose)` (job id `77479646291`):
  - Step *Start docker compose stack (5 services + MySQL + Redis)* at 2026-05-22T23:53:45.5889523Z: `Container platform-console-e2e-auth-service-1  Started` — container is `Started` (entrypoint shell running) but never exits the DNS-wait loop.
  - Step *Apply seed.sql (...)* at 2026-05-22T23:57:39.0202606Z: `ERROR 1146 (42S02) at line 84: Table 'auth_db.credentials' doesn't exist`. The credentials table is created by [auth-service V0001 migration](../../../global-account-platform/apps/auth-service/src/main/resources/db/migration/V0001__create_credentials_and_refresh_tokens.sql); it's missing because Flyway never ran because the JVM never started because `getent hosts kafka` never resolved.
  - Step *Dump docker compose logs on failure* tail: dozens of `auth-service-1 | waiting for DNS: mysql/kafka/redis` lines — the entrypoint loop is still active 4 minutes into the workflow.
- Workflow source [.github/workflows/nightly-e2e.yml:707-711](../../../../.github/workflows/nightly-e2e.yml#L707-L711):
  ```yaml
  - name: Start docker compose stack (5 services + MySQL + Redis)
    working-directory: projects/platform-console
    run: |
      docker compose -f docker-compose.e2e.yml up -d --build \
        mysql redis auth-service account-service admin-service
  ```
  `kafka` is not in this list. Docker Compose's `up <name>` command only starts the named services + their `depends_on` transitively; since none of these 5 declare `kafka` in their `depends_on`, the placeholder is skipped.

## Decision authority — Option A (`depends_on` wiring, project-internal)

Three real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — declare `kafka` in `depends_on` of the 3 GAP services** | Add `kafka: { condition: service_started }` to `auth-service`, `account-service`, `admin-service` depends_on blocks in `projects/platform-console/docker-compose.e2e.yml`. Compose's transitive dependency resolution will pull in the `kafka` placeholder when any of those 3 are named in `up`. | Project-internal (CLAUDE.md Shared-vs-project boundary preserved — no `.github/workflows/` byte-diff); self-documenting at the docker-compose layer; works for both CI workflow and local `docker compose up auth-service`; matches the `mysql` and `redis` depends_on pattern those services already use. | Slightly cosmetic noise (3 depends_on edits). The `kafka` placeholder has no healthcheck so condition must be `service_started`, not `service_healthy`. |
| **B — add `kafka` to the workflow's `docker compose up` command** | Edit `.github/workflows/nightly-e2e.yml:707-711` to add `kafka` to the named service list. | Trivial one-line edit; explicit. | Workflow byte-diff = monorepo-level task (CLAUDE.md "Shared paths"); fails for any local invocation that uses the same selective list pattern (devs must remember to add `kafka` everywhere); fix lives in the workflow, not in the file that documents the placeholder's existence. |
| **C — use `docker compose up -d` without service names** | Drop the explicit service list; bring up everything. | Simplest workflow edit. | Brings up `finance-account-service` + `console-bff` + `console-web` too early (the workflow's two-phase ordering is deliberate — GAP services start first, then `seed.sql` runs, then the rest. Phase 1 / Phase 2 separation would be lost). Changes more workflow semantics than necessary. |

**Chosen — Option A.** Rationale:

1. CLAUDE.md Shared-vs-project boundary: `.github/workflows/` is shared-path. Fixing the symptom there means a monorepo-level task with broader review surface. Option A keeps the fix project-internal to `projects/platform-console/`.
2. Self-documenting: the depends_on declaration tells *every* future reader (CI, dev, contributor) exactly which services the placeholder is required for. Workflow-level fix hides the requirement in CI config.
3. Matches existing pattern: those same 3 GAP services already declare `mysql` and `redis` in `depends_on` (with `condition: service_healthy`). Adding `kafka: { condition: service_started }` is a natural extension.
4. Phase ordering preserved: the workflow's deliberate two-phase startup (5 GAP services → seed.sql → 3 client services) remains intact because the placeholder is only pulled in by the 3 GAP services in Phase 1, and the placeholder is lightweight (`alpine:3.19 + sleep infinity` ≈ 5 MB image, ~0 s warm-up).

---

# Scope

## In Scope

- `projects/platform-console/docker-compose.e2e.yml` — add `kafka: { condition: service_started }` to the `depends_on` block of `auth-service`, `account-service`, `admin-service`. No other change to those service definitions. No change to the `kafka` placeholder service block itself (it was correctly defined by PC-FE-023).
- Inline comment near the new `depends_on: kafka` entries pointing to PC-FE-023 + this task for traceability (so a future reader can find the rationale without round-tripping through git blame).
- This task md + INDEX move.

## Out of Scope

- `projects/platform-console/apps/console-bff/` byte-diff (AC-4 — ADR-MONO-017 D4 HARD INVARIANT).
- Any change to `.github/workflows/` (AC-8 — fix is entirely inside `docker-compose.e2e.yml`; Option B / C explicitly rejected per § Decision authority).
- Any change to `projects/global-account-platform/` (AC-3 — GAP source byte-unchanged, PC-FE-023's hard invariant continues).
- Any change to other producer projects' bytes (AC-5 — zero-retrofit, **19회째 if landed**: PC-FE-023's running count was `18회째`).
- Any change to `console-integration-contract.md` or parity matrix (no operation/contract change).
- Resolving the *next* gap that surfaces after this fix lands (e.g., if `auth_db.credentials` schema becomes the next bottleneck for another reason, or `console-bff` health, or Playwright assertion drift). Separate fix-tasks per TASK-MONO-014 cycle pattern.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Platform Console E2E full-stack nightly job advances past the kafka-DNS-loop for **all 3 GAP services**. Specifically: the `Dump docker compose logs on failure` step's `auth-service-1 | waiting for DNS: mysql/kafka/redis` line count is **0** (or the job becomes GREEN entirely, in which case the dump step does not execute). Verified by the next nightly cron after merge, or by `workflow_dispatch` on the fix branch.
- [ ] **AC-2 (functional, local)** — `cd projects/platform-console && docker compose -f docker-compose.e2e.yml up -d --build mysql redis auth-service account-service admin-service` (workflow's exact command) followed by `docker compose -f docker-compose.e2e.yml ps kafka` shows the placeholder container `Running`. `docker compose -f docker-compose.e2e.yml exec auth-service getent hosts kafka` resolves successfully (or `docker compose -f docker-compose.e2e.yml logs auth-service | grep -c 'waiting for DNS' < 10` — a handful of pre-startup ticks is fine, the loop must exit).
- [ ] **AC-3 (hard invariant — GAP byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/` = empty.
- [ ] **AC-4 (hard invariant — console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/src/` = empty.
- [ ] **AC-5 (hard invariant — zero-retrofit other producers, 19회째)** — `git diff --stat origin/main -- 'projects/{wms,scm,finance,erp,fan,ecommerce}-platform/'` = empty.
- [ ] **AC-6 (PC-FE-023 placeholder service unchanged)** — `git diff origin/main -- projects/platform-console/docker-compose.e2e.yml` should show changes confined to the `depends_on` blocks of 3 services + comment additions. The `kafka:` service definition itself (image / command / restart / networks) must be byte-unchanged from PC-FE-023.
- [ ] **AC-7 (no contract / parity drift)** — `git diff --stat origin/main -- projects/platform-console/specs/contracts/ projects/platform-console/apps/console-web/src/lib/parity/` = empty. Parity matrix count unchanged at 18.
- [ ] **AC-8 (no workflow change)** — `git diff --stat origin/main -- .github/workflows/` = empty (Option A means the fix is entirely inside `docker-compose.e2e.yml`).
- [ ] **AC-9 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes. Same protocol PC-FE-023 just used.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/docker-compose.e2e.yml`](../../docker-compose.e2e.yml) — the file changed by this task. PC-FE-023's `kafka:` service block at lines ~130-170 must remain byte-unchanged; this task adds depends_on entries to the 3 GAP service blocks.
- [`projects/platform-console/tasks/done/TASK-PC-FE-023-e2e-account-service-kafka-dns-gap.md`](../done/TASK-PC-FE-023-e2e-account-service-kafka-dns-gap.md) — the predecessor; this task closes its AC-1's surfaced gap.
- [`projects/global-account-platform/apps/{auth,account,admin}-service/Dockerfile`](../../../global-account-platform/apps/auth-service/Dockerfile) — read-only reference (BE-048 ENTRYPOINT this fix completes the bridge for); **must not** be modified.

# Related Contracts

- None. This task does not touch any HTTP or event contract, parity matrix, or ADR.

# Related Skills

- None additional. CLAUDE.md standard workflow applies (spec PR → impl PR → close-chore PR with BE-303 3-dim verification).

---

# Edge Cases

- **`docker compose down -v` between runs** — `depends_on` is honored on every `up`; no cleanup nuance.
- **`docker compose up auth-service` (single-service invocation, dev path)** — now pulls in `mysql` + `redis` + `kafka` placeholder transitively. Improvement, not regression.
- **`condition: service_started` semantics** — Compose considers a container "started" when its OCI process is up. For `alpine:3.19 + sleep infinity`, that's effectively immediate (no startup latency). The `getent hosts kafka` call in the GAP entrypoint will succeed within milliseconds of the placeholder being "started".
- **Future `kafka` promotion to a real broker (Option A2 of PC-FE-023)** — when this happens, the depends_on can stay as `service_started` (broker startup takes ~10-30s; getent only needs DNS resolution, not broker handshake), or be tightened to `service_healthy` if the GAP services then need a healthy broker (they don't — they have `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999`, the broker is for future spec needs).

# Failure Scenarios

- **Compose version mismatch on the runner** — `depends_on: { service: { condition: ... } }` long form requires Compose v2 (which GHA runners have). If the runner uses Compose v1 short form somewhere, this fix would syntax-error. Mitigation: AC-2 local verifies the syntax loads via `docker compose config --quiet`.
- **Placeholder container fails to start** — `sleep infinity` is PID 1 in alpine; cannot exit under normal load. `restart: unless-stopped` (already in PC-FE-023's service def) covers the unlikely OOM-then-restart case.
- **A future migration introduces a different DNS dependency** — if a new GAP service Dockerfile adds `getent hosts kafka-controller` or similar, this fix doesn't help. Surfaced by the next nightly fix-task per cycle pattern.

---

# Test Requirements

- AC-2 local one-shot reproduction (the workflow's exact `up` command, then `docker compose exec auth-service getent hosts kafka`).
- CI verification = the next nightly firing (or `workflow_dispatch` on the fix branch — strongly recommended for faster signal).
- No new automated test needed; the existing `Platform Console E2E full-stack` job IS the regression test.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + INDEX entry), (2) impl PR (single-file edit to `docker-compose.e2e.yml`), (3) close-chore PR (`git mv ready/ → done/` + Status flip + INDEX move + BE-303 3-dim verification documented).
- [ ] AC-1 through AC-9 all checked off in the close-chore PR description.
- [ ] PC-FE-023 INDEX entry updated with one cross-ref line under its 메타 ⑤ closure block: "**PC-FE-023 AC-1 secondary gap closed by TASK-PC-FE-024** (2026-05-22~, PR #N spec / #M impl — workflow-vs-compose `up <names>` selective seam; `depends_on: kafka` on the 3 GAP services pulls placeholder in transitively, AC-1 re-verified)".
- [ ] Memory `project_operator_overview_finance_card_resolution_complete.md` "First nightly cron result observation" line cross-ref extended (currently points to PC-FE-023; add PC-FE-024 alongside as the secondary fix).

---

# 메타 (intended)

① **TASK-MONO-014 cycle pattern works as designed** — PC-FE-023 closed the kafka-DNS gap *in design*; PC-FE-024 closes the integration gap (workflow-vs-compose `up <names>` selective seam) PC-FE-023 didn't see because PC-FE-023 AC-1 was deferred to next nightly. Each cron cycle surfaces the next layer; each fix-task is narrower and surgical than the last.

② **CLAUDE.md Shared-vs-project boundary tested again** — same instinct PC-FE-023 had (resist GAP Dockerfile edit) applied to workflow: resist `.github/workflows/` edit when the cause can be expressed at the docker-compose layer. Option A (project-internal depends_on) honored the boundary; Option B (workflow edit) would have crossed it for a narrower fix.

③ **Self-documenting depends_on > workflow-level fix** — `depends_on: kafka` in 3 service blocks tells every future reader (CI + local dev + new contributor) the requirement. Workflow-level fix would have hidden the requirement in `.github/workflows/nightly-e2e.yml:711` where most readers don't look.

④ **Compose `up <name>` transitive dependency resolution** — the workflow's selective `up <names>` command is a *partial* graph traversal; only `depends_on` edges pull additional services in. This is true for every `up <name>` invocation, not just the workflow's — adding `kafka` to depends_on improves the local dev experience too.

⑤ **AC-1 verification still nightly-cron-bound** — same as PC-FE-023 메타 ⑤; the authoritative signal is the post-merge nightly cron. `workflow_dispatch` on fix branch optional but recommended.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (Decision-Authority-bearing — choice between Option A (project-internal depends_on) vs Option B (workflow byte-diff) vs Option C (drop selective up); single-file 3-edit impl mechanical but the option choice is Opus-shaped per CLAUDE.md scope rule).
