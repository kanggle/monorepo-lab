# Task ID

TASK-MONO-230

# Title

ADR-MONO-030 (PROPOSED) — ecommerce Multi-Vendor Marketplace SaaS. Author the committed PROPOSED ADR that promotes ecommerce from single-tenant/single-seller to a **multi-vendor marketplace SaaS** along two orthogonal axes: an **outer tenant axis** (Shopify-style — ecommerce joins the existing platform federation as the **6th entitlement-trust domain**, reusing the ADR-MONO-019 customer-tenant machinery: `tenant_domain_subscription` `domain_key='ecommerce'`, GAP/IAM `tenant_id` claim, row-level isolation M1-M7) and an **inner seller axis** (Coupang-style — a net-new ecommerce-local `seller` aggregate + `seller_id`, the `marketplace` scope `PROJECT.md` dropped, nested under `tenant_id`). The user fixed three forks via AskUserQuestion — **vertical-slice-first** (product + order), **row-level `tenant_id`**, **reuse platform IAM**. Doc-only; the ACCEPTED transition (which also applies the `PROJECT.md` classification change) + execution (specs → outer axis → inner axis → deferred follow-ups) are separate tasks (ADR § 3.4).

# Status

ready

# Owner

backend

# Task Tags

- adr
- multi-tenant
- marketplace
- ecommerce
- tenancy
- doc

---

# Dependency Markers

- **reuses**: ADR-MONO-019 (customer-tenant model + entitlement-trust gate — the outer-axis machinery ecommerce adopts as the 6th domain; NOT reinvented) + ADR-MONO-020 (operator↔tenant assignment — seller-admin operator surface) + ADR-MONO-023 (entitlement-plane ↔ IAM-plane separation — the D4 consumer-plane ↔ tenant/seller-admin-plane precedent) + ADR-MONO-025 (ABAC `org_scope` — the D3 seller-scoped read shape).
- **amends**: `projects/ecommerce-microservices-platform/PROJECT.md` § Out of Scope — lifts the `multi-tenant` + `marketplace` exclusions (HARDSTOP-04 supersession, D7). The edit lands at the ACCEPTED transition, NOT in this PROPOSED PR.
- **threads**: ADR-MONO-022 (ecommerce↔wms fulfillment) — its events gain `tenant_id` in a deferred Step-4 follow-up.
- **blocks**: the ADR § 3.4 execution roadmap (ACCEPTED transition + `PROJECT.md` change → specs → outer axis → inner axis → deferred). None start until ACCEPTED.

# Goal

Record the ecommerce multi-vendor-marketplace-SaaS decision so the promotion proceeds from a committed architecture record rather than a chat, fixing the two orthogonal axes (outer tenant = reuse platform federation; inner seller = net-new ecommerce-local aggregate), the three user-fixed forks (slice-first / row-level / reuse-IAM), the consumer↔operator plane separation, and a zero-regression vertical-slice roadmap — while explicitly bounding v1 to product + order and deferring settlement/commission, console integration, the remaining 11 services, and fulfillment `tenant_id` threading.

# Scope

- NEW `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` (Status PROPOSED) — D1-D8 + alternatives + relationship to ADR-019/020/022/023/025 + Status Transition History.
- Update `tasks/INDEX.md` ready list.
- This task file.

**Out of scope** (post-ACCEPTED, separate tasks): the `PROJECT.md` classification edit (D7 — at ACCEPTED), any `tenant_id`/`seller_id` schema/migration/seed, the `domain_key='ecommerce'` subscription, any console integration, any ecommerce auth-service / plane wiring change, seller settlement/commission, the other 11 services, ADR-022 fulfillment `tenant_id` threading.

# Acceptance Criteria

- **AC-1** `ADR-MONO-030` exists with Status PROPOSED and the D1-D8 decisions: D1 join-federation-as-6th-domain (reuse ADR-019); D2 row-level `tenant_id` M1-M7; D3 net-new ecommerce-local `seller` aggregate nested under `tenant_id` + ABAC seller-scoped read, settlement deferred; D4 two-plane separation (consumers on ecommerce auth-service, tenant/seller-admin on platform IAM); D5 vertical slice = product+order, both axes end-to-end; D6 zero-regression dual-accept + default-tenant/default-seller seed; D7 `PROJECT.md` classification lift (HARDSTOP-04, at ACCEPTED); D8 standalone degrades to single-store.
- **AC-2** The ADR records the accurate current state: ecommerce product/order carry **no `tenant_id`** (single-tenant verified 2026-06-12); the platform customer-tenant machinery (ADR-019 `tenant_domain_subscription` + entitlement-trust gate) is built and adopted by wms/scm/erp/finance; ecommerce has its own consumer `auth-service` + an iam-e2e federation harness.
- **AC-3** The ADR keeps the **two axes orthogonal** (outer tenant = isolation/reuse; inner seller = sharing+attribution/net-new) and explicitly **rejects** ecommerce-local tenancy (D1-B), seller-as-sub-tenant (D3-B), and single-plane identity (D4-B/C).
- **AC-4** Status Transition History has the PROPOSED row with the user intent quote ("(a+쿠팡) 멀티벤더 SaaS 둘 다" + the three AskUserQuestion forks + "진행").
- **AC-5** The ADR records the lighter fallback (chat option (b): single-tenant + thin console order/fulfillment slice) in Alternatives, as the documented retreat if the slice proves too costly.
- **AC-6** Doc-only — no `PROJECT.md` edit, no code, no migration, no contract, no console change in this PR.

# Related Specs

- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` (the reused customer-tenant model + entitlement-trust gate — outer axis)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (D4 plane-separation precedent) + `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` (D3 seller-scoped read shape) + `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` (the fulfillment loop threaded in Step 4)
- `projects/ecommerce-microservices-platform/PROJECT.md` § Out of Scope (the classification this ADR lifts) + `rules/traits/multi-tenant.md` M1-M7 (the inherited isolation invariants)

# Related Contracts

- none (the `tenant_domain_subscription` `domain_key='ecommerce'` contract + product/order tenancy specs are post-ACCEPTED deliverables, ADR § 3.4 Step 1)

# Edge Cases

- The promotion must be **zero-regression / standalone-degradable** (D6/D8): a default-tenant + default-seller seed reproduces today's single-store behavior, and the absent-`tenant_id`-claim path (standalone, no platform IAM) resolves to the default tenant — stated so the slice never breaks ecommerce's independent-repo demo.
- The **two axes must not be conflated** (§ 1.2): the seller axis is participant-attribution *within* a tenant (sharing), never an isolation boundary; the tenant axis is isolation. A reader must not mistake "make it multi-tenant" for "make sellers tenants" (D3-B rejected).
- The **two identity planes (D4)** must stay separate: consumers (ecommerce auth-service) are not platform tenants/operators; dragging them into platform IAM (D4-B) pollutes the IdP, forking tenant identity into ecommerce (D4-C / D1-B) forks the entitlement authority.
- The `PROJECT.md` classification change is HARDSTOP-04 territory — it must be **recorded in the ADR** and **applied only at the ACCEPTED transition**, never silently in the PROPOSED PR.

# Failure Scenarios

- If the ADR made ecommerce invent its own tenancy (D1-B) instead of reusing ADR-019, it would fork the entitlement authority + isolation key and lose the "free console fit" — § D1 + § 1.3 pin reuse-as-6th-domain.
- If the ADR modeled sellers as sub-tenants (D3-B), it would contradict the marketplace "shared catalog/customer pool" property and re-introduce the axis mismatch the chat resolved — § 1.2 + § D3 pin seller = in-tenant participant.
- If the ADR scoped v1 to all 13 services (D5-C) or skipped the dual-accept/default-seed (D6-B), it would transiently break main across the project, violating the BE-303 0-failing-at-merge + ADR-019 D6 dual-accept discipline.
- If the ADR omitted the `PROJECT.md` classification record (D7), the promotion would silently supersede a declared out-of-scope tag — HARDSTOP-04 violation; § D7 records it and defers the edit to ACCEPTED.
- If settlement/commission were folded into the slice instead of deferred (D5), the slice would bloat into full marketplace economics — § D3/D5 pin settlement as a named Step-4 follow-up.
