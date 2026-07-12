# Local Development — Platform Console

How to bring up the platform-console stack locally. The console is a **thin aggregator** —
`console:up` runs only the web + BFF; it reads data from the *other* platform stacks, so a
useful console needs one or more upstream domain stacks reachable too.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down (thin standalone) | `pnpm console:up` / `pnpm console:down` |
| Full federated demo | `pnpm console-demo:up` / `pnpm console-demo:down` |
| Status / logs | `pnpm console:ps` / `pnpm console:logs` |
| Entry hostname(s) | `console.local` (web) — **the BFF has none, by design** (see below) |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start — thin standalone (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/platform-console/.env.example projects/platform-console/.env   # one-time
pnpm console:up
pnpm console:ps
# open http://console.local in a browser
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **`platform-console-web`** (`console.local`, Next.js console UI).
- **`platform-console-bff`** (**no hostname** — backend-for-frontend that fans out to upstream domain services). It holds no Traefik router: `console-web` calls it **server-side on the docker network** (`http://console-bff:8080`), and the browser never reaches it. A backend service on the shared edge would have no gateway in front of it — no rate limiting, no identity-header strip→enrich, no uniform error envelope (`platform/api-gateway-policy.md` L13/L14, enforced by `scripts/check-gateway-drift.sh`). TASK-MONO-362.

## Project-specific notes

- **Thin by design**: `console:up` is only web + BFF. Domain panels (ecommerce, wms, erp, …) render as *degraded / unavailable* unless the corresponding upstream stack is up and the BFF has that leg's outbound base URL configured.
- **Full local demo**: the richer, wired-together experience is the `federation-hardening-e2e` stack via `pnpm console-demo:up`, which bundles the console with upstream platforms. Prefer that for an end-to-end console demo.
- **IAM hard dependency**: console login goes through `iam.local` — `pnpm iam:up` first.
- Host-specific operational concerns (cold-start timeouts, per-leg env wiring, redeploy `--no-deps`) are developer-environment notes, not part of this doc.
