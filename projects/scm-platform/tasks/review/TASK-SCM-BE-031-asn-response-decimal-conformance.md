# TASK-SCM-BE-031 — ASN response decimal string contract conformance (+ IVS import cleanup)

**Status:** review

**Type:** TASK-SCM-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (small fix; surfaced by the SCM-BE-030 refactor-code discovery)

---

## Goal

Fix the procurement-service contract-conformance gap that the `/refactor-code scm-platform`
discovery (post-BE-030) surfaced: `AsnResponse.LineResponse.quantityShipped` /
`quantityReceived` are plain `BigDecimal` with **no** `@JsonFormat(shape=STRING)`,
so Jackson serialises them as JSON **numbers** — but
[`procurement-api.md`](../../specs/contracts/http/procurement-api.md) (ASN webhook
response, lines ~276/304) quotes line quantities as decimal **strings**
(`"5.0000"`). This is the exact gap **TASK-SCM-BE-020** fixed for
`PurchaseOrderResponse` (PR #1037); the ASN line response was missed.

This is a **fix** that aligns the code to the **existing** contract (the contract
already mandates strings — no contract edit). Bundles one cosmetic refactor from
the same discovery (out-of-band scope, user-approved): the inventory-visibility
`InventoryVisibilityController` `/nodes` handler referenced `NodeResponse` by
inline fully-qualified name 3× while every sibling DTO is imported.

## Scope

**In scope:**

1. **AsnResponse decimal conformance (procurement-service)** —
   `presentation/dto/AsnResponse.java`: add `@JsonFormat(shape = JsonFormat.Shape.STRING)`
   to `LineResponse.quantityShipped` and `quantityReceived` (mirrors
   `PurchaseOrderResponse`, BE-020). Add a slice-test regression in
   `AsnWebhookControllerSliceTest.receiveAsnHappyPath` asserting
   `$.data.lines[0].quantityShipped` is a JSON **String** (`instanceOf(String.class)`
   + value `"5.00"`) through real Jackson/MockMvc.
2. **Cosmetic import cleanup (inventory-visibility-service)** —
   `adapter/inbound/web/InventoryVisibilityController.java`: replace the 3 inline
   FQN `com.example...dto.NodeResponse` references in the `/nodes` handler with a
   normal `import` (the only `NodeResponse`, no name collision). Pure readability;
   identical bytecode.

**Out of scope (unchanged):**

- No contract change — `procurement-api.md` already specifies string decimals; this
  brings code into conformance with it.
- `AsnWebhookRequest` (inbound) decimal parsing — unchanged (Jackson accepts both
  string and number on the inbound side; the gap is response serialisation only).
- The BE-030 deliberately-skipped items (`isEntitled` dedup, reorder fallback,
  audit-log boilerplate, consumer-skeleton dedup) — still skipped (behavior-risk /
  marginal).
- No Flyway / event / ADR / `PROJECT.md` frontmatter change.

## Acceptance Criteria

- **AC-1** — The ASN webhook 200 response serialises `lines[].quantityShipped` /
  `quantityReceived` as JSON **strings** (e.g. `"5.00"`), matching
  `procurement-api.md`; a non-null `quantityReceived` would likewise be a string,
  and `null` stays `null`.
- **AC-2** — `AsnWebhookControllerSliceTest` asserts the string shape through real
  Jackson (regression guard); `./gradlew :projects:scm-platform:apps:procurement-service:test` green.
- **AC-3** — `InventoryVisibilityController` no longer uses an inline FQN for
  `NodeResponse`; `./gradlew :projects:scm-platform:apps:inventory-visibility-service:test`
  green; behavior/bytecode identical.
- **AC-4** — No contract/schema/event/ADR change; change confined to the two
  service modules; `PROJECT.md` frontmatter byte-unchanged.

## Related Specs

- `projects/scm-platform/specs/contracts/http/procurement-api.md` (ASN webhook response — string decimals)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`

## Related Contracts

- `procurement-api.md` § `POST /api/procurement/webhooks/asn` (the response shape this fix conforms to — unchanged).

## Edge Cases

- **`quantityReceived` null** — `@JsonFormat(STRING)` on a null value still emits
  `null` (not `"null"`); the slice test's view has `quantityReceived=null`, so the
  happy-path assertion targets `quantityShipped`.
- **Inbound parsing unaffected** — the fix is response-only; `AsnWebhookRequest`
  decimal binding (string-or-number) is untouched, so supplier callers are not
  broken.

## Failure Scenarios

- **F1 — a console/consumer parsing `z.string()` on ASN line qty** would have
  `parse-fail` on the numeric form (the BE-020 symptom for PO list). Guarded by AC-1/AC-2.
- **F2 — over-reach** — touching inbound parsing or the BE-030-skipped items would
  risk behavior change. Guarded by the Out-of-scope list + AC-4.
