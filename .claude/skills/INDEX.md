# Skills Index

Use this index to find relevant skills for a task.
Read only the matched skill files — do not read unrelated ones.

---

## Available Skills

| Situation | Skill |
|---|---|
| Implement Spring Boot REST API | `backend/springboot-api/SKILL.md` |
| Implement Layered Architecture service | `backend/architecture/layered/SKILL.md` |
| Implement DDD Architecture service | `backend/architecture/ddd/SKILL.md` |
| Implement Hexagonal Architecture service | `backend/architecture/hexagonal/SKILL.md` |
| Implement Clean Architecture service | `backend/architecture/clean/SKILL.md` |
| Backend exception classes and global error handling | `backend/exception-handling/SKILL.md` |
| Backend input validation (DTO + domain) | `backend/validation/SKILL.md` |
| Backend DTO/entity mapping between layers | `backend/dto-mapping/SKILL.md` |
| Backend @Transactional usage and boundaries | `backend/transaction-handling/SKILL.md` |
| Backend test writing | `backend/testing-backend/SKILL.md` |
| Full backend implementation workflow | `backend/implementation-workflow/SKILL.md` |
| Backend refactoring patterns | `backend/refactoring/SKILL.md` |
| JWT token generation, refresh token rotation, token store | `backend/jwt-auth/SKILL.md` |
| Redis Sorted Set session management, concurrent limits | `backend/redis-session/SKILL.md` |
| Redis fixed-window rate limiting with Lua scripts | `backend/rate-limiting/SKILL.md` |
| Multi-provider OAuth 2.0 integration (Google, Naver) | `backend/oauth-provider/SKILL.md` |
| API gateway JWT filter, routing, header enrichment | `backend/gateway-security/SKILL.md` |
| Micrometer business metrics, OTel tracing, MDC | `backend/observability-metrics/SKILL.md` |
| Audit log entity, REQUIRES_NEW transaction pattern | `backend/audit-logging/SKILL.md` |
| Shared PageQuery/PageResult pagination DTOs | `backend/pagination/SKILL.md` |
| Scheduled tasks, outbox polling, batch jobs | `backend/scheduled-tasks/SKILL.md` |
| @Profile("standalone") in-memory fallbacks for local dev | `backend/standalone-profile/SKILL.md` |
| Implement Kafka event publisher/consumer | `messaging/event-implementation/SKILL.md` |
| Implement transactional outbox for reliable events | `messaging/outbox-pattern/SKILL.md` |
| Implement consumer retry and dead-letter queue | `messaging/consumer-retry-dlq/SKILL.md` |
| Implement idempotent event consumer | `messaging/idempotent-consumer/SKILL.md` |
| Database schema changes with Flyway | `database/schema-change-workflow/SKILL.md` |
| Database index design and optimization | `database/indexing/SKILL.md` |
| Flyway migration management | `database/migration-strategy/SKILL.md` |
| Transaction boundary design | `database/transaction-boundary/SKILL.md` |
| Implement Feature-Sliced Design app (Next.js) | `frontend/architecture/feature-sliced-design/SKILL.md` |
| Implement Layered by Feature app (Next.js) | `frontend/architecture/layered-by-feature/SKILL.md` |
| Full frontend implementation workflow | `frontend/implementation-workflow/SKILL.md` |
| Frontend API client setup and usage | `frontend/api-client/SKILL.md` |
| Frontend state management with React Query | `frontend/state-management/SKILL.md` |
| Frontend form handling patterns | `frontend/form-handling/SKILL.md` |
| Frontend loading and error state handling | `frontend/loading-error-handling/SKILL.md` |
| Frontend test writing (Vitest + Testing Library) | `frontend/testing-frontend/SKILL.md` |
| Reusable component primitives, a11y, design tokens | `frontend/component-library/SKILL.md` |
| Bundle analysis, code splitting, image/font, Core Web Vitals | `frontend/bundling-perf/SKILL.md` |
| Next.js App Router server actions and revalidation | `frontend/server-actions/SKILL.md` |
| Frontend auth: HttpOnly cookies, refresh proxy, server boundary | `frontend/auth-client/SKILL.md` |
| Elasticsearch index management | `search/elasticsearch-index/SKILL.md` |
| Elasticsearch query building | `search/elasticsearch-query/SKILL.md` |
| Search index sync via events | `search/index-sync/SKILL.md` |
| Test type selection and coverage strategy | `testing/test-strategy/SKILL.md` |
| API and event contract testing | `testing/contract-test/SKILL.md` |
| End-to-end integration testing | `testing/e2e-test/SKILL.md` |
| Test data and fixture management | `testing/fixture-management/SKILL.md` |
| Testcontainers setup and usage | `testing/testcontainers/SKILL.md` |
| Docker image build patterns | `infra/docker-build/SKILL.md` |
| Kubernetes deployment manifests | `infra/kubernetes-deploy/SKILL.md` |
| Terraform module patterns | `infra/terraform-module/SKILL.md` |
| GitHub Actions CI/CD pipelines | `infra/ci-cd/SKILL.md` |
| Prometheus/Grafana/Loki/Alertmanager stack as code | `infra/monitoring-stack/SKILL.md` |
| Secrets storage, sealed secrets, ESO, rotation | `infra/secrets-management/SKILL.md` |
| Service mesh (Linkerd/Istio) mTLS, traffic split, authz | `infra/service-mesh/SKILL.md` |
| K8s right-sizing, HPA, spot/ARM, PDB, cost levers | `infra/cost-optimization/SKILL.md` |
| Cache tier selection, TTL, invalidation policy | `cross-cutting/caching/SKILL.md` |
| API/event versioning, deprecation, coexistence | `cross-cutting/api-versioning/SKILL.md` |
| End-to-end observability setup (logs, metrics, traces, alerts) | `cross-cutting/observability-setup/SKILL.md` |
| OWASP Top 10 hardening checklist | `cross-cutting/security-hardening/SKILL.md` |
| Performance targets, profiling, load testing | `cross-cutting/performance-tuning/SKILL.md` |
| Set up a `rest-api` service end-to-end | `service-types/rest-api-setup/SKILL.md` |
| Set up an `event-consumer` service end-to-end | `service-types/event-consumer-setup/SKILL.md` |
| Set up a `batch-job` service end-to-end | `service-types/batch-job-setup/SKILL.md` |
| Set up a `grpc-service` end-to-end | `service-types/grpc-service-setup/SKILL.md` |
| Set up a `graphql-service` end-to-end | `service-types/graphql-service-setup/SKILL.md` |
| Set up an `ml-pipeline` service end-to-end | `service-types/ml-pipeline-setup/SKILL.md` |
| Set up a `frontend-app` service end-to-end | `service-types/frontend-app-setup/SKILL.md` |
| Set up an `identity-platform` service end-to-end | `service-types/identity-platform-setup/SKILL.md` |
| Code review checklist | `review-checklist/SKILL.md` |

---

## Default Skill Sets by Task Type

| Task Type | Skills to Read |
|---|---|
| Add backend API | `springboot-api`, matched architecture skill, `exception-handling`, `validation`, `dto-mapping`, `transaction-handling`, `pagination`, `testing-backend` |
| Add auth feature | `jwt-auth`, `redis-session`, `rate-limiting`, `audit-logging`, `testing-backend` |
| Add OAuth provider | `oauth-provider`, `jwt-auth`, `redis-session` |
| Add event publishing | `event-implementation`, `outbox-pattern`, `scheduled-tasks`, `testing-backend` |
| Add event consumer | `event-implementation`, `consumer-retry-dlq`, `idempotent-consumer`, `testing-backend` |
| Add database migration | `schema-change-workflow`, `migration-strategy`, `indexing` |
| Add frontend screen | matched architecture skill, `api-client`, `state-management`, `form-handling`, `loading-error-handling`, `component-library`, `testing-frontend` |
| Add search feature | `elasticsearch-index`, `elasticsearch-query`, `index-sync` |
| Add gateway route/filter | `gateway-security` |
| Add observability/metrics | `observability-metrics` |
| Add standalone support | `standalone-profile` |
| Integration task | backend skills + `e2e-test` + specs as needed |
| Infrastructure task | `docker-build`, `kubernetes-deploy`, `ci-cd` as needed |
| Code review | `review-checklist` + related architecture skill + `testing-backend` or `testing-frontend` |
| Set up new service | matching `service-types/<type>-setup` + `cross-cutting/observability-setup` + `cross-cutting/security-hardening` |
| Add caching layer | `cross-cutting/caching` + `backend/redis-session` (if Redis) |
| Bump API version | `cross-cutting/api-versioning` + `testing/contract-test` |
| Wire observability for a service | `cross-cutting/observability-setup` + `backend/observability-metrics` + `infra/monitoring-stack` |
| Performance investigation | `cross-cutting/performance-tuning` + `cross-cutting/observability-setup` |
| Security hardening pass | `cross-cutting/security-hardening` + `backend/jwt-auth` (if auth) |
