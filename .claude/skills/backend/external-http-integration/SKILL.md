---
name: external-http-integration
description: Real outbound HTTP adapters and inbound webhook ingestion for external channels
category: backend
---

# Skill: External HTTP Integration

Patterns for integrating a service with an external HTTP system — **outbound**
(the service calls a third party: Slack, carrier tracking, FCM, email gateway)
and **inbound** (the service receives a webhook callback). Extracted from the
repeated implementations in fan-platform (FAN-BE-016/017 email+push),
ecommerce shipping (BE-293 carrier pull / BE-294 carrier webhook), and
erp notification (ERP-BE-020 Slack).

No single canonical spec — this is a cross-cutting backend implementation
pattern, and the authoritative source-of-truth lives in **each consuming
service's own** `specs/services/<service>/architecture.md` (external-channel /
webhook section) plus the endpoint contract in `specs/contracts/http/<service>-api.md`.
Read the target service's spec for the concrete channel, payload, and endpoint
contract; this skill only captures the shared *how*.

Prerequisite: read the target service's `specs/services/<service>/architecture.md`
(Service Type + test-requirement section), and `platform/event-driven-policy.md`
if the call is triggered from an event consumer.

---

## Outbound Adapter (the core pattern)

A use-case talks to an **outbound port** (application layer); the real HTTP call
lives in an **infrastructure adapter** selected by a config property. The default
config selects a **noop/mock adapter**, so a fresh environment is **net-zero** (no
external dependency, no behavior change) until explicitly switched on.

### 1. Outbound port (application layer)

```java
public interface NotificationChannelPort {
    DeliveryOutcome deliver(Notification notification);

    record DeliveryOutcome(boolean success, String detail) {
        public static DeliveryOutcome delivered()           { return new DeliveryOutcome(true, null); }
        public static DeliveryOutcome failed(String detail) { return new DeliveryOutcome(false, detail); }
    }
}
```

### 2. Config properties (gate + timeouts)

```java
@ConfigurationProperties(prefix = "erpplatform.notification.external")
public class ExternalNotificationProperties {
    private boolean enabled = false;          // master gate — default OFF
    private String mode = "noop";             // selects the adapter bean
    private Slack slack = new Slack();

    public static class Slack {
        private String webhookUrl;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;
        // getters/setters
    }
    // getters/setters
}
```

### 3. Real adapter — built via the shared resilient RestClient

```java
@Component
@ConditionalOnProperty(name = "erpplatform.notification.external.mode", havingValue = "slack")
public class SlackWebhookChannelAdapter implements NotificationChannelPort {

    private final RestClient restClient;

    public SlackWebhookChannelAdapter(ExternalNotificationProperties props,
                                      ResilienceClientFactory clientFactory) {
        this.restClient = clientFactory.buildRestClient(           // libs/java-common
                props.getSlack().getWebhookUrl(),
                props.getSlack().getConnectTimeoutMs(),
                props.getSlack().getReadTimeoutMs());
    }

    @Override
    public DeliveryOutcome deliver(Notification n) {
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", render(n)))
                    .retrieve()
                    .toBodilessEntity();            // throws on non-2xx -> caught below
            return DeliveryOutcome.delivered();     // green-wash-safe: success only on 2xx
        } catch (Exception e) {                     // catch-all -> best-effort, never throw
            return DeliveryOutcome.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
```

### 4. Default noop adapter — net-zero fallback

```java
@Component
@ConditionalOnProperty(name = "erpplatform.notification.external.mode",
                       havingValue = "noop", matchIfMissing = true)   // default
public class NoopExternalChannelAdapter implements NotificationChannelPort {
    @Override public DeliveryOutcome deliver(Notification n) { return DeliveryOutcome.delivered(); }
}
```

> Exactly one adapter bean is active per `mode`. `matchIfMissing = true` on the
> noop means an unconfigured service wires the noop and stays inert.

---

## Best-Effort Contract (do not break the caller's transaction)

When the external call is part of a use-case that also writes domain state
(e.g. persisting an in-app inbox row in the same `@Transactional` fan-out),
the adapter **must never throw** — a thrown exception would roll back the
domain write. Return `DeliveryOutcome.failed(...)` instead and let the caller
decide (record it, schedule a retry, drop it).

For durable retry, **split the transaction**: the consume/use-case tx persists
only a `PENDING` external-delivery row; the actual HTTP I/O runs later in a
scheduler's *own* per-delivery `@Transactional`. See `backend/scheduled-tasks`
(polling) and `messaging/consumer-retry-dlq` (Category C backoff).

---

## Inbound Webhook Ingestion (the counterpart)

When the external system calls back (delivery status, tracking update):

1. **Authenticate the payload** — verify an HMAC-SHA256 signature over the raw
   body using a shared secret. **Blank/absent secret = fail closed** (reject).
2. **Idempotency** — dedup on the provider's event/delivery id (a
   `processed_<x>_webhooks` table); a redelivery is a no-op.
3. **Forward-only state advance** — map the external status to an ordinal and
   reject out-of-order/regressing updates (a late "in transit" must not undo a
   "delivered").

```java
@PostMapping("/api/shippings/carrier-webhook")
public ResponseEntity<Void> ingest(@RequestHeader("X-Signature") String sig,
                                    @RequestBody byte[] rawBody) {
    if (!verifier.verify(rawBody, sig)) return ResponseEntity.status(401).build();
    var evt = parse(rawBody);
    if (deliveryStore.alreadyProcessed(evt.deliveryId())) return ResponseEntity.ok().build();
    forwardAdvancer.advance(evt);                 // forward-only; shared with the pull path
    deliveryStore.markProcessed(evt.deliveryId());
    return ResponseEntity.ok().build();
}
```

The webhook endpoint is gateway-exposed but **bearer-less** (authenticated by
signature, not JWT) — register it `permitAll` (see `backend/gateway-security`)
and verify in the handler.

---

## Testing (MockWebServer — no new dependency)

```java
@Test
void delivers_on_2xx() {
    server.enqueue(new MockResponse().setResponseCode(200));
    var outcome = adapter.deliver(sampleNotification());
    assertThat(outcome.success()).isTrue();
}

@Test
void reports_failure_on_5xx_without_throwing() {
    server.enqueue(new MockResponse().setResponseCode(500));
    var outcome = adapter.deliver(sampleNotification());   // must not throw
    assertThat(outcome.success()).isFalse();
}
```

Cover: 2xx success, 5xx failure, connection-refused (server not started),
channel/route selection, and signature-reject for inbound.

---

## Configuration Options

| Property | Default | Purpose |
|---|---|---|
| `<prefix>.enabled` | `false` | Master gate — emit external deliveries at all |
| `<prefix>.mode` | `noop` | Selects the active adapter bean (`slack`/`email`/`noop`) |
| `<prefix>.<ch>.connect-timeout-ms` | `2000` | RestClient connect timeout |
| `<prefix>.<ch>.read-timeout-ms` | `5000` | RestClient read timeout |

---

## Rules

- Build the `RestClient` through `ResilienceClientFactory` (libs/java-common) — never `new RestTemplate()`. It carries the shared timeout/retry/circuit-breaker config.
- Default config must be net-zero: `mode=noop` + `matchIfMissing=true` + `enabled=false`.
- The outbound adapter is **best-effort, never-throw** — return `DeliveryOutcome`, never propagate.
- Mark delivered **only on 2xx** (green-wash-safe).
- Inbound webhooks: verify HMAC over the raw body (fail-closed on blank secret), dedup by provider id, advance forward-only.
- No new third-party HTTP client dependency — `RestClient` + `MockWebServer` (already on the classpath).

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Adapter throws -> caller's `@Transactional` rolls back the domain write | Catch-all -> `DeliveryOutcome.failed(...)`; never throw |
| Marking delivered before checking status | `retrieve().toBodilessEntity()` (throws on non-2xx) -> success only in the no-throw path |
| Two adapter beans match -> `NoUniqueBeanDefinitionException` | Disjoint `@ConditionalOnProperty havingValue` per `mode`; exactly one `matchIfMissing` |
| HMAC verify on parsed JSON | Verify over the **raw** request body bytes, before parsing |
| Blank webhook secret silently accepts | Treat blank/absent secret as fail-closed (reject) |
| Webhook double-delivery double-applies | Idempotency dedup table keyed on provider event id |
| External call blocks the consume tx / retry loops inline | Persist `PENDING` row in consume tx; do HTTP in a scheduler's own per-delivery tx |
