# Runbook — SecurityAutoLockSpike

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`rate(security_auto_lock_issued_total[5m]) > 1` for 5 minutes, severity `warning` — security-service
is ordering more than one account lock per second. The expression has no `sum`, so it is judged per
`result` series (`success` and `failure` separately).

Metric: counter `security_auto_lock_issued_total{result="success"|"failure"}`, exported by
`security-service/.../infrastructure/client/AccountServiceClient.java` (`lock`, TASK-BE-662). Counted
once per **final** outcome of `POST <account-service>/internal/accounts/{id}/lock`:
`success` = locked now or already locked; `failure` = retries exhausted, a non-409 4xx, or 409
(invalid transition, e.g. a deleted account). Retries are `security_auto_lock_retries_total`; the
spec's "operator must intervene" counter is `security_auto_lock_failures_total` (same class).

When does security-service lock? Rule scores are bands: 0-49 nothing, 50-79 alert only, **80-100
auto-lock** (`RiskLevel`). Defaults in `application.yml` (`security.detection.*`): velocity
`threshold` 10 in `window-seconds` 3600 scores 80 (so ten login failures on one account in an hour
locks it); geo anomaly `min-score` 85; token reuse locks on the 2nd reuse in an hour
(`token-reuse.lock-threshold`). A lock on **many accounts at once** is therefore either an attack
that deliberately fails logins against many accounts (locking them out is the attacker's gain) or
a rule that is too eager.

## 2. First checks

```bash
# Success vs failure rate right now
docker exec iam-prometheus wget -qO- --post-data='query=rate(security_auto_lock_issued_total[5m])' http://localhost:9090/api/v1/query

# Which rules produced the locks in the last hour, and how account-service answered
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" security_db' <<'SQL'
SELECT rule_code, action_taken, lock_request_result, COUNT(*) AS n, COUNT(DISTINCT account_id) AS accounts
FROM suspicious_events
WHERE detected_at > UTC_TIMESTAMP(6) - INTERVAL 1 HOUR
GROUP BY rule_code, action_taken, lock_request_result
ORDER BY n DESC;
SQL

# Is account-service answering
curl -s http://localhost:8082/actuator/health
```

`lock_request_result` is `SUCCESS`, `ALREADY_LOCKED`, `FAILURE` or `PENDING`
(`SuspiciousEvent.java`). security-service log: `Auto-lock attempt N failed with status …`,
`Auto-lock non-retryable 4xx`, `Auto-lock failed after 3 attempts; will be routed to outbox pending`.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| Credential-stuffing wave tripping the velocity rule on many accounts | The SQL shows `rule_code` velocity dominating with many `accounts`; `auth-login-failure-spike.md` queries show failures spread over many accounts |
| Token-reuse locks | `rule_code = 'TOKEN_REUSE'` dominating — `auth-refresh-token-reuse.md` |
| A rule threshold too low for real traffic (e.g. a shared office, a load test) | One `ip_masked` in `login_history` behind most of the failures (`auth-login-failure-spike.md` § 2 queries) |
| `result="failure"` is the spiking series: locks are being **ordered but not applied** | `lock_request_result = 'FAILURE'` rows; log shows 5xx/timeouts/401 from account-service; `curl -s http://localhost:8082/actuator/health`. Lock-call settings: `security.detection.auto-lock.*` — base URL env `ACCOUNT_SERVICE_BASE_URL` defaults to `http://localhost:8081` (**auth-service's port**, `application.yml:128`) — a host app started without that env sends every lock to the wrong service (TASK-MONO-727 fixed it only in the e2e compose) |
| account-service rejects the Bearer token | Log `Auto-lock non-retryable 4xx` with status 401/403; the token comes from `iam.internal-client.*` (`OIDC_TOKEN_URI`, client `security-service-client`) |

## 4. Recover / mitigate

- Real attack: the locks are doing their job; the work is reaching the affected users — unlocking an
  account is an admin-service command with an operator reason (no SQL). Coordinate with security.
- Over-eager rule: change the matching env var (`DETECT_VELOCITY_THRESHOLD`,
  `DETECT_VELOCITY_SCORE_WEIGHT`, `DETECT_GEO_MIN_SCORE`, `DETECT_TOKEN_REUSE_LOCK_THRESHOLD`, see
  `application.yml`) and restart security-service. This changes who gets locked — it is a security
  decision; get it approved.
- `failure` series: fix `ACCOUNT_SERVICE_BASE_URL` / account-service health, then restart
  security-service. Accounts whose lock was ordered but failed stay in `suspicious_events` with
  `lock_request_result = 'FAILURE'` and are **not** locked — list them with the SQL above and lock
  them through admin-service if they should be.
- Confirm: both `security_auto_lock_issued_total` series under 1/s for 5 minutes.

## 5. Escalate

- Mass lock-out of legitimate users, or `FAILURE` rows for accounts that should be locked: security
  immediately.
- Anything touching rule thresholds: the security-service owner decides.
