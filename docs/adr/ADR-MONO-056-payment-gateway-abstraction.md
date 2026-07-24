# ADR-MONO-056 — payment-gateway abstraction: standardize on PortOne as the aggregation layer, extract a project-agnostic `libs/payment` backend port, keep the checkout UI a documented per-project pattern (defer a standalone payment service)

**Status:** PROPOSED
**Date:** 2026-07-24
**History:** PROPOSED 2026-07-24 (this record). ACCEPT is a human gate — an agent may not accept its own ADR. The PROPOSED record authorises **no code**; acceptance binds the Phase 1 scope in § What acceptance binds.
**Decision driver:** Owner (2026-07-24) — *"또다른 PG사를 연동하거나 고를 수 있도록 하고, 이커머스/팬플랫폼 외에 추가로 만들어질 사이트에서도 사용할 수 있도록 라이브러리나 플랫폼이나 서비스로 빼는 건 어떻게 생각해?"* Two consumers now integrate a PG independently and more sites are planned; the owner asks whether to extract the PG integration for reuse + vendor-swappability, and in what form (library / platform / service).
**Related:** [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) (the `libs/java-messaging` precedent — project-agnostic infra extracted to a shared lib, adapters wired per service), [ADR-MONO-005](ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) (Category-B synchronous-external resilience taxonomy the Toss adapter already follows), fan-platform [ADR-001](../../projects/fan-platform/docs/adr/ADR-001-real-pg-portone-verification-boundary.md) (the PortOne verify-boundary this ADR generalises). `CLAUDE.md` § Shared vs project boundary + HARDSTOP-03 (why `libs/payment` must be domain-free) + HARDSTOP-09 (why this decision needs an ADR before any extraction task).

> **Why an ADR, not just tasks.** Extracting a shared payment layer is a cross-project structural change under shared paths (`libs/`), and the two existing integrations disagree on the one thing an abstraction must unify — the **settlement model** (confirm-charges vs verify-only). Picking a canonical model, and picking the *form* (lib vs service), are `platform/architecture-decision-rule.md` decisions. Making them silently inside an implementation task would bake a model choice into `libs/` that every future site inherits. This ADR settles them on the record first.

---

## 1. Context

### 1.1 Two live PG integrations, two different settlement models

| | ecommerce-microservices-platform | fan-platform |
|---|---|---|
| Vendor | **Toss Payments** (direct) | **PortOne** (aggregator over 토스·이니시스·카카오페이…) |
| Frontend SDK | `@tosspayments/tosspayments-sdk` v2 — 결제창(Payment Window), `payment().requestPayment({successUrl,failUrl})` | `@portone/browser-sdk` — `PortOne.requestPayment(...)` |
| **Settlement model** | **server-confirm charges**: client returns `paymentKey`, server `POST /v1/payments/confirm` is what actually captures money | **client-complete + server-verify**: PG already captured, server `GET /payments/{id}` only *verifies* status/amount/currency (ADR-001) |
| Payment shape | one-shot order | subscription/renew (future recurring / billing-key) |
| Backend adapter | [`TossPaymentsAdapter`](../../projects/ecommerce-microservices-platform/apps/payment-service/src/main/java/com/example/payment/adapter/out/pg/TossPaymentsAdapter.java) — real `api.tosspayments.com`, R4j CircuitBreaker/Retry/Bulkhead, behind `PaymentGatewayPort` | [`PortOnePaymentAdapter`](../../projects/fan-platform/apps/membership-service/src/main/java/com/example/fanplatform/membership/infrastructure/payment/PortOnePaymentAdapter.java) — `@Profile("portone")`, REST verify, fail-closed |

Both are already **hexagonal** — each hides its vendor behind an outbound port. The duplication is not "PG logic copy-pasted"; it is **two ports with different method shapes and a different money-capture point**.

### 1.2 The reuse goal is real, not hypothetical

The owner explicitly plans further sites beyond these two. So the classic YAGNI objection ("only 2 consumers") is time-boxed — the third consumer is a stated intent, which is the threshold at which a shared abstraction stops being premature.

### 1.3 PortOne already *is* a vendor-abstraction layer

PortOne is a payment **aggregator**: one SDK + one API front a channel-selectable set of ~20 downstream PGs (Toss, 이니시스, 카카오페이, 네이버페이…). "다른 PG사를 연동하거나 고르기" against PortOne is a **channel-config** change, not code. fan-platform already sits on it. This reframes the question: the cheapest vendor-swappability is not a hand-rolled abstraction over N direct integrations — it is *standardising on the aggregator* and letting config pick the downstream PG.

### 1.4 Constraints the form must respect

- **HARDSTOP-03 / shared-library policy** — `libs/` must be project-agnostic: no service names, no domain entities (`Membership`, `Order`). A payment-gateway port + canonical value types + resilience wrapper is pure infrastructure → allowed. A `libs/payment` that imported `Order` would be a Hard Stop.
- **Strict project boundary** — each project is self-contained (own gradle module set, own web workspace, own `node_modules`). Cross-project **frontend** code-sharing is not an established pattern here; a shared npm package spanning `ecommerce/web-store` and `fan-platform-web` would be a new structural element on its own.
- **ADR-MONO-005 Category B** — a synchronous external PG call is already a classified resilience category; the shared lib must carry that (CB/Retry/Bulkhead + fallback), not re-invent it per site.

---

## 2. Decision (proposed)

A **phased, layered** extraction — *not* a big-bang shared service.

### D1 — Standardise on **PortOne as the aggregation layer** for vendor choice

New sites integrate PortOne, not a direct vendor. Vendor selection ("PG사 고르기") becomes a **channel-key config** concern (0 code). This is the primary answer to "다른 PG사를 연동하거나 고를 수 있도록". Toss-direct in ecommerce is **kept** as a deliberate "direct-integration showcase" and as the proof that the port abstracts more than one *model* — but it is not the template new sites copy.

### D2 — Extract a project-agnostic **`libs/payment` backend library** (the real DRY win)

A shared Gradle module providing:
- `PaymentGatewayPort` — a **canonical** port. **Canonical model = verify** (ADR-001's post-authorization verify), because it is the superset that both vendors can express: a confirm-model vendor (Toss) wraps its `confirm` call *inside* the adapter and still returns a verified result to the port; a verify-model vendor (PortOne) implements it directly. Modeling the union around *verify* keeps the port's contract "prove this payment is real for this amount/currency", which is the invariant every consumer actually needs.
- Canonical value types — `PaymentIntent`, `PaymentVerification { status, paidAmount, currency, vendorRef }` — **no domain entities**.
- A shared **Category-B resilience + observability wrapper** (CB/Retry/Bulkhead + fail-closed fallback + metrics), lifted from `TossPaymentsAdapter`.
- Vendor **adapters** as separate wireable beans: `PortOnePaymentAdapter` (primary), `TossPaymentsAdapter` (alternative). Consumers select via Spring config/profile — exactly the `@Profile("portone")` pattern fan-platform already uses.

Consumers (`membership-service`, `payment-service`, future sites) delete their private port + resilience boilerplate and depend on `libs/payment`, wiring one adapter by config.

### D3 — Frontend stays a **documented per-project pattern**, not a cross-project package

Because of the strict project boundary (§1.4), do **not** create a shared npm package spanning projects now. Instead:
- Capture the checkout pattern (open PG window → obtain `paymentId`/`paymentKey` → server verifies via the `libs/payment` port; buyer-identity forwarding per PG requirements, cf. fan-platform FE-012 KG이니시스 email gap) as a **skill/guide** (`docs/guides/` or `.claude/skills/`).
- Each project keeps a thin local wrapper following it. Revisit a shared FE package only if/when a shared frontend workspace is introduced for another reason.

### D4 — **Defer** a standalone `payment-gateway-service` (the "real-company" form)

A dedicated microservice that all sites call (centralised webhooks, reconciliation, PCI-scope isolation, one place to add vendors) is the mature end-state — but for 2–3 portfolio sites it adds a **runtime SPOF + ownership cost + cross-project runtime coupling** that outweighs the benefit. Reconsider when **any** of these holds: (a) ≥4 consumers, (b) real inbound **webhook** handling is needed (a shared callback endpoint is a service, not a lib), (c) PCI scope must be physically isolated, (d) cross-site settlement/reconciliation must be centralised.

---

## 3. Options considered

| Option | Reuse | Vendor choice | Cost / risk | Verdict |
|---|---|---|---|---|
| **A. `libs/payment` backend lib** (D2) | High (backend) | via config-selected adapter | Fits `libs/` precedent (ADR-004); must resolve canonical model | **Chosen (Phase 1)** |
| **B. Standalone payment service** (D4) | Highest | centralised | New SPOF + ownership + cross-project runtime coupling; overkill for 2–3 sites | **Deferred** (trigger-gated) |
| **C. Shared FE npm package** | Medium (frontend) | n/a | Breaks strict project boundary; needs a shared FE workspace first | **Rejected now** → pattern doc (D3) |
| **D. Standardise on PortOne aggregator** (D1) | — | **config, 0 code** | PortOne fee + dependency vs direct | **Chosen** (composes with A) |
| **E. Do nothing** | none | per-site rewrite | duplication grows with each new site | Rejected (reuse intent is stated) |

---

## 4. Consequences

**Positive**
- New sites: pick a PortOne channel (vendor choice) + depend on `libs/payment` + wire one adapter → payment integration is config, not a rewrite.
- One audited place for PG resilience/observability (Category B), instead of N.
- The confirm-vs-verify union is decided once (canonical = verify) rather than re-litigated per site.

**Negative / risks**
- `libs/payment` must be **ruthlessly** domain-free (HARDSTOP-03). The canonical types are the guard surface — any leak of `Membership`/`Order` fails review.
- The **canonical-verify** choice pushes the Toss confirm-call *inside* its adapter; a subtle semantic (Toss `confirm` is a *capture*, not just a read) must be documented so a consumer never assumes the port only reads.
- Standardising on PortOne concentrates dependency/fees on one aggregator; the retained Toss-direct adapter is the hedge/escape hatch.
- Migrating two live, already-green integrations onto a shared lib is behavior-preserving-refactor work — must preserve each service's existing test surface (cf. `project_behavior_preserving_dedup_preserve_test_surface`).

---

## 5. What acceptance binds

The PROPOSED record authorises **no code**. On owner ACCEPT (exact-form `"ADR-MONO-056 ACCEPTED"`), the bound Phase 1 scope is:

1. Create `libs/payment` (port + canonical value types + Category-B resilience wrapper) — **no** consumer migration yet.
2. Move `PortOnePaymentAdapter` + `TossPaymentsAdapter` into it as config-selectable beans, domain-free.
3. Root `tasks/ready/` tasks (monorepo-level, one atomic cross-project PR per consumer migration): migrate `membership-service`, then `payment-service`, each preserving its current test surface + CI green.
4. Author the checkout-pattern skill/guide (D3).

**Not** bound by this ACCEPT (separate future ADRs): the standalone service (D4), any shared FE package (C), recurring/billing-key modeling for subscriptions.

---

## 6. Open questions (for review before ACCEPT)

- **Canonical model** — is *verify* the right union, or should the port expose both an `authorize/capture` (confirm) and a `verify` operation explicitly? (verify-only is simpler; two-op is truer to Toss.)
- **PortOne standardisation depth** — migrate ecommerce's checkout onto PortOne too (single SDK everywhere), or keep Toss-direct permanently as the showcase? (§D1 proposes keep.)
- **Module granularity** — one `libs/payment`, or `libs/payment-core` (port+types) + `libs/payment-portone` / `libs/payment-toss` (adapters) so a consumer pulls only its vendor? (leaning split, to avoid dragging both vendors' SDKs into every service.)
