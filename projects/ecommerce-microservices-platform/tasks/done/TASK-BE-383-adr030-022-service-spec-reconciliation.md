# TASK-BE-383 — Reconcile ecommerce service specs with merged ADR-MONO-030 (multi-tenant + marketplace) and ADR-MONO-022 (ecommerce↔wms fulfillment) reality

**Status:** done
**Type:** TASK-BE
**Analysis model:** Opus 4.8
**Recommended impl model:** Sonnet

---

## Goal

Spec-only (net-zero) reconciliation pass: correct ecommerce service specs and feature docs to match code reality after ADR-MONO-030 Steps 2–4 and ADR-MONO-022 have merged. No production code changes.

---

## Scope

### In Scope (9 spec edits)

1. **`PROJECT.md` (Trait Rationale + Out-of-Scope)** — Update `multi-tenant` trait rationale from "product-service + order-service" to the full completed set (+ user/promotion/shipping/notification). Update the "in-migration" list from 10 services to the 6 genuinely-pending (cart/payment/review/search/auth/web-store). Add admin-dashboard (frontend-app) retirement note (ADR-MONO-031 Phase 6 / TASK-MONO-259).

2. **`specs/services/settlement-service/architecture.md` (status banner)** — Replace "forward-declared / does not exist yet" banner with "implemented — ADR-MONO-030 Step 4 facet b, TASK-BE-365 (66 Java files)".

3. **`specs/features/marketplace-settlement.md` (preamble + §7 impact table)** — Drop "신규 settlement-service" future framing; mark settlement-service implemented (TASK-BE-365). Change §7 "구현 시 …" table to DONE where work has landed.

4. **`specs/services/shipping-service/architecture.md`** — (a) Add `## Multi-Tenancy & Marketplace (ADR-MONO-030)` section (V7 migration, M1-M6 + D8, mirroring user-service/promotion-service pattern). (b) Update Identity table Event publication row to include `ecommerce.fulfillment.requested.v1` (FulfillmentRequested via outbox, ADR-022 forward leg) and Event consumption row to include `wms.outbound.shipping.confirmed.v1` + `wms.outbound.order.cancelled.v1` (consumer group `shipping-service-wms`). (c) Add `## ADR-MONO-022 Fulfillment Integration` subsection describing forward (publish) + return (consume) legs. Update `## Events` section to match.

5. **`specs/services/shipping-service/overview.md` (Public surface table)** — Add `ecommerce.fulfillment.requested.v1` (Kafka publish, ADR-022 forward leg), `wms.outbound.shipping.confirmed.v1` (Kafka consume), and `wms.outbound.order.cancelled.v1` (Kafka consume) rows.

6. **`specs/services/order-service/architecture.md` (Identity table)** — Add `wms.outbound.order.cancelled.v1 (WmsOutboundCancelledConsumer, consumer group order-service-wms, ADR-022 §D4)` to the Event consumption row.

7. **`specs/services/notification-service/architecture.md`** — (a) Add `## Multi-Tenancy (ADR-MONO-030 Step 4 / TASK-BE-370)` section (V5 migration, mirroring user-service pattern). (b) Fix Identity table contradiction: change Event publication from "Kafka (delivery outcome events)" to "none (terminal consumer)". (c) Fix `UserSignedUp` publisher attribution from "(auth-service)" to "(IAM → ecommerce bridge; auth-service decommissioned TASK-BE-132)".

8. **`specs/features/multi-tenancy-and-marketplace.md`** — (a) Add `## 진행 현황 (as-of 2026-06-15)` table listing completed increments (user/promotion/shipping/notification migrations + settlement-service impl + admin-dashboard retirement). (b) Reframe §1 column header "현재 (single-tenant)" → "Before Step 2 (single-tenant, 역사적 기준)".

9. **`specs/contracts/events/wms-shipment-subscriptions.md`** — Remove "v2(a), TASK-MONO-197" pending qualifier markers; replace with "(implemented, TASK-MONO-197)" annotations on both the Consumer Groups section, the Subscribed Topics table row, and the `## order-service consumer` section heading.

### Out of Scope

- All code under `apps/` — read-only verification only, no edits
- `account_type` drift (already addressed in a prior series)
- Cluster B auth-residue spec cleanup (handled separately)
- Cluster C structural spec refactors (handled separately)
- Any new service implementation or migration

---

## Acceptance Criteria

1. All 9 spec edits applied; diff is spec-only (no `apps/` changes).
2. `PROJECT.md` in-migration list is exactly: cart/payment/review/search/auth/web-store (6 services).
3. `settlement-service/architecture.md` status banner reads "implemented" (not "forward-declared").
4. `marketplace-settlement.md` §7 table has a "상태" column with DONE items.
5. `shipping-service/architecture.md` contains `## Multi-Tenancy & Marketplace` and `## ADR-MONO-022 Fulfillment Integration` sections; Identity table has both fulfillment events.
6. `shipping-service/overview.md` Public surface table includes `ecommerce.fulfillment.requested.v1`, `wms.outbound.shipping.confirmed.v1`, and `wms.outbound.order.cancelled.v1` rows.
7. `order-service/architecture.md` Event consumption row includes `wms.outbound.order.cancelled.v1` with consumer group `order-service-wms`.
8. `notification-service/architecture.md` Identity table Event publication = "none (terminal consumer)"; Multi-Tenancy section added; UserSignedUp attribution corrected.
9. `multi-tenancy-and-marketplace.md` has progress table and §1 column header is historical ("Before Step 2").
10. `wms-shipment-subscriptions.md` has no "v2(a)" pending markers; all say "implemented, TASK-MONO-197".

---

## Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` — Step 1 SoT (ADR-MONO-030)
- `specs/features/marketplace-settlement.md` — Step 4 facet b SoT
- `specs/services/settlement-service/architecture.md`
- `specs/services/shipping-service/architecture.md` + `overview.md`
- `specs/services/order-service/architecture.md`
- `specs/services/notification-service/architecture.md`
- `specs/services/user-service/architecture.md` (pattern reference for Multi-Tenancy section)
- `specs/services/promotion-service/architecture.md` (pattern reference for Multi-Tenancy section)

---

## Related Contracts

- `specs/contracts/events/wms-shipment-subscriptions.md` — ADR-MONO-022 return-leg subscriptions
- ADR-MONO-030 (`docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md`) — multi-tenant + marketplace SaaS decision
- ADR-MONO-022 (`docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md`) — ecommerce ↔ wms fulfillment integration

---

## Edge Cases

- `notification-service` has no V5 migration task reference explicitly documented in the spec; use the known task number (TASK-BE-370) as a placeholder — if the actual task number differs, update during review.
- `shipping-service` V7 migration task reference similarly uses TASK-BE-369 as placeholder; verify against actual merged task before closing.
- `settlement-service/architecture.md` already has a `## Multi-Tenancy & Marketplace` section (accurate) — no duplicate to be added; the only edit is the status banner.
- `order-service/architecture.md` already has a complete `## Multi-Tenancy & Marketplace` section — no duplicate; only the Identity Event consumption row needs the ADR-022 addition.

---

## Failure Scenarios

- If a shipping/notification tenant_id migration task number is incorrect, the spec note is still factually accurate (migration exists in code); update the task ID reference as a follow-up without blocking close.
- If `wms-shipment-subscriptions.md` contains additional "(v2(a))" occurrences not caught above, re-scan and patch before review.
- If the §7 table in `marketplace-settlement.md` has additional "구현 시" rows beyond the four listed, mark them DONE or leave as-is with a comment depending on implementation reality.
