# Runbook — AuthLoginFailureSpike

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`rate(auth_login_total{result="failure"}[5m]) > 20` for 5 minutes, severity `warning` — more than
20 failed logins per second.

The metric is exported by `infrastructure/metrics/MicrometerAuthMetrics` (TASK-BE-664, through
`application/port/AuthMetricsPort`), once per login attempt, `result` ∈
`success` / `failure` / `rate_limited` / `account_locked`. Counted in: the form login
(`infrastructure/.../CredentialAuthenticationProvider.authenticate` — the only live password path),
social login (`application/OAuthLoginUseCase.resolveBrowserLogin`, after the account is resolved),
and `application/LoginUseCase` (no HTTP entry point today).

What `failure` does **not** include: infrastructure faults — account-service unreachable
(fail-closed `AuthenticationServiceException`) or a social-callback failure before the account is
resolved. Those are deliberately left out so an outage does not look like credential stuffing.
🔴 `result="rate_limited"` is 0 in practice: the live form login has no login rate limit (owner
decision, TASK-BE-599); only `LoginUseCase` has one. Before TASK-BE-664 (2026-11-24) the metric did
not exist and auth-service was not scraped at all, so history before that date is empty.

Sanity check that the series is being scraped:

```bash
docker exec iam-prometheus wget -qO- --post-data='query=sum by (result) (auth_login_total)' http://localhost:9090/api/v1/query
```

Empty result = auth-service is not being scraped (see `service-down.md`). Login failures are also
observable through the events auth-service emits (`LoginEventRecorder.recordFailed` →
`auth.login.failed`) and through security-service's history of them — the checks below use that to
find *who* is failing.

## 2. First checks

Failures by outcome in the last 15 minutes, from security-service's append-only history
(`security_db.login_history`, columns `outcome`, `account_id`, `ip_masked`, `occurred_at`;
`outcome` holds the `LoginOutcome` enum names, e.g. `SUCCESS`, `FAILURE`):

```bash
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" security_db' <<'SQL'
SELECT outcome, COUNT(*) AS n
FROM login_history
WHERE occurred_at > UTC_TIMESTAMP(6) - INTERVAL 15 MINUTE
GROUP BY outcome;

SELECT ip_masked, COUNT(*) AS n
FROM login_history
WHERE outcome = 'FAILURE' AND occurred_at > UTC_TIMESTAMP(6) - INTERVAL 15 MINUTE
GROUP BY ip_masked ORDER BY n DESC LIMIT 10;

SELECT account_id, COUNT(*) AS n
FROM login_history
WHERE outcome = 'FAILURE' AND occurred_at > UTC_TIMESTAMP(6) - INTERVAL 15 MINUTE
GROUP BY account_id ORDER BY n DESC LIMIT 10;
SQL
```

An empty history while users are failing to log in means the events are not arriving — go to
`security-consumer-lag-high.md` and `auth-outbox-lag-high.md`.

The edge's view of the same traffic: `gateway_ratelimit_rejected_total{scope="login"}` — see
`gateway-rate-limit-exceeded.md`.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| Credential stuffing (many accounts, few attempts each, few source addresses) | Second query: a handful of `ip_masked` values dominate; third query: many accounts with small counts |
| Targeted guessing (one account, many attempts) | Third query: one `account_id` with a large count; security-service may already have flagged it — see `security-auto-lock-spike.md` |
| A client or integration with a stale password | One `account_id` or one `ip_masked`, steady rate, no growth in distinct accounts |
| auth-service cannot reach account-service (credential lookups failing) | auth log shows account-service call errors; `curl -s http://localhost:8082/actuator/health`; `high-error-rate-5xx.md` for `service="auth-service"` |

## 4. Recover / mitigate

- Attack: the gateway already limits `login` to 20 per 60 s per tenant and /24 subnet
  (`gateway-rate-limit-exceeded.md`). Locking a targeted account is an admin-service command, a
  decision for the account's owner or security — not something to do from this page.
- Dependency failure: restore account-service (`service-down.md`).
- Nothing in this repository blocks an address at the network edge — **not verified** for the demo.

## 5. Escalate

- Many distinct accounts under attack: escalate to security with the three result sets.
- The metric gap itself: file it against auth-service observability (spec says required, code does not
  export it).
