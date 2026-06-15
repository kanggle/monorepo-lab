# Runbook — ADR-036 production identity backfill (P4-B / M4)

**Task:** TASK-BE-386 · **ADR:** [ADR-MONO-036 § 4 Amendment](../../../../docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md)

Reconciles **pre-existing (already-split) data** so every principal converges on one central
`identities.identity_id` across the three physically-separate IAM databases — the production
analog of the M3 demo seed-rewrite. Use it for any environment that ran in the window
**between the V0023 identities-registry backfill and the M1 born-unified deploy** (accounts
created in that window carry `identity_id = NULL`). On a fresh, contiguous deploy it is a
**net-zero safety net** (no orphans exist).

> **Non-destructive by construction.** Every step is additive, idempotent, and never
> overwrites a non-NULL `identity_id`, never deletes a user, and never email-auto-merges two
> pre-existing records (ADR-034 § 1.3). Safe to re-run.

## Why three steps (cross-DB)

`account_db`, `auth_db`, `admin_db` are physically separate — no cross-DB SQL/FK. So the
backfill is per-store, in dependency order:

| Store | Mechanism | Trigger |
|---|---|---|
| `account_db.accounts` / `identities` | Flyway migration `V0024` (in-DB) | **Automatic** on account-service deploy |
| `auth_db.credentials` | account-service → auth-service push endpoint | **Manual** (admin-triggered, after V0024) |
| `admin_db.admin_operators` | opt-in audited link surface (NOT auto) | **Per-operator**, deliberate |

## Procedure

### 1. account_db — automatic (Flyway V0024)

Runs on account-service startup. For each account with `identity_id IS NULL` it mints a
same-origin `(tenant, email)` identity (reusing an existing one if present) and links it.
Verify:

```sql
-- expect 0 after V0024
SELECT COUNT(*) FROM accounts WHERE identity_id IS NULL;
```

### 2. auth_db — trigger the credential propagation (after V0024)

account-service reads the resolved `account_id → identity_id` bindings and pushes them to
auth-service, which writes `credentials.identity_id` with the idempotent M2 writer.

```bash
# internal endpoint — GAP client_credentials Bearer JWT (real profiles)
curl -X POST "$ACCOUNT_BASE/internal/identity-backfill/credentials" \
     -H "Authorization: Bearer $INTERNAL_JWT"
# → { "accountsScanned": <n>, "credentialsUpdated": <m> }
```

Re-run is safe: already-linked credentials report `credentialsUpdated: 0`. Verify:

```sql
-- expect 0 credentials whose account has an identity but the credential does not
SELECT COUNT(*) FROM credentials WHERE identity_id IS NULL;
```

(A credential may legitimately stay NULL if its account is itself unlinked — e.g. an account
whose mint never succeeded; rerun step 1's source or inspect that account.)

### 3. admin_db operators — opt-in link (NOT automated)

Operator↔consumer linking is the privilege-escalation-sensitive merge, so it is **never**
auto-backfilled. For each operator that should be linked to a consumer identity, an authorized
admin invokes the audited link surface:

```
PATCH /api/admin/operators/{operatorId}/identity:link
```

This sets `admin_operators.identity_id` to the matching consumer account's central identity
and emits the `OPERATOR_IDENTITY_LINK` audit action. `oidc_subject`/email is necessary but
**not** sufficient — the explicit, audited link is what authorizes.

## Rollback / safety

- No rollback is required for re-running (idempotent). To "undo" a mistaken link, use the
  audited unlink surface (operators) — `accounts`/`credentials` identity values are never
  overwritten, so there is nothing to revert there.
- If step 2 reports a transport error (`AuthServiceUnavailable`), nothing partial is lost —
  re-run; the `IS NULL` guard makes it converge.

## Out of scope

`account_roles` re-key to `identity_id`, a dedicated identity-service, and the async
`IdentityAssigned` writer (P3-C) remain deferred follow-ups (ADR-036 § 3.3).
