---
id: TASK-BE-466
title: platform-scope 센티넬 '*' tenant_type 조회 400 → 로그인 fail-closed 폭사 수정
status: done
project: iam-platform
service: auth-service
type: bugfix
created: 2026-07-02
---

# TASK-BE-466 — platform-scope 센티넬 '*' tenant_type 조회를 단락해 로그인/refresh 복구

## Goal

`TenantTypeResolver.resolve("*")` 가 account-service `GET /internal/tenants/*` 를 호출해 **400** 을
받고, 이를 `AccountServiceUnavailableException`(fail-closed)으로 전파해 **모든 platform-scope
(SUPER_ADMIN, `tenant_id="*"`) 로그인/refresh 가 실패**하는 회귀를 수정한다. 수정 후 platform-scope
운영자는 정상 로그인하고 `Platform Console E2E full-stack` nightly 잡이 GREEN 이 된다.

## Background / Problem

- **증상**: nightly-e2e.yml `Platform Console E2E full-stack (Playwright + docker compose)` 잡이
  **2026-06-19(TASK-BE-407 머지, PR #1835)부터 매 실행 결정적 실패**(≥10일 연속, docs-only 커밋 포함).
  필수 `CI` 워크플로는 GREEN 이라 PR 차단은 없었음. globalSetup 의 superadmin OIDC 로그인이
  `http://auth-service:8081/login?error`(Spring Security 인증 거부)로 리다이렉트 → Playwright
  `waitForURL('/dashboards')` 30s 타임아웃.
- **루트 코즈 체인**:
  1. superadmin credential 시드 `tenant_id = "*"`(ADR-002 platform-scope 센티넬).
  2. credential+status 인증 통과. 이후 `LoginUseCase.issueTokensAndPublishSuccess`(~148) /
     `RefreshTokenUseCase`(~140) 가 BE-407 이 도입한 `tenantTypePort.resolve(resolvedTenantId="*")` 호출.
  3. → `AccountServiceClient.getTenantType("*")` → `GET /internal/tenants/*`.
  4. account-service 가 `*`(유효 tenant id 아님)에 **400 BAD_REQUEST** 반환.
  5. `getTenantType` 는 **404 만 `Optional.empty()`(fail-soft), 그 외 4xx(400 포함)는
     `AccountServiceUnavailableException` throw**(BE-407 이 tenant_type 오분류 방지 위해 의도적
     fail-closed). → 예외 전파 → 로그인/refresh 실패.
- **확증 로그**(실패 run `Dump docker compose logs on failure`):
  `auth-service | ...AccountServiceClient... "Account service tenant-type lookup returned client error 400 BAD_REQUEST"`
  (thread = http-nio 로그인 스레드). seed.sql 적용 스텝은 success → 시드 문제 아님, 순수 로그인-경로 결함.
- **영향 범위**: E2E superadmin 뿐 아니라 **모든 platform-scope(`tenant_id="*"`) 운영자 로그인**.
  `feedback_spring_boot_diagnostic_patterns` §32("GAP 로그인 tenant_type non-fail-soft 마스킹")의 구체 인스턴스.

## Scope

- **IN**: `TenantContext.PLATFORM_SCOPE_TENANT_ID = "*"` 상수 추가. `TenantTypeResolver.resolve` 가
  `*` 를 **account-service 조회 이전에** 단락해 `DEFAULT_TENANT_TYPE`(네트워크 0) 반환. resolver 단위테스트에
  `*` 케이스 추가. (resolver 레이어 수정 → 로그인 + refresh 두 경로 동시 복구.)
- **OUT**: `AccountServiceClient` 의 non-404 4xx fail-closed 정책 변경 없음(실 tenant 오분류 방지 의도는
  유지 — `*` 는 조회 자체를 안 하므로 정책 무관). account-service 변경 없음. tenant_type enum 변경 없음
  (신규 값 미도입 → 게이트웨이/다운스트림 consumer 파급 0; claim 은 pass-through 힌트로 엄격 분기 없음 확인).
  spec/contract 변경 없음(`*` 는 문서화된 platform-scope 센티넬이고 값 계약 불변).

## Acceptance Criteria

- [ ] **AC-1**: `TenantContext` 에 `PLATFORM_SCOPE_TENANT_ID = "*"` 상수 추가(Javadoc: ADR-002 platform-scope,
      실 tenant 아님).
- [ ] **AC-2**: `TenantTypeResolver.resolve` 가 `tenantId.equals("*")` 일 때 **account-service 호출 없이**
      `DEFAULT_TENANT_TYPE` 반환. null/blank 가드 직후, 캐시 조회 이전에 위치(네트워크 0). 근거 주석 필수.
- [ ] **AC-3**: resolver 단위테스트 — `resolve("*")` → `DEFAULT_TENANT_TYPE` 이고
      `accountServicePort.getTenantType("*")` 가 **never() 호출**됨을 검증. 기존 7개 테스트 회귀 없음.
- [ ] **AC-4**: `:auth-service:test` GREEN(단위, Docker-free). 로그인/refresh 경로 회귀 없음.
- [ ] **AC-5 (엔드투엔드 권위)**: 머지 후 `Platform Console E2E full-stack (Playwright + docker compose)`
      nightly 잡이 GREEN(= platform-scope 로그인이 `/dashboards` 로 완주). Testcontainers/E2E 는 CI Linux 권위
      (로컬 Windows Docker 불가). 동반 실패였던 `scm-platform E2E` / `web-store full-stack` 은 별건 간헐 flake로
      본 태스크 범위 아님(필요 시 `rerun --failed`).

## Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` §Tenant Model — SUPER_ADMIN `tenant_id="*"`
  platform-scope, tenant_type = 라우팅·권한 힌트(B2C_CONSUMER|B2B_ENTERPRISE 2값). `*` 는 실 tenant 아님.
- ADR-002 (platform-scope 센티넬 semantics).

## Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` — `GET /internal/tenants/{tenantId}`
  (BE-407 이 소비 추가). 본 수정은 `*` 에 대해 이 호출을 **하지 않도록** 함(계약 shape 불변).

## Edge Cases

- `*` → DEFAULT_TENANT_TYPE(네트워크 0). account-service 가 404 를 줬어도 기존 empty→DEFAULT 경로가 동일 값을
  내므로 값 정합성 유지(A/B안 수렴).
- 실 tenant(fan-platform/ecommerce/wms) → 기존 동작 불변(프리시드/조회/캐시).
- account-service 다운(실 tenant 조회 중) → 기존대로 `AccountServiceUnavailableException` 전파(fail-closed 유지).
- refresh 경로도 `resolve("*")` 호출 → 동일 가드로 복구(별도 수정 불필요).

## Failure Scenarios

- 가드를 캐시 조회 뒤에 두면 첫 `*` 조회가 여전히 네트워크(400) → 무효. 반드시 null/blank 가드 직후·조회 이전.
- 신규 tenant_type enum 값 도입 시 → 불필요한 spec/consumer 파급(본 태스크는 값 불변으로 회피).
- `AccountServiceClient` 4xx 정책을 완화(400→empty)하면 → 실 tenant 오분류 위험 재도입(BE-407 회귀). 건드리지 말 것.
