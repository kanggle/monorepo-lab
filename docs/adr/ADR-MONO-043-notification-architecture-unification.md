# ADR-MONO-043 — Notification architecture unification: a shared notification contract + lifted consumer/delivery library + a console notification aggregator over the four per-domain notification-services

**Status:** ACCEPTED

**Date:** 2026-06-26

**History:** PROPOSED 2026-06-26 (TASK-MONO-308 — records the **notification-architecture unification model**: how the four independently-bootstrapped per-domain notification-services [`erp`, `ecommerce`, `wms`, `fan`] — each a near-isolated silo with its own REST path convention, auth posture, schema, idempotency store, external-channel set, and UI consumer — converge onto (a) a shared domain-agnostic notification *contract*, (b) a lifted consumer/delivery *library* in `libs/`, and (c) a `console-bff` *aggregator* that fans the per-domain inboxes into the single shared-shell notification bell **without** coupling every console page to any one domain's availability. The convergence is **not** a mechanical refactor: lifting a notification envelope + consumer/dedupe/DLT/delivery machinery into shared code, defining a cross-domain inbox contract, and introducing a console aggregator each bake ownership/contract/failure-isolation postures → a task that did it without a record would HARDSTOP-09. This ADR records the decisions [D1–D8]. **Doc-only; ACCEPTED + implementation are separate user-explicit-intent-gated tasks [staged-child pattern, ADR-MONO-016/017/019/020/038]. Self-ACCEPT prohibited. Direction is CHOSEN-PROPOSED; finalised byte-unchanged at ACCEPTED.**) · ACCEPTED 2026-06-28 (TASK-MONO-311 — user-explicit *"ADR-043 ACCEPTED"* satisfying the D8 gate [the exact intent form, not an ambiguous "진행"]; the gate was honored — the PROPOSED D1–D8 record was presented + a recommendation given, and acceptance awaited before this flip, **NOT a self-ACCEPT**. D1–D8 CHOSEN-PROPOSED directions **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision + § 4 Alternatives + § 5 Relationship byte-identical to the PROPOSED draft; the flip = Status + this clause + the § 6 ACCEPTED row + § 3.3 PAUSED→UNPAUSED. Implementation (D7 P1–P3) is now unblocked as separate user-gated tasks — P1 = the shared contract + `libs/` library [D3/D4].)

**Builds on / Provenance:** 2026-06-26 live diagnosis + code investigation. The trigger was an operational incident — the platform-console header notification bell (`<NotificationBell />` in `projects/platform-console/apps/console-web/src/app/(console)/layout.tsx:110`) fired `GET /api/erp/notifications` on **every** console page (incl. `/wms/outbound`) and returned 503 because the `erp-gateway` was down. That exposed the architectural smell this ADR addresses: a **shared-shell** UI element hard-wired to a **single domain's** backend. A four-way topology sweep (one Explore pass per service) established the as-built fragmentation recorded in § 1.

**Related:**
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D3 — bootstrapped the erp notification-service as the *"notification first increment"*. This ADR is the cross-domain generalisation that *"first increment"* anticipated (additive forward-pointer only; ADR-016 § D3 byte-unchanged — HARDSTOP-04).
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` D4/D7 — the per-domain-credential + per-domain fan-out attribution model the console aggregator (D2) reuses verbatim (the aggregator is a dispatcher, never a credential-rewrite).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` — `libs/java-messaging` (outbox/`ProcessedEventJpaEntity`/envelope), the host module the lifted consumer-side machinery (D4) lands beside.
- `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` — direct precedent: lifting near-identical per-service machinery into a configurable shared abstraction is a recorded architecture decision, not a mechanical extraction. D4 follows its composition-over-inheritance + service-owned-strategy posture.
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` — the DLT / Category-C delivery taxonomy the lifted delivery-record machinery (D4) must preserve.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5/6/7/8 — per-domain credential rule the aggregator (D2/D6) must not weaken.

---

## 1. Context

### 1.1 As-built: four per-domain notification-services, four near-isolated silos (code-verified 2026-06-26)

Every domain bootstrapped its own notification-service. Each consumes **only its own domain's** events (one cross-platform edge), persists its own schema, exposes its own REST convention (or none), and is read by a **different** UI consumer:

```
 SERVICE     CONSUMES (topics)                         PRODUCER(S)              REST PATH                 UI CONSUMER              TERMINAL?          EXTERNAL CH.
 erp         erp.approval.{submitted,approved,         approval-service         /api/erp/notifications    platform-console        terminal           Slack (opt-in)
             rejected,withdrawn,delegated,                                                                  HEADER BELL
             delegation.revoked}.v1  (6)
 ecommerce   order.order.placed,                        order/payment/shipping   /api/notifications        web-store (customer     terminal           email (SMTP)
             payment.payment.completed,                 -service  +  iam/        (+ /templates admin)      inbox) + platform-
             shipping.shipping.status-changed,          account-service                                    console (TEMPLATE
             account.created  (4; 1 cross-platform)     (account.created)                                  admin only)
 wms         wms.inventory.{alert,adjusted},            inventory/inbound/       NONE (0 REST endpoints)   NONE                    RE-EMITS           Slack/none
             wms.inbound.{inspection.completed,         outbound-service                                                          wms.notification.
             asn.cancelled}, wms.outbound.                                                                                        delivered.v1
             {order.cancelled,shipping.confirmed}.v1 (6)                                                   (no downstream)
 fan         fan.membership.{activated,canceled,        membership-service       /api/fan/notifications    fan-platform-web         terminal           email + FCM push
             expired}.v1  (3)                           (+ external /api/v1/…)    (bell + inbox)
```

The divergences that make this a fragmented estate rather than one capability:

- **REST path convention** — three distinct prefixes (`/api/erp/notifications`, `/api/notifications`, `/api/fan/notifications`) plus wms with **no HTTP surface at all** (pure event→delivery engine).
- **Auth/recipient model** — erp & fan resolve recipient from the JWT `sub` (account UUID, ADR-MONO-040); ecommerce reads `X-User-Id` / `X-User-Role: ADMIN`; wms has no caller. No shared inbox auth shape.
- **Schema** — each owns a private `notification(s)` table with different columns; idempotency is `processed_events` (erp/fan) vs a service-local dedupe (ecommerce/wms); ecommerce adds `notification_templates` + `user_notification_preferences`; wms adds `notification_routing_rule` + its own `notification_outbox`.
- **Terminal vs producer** — erp/ecommerce/fan are terminal consumers (E5); **wms re-emits** `wms.notification.delivered.v1` (with no consumer found) and is a routing/delivery engine, not an inbox.
- **External channels** — Slack (erp), email (ecommerce/fan), FCM push (fan) — each wired independently, each opt-in/never-throw but with no shared adapter SPI.

### 1.2 The shared-shell coupling smell (the incident)

The console header bell is a **shared-shell** element (renders on every `(console)/*` route) but is wired to **exactly one** domain — erp. Consequences, all observed 2026-06-26:

- The bell shows **only erp approval notifications**. wms / ecommerce / fan notifications never reach it. ecommerce's console presence is a *template admin screen* (`/ecommerce/notifications/templates`), not the bell.
- Every console page (incl. WMS / SCM / finance screens) makes a hard call to the erp gateway just to render the header. When `erp-gateway` was down, **every page's bell 503'd**. It degrades gracefully (`throwOnError:false`) so it does not break the page — but a shared element depending on one domain's availability is the smell.

### 1.3 Why this is HARDSTOP-09 (architecture decision, not a refactor)

There is no mechanical move that fixes § 1.1/1.2. Unifying requires deciding: (a) whether to **merge** the four services or keep four behind a **shared contract** (they serve genuinely different audiences — operator approval inbox vs customer order/shipping alerts vs internal wms routing vs fan membership push — so a merge collapses incompatible semantics); (b) where a **shared notification envelope + inbox contract** lives and who owns it; (c) what consumer/dedupe/DLT/delivery machinery is **lifted into `libs/`** (an ownership + behavior-preservation decision, exactly the ADR-038 class); (d) how the console gets **one bell** without recoupling to a single upstream. Each is a posture worth recording. This ADR decides them; implementation is post-ACCEPTED.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; to be finalised (byte-unchanged) at ACCEPTED per the staged-child pattern. **No code / contract / schema change in this ADR.** Grounded in the 2026-06-26 four-service topology sweep recorded in § 1.

### D1 — Keep four per-domain notification-services; unify behind a shared *contract* + lifted *library*, do NOT merge into one platform service

The four services stay domain-owned (each keeps its consumers, schema, external channels, and home gateway). Unification is by **conformance to a shared notification contract** (D3) + **a shared consumer/delivery library** (D4), not by collapsing them into a single `platform-notification-service`. **Rejected:** *(a) single merged platform notification-service* — forces one schema/auth/SLA across operator-approval, customer-commerce, internal-wms, and fan-membership semantics; centralises a cross-domain failure blast-radius; violates the domain-ownership posture every other ADR (016/022/030) preserves. *(b) status quo (four silos)* — the divergence is already festering (wms re-emit-with-no-consumer, ecommerce-vs-others dedupe-store split, three path conventions) and the shared-shell coupling (§ 1.2) is unaddressed.

### D2 — One console bell via a `console-bff` notification **aggregator** that fans-in per-domain inboxes

The single shared-shell bell is fed by a new `console-bff` aggregator endpoint that calls each domain's inbox (per its own contract, D3) with that domain's own credential (D6), merges + sorts by recency, and returns a unified, per-domain-attributed feed. This reuses the ADR-MONO-017 D7 per-domain fan-out + D4 per-domain-credential model verbatim (the aggregator is a *dispatcher*, never a credential or data rewrite). **Rejected:** *(a) bell stays erp-only* — the § 1.2 coupling smell persists and 3 domains' notifications stay invisible to the console. *(b) a new central console-side notification store that ingests all domains* — duplicates domain-owned data, creates a second source of truth + its own dedupe/consistency problem, and re-introduces the merge that D1 rejected.

### D3 — A shared, domain-agnostic notification **envelope + inbox REST contract**

Define one notification envelope (`source_domain`, `type`, `title`, `body`, `deep_link`, `created_at`, `read` state, `id`) and one inbox REST shape (`GET <base>/notifications` paged + `?unread`, `GET <base>/notifications/{id}`, `POST <base>/notifications/{id}/read`) as a spec each domain conforms to. Domains keep their own base path + auth (D6) but the **shape** is shared, so the aggregator (D2) and any future client parse one model. **Rejected:** *(a) keep three path/shape conventions* — the aggregator must special-case each; no single notification model. *(b) impose one base path + one auth model on all four* — breaks the per-domain credential rule (ADR-017 / console-integration-contract § 2.4.5–8) and forces gateway-routing churn for net-zero benefit.

### D4 — Lift the common consumer/idempotency/DLT/delivery machinery into a shared library (configurable, composition-over-inheritance)

Lift the ~repeated machinery — envelope-validated Kafka consumer skeleton, dedupe/`processed_events` store port, DLT routing, the Category-C delivery record + retry (ADR-005), and an **external-channel adapter SPI** (Slack/email/FCM/SMS as pluggable strategies) — into `libs/` (beside `libs/java-messaging`, ADR-004). Each service registers it with its own config (topics, recipient-resolver, channel set, error envelope). Follows ADR-038's posture: shared *control flow + ports*, service-owned *strategies* (recipient resolution, error shape, channel wiring stay service-side). **Rejected:** *(a) each service keeps its own copy* — the drift in § 1.1 (dedupe-store split, wms-only re-emit, divergent channel wiring) keeps growing. *(b) a shared base class requiring per-service subclassing* — boilerplate subclass per service (ADR-038 I1 rationale).

### D5 — Failure isolation is a hard invariant of the aggregator: one domain down must not break the bell for the others

The aggregator (D2) calls each domain independently and **degrades per-domain** — a 503/timeout/network from one domain yields a partial feed (that domain marked degraded) while the others render. The shared-shell bell must **never** be coupled to a single domain's availability (the § 1.2 incident is the regression this invariant forbids). **Rejected:** *(a) single-upstream bell* — any one domain down → whole bell down (the status quo defect). *(b) all-or-nothing aggregation* — one slow/dead domain stalls the entire feed.

### D6 — Per-domain credential & tenant posture is preserved, not normalised

The aggregator attaches each domain's required credential (erp/fan: domain-facing IAM OIDC token, `sub`=account UUID; ecommerce: its header model; etc.) exactly as the existing per-domain console clients do today. The shared contract (D3) standardises *shape*, never *auth*. This keeps the ADR-MONO-017 D4 / console-integration-contract § 2.4.5–8 per-domain-credential invariant intact. **Rejected:** a single unified notification auth/token model — breaks the per-domain credential rule (an explicit, test-pinned invariant) for no benefit the aggregator needs.

### D7 — Phasing & ownership (shared-first, then conformance, then aggregator)

Sequencing (each a separate post-ACCEPTED task): **(1)** shared contract spec (D3) + lifted library (D4) — root `tasks/` + `libs/` + a shared contract location; **(2)** per-domain conformance — one project task per domain (`erp`/`ecommerce`/`wms`/`fan`) to adopt the contract + library and (for wms) decide whether it gains an inbox surface or stays delivery-only; **(3)** the `console-bff` aggregator (D2/D5) + console bell rewire — platform-console tasks. Ownership splits root (shared) vs per-project (conformance/console) along the standard monorepo boundary. **Rejected:** big-bang single PR — crosses the shared/project boundary in one unstageable change; staggered-but-sequenced is the monorepo norm.

### D8 — ACCEPTED transition mechanics (staged-child)

ACCEPTED is a separate task gated on explicit user intent. At ACCEPTED: flip Status PROPOSED→ACCEPTED, append the § 6 ACCEPTED row + a History ACCEPTED clause, and **finalise D1–D8 byte-unchanged** (ACCEPTED finalises, does not re-decide — ADR-038/017 pattern). Execution tasks (D7 phases 1–3) are post-ACCEPTED. **Rejected:** self-ACCEPT in this PR (prohibited); deciding all mechanics at PROPOSED (the staged-child pattern defers finalisation to ACCEPTED).

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **Zero retrofit of domain producers** — D3/D4 preserve every producing service's events + spec byte-unchanged. Notification services are consumers; producers (approval/order/payment/shipping/inventory/inbound/outbound/membership/account) are untouched.
- **Per-domain credential rule preserved** (D6) — ADR-MONO-017 D4 + console-integration-contract § 2.4.5–8 stay intact; the aggregator is a dispatcher, never a credential rewrite.
- **Failure isolation** (D5) — the shared-shell bell is forbidden from coupling to a single domain's availability (the regression that prompted this ADR).
- **Domain ownership preserved** (D1) — no merge; four services stay domain-owned.
- **ADR-MONO-016 § D3 byte-unchanged** — this ADR only adds an additive forward-pointer (HARDSTOP-04).

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED tasks)

- No contract spec is written, no library is lifted, no service is changed, no aggregator is built here. PROPOSED records the direction only (PR Separation Rule — spec-only, HARDSTOP-09 avoidance: a PROPOSED merge does **not** authorise implementation; ACCEPTED is required first).
- The wms inbox-vs-delivery-only question (does wms gain a `/notifications` surface, or stay a pure delivery engine excluded from the bell?) is flagged for D7 phase-2, decided per-domain at conformance time, not here.

### 3.3 Execution chain — **UNPAUSED (ACCEPTED 2026-06-28, TASK-MONO-311)**

> ACCEPTED — implementation is now unblocked. Step 1 (the ACCEPTED transition) is done by TASK-MONO-311; steps 2–4 are implement-ready (separate user-gated tasks; each preserves the § 3.1 hard invariants).

1. ✅ ADR-MONO-043 PROPOSED→ACCEPTED transition task (user-explicit-intent-gated) — **DONE (TASK-MONO-311)**.
2. Shared notification contract spec + `libs/` consumer/delivery/channel-SPI library (D3/D4) — root task(s).
3. Per-domain conformance tasks ×4 (erp/ecommerce/wms/fan) — project tasks; includes the wms inbox decision.
4. `console-bff` notification aggregator + console bell rewire (D2/D5) — platform-console tasks.

---

## 4. Alternatives Considered (cross-cutting)

- **Single merged `platform-notification-service`** — rejected at D1 (collapses incompatible audience semantics; centralises blast-radius; breaks domain ownership).
- **Do nothing / keep four silos** — rejected at D1/D2 (drift + the shared-shell coupling persist).
- **Client-side (console-web) fan-in instead of a BFF aggregator** — rejected implicitly under D2: would put four per-domain tokens + four upstream calls in the browser/server-component layer, violating the console-bff single-fan-out-point model (ADR-017) and the HttpOnly-token-server-side rule.
- **A shared notification *library* with no shared *contract*** — rejected under D3: without a shared envelope the aggregator still special-cases each domain's shape; the library alone does not give "one bell, one model."

---

## 5. Relationship to prior ADRs

| ADR | Relationship |
|---|---|
| ADR-MONO-016 (erp-platform bootstrap) § D3 | This ADR is the cross-domain generalisation the *"notification first increment"* anticipated. ADR-016 § D3 byte-unchanged; additive forward-pointer only. |
| ADR-MONO-017 (console-bff) D4/D7 | The aggregator (D2) reuses the per-domain-credential + per-domain fan-out attribution model verbatim. |
| ADR-MONO-004 (shared messaging) | `libs/java-messaging` is the host neighbourhood for the lifted consumer/delivery machinery (D4). |
| ADR-MONO-038 (shared idempotency filter abstraction) | Precedent + posture template: lift control-flow + ports, keep strategies service-owned; composition over inheritance. |
| ADR-MONO-005 (saga timeout / DLT / Category-C) | The DLT + Category-C delivery taxonomy the lifted delivery machinery must preserve. |

---

## 6. Verification / Audit-trail

ADR is a spec → no unit/integration tests. Verification = doc review + HARDSTOP linters:

- [ ] §1–§6 present; §2 Decision has D1–D8 each with CHOSEN-PROPOSED + ≥1 Rejected option.
- [ ] ADR-MONO-016 § D3 body byte-unchanged (`git diff` — additive forward-pointer only; HARDSTOP-04).
- [ ] No code / contract / schema changed in the PROPOSED PR (PR Separation Rule; HARDSTOP-09 avoidance).
- [ ] markdown lint (CI path-filter fast-lane) pass.

| Stage | Task | PR | Note |
|---|---|---|---|
| PROPOSED | TASK-MONO-308 | #1958 | D1–D8 CHOSEN-PROPOSED; doc-only. |
| ACCEPTED | TASK-MONO-311 | (this PR) | User-explicit *"ADR-043 ACCEPTED"* (D8 gate, not self-ACCEPT). D1–D8 finalised byte-unchanged; flip = Status + History clause + this row + § 3.3 UNPAUSED. D7 P1–P3 now implement-ready (separate user-gated tasks). |
| Follow-up close-out | TASK-MONO-317 | (this PR) | § 7 follow-ups resolved (2026-06-29, user-decided): wms → delivery-only; ecommerce shape + engine-level integration → **DECLINED** (no operator-console driver / HARDSTOP-06; ADR-038 § 5 M3). Records the decline only — D1–D8 + § 1–§ 5 byte-unchanged; no code/contract/library change. |

---

## 7. Outstanding follow-ups

**Resolution (2026-06-29, TASK-MONO-317 — user-decided close-out).** The value-delivering scope landed and resolved the originating incident: D1–D6 + D7 phases 1–3 for erp/fan shipped (P1a contract `platform/contracts/notification-inbox-contract.md`; P1b `libs/java-notification`; P2 erp/fan shape; P3a `console-bff` aggregator; P3b bell rewire — the shared-shell bell no longer couples to a single domain's availability, D5 live). The remaining surface-expansion follow-ups are **DECLINED** as a deliberate close-out (no pre-emptive implementation):

- **wms inbox-vs-delivery-only (D7 P2 / § 3.2) — DECLINED → delivery-only.** wms has no inbox surface and no UI consumer; it is a pure delivery engine (`wms.notification.delivered.v1` re-emit, no downstream). Surfacing it in the operator bell would mean building a new inbox surface with no operator demand. Resolution: wms stays delivery-only, excluded from the console bell (a decision, not an implementation).
- **ecommerce shape-conformance (D7 P2) — DECLINED (category mismatch).** ecommerce notifications are customer-facing (order/shipping → web-store inbox). Surfacing them in the **operator** console bell is a category error — operators have no need for customers' order notifications. No operator use case → conformance carries no value.
- **Engine-level integration into `libs/java-notification` (D4 deepening) — DECLINED (risk > benefit).** The "net-zero adoption" premise proved false: each domain's delivery engine genuinely diverges (status enum, backoff, dedupe model, DB dialect). Forcing convergence is a behaviour/schema change (HARDSTOP-06), not net-zero. Per the **ADR-MONO-038 § 5 M3** precedent ("share the leaf shape, keep the divergent engine service-side"), this is declined; the shared **shape** (P1a contract) is the unification boundary.
- **Shared external-channel SPI subsume-vs-wrap (D4) — ~~SETTLED at lift time~~ → CORRECTED: the library has zero consumers.** **[CORRECTED 2026-07-20 · `TASK-MONO-448`]** This entry originally read *"P1b `libs/java-notification` provides the channel SPI; **domains wire their own adapters.** No further decision pending"* — asserting an adoption that did not happen.

  **Measured 2026-07-20.** `libs/java-notification` exists, is registered in `settings.gradle`, and its `NotificationChannelAdapter` SPI plus consumer/dedupe/DLT machinery are real, tested code — but **no project depends on it.** The string `java-notification` appears in exactly one `build.gradle` in the repo: the library's own. No file under `projects/**` imports `com.example.platform.notification`. (Method sanity-check, so the zero is not a broken pattern: the same search for `java-messaging`, a genuinely consumed library, returns 33 files.) The two domains this entry names as conformant — erp and fan `notification-service` — depend on `java-common`/`java-web`/`java-messaging`/`java-observability`/`java-security` and **not** on `java-notification`; each implements its own local `NotificationChannelPort` (erp's `SlackWebhookChannelAdapter` targets the erp-local port, not the shared SPI).

  **Scope of the correction.** D3 (shared REST contract shape) and D2/D5 (console aggregator, per-domain fan-out with independent degrade) are **built and live** — verified independently in the same pass. Only this D4 library-wiring claim was false. The adjacent *"Engine-level integration — DECLINED"* entry already concedes the per-domain **engines** diverge; what it did not say — and what this entry asserted the opposite of — is that the **channel SPI** was never wired either.

  **Left open on purpose.** Whether to wire the library into erp/fan or retire it as an unused artifact is a fresh decision, not a cleanup. Both are defensible: an unconsumed library is not free (it accrues maintenance and implies a standard that does not exist), but the code is written and tested. **This note records the fact; it does not decide.** The honest starting point for whoever does decide is that "one library, zero consumers" was never an outcome this ADR chose — it is where the work stopped.
- **`account.created` identity-sourced-notification pattern — stays an ecommerce-local edge.** No shared pattern extracted.

**Re-open condition (all DECLINED items):** a concrete need to surface a specific domain's notifications in the **operator console bell**. The standard is already in place (P1a contract + P1b library + the `consolebff.notifications.domains` config), so re-opening costs one domain's shape-conformance + one config line (zero console-web change). **No pre-emptive work.**
