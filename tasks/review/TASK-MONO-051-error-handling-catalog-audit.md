# Task ID

TASK-MONO-051

# Title

`platform/error-handling.md` catalog audit — wms-domain drift backfill (B common-rule refactor wave 2)

# Status

ready

# Owner

backend / monorepo

# Task Tags

- spec
- catalog
- rules

---

# Goal

Close the **catalog ↔ implementation drift** in the platform-wide error
registry (`platform/error-handling.md`) for the **wms domain** (master /
inbound / inventory / outbound services). An initial grep audit of
`*Exception.java` files across the 4 wms services surfaces **11 error
codes emitted in production code that are absent from the catalog**.

The platform/error-handling.md `## Change Rule` is explicit:

> New error codes must be added to this document before being used in implementation.

The fact that 11 codes slipped through implies the rule has been informally
violated during the 029~033 + ecommerce / scm rapid-build series. This
task does **not** punish the gap — it backfills the catalog, surfaces
the pattern, and (optionally) hardens the rule.

This is **B (Common Rule Refactoring) wave 2** per memory
`project_b_common_rule_refactor_pending.md` candidate #2. D4 OVERRIDE
applies (sole user-acknowledged risk path — see
[ADR-MONO-003](../../docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md)
§ 3.4 risk 2).

---

# Scope

## In Scope

Backfill the catalog for **wms domain only** (the audit chunk already
done in the spec phase). Concrete drift list:

### `inbound-service` (`[domain: wms]`)

| Code | HTTP | Source (`*Exception.java`) | Suggested catalog section |
|---|---|---|---|
| `SKU_INACTIVE` | 422 | `SkuInactiveException` | Master Data (cross-ref from Inbound) |
| `LOCATION_INACTIVE` | 422 | `LocationInactiveException` | Master Data (cross-ref from Inbound) |

### `inventory-service` (`[domain: wms]`)

| Code | HTTP | Source (`*Exception.java`) | Suggested catalog section |
|---|---|---|---|
| `TRANSFER_NOT_FOUND` | 404 | `TransferNotFoundException` | Inventory |
| `RESERVATION_NOT_FOUND` | 404 | `ReservationNotFoundException` | Inventory |
| `RESERVATION_QUANTITY_MISMATCH` | 422 | `ReservationQuantityMismatchException` | Inventory |
| `ADJUSTMENT_NOT_FOUND` | 404 | `AdjustmentNotFoundException` | Inventory |

### `outbound-service` (`[domain: wms]`)

| Code | HTTP | Source (`*Exception.java`) | Suggested catalog section |
|---|---|---|---|
| `SHIPMENT_NOT_FOUND` | 404 | `ShipmentNotFoundException` | Outbound |
| `PICKING_REQUEST_NOT_FOUND` | 404 | `PickingRequestNotFoundException` | Outbound |
| `PICKING_INCOMPLETE` | 422 | `PickingIncompleteException` | Outbound |
| `PACKING_UNIT_NOT_FOUND` | 404 | `PackingUnitNotFoundException` | Outbound |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | `ExternalServiceUnavailableException` | General (Platform-Common — re-evaluate vs existing `SERVICE_UNAVAILABLE` / `CIRCUIT_OPEN` / `DOWNSTREAM_ERROR`) |

### Cross-service: WAREHOUSE_MISMATCH

Already in catalog under `Inbound` only. `outbound-service` emits the
same code. Either:
- promote to Master Data (cross-service shared invariant), OR
- explicitly note in Outbound: "reuses `WAREHOUSE_MISMATCH` from Inbound — same semantics applied to outbound `Shipment.warehouseId` vs `Order.warehouseId` consistency".

Recommendation: explicit cross-reference (no duplicate row).

### Optional: outbound `ORDER_NO_DUPLICATE` → currently emits `CONFLICT`

`outbound-service/.../OrderNoDuplicateException.java` returns `"CONFLICT"`
(the generic Transactional Trait code) but the semantic is "outbound
order number duplicate". Inbound has `ASN_NO_DUPLICATE` as a dedicated
code. Recommend either:
- Add `ORDER_NO_DUPLICATE` to Outbound section + update exception code,
  OR
- Add a note in the Outbound section explaining the deliberate reuse of
  `CONFLICT` for this case (and rename the exception class for clarity).

Recommendation: add `ORDER_NO_DUPLICATE` (mirror `ASN_NO_DUPLICATE`
naming).

## Out of Scope (explicitly deferred — follow-up TASK-MONO-052 candidates)

- **ecommerce domain audit** (10 services: product/search/order/payment/user/promotion/notification/review/wishlist/shipping)
- **scm domain audit** (procurement / inventory-visibility)
- **GAP domain audit** (auth / account / admin / community / membership / security)
- **fan-platform domain audit** (community / artist + missing services)
- **Code-side fixes** — exception classes that emit codes already in
  catalog but with subtly different semantics. Defer; only add catalog
  entries here.
- **Strengthening the "new error codes must be added to this document
  before being used" rule** — e.g. CI grep gate. Out of scope; consider
  for TASK-MONO-053.

---

# Acceptance Criteria

- [ ] 11 wms error codes added to `platform/error-handling.md` per the table above. Each entry includes HTTP status + 1-line description matching the exception's javadoc.
- [ ] `WAREHOUSE_MISMATCH` cross-service usage documented (outbound + inbound) — either by adding a note or by promoting to Master Data.
- [ ] `ORDER_NO_DUPLICATE` added to Outbound section (decision recorded) OR documented as deliberate `CONFLICT` reuse.
- [ ] `EXTERNAL_SERVICE_UNAVAILABLE` placement decision documented (Platform-Common vs Outbound vs alias of existing code).
- [ ] No code changes to services — spec-only update.
- [ ] Follow-up TASK-MONO-052 (ecommerce/scm/GAP/fan-platform domain audit) filed in same impl PR or referenced in INDEX.

---

# Related Specs

- `platform/error-handling.md` (target)
- `rules/domains/wms.md` § Standard Error Codes (cross-ref)
- `rules/traits/transactional.md` § T-codes (HTTP status conventions)
- `ADR-MONO-003-phase-5-template-extraction-deferred.md` § 3.4 risk 2 (D4 OVERRIDE rationale)

# Related Skills

- `.claude/skills/refactor-spec` (skill applies to spec drift cleanup)

---

# Related Contracts

None — registry is a catalog spec, not a contract. The codes themselves are already-shipping contracts (services emit them in 4xx/5xx responses); this task just records them in the canonical registry.

---

# Target Service

- Spec: `platform/error-handling.md` (shared)
- Audit source: `projects/wms-platform/apps/{master,inbound,inventory,outbound}-service/src/main/java/**/*Exception.java`

---

# Architecture

N/A — spec-only registry update. No service architecture impact.

---

# Implementation Notes

- Use existing catalog section structure (Master Data / Inbound /
  Inventory / Outbound / Admin / Notification, each tagged
  `[domain: wms]`).
- Match the column format: `Code | HTTP | Description`.
- For codes that already exist in code (the 11 above), copy the
  description from the exception class javadoc; if the javadoc is
  missing, write a 1-line description matching the constructor's
  intent.
- The grep audit command used to surface this list:
  ```
  grep -rn 'return\s\+"[A-Z][A-Z_]\+"' projects/wms-platform/apps/*/src/main/java/**/*Exception.java
  ```
  Document this command in the task's impl notes for future audits.

---

# Edge Cases

- **`EXTERNAL_SERVICE_UNAVAILABLE` vs `SERVICE_UNAVAILABLE`**: existing
  catalog has `SERVICE_UNAVAILABLE` (503, General). outbound-service
  emits `EXTERNAL_SERVICE_UNAVAILABLE` (also 503). These overlap. Three
  resolution options:
  1. Add `EXTERNAL_SERVICE_UNAVAILABLE` as Outbound-specific (TMS focus).
  2. Promote to Platform-Common General (deprecate `SERVICE_UNAVAILABLE`).
  3. Alias `EXTERNAL_SERVICE_UNAVAILABLE` → `SERVICE_UNAVAILABLE`
     (catalog records both; outbound-service eventually renames the
     exception class).

  **Recommendation**: option 1 (Outbound-specific) — distinguishes
  external 3rd-party (TMS, supplier) from internal service failures.
  Both 503 but different operator playbooks.

- **`WAREHOUSE_NOT_FOUND` in inbound's `WarehouseNotFoundInReadModelException`**:
  cross-service emission of a Master Data code from inbound's read
  model. Already in catalog under Master Data. Document the
  cross-service usage with a 1-line note in Inbound section (no new
  entry).

---

# Failure Scenarios

- **Catalog entry contradicts code behavior**: e.g. catalog says HTTP
  422 but code returns 409. Mitigation: cross-check against
  `GlobalExceptionHandler.handle*` mapping during impl. If mismatch
  found, prefer the **code's behavior** (real shipping contract) and
  update the description; flag for a follow-up code-fix task only if
  the code clearly violates the documented semantic.
- **Same code, different descriptions in different services**: 4 wms
  services may have subtly different javadoc for shared codes
  (`STATE_TRANSITION_INVALID`, `WAREHOUSE_MISMATCH`,
  `PARTNER_INVALID_TYPE`, `LOT_REQUIRED`). Mitigation: use the most
  inclusive description and note the multi-service emission in catalog.

---

# Test Requirements

N/A — spec-only update. Verification = manual review:
- `grep -rn '"[A-Z_]\+"' platform/error-handling.md` ⊇ the 11 new codes.
- Visual inspection of catalog structure (no duplicate codes, proper
  HTTP column).

---

# Definition of Done

- [ ] 11 wms domain codes added to catalog per table.
- [ ] `WAREHOUSE_MISMATCH` cross-service usage documented.
- [ ] `ORDER_NO_DUPLICATE` decision recorded.
- [ ] `EXTERNAL_SERVICE_UNAVAILABLE` placement decision recorded.
- [ ] Follow-up TASK-MONO-052 (ecommerce/scm/GAP/fan-platform audit) filed.
- [ ] Impl PR description references this task + the grep audit command for future replays.

---

# Provenance

Filed by memory `project_b_common_rule_refactor_pending.md` candidate #2
(`platform/error-handling.md` catalog audit). Wave 1 was PR #328 (which
added `[domain: scm]` section + 11 fixes). Wave 2 is this task (wms
backfill). Wave 3 (TASK-MONO-052 candidate) covers ecommerce / scm /
GAP / fan-platform.

D4 OVERRIDE applies — user-acknowledged risk path per
[ADR-MONO-003](../../docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md)
§ 3.4 risk 2. last_churn marker resets on impl PR merge.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical catalog append — 11
straightforward entries, decision points already enumerated in this
spec).
