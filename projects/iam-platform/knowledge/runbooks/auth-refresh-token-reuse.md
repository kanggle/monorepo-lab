# Runbook — AuthRefreshTokenReuseDetected

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`increase(auth_token_reuse_detected_total[5m]) > 0`, `for: 0m`, severity `critical` — a refresh
token that was already rotated was presented again. Possible token theft, or a client that raced its
own rotation outside the grace window.

🔴 **No code in this repository exports `auth_token_reuse_detected_total`** (searched
`projects/iam-platform/apps/*/src/main` and `libs/*/src/main`; `auth-service/observability.md`
lists it as required). This alert cannot fire today, so **a quiet alert does not mean no reuse**.
Confirm and use the other signals below:

```bash
docker exec iam-prometheus wget -qO- --post-data='query=count(auth_token_reuse_detected_total)' http://localhost:9090/api/v1/query
```

What the code does on reuse (`SasRefreshTokenAuthenticationProvider.handleReuse`, line ~659): it
logs `SAS_REFRESH: reuse detected — revoking the refresh-token family. account=<id>, children=<n>`
(WARN), revokes **every** refresh token, device session and SAS authorization of that account in one
transaction, and writes `auth.token.reuse.detected` (plus one `auth.session.revoked` per device) to
`auth_outbox`. A replay of an already-dead family writes nothing (no event). A replay inside
`auth.refresh-token.reuse-grace-seconds` (default 30) is treated as a client race, not reuse
(`application.yml`, `AuthorizationServerConfig`).

security-service consumes the event (`TokenReuseDetectedConsumer`, rule `TokenReuseRule`): the first
reuse in a 1-hour window records a `suspicious_events` row (`ALERT`); the second
(`security.detection.token-reuse.lock-threshold: 2`) is `AUTO_LOCK` and calls account-service to
lock the account. If its counter is unavailable it also locks.

## 2. First checks

```bash
# Rows the reuse produced in auth-service (look at event_type, aggregate_id = account, created_at, published_at)
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" auth_db' <<'SQL'
SELECT created_at, published_at, aggregate_id, partition_key
FROM auth_outbox
WHERE event_type = 'auth.token.reuse.detected'
ORDER BY created_at DESC LIMIT 20;
SQL

# What security-service decided (rule_code, risk_score, action_taken, lock_request_result)
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" security_db' <<'SQL'
SELECT detected_at, account_id, rule_code, risk_score, action_taken, lock_request_result
FROM suspicious_events
WHERE rule_code = 'TOKEN_REUSE'
ORDER BY detected_at DESC LIMIT 20;
SQL
```

Auth-service log (the `bootRun` terminal): search `reuse detected`. Rows older than the purge policy
may be gone from `auth_outbox` — retention of that table was not verified.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| Stolen refresh token replayed from elsewhere | `suspicious_events` has the account; the replay's source differs from the user's usual one (`login_history.ip_masked` for that `account_id` in `security_db`) |
| Client bug: two tabs / two processes refreshing the same token | Same account, same device, repeated at short intervals, no `AUTO_LOCK`; user reports being signed out |
| A retry after a lost response outside the 30 s grace | One reuse for the account, then nothing |
| Several accounts at once | `SELECT COUNT(DISTINCT account_id)` on the first query's table over the last hour is large — a leak of tokens, not one user |

## 4. Recover / mitigate

- The platform has already revoked the account's sessions; the user signs in again. Nothing to
  restore on the server side.
- If `lock_request_result` is `FAILURE`, the account was **not** locked although the rule asked for
  it: see `security-auto-lock-spike.md` § 3 and lock the account through admin-service.
- Unlocking a falsely locked account is an admin-service command with an operator reason — do it
  there, not in SQL.
- Confirm there is no backlog hiding events: `auth-outbox-lag-high.md`, `security-consumer-lag-high.md`.

## 5. Escalate

- Any reuse you cannot attribute to a client bug, and always when several accounts are affected:
  escalate to security **immediately** (the alert is `critical` for a single occurrence).
- The missing metric: file it against auth-service observability so this alert can actually fire.
