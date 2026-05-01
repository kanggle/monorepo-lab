# TASK-BE-041 - Fix issue found in TASK-BE-040

## Goal

Resolve the single Warning-level finding from the review of TASK-BE-040:
the `Shipment.shipment_no` format is not yet documented in
`specs/services/outbound-service/domain-model.md` §5 even though
TASK-BE-040 AC-06 explicitly required it. Implementation
(`SHP-YYYYMMDD-NNNN`) and the contract example
(`SHP-20260429-0001` in `outbound-service-api.md` §4.1) are already
consistent; the missing piece is the spec-side documentation.

## Scope

Target service: outbound-service (projects/wms-platform/apps/outbound-service/)
This task is **specs-only** — no Java code changes are expected.

In scope (Warning):

1. Update `specs/services/outbound-service/domain-model.md` §5 (Shipment)
   so the `shipment_no` row in the Fields table and/or the Invariants list
   declares the explicit format `SHP-YYYYMMDD-NNNN` (where `NNNN` is a
   4-digit numeric suffix), mirroring the way `Order.order_no` already
   documents `ORD-{YYYYMMDD}-{seq}` in §1. Cross-reference
   `outbound-service-api.md` §4.1 example for consistency.
2. Note the v1 generation strategy (random 4-digit suffix backed by the
   `idx_shipment_shipment_no` unique index, with retry-on-collision
   semantics) so future maintainers know the implementation choice.

Out of scope:

- Switching to a sequence-backed `shipment_no_seq` (TASK-BE-040 AC-06
  already accepted the random-suffix variant; sequence migration is its
  own task if the format ever needs to be lexicographically ordered).
- Any change to `apps/outbound-service/` Java code or Flyway migrations.
- Contract example updates — `outbound-service-api.md` §4.1 already
  matches the implementation.

## Acceptance Criteria

AC-01 `specs/services/outbound-service/domain-model.md` §5 documents
the `Shipment.shipment_no` format as `SHP-YYYYMMDD-NNNN` (or
equivalent unambiguous prose), with a short note describing how
`NNNN` is generated and how uniqueness is enforced.

AC-02 The format documented in domain-model.md §5 matches both the
contract example in `outbound-service-api.md` §4.1
(`SHP-20260429-0001`) and the implementation in
`ConfirmShippingService.generateShipmentNo` (`SHP-YYYYMMDD-NNNN`).

AC-03 No Java code or Flyway migration is modified by this task.

## Related Specs

- projects/wms-platform/specs/services/outbound-service/domain-model.md (§5 — Shipment)
- projects/wms-platform/specs/services/outbound-service/architecture.md
- projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md
- projects/wms-platform/specs/services/outbound-service/workflows/outbound-flow.md

## Related Contracts

- projects/wms-platform/specs/contracts/http/outbound-service-api.md (§4.1 — example uses `SHP-20260429-0001`)
- projects/wms-platform/specs/contracts/events/outbound-events.md (§7 — `outbound.shipping.confirmed` `shipmentNo` field)

## Edge Cases

- Random-suffix collision: domain-model.md §5 must note that on a
  duplicate `idx_shipment_shipment_no` violation, the use case retries
  with the same `Idempotency-Key` (already implemented in
  `ConfirmShippingService`).
- Format width: `String (40)` column accepts the full
  `SHP-YYYYMMDD-NNNN` shape (16 chars) with headroom for any future
  prefix change.

## Failure Scenarios

- None — this is a documentation-only change. If a reviewer disagrees
  with documenting the format as `SHP-YYYYMMDD-NNNN`, the alternative
  (`SHP-YYYYMMDD-{seq}` backed by a sequence) requires a separate
  task with a Flyway migration; that is out of scope here.

## References

- Original task: projects/wms-platform/tasks/done/TASK-BE-040-fix-TASK-BE-038.md (AC-06)
- Source: TASK-BE-040 review verdict 2026-04-29 (1 Warning: spec format documentation gap)
- Implementation reference: `ConfirmShippingService.generateShipmentNo` in
  `projects/wms-platform/apps/outbound-service/src/main/java/com/wms/outbound/application/service/ConfirmShippingService.java`
- Sibling pattern: `Order.order_no` is documented as `ORD-{YYYYMMDD}-{seq}` in `domain-model.md` §1

