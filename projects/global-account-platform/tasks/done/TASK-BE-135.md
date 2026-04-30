# Task ID

TASK-BE-135

# Title

community-service — PostMediaUrlsSerializer 추출 (PublishPostUseCase / UpdatePostUseCase mediaUrls JSON 직렬화 중복 제거)

# Status

ready

# Owner

backend

# Task Tags

- refactor

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

`PublishPostUseCase`와 `UpdatePostUseCase`에 동일한 7줄짜리 `mediaUrls` JSON 직렬화 블록이 반복된다:

```java
String mediaUrlsJson = null;
if (mediaUrls != null && !mediaUrls.isEmpty()) {
    try {
        mediaUrlsJson = objectMapper.writeValueAsString(mediaUrls);
    } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Invalid mediaUrls");
    }
}
```

community-service의 `application` 패키지에 `PostMediaUrlsSerializer @Component`(package-private)를 추가해 두 UseCase의 7줄 블록을 단일 `String mediaUrlsJson = mediaUrlsSerializer.serialize(mediaUrls);` 호출로 대체한다. BE-133에서 `PostAccessGuard @Component`를 추출한 것과 동일한 구조.

---

# Scope

## In Scope

- 신규 파일: `apps/community-service/src/main/java/com/example/community/application/PostMediaUrlsSerializer.java`
  - `@Component` (package-private)
  - 의존성: `ObjectMapper`만 주입
  - public method: `String serialize(List<String> mediaUrls)`
- `PublishPostUseCase` 수정:
  - `ObjectMapper` 의존성 제거
  - `PostMediaUrlsSerializer` 의존성 추가
  - `cmd.mediaUrls()` 직렬화 7줄 → `String mediaUrlsJson = mediaUrlsSerializer.serialize(cmd.mediaUrls());` 1줄
  - import 정리 (`com.fasterxml.jackson.core.JsonProcessingException`, `ObjectMapper` 제거)
- `UpdatePostUseCase` 수정:
  - `ObjectMapper` 의존성 제거
  - `PostMediaUrlsSerializer` 의존성 추가
  - 동일한 7줄 → 1줄 치환
  - import 정리
- 신규 테스트: `apps/community-service/src/test/java/com/example/community/application/PostMediaUrlsSerializerTest.java`
  - 단위 테스트 (real `ObjectMapper`만 사용, 외부 의존성 없음)
  - Korean `@DisplayName` 필수 (`platform/testing-strategy.md`)

## Out of Scope

- `Post` 도메인, `PostRepository` 변경 없음
- `mediaUrls` 자체의 검증 규칙 변경 없음 (URL 형식 검증, 길이 제한 등)
- 다른 UseCase / Controller 변경 없음
- API 계약 / 행위 변경 없음 — 동일한 `IllegalArgumentException("Invalid mediaUrls")` surface 유지

---

# Acceptance Criteria

- [ ] `PostMediaUrlsSerializer.java`가 community-service `application` 패키지에 추가된다 (package-private `class`)
- [ ] `serialize(List<String>)` 메서드가:
  - `null`이거나 비어있으면 `null` 반환
  - 비어있지 않으면 `objectMapper.writeValueAsString(mediaUrls)` 결과 반환
  - `JsonProcessingException` 발생 시 `IllegalArgumentException("Invalid mediaUrls")` 던짐
- [ ] `PublishPostUseCase`, `UpdatePostUseCase`가 각각 7줄 직렬화 블록 대신 `mediaUrlsSerializer.serialize(...)` 1줄 호출
- [ ] 두 UseCase에서 `ObjectMapper` 직접 의존성이 제거된다 (필드, 생성자 인자 모두)
- [ ] `PostMediaUrlsSerializerTest` 단위 테스트 통과 (3건 이상: null, empty, 정상 직렬화)
- [ ] 기존 `PublishPostUseCaseTest`, `UpdatePostUseCaseTest`가 모두 통과한다
- [ ] `./gradlew :apps:community-service:test` 통과

---

# Related Specs

- `specs/services/community-service/architecture.md`
- `specs/services/community-service/overview.md`
- `platform/testing-strategy.md` (Korean `@DisplayName`, 3-part naming)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 행위 변경 없음. `IllegalArgumentException` surface 동일.

---

# Target Service

- `community-service`

---

# Architecture

Follow:

- `specs/services/community-service/architecture.md`
- Layered Architecture: `application` 레이어 내부 컴포넌트 추출 (BE-133 `PostAccessGuard`와 동일한 구조)

---

# Implementation Notes

## PostMediaUrlsSerializer 구현

```java
package com.example.community.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class PostMediaUrlsSerializer {

    private final ObjectMapper objectMapper;

    String serialize(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mediaUrls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid mediaUrls");
        }
    }
}
```

- package-private (`class`, method) — community-service `application` 패키지 내부에서만 사용 (BE-133 `PostAccessGuard`와 동일).
- `IllegalArgumentException` 메시지는 기존 두 호출 지점이 모두 `"Invalid mediaUrls"`를 사용하므로 그대로 유지 (cause는 첨부하지 않음 — 기존 코드도 첨부하지 않음).

## 호출 변환 예시

Before (`PublishPostUseCase.execute`):
```java
String mediaUrlsJson = null;
if (cmd.mediaUrls() != null && !cmd.mediaUrls().isEmpty()) {
    try {
        mediaUrlsJson = objectMapper.writeValueAsString(cmd.mediaUrls());
    } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Invalid mediaUrls");
    }
}
```

After:
```java
String mediaUrlsJson = mediaUrlsSerializer.serialize(cmd.mediaUrls());
```

## 테스트 명명 규칙

`platform/testing-strategy.md`에 따라:
- Korean `@DisplayName` 필수
- 3-part 메서드 이름: `{method}_{condition}_{expectedResult}`

예시:
```java
@Test
@DisplayName("mediaUrls가 null이면 null을 반환한다")
void serialize_nullInput_returnsNull() { ... }

@Test
@DisplayName("mediaUrls가 빈 리스트이면 null을 반환한다")
void serialize_emptyList_returnsNull() { ... }

@Test
@DisplayName("mediaUrls가 정상이면 JSON 배열 문자열을 반환한다")
void serialize_validList_returnsJsonArray() { ... }
```

JsonProcessingException 발생 케이스는 `List<String>`에서는 사실상 발생하지 않으므로 (모든 String이 직렬화 가능) 별도 테스트는 선택사항. 굳이 테스트하려면 mock ObjectMapper 사용해야 하는데, real ObjectMapper만 사용하는 단위 테스트 가치가 더 큼 — 생략 권장.

---

# Edge Cases

- `mediaUrls`가 빈 문자열을 포함하는 리스트 (`List.of("")`): 정상 직렬화 (`["\""]` 형태) — 기존 동작과 동일.
- `mediaUrls`가 `null` 원소를 포함 (`List.of(null)`): `List.of`는 null을 허용하지 않으므로 호출 측에서 NPE — 기존 동작과 동일.
- 매우 긴 URL: 정상 직렬화 — 기존 동작과 동일.

---

# Failure Scenarios

- `JsonProcessingException`이 실제로 발생하는 경우는 매우 드물지만 (`List<String>`은 항상 직렬화 가능), 발생 시 `IllegalArgumentException("Invalid mediaUrls")`로 감싸서 던진다 — 기존 동작과 동일.
- ObjectMapper 빈 주입 실패: Spring 시작 시점에 감지됨 — 기존 동작과 동일.

---

# Test Requirements

- `PostMediaUrlsSerializerTest` 단위 테스트:
  - null 입력 → null 반환
  - 빈 리스트 → null 반환
  - 정상 리스트 → JSON 배열 문자열 반환
- 기존 `PublishPostUseCaseTest`, `UpdatePostUseCaseTest` 전체 케이스 재실행하여 모두 통과 확인
- Korean `@DisplayName`, 3-part 메서드 이름 준수

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (PostMediaUrlsSerializerTest 신규 + 기존 UseCaseTest 회귀 통과)
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
