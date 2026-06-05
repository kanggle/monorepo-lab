# ADR-MONO-016 — erp-platform Bootstrap Criteria, Integration Mode, Classification, Procedure, Readiness

**Status:** ACCEPTED
**Date:** 2026-05-19
**History:** PROPOSED 2026-05-19 (TASK-MONO-117 — bootstrap criteria pre-authored; **NOT** the bootstrap authorisation). ACCEPTED transition deferred to a future task at user-explicit intent (§ D6.1), structurally identical to ADR-MONO-008's PROPOSED→ACCEPTED two-stage path (TASK-MONO-071 → TASK-MONO-113). · **ACCEPTED 2026-05-19 (TASK-MONO-118 — § D6.1 user-explicit intent "ADR-016 ACCEPTED" satisfied [exact form, not the excluded ambiguous form]; D5.1–D5.7 evaluated; D1 = Option C (Both) / D2 = `erp` `[internal-system, transactional, audit-heavy]` `[rest-api]` / D3 = `masterdata-service` finalized via AskUserQuestion; NOT self-ACCEPT — governed §D6 transition, ADR-MONO-008/TASK-MONO-113 + ADR-MONO-013/TASK-MONO-108 analog. Bootstrap artifact = PR-B / TASK-MONO-119, dependency-correct base = this PR-A merged main).**
**Decision driver:** finance-platform v1 fully closed **both sides** 2026-05-19 (monorepo behavioural-proof chain TASK-MONO-115 → FIN-BE-002 → FIN-BE-003 → FIN-BE-004, `finance-integration-tests` CI 12/12; standalone Template fork `kanggle/finance-platform` confirmed; TASK-MONO-116 append-only recording; **ADR-MONO-008 fully resolved**). ADR-MONO-002 § D4 ordering `scm → finance → erp → mes`: scm shipped 2026-05-04~07, finance closed 2026-05-19, **erp is next**. ADR-MONO-003a § D2.1 mandates a fresh ADR for any new-project bootstrap (resets the shared-library churn clock + shifts portfolio narrative scope). This ADR is that fresh ADR for erp.
**Supersedes:** none.
**Related:** [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) § D4 (ordering parent — `finance → erp` forward-pointer appended by TASK-MONO-117), [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D2.1 (fresh-ADR mandate — this ADR satisfies it) + § 3 audit-trail, [ADR-MONO-003b](ADR-MONO-003b-phase-5-launch-criteria.md) § 1.4 + § D3 (Template ↔ monorepo sync context), [ADR-MONO-008](ADR-MONO-008-finance-platform-bootstrap.md) (structural template — finance bootstrap, same § 1–§ 7 / D1–D6 shape + two-stage PROPOSED→ACCEPTED pattern), **[ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) (ACCEPTED, binding — Model B: unified `platform-console` is the only UI for the enterprise suite incl. "future erp" → erp is backend-only)** + [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) / [ADR-MONO-015](ADR-MONO-015-platform-console-dashboards-model.md) (console operator-auth / dashboards refinements — referenced, not modified), [`rules/taxonomy.md`](../../rules/taxonomy.md) § Domains (`erp` L75) + § Traits (11 traits), memory [`project_portfolio_7axis_architecture`](../../../memory/project_portfolio_7axis_architecture.md), memory [`project_platform_console_adr_013`](../../../memory/project_platform_console_adr_013.md) (GAP backend-only precedent).

> **Identifier note (collision correction).** Older agent-memory / ADR-MONO-002 § D4 / ADR-MONO-008 forward-pointer text called the erp bootstrap ADR "ADR-MONO-009 candidate". That is **stale**: `ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` already exists (OpenAI Harness gap #4, pre-existing PROPOSED, TASK-MONO-072). `ls docs/adr` objectively = ADR-MONO-001..015 contiguous → the erp bootstrap ADR's correct identifier is **ADR-MONO-016** (next free). This is a factual identifier correction only; the substance (erp bootstrap criteria) is unchanged.

---

## 1. Context

### 1.1 Ordering state

ADR-MONO-002 § D4 stress-axis ordering is `scm → finance → erp → mes`. scm passed `transactional + integration-heavy + batch-heavy` (2026-05-04~07). finance passed `transactional + regulated + audit-heavy` and is fully closed both sides (2026-05-19; ADR-MONO-008 fully resolved — behavioural-proof chain CI 12/12 + standalone Template fork confirmed + TASK-MONO-116 recording). erp is the next ordering step; mes is the deferred final (intentionally dropped per memory `project_portfolio_7axis_architecture` — not re-proposed here).

### 1.2 Why an ADR for the bootstrap

Per ADR-MONO-003a § D2.1, adding any new project skeleton under `projects/<name>/` is NOT an OVERRIDE-class change — bootstrap resets the `libs/` churn clock and shifts portfolio narrative scope. A fresh ADR is required. This is that ADR. The pre-author pattern is identical to ADR-MONO-008 (PROPOSED via TASK-MONO-071, ACCEPTED later via TASK-MONO-113) and ADR-MONO-003b (PROPOSED via TASK-MONO-069, ACCEPTED via TASK-MONO-070).

### 1.3 Why PROPOSED, not ACCEPTED

PROPOSED ≠ "we will bootstrap erp eventually". PROPOSED = "if we choose to bootstrap erp, here are the criteria the moment must satisfy". The user has not stated erp bootstrap intent in this session — the directive was explicitly *"draft the PROPOSED ADR"*. Authoring criteria before the decision is exactly the staged pattern that made ADR-MONO-003b and ADR-MONO-008 work cleanly. **self-ACCEPT is prohibited**: the dispatcher never unilaterally declares this ADR ACCEPTED; the ACCEPTED transition is a separate future task at user-explicit intent (§ D6.1).

> **[Reconcile — ACCEPTED 2026-05-19, TASK-MONO-118]** This section records why PROPOSED was correct at authoring time (preserved as history). The user subsequently stated the exact §D6.1 intent (`"ADR-016 ACCEPTED"`) and finalized D1/D3 via AskUserQuestion; the ACCEPTED transition was then performed by TASK-MONO-118 as the mechanical §D6 execution (NOT self-ACCEPT — the dispatcher still did not unilaterally decide). See § 6.

### 1.4 Scope: what "erp bootstrap" means

Three artifacts in sequence (ADR-MONO-008 § 1.4 analog):

1. `gh repo create kanggle/erp-platform --template kanggle/project-template --public --clone` produces a fresh standalone repo from the Template.
2. The clone is populated with: `PROJECT.md` (domain + traits + service_types), first service skeleton, first task in `tasks/ready/`.
3. (D1 choice) Monorepo `direct-include` registration.

ACCEPTED transition produces 1 + 2 minimum; 3 is the D1 integration-mode choice. **None of this happens under this PROPOSED ADR** — this document is criteria only.

### 1.5 Binding constraint — ADR-MONO-013 (ACCEPTED): erp is backend-only

ADR-MONO-013 (ACCEPTED, Model B) establishes the unified `platform-console` as the **only UI** for the portfolio's enterprise suite, explicitly enumerated as "gap · scm · wms + **future erp** · finance". Consequently the older 7-axis framing of erp v1 as carrying its own "admin SPA (대시보드/결재함/마스터관리/통합조회)" is **superseded**: erp ships **backend-only** services; its operator UI is a `platform-console` parity slice (the same outcome ADR-MONO-013 § D2/§ 3.3 produced for GAP, which had its `frontend-app` service_type removed — memory `project_platform_console_adr_013`). This ADR is a **binding consumer** of ADR-MONO-013/014/015: it references and complies with them, and does not modify them. Contradicting an ACCEPTED ADR would be a HARDSTOP-04-class conflict.

---

## 2. Decision

### D1 — Integration mode

Three options. Choice deferred to ACCEPTED transition (ADR-MONO-008 D1 analog).

| Option | Pros | Cons | Portfolio narrative |
|---|---|---|---|
| **A. Standalone-only** (Template fork, no monorepo entry) | Cleanest "Use this template" demo. Zero monorepo churn. No D2.1 churn-clock reset. | No monorepo CI / cross-project refactor benefits. Library updates manual back-port (ADR-003b § D3.5). | A second independent Template-flow demonstration. |
| **B. Monorepo-only** (`projects/erp-platform/` direct-include, no standalone) | Full monorepo benefits (CI, refactor atomicity, libs/ sync). | Skips the Template-flow demo. D2.1 churn-clock reset. | Continuation of the scm model. |
| **C. Both** (Template fork + monorepo direct-include, à la finance/ecommerce/GAP) | Template demo + monorepo benefits. The "external prototype re-integration" pattern. | Double-bookkeeping (sync needed). Most complex artifact. | Strongest signal: Discovery → Distribution → Re-integration repeated a second time (after finance). |

**Default recommendation at ACCEPTED**: Option C (both) — consistent with the finance precedent (ADR-MONO-008 D1 = C). ACCEPTED moment may pick A or B on user judgement.

### D2 — Project classification

**Recommended primary domain**: `erp` (`rules/taxonomy.md` L75 — "Enterprise Resource Planning. 회계·구매·재고·HR을 통합 관리하는 기간계 시스템"). The taxonomy `erp` definition is broad (GL/AP/AR/Procurement/Inventory/HR). The portfolio's erp v1 is **deliberately narrower** per the 7-axis responsibility boundary (memory `project_portfolio_7axis_architecture`): erp owns **master data (부서/직원/직급/비용센터/거래처) + approval workflow + integrated read model** and **NO domain business logic** (procurement/inventory/order remain owned by scm/wms/ecommerce). This domain-vs-scope reconciliation is the exact analog of ADR-MONO-008 picking `fintech` while the ADR (not the 7-axis "분개/GL/AP" framing) is the SoT and ledger depth is v2.

**Trait stack (proposed; finalised at ACCEPTED transition — NOT in PROPOSED)**, verified against `rules/taxonomy.md` § Traits (the **11** traits — `transactional`, `regulated`, `data-intensive`, `real-time`, `read-heavy`, `integration-heavy`, `internal-system`, `multi-tenant`, `audit-heavy`, `batch-heavy`, `content-heavy`):

- **`internal-system`** (L308 — 내부 임직원 전용, 외부 공개 트래픽 없음; activates SSO/권한 매트릭스/감사 로그/외부 노출 금지/내부망 제약) — the **core** trait; erp is the archetypal internal enterprise system. This makes erp the **first `internal-system`-primary portfolio project** (new stress axis vs scm's `transactional+integration-heavy+batch-heavy`, finance's `transactional+regulated+audit-heavy`, fan's saas/`multi-tenant`).
- **`transactional`** (L278) — approval-workflow state transitions + master-data mutations are inherently transactional.
- **`audit-heavy`** (L318) — enterprise governance: "누가 무엇을 바꿨는가" for master data + approval is responsibility-critical.
- *Optional at ACCEPTED, decide then*: `integration-heavy` — the integrated read model consumes many services' events; **caveat**: taxonomy's `integration-heavy` definition (L304) emphasises **external** vendor integration (PG/통신사/배송사 3+), whereas erp's "통합" is **internal cross-service event consumption** — likely a loose fit, lean toward NOT declaring it and expressing read-model integration at the architecture level instead. `read-heavy` similarly optional (CQRS read side).

**Explicit lesson (ADR-MONO-008 D5.2 / HARDSTOP-02)**: ADR-MONO-002 § D4 describes erp's new stress as "workflow-heavy + multi-module integration". **"workflow-heavy" is NOT one of the 11 taxonomy traits** — it is descriptive only. Declaring a non-catalog tag in `PROJECT.md` would HARDSTOP-02 at bootstrap (exactly how ADR-MONO-008 had to exclude "optional event-driven"). The bootstrap must declare only catalog traits and express workflow/integration emphasis at the architecture/scope level.

**service_types**: `rest-api` minimum (+ `event-consumer` if the integrated read model subscribes to domain events in v1). **`frontend-app` is excluded** — per § 1.5 / ADR-MONO-013 binding, erp is backend-only and its UI is a `platform-console` parity slice. (Same outcome as GAP per ADR-013 § D2/§ 3.3.)

### D3 — Initial service skeleton scope

One service for v1. Two candidates:

| Candidate | Responsibility | Architecture | Stress axis |
|---|---|---|---|
| **`masterdata-service`** | 부서 계층 / 직원(조직 속성) / 직급 / 비용센터 / 거래처 마스터 (CRUD + 무결성) | Hexagonal | `internal-system` + `transactional` (+ `audit-heavy` on change) |
| **`approval-service`** | 결재 워크플로우 (1~2단계 라우팅, 상태기계, 결재함) | Hexagonal | `internal-system` + `transactional` + `audit-heavy` + a workflow state machine (harder) |

**Default recommendation**: `masterdata-service` — simpler v1 scope; master data is the foundation the approval workflow + integrated read model build on. `approval-service` deferred to v2 (after the rule library digests `internal-system` at smaller scope first — same staging logic as ADR-MONO-008 deferring `ledger-service` to v2).

> **[Amendment — TASK-ERP-BE-007, 2026-06-04]** `read-model-service` (forward-declared here as v2, and named the inbound consumer by `masterdata-service/architecture.md` § Dependencies + erp-masterdata-events.md § Consumer rules) is **partially executed** as a **first increment** — building the previously-empty consumer side of the master-data change-propagation loop (masterdata already publishes `erp.masterdata.*.changed.v1` from v1; "v1 consumers = none" was the gap). The increment is a single Hexagonal deployable, `rest-api` + `event-consumer` (scm `inventory-visibility-service` dual-type precedent), that consumes 4 masterdata topics (department/employee/jobgrade/costcenter), maintains MySQL projection tables with `processed_events` dedupe (T8), and serves a read-only **employee org-view** (employee + department-path + cost-center + job-grade). This is **additive** and does **not** reopen the D3 decision: it executes the already-recorded forward-declaration; no new architecture decision is introduced (HARDSTOP-09 is satisfied by the new `read-model-service/architecture.md` authored in the same PR). It holds **no domain business logic** (erp.md E5 — this service is E5's reference implementation: read-only projection, single source of record = masterdata-service, no re-emission, no write-back). The project frontmatter `service_types` gains `event-consumer` (the conditional in § D2 — "+ `event-consumer` if the integrated read model subscribes to domain events" — is now met). `approval-service` / `permission-service` / `notification-service` / `admin-service` + the **full** integrated view (business-partner, approval/permission facts, per-operator `org_scope` read filter) stay v2-deferred. Spec: `read-model-service/architecture.md` + `read-model-api.md` + `read-model-subscriptions.md`.

> **[Amendment — TASK-ERP-BE-009, 2026-06-05]** `approval-service` (forward-declared here as the harder v2 candidate — "결재 워크플로우 (1~2단계 라우팅, 상태기계, 결재함)") is **partially executed** as a **first increment**, mirroring the TASK-ERP-BE-007 read-model amendment pattern. The rule library has now digested `internal-system` at smaller scope (masterdata-service v1 + read-model-service first increment both live), satisfying the § D3 "default recommendation" staging condition ("approval-service deferred to v2 after the rule library digests internal-system at smaller scope first"). The increment is a single Hexagonal `rest-api` deployable (`apps/approval-service/`, package `com.example.erp.approval`) implementing the **core approval state machine** — `ApprovalRequest` aggregate with **DRAFT → SUBMITTED → APPROVED \| REJECTED \| WITHDRAWN** (erp.md E3 Ubiquitous Language; single-stage route), authorized-approver enforcement with no self-approval (E3/E6 + internal-system I4 Separation-of-Duties), idempotent transitions (E4 + transactional T-rules), immutable audit atomic with each transition (E2/E4/E8 + audit-heavy A2/A3/A7), cross-service reference-integrity check of the approval subject against `masterdata-service` REST (E1), and transactional-outbox event publication (`erp.approval.{submitted,approved,rejected,withdrawn}.v1` — forward consumers notification-service / full read-model = v2, NONE in this increment). This is **additive** and does **not** reopen the D3 decision: it executes the already-recorded forward-declaration; no new architecture decision is introduced (HARDSTOP-09 satisfied by the new `approval-service/architecture.md` + `approval-api.md` + `erp-approval-events.md` authored in the same spec PR). Unlike read-model-service (E5 read-only), approval-service holds **real domain logic** (a workflow state machine) — it is the first erp service exercising erp.md E3/E4. The project frontmatter `service_types` is unchanged (`rest-api` already present; approval **publishes** via outbox but does not **consume** in this increment, so `event-consumer` is not newly required by it). **v2-deferred (named, not designed here)**: multi-stage routing (1~N stages), 대결/위임 (delegation/substitution), the `IN_REVIEW` intermediate state, rich approval-inbox filtering, the console parity slice (separate platform-console PC-FE task), and read-model projection of approval facts. `permission-service` / `notification-service` / `admin-service` stay v2-deferred. Spec: `approval-service/architecture.md` + `approval-api.md` + `erp-approval-events.md`.

> **[Amendment — TASK-ERP-BE-010, 2026-06-05] read-model approval-fact projection.** The TASK-ERP-BE-007 read-model amendment deferred "the **full** integrated view (business-partner, approval/permission facts, per-operator `org_scope` read filter)" to v2. The **approval-facts** slice of that full view is now executed as an additive first increment: `read-model-service` consumes the four `erp.approval.{submitted,approved,rejected,withdrawn}.v1` events (TASK-ERP-BE-009's previously-consumer-less outbox — `erp-approval-events.md` "Consumers in this increment = none") into an `approval_fact_proj` projection, served read-only via `GET /api/erp/read-model/approvals` (list + detail). This **closes the `approval-service` → `read-model` event loop**, mirroring the masterdata → read-model loop. It does **not** reopen any decision: it executes the already-recorded integrated-view forward-declaration; no new architecture decision (HARDSTOP-09 satisfied by the read-model-service `architecture.md` amendment + `read-model-subscriptions.md`/`read-model-api.md` updates). It stays within **E5** — the projection holds only the *latest fact* (status + ids + timestamps + last reason), NOT the authoritative transition `history` (owned by `approval-service`); no approval business logic is reconstructed, no re-emission, no write-back. The `org_scope` subtree read-filter (TASK-ERP-BE-008) is applied to the approval-fact list (subject's department). **Still v2-deferred**: business-partner projection, permission facts, `notification-service` consumption, multi-stage/delegation approval facts. Spec: read-model `architecture.md` amendment + `read-model-subscriptions.md` + `read-model-api.md`.

> **[Amendment — TASK-ERP-BE-011, 2026-06-05] notification-service bootstrap (first increment).** `notification-service` (forward-declared as a v2 service in `PROJECT.md` Service Map + the forward-consumer notes of `erp-approval-events.md`/`erp-masterdata-events.md` — "fan-out approval-state / master-change notifications") is **partially executed** as a **first increment**, mirroring the read-model (BE-007) / approval (BE-009) first-increment pattern. A new single Hexagonal **dual-type** deployable (`apps/notification-service/`, package `com.example.erp.notification`; `event-consumer` primary + `rest-api` inbox read) consumes the four `erp.approval.{submitted,approved,rejected,withdrawn}.v1` events (closing the approval → notification forward-consumer leg), resolves the recipient (submitted→approver, approved/rejected→submitter, withdrawn→approver), renders + persists an in-app `Notification`, and serves a recipient-scoped inbox (`GET /api/erp/notifications` + `POST /{id}/read`). Resilience = **ADR-MONO-005 Category C** (single-step deliver with retry+DLT; notification-service is that ADR's named reference); dedupe via `processed_events` (T8); `@RetryableTopic` 3-retry+DLT + invalid→immediate DLT. It is a **terminal consumer** — it publishes **no** events (no outbox; `OutboxAutoConfiguration` excluded; erp.md § Internal Event Catalog has no `erp.notification.*` producer topic), so it does not reopen E5's no-re-emission spirit. It is **NOT** an E5 read-model (it holds recipient-resolution + render logic) but holds **no domain business logic** (no approval/master state machine). This is **additive** and does not reopen D3 — it executes the recorded forward-declaration; no new architecture decision (HARDSTOP-09 satisfied by the new `notification-service/architecture.md` + `notification-subscriptions.md` + `notification-api.md`; `NOTIFICATION_NOT_FOUND` registered in `platform/error-handling.md`). The project frontmatter `service_types` is unchanged (`event-consumer` + `rest-api` both already present). **v2-deferred (named)**: external channels (Slack/SMTP/push) + the exercised Category C retry scheduler, masterdata-change notifications, permission-change notifications, delegation notifications, notification preferences/routing, digest/batching, and the console notification-bell parity slice (separate platform-console PC-FE task). Spec: `notification-service/architecture.md` + `notification-subscriptions.md` + `notification-api.md`.

> **[Amendment — TASK-ERP-BE-012, 2026-06-05] approval-service v2.0 — multi-stage routing + `IN_REVIEW` (SECOND increment of the same § D3 forward-declaration).** The TASK-ERP-BE-009 amendment deferred "multi-stage routing (1~N stages), 대결/위임 (delegation/substitution), the `IN_REVIEW` intermediate state". The **multi-stage + `IN_REVIEW`** half is now executed as an **additive, backward-compatible second increment** (the v1.0 single-stage path becomes the N=1 special case — regression-gated). The `ApprovalRoute` becomes an ordered **1~N stage** list; `ApprovalStatus` gains the non-terminal **`IN_REVIEW`**; `approve` from `SUBMITTED|IN_REVIEW` advances to `APPROVED` if the current stage is the last, else to `IN_REVIEW` (next stage pending), with **per-stage approver authorization** (only the current stage's approver may act — sequential order enforced); `reject` (any stage) → `REJECTED`, `withdraw` (submitter) → `WITHDRAWN`. A Flyway migration **backfills every existing request as a 1-stage route** (new `approval_route_stage` table + `approval_request.current_stage_index`/`total_stages`). **Event contract stays consumer-compatible**: `erp.approval.approved.v1` fires **only on the final-stage approval** (intermediate stage advances write audit but emit no bus event — the next approver is surfaced by the inbox), preserving the terminal-once contract, and payloads gain additive `currentStage`/`totalStages` fields ignored by the existing `notification-service` (BE-011) / `read-model-service` (BE-010) consumers — **both consumers are UNCHANGED**. This is **additive** and does **not** reopen D3 — it executes the already-recorded forward-declaration; no new architecture decision (HARDSTOP-09 satisfied by the `approval-service/architecture.md` § v2.0 amendment + the additive `approval-api.md` / `erp-approval-events.md` updates authored in the same spec PR). **Still v2.1-deferred (named, not designed here)**: **대결/위임 (delegation/substitution)** + the `erp.approval.delegated` event (a distinct authority-delegation model layered on this multi-stage base — the immediate next increment), event-driven fan-out of stage advances (notify the next stage's approver), and rich inbox filtering. Spec: `approval-service/architecture.md` § v2.0 amendment + `approval-api.md` + `erp-approval-events.md`.

### D3.1 — UI: platform-console parity slice (binding, not an open choice)

erp does **not** ship a frontend. Its operator UI is a `platform-console` parity slice, conforming to ADR-MONO-013 § 3 / § D7.4 parity-checklist and ADR-MONO-015's composed-operator-overview model (not Grafana). The erp parity rows (masterdata browse, approval inbox, integrated read-model views) are added to the platform-console parity matrix at erp bootstrap time, gated by the same parity discipline as gap/scm/wms. This is recorded as a **decision** (not a D-option) because ADR-MONO-013 is ACCEPTED and binding; the alternative ("erp ships its own admin SPA") is in § 4 as Rejected.

> **[Amendment — TASK-PC-FE-046, 2026-06-04]** The § D3.1 parity slice was originally **read-only** ("masterdata browse"). The **department** master is hereby promoted to a console **write** parity slice — a **pilot** (create / update / retire / move-parent) that consumes the *existing* `masterdata-service` department mutation endpoints. This does **not** introduce a new `admin-service` (those, plus `approval-service` / `read-model-service`, stay v2-deferred per § D3) and does **not** contradict § 1.5 / ADR-MONO-013: the console remains the **only UI**, the write *affordance* lives in the console while the write *logic* (idempotency, E1 reference integrity, E6 fail-closed authz, E8 audit) stays in erp's backend. The amendment is therefore **additive**. The remaining four masters stay read-only pending follow-up tasks. The normative mutation matrix (credential = unchanged GAP OIDC domain-facing token, `Idempotency-Key` on all four, `reason` body only where the producer has a slot) lives in `platform-console/specs/contracts/console-integration-contract.md` § 2.4.8 *Department write binding (PILOT)*. The "console = read-only observability" framing is thereby relaxed **for the department master only**.

> **[Amendment — TASK-PC-FE-048, 2026-06-04]** The department write pilot is **extended to all five masters** (employees / job-grades / cost-centers / business-partners gain create / update / retire; department additionally has move-parent). Same posture as the pilot — write *affordance* in the console, write *logic* (idempotency, reference integrity, E6 fail-closed authz, E8 audit) in erp's backend (no new `admin-service`; v2 `approval-service` / `read-model-service` stay deferred). No backend change was needed: the `masterdata-service` mutation endpoints were already live and the write authz (the `erp.write` scope + `org_scope` gateway-enrichment, TASK-BE-336/337) is domain-scoped, covering every master. The "console = read-only observability" framing is now fully superseded for the erp masterdata surface. Matrix: `console-integration-contract.md` § 2.4.8 *Masterdata write binding*.

### D4 — Procedure

ADR-MONO-008 § D4 analog.

Pre-bootstrap:

1. `git status` clean in monorepo.
2. `bash scripts/verify-template-readiness.sh` — record exit code (informational; this ADR doesn't gate on it).
3. Finalise D1 / D2 / D3.

Bootstrap (Template flow):

4. `gh repo create kanggle/erp-platform --template kanggle/project-template --public --clone --description "ERP platform — erp domain (masterdata-service v1) bootstrapped from kanggle/project-template ADR-MONO-016 ACCEPTED <date>"` — **classifier-blocked outward-facing op**: hand off the exact command to the user's own shell; do NOT attempt it or work around the classifier (TASK-MONO-116 precedent). Run the command from a directory **outside** the monorepo to avoid the nested-clone artifact TASK-MONO-116 surfaced.
5. Populate `PROJECT.md` (D2), first service skeleton (D3, Hexagonal), `tasks/ready/TASK-ERP-BE-001-…`, `docker-compose.yml` (Traefik hostname `erp.local`), `.env.example`. Commit + push.

Monorepo integration (Options B / C): root `settings.gradle` include `projects:erp-platform:apps:masterdata-service`, root `package.json` shortcut, `scripts/sync-portfolio.sh` PROJECT_REMOTES (Option C only), `.github/workflows/ci.yml` erp per-project filter (mirror scm/finance, pure-positive — MONO-074/075 negation prohibition). A separate Testcontainers IT CI job is its own follow-up task (scm MONO-048 = finance MONO-115 pattern; do not assume the bootstrap ci.yml wires it).

Recording (append-only):

6. Append ACCEPTED row to this ADR § 6 (§ D6.3 format).
7. Append ADR-MONO-003a § 3 audit-trail row (category "New domain bootstrap", one-off — does NOT add to § D1; rows #15/#16/#17 finance precedent).
8. Append ADR-MONO-002 § D4 forward-pointer (`erp → mes` or "erp shipped; mes deferred-final").
9. Update memory `project_portfolio_7axis_architecture` + `project_monorepo_template_strategy` (erp bootstrapped; second downstream Template usage). External fork = classifier-blocked → if PENDING at PR-B, record honestly PENDING and resolve via a TASK-MONO-116-style append-only recording task once the user runs it (green-wash prohibited).

### D5 — Readiness criteria

Before ACCEPTED transition, the implementer evaluates:

| # | Criterion | Default verification |
|---|---|---|
| **D5.1** | `erp` domain in `rules/taxonomy.md` | grep `^#### erp` rules/taxonomy.md — confirmed present at L75. |
| **D5.2** | Trait stack finalised against the 11 traits | D2 stack reviewed vs `rules/taxonomy.md` § Traits; only catalog traits picked; "workflow-heavy" confirmed NON-trait (descriptive). |
| **D5.3** | Initial service decision finalised | D3 choice (`masterdata-service` / `approval-service` / other) made. |
| **D5.4** | Integration mode chosen (A / B / C) | D1 weighed; one option picked. |
| **D5.5** | User-explicit intent recorded | "ADR-016 ACCEPTED" / "erp 부트스트랩 시작" / equivalent affirmative direction (ambiguous "erp는 언제?" does NOT satisfy). |
| **D5.6** | Template repo unchanged since launch (informational) | `gh api repos/kanggle/project-template --jq .pushed_at`. |
| **D5.7** | ADR-MONO-013 binding re-confirmed | erp service_types exclude `frontend-app`; parity-slice plan referenced (§ D3.1); ADR-013/014/015 still ACCEPTED & unmodified. |

### D6 — ACCEPTED transition mechanics

#### D6.1 — User-explicit intent forms

Any of: "ADR-016 ACCEPTED" / "ADR-016 ACCEPTED 진행" / "erp 부트스트랩 시작" / "erp-platform 만들어" with affirmative direction context. Ambiguous statements ("erp는 언제?", "erp도 해야지") do NOT satisfy. **The dispatcher never self-declares ACCEPTED** — only an explicit user statement transitions this ADR.

#### D6.2 — Commit pattern

ACCEPTED transition + bootstrap typically produces 2 PRs (ADR-MONO-008 § D6.2 analog): **PR-A** (this ADR's status flip + recording: ADR-016 PROPOSED → ACCEPTED, ADR-002 § D4 footer, ADR-003a § 3 row, memory — doc-only) + **PR-B** (bootstrap artifact: new `kanggle/erp-platform` repo + monorepo `projects/erp-platform/` + first service skeleton + first task + `rules/domains/erp.md` on-demand domain rule if absent). Option A collapses PR-B to a monorepo PR with only the ADR row + memory.

#### D6.3 — Audit-trail row format (this ADR's § 6)

```
| YYYY-MM-DD | <Transition> | <Option> | <domain+traits+service_types> | <Standalone | Monorepo | Both> | <user-intent-quote> | <PR-A # / PR-B #> |
```

---

## 3. Consequences

### 3.1 PROPOSED merge (the TASK-MONO-117 chain)

- ADR-MONO-003a § D2.1's mandate satisfied — erp bootstrap now has its required fresh ADR (PROPOSED).
- No actual bootstrap. Portfolio state unchanged (5 active projects + finance).
- The future "should we bootstrap erp now?" question has a concrete § D5 checklist.
- The stale "ADR-MONO-009 candidate" forward references are corrected to ADR-MONO-016.

### 3.2 ACCEPTED moment (future)

- D5.1–D5.7 evaluated; D6.1 intent confirmed; D6.2 commit pattern followed.
- New `kanggle/erp-platform` repo via Template flow (D1 A/B/C).
- ADR-MONO-002 § D4 ordering progresses finance → **erp**.
- 7th project visible in monorepo (B/C) or portfolio hub (A/C).

### 3.3 Post-bootstrap

- Monorepo project count: 6 → 7 (B/C) or unchanged (A).
- Shared-library churn clock reset (settings.gradle + potential libs/ touch). Expected D2.1 consequence.
- `platform-console` gains erp parity rows (ADR-013 § 3 / § D7.4 discipline).
- erp = the portfolio's final domain; **mes remains intentionally dropped** (memory `project_portfolio_7axis_architecture` — not re-proposed).

### 3.4 Future-self / future-LLM-session

- A session evaluating "bootstrap erp now?" reads this ADR § D5 + § D4 + § D6.1 before mutating anything, and re-confirms ADR-MONO-013 binding (§ D5.7).

---

## 4. Alternatives Considered

### 4.1 Skip erp, jump to mes

ADR-MONO-002 § D4 ordering `scm → finance → erp → mes`. mes is the hardest (plant-floor real-time + safety) and intentionally deferred/dropped. Reversing skips intermediate `internal-system` validation. Rejected.

### 4.2 Bootstrap without an ADR

Rejected by ADR-MONO-003a § D2.1 explicit text. Bypassing would violate the meta-rule giving D4 OVERRIDE its consistency.

### 4.3 erp ships its own admin SPA (frontend-app)

Considered (the old 7-axis "admin SPA(대시보드/결재함/마스터관리/통합조회)" framing). **Rejected** — ADR-MONO-013 (ACCEPTED, Model B) makes `platform-console` the only UI for the enterprise suite incl. "future erp". erp is backend-only; its UI is a parity slice (§ D3.1). This is not a reopened decision — ADR-013 is binding; recorded here only to retire the stale framing.

### 4.4 Standalone-only / Monorepo-only as default

Same reasoning as ADR-MONO-008 § 4.3/4.4 — kept as D1 Option A/B, not default. Default = C (finance precedent).

### 4.5 ACCEPTED status now (skip PROPOSED)

Rejected — identical reasoning to ADR-MONO-003b § 4.6 / ADR-MONO-008 § 4.5. The user has not stated erp bootstrap intent in this session (the directive was "draft the PROPOSED ADR"). PROPOSED is the correct status for criteria authored before the decision. self-ACCEPT prohibited.

> **[Reconcile — ACCEPTED 2026-05-19, TASK-MONO-118]** "in this session" refers to the PROPOSED-authoring session (TASK-MONO-117). In a subsequent turn the user gave the exact §D6.1 intent (`"ADR-016 ACCEPTED"`) + AskUserQuestion D1/D3 finalization → the two-stage PROPOSED→ACCEPTED path completed as designed (this alternative correctly stayed rejected; ACCEPTED came via the proper §D6 governed transition, not a skip).

---

## 5. Relationship to prior ADRs

| Aspect | ADR-MONO-002 | ADR-MONO-008 | ADR-MONO-013 | ADR-MONO-016 (this) |
|---|---|---|---|---|
| Scope | Phase 4 catalyst + D4 ordering | Phase 6 first downstream (finance) | Unified console (Model B) | Phase 6 next domain (erp) |
| Status | ACCEPTED | ACCEPTED (fully resolved) | ACCEPTED (binding here) | **PROPOSED** |
| Key decision | scm catalyst + ordering | finance integration mode/classification | console = only UI; gap/erp backend-only | erp integration mode/classification/procedure/readiness |
| Audit trail | n/a | § 6 | § 6 | § 6 (analogous, append-only) |
| Forward-pointer | § D4 footer → ADR-MONO-008, then `finance → erp` (this PR) | § 5 row → ADR-MONO-016 (was stale "009") | n/a | n/a — gets one only if a post-erp ADR is filed (mes is dropped) |

**Practical reading order for a session evaluating "bootstrap erp now?"**: this ADR § 2 (D1–D6) → ADR-MONO-002 § D4 → ADR-MONO-013 § 3/§ D2/§ D7.4 (UI binding) → re-evaluate § D5.1–D5.7 → if pass and § D6.1 satisfied, follow § D4 + § D6.2.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Option | Classification | Standalone / Monorepo / Both | User intent quote | PR(s) |
|---|---|---|---|---|---|---|
| 2026-05-19 | created PROPOSED | TBD | TBD (proposed: `erp` / [internal-system, transactional, audit-heavy] / [rest-api, (event-consumer)]) | TBD | n/a (criteria pre-author via TASK-MONO-117; user directive = "draft the PROPOSED ADR", NOT bootstrap intent) | spec PR #615 (squash `eeb80039`) / impl PR #616 (squash `d189ffcc`) / close chore PR (this) — TASK-MONO-117 |
| 2026-05-19 | ACCEPTED | C (Both) | `erp` / [internal-system, transactional, audit-heavy] / [rest-api] | Both (Template fork `kanggle/erp-platform` + monorepo `projects/erp-platform/` direct-include) | "ADR-016 ACCEPTED" (exact § D6.1 intent form, not the excluded ambiguous form) + AskUserQuestion finalization D1 = Option C (Both) / D3 = `masterdata-service` (2026-05-19) | PR-A (TASK-MONO-118) #619 (squash `5beb04bc`) / PR-B (TASK-MONO-119) #620 (squash `9e13aabb`) bootstrap artifact / close chore PR (this) |
| 2026-05-19 | Option C standalone side CONFIRMED | C (Both) | (unchanged from ACCEPTED row) | Both — standalone CONFIRMED | 사용자 2026-05-19 셸 `gh repo create kanggle/erp-platform --template kanggle/project-template --public` 실행 (AskUserQuestion "외부 fork 선행" 연장선; dispatcher 독립 `gh repo view` 객관검증: templateRepository=kanggle/project-template, owner kanggle, PUBLIC, isTemplate false, createdAt 2026-05-19T10:01:11Z = 진짜 Template-derived fork). 2026-05-19 ACCEPTED row 의 "Both (Template fork…)" decision 의 standalone artifact 실생성 확정; 동반 stale-ref-prune 은 사용자 명령 경로오류로 미완(별 user-shell, 본 row 가 완료로 적지 않음 — green-wash 금지). 221/222 row + 224/226 주석 byte-unchanged (append-only). | impl PR (TASK-MONO-121) #628 (squash `e2ca7bde`) / close chore PR (this) |

(PROPOSED row appended 2026-05-19 per § D6.3 format. PR numbers backfilled at PR open / close chore — append-only, no rewrite. D2 trait stack excludes the descriptive "workflow-heavy" — not one of the 11 taxonomy traits, would HARDSTOP-02 at bootstrap; same lesson as ADR-MONO-008 D5.2's "optional event-driven" exclusion. ACCEPTED row appended only at the future user-intent transition — self-ACCEPT prohibited.)

(ACCEPTED row appended 2026-05-19 per § D6.3 — TASK-MONO-118 PR-A, the mechanical § D6 execution of the user's exact § D6.1 intent "ADR-016 ACCEPTED" + AskUserQuestion D1/D3 finalization. NOT self-ACCEPT: the dispatcher did not unilaterally decide. The created-PROPOSED row + the prior note are preserved byte-unchanged (append-only). D1–D6 decision bodies unchanged; only Status/History/§1.3/§4.5 reconciled to ACCEPTED tense per the ADR-MONO-013/TASK-MONO-108 + ADR-MONO-008/TASK-MONO-113 precedent. erp = the portfolio's final domain — mes intentionally dropped; no further bootstrap ADR expected.)

(Option C standalone-side CONFIRMED row appended 2026-05-19 per TASK-MONO-121 — the finance TASK-MONO-116 analog. The 2026-05-19 ACCEPTED row recorded `Both (Template fork…)` as the *decision*; the external `kanggle/erp-platform` repo's actual creation was the classifier-blocked user-shell hand-off, objectively re-verified by the dispatcher (`gh repo view` → templateRepository=`kanggle/project-template`, owner `kanggle`, PUBLIC, `isTemplate:false`, `createdAt 2026-05-19T10:01:11Z`). Reality-alignment of a completed PENDING hand-off — no new ADR (no competing convention; ADR-MONO-003a § 3 "no re-authorisation needed"). The created-PROPOSED row, the ACCEPTED row, and both prior notes are byte-unchanged (append-only). Honest caveat: the accompanying stale-remote-ref prune failed (user-shell path error) and is NOT recorded as done — separately re-handed; green-wash prohibited. Template downstream real usage now CONFIRMED twice: finance (TASK-MONO-116) + erp (this) — erp is the portfolio's final domain.)

---

## 7. Provenance

- ADR-MONO-002 § D4 — ordering origin (`scm → finance → erp → mes`).
- ADR-MONO-003a § D2.1 — explicit mandate for a fresh ADR on new-domain bootstrap. This ADR is that fresh ADR.
- ADR-MONO-008 — structural template (finance bootstrap; § 1–§ 7, D1–D6, two-stage PROPOSED→ACCEPTED). This ADR mirrors it.
- ADR-MONO-013/014/015 — binding UI constraint (Model B; erp backend-only, console parity slice). Referenced, not modified.
- `rules/taxonomy.md` § Domains (`erp` L75) + § Traits (11) — classification source; `internal-system` is the new stress axis.
- Memory `project_portfolio_7axis_architecture` — 7-axis architecture; erp = final domain, mes dropped. Memory `project_platform_console_adr_013` — GAP backend-only precedent.
- Identifier: ADR-MONO-016 (ADR-MONO-009 = Chrome DevTools MCP, pre-existing; 001..015 contiguous → 016 next free).

분석=Opus 4.7 / 구현=Opus 4.7 (meta-policy authoring — D1 integration mode + D2 erp classification [taxonomy `erp` 광의 vs 7축 경계 reconcile] + D3 first service + ADR-MONO-013 binding reconciliation + collision correction require interpretive judgement; structurally identical to ADR-MONO-008 / TASK-MONO-071 PROPOSED authoring path; dispatcher-direct, governance ADR not delegated — ADR-008/013/015 precedent).
