# Runbook — AdminActionSpike

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`rate(admin_command_total[5m]) > 5` for 10 minutes, severity `warning` — operators (or something
using their credentials) are running more than 5 admin commands per second. No `sum`: the rule is
judged **per series** (`action_code` × `outcome`), so one hot action code is enough.

Metric: counter `admin_command_total{action_code, outcome}`, exported by
`admin-service/.../application/AdminActionAuditor.java` (`countCommand`, TASK-BE-662), once per
**finished** command record: `outcome` is `success`, `failure` or `denied` (a refused permission
check counts). Not counted: the in-progress marker row and operator login. `action_code` is the
`ActionCode` enum name (or `UNKNOWN`), so cardinality is bounded.

Every command also leaves a row in `admin_db.admin_actions` (append-only: DELETE is forbidden and a
row can only move once from `IN_PROGRESS` to a terminal outcome — triggers in
`V0008__admin_actions_bigint_pk.sql` and later) — that table is the evidence.

## 2. First checks

```bash
# Which action code and outcome is spiking
docker exec iam-prometheus wget -qO- --post-data='query=sum by (action_code,outcome) (rate(admin_command_total[5m]))' http://localhost:9090/api/v1/query

# Who ran what in the last 30 minutes (admin_actions: actor_id, actor_role, action_code, target_type, outcome, started_at)
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" admin_db' <<'SQL'
SELECT actor_id, actor_role, action_code, outcome, COUNT(*) AS n,
       MIN(started_at) AS first_at, MAX(started_at) AS last_at
FROM admin_actions
WHERE started_at > UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE
GROUP BY actor_id, actor_role, action_code, outcome
ORDER BY n DESC LIMIT 20;
SQL

# A sample of what they targeted and the reason they gave
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" admin_db' <<'SQL'
SELECT started_at, actor_id, action_code, target_type, target_id, outcome, reason
FROM admin_actions
WHERE started_at > UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE
ORDER BY started_at DESC LIMIT 20;
SQL
```

`started_at` is stored in UTC (JDBC `serverTimezone=UTC` in the services' datasource URLs). Admin logs:
the `bootRun` terminal of admin-service (`:apps:admin-service:bootRun`).

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| A planned bulk operation (scripted onboarding, mass role change) | One `actor_id`, one `action_code`, reasons that reference a ticket (`ticket_id` column exists), `outcome = SUCCESS`; ask the operator |
| A runaway client / retry loop | One `actor_id`, identical `target_id` over and over; `outcome` mostly `FAILURE` or `DENIED` — also moves `admin-command-failure-high.md` |
| `denied` is the spiking outcome | Someone is probing permissions: `outcome = DENIED`, many `action_code`s from one actor |
| A stolen operator token | Many actions from an actor who is normally quiet, unusual `actor_role`; compare with the operator's usual activity |
| The console UI is looping | The same requests repeated at a fixed cadence from the console's operator |

## 4. Recover / mitigate

- Planned bulk work: acknowledge; nothing to do. Say so in the alert thread.
- Runaway client: stop that client / script; the operator's token stays valid for its TTL.
- Suspected compromise: disable or rotate that operator through admin-service operator management
  (see `specs/services/admin-service/` — `rbac.md` and the admin API contract; the exact call is
  **not** written here because it was not verified) and escalate at once. Do not edit
  `admin_actions`: it is the audit ledger and the database refuses DELETE and any UPDATE of a finished row.
- Confirm: the first query is under 5/s per series for 10 minutes.

## 5. Escalate

- Any unexplained actor, or any `denied` burst across many action codes: security.
- Commands that moved accounts (lock / role change / tenant changes) in bulk by mistake: the
  service owner of the affected area — reversal is a set of further admin commands with reasons,
  not a database edit.
