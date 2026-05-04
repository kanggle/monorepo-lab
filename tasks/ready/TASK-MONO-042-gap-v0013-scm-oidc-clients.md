# Task ID

TASK-MONO-042

# Title

GAP V0013 / V0015 Flyway seed — scm tenant + OIDC client 등록

# Status

ready

# Owner

backend

# Task Tags

- code
- gap
- oidc
- bootstrap
- scm

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[TASK-MONO-040](./TASK-MONO-040-scm-platform-bootstrap.md) (scm-platform 부트스트랩) 의 **선행 인프라**. scm-platform 의 gateway-service 가 GAP RS256 JWT 를 OAuth2 Resource Server 로 검증하려면, GAP 측에 (1) `scm` tenant row 와 (2) `scm-*` OIDC client 가 사전 등록되어 있어야 한다. 본 task 는 두 Flyway seed 를 추가한다:

- **account-service V0015** — `tenants` 테이블에 `scm` row (`B2B_ENTERPRISE`) seed.
- **auth-service V0013** — `oauth_clients` 테이블에 `scm-platform-internal-services-client` (client_credentials grant) + `oauth_scopes` 에 `scm.read` / `scm.write` 등록.

선행 패턴: TASK-MONO-019 (V0010 wms), TASK-MONO-026 (V0011 fan-platform), TASK-MONO-027 (V0012 ecommerce client + V0014 ecommerce tenant). 본 task 는 그 4번째 적용 사례.

**왜 040 의 in-scope 가 아니고 별도 task 인가**:
- 040 은 `projects/scm-platform/` 안의 인프라 skeleton 만. GAP 쪽 (`projects/global-account-platform/apps/`) 변경은 별도 ownership 영역.
- 040 의 docker-compose / .env.example 이 본 task 가 정한 client_id 를 환경변수로 참조 → 042 가 040 impl 의 **선행** 으로 머지되어야 client_id 정합성 확보.
- 042 SQL 은 isolated 변경 (auth-service `:check` 만 영향) — scope 가 작아 단일 PR 로 spec→impl 이 빠르게 흐를 수 있음.

**왜 user-flow-client 는 v2 로 미루는가**:
- scm-platform v1 = backend only (frontend 미발행). user-flow PKCE redirect URI 필요 없음.
- v2 frontend 도입 시 별도 task 로 V0014 또는 V0014+ 추가 (`scm-platform-user-flow-client` PKCE confidential).
- 본 task 가 client_credentials 만 등록 → dev 토큰 발급은 `curl -X POST /oauth2/token grant_type=client_credentials` 로 가능 → 040 impl 단계의 gateway smoke test 충분.

---

# Scope

## In Scope

### 1. account-service V0015 — scm tenant seed

파일: `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0015__seed_scm_tenant.sql`

V0014 (ecommerce tenant) 패턴 답습:

```sql
-- TASK-MONO-042: register the 'scm' tenant for scm-platform bootstrap
-- (TASK-MONO-040). scm-platform v1 is backend-only — only service-to-service
-- (client_credentials) OAuth flow is registered in auth-service V0013;
-- user-flow PKCE client deferred to v2 when frontend joins.
--
-- TenantType: B2B_ENTERPRISE — supply chain platform is enterprise-facing,
-- not consumer-facing. Mirrors wms's B2B nature; differs from ecommerce /
-- fan-platform B2C_CONSUMER.

INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('scm', 'Supply Chain Management Platform', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));
```

### 2. auth-service V0013 — scm OIDC client + scopes seed

파일: `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0013__seed_scm_oidc_clients.sql`

V0010 (wms internal) / V0012 (ecommerce) 패턴 융합. v1 은 client_credentials 만:

```sql
-- TASK-MONO-042
-- Seeds the OAuth 2.0 client used by scm-platform's backend services for
-- service-to-service (client_credentials) calls. scm becomes GAP's fourth
-- tenant consumer of the standard SAS token-issuance path (ADR-001 D1=A)
-- after wms (V0010), fan-platform (V0011), and ecommerce (V0012).
--
-- v1 is backend-only — no PKCE user-flow client. user-flow-client is
-- deferred to v2 when scm-platform-web (frontend) is introduced.
--
-- Why a Flyway seed and not the admin API:
--   admin-service OAuth client management API is not yet ready. We pre-register
--   the client here. When the admin API lands the row becomes administratable
--   through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string "scm-dev".
--     Same dev-only fixture model as V0011's "fan-platform-dev" and V0012's
--     "ecommerce-dev" — intended for portfolio / local dev environments.
--     Production deployments MUST rotate the secret via the admin API or by
--     overriding via env var (SCM_INTERNAL_SERVICES_CLIENT_SECRET) and
--     updating this row.
--   - Hash will be generated at impl time via Spring Security
--     BCryptPasswordEncoder strength=10.
--
-- Token TTLs (V0010 wms internal pattern):
--   - access_token:  PT5M = 300 seconds (short for service-to-service)

-- ============================================================
-- scm-platform-internal-services-client
--   Service-to-service flow (client_credentials).
--   Used by scm-platform backend services to call other monorepo backends
--   (e.g., wms-platform inventory snapshot subscription) and by external
--   integration partners' backends to call scm-platform endpoints.
--   Confidential client (client_secret_basic).
-- ============================================================
INSERT INTO oauth_clients (
    id,
    client_id,
    tenant_id,
    tenant_type,
    client_secret_hash,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings,
    created_at,
    updated_at
) VALUES (
    'scm-platform-internal-services-client-id',
    'scm-platform-internal-services-client',
    'scm',
    'B2B',
    '<bcrypt-hash-of-scm-dev>',
    'scm-platform Internal Services',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',  -- no redirect URIs for client_credentials
    '["scm.read","scm.write"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"}}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scopes for scm.
-- v1 starts with two coarse-grained scopes; service skeleton (TASK-SCM-BE-001)
-- may refine into per-bounded-context scopes (scm.procurement.read, etc.)
-- via a follow-up Flyway migration.
-- System scopes (openid/profile/email/offline_access/tenant.read) are
-- already seeded in V0008 + V0011 — do not re-insert.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('scm.read',  'scm', 'scm-platform read access (coarse, v1)',  FALSE, NOW()),
    ('scm.write', 'scm', 'scm-platform write access (coarse, v1)', FALSE, NOW());
```

### 3. spec 갱신

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients 표 — `scm` 행 1줄 추가 (tenant=scm, client=scm-platform-internal-services-client, grants=client_credentials, scopes=scm.read/write).
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` 가 tenant 등록 방법 reference — 필요 시 scm 사례 추가 (V0015 / V0013 reference).

### 4. (참고용) 040 spec / impl 의 환경변수 정합 검증

본 task 의 impl PR 머지 후, 040 의 `.env.example` 작성 시 다음 변수 정합 보장 (040 impl 단계의 책임이지만 본 task 가 그 기준 ID 를 확정):

- `OIDC_INTERNAL_CLIENT_ID=scm-platform-internal-services-client`
- `OIDC_INTERNAL_CLIENT_SECRET=scm-dev` (dev 평문, production 은 env override)
- `OIDC_REQUIRED_TENANT_ID=scm`

## Out of Scope

- **`scm-platform-user-flow-client` (PKCE)** — scm-platform v2 frontend 도입 시점에 별도 task (V0014 또는 그 이후 슬롯).
- **세분화된 도메인 scope** (`scm.procurement.read`, `scm.inventory.write`, `scm.settlement.admin`, …) — TASK-SCM-BE-001 의 첫 service skeleton 등장 시점 또는 v1 후반.
- **Production secret rotation** — secrets manager 연동은 별도 인프라 task.
- **scm tenant 의 admin user 사전 provisioning** — consumer-integration-guide.md § Phase 2 패턴이지만 v1 부트스트랩 범위 밖.
- **wms ↔ scm event subscription** — cross-project event 통합은 TASK-SCM-INT-001 (또는 후속 cross-project task) 범위.

---

# Acceptance Criteria

## SQL 무결성

1. `V0015__seed_scm_tenant.sql` 신설. `INSERT IGNORE` (idempotent), `tenant_id='scm'`, `tenant_type='B2B_ENTERPRISE'`, `status='ACTIVE'`.
2. `V0013__seed_scm_oidc_clients.sql` 신설. `oauth_clients` row 1건 + `oauth_scopes` 2건.
3. V0013 의 `client_secret_hash` 가 BCrypt strength=10 of `"scm-dev"` 와 검증 가능 (Spring Security `BCryptPasswordEncoder.matches("scm-dev", hash)` true).
4. V0013 의 `tenant_id='scm'`, `tenant_type='B2B'`, `authorization_grant_types=["client_credentials"]`, `scopes=["scm.read","scm.write"]`.
5. V0013 의 `redirect_uris='[]'` (client_credentials 라 redirect 불필요).
6. V0013 의 access_token TTL = 300초 (V0010 internal 패턴).

## 마이그레이션 검증

7. `./gradlew :projects:global-account-platform:apps:account-service:check` 통과 (V0015 migration validation 포함).
8. `./gradlew :projects:global-account-platform:apps:auth-service:check` 통과 (V0013 migration validation 포함, H2 + Testcontainers 양쪽).
9. Flyway version 충돌 없음 (V0013 / V0015 가 기존 migrations 와 conflict 0).

## DB row 검증 (impl 머지 후)

10. account-service: `SELECT tenant_id FROM tenants WHERE tenant_id='scm'` → 1 row.
11. auth-service: `SELECT client_id FROM oauth_clients WHERE client_id='scm-platform-internal-services-client'` → 1 row.
12. auth-service: `SELECT scope_name FROM oauth_scopes WHERE tenant_id='scm'` → 2 rows (`scm.read`, `scm.write`).

## Token 발급 smoke test (impl 머지 후, 옵션)

13. `pnpm gap:up` 후 `curl -u scm-platform-internal-services-client:scm-dev -d "grant_type=client_credentials&scope=scm.read" http://gap.local/oauth2/token` → 200 + `access_token` JSON.
14. 발급된 JWT 의 claim: `tenant_id=scm`, `scope=scm.read`, `aud=scm-platform-internal-services-client`.

## Spec 정합성

15. `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients 표에 scm 행 추가.
16. (선택) consumer-integration-guide.md 에 scm 사례 reference 추가.

## PR Separation Rule

17. 본 spec PR 은 SQL 파일 / spec 갱신 0 — task 파일 + INDEX.md ready 등재만.
18. impl PR 은 spec PR 머지 후 별도 진행 (lifecycle 모두 한 PR 안에서, lifecycle commit 과 impl commit 분리).

---

# Related Specs

- [TASK-MONO-040](./TASK-MONO-040-scm-platform-bootstrap.md) (ready) — scm-platform 부트스트랩. 본 task 는 040 impl 의 선행.
- [TASK-MONO-019](../done/TASK-MONO-019-wms-platform-oidc-resource-server-migration.md) (done) — V0010 wms client 패턴 reference.
- [TASK-MONO-026](../done/TASK-MONO-026-gap-v0011-fan-platform-oidc-clients.md) (done) — V0011 fan-platform client 패턴 reference.
- [TASK-MONO-027](../done/TASK-MONO-027-ecommerce-gap-integration.md) (done) — V0012 ecommerce client + V0014 ecommerce tenant 패턴 reference.
- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) (ACCEPTED) — Phase 4 catalyst 로 scm 채택. 본 task 는 그 D2 의 인프라 후속.
- [ADR-001 (GAP)](../../projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md) — GAP 표준 OIDC AS.
- `projects/global-account-platform/specs/contracts/http/auth-api.md` — OAuth2 Clients 표 갱신 대상.
- `projects/global-account-platform/specs/features/consumer-integration-guide.md` — Phase 1 tenant 등록 / Phase 2 OIDC client 등록.

# Related Contracts

- 본 task 는 GAP DB seed 와 auth-api.md OAuth Clients 표 갱신만 — 신규 HTTP/event contract 도입 없음.

---

# Edge Cases

1. **V 슬롯 충돌** — 본 task 머지 전에 누군가 다른 PR 에서 `V0013`/`V0015` 슬롯을 사용하면 conflict. impl PR 시점에 슬롯 재확인 필요.
2. **BCrypt 해시 결정성** — strength=10 BCrypt 는 매번 다른 salt 로 다른 해시 생성. spec 의 `<bcrypt-hash-of-scm-dev>` placeholder 는 impl 시점에 한 번 생성된 값을 commit. dev 환경에서 `BCryptPasswordEncoder.matches("scm-dev", hash)` 만 true 면 OK.
3. **Spring Authorization Server JSON column 호환성** — V0011 / V0012 가 만난 H2 vs MySQL JSON_SET 호환성 이슈 (PR #144 fix) 와 동일 패턴 적용 — `JSON_SET` 대신 inline JSON literal 사용. V0013 도 동일 처리.
4. **scope 명 namespace** — 다른 프로젝트 scope (`wms.*`, `ecommerce.*`, `fan-platform.*`) 와 충돌 가능성 검증 — `scm.*` 는 unique. tenant_id column 으로 추가 격리됨.
5. **tenant_type 선택** — scm 은 B2B_ENTERPRISE (wms 와 동일 카테고리). ecommerce / fan-platform 의 B2C_CONSUMER 와 다름. account-service `TenantType` enum 에 B2B_ENTERPRISE 가 있는지 사전 확인 필요 (V0014 ecommerce 가 B2C_CONSUMER 사용 → enum 에 두 값 모두 존재 추정).

---

# Failure Scenarios

## A. V0015 / V0013 슬롯이 머지 시점에 이미 점유

다른 task 가 동일 V 번호 사용 → Flyway checksum 위반 또는 migration 순서 conflict. impl PR 시작 전 `ls projects/global-account-platform/apps/{account,auth}-service/src/main/resources/db/migration/` 으로 최신 V 번호 확인. 충돌 시 다음 슬롯으로 이동 + 본 task 의 Goal/Scope 의 V 번호 갱신 (이때는 spec 수정 PR 도 별도 필요 — review/ 또는 done/ 진입 전이면 가능).

## B. BCrypt 해시 검증 실패

impl 시 생성한 해시가 `BCryptPasswordEncoder.matches("scm-dev", hash)` 에서 false → token 발급 시 `invalid_client`. 해시 재생성 + 재커밋. 040 impl 의 `.env.example` 의 `OIDC_INTERNAL_CLIENT_SECRET=scm-dev` 평문 값과 V0013 해시 정합성 강제.

## C. account-service 의 `TenantType` enum 에 B2B_ENTERPRISE 미존재

V0015 INSERT 가 enum constraint violation. 사전 확인 — `projects/global-account-platform/apps/account-service/src/main/java/.../domain/tenant/TenantType.java` grep. 없으면 042 spec 의 Out of Scope 가 enum 추가 task 까지 확장되어야 함 (현재 spec 은 enum 존재 가정).

## D. JSON column 직렬화 H2/MySQL 호환성

V0011 PR #144 의 `JSON_SET` 호환성 이슈 재발. V0013 SQL 작성 시 V0012 의 inline JSON literal 패턴 그대로 답습.

## E. scope catalog 구조 변경

`oauth_scopes` 테이블 스키마가 V0008 이후 어떤 PR 에서 수정됐을 수 있음. impl 시점에 컬럼 (`scope_name`, `tenant_id`, `description`, `is_system`, `created_at`) 정확성 재검증.

## F. 040 impl 보다 먼저 머지 안 된 경우

본 task 가 040 impl 의 선행 — 040 impl 이 본 task 의 client_id / scope / secret 을 환경변수로 참조하므로 042 머지 → 040 impl 시작 순서 강제. 본 spec PR 은 040 spec PR 보다 늦게 작성됐지만, impl PR 순서는 042 우선이 정합.

---

# Notes

- **Recommended impl model**: Sonnet 4.6 또는 Haiku 4.5 충분 — SQL 두 파일 + spec 표 갱신만. 단순 fix 카테고리. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (단순 seed 추가).
- **dependency 표현**:
  - `선행`: 없음 (단, ADR-MONO-002 D2 의 결정에 의해 발행)
  - `후속`: TASK-MONO-040 impl PR (본 task 머지 후 시작), TASK-SCM-BE-001 (첫 service skeleton — 본 task 의 client_id / scope 사용)
- **V 번호 잠정**: V0013 (auth-service) / V0015 (account-service) — impl 시점에 최신 V 번호 재확인.
- **본 task 와 040 의 머지 순서**:
  ```
  042 spec PR → 040 spec PR (#185) → 042 impl PR → 040 impl PR
  ```
  (040 spec 은 042 보다 먼저 발행됐지만, impl 단계에서 042 가 040 의 선행)
