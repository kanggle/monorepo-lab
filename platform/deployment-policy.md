# Deployment Policy

Defines how services are built, packaged, and deployed.

---

# Packaging

- Each service is packaged as a Docker image.
- Base image: `eclipse-temurin:21-jre-alpine` (or equivalent slim JRE).
- Images must not contain source code or build tools.
- Image tag format: `{service-name}:{git-sha}` (e.g. `<service-name>:abc1234`).

---

# Build

- All services are built via Gradle. Path syntax depends on repository shape:
  - Single-project: `./gradlew :apps:{service}:bootJar`
  - Monorepo: `./gradlew :projects:{project}:apps:{service}:bootJar`
- Build must succeed before any deployment.
- Tests must pass before building a production image.
- When a service `Dockerfile` / `.dockerignore` relies on **negation** patterns (e.g. `**/build` followed by `!apps/*/build/libs`), build with **BuildKit enabled** (`DOCKER_BUILDKIT=1` — the default for `docker compose build` / `docker buildx`). The legacy builder (`DOCKER_BUILDKIT=0`) does not honor `.dockerignore` re-include negation and silently drops the re-included paths (e.g. a prebuilt jar), producing a broken image.

---

# Environments

| Environment | Purpose | Auto-deploy |
|---|---|---|
| `local` | Developer machine | Manual |
| `dev` | Integration testing | On merge to `develop` |
| `staging` | Pre-production verification | On merge to `main` |
| `production` | Live traffic | Manual approval |

---

# Configuration

- All environment-specific configuration is injected via environment variables.
- No environment-specific config files are bundled into the Docker image.
- Secrets (DB passwords, JWT secret, API keys) are managed via a secrets manager (e.g. Vault, AWS Secrets Manager).
- Hard-coded secrets are forbidden.

---

# Health Check

- Kubernetes readiness probe: `GET /actuator/health` — 200 = ready.
- Kubernetes liveness probe: `GET /actuator/health` — 200 = alive.
- Startup probe timeout: 60 seconds.
- **docker-compose container healthchecks must target `127.0.0.1`, not `localhost`.** If the app binds IPv4-only while `localhost` resolves to IPv6 `::1` first, the probe gets connection-refused → false-negative `unhealthy` → dependents declared with `depends_on: { condition: service_healthy }` abort the whole stack bring-up. Use `curl -sf http://127.0.0.1:<port>/…` (or `wget`). Give slow images (e.g. Elasticsearch + plugins) a `start_period` that covers cold start.

---

# Rolling Update

- Use rolling update strategy for zero-downtime deployments.
- Minimum available replicas: 1 during rollout.
- New version must pass health checks before old version is terminated.

---

# Rollback

- Rollback is triggered automatically if health checks fail after deployment.
- Manual rollback: redeploy previous `git-sha` tagged image.

---

# Merge Freeze

- No non-critical merges to `main` during active incidents.
- Freeze windows must be communicated in advance.

---

# Change Rule

Changes to deployment infrastructure require documentation here and in related infrastructure configs before applying.
