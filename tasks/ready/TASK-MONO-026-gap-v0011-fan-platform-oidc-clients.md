# Task ID

TASK-MONO-026

# Title

GAP V0011 Flyway seed — fan-platform OIDC clients 등록

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

GAP V0010 seed 가 wms 의 OIDC client 만 등록 — fan-platform 의 client 가 누락되어 있어 fan-platform-web (TASK-FAN-FE-001) 의 OIDC 로그인이 production 에서 실패한다.

V0011 Flyway 마이그레이션으로 다음 client 등록:

1. **`fan-platform-user-flow-client`** — frontend (`fan-platform-web`) 의 PKCE 사용자 로그인 client
   - grant_types: `authorization_code`, `refresh_token`
   - redirect_uris: `http://localhost:3000/api/auth/callback/gap`, `http://fan-platform.local/api/auth/callback/gap`
   - PKCE 강제
   - scopes: `openid profile email tenant.read`

2. **`fan-platform-internal-services-client`** — backend service 간 internal 통신용 (필요 시)
   - grant_types: `client_credentials`
   - scopes: `internal.community internal.artist`
   - 단, fan-platform v1 의 service 간 호출은 OIDC 가 아닌 internal Spring Security 컨텍스트 위임 — 본 client 는 v2 membership/notification 도입 시 필요. 본 task 의 in-scope 여부는 spec 작성 시 확정.

이 태스크 완료 후:
- GAP DB 의 `oauth2_registered_client` (또는 SAS 가 사용하는 테이블) 에 `fan-platform-user-flow-client` 가 등록됨
- fan-platform-web 의 `/login` → GAP authorize 엔드포인트 → fan-platform-web callback flow 가 e2e 에서 동작
- TASK-MONO-019 의 wms client 패턴 따름

---

# Scope

## In Scope

### 1. Flyway 마이그레이션 추가

`projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0011__fan_platform_oidc_clients.sql`:
- `oauth2_registered_client` (Spring Authorization Server 표준 테이블) 에 `fan-platform-user-flow-client` row INSERT
  - id (UUID v7 권장)
  - client_id, client_id_issued_at, client_secret (BCrypt hashed), client_secret_expires_at (NULL or future)
  - client_name = "fan-platform Web"
  - client_authentication_methods = "client_secret_basic" 또는 "none" (PKCE only public client)
  - authorization_grant_types = "authorization_code,refresh_token"
  - redirect_uris = `http://localhost:3000/api/auth/callback/gap,http://fan-platform.local/api/auth/callback/gap`
  - post_logout_redirect_uris = `http://localhost:3000/,http://fan-platform.local/`
  - scopes = "openid,profile,email,tenant.read"
  - client_settings = JSON (require_proof_key=true, require_authorization_consent=false)
  - token_settings = JSON (access_token_format=self-contained, access_token_time_to_live=PT15M, refresh_token_time_to_live=PT24H)

- (옵션) `fan-platform-internal-services-client` row 추가 — v1 에 필요한지는 spec 작성 시 결정. v2 로 미루는 게 합리적.

### 2. Scope catalog 갱신

만약 V0010 가 wms 별 9 scopes 를 등록했다면, fan-platform 도 동일 패턴:
- `fan-platform.community.read`
- `fan-platform.community.write`
- `fan-platform.artist.read`
- `fan-platform.artist.admin`
- 기타 — auth-service / community-service / artist-service 의 SecurityConfig 가 어떤 authority 를 요구하는지 확인 후 결정.

### 3. spec 갱신

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients 표 갱신 (fan-platform 행 추가)
- `projects/fan-platform/specs/integration/gap-integration.md` § OIDC Client Registration 갱신 (실제 client_id 명시 + V0011 reference)

### 4. .env.example 갱신

- `projects/fan-platform/.env.example`: `OIDC_CLIENT_ID=fan-platform-user-flow-client` 명시 + `OIDC_CLIENT_SECRET=<from V0011 seed>` 안내 (실제 값은 V0011 마이그레이션 plain text 또는 별도 secrets 관리)

### 5. 검증

- `./gradlew :projects:global-account-platform:apps:auth-service:check --no-daemon` (단위 + Flyway migration 검증)
- 로컬 e2e (옵션):
  - `pnpm gap:up` + `pnpm fan-platform:up` + `pnpm fan-platform:web` → `/login` → GAP `/oauth2/authorize` → callback → fan-platform-web 인증된 페이지 진입

## Out of Scope

- Production 비밀번호 secret 관리 — dev 용 plain BCrypt hash 면 충분. production 은 별도 secrets 관리 (vault) 와 결합.
- fan-platform 의 admin client (admin-service v2 와 함께)
- artist self-service client (v2 artist self-service feature)
- multi-tenant 권한 검증 강화 (tenant_id 기반 token shaping)

---

# Acceptance Criteria

- [ ] V0011 Flyway 마이그레이션 추가 + `fan-platform-user-flow-client` row 등록
- [ ] auth-service `:check` 통과 (Flyway migration validation 포함)
- [ ] auth-api.md / gap-integration.md spec 갱신 — fan-platform client 명시
- [ ] `.env.example` 의 `OIDC_CLIENT_ID` 정확한 값 명시
- [ ] (옵션) 로컬 e2e 로 fan-platform-web `/login` → callback flow 동작

---

# Related Specs

- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Endpoints
- `projects/global-account-platform/specs/services/auth-service/architecture.md`
- `projects/global-account-platform/specs/features/consumer-integration-guide.md`
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md`
- `projects/fan-platform/specs/integration/gap-integration.md`
- `tasks/done/TASK-MONO-019-wms-platform-oidc-resource-server-migration.md` — wms client 패턴 reference

# Related Skills

- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`

---

# Target Service / Component

- `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0011__fan_platform_oidc_clients.sql` (신규)
- `projects/global-account-platform/specs/contracts/http/auth-api.md` (갱신)
- `projects/fan-platform/specs/integration/gap-integration.md` (갱신)
- `projects/fan-platform/.env.example` (갱신)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. auth-service 의 architecture 는 그대로 — Flyway seed 추가만.

---

# Implementation Notes

- V0010 (wms) 는 BCrypt secret + redirect URI 등록 패턴이 이미 존재 — 그대로 복제 + fan-platform 변경.
- BCrypt secret hash: dev 환경은 단순 strong-enough hash 면 OK (e.g., `password = "fan-platform-dev"`). production 은 별도 secret rotation strategy.
- `client_secret_expires_at = NULL` (만료 없음) — dev 편의. production 은 90 days 또는 rotate 정책 검토.
- TASK-FAN-FE-001 (PR #132) 머지 후 진행 권장 — frontend 가 V0011 의 client 를 활용하므로.

---

# Edge Cases

- **redirect URI mismatch**: localhost vs fan-platform.local 둘 다 등록 — dev (Next.js localhost) 와 hostname routing 모두 지원.
- **client_secret 노출**: dev 의 plain text secret 이 commit 에 포함되는 보안 우려 — 본 task 는 dev/portfolio 환경이라 허용. production 은 secrets manager.
- **PKCE only client (no secret)**: Spring Authorization Server 가 PKCE only public client 도 허용 — `client_secret = ""` + `require_proof_key = true`. 단, refresh_token grant 사용 시 secret 필요. 본 task 는 confidential client (secret + PKCE).

---

# Failure Scenarios

- **마이그레이션 충돌**: V0011 충돌 (다른 task 가 동시에 V0011 사용) — 발행 시점에 V 번호 재확인 필수.
- **GAP DB 가 V0010 실행 안 한 상태**: V0010 가 main 에 머지됐으므로 (TASK-MONO-019 done 2026-05-02) 이 가정 안전.
- **fan-platform-web 의 client_id 불일치**: `.env.example` 의 OIDC_CLIENT_ID 가 V0011 의 client_id 와 정확히 일치해야 함. spec 와 코드 일관성 검증.

---

# Test Requirements

- 단위 / 슬라이스: 영향 없음 (Flyway migration 만 추가)
- 통합:
  - auth-service `:check` 가 Flyway 자동 실행 → migration 무결성 검증
  - (옵션) `OAuth2RegisteredClientRepositoryTest` 가 fan-platform client 조회 검증
- e2e:
  - TASK-FAN-INT-001 (PR #131) 또는 별도 task 가 GAP 컨테이너 실제 띄워 fan-platform-web → GAP login flow 검증

---

# Definition of Done

- [ ] V0011 Flyway 마이그레이션 SQL 작성 + auth-service `:check` 통과
- [ ] `fan-platform-user-flow-client` 등록 검증 (Flyway 실행 후 DB row 존재)
- [ ] auth-api.md / gap-integration.md / .env.example 갱신
- [ ] (옵션) 로컬 e2e — `pnpm gap:up && pnpm fan-platform:up && pnpm fan-platform:web` → `/login` → callback 통과
- [ ] Ready for review
