# Local Development — IAM Platform

How to bring up the IAM stack locally. **IAM is the shared identity provider** — every
other project's gateway validates OIDC tokens against `http://iam.local/oauth2/jwks`, so
this stack must be healthy before any consumer project is started.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm iam:up` / `pnpm iam:down` |
| Bootrun (local JVM overlay) | `pnpm iam:bootrun` |
| Status / logs | `pnpm iam:ps` / `pnpm iam:logs` |
| Entry hostname(s) | `iam.local` (also `kafka.iam.local`, `grafana.iam.local`) |
| Needs IAM first | — (this **is** the provider; it has no OIDC dependency) |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
cp projects/iam-platform/.env.example projects/iam-platform/.env   # one-time
pnpm iam:up
pnpm iam:ps                                        # wait for healthy
curl -i http://iam.local/actuator/health
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Identity services**: Spring Authorization Server + gateway (issues/validates OIDC tokens) — see compose.
- **Backing resources**: `iam-mysql`, `iam-redis`, `iam-kafka` (+ `iam-kafka-init`), `iam-kafka-ui`.
- **Observability**: `iam-prometheus`, `iam-alertmanager`, `iam-loki`, `iam-promtail`, `iam-grafana`.

## Project-specific notes

- **Provider ordering**: bring `iam:up` up *before* any consumer stack (ecommerce, wms, fan, scm, erp, finance, console). Consumer gateways fail fast when `iam.local` JWKS is unreachable.
- Backing store is **MySQL**, not Postgres.
- Host-specific operational concerns (memory pressure, cold-start timing) are developer-environment notes, not part of this doc.
