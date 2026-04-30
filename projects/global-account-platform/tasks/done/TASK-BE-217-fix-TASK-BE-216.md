---
id: TASK-BE-217
title: "Fix TASK-BE-216: ARTIST_NOT_FOUND 에러 코드 등록 및 도메인 레이어 의존성 방향 수정"
type: fix
status: ready
service: community-service
---

## Goal

TASK-BE-216 리뷰에서 발견된 두 가지 Critical 이슈를 수정한다.

1. `ARTIST_NOT_FOUND` 에러 코드가 `platform/error-handling.md`에 등록되지 않았음  
   (`error-handling.md` 규칙: "Error codes must be registered in this document before use.")
2. `domain/access/ArtistAccountChecker.java`가 `application/exception/ArtistNotFoundException`을 import하여  
   도메인 레이어가 애플리케이션 레이어에 의존하는 레이어 방향 위반이 발생함

## Scope

### 수정 (스펙/플랫폼)
- `platform/error-handling.md`
  - Community 도메인 섹션(또는 saas 도메인 섹션)에 `ARTIST_NOT_FOUND | 404 | Artist account does not exist` 추가

### 수정 (구현)
- `domain/access/ArtistNotFoundException.java` (신규 생성) — 예외 클래스를 `domain` 레이어로 이동
  - `package com.example.community.domain.access;`
  - `public class ArtistNotFoundException extends RuntimeException`
- `domain/access/ArtistAccountChecker.java`
  - `application/exception/ArtistNotFoundException` import 제거
  - `domain/access/ArtistNotFoundException` import로 교체
  - `throws ArtistNotFoundException` 선언 제거 (RuntimeException이므로 불필요)
- `application/exception/ArtistNotFoundException.java`
  - 기존 클래스 삭제하거나, `domain/access/ArtistNotFoundException`을 re-export/extends 하도록 변경
  - 가장 단순한 접근: `application/exception/ArtistNotFoundException.java` 삭제 후 모든 참조를 `domain.access.ArtistNotFoundException`으로 교체
- `infrastructure/client/AccountExistenceClient.java`
  - import를 `domain.access.ArtistNotFoundException`으로 교체
  - 불필요한 `catch (ArtistNotFoundException e) { throw e; }` 블록 제거 (Warning 수정)
- `presentation/exception/GlobalExceptionHandler.java`
  - import를 `domain.access.ArtistNotFoundException`으로 교체

### 수정 (스펙)
- `specs/services/community-service/architecture.md`
  - Internal Structure Rule 다이어그램에 `ArtistAccountChecker.java` 항목 추가 (domain/access/ 섹션)
  - `infrastructure/client/` 섹션에 `AccountExistenceClient.java` 항목 추가

### 수정 (테스트)
- `application/FollowArtistUseCaseTest.java`
  - import를 `domain.access.ArtistNotFoundException`으로 교체
- `infrastructure/client/AccountExistenceClientTest.java`
  - import를 `domain.access.ArtistNotFoundException`으로 교체

## Acceptance Criteria

- [ ] `platform/error-handling.md`에 `ARTIST_NOT_FOUND | 404` 등록
- [ ] `ArtistNotFoundException`이 `domain/access/` 패키지에 위치
- [ ] `domain/access/ArtistAccountChecker.java`가 application 레이어를 import하지 않음
- [ ] `ArtistAccountChecker.assertExists()`에 `throws` 선언 없음
- [ ] `AccountExistenceClient`의 중복 catch 블록 제거
- [ ] `specs/services/community-service/architecture.md` Internal Structure 다이어그램 갱신
- [ ] `./gradlew :apps:community-service:test` 통과

## Related Specs

- `specs/services/community-service/architecture.md`
- `platform/error-handling.md`
- `specs/contracts/http/internal/community-to-account.md`

## Related Contracts

- `specs/contracts/http/community-api.md`

## Edge Cases

- `application/exception/ArtistNotFoundException.java` 삭제 후 다른 서비스 코드에 해당 패키지 참조가 없는지 확인
- `domain.access.ArtistNotFoundException`으로 통일 후 GlobalExceptionHandler가 정상적으로 매핑하는지 확인

## Failure Scenarios

- 패키지 이동 중 순환 import 발생 → domain 레이어는 application/infrastructure를 참조하지 않아야 함
