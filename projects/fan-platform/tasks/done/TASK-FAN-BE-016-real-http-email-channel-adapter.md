# TASK-FAN-BE-016 — Real HTTP email channel adapter (`NotificationChannelPort` real implementation)

**Status:** done

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (adapter against an existing port; resilience/error-mapping is the established pattern)

---

## Goal

Replace the EMAIL channel's deterministic logged mock with a **real outbound HTTP integration** to a transactional-email provider, completing the future increment the notification-service spec reserves: *"A real channel adapter (FCM/APNs/SES) is a future increment that re-implements `NotificationChannelPort`, wired via `@ConditionalOnMissingBean` / profile, with the mock retained for dev + integration tests"* ([architecture.md § Channel Mock Boundary](../../specs/services/notification-service/architecture.md), §121/§252).

This is the notification-service's first **real external integration** — until now every channel fan-out was a logged mock. It proves the outbound-HTTP story (provider auth, JSON send, timeouts, resilience, error mapping, per-outcome metrics) end-to-end, behind the existing port, **without touching the domain or use-case layers**.

**Channel choice (kickoff decision):** EMAIL via a provider-agnostic **HTTP** transactional-email API (RestClient + `ResilienceClientFactory` + MockWebServer test), NOT SMTP/SES-SDK. Rationale: tightest fit to the repo idiom (`ResilienceClientFactory` already a dependency, MockWebServer already a test dependency → **zero new dependencies**), and the resilience/auth/error-mapping surface is the portfolio-relevant story. PUSH (FCM/APNs) stays a mock — it needs heavier credential ceremony and is a separate future increment.

## Scope

**In scope (notification-service only):**
1. `HttpEmailChannelAdapter implements NotificationChannelPort` (new, `infrastructure/channel/`) — `channel()="EMAIL"`; `deliver(...)` POSTs a JSON `{from, to, subject, body}` to the configured provider with an API-key auth header, via a `ResilienceClientFactory.buildRestClient(...)` client; maps a 2xx to `DeliveryResult(true, EMAIL, <provider-ref>)`, increments `notification_channel_deliveries_total{channel=EMAIL,outcome=delivered}`.
2. **Best-effort, never-throw** semantics: any non-2xx / transport / timeout / parse failure is **caught**, logged `warn`, increments `outcome=failed`, and returns `DeliveryResult(false, EMAIL, null)`. The adapter MUST NOT throw — the fan-out runs inside the use-case's `@Transactional`, and a throw would roll back the **durable inbox row** (which architecture.md § Channel Mock Boundary declares authoritative and *decoupled from delivery*). This refines the v1 mock's "a throwing channel rolls the unit back" note: a *real* delivery failure must not discard the in-app notification.
3. **Channel selection by property** (`fanplatform.notification.email.mode`): `HttpEmailChannelAdapter` is `@ConditionalOnProperty(... havingValue="http")`; `LoggingEmailChannelAdapter` gains `@ConditionalOnProperty(... havingValue="mock", matchIfMissing=true)` so **exactly one** EMAIL `NotificationChannelPort` bean exists (default = mock; dev + integration tests unaffected).
4. **Recipient resolution** — the consumed event carries no recipient email (only `accountId` = IAM `sub`), and cross-service table reads are forbidden (architecture.md § Forbidden dependencies). The adapter therefore sends to a **deterministic synthetic recipient** `${accountId}@${fanplatform.notification.email.recipient-domain}`. This is a **documented limitation**: a production version would enrich the address via a preferences/profile lookup (out of scope). Recorded in architecture.md.
5. `application.yml` — `fanplatform.notification.email.{mode,provider-base-url,api-key,from-address,recipient-domain,connect-timeout-ms,read-timeout-ms}` with env overrides; `mode` defaults to `mock` (net-zero — identical to today).
6. `architecture.md` § Channel Mock Boundary — document the optional real `http` EMAIL mode, the best-effort/never-throw refinement, and the recipient-resolution limitation. (`ResilienceClientFactory`/RestClient are already allowed via `libs:java-common` + `spring-boot-starter-web`; no Allowed-dependencies change, and no real SDK is added — the Forbidden-dependencies "no real push/email SDK" line stays true.)
7. Unit test `HttpEmailChannelAdapterTest` (MockWebServer): happy-path mapping + request shape (path/auth header/JSON body/synthetic recipient), and the failure modes (5xx → failed+no-throw; server-down → failed+no-throw), each asserting the outcome counter.

**Out of scope:**
- PUSH real adapter (FCM/APNs) — separate future increment.
- Automatic redelivery/retry of a *failed real send* — v1 records `outcome=failed`; the durable inbox row remains authoritative. A redelivery mechanism is a further increment.
- Any change to the domain, `HandleMembershipEventUseCase`, the consumer, the inbox API, or the membership-event contract.
- The Testcontainers IT — the HTTP adapter is DB-free; a MockWebServer **unit** test is its authoritative gate, and the default `mock` mode keeps the existing consumer IT deterministic and unchanged.
- A real provider SDK / SMTP stack / new dependency.

## Acceptance Criteria

- **AC-1 (happy path)** — with `mode=http`, `deliver(notification)` POSTs to `${provider-base-url}` with the API-key auth header and a JSON body whose `to` is `${accountId}@${recipient-domain}`, `subject` is the notification title, `body` is the notification body, `from` is the configured from-address; a provider 2xx `{ "id": "<ref>" }` yields `DeliveryResult(true, "EMAIL", "<ref>")` and increments `…{outcome=delivered}`.
- **AC-2 (best-effort on failure)** — a provider 5xx, a connection failure (provider down), and an unparseable 2xx body each return `DeliveryResult(false, "EMAIL", null)` **without throwing**, and increment `…{outcome=failed}`.
- **AC-3 (net-zero default)** — with `mode` unset/`mock`, the EMAIL channel is the existing `LoggingEmailChannelAdapter` (byte-identical behaviour), `HttpEmailChannelAdapter` is not instantiated, and exactly one EMAIL `NotificationChannelPort` bean is present. The existing `LoggingChannelAdapterTest` and the consumer IT pass unchanged.
- **AC-4 (single-bean invariant)** — `mode=http` yields exactly one EMAIL bean (`HttpEmailChannelAdapter`) and the unchanged PUSH mock; the use-case's `List<NotificationChannelPort>` fan-out is unaffected (still one EMAIL + one PUSH).
- **AC-5** — `:notification-service:check` BUILD SUCCESSFUL; CI "Integration (fan-platform, Testcontainers)" GREEN (no regression — IT runs the default mock mode).

## Related Specs

- `projects/fan-platform/specs/services/notification-service/architecture.md` (§ Channel Mock Boundary — the section updated here; § Forbidden dependencies — the no-SDK line preserved)
- `projects/fan-platform/PROJECT.md` (trait `integration-heavy` — "푸시 알림(FCM/APNs), 이메일 … 외부 연동")

## Related Contracts

- None changed. `NotificationChannelPort` (the in-service domain port) is re-implemented, not altered. The membership-event contract (`fan-membership-events.md`) and the inbox API are untouched.

## Edge Cases

- **No recipient email in the event** — resolved to a synthetic `${accountId}@${recipient-domain}` (documented limitation). A blank/missing `recipient-domain` with `mode=http` is a misconfiguration; the adapter still attempts the send and a provider rejection routes through the best-effort `failed` path (never throws).
- **Two EMAIL adapter classes on the classpath** — both are `@ConditionalOnProperty` on `fanplatform.notification.email.mode` (http vs mock+matchIfMissing), so exactly one is ever instantiated. Verify the `List<NotificationChannelPort>` injection holds exactly one EMAIL + one PUSH under each mode.
- **Provider slow/unreachable** — connect/read timeouts (`ResilienceClientFactory.buildRestClient(baseUrl, connectMs, readMs)`) bound the call; a timeout is caught → `failed`, never stalls the consumer thread beyond the read timeout, never throws.
- **2xx with no/!json body** — treated as delivered=false (`failed`) rather than a hard throw (best-effort); alternatively a 2xx with no parseable ref could be delivered=true with a synthetic ref — choose `failed` to surface provider-contract drift in the metric. Assert the chosen semantics in the test.

## Failure Scenarios

- **F1 — a real delivery failure rolls back the durable inbox** — if the adapter threw (instead of best-effort), the use-case `@Transactional` would discard the `Notification` row and DLQ the event, losing the in-app notification over a transient email outage. Guarded by AC-2 (never-throw) + the catch-all in `deliver(...)`.
- **F2 — two EMAIL beans (or zero)** — a mis-gated conditional would inject two EMAIL adapters (double-send) or none (no email). Guarded by AC-3/AC-4 (mutually-exclusive `@ConditionalOnProperty`, `matchIfMissing=true` on the mock) + a wiring assertion.
- **F3 — net-zero regression** — if `mode` defaulted to `http`, every deployment without a provider configured would log `failed` for every notification. Guarded by AC-3 (`mode` defaults to `mock`).
- **F4 — leaking PII / a real send in CI** — the IT runs the default `mock` mode and the unit test points RestClient at a MockWebServer; no real provider is ever contacted in CI. Guarded by AC-5 (IT on mock) + the unit test's MockWebServer base-url.

---

## Closure

- **Impl PR**: #1343 — squash `353a657b892d599c3c8459429eb514f9b727e5ad` (merged 2026-06-12). 3-dim verified: (a) `gh pr view` state=MERGED; (b) `origin/main` tip = the squash commit; (c) pre-merge checks = 20 SUCCESS + 1 SKIPPED, **0 failing required**.
- **Delivered**: `HttpEmailChannelAdapter` (real provider-agnostic HTTP transactional-email, `@ConditionalOnProperty(email.mode=http)`, `ResilienceClientFactory` RestClient, API-key header, best-effort/never-throw, `outcome=delivered|failed` metric, synthetic `${accountId}@${recipient-domain}`); `LoggingEmailChannelAdapter` gated `mock`/`matchIfMissing=true` (exactly one EMAIL bean per mode); `application.yml` `fanplatform.notification.email.*` (default `mock` = net-zero); `architecture.md` § Channel Mock Boundary (http mode + best-effort + recipient limitation); `HttpEmailChannelAdapterTest` (MockWebServer, 4 tests).
- **Verification**: `:notification-service:check` (unit/slice, incl. new 4/4 + existing `LoggingChannelAdapterTest` 2/2 unchanged) + `integrationTest` (Testcontainers, default mock — full-context bean wiring) BUILD SUCCESSFUL locally; CI "Build & Test" + "Integration (fan-platform, Testcontainers)" + all frontend GREEN.
- **AC**: AC-1…AC-5 all satisfied. **No new dependency, no real SDK, no domain/use-case/consumer/inbox/contract change.**
- **Deferred (out of scope, follow-on)**: real PUSH adapter (FCM/APNs); automatic redelivery of a failed real send; recipient enrichment via a preferences/profile lookup.
