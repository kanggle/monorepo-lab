# Local Development — Fan Platform

How to bring up the fan-platform stack locally so all its services and backing resources
are operable.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm fan-platform:up` / `pnpm fan-platform:down` |
| Web frontend (dev) | `pnpm fan-platform:web` |
| Status / logs | `pnpm fan-platform:ps` / `pnpm fan-platform:logs` |
| Entry hostname(s) | `fan-platform.local` |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/fan-platform/.env.example projects/fan-platform/.env   # one-time
pnpm fan-platform:up
pnpm fan-platform:ps                               # cold boot takes minutes
curl -i http://fan-platform.local/actuator/health
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Edge**: `fan-platform-gateway` (`fan-platform.local`, IAM OIDC consumer).
- **Backend services**: `fan-platform-community`, `fan-platform-artist`, `fan-platform-membership`, `fan-platform-notification`.
- **Backing resources**: `fan-platform-postgres` (single shared DB), `fan-platform-redis`, `fan-platform-kafka`.

## Project-specific notes

- **IAM hard dependency**: the gateway fails fast if `iam.local` JWKS is unreachable — `pnpm iam:up` first. Browser login must go through the IAM login page (`iam.local`).
- Frontend dev server runs separately via `pnpm fan-platform:web`.
- Host-specific operational concerns are developer-environment notes, not part of this doc.
