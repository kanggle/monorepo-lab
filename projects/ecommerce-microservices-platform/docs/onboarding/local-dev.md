# Local Development — E-Commerce Microservices Platform

How to bring up the e-commerce stack locally so all its services and backing resources are
operable. This is the largest stack in the monorepo (~30+ containers).

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm ecommerce:up` / `pnpm ecommerce:down` |
| Status / logs | `pnpm ecommerce:ps` / `pnpm ecommerce:logs` |
| Entry hostname(s) | `ecommerce.local` (gateway API), `web.ecommerce.local` (storefront) |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/ecommerce-microservices-platform/.env.example \
   projects/ecommerce-microservices-platform/.env # one-time
pnpm ecommerce:up
pnpm ecommerce:ps                                  # cold boot takes several minutes
curl -i http://ecommerce.local/actuator/health     # gateway
# open http://web.ecommerce.local in a browser      # storefront
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Edge**: `gateway-service` (`ecommerce.local`, GAP/IAM OIDC consumer), `web-store` (`web.ecommerce.local`, Next.js storefront).
- **Backend services** (11): product, search, user, order, payment, batch-worker, shipping, review, promotion, notification, settlement.
- **Per-service Postgres** (10): product / order / payment / user / batch / shipping / review / promotion / notification / settlement.
- **Shared infra**: `kafka` (KRaft), `redis`, `elasticsearch` (nori analyzer), `minio` (+ `minio-init`, product images).
- **Observability**: `jaeger` (UI on host `:16686`), `prometheus`, `alertmanager`, `grafana`, `loki`, `promtail`.

## Project-specific notes

- **IAM hard dependency**: the gateway validates GAP/IAM OIDC tokens (`http://iam.local/oauth2/jwks`, `OIDC_ISSUER_URL`) and will not come up healthy if `iam.local` is unreachable. `pnpm iam:up` first.
- **Elasticsearch cold start** is slow (nori plugin + single-node bootstrap, ~120s `start_period`); the search stack is the last to become healthy.
- **Object storage**: product images are served from MinIO; `minio-init` is a one-shot bucket bootstrap that exits after running.
- **WMS fulfillment loop** (order → pick/pack/ship → auto-SHIPPED) is a *separate cross-stack* wired under `tests/fulfillment-demo/` — not part of this standalone bring-up.
- Host-specific operational concerns (memory pressure, batch redeploy sizing) are developer-environment notes, not part of this doc.
