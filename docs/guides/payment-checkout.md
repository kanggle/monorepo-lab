# Guide: Payment Checkout (real PG)

> Human reference (per `CLAUDE.md`, AI agents do not read `docs/guides/` as a
> source of truth). The canonical rule is [`ADR-MONO-056`](../adr/ADR-MONO-056-payment-gateway-abstraction.md).

How to add a real payment-gateway checkout to a site in this monorepo — the
frontend opens a PG payment window, the server independently **verifies** the
payment through the shared `libs/payment` port. The client's success signal is
never trusted on its own.

There are two adapter styles behind the one `libs/payment` port, and two live
consumers to copy from: a **verify-model** consumer (PortOne) and a
**confirm-model** consumer (Toss). Read whichever matches your PG.

---

## The shape (both models)

```
[browser] open PG window (vendor SDK) ──► user pays ──► client gets paymentId/paymentKey
    │                                                          │
    └─ NEVER trust this success signal alone ──────────────────┘
[server] libs/payment  PaymentGatewayPort.verify(request)
    → PortOne: GET /payments/{id}, assert status==PAID AND amount AND currency
    → Toss:    POST /v1/payments/confirm (this CAPTURES money), returns verified result
    → approved ⟹ record vendorPaymentRef;  declined/throw ⟹ no fulfilment
```

The server verify is the only authority. The client hands over an opaque
reference; the backend proves it against the PG for the **exact amount + currency**
it charges.

---

## Backend — depend on `libs/payment`, don't re-implement a PG adapter

1. Add the deps your vendor needs (NOT both — keep the other vendor's SDK off your
   classpath):
   - `implementation project(':libs:payment-core')`
   - `implementation project(':libs:payment-portone')` **or** `implementation project(':libs:payment-toss')`
2. Register the vendor adapter with an explicit `@Bean` / `@Import` in a small
   `PaymentGatewayConfig`. The lib adapters are profile-agnostic `@Component`s in
   `com.example.libs.payment.*`, outside your app's component-scan base — so
   scanning never picks them up, and explicit registration keeps profile/keyless
   control:
   - **PortOne** (verify-model consumer): a `@Bean @Profile("portone")` constructing
     the lib adapter from your config keys; a mock adapter under `@Profile("!portone")`
     is the keyless/CI default — a real PG call must never happen without an explicit
     profile + secret.
   - **Toss** (confirm-model consumer): `@Import(TossPaymentsAdapter.class)` +
     `@EnableConfigurationProperties(TossPaymentsProperties.class)`; the Resilience4j
     `toss-payments` CircuitBreaker/Retry/Bulkhead config stays in **your**
     `application.yml`.
3. Call `PaymentGatewayPort.verify(new PaymentVerificationRequest(reference, expectedAmountMinor, currency, orderReference))`
   and act on the `PaymentAuthorization` (`approved()` / `vendorPaymentRef()` /
   `paymentMethod()` / `receiptUrl()`).

### FAILURE CONTRACT — know your adapter's shape

`verify` deliberately admits two failure shapes (see the port javadoc). Handle the
one your wired adapter uses; do **not** assume the other:

| Adapter | On a failed / forged / tampered / unreachable payment |
|---|---|
| **PortOne** (verify-model) | returns `PaymentAuthorization.declined()` — **never throws** |
| **Toss** (confirm-model) | **throws** `PgConfirmFailedException` (permanent / 4xx) or `PgGatewayUnavailableException` (transient / exhausted) |

For Toss, keep the Resilience4j `ignore-exceptions` list pointing at the **lib**
FQN `com.example.libs.payment.PgConfirmFailedException` — a stale in-service FQN
silently drops the "4xx = permanent, no retry" guard (a money-safety regression).

---

## Frontend — open the window, hand the reference to a server action

1. `NEXT_PUBLIC_*` PG keys (store / channel / client key) are **inlined at build
   time** — a key change needs a **rebuild** of the web app, not just a restart.
2. The `totalAmount` sent to the PG window MUST equal the amount the backend
   re-verifies. A mismatch → the server's tamper guard declines a legitimate
   payment. For a discounted / prorated charge, send the *quoted* amount, not the
   list price.
3. **Forward the buyer's identity.** Some PGs reject the window up-front without
   it — **KG이니시스 V2 일반결제 requires `customer.email`** ("구매자 이메일은
   필수 입력입니다"); a bare `requestPayment` without a `customer` block fails
   before the window even opens. Forward the signed-in buyer's
   `{ email, fullName, phoneNumber }` from the session (via the OIDC `email` /
   `profile` scopes); fall back to a well-formed value so a missing claim never
   hard-blocks. Buyer identity is client→PG only — it is **not** part of the
   server verify.
4. On a thrown SDK error, **surface the PG's own message** (log `err.message`); a
   generic "결제 창을 여는 중 오류" hides the actual, actionable cause.
5. The bearer token never reaches the client; the paymentId goes to a `'use server'`
   action that calls the backend verify path.

---

## Vendor choice

New sites default to **PortOne** — it is an aggregator, so choosing / adding a
downstream PG (Toss / 이니시스 / 카카오페이 …) is a **channel-key config** change,
not code (ADR-MONO-056 D1). A Toss-direct integration is kept as a showcase of a
second settlement model behind the same port.

**Integration style** (Toss terminology): the reference consumers use the **결제창
(Payment Window)** style (`requestPayment` + redirect / popup), not the 결제위젯
(주문서형) embedded widget — the window flow is the minimal one and matches the
server-verify boundary. Pick the widget only if you want the embedded
method-selection UX.

---

## Checklist

- [ ] Backend depends on `libs/payment-core` + exactly one vendor module.
- [ ] Vendor adapter registered by explicit `@Bean` / `@Import`; keyless/CI stays on a mock or the safe default.
- [ ] Server `verify` asserts amount + currency; the client signal is never trusted alone.
- [ ] Failure shape (declined-return vs throw) handled for the wired adapter; Toss Resilience4j `ignore-exceptions` points at the lib FQN.
- [ ] Frontend sends the exact backend amount; buyer identity forwarded; `NEXT_PUBLIC_*` rebuilt after a key change; SDK error message surfaced.
