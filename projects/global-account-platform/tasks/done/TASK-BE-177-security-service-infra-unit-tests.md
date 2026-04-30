---
id: TASK-BE-177
type: BE
title: security-service AccountServiceClient + MaxMindGeoLookup 단위 테스트
status: ready
target_service: security-service
created: 2026-04-29
---

# TASK-BE-177: security-service AccountServiceClient + MaxMindGeoLookup 단위 테스트

## Goal

`security-service` 인프라 어댑터 두 개에 대한 단위 테스트를 추가한다.

- `AccountServiceClient` — auto-lock POST HTTP 동작 (200/409/4xx/5xx/network fault)
- `MaxMindGeoLookup` — DB 없는 disabled 경로 및 경계 입력 처리

## Scope

- `apps/security-service/src/test/.../infrastructure/client/AccountServiceClientUnitTest.java` (신규)
- `apps/security-service/src/test/.../infrastructure/geo/MaxMindGeoLookupUnitTest.java` (신규)
- 프로덕션 코드 수정 없음

## Acceptance Criteria

- [ ] `AccountServiceClient.lock()` — 200 (previousStatus != "LOCKED") → `Status.SUCCESS`
- [ ] `AccountServiceClient.lock()` — 200 (previousStatus == "LOCKED") → `Status.ALREADY_LOCKED`
- [ ] `AccountServiceClient.lock()` — 409 → `Status.INVALID_TRANSITION`
- [ ] `AccountServiceClient.lock()` — 4xx (409 제외) → `Status.FAILURE`
- [ ] `AccountServiceClient.lock()` — 5xx → `Status.FAILURE`
- [ ] `AccountServiceClient.lock()` — 네트워크 오류 → `Status.FAILURE`
- [ ] `MaxMindGeoLookup` — DB 없이 init() 호출 → `isAvailable()` = false
- [ ] `MaxMindGeoLookup.resolve()` — isAvailable() false 일 때 → `Optional.empty()`
- [ ] `MaxMindGeoLookup.resolve()` — null IP → `Optional.empty()`
- [ ] `MaxMindGeoLookup.resolve()` — 공백 IP → `Optional.empty()`
- [ ] `MaxMindGeoLookup.resolve()` — 마스킹된 IP ("1.2.3.*") → `Optional.empty()`

## Related Specs

- `specs/services/security-service/architecture.md`
- `specs/features/abnormal-login-detection.md`

## Related Contracts

- `specs/contracts/http/internal/security-to-account.md`

## Edge Cases

- `AccountServiceClient` 재시도 지연을 피하기 위해 `maxAttempts=1`, `initialBackoffMs=1` 설정
- `MaxMindGeoLookup.init()` 는 package-private — 테스트를 같은 패키지에 위치
- DB 없는 경우 classpath, 환경변수, 설정 경로 모두 탐색 후 disabled 상태 유지
- `@PostConstruct` 는 Spring context 없이 수동 호출로 대체

## Failure Scenarios

- WireMock `Fault.EMPTY_RESPONSE` → `Status.FAILURE` (예외 전파 없음)
- 5xx 응답 + maxAttempts=1 → 재시도 없이 즉시 `Status.FAILURE`
