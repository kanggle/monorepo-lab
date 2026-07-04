# Local Development — WMS Platform

How to bring up the WMS (warehouse management) stack locally so all its services and
backing resources are operable.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm wms:up` / `pnpm wms:down` |
| Bootrun (local JVM overlay) | `pnpm wms:bootrun` |
| Status / logs | `pnpm wms:ps` / `pnpm wms:logs` |
| Entry hostname(s) | `wms.local` (also `kafka.wms.local`, `grafana.wms.local`) |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/wms-platform/.env.example projects/wms-platform/.env   # one-time
pnpm wms:up
pnpm wms:ps                                        # cold boot takes minutes
curl -i http://wms.local/actuator/health
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Edge**: gateway (`wms.local`, IAM OIDC consumer).
- **Backend services**: admin, inventory, outbound, notification (Spring Boot) — see compose.
- **Backing resources**: `wms-postgres`, `wms-redis`, `wms-kafka` (+ `wms-kafka-init`), `wms-kafka-ui` (`kafka.wms.local`).
- **Observability**: `wms-prometheus`, `wms-alertmanager`, `wms-loki`, `wms-promtail`, `wms-grafana` (`grafana.wms.local`).

## Project-specific notes

- **IAM hard dependency**: gateway validates OIDC tokens against `http://iam.local` — `pnpm iam:up` first.
- **E-commerce fulfillment loop** (order → pick/pack/ship → auto-SHIPPED) is a *separate cross-stack* wired under `tests/fulfillment-demo/` against a shared Kafka — not part of this standalone bring-up.
- Host-specific operational concerns are developer-environment notes, not part of this doc.
