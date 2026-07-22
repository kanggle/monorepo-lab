# admin-service — DB Role Grants Runbook

This runbook captures the GRANT / REVOKE statements that production and
staging operators must apply to the `admin_db` PostgreSQL role used by
`admin-service` at runtime (`admin_app`). The append-only intent of two
read-model tables — `admin_adjustment_audit` and `admin_alert_log` — is
expressed in V2 SQL as table-level comments (environment-agnostic) and is
**enforced at the DB role level by this runbook** (environment-specific).

These statements are deliberately **not** part of any Flyway migration. Flyway
runs as a superuser owning the schema, so DDL-time GRANT / REVOKE would either
be ignored (superuser) or break local-only standalone profiles. Runtime role
grants are an ops concern.

---

## Roles

| Role | Purpose | Source of credentials |
|---|---|---|
| `admin_app` | Runtime application role used by every `admin-service` pod | Vault / cloud secret manager |
| `admin_owner` | Schema owner; runs Flyway migrations | CI deploy job; rotated separately |
| `admin_readonly` | Dashboard / BI read-only access | Optional; not required for v1 |

Role creation is environment-bootstrap; this runbook only covers grants /
revokes against the read-model tables that already exist after
`V2__init_readmodel.sql`.

---

## Append-only Tables (REVOKE UPDATE / DELETE)

`admin_adjustment_audit` and `admin_alert_log` carry append-only semantics per
[`domain-model.md § 11`](../domain-model.md) and [`§ 12`](../domain-model.md).
The projection consumer inserts rows; the application **never** issues UPDATE /
DELETE except for the single documented acknowledgement path on
`admin_alert_log`.

### `admin_adjustment_audit`

```sql
-- Strict append-only. No application-layer mutation paths exist.
REVOKE UPDATE, DELETE ON admin_adjustment_audit FROM admin_app;
GRANT  INSERT, SELECT  ON admin_adjustment_audit TO   admin_app;
```

### `admin_alert_log` (column-level UPDATE for acknowledgement)

```sql
-- Default: deny mutation.
REVOKE UPDATE, DELETE ON admin_alert_log FROM admin_app;

-- Insert from the projection consumer + the alert-acknowledge endpoint
-- mutation of acknowledged_at / acknowledged_by ONLY (architecture.md § 1.6
-- Justification — the only application-layer write path on a read-model
-- table).
GRANT INSERT, SELECT                                ON admin_alert_log TO admin_app;
GRANT UPDATE (acknowledged_at, acknowledged_by)     ON admin_alert_log TO admin_app;
```

The column-level GRANT is the spec-mandated narrowing: any future mutation
attempt on a non-acknowledgement column surfaces as a PostgreSQL
`permission denied for column …` error rather than silently corrupting the
read-model. CI smoke-tests should cover this (production-only — locally the
admin user owns the schema).

---

## Apply Order

Runtime grants must be applied **after** the schema migration completes, so
the order on a fresh environment is:

```
1. Flyway runs as admin_owner  (V1 / V2 / V99)
2. Apply this runbook's GRANT / REVOKE statements as admin_owner
3. Application pods start with admin_app credentials
```

Re-running the GRANT / REVOKE statements is idempotent: they describe the
**desired** privilege set rather than a delta.

---

## Verification

The following SELECT against `information_schema` confirms the column-level
narrowing is in effect. Expect exactly two rows (`acknowledged_at`,
`acknowledged_by`).

```sql
SELECT column_name, privilege_type
FROM   information_schema.column_privileges
WHERE  table_name = 'admin_alert_log'
  AND  grantee    = 'admin_app'
  AND  privilege_type = 'UPDATE'
ORDER  BY column_name;
```

A negative-path probe (must return `permission denied`) — run as
`admin_app`:

```sql
UPDATE admin_alert_log SET alert_type = 'WRONG' WHERE id = '<some uuid>';
-- ERROR: permission denied for column alert_type of relation admin_alert_log
```

For `admin_adjustment_audit`, any UPDATE / DELETE attempt as `admin_app`
must error with `permission denied for table admin_adjustment_audit`.

---

## Rolling Back

The runbook is the source of truth — there is no migration to revert. To
relax for a controlled incident response, run the GRANTs in the opposite
direction (e.g., `GRANT UPDATE, DELETE ON admin_adjustment_audit TO admin_app`)
explicitly under change-management. Document any temporary widening so the
next regular deploy re-applies the runbook and restores the narrowed grants.

---

## Out of Scope

- `admin_user` / `admin_role` / `admin_user_role_assignment` /
  `admin_setting` — these are write-side tables; the application has full
  CRUD privileges by design.
- All `*_ref`, `*_summary`, `*_snapshot`, and `admin_throughput_*_daily`
  tables — projection-only mutation paths; no application UPDATE / DELETE
  outside the projection consumer's own queries (which use `admin_app` and
  are intentionally allowed). No GRANT narrowing required for v1.
- Multi-tenant grants — out of v1 (PROJECT.md `multi_tenant: false`).

---

## References

- [`architecture.md § Open Items`](../architecture.md) — projection write-side
  surface and acknowledgement justification
- [`domain-model.md § 11`](../domain-model.md) — `admin_adjustment_audit`
- [`domain-model.md § 12`](../domain-model.md) — `admin_alert_log`
- [`admin-service-api.md § 1.5`](../../../contracts/http/admin-service-api.md)
  — append-only adjustment endpoint
- [`admin-service-api.md § 1.6`](../../../contracts/http/admin-service-api.md)
  — alert acknowledgement endpoint (sole UPDATE path)
- TASK-BE-046 — read-model projection (introduces these tables)
- TASK-BE-048 — polish bundle (this runbook is deviation #6)
