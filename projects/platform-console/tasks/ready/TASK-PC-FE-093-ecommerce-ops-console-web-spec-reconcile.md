# TASK-PC-FE-093 — reconcile `console-web` architecture spec with the shipped `features/ecommerce-ops` surface

**Status:** ready
**Area:** platform-console / console-web · **Spec-only** (docs reconciliation — 0 production code change)
**Parent:** ADR-MONO-031 (ecommerce operator UI console consolidation) · ADR-MONO-030 Step 4 facet a/f.

## Goal

Close the spec drift left by **TASK-PC-FE-081…090**. Those tasks shipped the full
`features/ecommerce-ops` console surface (7 operator areas) **and** kept the
**contract** current (`console-integration-contract.md` § 2.4.10–§ 2.4.10.5), but
none of them updated **`specs/services/console-web/architecture.md`**. As a result
that service-architecture spec still:

1. Describes `features/ecommerce-ops` as **"planned"** (the § Status blurb, line ~19),
   listing only product/order as the surface and users/promotions/shippings/
   notifications as "staged" — all 7 areas are now **live in `origin/main`**.
2. Omits the `ecommerce/` routes, the `api/ecommerce/**` Route Handlers, and the
   `features/ecommerce-ops/` feature block from the **Internal Structure Rule** tree
   (lines 78–167), even though every other shipped feature is enumerated there.

The **contract** is correct and stays the authoritative source; this task only
realigns the consumer service-architecture doc (plus one stale tail-clause in the
contract's § 2.3 catalog-drill-in note) so the spec matches deployed reality.

## Scope (spec-only, two files)

### A. `specs/services/console-web/architecture.md`
1. **§ Status blurb (line ~19)** — rewrite from "planned / Phase 1" to **shipped**:
   the `features/ecommerce-ops` surface is live across **7 operator areas** —
   products (+images/variants/stock), orders, users, promotions, shippings,
   notification templates, sellers — landed by TASK-PC-FE-081…090. Keep the
   architecture facts (console-web → ecommerce gateway direct, ADR-MONO-017 D2.A,
   no console-bff write leg; `getDomainFacingToken()` never `getOperatorToken()`;
   `productKey=ecommerce` eligibility; § 3 parity matrix not mutated, count stays
   16). Repoint to contract **§ 2.4.10–§ 2.4.10.5**. Note that the once-"staged"
   tenant-isolation backlog (users/promotions/shippings/notifications) is now
   **resolved** — each area's backend `tenant_id` migration (ADR-MONO-030 Step 4)
   landed before its console absorption, as the contract sub-sections record.
2. **Internal Structure Rule tree** — add, mirroring the existing annotated style:
   - under `(console)/`: `ecommerce/` drill-in parent (운영 + products/orders/users/
     promotions/shippings/notifications/sellers child routes).
   - under `api/`: `ecommerce/...` Route Handlers (direct-to-gateway write/read
     proxy; `getDomainFacingToken()`; `ECOMMERCE_ADMIN_BASE_URL` for
     products/orders/users/sellers, `ECOMMERCE_PUBLIC_BASE_URL` for
     promotions/shippings/notifications; flat ecommerce error envelope; no
     `X-Tenant-Id`, no `Idempotency-Key`, no `X-Operator-Reason`).
   - under `features/`: `ecommerce-ops/` block (api/hooks/components/index barrel)
     summarising the 7 areas + per-area read vs mutation.

### B. `specs/contracts/console-integration-contract.md`
3. **§ 2.3 catalog-drill-in note (line ~53)** — the tail clause "The v1 `/ecommerce`
   content surfaces the ecommerce **domain-health** summary; the rich operations
   surface (product/order/seller management) is a deferred follow-up" is stale.
   Update to record that the rich operations surface is **delivered**
   (§ 2.4.10–§ 2.4.10.5, TASK-PC-FE-081…090); the `/ecommerce` index (운영) keeps
   the domain-health summary as the parent landing.

## Out of scope
- Any production code, test, or contract § 2.4.10.x body change (contract is correct).
- admin-dashboard sunset bookkeeping (ADR-MONO-031 Phase 6 / TASK-MONO-259 — done separately).
- Re-verifying the console surface in a browser (covered by PC-FE-081…090 + the
  federation-demo Playwright runs).

## Acceptance Criteria
- `console-web/architecture.md` § Status blurb describes ecommerce-ops as **shipped**
  (no "planned"), enumerates the 7 areas, and points to § 2.4.10–§ 2.4.10.5.
- The Internal Structure Rule tree lists the `ecommerce/` routes, `api/ecommerce/**`
  handlers, and the `features/ecommerce-ops/` block, consistent in style with the
  surrounding feature entries.
- The contract § 2.3 drill-in note no longer calls the operations surface a
  "deferred follow-up".
- No production code / test / contract-§ 2.4.10.x change; § 3 parity attestation
  count stays **16**.
- Markdown links resolve (relative paths intact).

## Related Specs / Contracts
- `console-integration-contract.md` § 2.3 + § 2.4.10–§ 2.4.10.5 (authoritative, unchanged body)
- `docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md`
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` (Step 4 facets a/f)
- Shipped code: `apps/console-web/src/features/ecommerce-ops/`, `app/(console)/ecommerce/`, `app/api/ecommerce/`

## Edge Cases
- Keep the blurb's § 3 parity invariant intact (additive domain scope, attestation count 16 — do not imply a new parity row).
- Promotions/shippings/notifications use `ECOMMERCE_PUBLIC_BASE_URL`; products/orders/users/sellers use `ECOMMERCE_ADMIN_BASE_URL` — do not flatten this distinction in the tree annotation.

## Failure Scenarios
- Asserting a console-bff **write** leg for ecommerce (there is none — ADR-MONO-017 D2.A; BFF stays read-aggregation only).
- Implying `getOperatorToken()` or `X-Tenant-Id` on the ecommerce direct call (forbidden — § 2.4.10 cross-cutting).
