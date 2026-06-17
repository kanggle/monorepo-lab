# federation-hardening-e2e — Host Port Registry

Single source of truth for **host-published** ports across the `federation-hardening-e2e`
docker stack (base committed file + the uncommitted demo overlays). This registry exists
because overlays are authored independently with no central allocation — twice a new overlay
grabbed an already-used port (the `ledger` ↔ `erp-read-model` `18097` clash, reactively moved
to `18197`). **Consult and update this table before assigning a host port in any overlay.**

> Scope note: this documents *host* port bindings only. Service-to-service traffic inside the
> `fed-e2e` bridge network uses **container names + container ports** (8080/5432/…) and is
> unaffected by any host-port choice. Except for the three functional ports below, host ports
> are **debug-only** (DBeaver/psql/curl from the host) and could be any free value — what matters
> is that they do not collide.

## Reserved — functional (NEVER reassign)

These are reachable by a **browser on the host** or carry an identity baked into tokens, so the
host port is load-bearing, not arbitrary.

| Host | Container | Service | Why fixed |
|---|---|---|---|
| `8081` | `8081` | `auth-service` (SAS OIDC AS) | OIDC issuer `http://auth-service:8081` is embedded in every JWT `iss` claim + discovery + JWKS URI. `auth-service` resolves to `127.0.0.1` via the host `hosts` file, so the host port **must equal** the container port — it cannot be remapped. |
| `3000` | `3000` | `console-web` | Operator console; Playwright + browser `CONSOLE_BASE_URL`. |
| `3001` | — | `web-store` (standalone, *not* in these compose files) | Customer storefront dev server (`pnpm dev --port 3001`); reserved so the console and storefront can run side by side. |
| `8080` | — | `web-store` gateway socat proxy (standalone) | `webstore-gw-proxy` publishes `ecommerce-gateway:8080` to the host for the storefront. Reserved to avoid clashing with the demo. |

## Infrastructure / datastores

| Host | Container | Service | Source file |
|---|---|---|---|
| `13307` | `3306` | `mysql` (shared: iam `auth_db` + finance + erp DBs) | base |
| `16380` | `6379` | `redis` | base |
| `10428` | `10428` | `victoriatraces` | base |
| `15432` | `5432` | `wms-postgres` (`master_db`) | base |
| `15433` | `5432` | `scm-postgres` (`scm_procurement`) | base |
| `15434` | `5432` | `wms-admin-postgres` (`admin_db`) | base |
| `15435` | `5432` | `scm-inv-postgres` (`scm_inventory_visibility`) | base |
| `15436` | `5432` | `scm-replenishment-postgres` | `…replenishment.yml` |
| `19092` | `19092` | `redpanda` (replenishment Kafka) | `…replenishment.yml` |

Next free datastore port: **`15437`** (then `15438`, …).

## Application services (debug-only host ports)

| Host | Container | Service | Domain | Source file |
|---|---|---|---|---|
| `18082` | `8082` | `account-service` | iam | base |
| `18085` | `8085` | `admin-service` | iam | base |
| `18086` | `8080` | `finance-account-service` | finance | base |
| `18090` | `8080` | `console-bff` | console | base |
| `18091` | `8081` | `wms-master-service` | wms | base |
| `18092` | `8080` | `scm-procurement-service` | scm | base |
| `18093` | `8080` | `erp-masterdata-service` | erp | base |
| `18094` | `8086` | `wms-admin-service` | wms | base |
| `18095` | `8080` | `scm-inventory-visibility-service` | scm | base |
| `18096` | `8080` | `ecommerce-gateway` | ecommerce | `…demo.yml` |
| `18097` | `8080` | `ledger-service` | finance | `…ledger.yml` |
| `18098` | `8080` | `erp-approval-service` | erp | `…erp-fullstack.yml` |
| `18099` | `80` | `erp-gateway` (nginx fan-out) | erp | `…erp-fullstack.yml` |
| `18100` | `8080` | `scm-replenishment-service` | scm | `…replenishment.yml` |
| `18197` | `8080` | `erp-read-model-service` | erp | `…erp-fullstack.yml` (moved off `18097` — see incident) |

> `ecommerce.yml` / `ecommerce-extra.yml` (product/order/user/promotion/shipping/notification +
> payment/review) publish **no** host ports — all reached internally via `ecommerce-gateway`.

Allocated app block: `18082`–`18100` (dense) + `18197`. **Next free app port: `18101`** (continue
`18101`–`18196`; `18197`–`18199` is the tail band, `18197` already used).

## Verified collision-free

As of TASK-MONO-293, the full overlay set
(`base` + `.demo` + `.ledger` + `.erp-fullstack` + `.replenishment` + `.ecommerce` + `.ecommerce-extra`)
publishes the union of the host ports above with **no duplicates**.

## Allocation rule (for the next overlay)

1. Pick the **next free** host port from the band above (`18101+` for an app service, `15437+`
   for a datastore). Container ports stay as the service's real port — only the host side moves.
2. **Add a row here in the same change** that introduces the port.
3. Never touch the *Reserved — functional* ports (`8081`, `3000`, `3001`, `8080`).
4. Some scripts/fixtures hard-code a debug host port (e.g. the `/audit` recipe posts to
   `localhost:18085`). Renumbering an **existing** port therefore ripples into throwaway verify
   scripts — prefer appending new ports over renumbering.

## Why no renumbering

The existing 18082–18100 numbers are non-domain-aligned (services grabbed the next sequential
value as they were added). A clean per-domain block scheme (`181xx`=iam, `182xx`=wms, …) was
considered and **rejected**: host debug ports are functionally arbitrary, so renumbering buys no
runtime benefit while breaking every verify script / muscle-memory reference to the current
numbers. This registry fixes the actual defect — the absence of a central allocation table — at
zero ripple.
