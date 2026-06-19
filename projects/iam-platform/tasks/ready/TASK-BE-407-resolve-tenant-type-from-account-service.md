---
id: TASK-BE-407
title: resolveTenantType 하드코딩 폴백 제거 — account-service tenant_type 조회로 대체
status: ready
project: iam-platform
service: auth-service
type: bugfix
created: 2026-06-20
---

# TASK-BE-407 — resolveTenantType: account-service tenant_type 조회로 대체

## Goal

auth-service `TenantContext.resolveTenantType` 의 하드코딩 2값 폴백("fan-platform"=B2C_CONSUMER, 그 외=B2B_ENTERPRISE)을 제거하고, account-service 가 보유한 권위 `tenant_type` 을 조회(`GET /internal/tenants/{tenantId}`)해 JWT `tenant_type` 클레임을 정확히 채운다. TASK-BE-231(account-service provisioning) 이 이미 `tenants.tenant_type` 을 저장·노출하므로 데이터소스는 실재한다.

## Background / Problem

- 현재 `TenantContext.resolveTenantType(tenantId)`(정적)는 `"fan-platform"→B2C_CONSUMER`, 그 외 전부 `B2B_ENTERPRISE` 로 고정. → 신규 B2C 테넌트(예: ecommerce)가 `B2B_ENTERPRISE` 로 **오분류**되어 JWT `tenant_type` 클레임이 틀림.
- 호출처 4곳: `LoginUseCase`(~146), `RefreshTokenUseCase`(~137), `CredentialAuthenticationProvider`(~91), `SavedRequestTenantResolver`(~109).
- 데이터소스 검증됨: account-service `TenantLifecycleController` 가 `GET /internal/tenants/{tenantId}` → `tenantType`("B2C_CONSUMER"|"B2B_ENTERPRISE") 반환. 저장=`tenants.tenant_type VARCHAR(20)`. (HARDSTOP-08/09 아님.)

## Scope

- **IN**: `AccountServicePort` 에 tenant_type 조회 메서드 추가 + `AccountServiceClient` 구현(기존 RestClient 패턴), 신규 `TenantTypeResolver`(캐시 + fail-safe), 4 호출처를 정적 호출 → 주입 resolver 로 교체, 정적 `resolveTenantType` 제거, `auth-to-account.md` 컨트랙트에 호출 문서화(spec-first), 테스트.
- **OUT**: account-service 측 변경(엔드포인트 이미 존재), TenantType enum 변경, 토큰 구조 변경(클레임 값만 정확해짐).

## Acceptance Criteria

- [ ] **AC-1 (spec-first)**: `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` 에 auth-service → account-service `GET /internal/tenants/{tenantId}`(tenant_type 조회) 호출을 문서화한다(요청/응답 shape, 호출 목적). 코드 변경 전 갱신.
- [ ] **AC-2**: `AccountServicePort` 에 `Optional<String> getTenantType(String tenantId)`(또는 동등) 추가. `AccountServiceClient` 가 `GET /internal/tenants/{tenantId}` 호출 — 200→tenantType, 404→`Optional.empty()`, 5xx/네트워크장애→`AccountServiceUnavailableException`(기존 예외 재사용). 기존 internal 호출의 인증 방식(client_credentials 등)과 동일 패턴.
- [ ] **AC-3**: 신규 `infrastructure/tenant/TenantTypeResolver`(빈) — `resolve(tenantId)` 가 캐시 우선 조회 후 미스 시 `AccountServicePort.getTenantType` 호출. `TenantContext.DEFAULT_TENANT_ID`("fan-platform")→`DEFAULT_TENANT_TYPE`(B2C_CONSUMER)는 캐시 프리시드해 hot-path 네트워크 0. 캐시는 기존 코드베이스 패턴(가능하면 무의존, 아니면 Caffeine — `spring-boot-dependencies` BOM 관리 버전, `build.gradle` 추가) 사용.
- [ ] **AC-4**: 4 호출처(`LoginUseCase`, `RefreshTokenUseCase`, `CredentialAuthenticationProvider`, `SavedRequestTenantResolver`)를 정적 `TenantContext.resolveTenantType(...)` → 주입된 `tenantTypeResolver.resolve(...)` 로 교체. `@RequiredArgsConstructor` 필드 추가.
- [ ] **AC-5 (Spring Security 계약)**: `CredentialAuthenticationProvider.authenticate()` 내에서 `AccountServiceUnavailableException`(RuntimeException) 발생 시 `AuthenticationServiceException`(AuthenticationException 하위)로 래핑한다 — 그러지 않으면 500 누수(인프라 장애가 깔끔한 503/401 경계로 매핑 안 됨).
- [ ] **AC-6**: 정적 `TenantContext.resolveTenantType` 제거(또는 `@Deprecated` + Javadoc→resolver). `DEFAULT_TENANT_ID`/`DEFAULT_TENANT_TYPE` 상수는 캐시 시드·`defaultContext()` 에서 계속 사용하므로 유지.
- [ ] **AC-7 (핵심 회귀 테스트)**: non-fan-platform B2C 테넌트(예: "ecommerce")에 대해 `resolve` 가 `B2C_CONSUMER` 를 반환하고 그 값이 토큰 생성 경로의 `TenantContext` 에 실림을 검증한다(기존엔 B2B_ENTERPRISE 로 틀렸던 케이스). 추가: 캐시 히트 시 포트 미호출, 미스→1회 호출, not-found/장애 폴백 동작, 4 호출처 단위테스트 갱신.
- [ ] **AC-8**: `:auth-service:test` GREEN(단위). Testcontainers IT 는 CI Linux 권위(로컬 Windows Docker 불가).

## Related Specs

- `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` (AC-1 갱신)
- account-service `tenants.tenant_type` (TASK-BE-231 provisioning)

## Related Contracts

- account-service `GET /internal/tenants/{tenantId}` (이미 존재; auth-service 가 소비 추가)

## Edge Cases

- account-service 다운 → `AccountServiceUnavailableException` → login/refresh 는 명확한 503 경계(AC-5 의 security 래핑 포함). 정적 하드코드는 절대 실패 안 했으나, 정확성·fail-safe 가 우선.
- unknown tenant(404) → `Optional.empty()`. resolver 정책: 명확한 실패(`AccountServiceUnavailableException`/도메인 예외) 또는 안전 기본값 중 코드베이스 정합 선택 — 단 조용한 오분류 금지.
- fan-platform → 프리시드 캐시로 네트워크 0(회귀 없음).
- hot-path 성능 → 캐시 필수(로그인/리프레시마다 동기 HTTP 금지).

## Failure Scenarios

- AC-5 누락 → account-service 장애 시 인증 경로가 500 누수.
- 캐시 미적용 → 로그인 latency 회귀 + account-service 부하.
- 정적 메서드만 남기고 호출처 미교체 → 무효(여전히 오분류). 4곳 전부 교체 필수.
