# TASK-MONO-251 — nightly-e2e.yml: add missing SETTLEMENT_DB_PASSWORD to .env

Status: done
Type: chore (CI)
Scope: monorepo-level (`.github/workflows/`)
Analysis model: Opus 4.8 / Implementation model: Haiku (single-line CI omission)

## Goal

Restore the Nightly E2E **Frontend E2E full-stack (web-store)** job to GREEN by
adding the omitted `SETTLEMENT_DB_PASSWORD` to the docker-compose `.env`
generated in `.github/workflows/nightly-e2e.yml`.

## Background / Root cause

Follow-up to TASK-MONO-250. That task fixed the missing `settlement-service`
boot jar, after which the docker-compose build of `settlement-service`
succeeded — exposing the **next** drift from the same settlement-service
addition: the `.env` creation step never set `SETTLEMENT_DB_PASSWORD`.

Nightly run 27460883968 (headSha bf2db06cc) failed:

```
The "SETTLEMENT_DB_PASSWORD" variable is not set. Defaulting to a blank string.
Container ecommerce-settlement-postgres  Error
dependency failed to start: container ecommerce-settlement-postgres is unhealthy
```

`docker-compose.yml` references `${SETTLEMENT_DB_PASSWORD}` for both
`settlement-postgres` (`POSTGRES_PASSWORD`, L594) and `settlement-service`
(`DB_PASSWORD`, L1038). With a blank value the postgres image refuses to
initialize → unhealthy → `settlement-service` (depends_on healthy) → gateway
never starts → job dies before Playwright.

Verified `SETTLEMENT_DB_PASSWORD` is the **only** no-default `${VAR}` in
`docker-compose.yml` absent from the `.env` block (other 13 all present).

## Affected file / site

`.github/workflows/nightly-e2e.yml`, "Create .env for docker compose" step —
add `SETTLEMENT_DB_PASSWORD=ci_settlement_pass` alongside the other
`*_DB_PASSWORD` synthetic CI values (after `NOTIFICATION_DB_PASSWORD`).

## Acceptance Criteria

- AC-1: The `.env` written by the nightly workflow includes
  `SETTLEMENT_DB_PASSWORD` with a non-blank synthetic value.
- AC-2: Every no-default `${VAR}` referenced in `docker-compose.yml` has a
  corresponding line in the `.env` block.
- AC-3: After merge, the next Nightly E2E "Frontend E2E full-stack (web-store)"
  job starts `settlement-postgres` healthy and reaches the Playwright run step
  (no `dependency failed to start` on settlement-postgres).

## Related Specs

- N/A (CI configuration parity fix; no spec/contract change).

## Related Contracts

- N/A.

## Edge Cases

- Synthetic value only — no real secret. Tests do not assert on the settlement
  DB password; postgres just needs a non-blank `POSTGRES_PASSWORD` to init.

## Failure Scenarios

- If a future ecommerce service adds another `${X_DB_PASSWORD}` to compose, the
  same drift recurs. AC-2 documents the invariant (compose no-default vars ⊆
  .env) for future maintainers to re-check.
