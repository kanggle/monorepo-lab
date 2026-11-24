# Runbook — AdminCommandFailureHigh

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`sum(rate(admin_command_total{outcome="failure"}[10m])) / sum(rate(admin_command_total[10m])) > 0.2`
for 10 minutes, severity `critical` — more than a fifth of finished admin commands ended in
`failure`. The denominator counts **all** outcomes (`success`, `failure`, `denied`); `denied` is a
refused permission, not a failure, and does not enter the numerator. There is no minimum-traffic
guard, so on a quiet system two failed commands out of eight trigger it.

Metric: counter `admin_command_total{action_code, outcome}`, exported by
`admin-service/.../application/AdminActionAuditor.java` (`countCommand`, TASK-BE-662), once per
finished command record, after the audit row was written. Per `admin-service/observability.md`, a
failing command is usually a failing downstream (account-service, auth-service, security-service).

## 2. First checks

```bash
# Which action codes fail
docker exec iam-prometheus wget -qO- --post-data='query=sum by (action_code,outcome) (rate(admin_command_total[10m]))' http://localhost:9090/api/v1/query

# The failures themselves: what was attempted and the downstream's answer (downstream_detail is TEXT)
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" admin_db' <<'SQL'
SELECT started_at, action_code, target_type, target_id, outcome, LEFT(downstream_detail, 300) AS detail
FROM admin_actions
WHERE outcome = 'FAILURE' AND started_at > UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE
ORDER BY started_at DESC LIMIT 20;
SQL

# Circuit breakers (accountService, authService, securityService) and downstream health
curl -s http://localhost:8085/actuator/circuitbreakers
curl -s http://localhost:8085/actuator/health/circuitbreakers
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8084/actuator/health
```

`/actuator/circuitbreakers` and the `circuitbreakers` health group are exposed by admin-service
(`application.yml:54-62`). Admin log: the `bootRun` terminal of admin-service.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| A downstream service is down or slow | The `curl` health calls above; `detail` in the SQL shows connection/timeout text; `service-down.md` for the dead one. Timeouts: connect 3 s, read 10 s (`admin.downstream.*`) |
| A circuit breaker is open (calls rejected without being sent: `503 CIRCUIT_OPEN`) | `/actuator/circuitbreakers` shows `OPEN`. Opens at 50 % failures over a 10 s time window with ≥ 5 calls, stays open 10 s, then half-opens (`resilience4j.circuitbreaker.instances.*`) |
| Admin cannot authenticate to the downstream | `detail` contains 401/403; the Bearer token comes from `iam.internal-client.*` (client `admin-service-client`) fetched from auth-service `/oauth2/token` — auth-service down also fails every other downstream call |
| Downstream URL wrong for the way the apps run | `AUTH_SERVICE_URL`, `ACCOUNT_SERVICE_URL`, `SECURITY_SERVICE_URL` default to `localhost:8081/8082/8084` (`application.yml:161-166`); a host started with different env sends calls elsewhere |
| One action code fails (bad request pattern / one broken endpoint) | The first query shows a single `action_code`; `detail` repeats the same downstream error |
| Business rejections counted as failures | Account-service 4xx business answers are excluded from the **circuit breaker** (`AccountBusinessException`), but whether each is recorded as `outcome=FAILURE` in the audit row was **not verified** — read `detail` before assuming an outage |

## 4. Recover / mitigate

- Restore the failing downstream (`service-down.md`, or `high-error-rate-5xx.md` if it is up but
  erroring). Open circuits close on their own after the wait window once calls succeed; no admin
  restart is needed.
- Operators should stop retrying the failing command while a circuit is open: each retry is another
  `failure` in the ratio. Tell them what is down.
- Do not edit `admin_actions` (append-only ledger).
- Confirm: the ratio expression falls below 0.2 for 10 minutes and the alert leaves
  `docker exec iam-prometheus wget -qO- http://localhost:9090/api/v1/alerts`.

## 5. Escalate

- Downstream owner of whichever service the `detail` column names.
- If failures are `AUDIT_FAILURE` (the audit row could not be written — `AdminActionAuditor.recordStart`
  fails closed, so **every** command is then blocked): admin-service owner immediately; the spec
  treats that as an emergency (`admin-service/observability.md` § Alerts, `AdminAuditWriteFailing`).
