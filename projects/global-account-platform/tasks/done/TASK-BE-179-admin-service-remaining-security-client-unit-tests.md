---
id: TASK-BE-179
type: BE
title: admin-service 나머지 보안 컴포넌트 + AuthServiceClient 단위 테스트
status: ready
target_service: admin-service
created: 2026-04-29
---

# TASK-BE-179: admin-service 나머지 보안 컴포넌트 + AuthServiceClient 단위 테스트

## Goal

TASK-BE-174/175에서 다루지 않은 admin-service 인프라 어댑터 4개에 대한 단위 테스트를 추가한다.

- `AdminJwtKeyStore` — RSA 키 로딩 유효성 검증 (키 크기, PEM 형식, activeKid)
- `OperatorContextHolder` — SecurityContext에서 OperatorContext 추출
- `RedisTokenBlacklistAdapter` — JTI 블랙리스트 set/check (Mockito, fail-closed)
- `AuthServiceClient` — force-logout HTTP 호출 (WireMock: 200/4xx/5xx/network fault)

## Scope

- `apps/admin-service/src/test/.../infrastructure/security/AdminJwtKeyStoreUnitTest.java` (신규)
- `apps/admin-service/src/test/.../infrastructure/security/OperatorContextHolderUnitTest.java` (신규)
- `apps/admin-service/src/test/.../infrastructure/security/RedisTokenBlacklistAdapterUnitTest.java` (신규)
- `apps/admin-service/src/test/.../infrastructure/client/AuthServiceClientUnitTest.java` (신규)
- 프로덕션 코드 수정 없음

## Acceptance Criteria

- [ ] `AdminJwtKeyStoreUnitTest` — 유효한 2048-bit RSA PEM → activeKid/privateKey/publicKey 정상 로드
- [ ] `AdminJwtKeyStoreUnitTest` — blank PEM → `IllegalStateException("blank")`
- [ ] `AdminJwtKeyStoreUnitTest` — 유효하지 않은 Base64 → `IllegalStateException`
- [ ] `AdminJwtKeyStoreUnitTest` — 1024-bit RSA (크기 미달) → `IllegalStateException("at least 2048 bits")`
- [ ] `AdminJwtKeyStoreUnitTest` — activeKid가 키 맵에 없음 → `IllegalStateException`
- [ ] `AdminJwtKeyStoreUnitTest` — 빈 키 맵 → `IllegalStateException("must not be empty")`
- [ ] `OperatorContextHolderUnitTest` — 유효한 OperatorContext principal → `require()` 반환
- [ ] `OperatorContextHolderUnitTest` — Authentication 없음 → `OperatorUnauthorizedException`
- [ ] `OperatorContextHolderUnitTest` — principal 타입 불일치 → `OperatorUnauthorizedException`
- [ ] `RedisTokenBlacklistAdapterUnitTest` — 정상 blacklist → key/value/TTL로 set 호출
- [ ] `RedisTokenBlacklistAdapterUnitTest` — null TTL → 1초 폴백으로 set 호출
- [ ] `RedisTokenBlacklistAdapterUnitTest` — 키 존재 → `isBlacklisted()` true
- [ ] `RedisTokenBlacklistAdapterUnitTest` — 키 없음 → `isBlacklisted()` false
- [ ] `RedisTokenBlacklistAdapterUnitTest` — Redis 오류 → fail-closed (true 반환)
- [ ] `AuthServiceClientUnitTest` — 200 응답 → `ForceLogoutResponse(accountId, revokedTokenCount)` 반환
- [ ] `AuthServiceClientUnitTest` — 4xx 응답 → `NonRetryableDownstreamException`
- [ ] `AuthServiceClientUnitTest` — 5xx 응답 → `DownstreamFailureException`
- [ ] `AuthServiceClientUnitTest` — 네트워크 오류 → `DownstreamFailureException`

## Related Specs

- `specs/services/admin-service/architecture.md`

## Related Contracts

- None

## Edge Cases

- `AdminJwtKeyStore`는 PKCS#8 PEM 파싱 — Base64 인코딩이 올바르지 않으면 `IllegalStateException` 발생
- `RedisTokenBlacklistAdapter.isBlacklisted()` — Redis 오류 시 fail-closed(true) 정책 검증 필요
- `AuthServiceClient` — 4xx는 `NonRetryableDownstreamException`, 5xx는 기본 `DownstreamFailureException` (서브타입이 아님) 구분

## Failure Scenarios

- `AdminJwtKeyStore`: PEM blank/invalid Base64/key too small/activeKid missing/empty map → `IllegalStateException`
- `OperatorContextHolder`: no auth or wrong principal type → `OperatorUnauthorizedException`
- `RedisTokenBlacklistAdapter`: Redis `QueryTimeoutException` → fail-closed true
- `AuthServiceClient`: WireMock `Fault.EMPTY_RESPONSE` → `DownstreamFailureException`
