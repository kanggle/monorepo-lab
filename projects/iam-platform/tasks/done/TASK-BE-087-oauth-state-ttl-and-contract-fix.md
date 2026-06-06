---
id: TASK-BE-087
title: "OAuth state TTL 스펙 일치 수정 및 internal contract MICROSOFT 추가"
status: ready
area: backend
service: auth-service
---

## Goal

`OAuthLoginUseCase`의 state TTL이 스펙(`specs/features/oauth-social-login.md`)에서 **10분**으로 정의되어 있음에도 실제 코드는 5분(`Duration.ofMinutes(5)`)으로 설정되어 있다. 이 불일치를 수정한다.

아울러 `specs/contracts/http/internal/auth-to-account-social.md`의 provider enum이 `'GOOGLE' | 'KAKAO'`만 나열하고 있으나, TASK-BE-056에서 Microsoft provider가 추가 완료되었으므로 계약서를 현행화한다.

## Scope

### In

- `apps/auth-service/src/main/java/com/example/auth/application/OAuthLoginUseCase.java`
  - `STATE_TTL = Duration.ofMinutes(5)` → `Duration.ofMinutes(10)` 변경
- `specs/contracts/http/internal/auth-to-account-social.md`
  - Request `"provider"` 필드 설명에 `'MICROSOFT'` 추가
- `apps/auth-service/src/test/java/com/example/auth/application/OAuthLoginUseCaseTest.java`
  - state TTL 검증 테스트가 있으면 10분으로 업데이트

### Out

- Redis key 스키마 변경 없음 (`oauth:state:{state}` 그대로 유지)
- 다른 OAuth provider 추가 또는 변경
- 게이트웨이 라우팅 변경
- 프론트엔드 변경

## Acceptance Criteria

- [ ] `OAuthLoginUseCase.STATE_TTL`이 `Duration.ofMinutes(10)`으로 변경됨
- [ ] `specs/contracts/http/internal/auth-to-account-social.md`의 provider enum에 `'MICROSOFT'` 포함
- [ ] `./gradlew :apps:auth-service:test` 통과
- [ ] `OAuthLoginUseCaseTest`에서 state 만료 관련 테스트가 있는 경우 TTL 값이 10분으로 업데이트됨

## Related Specs

- `specs/features/oauth-social-login.md` — "state TTL: **10분** (Redis `oauth:state:{state}`)" 정의
- `specs/services/auth-service/redis-keys.md`

## Related Contracts

- `specs/contracts/http/internal/auth-to-account-social.md` — provider enum 현행화
- `specs/contracts/http/auth-api.md` — GET /api/auth/oauth/authorize, POST /api/auth/oauth/callback (변경 없음)

## Edge Cases

- 기존 5분 TTL로 저장된 Redis key는 자연 만료되므로 별도 마이그레이션 불필요
- 계약서 변경은 이미 배포된 account-service 동작에 영향 없음 (문서 현행화만)

## Failure Scenarios

- TTL 변경 후 기존 integration test에서 Redis TTL assertion이 있으면 실패 → 해당 assertion도 함께 수정

## Test Requirements

- `OAuthLoginUseCaseTest`: state TTL 관련 단언이 존재하면 10분 기준으로 업데이트
- `OAuthLoginIntegrationTest`: 변경 영향 없음 (TTL 내에서 동작하는 테스트이므로 그대로 통과)
- 별도 신규 테스트 작성 불필요 (단순 상수 수정)
