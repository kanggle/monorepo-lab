# TASK-BE-406 — Harden ecommerce-elasticsearch healthcheck/resources against recurring web-store full-stack e2e flake

**Status:** done

**Type:** TASK-BE (project-internal — `projects/ecommerce-microservices-platform/docker-compose.yml` only)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (single compose-config change; verify by booting ES locally + a nightly re-run)

---

## Goal

The `Nightly E2E` workflow's **Frontend E2E full-stack (web-store, Playwright + docker compose)** job
intermittently fails at the stack bring-up step with:

```
Start docker compose stack: dependency failed to start:
container ecommerce-elasticsearch is unhealthy
```

This is an **infrastructure flake, not a test/code failure** — the tests never run; the
`elasticsearch` container fails its healthcheck during startup so every service that `depends_on` it
(condition `service_healthy`) is aborted, taking the whole stack down. Observed twice in ~1.5h on
2026-06-18 (commits `d7dc2e7` and `8fbcc88`); each cleared on a no-change job re-run, confirming
intermittency. Goal: make ES bring-up resilient so the nightly stops needing manual re-runs.

## Root cause (config)

[`docker-compose.yml`](../../docker-compose.yml) `elasticsearch` service:

- `image: ecommerce-microservices-platform-elasticsearch:nori` — the nori (Korean analyzer) plugin
  lengthens cold start (JVM + plugin load + single-node bootstrap).
- `start_period: 30s` — too short for that cold start on a loaded CI runner; the first **real** health
  probes (which count against `retries`) begin before ES is ready and burn the retry budget.
- `memory: 1G` with `ES_JAVA_OPTS=-Xms512m -Xmx512m` — a 512m heap plus ~equal off-heap (Lucene mmap,
  plugin, JVM overhead) leaves no headroom in a 1G cgroup → possible OOM pressure → unhealthy.

Timing vs OOM could not be isolated post-hoc (the failed run only surfaces the "unhealthy" dependency
message; the ES container's own logs are not exported by the job). The fix therefore addresses **both**
hypotheses; it is conservative (widens tolerance only) and can be tightened later if shown excessive.

## Scope

**In scope** — `projects/ecommerce-microservices-platform/docker-compose.yml`, `elasticsearch` service only:

1. `healthcheck.start_period`: `30s` → `120s` (cold-start headroom; failures during this window do
   not count against retries).
2. `healthcheck.retries`: `10` → `15` (absorb post-start_period variance).
3. `deploy.resources.limits.memory`: `1G` → `2G` (remove OOM pressure for a 512m-heap ES).

**Out of scope:** the ES heap (`ES_JAVA_OPTS` unchanged — 512m is fine once the cgroup has headroom);
the healthcheck `test` command (correctly fails-closed when ES is down); any service code; the
`docker-compose.ci.yml` overlay; other containers' healthchecks.

## Acceptance Criteria

- **AC-1** — `elasticsearch` healthcheck is `start_period: 120s`, `retries: 15`; memory limit `2G`.
  No other service stanza changed (verify via diff).
- **AC-2 (local boot verify)** — `docker compose up -d elasticsearch` (from the ecommerce project)
  reaches `healthy` from a cold start (no warm volume); `docker inspect` shows `Health.Status=healthy`.
- **AC-3 (behavior verified, authoritative)** — the `Nightly E2E` web-store full-stack job goes GREEN
  on the merge commit (push-to-main). Because the flake is intermittent, a single GREEN is necessary
  but not sufficient proof of elimination; monitor the next few nightlies for recurrence (note in PR).
- **AC-4** — `docker compose config` parses (no YAML/schema error) for the full ecommerce compose.

## Related Specs

- None — pure test/CI infrastructure config. The web-store full-stack e2e (`apps/web-store/e2e/`)
  consumes the stack but is unchanged.

## Related Contracts

- None.

## Edge Cases

- **Warm volume masks cold-start cost locally** — AC-2 must boot ES with a fresh
  `elasticsearch-data` volume (`docker compose down -v` first) to reproduce the CI cold start; a warm
  local volume starts faster and would not exercise the widened `start_period`.
- **Runner with <2G free** — if the CI runner cannot grant 2G, ES still OOMs; if recurrence persists
  after this change, the next step is lowering the heap (`-Xms/-Xmx 384m`) rather than raising the
  limit further. Record which lever was pulled.
- **Genuinely-down ES** — the widened window must not mask a real failure: the healthcheck still
  fails-closed (curl error / `status:red`), so a truly broken ES still fails after 120s + 15×10s.

## Failure Scenarios

- **start_period too generous hides a slow-degradation** — 120s is a one-off cold-start budget, not a
  steady-state allowance; ES that is healthy at boot but degrades later is still caught by the
  ongoing interval probes. Acceptable.
- **Memory bump exceeds runner capacity** → ES OOM regardless (covered by the edge case above; fall
  back to heap reduction).
- **Re-run still flakes** → timing/OOM was not the (only) cause; escalate to capturing ES container
  logs in the nightly workflow (`docker compose logs elasticsearch` on failure) to diagnose precisely.
