# Local Development — SCM Platform

How to bring up the SCM (supply chain management) stack locally so all its services and
backing resources are operable.

## Bring-up

This project follows the shared **Local Bring-Up Sequence** — see
[`TEMPLATE.md § Local Network Convention → Local bring-up sequence`](../../../../TEMPLATE.md#local-bring-up-sequence)
for the full ordered procedure and the cross-project matrix.

| | |
|---|---|
| Up / down | `pnpm scm:up` / `pnpm scm:down` |
| Status / logs | `pnpm scm:ps` / `pnpm scm:logs` |
| Entry hostname(s) | `scm.local` |
| Needs IAM first | ✅ `pnpm iam:up` before this stack |

Quick start (from repo root):

```bash
# one-time: register *.local hosts — see TEMPLATE.md § One-time developer setup
pnpm traefik:up                                   # shared Traefik (once per session)
pnpm iam:up                                        # OIDC provider — REQUIRED before this stack
cp projects/scm-platform/.env.example projects/scm-platform/.env   # one-time
pnpm scm:up
pnpm scm:ps                                        # cold boot takes minutes
curl -i http://scm.local/actuator/health
```

## Services & resources

Authoritative inventory: [`docker-compose.yml`](../../docker-compose.yml). At a glance:

- **Edge**: `scm-platform-gateway` (`scm.local`, IAM OIDC consumer).
- **Backend services**: `scm-platform-procurement`, `scm-platform-demand-planning`, `scm-platform-inventory-visibility`.
- **Backing resources**: `scm-platform-postgres`, `scm-platform-redis`, `scm-platform-kafka`.

## Project-specific notes

- **IAM hard dependency**: gateway validates OIDC tokens against `http://iam.local` — `pnpm iam:up` first.
- Host-specific operational concerns are developer-environment notes, not part of this doc.
