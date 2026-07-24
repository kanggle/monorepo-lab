# TASK-BE-560 — retire the outbound-service TMS side-channel (ADR-053 §D8 wms half)

**Status:** done
**Type:** TASK-BE
**Depends on / 전제:** [TASK-PC-FE-258](../../../platform-console/tasks/done/TASK-PC-FE-258-repoint-outbound-retry-to-logistics.md) **done** (the platform-console operator action no longer calls the wms `:retry-tms-notify` endpoint — it now drives logistics `:retry`) · [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** §D8 · [ADR-MONO-052](../../../../docs/adr/ADR-MONO-052-transport-context-map.md) §D7 (custody boundary — transport left wms).
**후속 / blocks:** none — this **closes ADR-053 §D8** and the Phase-1 D8 line. (3PL Phase 2 / tracking Phase 3 remain, gated on ADR-052 §D8-3.)

> **The wms half of D8, now a clean wms-internal removal.** Carrier dispatch moved to `logistics-service` (live since BE-044); the console operator recovery action was repointed to logistics `:retry` (PC-FE-258, done). Nothing external now calls the wms TMS path — so the whole outbound-service **TMS side-channel** (after-commit push + `:retry-tms-notify` + adapter + `SHIPPED_NOT_NOTIFIED` alert state + dedupe table + `tms_status`) can be retired. **Critical framing: TMS was never on the saga's completion critical path** — the saga completes `SHIPPED → COMPLETED` via `inventory.confirmed` (saga-status.md line "TMS may still be pending — independent side-channel"). This removes a **parallel alert branch**, not the main flow, so the ↔inventory choreography is untouched.

---

## Goal

Remove the outbound-service TMS notification side-channel in full — the after-commit
`ShipmentNotificationListener` push, the `:retry-tms-notify` REST recovery, the entire
`adapter/out/tms/*` subsystem, the `SHIPPED_NOT_NOTIFIED` saga alert state + its two
transitions, the `tms_request_dedupe` table, and `Shipment.tms_status` — plus the ~11
spec sections that describe them. The main saga path (`PACKED → SHIPPED → COMPLETED`
via `inventory.confirmed`, and all cancellation/reserve/sweeper paths) is **unchanged**.
Realises **ADR-MONO-053 §D8** and closes the D8 line.

## Scope (all under `projects/wms-platform/apps/outbound-service/` + wms specs, unless noted)

**Grep-consumers discipline (refactoring-policy.md §Rules #6):** each removal below MUST be
preceded by a repo-wide grep of the symbol; a survivor reference is a defect the compiler
may not catch (e.g. a spec sentence, a metric name, a config key). Delete the survivors too.

1. **After-commit TMS push (application/service):** delete `ShipmentNotificationListener`, `ShipmentNotifyTrigger`, `ShipmentNotificationPersistence`, `RetryTmsNotificationService`, `RetryTmsPersistenceHelper`. Remove wherever `ConfirmShippingService` (or its after-commit hook) fires the notify trigger — `ConfirmShippingService` keeps marking `SHIPPED` + publishing `outbound.shipping.confirmed`; it just no longer schedules a TMS push.
2. **Ports/results/commands:** delete `application/port/out/ShipmentNotificationPort`, `TmsAcknowledgement`, `TmsRequestDedupePort`; `application/port/in/RetryTmsNotificationUseCase`; `application/command/RetryTmsNotificationCommand`; `application/result/RetryTmsNotificationResult`.
3. **Adapter subsystem (adapter/out/tms/):** delete the whole package — `TmsClientAdapter`, `StubTmsClientAdapter`, `TmsClientConfig`, `TmsClientProperties`, `TmsShipmentMapper`, `TmsShipmentRequest`, `TmsShipmentResponse`, `TmsPermanentException`, `TmsTransientException`, `TmsMetrics`, and `persistence/{TmsRequestDedupeEntity, TmsRequestDedupeJpaRepository, TmsRequestDedupeRepositoryImpl}`.
4. **REST surface:** remove the `POST /shipments/{id}:retry-tms-notify` method from `ShipmentController` + `adapter/in/web/dto/response/RetryTmsNotificationResponse`. (Confirm no other console/gateway route references it — PC-FE-258 already removed the console side; verify `gateway-public-routes`/wms gateway config too.)
5. **Domain:** remove `SagaStatus.SHIPPED_NOT_NOTIFIED` and its `OutboundSaga` transitions (`SHIPPED → SHIPPED_NOT_NOTIFIED`, `SHIPPED_NOT_NOTIFIED → COMPLETED`) + any `apply()`/already-applied cases referencing it; delete `domain/model/TmsStatus` + `domain/exception/TmsRetryNotAllowedException`; remove `Shipment.tms_status` (field + entity column mapping).
6. **Flyway (new versioned migration, do NOT edit existing):** `UPDATE outbound_saga SET state='SHIPPED' WHERE state='SHIPPED_NOT_NOTIFIED'` (rejoin the inventory-confirmation path — the sweeper's `SHIPPED` re-emit + inventory idempotent re-confirm complete them; likely **0 rows** in demo since the stub always acked, but the migration must be correct regardless); `DROP TABLE tms_request_dedupe`; `ALTER TABLE shipment DROP COLUMN tms_status` (+ any tms-only `failure_reason`/columns — verify they are not shared with a non-TMS use before dropping).
7. **Sweeper:** remove the `SHIPPED_NOT_NOTIFIED` note from `SagaSweeper` / its spec (it already does not re-emit for it — just delete the dead reference).
8. **Config:** remove the TMS `RestClient` / Resilience4j / properties block from `application.yml` (+ any `application-standalone.yml` TMS keys); remove `tms.*` env fallbacks.
9. **Metrics:** remove `outbound.saga.failed.count{reason=tms_notify_failed}` and any `tms_*` meters (`TmsMetrics`) — and the `reason=tms_notify_failed` label from the saga-failed counter (keep `reason=reserve_failed`).
10. **Specs (~11) — update to the post-D8 reality:** `state-machines/saga-status.md` (drop the `SHIPPED_NOT_NOTIFIED` state row, the two transitions, the diagram/mermaid arms, the sweeper note, the already-applied/impossible rows, the test-requirement bullets), `domain-model.md` §6 (OutboundSaga states + Shipment.tms_status), `architecture.md` (TMS integration, failure-handling, metrics, dedupe-table lines ~464/469/471/501/514/575/577), `database-design.md` (tms_status FSM ~403, tms_request_dedupe table, the `SHIPPED_NOT_NOTIFIED` terminal-list line ~439), `external-integrations.md` §2 TMS (remove the whole TMS vendor section — or reframe to a one-line "transport dispatch relocated to scm logistics-service, ADR-053 §D8"), `contracts/http/outbound-service-api.md` (remove `:retry-tms-notify`), `contracts/events/outbound-events.md` (the `shipping.confirmed` `tms_status=PENDING` side-effect note), `state-machines/order-status.md` (if it references the tms branch), `idempotency.md` §4 (drop the TMS dedupe layer if listed), `workflows/outbound-flow.md` (the TMS step in the narrative), `sagas/outbound-saga.md` (the TMS failure/recovery section), and **delete** `contracts/http/tms-shipment-api.md` (the vendor wire spec — now scm's concern). Fix all resulting dead-refs/anchors.
11. **Error codes:** remove any wms TMS-only codes from `rules/domains/wms.md` + `platform/error-handling.md` (e.g. a `TMS_*` / retry-not-allowed code) **only if** they are TMS-exclusive and now emitter-zero — verify with an emitter grep first (error-code registry guard: emitted ⊆ registry).
12. **Tests:** delete `integration/tms/TmsClientAdapterIT`, `application/service/ShipmentNotificationListenerTest`, any `RetryTmsNotification*Test` / retry-tms slice tests; **update** `OutboundSagaTest` (remove the `SHIPPED → SHIPPED_NOT_NOTIFIED` / recovery cases) and `ShipmentControllerSliceTest` (remove the `:retry-tms-notify` case) and the saga application-service test (remove the TMS-exhaustion / recovery bullets). The main-path saga tests stay green unchanged.

**Out of scope:**
- **No scm / logistics change** — logistics already owns dispatch (BE-042/043/044). No console change (PC-FE-258 already repointed it).
- The main saga completion path (`SHIPPED → COMPLETED` via `inventory.confirmed`), reserve/cancel/sweeper paths — **untouched**.
- 3PL (Phase 2, ADR-052 §D8-3) / tracking (Phase 3).

## Acceptance Criteria

- [ ] `./gradlew :projects:wms-platform:apps:outbound-service:build` succeeds; no `adapter/out/tms/*`, no `ShipmentNotificationListener`, no `RetryTmsNotification*`, no `TmsStatus`/`ShipmentNotificationPort` remain (`grep -rn "tms\|Tms\|SHIPPED_NOT_NOTIFIED\|retry-tms-notify" apps/outbound-service/src` returns only intentional history/none).
- [ ] `SagaStatus` no longer has `SHIPPED_NOT_NOTIFIED`; `OutboundSaga` has no transition to/from it; the main path `PACKED → SHIPPED → COMPLETED` (via `inventory.confirmed`) + reserve/cancel/sweeper paths are byte-behaviour-unchanged (existing main-path saga unit tests pass **unmodified**).
- [ ] New Flyway migration applies on a clean Testcontainers PostgreSQL with `ddl-auto=validate` green: `SHIPPED_NOT_NOTIFIED`→`SHIPPED` data migration, `DROP TABLE tms_request_dedupe`, `DROP COLUMN shipment.tms_status`. Existing migrations' checksums unchanged.
- [ ] `POST /shipments/{id}:retry-tms-notify` returns 404/no-route (endpoint removed); nothing in the repo calls it (`grep -rn "retry-tms-notify"` across all projects = 0).
- [ ] The ~11 spec sections updated + `tms-shipment-api.md` deleted; **no dead links/anchors** (dead-ref check green); error-code registry guard green (any removed code is emitter-zero).
- [ ] wms CI lanes GREEN — Build & Test + Integration (`master-service + notification-service + outbound-service`, Testcontainers) + wms E2E + Package boot jars + Service-type/Gateway/INDEX drift + error-code registries. CI Linux is authority (Windows local cannot run Testcontainers; host may be memory-constrained — read the junit XML).

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md` — **the primary edit** (state machine minus the TMS branch)
- `.../outbound-service/{architecture.md, domain-model.md, database-design.md, external-integrations.md, idempotency.md, workflows/outbound-flow.md, sagas/outbound-saga.md}` — TMS references to remove
- `.../specs/contracts/http/{outbound-service-api.md (remove :retry-tms-notify), tms-shipment-api.md (delete)}`, `.../contracts/events/outbound-events.md` (tms_status side-effect note)
- `docs/adr/ADR-MONO-053-...` §D8 / `ADR-MONO-052-...` §D7 — the authorising decisions

## Related Contracts

- `projects/scm-platform/specs/contracts/events/logistics-dispatch-subscriptions.md` — the seam logistics consumes (read-only; the reason the wms TMS push is redundant)
- `rules/domains/wms.md` + `platform/error-handling.md` — error-code registry (remove TMS-only codes if emitter-zero)

## Edge Cases

- **TMS was a side-channel, not the completion path.** `SHIPPED → COMPLETED` is driven by `inventory.confirmed`, independent of TMS (saga-status.md). Removing the TMS branch must NOT alter when/how a saga completes. If a main-path saga test needs editing, you removed too much — stop and re-scope.
- **Data migration target.** `SHIPPED_NOT_NOTIFIED` means "shipped + published, TMS push failed". Post-D8 the correct home is the main branch → migrate to `SHIPPED` (awaiting/having `inventory.confirmed`; sweeper + idempotent re-confirm settle it to `COMPLETED`). Do NOT migrate straight to `COMPLETED` blindly — a row that never got `inventory.confirmed` would skip stock confirmation. `SHIPPED` is the safe rejoin.
- **`tms_status` / `failure_reason` sharing.** Verify `failure_reason` (or any column you drop) is TMS-exclusive before dropping — if a non-TMS path writes it, keep it.
- **Stub always acked.** `StubTmsClientAdapter.notify` returns success unconditionally, so demo/standalone DBs likely have **zero** `SHIPPED_NOT_NOTIFIED` rows — the migration is a correctness safety net, not a bulk fix. Do not assume rows exist; do not assume they don't.
- **Windows local = not authority.** Testcontainers IT SKIPs locally; CI Linux is the gate. Host may be memory-constrained (recent agents hit commit-limit OOM) — if Gradle OOMs, do a line-by-line review + rely on CI, and say so explicitly.

## Failure Scenarios

- **A — Main-path regression.** If the `SHIPPED → COMPLETED` (inventory.confirmed) transition, or reserve/cancel/sweeper, changes behaviour, the removal over-reached — the TMS branch is parallel/independent; only it comes out.
- **B — Survivor reference (deletion leaves survivors).** A spec sentence, metric label, config key, gateway route, or test still naming `tms`/`SHIPPED_NOT_NOTIFIED`/`retry-tms-notify` after removal = incomplete retirement; grep each symbol repo-wide and clear it. The dead-ref + error-code-registry guards are the CI backstop.
- **C — Existing migration edited.** Editing a prior `V*.sql` instead of adding a new versioned migration breaks Flyway validate on existing DBs — additive new migration only.
- **D — Cross-project leak.** Any `projects/scm-platform/` or `projects/platform-console/` change here means the boundary was wrong — this is wms-internal (PC-FE-258 already did the console half; logistics already owns dispatch).
- **E — Emitter-zero code left / live code removed.** Removing an error code still emitted (registry guard RED) or leaving a TMS-only code emitter-zero — reconcile via emitter grep, not sentiment.
- **F — CI-RED at merge.** 3-dim merge-verification applies. The wms outbound Integration lane is the highest-signal check (saga + Flyway validate).

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): a full subsystem removal touching a live saga state machine + a Flyway data migration + ~11 spec sections + error-code/metrics reconciliation, where the discipline is "remove exactly the side-channel, prove the main path is untouched" → **Opus** (backend-engineer dispatch, `model=opus`). Not mechanical — the grep-consumers sweep and the migration target are the crux.
