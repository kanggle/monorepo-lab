# Task ID

TASK-MONO-201

# Title

fulfillment-demo: document the Option-B on-screen loop runbook now that the console `WMS 출고` menu exists (PC-FE-057 + BE-343)

# Status

done

# Owner

claude (Opus 4.8) — docs-only runbook extension. Monorepo-level (shared path `tests/fulfillment-demo/`).

# Task Tags

- onboarding

---

# Dependency Markers

- **선행 (DONE)**: TASK-BE-343 (#1191 `01ee3163`) — `GET /orders/{id}/picking-requests`; TASK-PC-FE-057 (#1193 `7a767a71`) — console `/wms/outbound` pick/pack/ship menu. With both merged, the on-screen operator leg of ADR-MONO-022 §D7 is real production code; this task documents how to run it live.
- **맥락**: TASK-MONO-200 (`tests/fulfillment-demo/`) shipped the Option-C forward-leg demo + an A/B "extension (not wired)" stub. This task replaces that stub with a concrete Option-B runbook reflecting the now-built console screen.

# Goal

Update `tests/fulfillment-demo/README.md` "Extending to SHIPPED" section into a concrete **Option-B** runbook: bring up IAM + wms gateway-service + inventory-service (seeded) + platform-console (+ web-store), then drive an ecommerce-originated outbound order pick → pack → ship from the console `WMS 출고` menu (`/wms/outbound`) so the ecommerce order reaches `SHIPPED` on screen. Documented (not executed) — a 43-container live run is a real host-OOM risk on this Windows host; the code is CI-gated, so correctness does not depend on the live run.

# Scope

## In Scope

- `tests/fulfillment-demo/README.md` — rewrite the "Extending to SHIPPED" section: note the console `/wms/outbound` menu (PC-FE-057) + the BE-343 picking-requests read now exist; list the Option-B additions (IAM stack, wms gateway-service, inventory-service + stock/location seed, platform-console, optional web-store, operator account with `wms` tenant + `OUTBOUND_WRITE`); give the operator on-screen loop (place order → console Pick/Pack/Ship → order SHIPPED → observe in web-store `/my/orders` + admin-dashboard). State it is documented, not run on this host (~43 containers, OOM risk).

## Out of Scope

- Actually running the 43-container stack (host risk; the code is CI-gated).
- Any code/compose change (the console screen + endpoint are already merged; the demo compose overlay stays Option-C/forward-leg).
- New IAM/inventory seed scripts (the runbook references the existing per-project seeds/compose; wiring them into one overlay is a future task if a live run is ever attempted).

# Acceptance Criteria

- [ ] `tests/fulfillment-demo/README.md` Option-B section reflects the real, merged console `/wms/outbound` menu + BE-343 endpoint, lists the Option-B stack additions, and gives the operator on-screen pick→pack→ship→SHIPPED loop with the observation points.
- [ ] Honest scope note: documented, not executed on this host (~43 containers / OOM risk); correctness is CI-gated.
- [ ] Docs-only; no code/compose change; internal links valid.

# Related Specs

- `docs/adr/ADR-MONO-022-*` §D7 (the fulfillment loop)
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5.1 (the console outbound surface)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` (the producer lifecycle)

# Related Contracts

- Cross-reference only (no contract change). The console + outbound contracts are already merged.

# Target Service

- Shared `tests/fulfillment-demo/` (no service code).

# Edge Cases

- Reader on a capable host who wants to actually run Option B → the runbook lists the stack additions + seeds needed, but flags it as non-trivial (per-project compose composition + IAM operator provisioning) — a future task if pursued.

# Failure Scenarios

- Runbook implies the menu is hypothetical → wrong; it is merged production code (PC-FE-057). The section must state the menu exists and where (`/wms/outbound`, nav `WMS 출고`).

# Definition of Done

- [ ] README Option-B section updated + honest "documented, not run" note
- [ ] Docs-only, links valid
- [ ] Ready for review
