# ADR-MONO-031 — Consolidate the ecommerce operator UI into platform-console (sunset the standalone admin-dashboard)

**Status:** ACCEPTED
**Date:** 2026-06-13 (PROPOSED 2026-06-13 · ACCEPTED 2026-06-13, same-session user-explicit "ACCEPTED 진행" intent on the D1–D7 decisions as reviewed; the three execution forks — sunset-not-dual, all-6-areas, product/order-first-then-staged — were pre-fixed via AskUserQuestion. **NOT self-ACCEPT** — the user directed the transition; sunsetting a published portfolio app + amending ADR-MONO-030 D8 is a genuine, low-reversibility architecture decision, recorded before execution. Execution begins under TASK-MONO-252.)
**Decision driver:** User request (2026-06-13) — *"이커머스 자체 어드민 앱을 없애고 통합 콘솔로 흡수시키고 싶어"*. Investigation surfaced that ecommerce operator UI exists in **two** places: (1) the standalone `admin-dashboard` Next.js app (17 pages, 6 management areas, 27 backend admin endpoints — products/orders/users/promotions/shippings/notifications + dashboard), and (2) the `platform-console` `/ecommerce` section, which today renders only the domain-health card + a "상세 운영 표면 준비중" placeholder (TASK-MONO-240/241, ADR-MONO-030 Step 4 facet a / a-후속). The user chose, via AskUserQuestion (2026-06-13), to **eliminate the standalone admin-dashboard and unify all 6 areas' full CRUD into platform-console**, sequenced **product/order first** (already tenant-isolated) **then the remaining 4 areas alongside their backend multi-tenant migration**, with admin-dashboard deletion gated on full-area parity.

**Relationship to ADR-MONO-030:** This ADR **executes** [ADR-MONO-030](ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) **Step 4 facet a-후속-2** ("the rich operations surface (product/order/seller management)", named deferred in `ecommerce/page.tsx` and ADR-030 § 3.4) and **amends ADR-030 D8** (Standalone-publish degradation). ADR-030 D8 held that ecommerce's standalone single-store demo stays UI-intact; this ADR narrows that — the **operator** UI moves to the hub console only; the ecommerce standalone repo retains its **customer storefront (`web-store`) + backend**, but no operator console (D5 below).

**Relationship to ADR-MONO-013:** Reuses the **`admin-web` retirement gate** precedent ([ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) § 6: the IAM standalone `admin-web` is retired only **after** Phase-2 console parity is verified). admin-dashboard sunset is the **second application** of that "verify console parity, then retire the standalone admin app" pattern. Reuses ADR-MONO-017 D2.A (console-web calls a single domain directly; BFF is cross-domain-aggregation only — **no BFF write leg**) and the per-domain credential rule (non-IAM domain → IAM OIDC access token, `getDomainFacingToken()`, never `getOperatorToken()`).

> **ACCEPTED (2026-06-13).** Records the decision direction (D1–D7) + a zero-regression, parity-gated roadmap. Execution proceeds per the § 4 roadmap under TASK-MONO-252 (Phase 0: this ACCEPTED flip + spec binding). admin-dashboard deletion stays gated on verified console parity across all 6 areas (D7) — no app deletion in Phase 0–5.

---

## 1. Context

- **Two operator surfaces for one domain.** `admin-dashboard` (`projects/ecommerce-microservices-platform/apps/admin-dashboard/`) is a complete, tested Next.js operator app: 6 areas, 17 pages, its own OIDC client (`ecommerce-admin-dashboard-client`, scopes `openid profile email tenant.read ecommerce.operator`, `account_type=OPERATOR` gate), its own shared UI kit (DataTable/FilterBar/PageLayout/…). `platform-console` is the unified AWS/GCP-style operator console over the 6 live domains; ecommerce is its **6th** domain (ADR-030) but its `/ecommerce` section is a health card only.
- **ADR-030 already framed the fix.** ADR-030's own decision driver was "surface the ecommerce admin in platform-console." Its Step 4 names console integration as the deferred follow-up; facet a (catalog tile + health section) is DONE. The CRUD surface the user now wants **is facet a-후속-2** — this ADR is its execution record, plus the net-new decision ADR-030 did **not** make: *delete* the standalone admin-dashboard.
- **The binding constraint is backend tenant isolation.** ADR-030 Step 2/3 added `tenant_id` to **product-service + order-service only**; the other 11 services (incl. user/promotion/shipping/notification) are the named "in-migration" backlog (ADR-030 Step 4 / PROJECT.md § Out of Scope). The console is a **multi-tenant** operator surface (ADR-019/020); absorbing a not-yet-isolated backend would let a tenant-scoped operator see cross-tenant rows (ADR-030 M1/M6 violation). Therefore CRUD absorption is **gated, per area, on that area's backend `tenant_id` migration**.
- **Why an ADR (HARDSTOP-09).** Deleting a published portfolio app + amending ADR-030 D8 + moving an operator surface across a project boundary is a cross-cutting, low-reversibility architecture decision. Recorded before execution, staged PROPOSED→ACCEPTED per the ADR-019/030 sibling pattern.

---

## 2. Decision

### D1 — Unify vs keep both (dual)

| Option | Verdict |
|---|---|
| **A. Sunset admin-dashboard; all 6 areas' CRUD live in platform-console only** | **CHOSEN** (user, 2026-06-13) — one operator surface, one auth model, one shared UI kit; eliminates the drift/duplication of maintaining two operator frontends. |
| B. Keep admin-dashboard for standalone, also build console surface (dual) | Rejected by user — accepts standalone operator-UI loss (D5) in exchange for removing duplication. |

### D2 — Absorption mechanism: console-web direct domain call vs new BFF write leg

| Option | Verdict |
|---|---|
| **A. console-web Next.js Route Handlers call the ecommerce gateway directly (read via server components / route handlers, write via route handlers); BFF unchanged** | **CHOSEN** — matches ADR-017 D2.A (single-domain call bypasses BFF; BFF is cross-domain aggregation only). The console already has the exact precedent: `wms-outbound-ops` (ship/pick/pack POST route handlers), `accounts` lock/unlock, `erp` approval transitions, `ledger` discrepancy-resolve. The BFF has **zero** write handlers today and gains none. |
| B. Add POST/PATCH/DELETE legs to console-bff | Rejected — violates ADR-017 D2.A; the BFF write surface is deliberately empty. |

### D3 — Auth: reuse console operator credential vs port admin-dashboard's OIDC client

| Option | Verdict |
|---|---|
| **A. Reuse the console's per-domain credential rule — domain-facing IAM OIDC access token (`getDomainFacingToken()`), `tenant_id ∈ {ecommerce,*}` JWT claim; the console's existing OIDC client + `ecommerce.operator` scope added to it** | **CHOSEN** — identical to how wms/scm/finance/erp sections authenticate (console-integration-contract § 2.4.5–2.4.8). The standalone `ecommerce-admin-dashboard-client` OIDC client is retired with the app (D7). Tenant scope is enforced by the **signed JWT claim** (ADR-019 D5), never a bare `X-Tenant-Id` header. |
| B. Port admin-dashboard's NextAuth/OIDC client into the console | Rejected — a second auth model inside one console; contradicts the unified-surface goal. |

### D4 — Absorption order: area-by-area, gated on backend `tenant_id`

| Option | Verdict |
|---|---|
| **A. product + order first (already isolated → absorb now); then users → promotions → shippings → notifications, each preceded by its backend `tenant_id` migration (ADR-030 Step 4)** | **CHOSEN** (user, 2026-06-13) — matches ADR-030's slice-first, main-GREEN-throughout discipline. Each area lands independently mergeable with no cross-tenant leak. |
| B. Migrate all 6 backends' tenancy first, then absorb in one pass | Deferred-not-rejected — cleanest end state but largest batch + longest to first value. |
| C. Absorb all 6 now with default-tenant-only (no isolation) | Rejected — M1 violation; bakes a cross-tenant-leak surface into the multi-tenant console. |

### D5 — ADR-030 D8 amendment (standalone degradation, narrowed)

ADR-030 D8 kept ecommerce's standalone single-store demo fully UI-intact. This ADR **narrows** it: the ecommerce standalone repo retains its **customer storefront (`web-store`) + the 13 backend services** (still `docker-compose up`-runnable, still degrades to single-store via the default-tenant/default-seller seeds), but **no operator console** — the operator surface is hub-console-only. The portfolio consequence (standalone ecommerce shows no operator admin UI) is **explicitly accepted** by the user (AskUserQuestion, 2026-06-13). The portfolio-submission-strategy memory + ecommerce standalone README must note operator UI lives in the hub console.

### D6 — service_types / web-store unaffected

`admin-dashboard` and `web-store` are both `frontend-app`. Only `admin-dashboard` is removed; `web-store` (customer storefront) stays. ecommerce `PROJECT.md` `service_types: [..., frontend-app]` is **unchanged** (web-store keeps the type live). The `admin-dashboard` spec (`specs/services/admin-dashboard/architecture.md`) is marked **RETIRED** with a pointer to the console section, not deleted-without-trace.

### D7 — Deletion gate (parity-first, mirrors ADR-013 § 6)

admin-dashboard is **not deleted until all 6 areas are absorbed and parity-verified** in the console. Until then both surfaces coexist (transitional dual). The retirement task is the final phase: delete `apps/admin-dashboard/`, retire its OIDC client + spec, fix the backend gap (`TemplateController` has no single-template GET — see roadmap Phase 6), and update the portfolio sync exclusions.

---

## 3. Consequences

- **One operator surface, one auth model, one UI kit** — removes the maintenance drift of two ecommerce operator frontends.
- **Portfolio standalone loses its operator UI** (D5) — accepted trade-off; the hub console (`monorepo-lab`) becomes the only place to see ecommerce operations. Mitigated by README notes.
- **Absorption is gated on backend tenancy** (D4) — users/promotions/shippings/notifications cannot be safely absorbed until their `tenant_id` migration lands; this couples Phase 2–5 to ADR-030 Step 4's 11-service backlog (sequenced, not blocked).
- **Backend gap surfaced** — `notification-service` `TemplateController` exposes no `GET /templates/{id}` though admin-dashboard's edit page calls it; must be added (or the console edit page derived from the list) during notifications absorption.
- **Reversibility: low** — once admin-dashboard is deleted, restoring the standalone operator UI means rebuilding it. This is why D7 gates deletion on verified console parity.

---

## 4. Post-ACCEPTED execution roadmap (sketch; finalised at ACCEPTED)

0. **Doc** — this ADR PROPOSED→ACCEPTED (user-gated) + spec updates: `platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 ecommerce-ops binding; `console-web/architecture.md` ecommerce-ops phase note; ecommerce `admin-dashboard` spec → RETIRED marker (deferred to Phase 6). Model = **Opus**.
1. **product + order absorption** (platform-console-internal) — `features/ecommerce-ops` (products + orders), route handlers (`app/api/ecommerce/products|orders/**`), pages (`app/(console)/ecommerce/products|orders`), `ConsoleSidebarNav` ecommerce group, `ecommerce.operator` scope. Reuses wms-outbound-ops pattern. Model = **Opus** (first slice/contract) → **Sonnet** (mechanical replicas).
2–5. **users → promotions → shippings → notifications** — each = (a) ecommerce-internal backend `tenant_id` migration (ADR-030 Step 4 / multi-tenant M1-M7; Model **Opus**) **then** (b) console absorption of that area (Model **Sonnet**). Notifications phase also fixes the `TemplateController` GET gap.
6. **admin-dashboard retirement** (cross-project) — delete `apps/admin-dashboard/`, retire OIDC client + spec marker, portfolio `sync-portfolio.sh` exclusion + README notes (D5). Model = **Sonnet**.

Each phase is independently main-GREEN, parity-gated (D7), and BE-303-compliant (0 failing required checks at merge).
