# Traefik Reverse Proxy (shared dev infra)

Single shared Traefik v3 instance that routes hostname-based traffic across all monorepo projects. Per [ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) (Option C).

## What this provides

- **External hostname routing** — projects join the `traefik-net` network and register a hostname via Traefik labels. Traefik binds host ports `:80`/`:443`; each project's services bind no host ports.
- **Dashboard** at `http://localhost:8080` (dev only — no authentication).
- **Network**: `traefik-net` (external bridge, joined by every project that wants to be routed).

## Quick start

From monorepo root:

```bash
# Start Traefik
pnpm traefik:up

# Verify (dashboard)
open http://localhost:8080

# View logs
pnpm traefik:logs

# Stop
pnpm traefik:down
```

Or directly:

```bash
docker compose -f infra/traefik/docker-compose.yml up -d
docker compose -f infra/traefik/docker-compose.yml down
```

## Joining a project to Traefik

In a project's `docker-compose.yml`:

```yaml
services:
  gateway:
    expose: ["8080"]                              # internal-only, no host port
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.<project>.rule=Host(`<project>.local`)"
      - "traefik.http.services.<project>.loadbalancer.server.port=8080"
    networks:
      - traefik-net
      - <project>-net

  postgres:
    expose: ["5432"]                              # internal only
    networks: [<project>-net]

networks:
  traefik-net:
    external: true                                # shared with this Traefik stack
  <project>-net:
    driver: bridge
```

Then access via `http://<project>.local/...` from your machine.

## Hostname → 127.0.0.1 mapping (one-time setup)

`*.local` hostnames must resolve to `127.0.0.1` on your machine. Run the helper:

```bash
# Linux / macOS
bash scripts/dev-setup.sh

# Windows (Run as Administrator)
.\scripts\dev-setup.ps1
```

This appends entries to `/etc/hosts` (or `C:\Windows\System32\drivers\etc\hosts`). Idempotent — re-running is safe.

## Port overrides

If `:80`, `:443`, or `:8080` are taken on your machine, copy `infra/traefik/.env.example` to `infra/traefik/.env` and uncomment the overrides:

```bash
cp infra/traefik/.env.example infra/traefik/.env
```

Then access via the overridden port (e.g. `http://wms.local:8800/`).

## Database / queue tools (DBeaver, Redis Insight, Kafka UI)

External tools that need direct TCP access to backing services (postgres, redis, kafka) cannot reach `traefik-net` services from outside docker. See [docs/guides/dev-tooling.md](../../docs/guides/dev-tooling.md) for three approaches:

1. `docker exec` — recommended, no extra setup.
2. Per-developer `docker-compose.dev.yml` overlay — adds `ports:` for your machine only, uncommitted.
3. Traefik TCP routing — advanced, declares a TCP router with a unique hostname.

## Troubleshooting

### `Bind for 0.0.0.0:80 failed: port is already allocated`

Another process (Apache, Nginx, AirPlay on macOS) is using port 80. Either stop it or override `TRAEFIK_HTTP_PORT` in `infra/traefik/.env`.

### `Network traefik-net declared as external, but could not be found`

Traefik isn't running. Start it first: `pnpm traefik:up`.

### Dashboard not accessible

- Confirm `:8080` isn't blocked by another service.
- Check `pnpm traefik:logs` for startup errors.
- Verify the container is healthy: `docker compose -f infra/traefik/docker-compose.yml ps`.

### Project's hostname returns 404 or hangs

- Confirm the project container is running and joined to `traefik-net`.
- Confirm the Traefik labels include `traefik.enable=true` and the router rule.
- Check the dashboard's "HTTP → Routers" page to see if the route was discovered.

## Production note

This Traefik stack is **dev-only**:

- `--api.insecure=true` exposes the dashboard without auth.
- HTTP only — no TLS / certificate handling.
- No middleware (rate-limit, auth, etc.) configured.

Production deployment uses Kubernetes Ingress / cloud load balancers — not this compose file.
