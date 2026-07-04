# Local Development — ERP Platform

How to bring up the ERP stack locally so all its services and backing resources are
operable.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm erp:up` / `pnpm erp:down` |
| Status / logs | `pnpm erp:ps` / `pnpm erp:logs` |
| Entry hostname(s) | `erp.local` |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/erp-platform/.env.example projects/erp-platform/.env   # one-time
pnpm erp:up
pnpm erp:ps                                        # cold boot takes minutes
curl -i http://erp.local/actuator/health
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Edge**: `erp-platform-gateway` (`erp.local`, IAM OIDC consumer).
- **Backend services**: `erp-platform-masterdata`, `erp-platform-read-model`, `erp-platform-approval`, `erp-platform-notification`.
- **Backing resources**: `erp-platform-mysql`, `erp-platform-redis`, `erp-platform-kafka`.

## Project-specific notes

- **IAM hard dependency**: gateway validates OIDC tokens against `http://iam.local` — `pnpm iam:up` first.
- Backing store is **MySQL**, not Postgres.
- Host-specific operational concerns are developer-environment notes, not part of this doc.
