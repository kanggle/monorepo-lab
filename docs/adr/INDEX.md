# Monorepo-level ADR Index

Architecture Decision Records that span the entire monorepo (shared library
boundary, cross-project workflow, infrastructure conventions). Project-internal
ADRs live under `projects/<name>/docs/adr/`.

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-MONO-001](ADR-MONO-001-port-prefix-scaling.md) | PORT_PREFIX 슬롯 부족과 7개+ 프로젝트 동시 운영 정책 | ACCEPTED | 2026-05-02 |
| [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) | Phase 4 (Template 레포 추출 진입 결정 + scm catalyst) | ACCEPTED | 2026-05-04 |
| [ADR-MONO-003](ADR-MONO-003-phase-5-template-extraction-deferred.md) | Phase 5 (Template 레포 추출 발사) 결정 — DEFERRED → SUPERSEDED (D4 by 003a, D1 by 003b on launch) | SUPERSEDED | 2026-05-08 |
| [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) | D4 OVERRIDE Scope Canonicalization (meta-policy: IN/OUT scope + meta-rule) | ACCEPTED | 2026-05-12 |
| [ADR-MONO-003b](ADR-MONO-003b-phase-5-launch-criteria.md) | Phase 5 Launch Criteria — Template Repo Extraction (Phase 5 LAUNCHED 2026-05-13, `kanggle/project-template`) | ACCEPTED | 2026-05-13 |
| [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) | Shared Messaging Scaffolding in `libs/java-messaging` | ACCEPTED | 2026-05-10 |
| [ADR-MONO-005](ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) | Saga Timeout / Escalation / Dead-Letter Policy (4-category taxonomy) | ACCEPTED | 2026-05-11 |
| [ADR-MONO-006](ADR-MONO-006-lint-remediation-as-agent-context.md) | Lint Remediation Message as Agent Context (OpenAI Harness gap A, 4-block standard) | ACCEPTED | 2026-05-12 |
| [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) | Worktree-isolated Ephemeral Observability Stack (OpenAI Harness gap #3, Vector + VictoriaLogs/Metrics) | ACCEPTED | 2026-05-12 |
| [ADR-MONO-007a](ADR-MONO-007a-trace-layer.md) | Trace Layer (VictoriaTraces + OTLP-via-Vector) | ACCEPTED | 2026-05-28 |
| [ADR-MONO-008](ADR-MONO-008-finance-platform-bootstrap.md) | finance-platform Bootstrap Criteria (next domain after Phase 5 launch) | ACCEPTED | 2026-05-13 |
| [ADR-MONO-009](ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) | Chrome DevTools MCP Visual Regression Loop (OpenAI Harness gap #4, triple-snapshot LOOP UNTIL CLEAN) | PROPOSED | 2026-05-13 |
| [ADR-MONO-010](ADR-MONO-010-e2e-tag-taxonomy.md) | E2E Test Tag Taxonomy (`smoke` / `full`) and Gradle / CI Job Split — Phase 2 of e2e 3단계 전략 | ACCEPTED | 2026-05-13 |
| [ADR-MONO-011](ADR-MONO-011-nightly-full-e2e-cadence.md) | Nightly + Push-to-main Cadence for `@Tag("full")` E2E Suites — Phase 3 of e2e 3단계 전략 | ACCEPTED | 2026-05-13 |
| [ADR-MONO-012](ADR-MONO-012-cross-project-architecture-md-canonical-form.md) | Cross-Project `architecture.md` Canonical Form — Identity-table + Service Type Composition (refactor-spec Tier 3 reconsider) | ACCEPTED | 2026-05-15 |
| [ADR-MONO-012a](ADR-MONO-012a-cross-project-architecture-md-canonical-form-corrections.md) | ADR-MONO-012 forward correction — ecommerce 14 (not 13), hook cross-project (not WMS-only), Composition H3 always present (not dual-only). Option-C-1 → forward-ADR promotion (TASK-MONO-103). | ACCEPTED | 2026-05-15 |
| [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) | platform-console Foundation: Single-UI Console Model, New-Project Placement, admin-web Parity-Gated Retirement, Cross-Project Integration Contract | ACCEPTED | 2026-05-16 |
| [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) | platform-console Operator Authentication Model (GAP OIDC ↔ admin-service Operator Token Exchange) | ACCEPTED | 2026-05-16 |
| [ADR-MONO-015](ADR-MONO-015-platform-console-dashboards-model.md) | platform-console Dashboards Model (Composed Operator Overview, not Grafana Embed) | ACCEPTED | 2026-05-16 |
| [ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md) | erp-platform Bootstrap Criteria, Integration Mode, Classification, Procedure, Readiness | ACCEPTED | 2026-05-19 |
| [ADR-MONO-017](ADR-MONO-017-platform-console-bff-architecture.md) | platform-console-bff Architecture (Phase 7 Aggregation & Cross-Domain Dashboards) | ACCEPTED | 2026-05-20 |
| [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) | platform-console Phase 8 Federation Hardening Architecture (Cross-Product E2E + Observability Federation + Multi-Tenant Isolation Regression) | ACCEPTED | 2026-05-25 |
| [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) | platform-console Real Customer-Tenant Model (AWS IAM Identity Center-style tenant ↔ domain subscription, decoupling tenant from product/domain) | ACCEPTED | 2026-05-30 |
| [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) | Operator ↔ multi-customer assignment (AWS IAM Identity Center "user → multiple account assignments" parity; active-tenant token scoping) | ACCEPTED | 2026-05-31 |
| [ADR-MONO-021](ADR-MONO-021-account-type-claim-source.md) | `account_type` OIDC claim source (CONSUMER vs OPERATOR — per-account, denormalized on the credential) | SUPERSEDED | 2026-06-02 |
| [ADR-MONO-022](ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) | ecommerce ↔ wms Cross-Project Order-Fulfillment Integration | ACCEPTED | 2026-06-08 |
| [ADR-MONO-023](ADR-MONO-023-entitlement-iam-plane-separation.md) | Entitlement/Subscription Plane ↔ IAM Plane Separation (subscription lifecycle state machine; GCP billing↔IAM parity; entitlement suspension never mutates IAM bindings) | ACCEPTED | 2026-06-10 |
| [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) | Tenant-Admin Delegation (a tenant-scoped operator-management authority; a customer's own admin manages its operators/assignments within its tenant; strict no-escalation confinement; AWS Organizations "delegated administrator" / GCP project-IAM-admin parity) | ACCEPTED | 2026-06-10 |
| [ADR-MONO-025](ADR-MONO-025-abac-data-scope-generalization.md) | ABAC Data-Scope Generalization (`data_scope` as a cross-domain attribute-based data-scope claim) | ACCEPTED | 2026-06-11 |
| [ADR-MONO-026](ADR-MONO-026-role-grant-access-conditions.md) | Role-Grant Access Conditions (closed-enum condition clauses — the 2단계 of axis ②) | ACCEPTED | 2026-06-11 |
| [ADR-MONO-027](ADR-MONO-027-wms-scm-replenishment-loop.md) | wms → scm Stock-Replenishment Loop (low-stock → reorder suggestion) | ACCEPTED | 2026-06-11 |
| [ADR-MONO-028](ADR-MONO-028-time-window-access-condition.md) | `TIME_WINDOW` Access Condition (2nd condition type under ADR-026's closed enum) | ACCEPTED | 2026-06-11 |
| [ADR-MONO-029](ADR-MONO-029-resource-tag-access-condition.md) | `RESOURCE_TAG` Access Condition (3rd / final condition type under ADR-026's closed enum) | ACCEPTED | 2026-06-11 |
| [ADR-MONO-030](ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) | ecommerce Multi-Vendor Marketplace SaaS (join the platform federation as the 6th customer-tenant domain + an in-tenant seller axis) | ACCEPTED | 2026-06-12 |
| [ADR-MONO-031](ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md) | Consolidate the ecommerce operator UI into platform-console (sunset the standalone admin-dashboard) | ACCEPTED | 2026-06-13 |
| [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) | Unified identity model (single account → roles set; remove the `account_type` CONSUMER/OPERATOR partition) | ACCEPTED | 2026-06-14 |
| [ADR-MONO-033](ADR-MONO-033-roles-issuance-resolution-model.md) | Roles-issuance resolution model (where the `roles` claim's values come from at token-issue time; ADR-032 D5 step 2 mechanics) | ACCEPTED | 2026-06-14 |
| [ADR-MONO-034](ADR-MONO-034-account-credential-unification-model.md) | Account/credential unification model (one person → one central identity → consumer account + operator extension; ADR-MONO-032 D5 step 3 / D6-A mechanics) | ACCEPTED | 2026-06-14 |
| [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md) | Operator authentication unification + operator domain-role issuance (ADR-MONO-032 D5 step 4 mechanics; the deferred operator login/credential consolidation + the newly-found operator JWT-domain-role gap) | ACCEPTED | 2026-06-14 |
| [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) | Born-unified identity provisioning (ADR-MONO-032 D6-A realization for new records): mint the central identity at record birth; seed-rewrite the demo legacy; design — not build — the production cross-DB backfill | ACCEPTED | 2026-06-15 |
| [ADR-MONO-037](ADR-MONO-037-ecommerce-account-lifecycle-projection.md) | Project the IAM account lifecycle into ecommerce: re-point onboarding off the decommissioned `auth.user.signed-up` to IAM `account.created`, and wire the `account.deleted` two-phase (withdraw → GDPR-anonymize) reaction the existing IAM consumer contract already mandates | ACCEPTED | 2026-06-15 |
| [ADR-MONO-038](ADR-MONO-038-shared-idempotency-filter-abstraction.md) | Lift the REST Idempotency-Key filter skeleton, storage port, and stored-response DTO into `libs/java-web-servlet` as a configurable shared abstraction | ACCEPTED | 2026-06-15 |
| [ADR-MONO-039](ADR-MONO-039-e2e-cross-suite-shared-toolkit-decision.md) | Do NOT build a shared `e2e-toolkit` package; keep the three Playwright suites independent and govern the small cross-suite overlap by a documented login convention | ACCEPTED | 2026-06-16 |
| [ADR-MONO-040](ADR-MONO-040-oidc-subject-claim-account-id-contract-alignment.md) | Align the SAS OIDC access-token `sub` to the platform `jwt-standard-claims` contract (`sub` = account UUID), and unblock ecommerce consumer authed writes | ACCEPTED | 2026-06-17 |
| [ADR-MONO-041](ADR-MONO-041-container-image-build-standard.md) | Container image build standard: layered-jar extraction, a shared Java-service base image, and per-service build-context narrowing | ACCEPTED | 2026-06-17 |
| [ADR-MONO-042](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) | Ecommerce seller onboarding mints a real IAM seller-operator account: replace the "trusted token claim, no real account" seller with born-unified IAM provisioning (ADR-036 reuse) + seller-lifecycle deactivation, fail-soft | ACCEPTED | 2026-06-18 |
| [ADR-MONO-043](ADR-MONO-043-notification-architecture-unification.md) | Notification architecture unification: a shared notification contract + lifted consumer/delivery library + a console notification aggregator over the four per-domain notification-services | ACCEPTED | 2026-06-26 |
| [ADR-MONO-044](ADR-MONO-044-self-service-tenant-onboarding.md) | Self-Service B2B Tenant Onboarding (an authenticated visitor creates a NEW tenant and is appointed its first `TENANT_ADMIN` with zero platform-operator in the loop; AWS "create account → root" / GCP "create project → owner" parity) | ACCEPTED | 2026-07-04 |
| [ADR-MONO-045](ADR-MONO-045-cross-org-partner-delegation.md) | Cross-Org Partner Delegation (a first-class tenant↔tenant partnership that lets a partner organization operate a **bounded, attenuated, revocable-as-a-unit** slice of another tenant, with **relationship-scoped offboarding** — the first privilege origination that crosses the org boundary ADR-024 and ADR-044 both keep inside a single tenant) | ACCEPTED | 2026-07-04 |
| [ADR-MONO-046](ADR-MONO-046-operator-group-model.md) | Operator Group Model (a first-class grouping primitive for `admin_operators` that lets an admin assign roles / tenant-assignments to **many operators as a unit** — the workforce-grouping facet AWS IAM User Group, IdC Group, and Google Group all provide but the portfolio has never had) | ACCEPTED | 2026-07-08 |
| [ADR-MONO-047](ADR-MONO-047-org-node-tenant-hierarchy.md) | Org-Node Tenant Hierarchy (a grouping tree **above** `tenant` that lets one company own many isolated service-tenants, with a **deny-ceiling** entitlement guardrail inherited down the tree — the "회사 → 서비스 → 도메인" three-axis structure AWS Organizations OU→Account and GCP Folder→Project both provide but the portfolio's flat tenant registry never had) | ACCEPTED | 2026-07-10 |
| [ADR-MONO-048](ADR-MONO-048-shared-reactive-gateway-library.md) | `libs/java-gateway`: a shared **reactive** (WebFlux) gateway library, extracted from four copy-pasted edges whose divergence had already started costing security fixes | ACCEPTED | 2026-07-11 |
| [ADR-MONO-049](ADR-MONO-049-framework-neutral-security-library.md) | `libs/java-security`: a **framework-neutral** security library, because **49** hand-copied validators across **20 services** are only a few tests away from a fix that lands nowhere (§ 1.6 — the count was low three times; **ACCEPTED with scope A: D5 covers all 20**, § 1.7) | ACCEPTED | 2026-07-12 |
| [ADR-MONO-050](ADR-MONO-050-scm-procurement-wms-inbound-expected.md) | scm→wms **inbound-expected loop**: a confirmed replenishment PO pre-creates a wms inbound expectation (ASN), closing the leg ADR-027 left open — the first `scm → wms` coupling; warehouse-addressed for single **and** multi warehouse; §7 D9 reconciliation standardises cross-service identifiers on **codes** | ACCEPTED | 2026-07-19 |
| [ADR-MONO-051](ADR-MONO-051-master-data-stays-federated.md) | **Master data stays federated — no central MDM hub.** The repo already runs one (single owner per master, business **codes** at the seam, consumer-owned projections) but never declared it; 051 names it, generalises `050` §7 D9 repo-wide, accepts the supplier triplication **because nothing joins it yet**, and records the **tripwire** that reopens the decision (§D5) plus why a hub would break standalone extraction (§D6) | ACCEPTED | 2026-07-20 |
| [ADR-MONO-052](ADR-MONO-052-transport-context-map.md) | **Transport is scm's context; wms owns the dock, not the road.** Five capabilities were unowned or misplaced — the redistribution decision, the A→B leg + in-transit custody, 3PL execution, the TMS vendor connection, and `logistics-service` itself (named in **9** documents, built in none). 052 maps all five (§D2), draws the line at **custody** (goods on a vehicle are in no warehouse, and wms's per-warehouse quantity buckets cannot hold them — §D1), recasts the cross-warehouse guard as *boundary enforcement* rather than a v1 limitation (§D3), reuses the existing `outbound.shipping.confirmed` fact-event so the seam needs **no new contract** (§D5), declines an eighth `tms-platform` (§D6), leaves the TMS adapter in wms until there is a receiver (§D7), and records the **tripwire** that starts the bootstrap (§D8) | ACCEPTED | 2026-07-20 |
| [ADR-MONO-053](ADR-MONO-053-logistics-service-multimodal-fulfillment.md) | **logistics-service goes multimodal: carrier dispatch now, 3PL fulfillment designed-in.** The owner fires 052 §D8-2 by adopting **EasyPost** (real vendor, free sandbox) and elects the hybrid **carrier + 3PL multi-node fulfillment** 052 mapped but left unbuilt. 053 does **not** supersede 052 — it is 052 §D8 producing *work*. It bootstraps `logistics-service` as a service inside scm (not a `tms-platform`, §D9), puts multi-vendor dispatch behind one `ShipmentDispatchPort` over the existing `outbound.shipping.confirmed` seam (no new contract, §D2), adds a `CarrierRouter` (§D3) and a `FulfillmentRouter` **seam with only the self-branch wired** so 3PL slots in additively (§D4), designs the 3PL path for **Phase 2** on the D8-3 firing (§D5), retires the wms TMS interim once the receiver is live (§D8), and keeps 052's *"premature"* tension explicit as an owner override (§1.4). Carrier-first, 3PL-second, tracking-optional (§D7). | ACCEPTED | 2026-07-24 |
| [ADR-MONO-054](ADR-MONO-054-third-party-logistics-node-activation.md) | **Activating `THIRD_PARTY_LOGISTICS`: the 3PL node is observed and its inbound half is honoured; the outbound routing decision still has no owner.** The owner fires 052 §D8-3 (a 3PL destination honoured, not DLT'd). 054 does **not** supersede 053 — it is 053 §D5/§D7 producing *Phase-2 work*, and it **re-measures** 053's deferred 052 §D2① analogy as its own §D5 mandate required. The re-measurement **fails**: the `outbound.shipping.confirmed` seam is **self-fulfillment-only** (a 3PL order never emits it) and `demand-planning-service` is **replenishment-only** (no order/allocation surface), so "둘 다" is **not** additively reachable for the outbound path. 054 splits the two 3PL surfaces: **inbound-to-3PL** (the literal D8-3) is buildable now — activate the node factory (no Flyway), observe 3PL stock **read-only** (§D4), and **route** a 3PL-destined inbound-expected away from wms rather than widen the correct wms DLT gate (§D3); **outbound-from-3PL** (§D5/§D7) is **deferred on a named missing owner** — an order-to-node allocation decision upstream of wms (ADR-022 territory), not demand-planning. Corrects two 053 claims via the amendment-section pattern (§D6). Still a service inside scm, still no `tms-platform` (§D8). | PROPOSED | 2026-07-24 |

Every column above is read from the ADR files themselves and enforced by
`scripts/check-adr-index-drift.sh` — every ADR has a row, every row has an ADR, and each
row's `Status` and `Date` equal that ADR's own header. **The file is the authority: if a
row and its ADR disagree, the row is wrong.**

`Date` is **the date the ADR was proposed** (see § Authoring Convention), so it does not
move when an ADR is accepted. To find out *when* a decision took effect, open the ADR —
its `History` line, or the annotations on its own `Date` line, carry the transitions.
The index deliberately does not restate them: those live in the files in half a dozen
different prose shapes, and a guard that had to parse them would be a false-positive
generator rather than a guard — and a guard that is red on day one gets switched off,
which is worse than no guard, because a skipped job reports green. `Title` is likewise
unguarded: the titles here are curated summaries, not copies of the ADRs' H1 lines.

---

## Authoring Convention

- File name: `ADR-MONO-NNN-short-kebab-title.md`
- Required header fields: `Status`, `Date`, `Decision driver`, `Supersedes`, `Related`
- **`Status`** — the ADR's current state. Lifecycle: `PROPOSED → ACCEPTED | DEFERRED | REJECTED | SUPERSEDED`.
  `PROPOSED → ACCEPTED` is a **human gate**: an agent may not accept its own ADR.
- **`Date`** — **the date this decision record was authored, i.e. the date it was proposed.**
  It is *not* the date the ADR was accepted, and it does not move when the `Status` does.
  This was never written down until TASK-MONO-369, and the omission cost: 12 ADRs shipped
  with no `Date` at all, and the three where the two readings are distinguishable
  (`008`, `018`, `019`) recorded the proposed date while the index recorded the accepted one.
  Everywhere else PROPOSED and ACCEPTED landed on the same day, so nobody could see the split.
- **`History`** (optional but recommended) — the `PROPOSED → ACCEPTED` transitions, with dates.
  Older ADRs instead annotate their transitions inline on the `Date` line; both are accepted,
  and neither is machine-parsed. **Put the transition dates somewhere** — for `040` and `041`
  they are in neither place, so when those decisions took effect is now unrecoverable from the file.
- Sections: `Context`, `Decision` (numbered `D1, D2, …`), `Alternatives Considered`, `Consequences`, `Verification`, `Outstanding follow-ups`
- Reference: `platform/architecture-decision-rule.md`
