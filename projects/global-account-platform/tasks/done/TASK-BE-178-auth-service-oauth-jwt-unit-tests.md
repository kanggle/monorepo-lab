---
id: TASK-BE-178
type: BE
title: auth-service KakaoOAuthClient + JwksEndpointProvider 단위 테스트
status: ready
target_service: auth-service
created: 2026-04-29
---

# TASK-BE-178: auth-service KakaoOAuthClient + JwksEndpointProvider 단위 테스트

## Goal

`auth-service` 인프라 어댑터 두 개에 대한 단위 테스트를 추가한다.

- `KakaoOAuthClient` — code exchange 2-step HTTP 동작 (정상/token 오류/user info 오류/network fault)
- `JwksEndpointProvider` — RSA 공개키 → JWKS JSON 변환 정확성

## Scope

- `apps/auth-service/src/test/.../infrastructure/oauth/KakaoOAuthClientUnitTest.java` (신규)
- `apps/auth-service/src/test/.../infrastructure/jwt/JwksEndpointProviderUnitTest.java` (신규)
- 프로덕션 코드 수정 없음

## Acceptance Criteria

- [ ] `KakaoOAuthClient` — 정상 응답 → `OAuthUserInfo(providerUserId, email, name, provider=KAKAO)` 반환
- [ ] `KakaoOAuthClient` — token 응답에 `access_token` 누락 → `OAuthProviderException`
- [ ] `KakaoOAuthClient` — token endpoint 5xx → `OAuthProviderException`
- [ ] `KakaoOAuthClient` — user info endpoint 5xx → `OAuthProviderException`
- [ ] `KakaoOAuthClient` — 네트워크 오류 → `OAuthProviderException`
- [ ] `JwksEndpointProvider` — `keys` 배열 포함, kty/kid/use/alg/n/e 필드 검증
- [ ] `JwksEndpointProvider` — n/e 값이 Base64Url(패딩 없음), leading zero 제거 후 원본 값과 일치

## Related Specs

- `specs/services/auth-service/architecture.md`

## Related Contracts

- `specs/contracts/http/internal/auth-jwks.md` (존재하는 경우)

## Edge Cases

- `KakaoOAuthClient`는 token endpoint와 user info endpoint 두 곳을 호출 — WireMock 동일 서버에서 path 구분
- `JwksEndpointProvider` — `BigInteger.toByteArray()` leading zero byte 제거 로직 검증 필요
- Base64Url 인코딩 패딩(`=`) 없이 인코딩되어야 함

## Failure Scenarios

- WireMock `Fault.EMPTY_RESPONSE` on token endpoint → `OAuthProviderException`
- token endpoint 5xx → `OAuthProviderException`
- user info endpoint 5xx (token 교환 성공 이후) → `OAuthProviderException`
