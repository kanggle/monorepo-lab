# Deployment Policy

Defines how services are built, packaged, and deployed.

---

# Packaging

- Each service is packaged as a Docker image.
- Base image: `eclipse-temurin:21-jre-alpine` (or equivalent slim JRE).
- Images must not contain source code or build tools.
- Image tag format: `{service-name}:{git-sha}` (e.g. `auth-service:abc1234`).

---

# Build

- All services are built via Gradle: `./gradlew :apps:{service}:bootJar`.
- Build must succeed before any deployment.
- Tests must pass before building a production image.

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
