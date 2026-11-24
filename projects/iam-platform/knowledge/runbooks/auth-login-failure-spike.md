# Runbook — AuthLoginFailureSpike

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`rate(auth_login_total{result="failure"}[5m]) > 20` for 5 minutes, severity `warning` — more than
20 failed logins per second.

🔴 **No code in this repository exports `auth_login_total`.** `auth-service/observability.md` lists
it as required, but searching `projects/iam-platform/apps/*/src/main` and `libs/*/src/main` finds
no `auth_login_total` / `auth.login` meter (auth-service registers meters only in
`OutboxLagMetric`, `AuthOutboxPublisher`, `AccountLockedConsumer`, `KafkaConsumerConfig`). Until
something exports it, **this alert cannot fire** and its silence says nothing about logins. Confirm:

```bash
docker exec iam-prometheus wget -qO- --post-data='query=count(auth_login_total)' http://localhost:9090/api/v1/query
```

Empty result = still not exported. If you are reading this because it did fire, someone has since
added the metric — read its exporting class first; the rest of this page then still applies.

Login failures are still observable through the events auth-service emits
(`LoginEventRecorder.recordFailed` → `auth.login.failed`) and through security-service's history of
them. That is what the checks below use.

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
