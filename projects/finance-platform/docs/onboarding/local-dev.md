# Local Development — Finance Platform

How to bring up the finance stack locally so all its services and backing resources are
operable.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm finance:up` / `pnpm finance:down` |
| Status / logs | `pnpm finance:ps` / `pnpm finance:logs` |
| Entry hostname(s) | `finance.local` (only — see note below) |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/finance-platform/.env.example projects/finance-platform/.env   # one-time
pnpm finance:up
pnpm finance:ps                                    # cold boot takes minutes
curl -i http://finance.local/actuator/health
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Edge**: `finance-platform-gateway` (`finance.local`, IAM OIDC consumer) — **the only edge**. 🔴 `ledger-service` is **not** reachable at `ledger.local`; it is `expose:`-only and answers through the gateway. It did hold its own router once, and `TASK-MONO-357` / `ADR-MONO-048` removed it (a backend service on the shared edge violates `platform/api-gateway-policy.md` L13/L14). This line kept naming the dead hostname until 2026-08-15 (TASK-MONO-532).
- **Backend services**: `finance-platform-account`, `finance-platform-ledger`.
- **Backing resources**: `finance-platform-mysql` (account), `finance-platform-ledger-mysql` (ledger has its own DB), `finance-platform-redis`.

## Project-specific notes

- **IAM hard dependency**: gateway validates OIDC tokens against `http://iam.local` — `pnpm iam:up` first.
- Backing store is **MySQL**; the ledger service uses a **separate** MySQL instance (`finance-platform-ledger-mysql`).
- Host-specific operational concerns are developer-environment notes, not part of this doc.
