# Task ID

TASK-BE-231

# Title

account-service — 내부 테넌트 provisioning API (`/internal/tenants/{tenantId}/accounts`)

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

`specs/features/multi-tenancy.md` 의 "Internal Provisioning API" 와 `specs/services/account-service/architecture.md` 의 `presentation/internal/TenantProvisioningController` 명세에 따라:

- WMS 등 enterprise 소비자가 자신의 테넌트(`tenant_id=wms`) 사용자를 생성·관리할 수 있는 내부 HTTP 엔드포인트를 제공한다.
- path `{tenantId}` ↔ 호출 주체 tenant scope 불일치 시 403 `TENANT_SCOPE_DENIED` 로 차단한다.
- 모든 mutation은 `admin_actions` 와 동등한 audit 기록을 남긴다 (audit-heavy 정합).
- 신규 internal contract `specs/contracts/http/internal/account-internal-provisioning.md` 를 작성한다.

# Scope

## In Scope

- **Presentation (`presentation/internal/`)**:
  - `TenantProvisioningController.java`
  - 엔드포인트:
    - `POST /internal/tenants/{tenantId}/accounts` — 신규 사용자 생성 (이메일·초기 비밀번호·역할 배열)
    - `GET /internal/tenants/{tenantId}/accounts` — 테넌트 사용자 목록 (페이지네이션, 필터: `status`, `role`)
    - `GET /internal/tenants/{tenantId}/accounts/{accountId}` — 단일 사용자 조회
    - `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles` — 역할 전체 교체
    - `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status` — 상태 변경 (ACTIVE↔LOCKED, DELETED)
    - `POST /internal/tenants/{tenantId}/accounts/{accountId}/password-reset` — 운영자에 의한 비밀번호 재설정 토큰 발급
  - 요청/응답 DTO + validation (Bean Validation)
  - 응답에서 `password_hash`, `deleted_at` 등 민감 필드 제외
- **Application use-cases**:
  - `ProvisionAccountUseCase` — 신규 사용자 생성 (Account + Profile + 역할 매핑) → outbox `account.created` 발행
  - `AssignRolesUseCase` — 역할 전체 교체 → outbox `account.roles.changed` 또는 동등 이벤트 발행
  - 기존 `ChangeAccountStatusUseCase`, `DeleteAccountUseCase` 를 재사용 (단, internal 호출 분기 시 reason 코드를 `OPERATOR_PROVISIONING_*` 으로 기록)
  - 신규 비밀번호 재설정 토큰 발급은 기존 흐름 재사용 또는 internal 전용 use-case 도입 (auth-service 와의 경계는 contract 갱신 시 명확화)
- **Authorization 검증**:
  - path `{tenantId}` ↔ 호출 주체(JWT `tenant_id` 또는 platform-scope SUPER_ADMIN) 일치 검증
  - 불일치 → 403 `TENANT_SCOPE_DENIED`
  - gateway 가 1차 검증(TASK-BE-230), 본 컨트롤러가 defense-in-depth로 재검증
- **테넌트 사전 검증**:
  - path `{tenantId}` 가 `tenants` 테이블에 존재하고 `status=ACTIVE` 인지 확인 (TASK-BE-228의 `TenantRepository` 사용)
  - 미존재 또는 SUSPENDED → 404 또는 409 (contract에서 명시)
- **Audit 기록**:
  - 모든 mutation에 대해 `admin_actions` 또는 동등 audit 테이블에 `action_code=OPERATOR_PROVISIONING_*` 기록
  - 기록 항목: 호출 주체, target tenant, target account, action, timestamp
- **Contract 작성**:
  - 신규 파일 `specs/contracts/http/internal/account-internal-provisioning.md` 생성
  - 6개 엔드포인트 모두 명시 (요청 schema, 응답 schema, 에러 코드)
  - 인증 방식: mTLS 또는 service-to-service 토큰. 게이트웨이 비공개 라우트
- **테스트**:
  - WMS 시스템 토큰 (`tenant_id=wms`) 으로 자기 테넌트 사용자 생성 → 200/201
  - WMS 토큰으로 `tenant_id=fan-platform` path 호출 → 403 `TENANT_SCOPE_DENIED`
  - 비활성/미등록 `tenantId` path → 404 또는 409
  - 신규 계정 생성 후 outbox `account.created` 페이로드에 `tenant_id=wms` 포함
  - 역할 교체 PATCH 후 audit 로그 row 생성
  - 같은 이메일이 `fan-platform` 에 이미 존재해도 `wms` 에 정상 생성 가능 (cross-tenant unique)

## Out of Scope

- gateway-service 의 internal 라우트 검증 (→ TASK-BE-230 에서 처리)
- WMS 측 백엔드의 system credential 발급/관리 (운영 절차)
- platform-scope SUPER_ADMIN 토큰의 cross-tenant 호출 흐름 상세 (admin-service 후속 태스크) — 본 태스크에서는 path tenant 검증만 적용하며 SUPER_ADMIN 우회 로직은 향후 별도 처리
- `Tenant` CRUD (신규 테넌트 생성/SUSPEND) — admin-service 책임 (별도 태스크)
- `TenantRoleCatalog` 등 역할 카탈로그 도메인 신설 (별도 태스크) — 본 태스크에서는 단순 문자열 배열로 역할 수용 (DB 컬럼은 기존 구조 활용 또는 임시 매핑)
- self-service signup의 변경 (외부 가입 흐름은 본 태스크 범위 외)
- 비밀번호 재설정 토큰의 실제 이메일 발송 흐름 (기존 노티 채널 재사용)

# Acceptance Criteria

- [ ] `presentation/internal/TenantProvisioningController` 가 6개 엔드포인트를 모두 노출한다
- [ ] path `{tenantId}` ↔ 호출 주체 `tenant_id` 불일치 시 403 `TENANT_SCOPE_DENIED` 가 반환된다
- [ ] path `{tenantId}` 가 `tenants` 테이블에 ACTIVE 상태로 존재하지 않으면 404 또는 409 (contract 정의에 따라)
- [ ] 신규 계정 생성 시 Account + Profile + 역할 매핑이 동일 트랜잭션에서 영속되며 outbox `account.created` 가 기록된다
- [ ] outbox 페이로드의 `tenant_id` 가 path `{tenantId}` 와 일치한다
- [ ] 역할/상태 변경/비밀번호 재설정 모든 mutation이 `admin_actions`(또는 동등) audit 로그에 `OPERATOR_PROVISIONING_*` 코드로 기록된다
- [ ] 응답 DTO에 `password_hash`, `deleted_at` 등 민감 필드가 포함되지 않는다
- [ ] WMS 토큰으로 `tenant_id=fan-platform` path 호출 → 403 통합 테스트 통과
- [ ] 같은 이메일을 `fan-platform`/`wms` 양쪽에서 생성 가능 (cross-tenant unique 회귀 테스트)
- [ ] 신규 contract 파일 `specs/contracts/http/internal/account-internal-provisioning.md` 가 작성된다 (6개 엔드포인트의 요청/응답/에러 코드 명세)

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/features/multi-tenancy.md`
- `specs/services/account-service/architecture.md`
- `rules/traits/multi-tenant.md`
- `rules/traits/audit-heavy.md` (mutation audit 의무)
- `rules/traits/regulated.md` (응답 민감 필드 제거)
- `rules/traits/transactional.md` (outbox + DB 동일 트랜잭션)
- `platform/security-rules.md`
- `platform/api-gateway-policy.md` (internal 라우트 비공개 정책)
- `platform/error-handling.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/INDEX.md` (해당 도메인/서비스 스킬 매칭 결과 적용)

# Related Contracts

- **신규**: `specs/contracts/http/internal/account-internal-provisioning.md` — 본 태스크에서 작성
- `specs/contracts/http/internal/auth-to-account.md` (lookupCredential 응답 — TASK-BE-229에서 갱신됨, 본 태스크는 영향 없음)
- `specs/contracts/http/internal/admin-to-account.md` (관리자 상태 변경 — 기존 흐름 유지)
- `specs/contracts/events/account-events.md` (페이로드 `tenant_id` 포함 — TASK-BE-228에서 갱신됨)

# Target Service

- account-service

# Architecture

Follow:

- `specs/services/account-service/architecture.md`

상위 원칙:

- Layered. `presentation/internal/` 은 외부 라우트와 분리된 URL prefix(`/internal/*`)로만 노출
- 게이트웨이는 `/internal/*` 을 외부 라우트로 열지 않음 (TASK-BE-230 의 strip 정책 + 라우트 화이트리스트)
- 내부 컨트롤러도 동일한 application use-case 를 호출 — 별도 백도어 금지
- `tenant_id` 없는 도메인 쿼리는 사용 불가 (TASK-BE-228 에서 enforce 된 시그니처를 그대로 사용)

# Implementation Notes

- **순서 의존성**: TASK-BE-228 (account `tenant_id` 스키마 + `domain/tenant/`) **선행 필수**. 본 태스크는 TASK-BE-228 의 `Tenant` 엔터티/`TenantRepository`/`tenant_id` 컬럼이 갖춰진 후에만 의미를 가진다.
- TASK-BE-229/TASK-BE-230 은 본 태스크의 직접 전제는 아니지만, **운영 환경에서 internal 라우트가 외부에 노출되지 않으려면 TASK-BE-230 의 gateway tenant scope 검증이 함께 배포되어야 한다**. 통합 테스트는 mock으로 처리 가능.
- **인증 주체 식별**: mTLS 클라이언트 인증서 또는 service-to-service 토큰. 본 태스크에서는 기존 internal 인증 어댑터를 재사용. JWT 기반이라면 `tenant_id` claim을 그대로 사용 (TASK-BE-229 산출물).
- **path tenant 검증 위치**: 컨트롤러 진입 직후 또는 Spring Security `@PreAuthorize` SpEL. AOP 또는 어드바이스로 일관 적용 가능.
- **역할 매핑**: 본 태스크에서는 단순 `List<String>` 으로 수용. `(tenant_id, role_name)` 정합성 검증은 추후 `TenantRoleCatalog` 도입 시점에 강화. 현재는 known role set 검증을 application 레벨 상수로 처리하거나, 검증 없이 그대로 영속(향후 강화).
- **비밀번호 재설정 토큰**: auth-service 와의 경계 검토 필요. 기존 self-service password-reset 흐름이 있으면 재사용. 없으면 token 발급 + 이메일 발송 흐름을 별도 use-case로 도입. contract 갱신 시 명확화.
- **`OPERATOR_PROVISIONING_*` action_code**: `OPERATOR_PROVISIONING_CREATE`, `OPERATOR_PROVISIONING_ROLES_REPLACE`, `OPERATOR_PROVISIONING_STATUS_CHANGE`, `OPERATOR_PROVISIONING_PASSWORD_RESET` 등 명시.
- **응답 DTO 분리**: 외부 응답 DTO 와 internal 응답 DTO 를 분리하거나, 공통 DTO에서 민감 필드를 명시적 제외. 직렬화 시 누락 검증 단위 테스트 추가.

# Edge Cases

- WMS 토큰이 자기 테넌트(`wms`) path 호출 → 정상
- WMS 토큰이 `fan-platform` path 호출 → 403
- platform-scope SUPER_ADMIN 토큰의 cross-tenant 호출 → 본 태스크 범위에서는 단순 path 검증만 적용. SUPER_ADMIN 우회는 향후 admin-service 후속 (필요 시 본 태스크에서 임시 화이트리스트 + TODO 주석)
- 신규 계정 생성 시 같은 이메일이 같은 테넌트에 존재 → 409 `EMAIL_DUPLICATE` (기존 동작 유지)
- 신규 계정 생성 시 같은 이메일이 다른 테넌트에 존재 → 정상 생성 (cross-tenant unique)
- 미등록 `tenantId` path → 404
- SUSPENDED `tenantId` path 로 신규 생성 → 409 `TENANT_SUSPENDED`
- 역할 PATCH 에서 빈 배열 전달 → 모든 역할 제거 (정책 결정: 허용 또는 400). contract에서 명시
- 상태 PATCH 에서 허용되지 않는 전이 (예: `DELETED → LOCKED`) → 400 `STATE_TRANSITION_INVALID` (`AccountStatusMachine` 그대로 적용)
- 비밀번호 재설정 토큰을 짧은 시간 내 반복 발급 → rate limit (운영 정책 — 본 태스크에서는 기존 정책 재사용)

# Failure Scenarios

- 호출 주체 tenant scope 결정 실패 (JWT/인증 정보 불완전) → 401 (`UNAUTHORIZED`) 또는 403, contract 명시
- `Tenant` 사전 검증 시 DB 장애 → 5xx + 알람. 백오프 후 재시도 가능
- 신규 계정 생성 트랜잭션 도중 outbox 기록 실패 → 트랜잭션 롤백 (T3)
- 역할/상태 변경 후 audit 기록 실패 → 트랜잭션 일관성 유지 (`@Transactional` 내부에서 동시 커밋). 별도 트랜잭션 분리 시 audit 누락 위험 → 본 태스크에서는 동일 트랜잭션 보장
- 비밀번호 재설정 토큰 발급 후 이메일 발송 실패 → 기존 알림 채널의 retry/DLQ 정책 그대로 (본 태스크 범위 외)
- WMS 호출이 폭증하여 internal API rate limit 초과 → 429 (gateway/internal 정책에 따라). 본 태스크에서는 기존 메커니즘 재사용

# Test Requirements

- **Unit**:
  - 응답 DTO 직렬화 시 민감 필드 제외
  - path tenant 검증 분기 (allow/deny)
  - 역할 입력 검증 (빈 배열 정책 등)
- **Application integration (Testcontainers)**:
  - `ProvisionAccountUseCase` 호출 → Account + Profile + 역할 매핑 영속, outbox `account.created` 페이로드에 `tenant_id` 포함
  - `AssignRolesUseCase` 호출 → 역할 교체 + audit row 생성
  - 기존 `ChangeAccountStatusUseCase` 가 internal 분기에서 `OPERATOR_PROVISIONING_STATUS_CHANGE` 코드로 audit 기록
- **Controller / Web slice**:
  - 6개 엔드포인트의 요청/응답 schema가 contract와 일치
  - validation 오류 → 400 + 표준 에러 포맷
  - 민감 필드가 응답에 누설되지 않음
- **Integration / E2E**:
  - WMS 토큰으로 자기 테넌트 사용자 생성 → 201 + 후속 GET 으로 조회 가능
  - WMS 토큰으로 다른 테넌트 path 호출 → 403 `TENANT_SCOPE_DENIED`
  - 미등록 `tenantId` path → 404
  - SUSPENDED `tenantId` path 로 신규 생성 → 409 `TENANT_SUSPENDED`
  - cross-tenant unique: 같은 이메일을 두 테넌트에 양쪽 생성 후 양쪽 모두 정상 조회
- **Contract test**:
  - `specs/contracts/http/internal/account-internal-provisioning.md` 의 schema 와 실제 응답 일치
- 새로 추가/수정한 테스트는 `@EnabledIf` 없이 항상 실행

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (provisioning scope 403 + cross-tenant unique 회귀 포함)
- [ ] Tests passing (Testcontainers 포함 전체 그린)
- [ ] Contracts updated (`account-internal-provisioning.md` 신규 작성, 필요 시 `account-events.md` 보완)
- [ ] Specs updated first if required (현재 specs는 본 태스크가 따르는 형태로 이미 갱신됨 — 추가 변경이 필요하면 specs 선행)
- [ ] Ready for review
