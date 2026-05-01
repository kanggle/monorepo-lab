# TASK-BE-040 - Fix issues found in TASK-BE-038

## Goal

Resolve review findings against TASK-BE-038 (outbound-service PickingRequest /
PackingUnit / Shipment domain + REST + TMS post-commit wiring). Two critical
issues, three warnings, and minor suggestions need follow-up.

## Scope

Target service: outbound-service (projects/wms-platform/apps/outbound-service/)

In scope (Critical):

1. Contract-compliant packing.completed emission via PATCH packing-units seal flow.
   The HTTP contract outbound-service-api.md section 3.2 requires the PATCH
   seal endpoint to emit outbound.packing.completed and transition the Order
   to PACKED when seal=true AND all units are SEALED AND sum of
   PackingUnitLine.qty equals every order line qty_ordered. Current
   implementation only emits the event from a non-contract POST orders
   packing/confirm endpoint. Make the seal path the canonical trigger; the
   extra packing/confirm endpoint may stay as a fallback but contract
   clients must succeed through the seal path alone.

2. Fix transactional self-invocation in ShipmentNotificationListener.
   markNotified and markFailed are called from within the same bean
   (onShipmentNotifyTrigger calls them), so the Spring AOP proxy is
   bypassed and the REQUIRES_NEW annotation has no effect. Either
   extract the two helpers into a separate component so the transactional
   proxy applies, OR self-inject via lazy proxy, OR wrap the work in
   TransactionTemplate.execute inside the listener method.

In scope (Warnings):

3. Remove direct PickingPersistencePort and PackingPersistencePort calls
   from controllers. Move the lookup into a dedicated read use case
   (LookupPickingRequestUseCase, LookupPackingUnitUseCase) or restructure
   the use-case command to accept the picking-request-id directly.

4. Strip extra pickingRequestId field from outbound.picking.requested and
   outbound.picking.cancelled event payloads in EventEnvelopeSerializer.
   The contract sections 3 and 4 only declare reservationId for these
   events; the extra field violates the field-name match rule.

5. Document or align Shipment.shipmentNo format. Task AC-07 said
   SHIP-YYYYMMDD-seq, the contract example says SHP-20260429-0001,
   current implementation generates SHIP-YYYYMMDD-8hexUpper. Align on one
   format and update the contract or the implementation accordingly.

In scope (Suggestion):

6. Add createdBy field to ShipmentResponse. Contract section 4.1 example
   includes it.

Out of scope:

- Saga sweeper implementation (separate task)
- TMS retry endpoint POST shipments retry-tms-notify (separate task)
- Persistence-adapter integration tests (Testcontainers)
- Webhook contract tests beyond unit-level

## Acceptance Criteria

AC-01 PATCH packing-units id with seal=true, when sealing the last open
unit for the order AND sum of PackingUnitLine.qty per order_line_id
equals every order_line.qty_ordered, atomically transitions Order PACKING
to PACKED via Order.completePacking, transitions Saga PICKING_CONFIRMED
to PACKING_CONFIRMED via OutboundSaga.onPackingConfirmed, writes
outbound.packing.completed outbox row in the same TX, and returns 200
with the sealed PackingUnitResponse.

AC-02 When PATCH packing-units id seals a non-final unit (other units
still OPEN or quantities not yet covering all order lines), the endpoint
succeeds with 200 and does NOT emit outbound.packing.completed nor
transition the order. Existing confirm endpoint may still be available
as a fallback but it is no longer the canonical path.

AC-03 ShipmentNotificationListener.markNotified and markFailed run in
their own database transactions even though the originating
TransactionalEventListener AFTER_COMMIT has no active TX. Implementation
MUST NOT rely on self-invoked Transactional annotations. A unit test
exercises the failure path with a fake TMS that throws and asserts the
saga state and Shipment.tms_status are persisted.

AC-04 PickingController and PackingController no longer inject
PickingPersistencePort or PackingPersistencePort. Lookups happen in the
application service or via a dedicated read use-case port.

AC-05 Outbound events outbound.picking.requested and
outbound.picking.cancelled carry only the fields declared in
outbound-events.md sections 3 and 4. The duplicate pickingRequestId
field is removed.

AC-06 Shipment.shipmentNo format is documented in domain-model.md
section 5, the implementation matches the documented format, and the
contract example is consistent. If sequential, a Flyway migration adds
the shipment_no_seq sequence.

AC-07 ShipmentResponse contains createdBy populated from
Shipment.created_by, or the contract example is updated to remove the
field.

AC-08 All existing unit tests in
projects/wms-platform/apps/outbound-service continue to pass; the new
seal-completes-packing path has direct unit-test coverage; the
post-commit listener path has a unit-test variant that does not rely on
self-invocation proxying.

## Related Specs

- projects/wms-platform/specs/services/outbound-service/architecture.md
- projects/wms-platform/specs/services/outbound-service/domain-model.md
- projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md
- projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md
- projects/wms-platform/specs/services/outbound-service/external-integrations.md
- projects/wms-platform/specs/services/outbound-service/workflows/outbound-flow.md

## Related Contracts

- projects/wms-platform/specs/contracts/http/outbound-service-api.md (sections 3.2 and 4.1)
- projects/wms-platform/specs/contracts/events/outbound-events.md (sections 3, 4, 6)

## Edge Cases

- Race between two PATCH seal calls on different units that both
  complete the order: the second commit must observe Order or Saga at
  the new version and react with the saga-state guard so there is no
  double outbound.packing.completed emission.
- Seal of the only unit but its lines do not cover all order lines:
  unit transitions to SEALED but no order or saga transition; client
  must add another unit before the order can be marked PACKED.
- TMS failure during AFTER_COMMIT in a clustered environment: the
  listener TX must commit Shipment.tms_status=NOTIFY_FAILED and Saga
  to SHIPPED_NOT_NOTIFIED even if the original ConfirmShipping pod
  crashes mid-listener (saga sweeper covers the saga side).

## Failure Scenarios

- PATCH seal succeeds but outbox write fails: the entire seal TX rolls
  back; the unit stays OPEN, order stays PACKING, saga unchanged.
  Client retries with the same Idempotency-Key.
- Listener helper-method TX fails (DB unavailable): the
  TransactionalEventListener swallows the exception by default;
  without an outer TX context, the original ConfirmShipping commit has
  already succeeded and stock consumption is in flight. The saga
  sweeper re-emit covers the stuck-saga case; manual TMS retry
  endpoint covers the TMS side once the listener is fixed.

## References

- TASK-BE-038 (original): projects/wms-platform/tasks/done/TASK-BE-038-outbound-pick-pack-ship-domain.md
- Review checklist: .claude/skills/review-checklist/SKILL.md
- Hexagonal layer rules: .claude/skills/backend/architecture/hexagonal/SKILL.md
