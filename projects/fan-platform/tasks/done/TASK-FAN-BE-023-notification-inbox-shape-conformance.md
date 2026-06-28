---
id: TASK-FAN-BE-023
title: fan notification-service inbox §1 shape conformance (ADR-MONO-043 P2)
status: done
project: fan-platform
service: notification-service
type: backend (conformance, net-zero additive)
created: 2026-06-28
---

# TASK-FAN-BE-023 — fan notification inbox §1 shape conformance (ADR-MONO-043 P2)

## Goal

P2/fan conformance increment of ADR-MONO-043 (ADR ACCEPTED; P1a contract + P1b `libs/java-notification` merged; P2/erp = TASK-ERP-BE-027). Conform the fan notification-service inbox response to the **§1 canonical envelope** of `platform/contracts/notification-inbox-contract.md` so the **P3 console-bff aggregator** can fan it in with per-domain attribution.

**Scope = the REST §1 shape only** (Axis 1, net-zero additive) — same shape-level conformance as erp. The deeper lib-internal engine adoption is NOT pursued (fan, like erp, has its own delivery/dedupe semantics; see the P2 finding in `project_adr043_notification_unification` memory + the ERP-BE-027 deferral — the wms-derived lib engine is not net-zero across domains without seam additions).

## Scope

`projects/fan-platform/apps/notification-service/`:

- `presentation/dto/NotificationResponse.java` — additive contract §1 fields:
  - `sourceDomain` = constant `"fan"` (always; the aggregator's attribution key).
  - `deepLink` = nullable (`null` for now; Jackson NON_NULL omits it).
  - Pre-existing `status` (UNREAD/READ) + `membershipId` preserved (contract §1.2 non-normative domain extensions); the normative read signal stays the `read` boolean.
- `presentation/controller/NotificationInboxController.java` — add the **normative `unread` boolean query param** (contract §2.1); it maps onto fan's `NotificationStatus` enum (`unread=true→UNREAD`, `false→READ`). The pre-existing `status=UNREAD|READ` param is kept for backward compatibility and used only when `unread` is absent.
- `presentation/controller/NotificationInboxControllerSliceTest.java` — assert `sourceDomain="fan"`, `deepLink` absent, and the `unread` alias → UNREAD filter mapping.

list + mark-read both carry `sourceDomain` (shared `NotificationResponse.from(...)`). fan has no single-item `GET /{id}` — contract §2 makes that verb optional, so no change there.

## Out of Scope

- Lib delivery/dedupe engine adoption (fan has its own channels/dedupe; not net-zero — same finding as erp ERP-BE-027).
- No producer change; no other fan service.

## Acceptance Criteria

- [x] `NotificationResponse` carries `sourceDomain="fan"` (never null) + nullable `deepLink` (NON_NULL omit); `status`/`membershipId` preserved.
- [x] `unread` boolean query param added (normative §2.1), mapping onto the status enum; `status` param still works when `unread` absent.
- [x] list + mark-read emit `sourceDomain`.
- [x] Net-zero for the CI `Integration (fan-platform, Testcontainers)` lane (additive JSON field + additive query param; existing `status` behavior unchanged).
- [x] `:notification-service:test` BUILD SUCCESSFUL (Docker-free); slice test asserts the new fields + the `unread` mapping.

## Related Specs

- [ADR-MONO-043](../../../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — ACCEPTED; P2/fan shape-conformance increment.
- [platform/contracts/notification-inbox-contract.md](../../../../platform/contracts/notification-inbox-contract.md) — §1 shape + §2.1 `unread` alias.
- TASK-ERP-BE-027 — the sibling P2/erp shape conformance (same pattern + the engine-adoption deferral finding).

## Edge Cases / Failure Scenarios

- **Both `unread` and `status` provided** → `unread` (the normative param) takes precedence; documented.
- **Existing `status=` clients** → unaffected (param retained; behavior byte-identical when `unread` absent).
- **Aggregator can't attribute fan items** → resolved: `sourceDomain="fan"` always present.

## Definition of Done

- [x] Additive §1 shape + `unread` alias + slice-test assertions.
- [x] `:test` GREEN.
- [ ] commit + push (branch `task/fan-be-023-notification-conformance-p2`) + PR + CI `Integration (fan-platform)` GREEN + merge (3-dim verify).
- [ ] Remaining P2: ecommerce shape conformance + wms inbox-vs-delivery decision; then P3 aggregator.
