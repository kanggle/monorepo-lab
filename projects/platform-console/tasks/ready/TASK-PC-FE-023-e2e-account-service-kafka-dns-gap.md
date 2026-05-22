# Task ID

TASK-PC-FE-023

# Title

platform-console e2e — finalize TASK-PC-FE-019 nightly first-fire `kafka` DNS gap. The first scheduled nightly (UTC 2026-05-22 18:00 = KST 2026-05-23 03:00) of the PC-FE-019 harness fired RED on the `Platform Console E2E full-stack (Playwright + docker compose)` job because `apps/account-service/Dockerfile`'s `BE-048` ENTRYPOINT blocks JVM startup on `getent hosts kafka`, while [`docker-compose.e2e.yml`](../../docker-compose.e2e.yml) deliberately excludes Kafka (per its own scope-discipline comment). Three GAP services in the overlay (`auth-service`, `account-service`, `admin-service`) all have the same `kafka` getent check; without `kafka` resolving they spin forever, depends_on cascade stalls, `console-bff` + `console-web` never boot, Playwright never runs (`No files were found with the provided path: playwright-report/`), the job times out.

# Status

ready

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
- fix

---

# Dependency Markers

- **depends on**: TASK-PC-FE-019 (Playwright e2e harness standup, **DONE** 2026-05-21, PR #726). PC-FE-019 explicitly deferred first-nightly-cycle verification per its `메타 ⑤ first-cycle nightly result 미확인 (next cron 대기) — 첫 nightly cron 결과는 UTC 18:00 = KST 03:00 대기 후 확인, surface 한 orchestration / seed timing 이슈는 fix-task 으로 cycle (TASK-MONO-014 prior art 패턴)`. This task is exactly that fix-task following the first-fire surface.
- **prerequisite of**: any future `PC-FE` task that depends on nightly full-stack GREEN signal (Operator Overview finance card production-parity verification, ADR-MONO-013 § D6 Phase 4-6 nightly regression guard).

---

# Goal

Make the Platform Console E2E full-stack nightly job GREEN by closing the `kafka`-DNS-blocks-boot gap **without** modifying GAP Dockerfile bytes (AC-3 hard invariant from PC-FE-019/021/022 — "GAP-owned territory; this project must not byte-diff GAP source") and **without** dropping the overlay's "no Kafka broker" scope discipline (its own comment block §22-26: "Excluded (deliberately — mirrors ecommerce CI overlay scope discipline) — the 2 e2e specs do not exercise an event-publishing code path; admin-service writes `admin_actions` synchronously. Future spec needing event observation can re-add Kafka.").

The narrow surface is: `getent hosts kafka >/dev/null` must succeed inside the three GAP service containers, so their ENTRYPOINT loop exits and the JVM starts. The application-level Kafka producers then attempt `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999` (already non-routable, by overlay design) and log warnings without crashing the Spring context — this is the existing intent ("the consumer pollers log warnings but do not crash the application context").

## Root cause (verbatim quotes from source files)

- [`projects/global-account-platform/apps/account-service/Dockerfile:36`](../../../global-account-platform/apps/account-service/Dockerfile):
  ```
  ENTRYPOINT ["sh","-c","until getent hosts mysql >/dev/null && getent hosts kafka >/dev/null; do echo 'waiting for DNS: mysql/kafka'; sleep 1; done; exec java $JAVA_OPTS -jar /app/app.jar"]
  ```
- [`projects/global-account-platform/apps/auth-service/Dockerfile`](../../../global-account-platform/apps/auth-service/Dockerfile) + [`apps/admin-service/Dockerfile`](../../../global-account-platform/apps/admin-service/Dockerfile): identical `getent hosts kafka` block (with `redis` appended).
- [`projects/platform-console/docker-compose.e2e.yml:22-31`](../../docker-compose.e2e.yml): scope-discipline comment explicitly drops Kafka.
- Nightly run `26309137133` (2026-05-22T19:59:03Z, schedule trigger, head SHA `d93625df`) — job `Platform Console E2E full-stack (Playwright + docker compose)` log:
  ```
  account-service-1  | waiting for DNS: mysql/kafka
  account-service-1  | waiting for DNS: mysql/kafka
  […]
  ##[warning]No files were found with the provided path: projects/platform-console/apps/console-web/playwright-report/
  ```

## Decision authority — Option B (placeholder `kafka` service)

Two real options were considered:

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A — bring up a real Kafka broker** | Add the GAP-style `apache/kafka:3.7.0` KRaft container the GAP own overlay uses (see [`projects/global-account-platform/docker-compose.e2e.yml:91`](../../../global-account-platform/docker-compose.e2e.yml)). | Trivially mirrors GAP overlay; future Kafka-needing e2e spec gets it for free. | Drops the platform-console overlay's explicit scope discipline ("mirrors ecommerce CI overlay scope discipline"); adds ~256 MB RAM + ~30–60 s boot to the 7-GB runner budget; the 2 currently-active e2e specs (and the foreseeable ones) do not exercise any Kafka producer/consumer path. |
| **B — `kafka` DNS placeholder** | Add a minimal `alpine:3.19` service named `kafka` whose only job is to occupy the `kafka` DNS name in the `pc-e2e` network so `getent hosts kafka` resolves to that container's IP. The container runs `sleep infinity` and serves no Kafka protocol. The existing `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999` env (already non-routable) is unchanged — the application-layer Kafka client still fails to connect, logging the expected warning, exactly as designed by the overlay author. | Honors the overlay's "no Kafka broker" scope discipline verbatim; ~5 MB image, ~0 s warm-up; surgical (single service block + zero env changes). | Requires a one-comment-block explanation; future Kafka-needing spec must promote to Option A (one-line `image:` swap). |

**Chosen — Option B.** Rationale:

1. The overlay's author explicitly chose the no-Kafka scope (comment block §22-31); promoting Option A would silently overturn that decision.
2. The surface failure is *DNS-resolution-only*; we should fix the DNS-resolution-only surface, not pull in a broker.
3. Option A is one trivial future-task away the moment a spec actually needs broker semantics — until then, paying broker boot cost on every nightly is dead weight.
4. The same comment block already documents the `127.0.0.1:9999` non-routable Kafka bootstrap pattern as the *application-layer* part of the discipline; Option B is the *DNS-layer* counterpart of the same discipline (both layers say "no real Kafka").

**Not chosen — Option C (modify GAP Dockerfile to make `kafka` check conditional)**: violates AC-3 / the PC-FE-019/021/022 "GAP source byte-unchanged" hard invariant + CLAUDE.md "Shared-vs-project boundary (strict, Hard-Stop-enforced)" — GAP's `apps/account-service/Dockerfile` is owned by the GAP project. The platform-console overlay must adapt to GAP source, not the other way around.

---

# Scope

## In Scope

- `projects/platform-console/docker-compose.e2e.yml` — add one `kafka` service block (Option B implementation: `image: alpine:3.19`, `command: ["sleep", "infinity"]`, `networks: [pc-e2e]`, `healthcheck` optional but recommended for `depends_on: condition: service_started` consistency). No changes to existing GAP service env, no changes to networks list, no changes to ports list.
- One updated comment block in the same file (under the existing "Excluded (deliberately …)" header) explaining the DNS-placeholder pattern so a future reader does not "fix" it by adding a real Kafka broker.
- This task md + INDEX move.

## Out of Scope

- Any change to `projects/global-account-platform/` source bytes (AC-3 hard invariant — verified via `git diff --stat origin/main -- projects/global-account-platform/` must be empty in impl PR).
- Any change to `apps/console-bff/` source bytes (AC-4 — ADR-MONO-017 D4 HARD INVARIANT).
- Any change to other producer projects' bytes (AC-5 — zero-retrofit, **18회째 if landed**: PC-FE-019/021/022's running count is `17회째`; this would make `18회째`).
- Any change to `console-integration-contract.md` or parity matrix (no operation/contract change; this is e2e infra only).
- Promoting the overlay to Option A — explicitly deferred until a spec actually needs Kafka broker semantics.
- Resolving the pre-existing `E2E full (gap docker-compose)` 3-night streak (2026-05-20/21/22) — that's a separate failure inside the GAP project's own overlay, unrelated to this overlay; tracked under a separate `TASK-MONO-` ticket (recommended sibling, see § Related to other surfaces).

---

# Acceptance Criteria

- [ ] **AC-1 (functional)** — Platform Console E2E full-stack nightly job (`platform-console-e2e-fullstack` in `.github/workflows/nightly-e2e.yml`) GREEN on the first nightly schedule firing after impl-PR merge. The first observable verification window is the next `0 18 * * *` UTC cron (= KST 03:00) after merge.
- [ ] **AC-2 (functional, local)** — `cd projects/platform-console && docker compose -f docker-compose.e2e.yml up -d --build` followed by `docker compose -f docker-compose.e2e.yml ps` shows `account-service` / `auth-service` / `admin-service` all `Healthy` within their `start_period+retries*interval` budgets (60 s start + 12*15 s = 4 min worst case). Verified by `docker compose -f docker-compose.e2e.yml logs account-service auth-service admin-service | grep -c 'waiting for DNS' < 10` (a handful of pre-startup ticks is fine; the loop must exit before health check budget).
- [ ] **AC-3 (hard invariant — GAP byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/` = empty for the impl PR. Verified inline in the impl PR description.
- [ ] **AC-4 (hard invariant — console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/src/` = empty.
- [ ] **AC-5 (hard invariant — zero-retrofit other producers)** — `git diff --stat origin/main -- 'projects/{wms,scm,finance,erp,fan,ecommerce}-platform/'` = empty.
- [ ] **AC-6 (hard invariant — scope discipline preserved)** — the impl PR description quotes the updated comment block verbatim showing that the "no Kafka broker" intent is preserved (image is `alpine:3.19`, not `apache/kafka:*`); reviewer can independently grep the file for `apache/kafka` and find zero matches under `projects/platform-console/`.
- [ ] **AC-7 (no contract / parity drift)** — `git diff --stat origin/main -- projects/platform-console/specs/contracts/ projects/platform-console/apps/console-web/src/lib/parity/` = empty. Parity matrix count unchanged at 18.
- [ ] **AC-8 (no workflow file change)** — `git diff --stat origin/main -- .github/workflows/` = empty (fix is entirely inside `docker-compose.e2e.yml`).
- [ ] **AC-9 (objective merge verification per CLAUDE.md BE-303 3-dim)** — the close-chore PR must be authored only after (a) `gh pr view <impl-PR-#> --json state,mergedAt,mergeCommit,statusCheckRollup` reports `state=MERGED` with `failing=0`; (b) `git log origin/main` tip equals the impl PR's `mergeCommit`; (c) pre-merge `gh pr checks <impl-PR-#>` snapshot showed `failing=0`. Document all three checks inline in the close-chore PR description.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`. Per [PROJECT.md](../../PROJECT.md), this project is `domain=saas`, `traits=[multi-tenant, integration-heavy, audit-heavy]`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md) — Phase 4-6 nightly regression guard rationale (no spec change needed).
- [`projects/platform-console/docker-compose.e2e.yml`](../../docker-compose.e2e.yml) — the file changed by this task; existing comment block (lines 22-31) documents the no-Kafka scope discipline that this task explicitly preserves.
- [`projects/global-account-platform/apps/account-service/Dockerfile`](../../../global-account-platform/apps/account-service/Dockerfile) — read-only reference (the BE-048 ENTRYPOINT this task adapts to); **must not** be modified.
- [`projects/global-account-platform/tasks/done/TASK-BE-048-fix-redis-in-dns-wait.md`](../../../global-account-platform/tasks/done/TASK-BE-048-fix-redis-in-dns-wait.md) — historical context for *why* the ENTRYPOINT pre-flight exists (JDK `InetAddress$CachedLookup` negative-cache hazard); this task does not undo BE-048's protection, it satisfies its precondition.

# Related Contracts

- None. This task does not touch any HTTP or event contract, parity matrix, or ADR.

# Related Skills

- None additional. CLAUDE.md standard workflow applies (spec PR → impl PR → close-chore PR with BE-303 3-dim verification).

---

# Edge Cases

- **`docker compose down -v` between runs** — the placeholder container is short-lived; recreation is idempotent.
- **`getent hosts kafka` returns the placeholder IP, then the application-layer Kafka producer connects to that IP on port 9092 / 9093** — does not happen because `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999` (the env var on `auth-service`/`admin-service`/`account-service` in the overlay) hard-codes the bootstrap to a non-routable port, ignoring the resolved hostname. The placeholder IP is never connected to.
- **Future spec needs real Kafka** — promote Option A: swap the `kafka` service block's `image: alpine:3.19` + `command: ["sleep", "infinity"]` for the GAP-overlay `apache/kafka:3.7.0` KRaft config, set `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` on all three GAP services. One file, ~30 lines.
- **Health check on the placeholder** — `alpine:3.19` has no curl/wget; the simplest healthcheck is `["CMD", "true"]` (effectively "always healthy") or omit it entirely (default behavior `condition: service_started` works without healthcheck). Either approach is fine; the choice is whichever the impl session finds cleaner.
- **`getent hosts kafka` on Docker embedded DNS** — Docker's embedded DNS resolves any service name in the same compose network, regardless of whether the service is running an application protocol. As long as the placeholder container is `created` or `running` (not `removed`), DNS resolves. `sleep infinity` keeps the container `running`.

# Failure Scenarios

- **Placeholder container exits unexpectedly** — `sleep infinity` cannot exit by design (it's PID 1, not killed by OOM under any normal workload). If it does (host-level memory pressure), the depends_on cascade would re-block; `restart: unless-stopped` is recommended.
- **DNS negative-cache hits before placeholder is up** — the BE-048 ENTRYPOINT loops on `getent` (no DNS cache within the shell), so transient NXDOMAIN does not persist. The `JAVA_TOOL_OPTIONS=-Dnetworkaddress.cache.ttl=0` env on each GAP service container (already set in overlay) ensures the JVM also does not cache negatives.
- **Image pull failure for `alpine:3.19` on the runner** — extremely unlikely (Docker Hub uptime). If it happens, GHA retries the step; same as any other image dependency. Could pre-pull in `:Package platform-console e2e boot jars (nightly)` job for resilience, but out-of-scope optimization.
- **`account-service` health check (`curl /actuator/health`) still fails after entrypoint exits** — distinct failure mode unrelated to this task (would surface as the next fix-task following the next nightly).

---

# Test Requirements

- **Local one-shot reproduction** before raising the impl PR: full bring-up sequence per overlay's own docstring (lines 38-42), expectation = all 8 containers `Healthy` within ~4 min.
- **Optional Playwright dry-run** locally: after bring-up, `pnpm --filter console-web exec playwright test` should at least *start* (whether the 2 specs pass depends on seed timing — that's a separate concern; the point here is the harness *boots*).
- **CI verification** = the next nightly firing. AC-1 documents the verification window.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + INDEX entry, no functional change), (2) impl PR (single-file edit to `docker-compose.e2e.yml`), (3) close-chore PR (`git mv ready/ → done/` + Status update + INDEX move, with BE-303 3-dim verification documented).
- [ ] AC-1 through AC-9 all checked off in the close-chore PR description.
- [ ] PC-FE-019 INDEX entry updated with one cross-ref line ("**first-cycle nightly RED closure** — TASK-PC-FE-023") so the honest-deferral chain is closed in writing.
- [ ] Memory `project_operator_overview_finance_card_resolution_complete.md` updated to mark the "first PC-FE-019 nightly cron UTC 18:00 = KST 03:00 대기" line as resolved (cross-ref to this task ID).

---

# 메타 (intended)

① **PC-FE-019's `메타 ⑤ first-cycle nightly result 미확인` closed by exactly the prior-art it referenced** (TASK-MONO-014 fix-task pattern). The honest deferral worked as designed — author flagged the unverified window, first cron surfaced the gap, fix-task closes it.

② **AC-3 hard invariant under stress** — the cheap reflex would be "just add `getent hosts kafka` guard to GAP Dockerfile". Resisted because GAP source must not byte-diff for platform-console-driven reasons (Shared-vs-project boundary, CLAUDE.md). The overlay-side fix is the right architectural seam.

③ **Scope-discipline preservation via DNS-only placeholder** — the cheap reflex #2 would be "bring up a real Kafka broker, mirror GAP overlay". Resisted because the overlay author explicitly documented the "no Kafka broker" decision and grounded it in resource budget + spec-coverage analysis. DNS-level placeholder honors both layers (DNS resolves, app still cannot connect — exactly the existing `127.0.0.1:9999` discipline).

④ **AC-5 18회째 zero-retrofit running count** — `Phase 2/4/5/6/7-skeleton/7-MVP/7-health/7-write-self-be/7-write-admin-be/7-write-admin-fe/7-list-extension/7-list-consumer/7-selfOpId/7-e2e-harness/7-pr-time-smoke/7-auth-formLogin/7-fixture-oidc-migration/this 7-e2e-dns-gap`.

---

# Related to other surfaces (not blocking this task)

- **`E2E full (gap docker-compose)` 3-night streak (2026-05-20/21/22)** is a separate failure inside the GAP project's own e2e overlay (different docker-compose file, different scope). Should be tracked as a sibling **`TASK-MONO-` or `TASK-BE-` task** in the GAP project's lifecycle; this task explicitly does not bundle it (different project, different surface, different scope discipline).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (docker-compose × multi-service DNS × scope-discipline preservation — Decision-Authority-bearing surface, not mechanical) / 리뷰=Opus 4.7.
