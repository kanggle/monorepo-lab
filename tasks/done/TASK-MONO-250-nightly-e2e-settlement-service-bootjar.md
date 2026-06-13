# TASK-MONO-250 — nightly-e2e.yml: add missing settlement-service boot jar

Status: done
Type: chore (CI)
Scope: monorepo-level (`.github/workflows/`)
Analysis model: Opus 4.8 / Implementation model: Haiku (single-file CI omission)

## Goal

Restore the Nightly E2E **Frontend E2E full-stack (web-store)** job to GREEN by
adding the omitted `settlement-service` to the ecommerce boot-jar pipeline in
`.github/workflows/nightly-e2e.yml`, matching the already-correct `ci.yml`.

## Background / Root cause

The nightly **Frontend E2E full-stack** job fails at the "Start docker compose
stack" step:

```
target settlement-service: failed to solve:
lstat /apps/settlement-service/build/libs: no such file or directory
```

docker compose builds the `settlement-service` image via
`COPY apps/settlement-service/build/libs/*.jar app.jar`, but the jar is never
produced because `nightly-e2e.yml` processes only 11 of the 12 ecommerce
services — `settlement-service` is missing from three steps. `ci.yml`
(lines 501, 525) already includes it; this is workflow drift introduced when
settlement-service was added to the ecommerce stack. The omission only surfaces
in the nightly run (the full-stack job is gated to `kanggle/monorepo-lab`), so
it landed on main before being caught.

## Affected file / sites

`.github/workflows/nightly-e2e.yml`:

1. **Build ecommerce boot jars** step (~L97-107) — add
   `:projects:ecommerce-microservices-platform:apps:settlement-service:bootJar`
   in alphabetical position (after `search-service`, before `shipping-service`).
2. **Upload ecommerce boot jars** step (~L118-129) — add the
   `.../settlement-service/build/libs/settlement-service.jar` path.
3. **Restore ecommerce boot jar paths** step (~L195-197) — add
   `settlement-service` to the `services=(...)` bash array.

## Acceptance Criteria

- AC-1: `nightly-e2e.yml` builds, uploads, and restores `settlement-service`
  exactly as the other 11 ecommerce services (parity with `ci.yml`).
- AC-2: The three ecommerce service lists in `nightly-e2e.yml` (build / upload /
  restore) each contain all 12 services.
- AC-3: After merge, the next Nightly E2E "Frontend E2E full-stack (web-store)"
  job reaches the Playwright run step (no docker-compose build failure on the
  settlement-service target).

## Related Specs

- N/A (CI configuration parity fix; no spec/contract change).

## Related Contracts

- N/A.

## Edge Cases

- alphabetical insertion only — no reordering of existing lines.
- restore step is a bash array (space/`\`-continued), distinct syntax from the
  gradle/upload multiline lists; settlement-service must be added in array form.

## Failure Scenarios

- If settlement-service is added to build/upload but NOT restore → artifact
  contains the jar but the canonical `apps/.../build/libs/` path is missing →
  same docker-compose `lstat` failure. All three sites must change together.
