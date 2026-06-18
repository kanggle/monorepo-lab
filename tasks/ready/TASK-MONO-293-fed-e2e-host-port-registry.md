# TASK-MONO-293 — federation-hardening-e2e host port registry

**Status:** ready

**Type:** TASK-MONO (monorepo-level — root-scoped shared test infra, ADR-MONO-018 D1)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (doc-only registry, no behavior change)

---

## Goal

Establish a single committed source of truth for the **host-published** ports of the
`federation-hardening-e2e` docker stack so independently-authored demo overlays stop colliding
on host ports. The trigger: `ledger-service` and `erp-read-model-service` both grabbed host port
`18097` independently, caught only reactively (`erp-read-model` moved to `18197`). Document the
full allocation + a forward allocation rule; do **not** renumber working ports.

## Scope

**In scope:**
1. New doc `tests/federation-hardening-e2e/docker/HOST-PORTS.md` — frozen inventory of every
   host port across the base file + all overlays, split into reserved-functional (`8081`/`3000`/
   `3001`/`8080`), infra/datastore, and app-service (debug-only) bands; plus a "pick next-free +
   add a row" allocation rule.
2. Pointer to the registry from `tests/federation-hardening-e2e/README.md`.

**Out of scope:**
- Renumbering any existing host port (rejected — debug ports are functionally arbitrary; renumber
  ripples into throwaway verify scripts for zero runtime gain).
- Editing the uncommitted overlay files (`.ecommerce`, `.ecommerce-extra`, `.erp-fullstack`,
  `.ledger`, `.replenishment`) — they remain commit-forbidden local demo scaffolding; the registry
  documents their allocations without committing the files.
- Any change to container ports or service-to-service wiring (internal traffic uses container
  names + container ports, unaffected by host bindings).

## Acceptance Criteria

- **AC-1** — `HOST-PORTS.md` lists every host port currently published by `base + .demo + .ledger
  + .erp-fullstack + .replenishment` with service name, container port, and source file; the union
  is asserted duplicate-free.
- **AC-2** — The three functional ports (`8081` issuer / `3000` console-web / `3001` web-store) and
  the `8080` socat proxy are marked NEVER-reassign with rationale.
- **AC-3** — A forward allocation rule states the next-free app band (`18101+`) and datastore band
  (`15437+`) and requires a registry row in the same change that introduces a new port.
- **AC-4** — README links the registry. No compose file or container behavior changes (doc-only;
  `git diff --stat` touches only `HOST-PORTS.md` + `README.md` + this task file).

## Related Specs

- `docs/adr/ADR-MONO-018-*` (federation hardening e2e — root-scoped cross-product cohort, D1).
- `tests/federation-hardening-e2e/README.md` (stack bring-up doc — gains the registry pointer).

## Related Contracts

- None (doc-only; no API/event surface).

## Edge Cases

- **Uncommitted overlay drift** — an overlay file can be reset/lost (the erp-fullstack defs were
  lost once). The committed registry survives and tells the next author which ports are taken.
- **Host-side collision outside the stack** — `8081`/`3000` can still clash with another host
  process or a per-project `docker-compose.yml` run simultaneously; the registry notes the fed-e2e
  stack is the single intended vehicle and per-project composes must not run alongside it.

## Failure Scenarios

- **F1 — registry goes stale** — a future overlay adds a port without a row, recreating the
  silent-collision risk. Mitigation: AC-3's "add a row in the same change" rule + the README pointer
  making the registry discoverable.
- **F2 — someone renumbers existing ports** citing "consistency" — breaks verify scripts hard-coding
  e.g. `localhost:18085`. The doc's "Why no renumbering" section records the decision to prevent this.
