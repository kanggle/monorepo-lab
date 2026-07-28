# ADR-MONO-057 — extend `libs/payment` with a recurring-billing capability: server-initiated billing-key charges, reusing the existing `verify` op for reconciliation

**Status:** PROPOSED
**Date:** 2026-07-25
**History:** PROPOSED 2026-07-25 (this record). ACCEPT is a human gate — an agent may not accept its own ADR. The PROPOSED record authorises **no code**.
**Decision driver:** Owner (2026-07-25) — fan-platform's subscribe/renew is currently *repeated one-time payment* (the fan re-opens the PG window every month), not PG-level recurring billing. Owner asked what's needed for real 정기결제 (auto-charge) and directed proceeding with the design first.
**Related:** [ADR-MONO-056](ADR-MONO-056-payment-gateway-abstraction.md) (the `libs/payment` port this extends; explicitly deferred "recurring/billing-key modeling" to a future ADR — this is that ADR), [fan-platform ADR-002](../../projects/fan-platform/docs/adr/ADR-002-billing-key-auto-renewal.md) (the project-specific consumption design — BillingKeyEnrollment, scheduler, webhook handler; companion to this ADR), [fan-platform ADR-001](../../projects/fan-platform/docs/adr/ADR-001-real-pg-portone-verification-boundary.md) (the one-time-payment verify boundary this generalises), `TASK-BE-438` stranded-refund reconciler (the money-safety reconciliation pattern this ADR reuses for a lost-response charge).

> **Why an ADR, not just a task.** Extending `libs/payment-core` is a shared-library change (HARDSTOP-03/09) — a second consumer-independent decision after ADR-056. It also introduces a **new trust model**: today's port is entirely client-initiated-then-server-verified (ADR-001); a recurring charge is **server-initiated with no client step at all**, and its completion signal can arrive via a lost synchronous response *or* an asynchronous webhook. Picking the op shape and the reconciliation mechanism before any consumer builds on it prevents baking a wrong contract into `libs/`.

---

## 1. Context

### 1.1 What exists today is NOT recurring billing

fan-platform's `SubscribeUseCase` / `RenewMembershipUseCase` both call `PaymentGatewayPort.verify(...)` after the **fan manually opens the PG window and pays**, every single time — including every monthly renewal. This is Korean PG terminology's **일반결제** (one-time payment) repeated on a schedule, not **정기결제** (recurring billing). Nothing charges automatically; a fan who doesn't come back simply lets the membership expire (already handled — `MembershipStatus` has no stored `EXPIRED`, it's read-time).

### 1.2 What real recurring billing requires (PortOne V2 model)

PortOne's recurring model is **빌링키(billing key)**-based:

1. **Issuance** (once): the fan registers a card via a client SDK call (`PortOne.requestIssueBillingKey(...)`) — conceptually parallel to `requestPayment`, but it registers a *payment method*, not a charge. Returns a `billingKey`.
2. **Charge** (repeated, server-initiated): the **server** calls the PG with the stored `billingKey` + amount — no client, no browser, no user interaction. This is the fundamental shift: today's port is *always* preceded by a client action; a recurring charge has none.
3. **Completion signal**: PortOne's server-to-server charge call is expected to respond synchronously with the result — but for money-moving server-to-server calls, a lost/timed-out response is a **known failure class** this repo has already hit and solved once: `payment-service`'s stranded-refund reconciler (`TASK-BE-438`) exists precisely because a cancel call can succeed at the PG while the caller never learns it. A billing-key charge has the identical shape.

### 1.3 The reconciliation mechanism already exists on the port — this is the key design insight

Because **we** generate the `paymentId` for a server-initiated charge (exactly as the client SDK generates one for `requestPayment` today), a charge whose synchronous response is lost is **not a new problem**: the existing `PaymentGatewayPort.verify(paymentReference, expectedAmountMinor, currency, orderReference)` — unchanged — can be called again with that same `paymentId` to determine what actually happened at the PG (PAID → the charge went through; not found/unpaid → it didn't). No new read capability is required for this reconciliation path; `verify` already is one.

---

## 2. Decision (proposed)

### D1 — One new optional capability: `RecurringBillingGateway`, in `libs/payment-core`

```java
public interface RecurringBillingGateway {
    /**
     * Server-initiated charge against a previously issued billing key. Generates
     * no client interaction. On a lost/ambiguous response (timeout, 5xx after the
     * PG may have captured), the caller MUST NOT blindly retry with the same
     * amount — reconcile first via the base PaymentGatewayPort.verify(paymentId, ...)
     * using the paymentId this call was invoked with, exactly as the stranded-refund
     * reconciler already does for Toss cancels.
     */
    PaymentAuthorization chargeBillingKey(
        String billingKey, String paymentId, long amountMinor, String currency, String orderName);
}
```

- Domain-free (a `billingKey` is an opaque vendor string; no `Membership`/`Order` type — HARDSTOP-03).
- Optional capability (like `RefundablePaymentGateway`/`PaymentStatusReadPort` from ADR-056) — a vendor adapter implements it only if it supports recurring billing.
- Reuses the existing `PaymentAuthorization` return type and the existing `verify` op for reconciliation — **no new read/verify method**.
- Issuance itself (`requestIssueBillingKey`) is a **client-SDK-only** call; this ADR does not add a server-side "verify billing key issuance" op to the port for Phase 1 (see §6 open question — a thin one may be added later if a forged/tampered billing-key-ID class of attack is judged worth closing now rather than later).

### D2 — `libs/payment-portone` implements it; webhook **signature verification** utility also lives there (vendor-specific, reusable)

- `PortOneRecurringBillingAdapter` (or an added method on the existing `PortOnePaymentAdapter`) implements `RecurringBillingGateway` against PortOne's billing-key charge endpoint.
- PortOne's webhook signature scheme is vendor-specific (HMAC over the raw body with a webhook secret) — the **verification utility** (`PortOneWebhookVerifier.verify(secret, rawBody, headers) -> boolean/parsed-event`) belongs in `libs/payment-portone` so any future consumer gets it for free. The **HTTP endpoint that receives the webhook and the business handling of its payload** stays in the consuming service (fan-platform ADR-002) — that is domain-specific ("what do we do when a renewal charge is confirmed/declined"), not library concern.
- Toss is out of scope for this ADR — ecommerce is one-time orders; a `libs/payment-toss` recurring capability is not built until a real consumer needs it.

### D3 — Webhook is a **durability backstop**, not the primary completion signal

The synchronous charge-call response is the primary path (mirrors Toss confirm today). The webhook exists so a charge whose synchronous response was lost is not silently un-reconciled forever — the webhook, when it arrives, is itself just a trigger to call `verify(paymentId, ...)` (never trust the webhook payload's amount/status directly — same "never trust the client/webhook signal alone" doctrine as ADR-001, extended to server-to-server).

### D4 — No new failure-shape decision needed

`chargeBillingKey` follows the **same FAILURE CONTRACT posture ADR-056 already established**: an adapter may return `declined()` or throw `Pg*Exception` — a consumer wires against its adapter's declared shape (PortOne today: declined-return, consistent with its existing `verify`).

---

## 3. Options considered

| Option | Verdict |
|---|---|
| **A. `RecurringBillingGateway` capability + reuse `verify` for reconciliation** (D1–D3) | **Chosen** — minimal new surface, reuses a money-safety pattern already proven in this repo (stranded-refund reconciler) |
| B. A whole new port hierarchy for recurring billing (separate from `PaymentGatewayPort`) | Rejected — recurring is a *capability add*, not a different payment concept; the existing `PaymentAuthorization`/`verify` machinery already fits |
| C. Trust the webhook payload directly (skip reconciliation via `verify`) | Rejected — repeats the exact mistake ADR-001 was written to avoid (trusting an unverified signal for a money decision); webhook payloads can be replayed/forged without a signature+reconcile discipline |
| D. Poll for billing-key charge status instead of webhook | Rejected as sole mechanism — polling delays reconciliation of a lost response by up to a full poll interval; webhook-as-trigger + `verify`-as-truth is strictly better and no more code |

---

## 4. Consequences

**Positive**
- One new interface, zero new value types beyond what already exists — small diff, low review surface.
- Reuses a reconciliation pattern this repo has already built and tested (stranded-refund), instead of inventing a new one.
- Webhook signature verification is reusable infrastructure for any future PortOne-based recurring consumer.

**Negative / risks**
- A server-initiated charge is a genuinely new trust class: the *decision to charge* now lives entirely server-side (a scheduler), with no user-facing confirmation step at charge time. Getting the **scheduling + failure/retry policy** right is a project-specific, domain-significant decision — deliberately **not** decided here; see the companion fan-platform ADR-002.
- A billing key is long-lived, sensitive-adjacent data (not a card number, but a durable charge capability) — its storage/encryption is a consumer concern (ADR-002), not this ADR's.

---

## 5. What acceptance binds

The PROPOSED record authorises **no code**. On owner ACCEPT (exact-form `"ADR-MONO-057 ACCEPTED"`), the bound scope is:

1. Add `RecurringBillingGateway` to `libs/payment-core` (domain-free, optional capability).
2. Implement it in `libs/payment-portone` + the webhook signature-verification utility.
3. A root `tasks/ready/` task for the library addition, decoupled from any consumer (mirrors TASK-MONO-478's "library only, no consumer migration").

**Not** bound by this ACCEPT: the fan-platform consumption (scheduler, webhook endpoint, `BillingKeyEnrollment`, retry/grace policy) — that is the companion **fan-platform ADR-002**, which needs its own separate ACCEPT (project-internal, `platform/architecture-decision-rule.md`).

---

## 6. Open questions (for review before ACCEPT)

- **Billing-key issuance verification** — Phase 1 trusts the client SDK's `billingKey` return value as-is (it registers a payment method, not money movement, so the blast radius of a forged issuance is bounded by the charge-time `verify`/reconcile step that follows). Add a server-side issuance-status check now, or defer until a concrete abuse case is observed?
- **Webhook delivery guarantee** — PortOne webhooks are typically at-least-once with retries; the consumer's webhook handler must be idempotent (this ADR notes it; ADR-002 must design for duplicate delivery, e.g. dedupe on `paymentId`).
- **Method name** — `chargeBillingKey` vs `charge` (shorter, since the capability interface already scopes it). Leaning `chargeBillingKey` for grep-ability against `verify`.
