# Task ID

TASK-BE-230

# Title

gateway-service — `X-Tenant-Id` 헤더 전파 + JWT `tenant_id` claim 검증

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`specs/features/multi-tenancy.md` 의 cross-tenant 보안 규칙을 게이트웨이에서 강제한다.

- gateway는 들어오는 요청의 access token에서 `tenant_id` claim 존재를 검증한다 (누락 시 401).
- 검증된 `tenant_id` 를 다운스트림 서비스로 `X-Tenant-Id: {tenant_id}` 헤더로 전파한다 (defense-in-depth).
- rate limit 키 패턴에 `tenant_id` 를 포함하여 테넌트별 quota 운용 기반을 제공한다.
- 마이그레이션 grace period(옵션, 설정값) 동안 `tenant_id` claim 누락 토큰을 `fan-platform` 으로 간주하는 fallback 정책을 제공한다.

# Scope

## In Scope

- **JWT 검증 로직 변경**:
  - `GatewayAuthFilter` (또는 동등) 에서 access token 파싱 후 `tenant_id` claim 존재 검증
  - 누락 시 401 `TOKEN_INVALID` (fallback 비활성 시)
- **`X-Tenant-Id` 헤더 전파**:
  - 다운스트림 라우팅 시 `X-Tenant-Id: {tenant_id}` 헤더를 추가
  - 기존 헤더(클라이언트 위조)가 들어오면 **항상 덮어쓰기** (외부 입력 신뢰 금지)
- **Rate limit 키 패턴 변경**:
  - 기존 `rate:login:{ip}` → `rate:login:{tenant_id}:{ip}` (signup, refresh 등 동일)
  - tenant 컨텍스트가 없는 경로(예: 공개 health-check)는 기존 패턴 유지하거나 `anonymous` 라벨 사용
  - `gateway_ratelimit_total{scope, result}` 메트릭에 `tenant` 차원 추가 검토 (필수 아님 — 본 태스크에서는 키 패턴 변경만 강제)
- **Internal provisioning 라우트의 path tenant 검증**:
  - `/internal/tenants/{tenantId}/...` 라우트에서 path `{tenantId}` ↔ JWT `tenant_id` (또는 platform-scope) 일치 검사
  - 불일치 시 403 `TENANT_SCOPE_DENIED`
  - 단, 본 태스크에서는 라우트 추가/검증만 수행 (실제 컨트롤러 구현은 TASK-BE-231)
- **Grace period fallback (옵션, 설정값)**:
  - `gateway.tenant.legacy-fallback.enabled=true|false` (기본 false)
  - true 일 때 `tenant_id` claim 누락 토큰은 `fan-platform` 으로 간주하여 통과
  - 활성 시 보안 로그/메트릭에 fallback 사용 카운트 기록
- **테스트**:
  - `tenant_id` 없는 토큰 → 401 (fallback 비활성)
  - `tenant_id=fan-platform` 토큰 → 다운스트림에 `X-Tenant-Id: fan-platform` 전파
  - 클라이언트가 `X-Tenant-Id: malicious` 헤더를 보내도 gateway가 JWT 기반으로 덮어씀
  - rate limit 키가 `rate:login:fan-platform:{ip}` 패턴으로 기록되는지

## Out of Scope

- account-service / auth-service 의 `tenant_id` 도입 (→ TASK-BE-228, TASK-BE-229 선행 완료)
- internal provisioning controller 구현 (→ TASK-BE-231)
- 다운스트림 서비스의 `X-Tenant-Id` ↔ JWT claim 재검증 로직 (각 서비스 별 별도 태스크)
- platform-scope SUPER_ADMIN 토큰의 cross-tenant 라우팅 (admin-service 후속 태스크)
- 테넌트별 차등 quota 설정값 (key 패턴 도입만, 실제 quota 분리는 운영 결정)
- JWT 서명 키 rotation
- WebSocket / SSE 의 tenant 전파 (해당 라우트가 도입되면 별도 태스크)

# Acceptance Criteria

- [ ] gateway가 access token 파싱 후 `tenant_id` claim 존재를 강제 검증한다 (fallback 비활성 시 누락은 401)
- [ ] 검증된 `tenant_id` 가 다운스트림 요청 헤더 `X-Tenant-Id` 로 전파된다
- [ ] 클라이언트가 `X-Tenant-Id` 헤더를 위조해도 gateway가 JWT 기반으로 덮어쓴다 (제거 후 재설정)
- [ ] rate limit 키가 `rate:login:{tenant_id}:{ip}`, `rate:signup:{tenant_id}:{ip}`, `rate:refresh:{tenant_id}:{ip}` 패턴으로 동작한다
- [ ] `tenant_id` 가 없는 path(공개 health 등)는 정상 동작 (회귀 없음)
- [ ] `/internal/tenants/{tenantId}/...` 라우트에서 path `{tenantId}` ↔ JWT `tenant_id` 불일치 시 403 `TENANT_SCOPE_DENIED`
- [ ] grace period fallback 설정이 활성화되면 `tenant_id` 누락 토큰을 `fan-platform` 으로 간주하여 통과시키며, fallback 사용은 메트릭/로그에 기록된다
- [ ] grace period fallback 기본값은 비활성 (false)
- [ ] 통합 테스트가 위 흐름을 모두 검증

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/features/multi-tenancy.md`
- `specs/services/gateway-service/architecture.md`
- `specs/features/rate-limiting.md` (key 패턴 변경 영향)
- `rules/traits/multi-tenant.md`
- `rules/traits/audit-heavy.md`
- `platform/security-rules.md`
- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/INDEX.md` (해당 도메인/서비스 스킬 매칭 결과 적용)

# Related Contracts

- `specs/contracts/http/auth-api.md` (login/refresh 응답에 `tenant_id` — TASK-BE-229에서 이미 갱신)
- 모든 다운스트림 컨트랙트의 요청 헤더 명세에 `X-Tenant-Id` 추가 (account-api, admin-api 등 — additive)

# Target Service

- gateway-service

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

상위 원칙:

- gateway는 신뢰 경계의 시작점. 외부 입력의 `X-Tenant-Id` 는 절대 그대로 다운스트림 전달 금지 — JWT claim으로만 결정
- defense-in-depth: 다운스트림은 헤더와 JWT claim 일치 여부를 추가로 검증할 책임을 지나, gateway에서 우선 보장
- rate limit, JWT 검증, 헤더 전파는 모두 동일 filter chain 내에서 일관된 순서로 동작

# Implementation Notes

- **순서 의존성**: TASK-BE-228 (account `tenant_id`) → TASK-BE-229 (auth JWT claim 발급) **선행 필수**. auth-service가 claim 없는 토큰만 발급하면 본 태스크의 모든 강제 검증은 401만 만들어낸다.
- **fallback 토글 필요성**: 운영 배포 시 마이그레이션 시점 차이로 `tenant_id` claim 없는 legacy 토큰이 잠시 떠돌 수 있음. 토글로 통제. 본 태스크에서는 default false (강제 검증)이며, 실제 운영 배포 시점에 별도 ADR 또는 운영 결정으로 토글 운영.
- **claim 추출 위치**: gateway가 JWT 서명 검증을 이미 수행하는 filter에서 함께 처리. 별도 filter 추가는 회피 (latency).
- **`X-Tenant-Id` 위조 방지**: filter는 inbound `X-Tenant-Id` 헤더를 **반드시 제거**한 뒤 JWT claim 기반으로 재설정. 단순 덮어쓰기로 충분하나, mutation API에서 missed cases 가 있으면 명시적 strip-then-set 패턴.
- **rate limit 키 변경의 운영 영향**: 기존 키들은 짧은 TTL(분 단위)로 자연 만료. 변경 직후 일시적 quota 리셋 효과는 허용 범위로 판단.
- **internal route**: `/internal/*` 라우트가 외부 인터넷으로 노출되지 않도록 라우트 화이트리스트가 이미 구성되어 있을 가능성 높음. 본 태스크에서는 path-based tenant scope 검증만 추가. 실제 컨트롤러는 TASK-BE-231.
- **메트릭**: 기존 `gateway_ratelimit_total{scope, result}` 카운터(TASK-BE-226)에 `tenant` 차원 추가가 자연스러우나, cardinality 폭증 위험 — 본 태스크에서는 **선택적**으로 도입 (필요 시 별도 ADR).

# Edge Cases

- `tenant_id` 가 path와 JWT 모두에 존재하지만 서로 다름 (잠재적 공격) → 403 `TENANT_SCOPE_DENIED` + 보안 로그
- access token이 만료되어 401인 경우 → 기존 동작 유지. `tenant_id` 검증 이전에 만료/서명 검증 실패가 우선
- 클라이언트가 `X-Tenant-Id: wms` 위조 + JWT는 `tenant_id=fan-platform` → gateway가 헤더를 `fan-platform` 으로 덮어쓰고 다운스트림 전달
- 공개 라우트(예: `/api/auth/login`, health, JWKS) → JWT 없으므로 `tenant_id` 검증을 적용하지 않음. rate limit 키도 `tenant_id` 없이 또는 `anonymous` 라벨로 처리
- platform-scope SUPER_ADMIN 토큰(`tenant_id=*` 또는 `platform_scope=true`) → 본 태스크 범위에서는 처리 미정. 향후 admin-service 후속 태스크에서 정의 (현재는 일반 토큰처럼 단일 tenant_id 보유 가정)
- grace period fallback 활성 + 위조된 무효 토큰 (서명 실패) → 서명 검증이 우선이므로 fallback 무관하게 401
- WebClient/HTTP client가 outbound 요청에서 `X-Tenant-Id` 헤더를 자동 추가하지 못하는 경우 (라우터 설정 누락) → 다운스트림 서비스가 JWT claim 으로 회수 가능 (defense-in-depth). 그러나 통합 테스트에서 헤더 누락은 실패로 처리

# Failure Scenarios

- JWT parser가 `tenant_id` claim 추출 중 예외 발생 → 401 `TOKEN_INVALID`. 내부 예외 메시지 노출 금지
- 다운스트림 라우팅 단계에서 `X-Tenant-Id` 추가 실패 (router 빈 미주입 등) → gateway 응답 5xx + 알람. 사용자에게는 일반 5xx 메시지
- Redis 장애로 rate limit 카운터 read/write 실패 → 기존 fail-open/fail-closed 정책 유지. 키 패턴 변경과 무관
- grace period fallback 토글이 활성 상태로 운영 배포 후 회수되지 않음 → 메트릭으로 fallback 사용량 모니터링 후 운영자가 토글 비활성. 본 태스크 범위는 토글 제공까지
- internal route path `{tenantId}` 가 정규식 위반 (예: `*` 외 와일드카드) → 정규식 검증으로 거부 (404 또는 400)

# Test Requirements

- **Unit**:
  - JWT 파싱 후 `tenant_id` 추출 로직 (누락 케이스 포함)
  - `X-Tenant-Id` strip-then-set 동작
  - rate limit 키 빌더 (`rate:login:fan-platform:127.0.0.1`)
  - grace period fallback 토글 분기
- **Filter / Integration test (`@SpringBootTest` 또는 동등)**:
  - `tenant_id` 없는 토큰 → 401 (fallback 비활성)
  - `tenant_id=fan-platform` 토큰 → 다운스트림이 `X-Tenant-Id: fan-platform` 수신 (WireMock 등으로 검증)
  - 클라이언트가 위조한 `X-Tenant-Id` 가 무력화됨
  - `/internal/tenants/wms/...` path + JWT `tenant_id=fan-platform` → 403 `TENANT_SCOPE_DENIED`
  - grace period 활성 + claim 누락 토큰 → 통과 + 메트릭 카운트
  - rate limit 키 패턴 회귀 (Redis testcontainer로 키 prefix 검증)
- **Contract**:
  - 다운스트림 서비스 호출 시 `X-Tenant-Id` 헤더가 항상 포함되는지 (account-service / auth-service 의 mock으로 검증)
- 새로 추가/수정한 테스트는 `@EnabledIf` 없이 항상 실행

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (헤더 위조 방지, fallback 토글, rate limit 키 회귀 포함)
- [ ] Tests passing (Testcontainers 포함 전체 그린)
- [ ] Contracts updated if needed (다운스트림 컨트랙트의 `X-Tenant-Id` 헤더 명시 — additive)
- [ ] Specs updated first if required (필요 시 multi-tenancy.md 의 grace period 절 갱신 후 진행)
- [ ] Ready for review
