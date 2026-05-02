# Dev Tooling Guide — DB / Queue Tool Access

After the [Traefik hostname-routing migration](../../infra/traefik/README.md) (per [ADR-MONO-001](../adr/ADR-MONO-001-port-prefix-scaling.md)), backing services like PostgreSQL, Redis, and Kafka **no longer publish host ports**. They are reachable only from within the docker network.

This is the production-correct security model — DBs are never exposed to the host directly. But it complicates direct access from external GUI tools (DBeaver, Redis Insight, Kafka UI, MongoDB Compass, etc.).

This guide describes three approaches, ordered by safety and convenience.

---

## Method 1: `docker exec` (recommended for everyday queries)

Use when: quick interactive query, no GUI needed.

The simplest path: open a shell inside the container and use the native CLI.

### PostgreSQL

```bash
# wms-platform postgres
docker exec -it wms-postgres psql -U wms_user -d wms_db

# global-account-platform postgres (for any GAP service's DB)
docker exec -it gap-postgres psql -U auth_user -d auth_db
```

### Redis

```bash
docker exec -it wms-redis redis-cli
```

### Kafka

```bash
# List topics
docker exec -it wms-kafka kafka-topics --list --bootstrap-server localhost:9092

# Console consumer
docker exec -it wms-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic wms.inventory.received \
  --from-beginning
```

**Pros**: zero setup, always works, no security risk.
**Cons**: CLI only, no GUI.

---

## Method 2: Per-developer `docker-compose.dev.yml` overlay (recommended for GUI tools)

Use when: DBeaver / Redis Insight / Kafka UI etc. needed for ad-hoc inspection.

Create a **local-only, uncommitted** overlay file that adds `ports:` for your machine. Docker Compose merges it on top of the project's main `docker-compose.yml` only when explicitly invoked.

### Step 1 — Create the overlay (once per project)

`projects/<project>/docker-compose.dev.yml` (gitignored — never commit):

```yaml
services:
  postgres:
    ports:
      - "${LOCAL_POSTGRES_PORT:-15432}:5432"

  redis:
    ports:
      - "${LOCAL_REDIS_PORT:-16379}:6379"

  kafka:
    ports:
      - "${LOCAL_KAFKA_PORT:-19092}:9092"
```

Pick host ports that don't collide with anything else on your machine. Use prefixes like `1XXXX` (ecommerce), `2XXXX` (wms), `3XXXX` (GAP) for clarity, or whatever you prefer — these are local to your machine only.

### Step 2 — Bring up with the overlay

```bash
docker compose \
  --project-directory projects/wms-platform \
  -f projects/wms-platform/docker-compose.yml \
  -f projects/wms-platform/docker-compose.dev.yml \
  up -d
```

Or alias it as a personal shell function. The overlay is **never** referenced by CI or other developers — it's strictly your machine.

### Step 3 — Connect with your tool

DBeaver: `localhost:15432` (or whatever port you chose). Username/password from the project's `.env`.

### Add to `.gitignore`

Each project's `.gitignore` should include:

```
docker-compose.dev.yml
```

(Or the monorepo root's `.gitignore` can list `**/docker-compose.dev.yml`.)

**Pros**: full GUI tooling, isolated to your machine.
**Cons**: each developer maintains their own overlay; easy to forget to bring up with the overlay.

---

## Method 3: Traefik TCP routing (advanced)

Use when: you want a permanent named hostname for a TCP service (e.g., `wms-postgres.local:5432`), shared across team or scripts. Or when you want to expose a DB through a single shared port.

### Project docker-compose addition

Add a TCP router label to the postgres service:

```yaml
services:
  postgres:
    expose: ["5432"]
    labels:
      - "traefik.enable=true"
      - "traefik.tcp.routers.wms-postgres.rule=HostSNI(`wms-postgres.local`)"
      - "traefik.tcp.routers.wms-postgres.entrypoints=postgres"
      - "traefik.tcp.routers.wms-postgres.tls=true"
      - "traefik.tcp.services.wms-postgres.loadbalancer.server.port=5432"
    networks: [traefik-net, wms-net]
```

### Traefik infra addition

Add an entrypoint for postgres traffic in `infra/traefik/docker-compose.yml`:

```yaml
services:
  traefik:
    command:
      # ... existing flags ...
      - "--entrypoints.postgres.address=:5432"
    ports:
      - "5432:5432"
```

### Connect

Tool: `wms-postgres.local:5432`. SNI-based routing means multiple postgres instances can share the same `:5432` port via their unique SNI hostname.

**Pros**: production-correct, named endpoints, shareable scripts.
**Cons**: significantly more setup; TLS config needed for SNI; not all DB tools handle SNI well; overkill for ad-hoc queries.

---

## Recommendation

| Scenario | Method |
|---|---|
| Quick query in production-shaped envs | Method 1 (`docker exec`) |
| Daily GUI usage (DBeaver / Redis Insight / Kafka UI) | Method 2 (overlay) |
| Sharing exact endpoint URLs in team docs / scripts | Method 3 (Traefik TCP) |
| One-off | Method 1 |

For most monorepo workflows, **Method 1 + Method 2** cover ~95% of needs. Reserve Method 3 for cases where you've genuinely felt the friction of Methods 1/2.

---

## Why no host ports by default

- **Production parity**: AWS ECS / Kubernetes never expose DBs to the public; mirror that locally.
- **Security**: random docker port exposure is a well-known foot-gun (`postgres:5432` open to anyone on the LAN).
- **Cleanliness**: when CI runs all 7+ projects concurrently, no host port slot conflicts.

The ADR rationale is in [docs/adr/ADR-MONO-001-port-prefix-scaling.md](../adr/ADR-MONO-001-port-prefix-scaling.md).

---

## Related

- [infra/traefik/README.md](../../infra/traefik/README.md) — Traefik infrastructure usage
- [docs/adr/ADR-MONO-001-port-prefix-scaling.md](../adr/ADR-MONO-001-port-prefix-scaling.md) — decision rationale
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — bootstrap pattern for new projects
