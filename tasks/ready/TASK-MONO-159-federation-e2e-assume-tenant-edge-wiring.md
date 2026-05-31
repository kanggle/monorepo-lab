# Task ID

TASK-MONO-159

# Title

Fix-forward for TASK-MONO-158 (ADR-MONO-020 D4) — wire the **auth→admin assume-tenant assignment-check edge** (TASK-BE-327) for the federation-hardening-e2e stack: (1) admin-service `/internal/**` inbound JWT chain config is env-driven from `OIDC_ISSUER_URL` (mirrors account-service; BE-327 only set `@Value` localhost defaults); (2) the federation compose sets `ADMIN_SERVICE_URL` on auth-service so `AdminAssignmentClient` reaches admin-service. Without these the assume-tenant exchange fail-closed-denies every tenant switch (403), so the MONO-158 A↔B e2e fails at the first switch.

# Status

ready

# Owner

backend

# Task Tags

- code
- e2e
- security

---

# Dependency Markers

- **fixes**: TASK-MONO-158 (ADR-020 D4 console switcher → assume-tenant flow) — its federation-e2e `tenant-switch-rescope.spec.ts` failed post-merge (run 26712964796: `switch to acme-corp` expected 200, **received 403**). All 7 other federation specs passed (no regression).
- **root cause = TASK-BE-327** config gap surfaced by the new auth→admin runtime edge: BE-327 added the admin `/internal/**` chain JWT config as `@Value` inline defaults (`localhost:8081`) only — NOT env-driven like account-service's application.yml. In any non-localhost deploy (the federation compose) admin cannot fetch the auth JWKS → rejects the GAP `client_credentials` JWT → 401 → auth `AdminAssignmentClient` fail-closed → `invalid_grant` → console `/api/tenant` 403. Compounded by the federation compose not setting `ADMIN_SERVICE_URL` on auth-service (`auth.admin-service.base-url` defaulted to `localhost:8084`).
- **model**: 분석=Opus 4.8 / 구현=Opus (config wiring; direct fix).

---

# Goal

Make the MONO-158 A↔B federation e2e GREEN by wiring the BE-327 auth→admin assume-tenant assignment-check edge for a non-localhost (containerised) deploy — the same env-driven pattern account-service / security-service already use for their `/internal/**` chains.

# Scope

## In scope

1. **admin-service `application.yml`** — add a root-level `internal.api.jwt` block deriving from `OIDC_ISSUER_URL` (verbatim mirror of `account-service/application.yml`):
   ```yaml
   internal:
     api:
       bypass-when-unconfigured: ${INTERNAL_API_BYPASS_WHEN_UNCONFIGURED:false}
       jwt:
         jwk-set-uri: ${OIDC_JWK_SET_URI:${OIDC_ISSUER_URL:http://localhost:8081}/oauth2/jwks}
         issuer: ${OIDC_ISSUER_URL:http://localhost:8081}
   ```
   The BE-327 `SecurityConfig` `@Value("${internal.api.jwt.jwk-set-uri:...}")` now resolves from this (the inline default is only a fallback). **Production fix**, not e2e-only: admin's `/internal` JWKS/issuer should be env-driven like its peers. fail-closed preserved (`bypass-when-unconfigured=false`).
2. **`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`** — add `ADMIN_SERVICE_URL: http://admin-service:8085` to the `auth-service` env (so `auth.admin-service.base-url` resolves to the admin container, not `localhost:8084`).

## Out of scope

- Any console-web / BFF / domain / seed / spec change (MONO-158 is correct; this is pure infra/config wiring).
- BE-327 producer logic (the exchange + assignment check are correct — only the deploy wiring was missing).

# Acceptance Criteria

- **AC-1**: admin-service boots under the `e2e` profile with the new `internal.api.jwt` block; its `/internal/operator-assignments/check` chain validates the GAP `client_credentials` JWT against `http://auth-service:8081/oauth2/jwks` (env-driven). GAP Integration (Testcontainers) GREEN — admin still boots, no `/api/admin/**` regression.
- **AC-2**: in the federation compose, auth-service's `AdminAssignmentClient` reaches `http://admin-service:8085/internal/operator-assignments/check`.
- **AC-3 (the proof)**: re-running `federation-hardening-e2e.yml` post-merge → **SUCCESS**, incl. `tenant-switch-rescope.spec.ts` (the A↔B switch flips finance/wms ↔ scm/erp). The other 7 specs stay GREEN.
- **AC-4 (scope-lock)**: changes = admin-service `application.yml` + the federation compose only.

# Related Specs / Code

- `projects/global-account-platform/apps/account-service/src/main/resources/application.yml` (the `internal.api.jwt` env-driven pattern mirrored) ; `projects/global-account-platform/apps/admin-service/src/main/java/.../infrastructure/config/SecurityConfig.java` (BE-327 `/internal` chain `@Value`) + `infrastructure/client/AdminAssignmentClient.java` (`auth.admin-service.base-url`).
- ADR-MONO-020 § 2 D2 (the assume-tenant exchange + assignment gate this wires).

# Edge Cases / Failure Scenarios

- **fail-closed preserved**: `bypass-when-unconfigured=false` — the bypass is `@WebMvcTest`-only; production/e2e still require a valid GAP cc JWT.
- **lazy JWKS**: `NimbusJwtDecoder.withJwkSetUri` fetches on first verification, so no auth→admin startup ordering dependency (the switch is a runtime event, both up).
- **port**: admin-service listens on 8085 in the compose (per `CONSOLE_REGISTRY_URL`/`CONSOLE_TOKEN_EXCHANGE_URL`); the auth default was `localhost:8084` (wrong host AND port).

# Notes

- Fix-forward sibling of TASK-MONO-155 (which fixed a MONO-154 federation-e2e regression post-merge). federation-e2e is workflow_dispatch/nightly (not PR-gated); AC-3 verified post-merge via `gh workflow run`.
- Meta (the recurring lesson): **a new cross-service runtime edge needs its deploy wiring (base-url + the receiver's inbound-JWT JWKS/issuer) in every compose that exercises it** — BE-318b/c token-endpoint footprint + BE-322 registry-footprint class. BE-327's admin `/internal` JWT config should have been env-driven (application.yml) not `@Value`-localhost from the start.
