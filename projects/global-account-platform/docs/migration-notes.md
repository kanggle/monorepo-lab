# Migration Notes — global-account-platform into monorepo-lab

**Date:** 2026-04-30
**Task:** TASK-MONO-017
**Pattern:** direct-include (same as ecommerce-microservices-platform)

## What changed

### Monorepo additions (root layer)

| Path | Description |
|---|---|
| `rules/domains/saas.md` | SaaS domain rules (Identity/Account/Security/Audit/Admin bounded contexts) |
| `rules/traits/regulated.md` | GDPR/PIPA compliance rules (R1–R10) |
| `rules/traits/audit-heavy.md` | Immutable audit trail rules (A1–A10) |
| `libs/java-common/.../resilience/ResilienceClientFactory` | Resilient HTTP client factory (Resilience4j CB + Retry) |
| `libs/java-messaging/.../event/BaseEventPublisher` | Standard 7-field event envelope publisher |
| `libs/java-messaging/.../event/EventSerializationException` | JSON serialization failure wrapper |
| `libs/java-messaging/.../outbox/OutboxFailureHandler` | Functional interface for publish failure callbacks |
| `libs/java-messaging/.../outbox/OutboxProperties` | `@ConfigurationProperties(prefix="outbox")` topic mapping |
| `libs/java-messaging/.../outbox/OutboxSchedulerConfig` | Context-scoped `ThreadPoolTaskScheduler` for outbox polling |
| `libs/java-messaging/.../outbox/OutboxMetricsAutoConfiguration` | Micrometer counter auto-config for publish failures |
| `libs/java-security/.../pii/PiiMaskingUtils` | PII masking utilities (email/phone/IP/fingerprint) |

### Removed from projects/global-account-platform/

The following were present in the standalone repo but are served by the monorepo root:

- `libs/` — replaced by root `libs/` (with additions merged above)
- `platform/` — replaced by root `platform/`
- `rules/` — replaced by root `rules/` (with promotions above)
- `.claude/` — replaced by root `.claude/`
- `.github/` — GitHub only reads `.github/` at repo root; nested copy is dead
- `CLAUDE.md` — replaced by root `CLAUDE.md`
- `TEMPLATE.md` — replaced by root `TEMPLATE.md`
- `settings.gradle` — replaced by root `settings.gradle` (project registered via direct-include)
- `gradle/`, `gradlew`, `gradlew.bat`, `gradle.properties` — replaced by root Gradle wrapper
- `scripts/` — monorepo-level scripts at root `scripts/`; project-specific ops moved here

### Port namespace

Applied `${PORT_PREFIX:-3}XXXX` convention to `docker-compose.yml`.
All services default to PORT_PREFIX=3 host ports when run standalone.

## OutboxPollingScheduler note

The standalone repo had a concrete `OutboxPollingScheduler` (constructor-injected
`ThreadPoolTaskScheduler`, `OutboxProperties`, `OutboxFailureHandler`). The monorepo
already has an abstract version used by WMS and ecommerce. The new supporting classes
(`OutboxProperties`, `OutboxSchedulerConfig`, `OutboxFailureHandler`,
`OutboxMetricsAutoConfiguration`) were promoted to `libs/java-messaging`.

The concrete scheduler for global-account apps is deferred to TASK-MONO-018 (CI wiring),
where each app will register a project-level `@Configuration` that wires the scheduler.

## Standalone catch-up (2026-04-30, post-TASK-MONO-017)

The original TASK-MONO-017 import captured standalone commit `9830ecb` (TASK-BE-230
review approval). After the import, work on the standalone progressed to commit
`34ef5e9` adding TASK-BE-234..247 + TASK-FE-025..026 (e2e platform fixes, OAuth client
hardening, signup half-commit idempotency, admin-web env schema, dashboard tab boundary).

To preserve that work without losing it on the next `sync-portfolio.sh` force-push,
the 39 changed `apps/*`, `specs/*`, `tasks/done/*` files were copied forward from
standalone master into `projects/global-account-platform/`.

### Skipped during catch-up

- `libs/java-messaging/.../OutboxPollingScheduler.java` — standalone applied
  `ApplicationReadyEvent`-based lifecycle + idempotency guard (TASK-BE-243, BE-245)
  to the standalone's *concrete* scheduler. The monorepo's libs class is
  *abstract* with `@Scheduled`-driven polling (subclasses extend per-service in
  TASK-MONO-018). The race condition that TASK-BE-243 fixes does not apply to
  `@Scheduled`-driven invocation — Spring waits for context init before firing
  scheduled methods. Kept the monorepo design; recorded the design difference here.
- `platform/testing-strategy.md` — monorepo has its own root `platform/` copy.
  Standalone's edits to this file were intended for the standalone-local platform/
  folder, which Phase 4 of TASK-MONO-017 deleted.
- `.github/dependabot.yml` — monorepo has its own root `.github/` configuration.

### Renumbered

PR #96 originally drafted `TASK-BE-234`, `TASK-BE-235`, `TASK-BE-236` for the
multi-tenancy gap-fill work. Those IDs are now occupied by standalone fix tasks.
Renumbered to `TASK-BE-248`, `TASK-BE-249`, `TASK-BE-250` to start fresh after the
standalone's last assigned number (`TASK-BE-247`).

---

## admin-web retirement (2026-05-18 · ADR-MONO-013 Phase 3 · TASK-BE-299)

**Date:** 2026-05-18
**Task:** TASK-BE-299 (GAP project-internal, spec-first)
**Governance:** [ADR-MONO-013](../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
§ D4 (parity-gated retirement) + § D6 Phase 3 + § 6 (gate satisfied);
[ADR-MONO-014](../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md)
+ [ADR-MONO-015](../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md).

### What changed

GAP `admin-web` (the operator-only Next.js console — GAP's only `frontend-app`)
was **retired**. Its entire operator surface was **absorbed by the unified
[`projects/platform-console/`](../../platform-console/PROJECT.md)** (ADR-MONO-013
Model B — the console is the single UI). GAP returns to **backend-only IdP**.

| Removed | Replaced by |
|---|---|
| `apps/admin-web/` (deployable unit, ~95 files incl. `tests/e2e/*`) | platform-console `apps/console-web/` |
| `pnpm-workspace.yaml` / `pnpm-lock.yaml` | — (GAP is now pure Java/Gradle, no JS workspace) |
| `specs/services/admin-web/{architecture,dependencies,observability}.md` | git history (`git log --follow`) |
| `specs/services/admin-web/overview.md` (active spec) | rewritten → **RETIRED record** (tombstone) |
| `PROJECT.md` `service_types: [...,frontend-app,...]` | `[rest-api, event-consumer, identity-platform]` + § "admin-web — RETIRED" record |

### Parity gate (satisfied before removal)

Retirement was **not** a silent delete (ADR-MONO-013 § D4). The console reached
**verified operator parity** first: [ADR-MONO-013 § 6](../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
additive note + [console-integration-contract § 3](../../platform-console/specs/contracts/console-integration-contract.md)
— a 16/16 verified parity matrix, programmatically attested by
`projects/platform-console/apps/console-web/tests/unit/parity-verification.test.ts`.
Absorption path = platform-console `TASK-PC-FE-002…006` + GAP `TASK-BE-296`
(console OIDC client + product registry) / `TASK-BE-298` (operator token
exchange) / `TASK-BE-299` (this retirement). **Operator capability lost: none.**

### Monorepo-level ripple (same atomic PR)

Removing GAP's only `frontend-app` deterministically invalidated shared files
(authorized by ADR-MONO-013 § D6 Phase 3; CLAUDE.md § Cross-Project Changes
one-atomic-PR): root `package.json` dead `gap:{install,dev,build,lint,admin-web,pnpm}`
scripts removed (docker-compose `gap:*` kept); root `README.md` GAP row +
`docs/project-overview.md` § 2.2 GAP service map updated to backend-only.

### Not changed

No GAP backend code, contract, Gradle module, Flyway migration, or CI workflow
— `admin-web` was a thin consumer (owned no contract/persistence; not a Gradle
module; zero `admin-web` references in `.github/workflows/*`). Historical
`tasks/done/TASK-FE-*` / `TASK-BE-275/290` references are an immutable audit
trail, left verbatim.
