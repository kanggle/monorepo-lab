# Task ID

TASK-SCM-BE-028

# Title

scm-side spec reconciliation — recognise platform-console as a sanctioned operator **config (seed)** consumer of demand-planning reorder-policy + sku-supplier-map routes

# Status

review

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: `TASK-SCM-BE-024` (demand-planning-service bootstrap — **done**; the `/api/v1/demand-planning/**` route that already serves the `policies` / `sku-supplier-map` seed endpoints) and `TASK-SCM-BE-027` (platform-console operator-**action** consumer acknowledgment — **done**). ADR-MONO-027 ACCEPTED governs the loop.
- **extends**: `TASK-SCM-BE-027`. That task widened the console acknowledgment from read-only (SCM-BE-015) to the demand-planning **operator-action** surface (`approve`/`dismiss`), but **explicitly fenced OUT** the `GET|PUT /policies/{skuCode}` and `GET|PUT /sku-supplier-map/{skuCode}` **seed** routes ("admin-seed, not the operator gate … if/until a separate seeding screen is specced"). This task is that **separate** reconciliation — it widens the acknowledgment to the **operator config (seed)** surface so the console can build the policy / supplier-mapping config screen (PC-FE-080).
- **blocks**: `TASK-PC-FE-080` (platform-console *scm replenishment seed/config screen*). FE-080 is the console consumer; per CLAUDE.md "Specs win over tasks", the producer spec must acknowledge the console **config** consumer **before** the console code lands (mirrors SCM-BE-027 ⊃ FE-077, now for the seed routes).
- **(B) document/accept, not a capability change**: demand-planning's `policies` / `sku-supplier-map` routes already exist (built by SCM-BE-024; [`demand-planning-api.md`](../../specs/contracts/http/demand-planning-api.md) § Route publicity declares them "gateway-public (operator/admin seed)"), the gateway route is live (SCM-BE-027 reconciled the catalogue line), and ADR-MONO-027 § rest-api facet (D-table) lists "CRUD the `reorder_policy` / `sku_supplier_map` seed" as part of the **same operator rest-api surface** as approve. This task **documents that the console is a sanctioned caller** of them — no new route, no new OAuth client, no new gateway code, no auth-model change.

# Goal

Reconcile the scm gateway contract so it acknowledges `platform-console` (ADR-MONO-013 Model B operator console) as a sanctioned **operator config (seed)** consumer of the demand-planning reorder-policy and SKU→supplier-mapping surface — closing the gap SCM-BE-027 deliberately deferred.

The replenishment operator gate (FE-077) approves `SUGGESTED` reorders, but when a suggestion has no `sku_supplier_map` row the approve fails `SKU_SUPPLIER_UNMAPPED` (422) and the operator **cannot fix the mapping from the console** — the loop's UX gap. demand-planning already exposes `GET|PUT /policies/{skuCode}` and `GET|PUT /sku-supplier-map/{skuCode}` as gateway-public operator/admin-seed routes; the producer spec must say the console is a sanctioned caller before the console (FE-080) consumes them.

This is a **spec/document-accept** task (production code = 0), the seed-surface analog of SCM-BE-027 — same credential, same gateway chain, same single-org posture.

# Scope

## In Scope

- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md`:
  - Widen the **§ *platform-console operator action consumer — demand-planning replenishment gate*** subsection (authored by SCM-BE-027) — or add a clearly-titled sibling subsection (e.g. *platform-console operator config (seed) consumer*) — to record:
    - **Consumed (operator config — the net-new acknowledgment)**: `GET|PUT /api/v1/demand-planning/policies/{skuCode}`, `GET|PUT /api/v1/demand-planning/sku-supplier-map/{skuCode}`.
    - The **credential is unchanged** — the console calls server-side with the human operator's IAM `platform-console-web` OIDC access token (RS256, ADR-001), validated by the **existing** gateway chain (`AllowedIssuersValidator` + `TenantClaimValidator` `tenant_id ∈ { scm, * }` + `JwtHeaderEnrichmentFilter` `X-Token-Type=user`). No new scm OAuth client, no new gateway route, no new gateway code. **Same** credential the read + action consumers already use (SCM-BE-015 / SCM-BE-027) — config rides the same token, not a privileged/admin exchange.
    - The **config-surface invariant**: these are **upsert (PUT) / inspect (GET) per-SKU** seed rows that feed the reorder evaluation (`reorder_policy`: reorderPoint/safetyStock/reorderQty; `sku_supplier_map`: supplierId/defaultOrderQty/leadTimeDays/currency). Editing them changes **future** suggestion evaluation only — it does **not** mutate existing suggestions or POs, does **not** dispatch anything, and does **not** bypass the operator gate. Single-organization preserved (the `multi-tenant` non-declaration in [`PROJECT.md`](../../PROJECT.md) is **unaffected**).
  - **Cross-reference, not redefine**: authoritative endpoint shapes/idempotency/error-codes stay in [`demand-planning-api.md`](../../specs/contracts/http/demand-planning-api.md) (unchanged by this task). Point the console-side obligation at platform-console [`console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md) § 2.4.6.2 (authored by FE-080).
  - Update the **route-catalogue** `demand-planning-service` endpoint table (the one SCM-BE-027 set to **live**) only if the `policies` / `sku-supplier-map` rows' Publicity column needs to reflect "operator config (console-consumed)" instead of "operator/admin seed" — keep additive, byte-minimal.

## Out of Scope

- Any change to `demand-planning-api.md` endpoint shapes, idempotency semantics, or error codes (authoritative, consumed unchanged).
- Any production code, gateway filter, OAuth client, new route, or **new role/scope** (the seed routes are already gateway-public under the existing `tenant_id=scm` gate; this task does **not** introduce an admin-only tier).
- The console-side screen + contract section (`§ 2.4.6.2`) — that is FE-080 in platform-console.
- scm `PROJECT.md` classification bytes (must stay unchanged — single-org preserved).
- The `procurement` PO-write surface (submit/confirm/cancel) — buyer/machine path, deliberately not console-consumed (ADR-MONO-027 D5).

# Acceptance Criteria

- [ ] `gateway-public-routes.md` records `platform-console` as a sanctioned operator-**config** consumer of demand-planning `GET|PUT /policies/{skuCode}` + `GET|PUT /sku-supplier-map/{skuCode}`, with the credential explicitly unchanged (IAM `platform-console-web` OIDC token, existing gateway chain, `tenant_id ∈ {scm,*}`, **no** admin-only tier).
- [ ] The config-surface invariant (upsert/inspect per-SKU seed; affects future evaluation only; no existing-suggestion/PO mutation; no dispatch; single-org preserved; `PROJECT.md` `multi-tenant` non-declaration unaffected) is stated.
- [ ] Authoritative shapes stay in `demand-planning-api.md` (unchanged); the console obligation is pointed at `console-integration-contract.md` § 2.4.6.2 (cross-reference, no redefinition).
- [ ] Change is **additive + spec-only** (production code 0); scm classification bytes unchanged; spec internal-link lint clean; `validate-rules` no new inconsistency.
- [ ] Merged **before** FE-080 code (spec-first cross-project gate — the FE-080 dependency marker links this task).

# Related Specs

> Target project = `scm-platform`. This is a contract/spec reconciliation; no service-type code change. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md` § D3 (`sku_supplier_map`) / § D4 (`reorder_policy`) / rest-api facet ("CRUD the seed")
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 Model B / § D6 (the governing console ADR)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (**changed** — the operator config consumer acknowledgment)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (authoritative producer — consumed unchanged; route publicity already declares the seed routes gateway-public)
- `projects/scm-platform/PROJECT.md` (single-org; `multi-tenant` non-declaration must stay unaffected)
- `projects/scm-platform/tasks/done/TASK-SCM-BE-027-platform-console-operator-action-consumer-reconciliation.md` (the action-surface precedent this extends)

---

# Related Contracts

- **Changed (this task)**: scm `gateway-public-routes.md` — platform-console operator-config (seed) consumer acknowledgment.
- **Consumed (unchanged, authoritative — scm-owned)**: `demand-planning-api.md` (policies + sku-supplier-map GET/PUT shapes, error codes).
- **Downstream consumer obligation (other project, FE-080)**: platform-console `console-integration-contract.md` § 2.4.6.2.

---

# Edge Cases

- A reviewer reads the seed acknowledgment as introducing an **admin-only** credential tier → the subsection must state the seed routes ride the **same** IAM OIDC `tenant_id=scm` operator token as the reads/actions (scm has no operator/admin-token split; "operator/admin seed" in the producer contract is a *naming* of the surface, not a stronger credential).
- Someone assumes editing a policy/mapping retroactively changes existing suggestions/POs → the invariant must state it affects **future** evaluation only.
- The `procurement` PO-write surface is mistaken as now console-consumable → out of scope; only the demand-planning seed routes are widened.

# Failure Scenarios

- FE-080 code starts before this merges → spec-first violation; the FE-080 dependency marker + AC gate it on this task.
- The widening accidentally edits `demand-planning-api.md` shapes or `PROJECT.md` classification → scope violation (consumer-acknowledgment only; producer + classification untouched).
- The acknowledgment invents an admin role/scope → scope violation (no new auth tier; same operator token).

---

# Recommended Implementation Model

- **Sonnet** — spec/document-accept reconciliation (production code 0), pattern fully established by SCM-BE-027. Escalate only if the route-catalogue check reveals an unexpected gateway gap (it should not — SCM-BE-027 already set the demand-planning route live).

---

# Definition of Done

- [ ] `gateway-public-routes.md` widened to operator-config (seed) consumer
- [ ] Additive, spec-only, classification bytes unchanged; cross-references correct; link lint + `validate-rules` clean
- [ ] Merged before FE-080 (spec-first gate)
- [ ] Acceptance Criteria all satisfied
- [ ] Ready for review
