# auth-service — DEPRECATED

> **This service has been decommissioned as of 2026-05-04.**

## Deprecation Summary

| Field | Value |
|-------|-------|
| Deprecated | 2026-05-04 |
| Replaced by | IAM (Global Account Platform) OIDC |
| Deprecation PRs | PR #145 (TASK-MONO-027), PR #148 (TASK-FE-067), this PR (TASK-BE-132) |

## Reason

The ecommerce platform's self-hosted `auth-service` issued HS256 JWTs and
handled Google / Naver social login plus admin account seeding. This
responsibility has been transferred to the Global Account Platform (IAM), which
provides a standard OIDC Authorization Server (Spring Authorization Server,
RS256/JWKS).

The migration was completed in three steps:

1. **TASK-MONO-027** (PR #145) — ecommerce gateway switched from auth-service
   JWKS to `IAM /oauth2/jwks`. `TenantClaimValidator` + `AllowedIssuersValidator`
   added to enforce tenant isolation.
2. **TASK-FE-067** (PR #148) — `web-store` and `admin-dashboard` migrated from
   direct auth-service calls to NextAuth v5 + IAM OIDC
   (`/oauth2/authorize`, `/oauth2/token`).
3. **TASK-BE-132** (this PR) — auth-service removed from `docker-compose.yml`,
   `settings.gradle`, CI, k8s manifests, and `.env.example`.

## Replacement

- **Token issuance / login**: `projects/iam-platform/` — IAM
  Authorization Server at `http://iam.local` (dev) or the production IAM
  cluster.
- **Integration spec**: `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md`
- **IAM auth contract**: `projects/iam-platform/specs/contracts/http/auth-api.md`

## Source Code

The source code in `apps/auth-service/` is preserved for history but is
excluded from the Gradle build (removed from `settings.gradle`). A full source
deletion is deferred to a future cleanup task once the team is confident the
history is no longer needed.
