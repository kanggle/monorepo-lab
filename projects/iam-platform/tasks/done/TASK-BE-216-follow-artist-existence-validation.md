---
id: TASK-BE-216
title: "FollowArtistUseCase — artist 계정 존재 검증 추가"
type: feature
status: ready
service: community-service
---

## Goal

`FollowArtistUseCase.follow()` 에 artist 계정 존재 여부 검증이 없어, 존재하지 않는 `artistAccountId` 로 팔로우 시 성공하고 이후 feed가 빈 결과를 반환한다.  
`community-api.md`에 ARTIST_NOT_FOUND(404) 응답이 선언되어 있으므로, account-service 내부 API를 호출해 artist 계정 존재를 검증하고 미존재 시 404를 반환하도록 구현한다.

## Scope

### 추가 (스펙/컨트랙트 선행)
- `specs/contracts/http/internal/community-to-account.md`
  - `GET /internal/accounts/{accountId}` — 200(존재) / 404(미존재) 응답 문서화
  - caller constraints: community-service, internal-only, fail-open on 5xx/timeout (404만 ARTIST_NOT_FOUND로 처리)

### 수정 (스펙)
- `specs/services/community-service/architecture.md` Integration Rules 섹션 (line 128-129)
  - community-to-account.md 링크 추가

### 추가 (구현)
- `domain/access/ArtistAccountChecker.java` — port 인터페이스
  ```java
  public interface ArtistAccountChecker {
      void assertExists(String artistAccountId);  // ArtistNotFoundException if 404
  }
  ```
- `application/exception/ArtistNotFoundException.java`
- `infrastructure/client/AccountExistenceClient.java` — `ArtistAccountChecker` 구현체
  - `GET /internal/accounts/{accountId}` 호출 (account-service)
  - 404 → `ArtistNotFoundException`
  - 5xx / timeout → fail-open (예외 삼킴, log warn)
  - RestClient 사용, `${community.account-service.base-url}` 재사용
  - connect-timeout: `${community.account-service.connect-timeout-ms:2000}`, read-timeout: `${community.account-service.read-timeout-ms:3000}`

### 수정 (구현)
- `application/FollowArtistUseCase.java`
  - 생성자에 `ArtistAccountChecker` 주입
  - `follow()` 메서드에서 self-follow 확인 직후, duplicate 확인 전에 `checker.assertExists(artistAccountId)` 호출
  - TODO 주석 제거

- `presentation/exception/GlobalExceptionHandler.java`
  - `ArtistNotFoundException` → 404 ARTIST_NOT_FOUND 핸들러 추가

### 테스트
- `application/FollowArtistUseCaseTest.java` 업데이트
  - `ArtistAccountChecker` mock 추가
  - 기존 테스트에 `when(checker.assertExists(...))` stub 추가 (no-op)
  - 신규: `follow_artistNotFound_throwsArtistNotFoundException`

- `infrastructure/client/AccountExistenceClientTest.java` (WireMock 단위 테스트)
  - 200 → assertExists 정상 반환
  - 404 → ArtistNotFoundException
  - 503 → fail-open (예외 없음)
  - 네트워크 fault → fail-open (예외 없음)

## Acceptance Criteria

- [ ] `specs/contracts/http/internal/community-to-account.md` 생성
- [ ] `specs/services/community-service/architecture.md` Integration Rules에 community-to-account.md 링크 추가
- [ ] `ArtistAccountChecker` 인터페이스 추가
- [ ] `ArtistNotFoundException` 추가
- [ ] `AccountExistenceClient` 추가 — 404만 ArtistNotFoundException, 5xx/timeout은 fail-open
- [ ] `FollowArtistUseCase.follow()` 에서 artist 존재 검증 수행
- [ ] `GlobalExceptionHandler` 에 ARTIST_NOT_FOUND 404 핸들러 추가
- [ ] `FollowArtistUseCaseTest` 업데이트 및 신규 시나리오 포함
- [ ] `AccountExistenceClientTest` (WireMock) 추가
- [ ] `./gradlew :apps:community-service:test` 통과

## Related Specs

- `specs/services/community-service/architecture.md`
- `specs/contracts/http/community-api.md` (POST /api/community/subscriptions/artists/{artistAccountId} — ARTIST_NOT_FOUND 404)

## Related Contracts

- `specs/contracts/http/internal/community-to-account.md` (신규 생성)

## Edge Cases

- account-service가 503/timeout일 때 → fail-open: 팔로우 허용 (account-service 가용성에 coupling 최소화)
- artistAccountId가 null/blank → 컨트롤러 레벨 @PathVariable 검증으로 처리, use-case 도달 전 차단
- self-follow + artist 미존재 동시 → self-follow 먼저 검사 (IllegalArgumentException 우선)

## Failure Scenarios

- account-service 완전 장애 → fail-open 정책으로 팔로우 허용, silent follow 상태 유지
- 잘못된 base-url 설정 → client 초기화 실패 (빈 문자열 URL은 Spring RestClient가 요청 시 실패)
