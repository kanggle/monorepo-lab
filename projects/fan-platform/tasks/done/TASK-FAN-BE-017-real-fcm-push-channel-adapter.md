# TASK-FAN-BE-017 — Real FCM HTTP v1 push channel adapter (`NotificationChannelPort` real impl, PUSH)

**Status:** done

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (symmetric to TASK-FAN-BE-016; same port + resilience pattern)

---

## Goal

Replace the PUSH channel's deterministic logged mock with a **real outbound HTTP integration** to **Firebase Cloud Messaging (FCM) HTTP v1**, the symmetric completion of the channel story begun by TASK-FAN-BE-016 (real EMAIL). After this, the *"real channel adapter (FCM/APNs/SES)"* future increment ([architecture.md § Channel Mock Boundary](../../specs/services/notification-service/architecture.md)) is closed for **both** EMAIL and PUSH; APNs remains a further increment.

It re-implements `NotificationChannelPort` behind the same port the v1 mock uses, reusing the exact pattern BE-016 validated — `ResilienceClientFactory` RestClient, property-gated mode, best-effort/never-throw, per-outcome metric, MockWebServer test — so **domain + use-case layers are unchanged**.

**Targeting (kickoff decision):** FCM **topic** messaging (`message.topic = ${topic-prefix}<accountId>`), NOT device-token messaging. Rationale: the consumed event carries no device registration token (only `accountId` = IAM `sub`) and cross-service table reads are forbidden — a device-token registry is unavailable here. FCM topic targeting (clients subscribe to their own per-account topic) is a first-class FCM v1 message shape that needs no token registry, so it is the natural fit. Device-token targeting would need a device-registry/preferences lookup (out of scope), mirroring BE-016's documented recipient limitation.

**Auth (kickoff decision):** a configurable bearer token (`api-key`), provider-agnostic like BE-016. Real FCM v1 mints a short-lived OAuth2 access token from a Google service account; that token-minting is **out of scope** (documented) — the adapter sends `Authorization: Bearer ${api-key}` and a real deployment would supply a current access token. No Google SDK is added.

## Scope

**In scope (notification-service only):**
1. `HttpFcmPushChannelAdapter implements NotificationChannelPort` (new, `infrastructure/channel/`) — `channel()="PUSH"`; `deliver(...)` POSTs `${fcm-base-url}/v1/projects/${project-id}/messages:send` with `Authorization: Bearer ${api-key}` and the FCM v1 JSON `{"message":{"topic":"<prefix><accountId>","notification":{"title":<title>,"body":<body>}}}`, via a `ResilienceClientFactory.buildRestClient(...)` client; a 2xx `{"name":"projects/…/messages/<id>"}` → `DeliveryResult(true, PUSH, <name>)`, increments `notification_channel_deliveries_total{channel=PUSH,outcome=delivered}`.
2. **Best-effort, never-throw** (identical discipline to BE-016): non-2xx / transport / timeout / unparseable-or-no-`name` body are **caught**, logged `warn`, increment `outcome=failed`, return `DeliveryResult(false, PUSH, null)`. The fan-out runs inside the use-case `@Transactional`; a throw would roll back the durable inbox row.
3. **Channel selection by property** (`fanplatform.notification.push.mode`): `HttpFcmPushChannelAdapter` is `@ConditionalOnProperty(... havingValue="fcm")`; `LoggingPushChannelAdapter` gains `@ConditionalOnProperty(... havingValue="mock", matchIfMissing=true)` → **exactly one** PUSH `NotificationChannelPort` bean (default = mock; EMAIL channel selection from BE-016 is independent).
4. **Topic resolution** — `${topic-prefix}<accountId>`, with `accountId` sanitized to the FCM topic charset `[a-zA-Z0-9-_.~%]+` (a UUID already complies; sanitize defensively). Documented topic-targeting limitation (no device registry).
5. `application.yml` — `fanplatform.notification.push.{mode,fcm-base-url,project-id,api-key,topic-prefix,connect-timeout-ms,read-timeout-ms}` with env overrides; `mode` defaults to `mock` (net-zero — identical to today).
6. `architecture.md` § Channel Mock Boundary — add the optional real `fcm` PUSH mode (topic targeting + auth note + best-effort), and update the "A real PUSH adapter … remains a future increment" line to reflect PUSH is now done (APNs the remaining one).
7. Unit test `HttpFcmPushChannelAdapterTest` (MockWebServer): happy-path mapping + request shape (path `…/v1/projects/<id>/messages:send`, bearer header, `message.topic`/`notification` body), and the failure modes (5xx → failed+no-throw; server-down → failed+no-throw; 2xx without `name` → failed), each asserting the outcome counter.

**Out of scope:**
- APNs real adapter — a further increment.
- Real Google service-account OAuth2 access-token minting (the adapter takes a configured bearer token).
- Device-token targeting / a device registry — topic targeting is used instead.
- Automatic redelivery of a failed real send (the durable inbox row remains authoritative — same as BE-016).
- Any change to the domain, `HandleMembershipEventUseCase`, the consumer, the inbox API, the EMAIL channel, or the membership-event contract.
- The Testcontainers IT (the HTTP adapter is DB-free; a MockWebServer **unit** test is its authoritative gate, and the default `mock` mode keeps the consumer IT unchanged).
- Any new dependency / SDK.

## Acceptance Criteria

- **AC-1 (happy path)** — with `push.mode=fcm`, `deliver(notification)` POSTs `…/v1/projects/${project-id}/messages:send` with `Authorization: Bearer ${api-key}` and a JSON body whose `message.topic` is `${topic-prefix}${accountId}`, `message.notification.title`/`body` are the notification title/body; a 2xx `{"name":"projects/p/messages/abc"}` yields `DeliveryResult(true, "PUSH", "projects/p/messages/abc")` and increments `…{channel=PUSH,outcome=delivered}`.
- **AC-2 (best-effort on failure)** — a 5xx, a connection failure (server down), and a 2xx without a usable `name` each return `DeliveryResult(false, "PUSH", null)` **without throwing**, and increment `…{outcome=failed}`.
- **AC-3 (net-zero default)** — with `push.mode` unset/`mock`, the PUSH channel is the existing `LoggingPushChannelAdapter` (byte-identical), `HttpFcmPushChannelAdapter` is not instantiated, and exactly one PUSH bean is present. The existing `LoggingChannelAdapterTest` + the consumer IT pass unchanged.
- **AC-4 (single-bean invariant)** — `push.mode=fcm` yields exactly one PUSH bean (`HttpFcmPushChannelAdapter`) and the EMAIL channel is whatever `email.mode` selects (independent); the use-case `List<NotificationChannelPort>` fan-out is still one EMAIL + one PUSH.
- **AC-5** — `:notification-service:check` BUILD SUCCESSFUL; CI "Integration (fan-platform, Testcontainers)" GREEN (no regression — IT runs the default mock mode).

## Related Specs

- `projects/fan-platform/specs/services/notification-service/architecture.md` (§ Channel Mock Boundary — updated here; § Forbidden dependencies — the no-SDK line preserved)
- `projects/fan-platform/PROJECT.md` (trait `integration-heavy` — "푸시 알림 (FCM/APNs)")
- `TASK-FAN-BE-016` (the symmetric EMAIL adapter — same pattern)

## Related Contracts

- None changed. `NotificationChannelPort` is re-implemented, not altered. No event/API contract is touched.

## Edge Cases

- **No device token in the event** — resolved via topic targeting `${topic-prefix}<accountId>` (documented limitation), not a device token.
- **accountId with topic-illegal chars** — sanitized to `[a-zA-Z0-9-_.~%]+` (a UUID already complies); assert the sanitization on a non-UUID accountId in the test.
- **Two PUSH adapter classes on the classpath** — both `@ConditionalOnProperty` on `fanplatform.notification.push.mode` (fcm vs mock+matchIfMissing) → exactly one instantiated. Verify the `List<NotificationChannelPort>` holds one EMAIL + one PUSH under each mode.
- **Provider slow/unreachable** — connect/read timeouts bound the call; a timeout is caught → `failed`, never stalls the consumer thread beyond the read timeout, never throws.
- **2xx without `name`** — treated as `failed` (surfaces FCM-contract drift in the metric), consistent with BE-016's no-id handling.

## Failure Scenarios

- **F1 — a real delivery failure rolls back the durable inbox** — if the adapter threw, the use-case `@Transactional` would discard the `Notification` row and DLQ the event over a transient FCM outage. Guarded by AC-2 (never-throw) + the catch-all in `deliver(...)`.
- **F2 — two PUSH beans (or zero)** — a mis-gated conditional would double-send or drop push. Guarded by AC-3/AC-4 (mutually-exclusive `@ConditionalOnProperty`, `matchIfMissing=true` on the mock) + a wiring assertion.
- **F3 — net-zero regression** — if `push.mode` defaulted to `fcm`, every deployment without FCM configured would log `failed` for every notification. Guarded by AC-3 (`mode` defaults to `mock`).
- **F4 — a real push in CI** — the IT runs the default `mock` mode and the unit test points RestClient at a MockWebServer; no real FCM endpoint is contacted in CI. Guarded by AC-5 (IT on mock) + the unit test's MockWebServer base-url.

---

## Closure

- **Impl PR**: #1346 — squash `065f52ff36b6f104521606ddc89fd9a6e6c256f9` (merged 2026-06-12). 3-dim verified: (a) state=MERGED; (b) `origin/main` tip = the squash commit; (c) pre-merge checks = 20 SUCCESS + 1 SKIPPED, **0 failing required**.
- **Delivered**: `HttpFcmPushChannelAdapter` (real FCM HTTP v1, `@ConditionalOnProperty(push.mode=fcm)`, topic targeting `${topic-prefix}<accountId>`, bearer auth, best-effort/never-throw, `outcome=delivered|failed` metric); `LoggingPushChannelAdapter` gated `mock`/`matchIfMissing=true` (exactly one PUSH bean per mode); `application.yml` `fanplatform.notification.push.*` (default `mock` = net-zero); `architecture.md` § Channel Mock Boundary (fcm mode + topic targeting + APNs-only future-increment line); `HttpFcmPushChannelAdapterTest` (MockWebServer, 5 tests).
- **Verification**: `:notification-service:check` (new 5/5 + existing `LoggingChannelAdapterTest` 2/2 unchanged) + `integrationTest` (Testcontainers, default mock — full-context bean wiring) BUILD SUCCESSFUL locally; CI "Build & Test" + "Integration (fan-platform, Testcontainers)" + all frontend GREEN.
- **AC**: AC-1…AC-5 all satisfied. **No new dependency, no Google SDK, no domain/use-case/consumer/inbox/EMAIL-channel/contract change.**
- **Channel story**: real-channel future increment now closed for **both EMAIL (BE-016) and PUSH (BE-017)**. **Deferred (out of scope, follow-on)**: APNs adapter; real OAuth2 service-account access-token minting; device-token targeting + device registry; failed-send redelivery.
