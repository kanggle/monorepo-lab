# Runbook — AuthAccountServiceDown

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`max(resilience4j_circuitbreaker_state{service="auth-service",name="accountService",state=~"open|forced_open"}) == 1`
for 1 minute, severity `critical`. **Logins are failing.**

auth-service calls account-service for every login (account status / tenant lookups) through
`auth-service/.../infrastructure/client/AccountServiceClient.java`, wrapped in retry (3 attempts,
exponential-random backoff from 500 ms) and then a circuit breaker named `accountService`
(`ResilienceClientFactory.standardCircuitBreakerConfig()` in `libs/java-common`: opens at a **50%
failure rate** over a **10-second time window** once at least **5 calls** were made, stays open
**10 s**, then lets **3** trial calls through in `half_open`). 4xx answers do not count — only 5xx,
connection errors and timeouts do.

While open, every call is refused without reaching account-service (`CallNotPermittedException`),
the client throws `AccountServiceUnavailableException`, and the login **fails closed** (503 / login
error — never "let the user in without the check"). These failures are deliberately **not** counted
in `auth_login_total{result="failure"}` (TASK-BE-664), so `AuthLoginFailureSpike` stays quiet;
this alert is the one that owns an account-service outage.

The breaker cycles `open` → (10 s) → `half_open` → `open` again while account-service is still down,
so the `open` line can drop to 0 for a few seconds at a time; `max(...)` over the 1-minute `for:` is
what keeps the alert steady. The series exists only because, since TASK-BE-665, the breaker is taken
from Spring's `CircuitBreakerRegistry` (exported by `resilience4j-micrometer`). Before that it was a
standalone instance and exported nothing.

- A missing series means auth-service is not being scraped (`service-down.md`), not that the breaker
  is closed.
- The other auth-service breakers (`adminAssignment`, `adminOperatorFacet`,
  `adminOperatorInvitation`) are still standalone and **not** exported; this alert covers
  `accountService` only.

## 2. First checks

```bash
# Breaker state as Prometheus sees it (1 on exactly one state line)
docker exec iam-prometheus wget -qO- --post-data='query=resilience4j_circuitbreaker_state{name="accountService"}' http://localhost:9090/api/v1/query

# Is account-service itself up? (scrape target + its own health, port 8082 on the host)
docker exec iam-prometheus wget -qO- --post-data='query=up{service="account-service"}' http://localhost:9090/api/v1/query
curl -s http://localhost:8082/actuator/health

# Failure rate the breaker is reacting to
docker exec iam-prometheus wget -qO- --post-data='query=resilience4j_circuitbreaker_failure_rate{name="accountService"}' http://localhost:9090/api/v1/query

# account-service's own database
docker compose -f projects/iam-platform/docker-compose.yml ps iam-mysql
```

In the auth-service `bootRun` terminal, look for `Account service is unavailable` and the
underlying cause (connection refused, read timeout, 5xx status).

## 3. Likely causes

| Cause | How to confirm |
|---|---|
| account-service process not running | `up{service="account-service"} == 0` · `curl :8082/actuator/health` refused · `ServiceDown` also firing |
| account-service up but its MySQL down / slow | account-service health shows `db` DOWN · `iam-mysql` not `healthy` |
| account-service answering 5xx | `HighErrorRate5xx` for `account-service` · its `bootRun` terminal shows stack traces |
| Read timeouts (slow account-service) | auth-service log shows `SocketTimeoutException`; read timeout is `auth.account-service.read-timeout-ms` (default 20000) |
| Wrong URL in auth-service | auth-service log shows connection refused to an unexpected host; `ACCOUNT_SERVICE_URL` (default `http://localhost:8082`) |
| Breaker forced open by someone | `state="forced_open"` is the line at 1 — it will not close on its own; not verified that any code path in this repo forces it |

## 4. Recovery / mitigation

1. Fix account-service (start it, restore its database, or roll back the change that made it 5xx).
   No action is needed in auth-service: the breaker probes again 10 s after opening and closes by
   itself after 3 successful trial calls.
2. Restarting auth-service also resets the breaker, but **does not help** while account-service is
   still down — it will reopen within the first 10-second window.
3. Do not raise the failure threshold or disable the breaker to "let logins through": without
   account-service the login cannot be checked, and the path is fail-closed by design.

## 5. Escalation

Login is unavailable for every user and tenant served by this auth-service — treat as an outage.
Escalate to the account-service owner immediately if it is not back within 5 minutes. If
account-service is healthy but the breaker stays open, escalate to the auth-service owner (wrong URL,
token-provider failure, or a network path between the two).
