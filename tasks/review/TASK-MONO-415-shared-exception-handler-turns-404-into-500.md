# Task ID

TASK-MONO-415

# Title

공유 예외 핸들러가 404(NoResourceFound)를 500 으로 변질 — `libs/java-web-servlet`, 소비 서비스 11개 파급

# Status

review

# Owner

backend / platform

# Task Tags

- code
- lib
- cross-project

---

# 🔴 발견 경위 (2026-07-15 IAM 라이브 풀스택 스윕)

IAM 게이트웨이 경유로 존재하지 않는 경로(`/api/accounts/definitely-not-a-real-endpoint`, 그리고 오배송된 세션 경로)를 호출했더니 **404 가 아니라 `500 INTERNAL_ERROR`** 가 나왔다. 원인은 IAM 코드가 아니라 **공유 라이브러리** `libs/java-web-servlet` 의 `CommonGlobalExceptionHandler` 였다. 아래는 실측 — **착수 시 재현·소비자 재열거부터(모집단 재측정).**

---

# Goal

`libs/java-web-servlet` 의 `CommonGlobalExceptionHandler` 가 "매핑 없는 경로"(Spring 의 `NoResourceFoundException` / `NoHandlerFoundException`)를 **404** 로 응답하도록 고친다. 현재는 포괄 `@ExceptionHandler(Exception.class)` 가 이를 삼켜 **500 INTERNAL_ERROR** 로 변질시킨다.

## Root Cause (실측, 재검증 대상)

`libs/java-web-servlet/src/main/java/com/example/web/exception/CommonGlobalExceptionHandler.java` 의 최종 핸들러:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
}
```

Spring 6.1+ 는 정적/매핑 미존재 경로에 `org.springframework.web.servlet.resource.NoResourceFoundException`(HTTP 404 의미)을 던지는데, 위 포괄 핸들러가 이를 잡아 500 으로 만든다. 클라이언트는 "서버 버그" 로 오인하고, 오배송·오타 경로가 404 대신 500 으로 관측된다(디버깅 함정 — `TASK-BE-508` 에서 세션 오배송이 500 으로 보인 원인이기도 함).

## 파급 범위 — 🔵 착수 시 재열거로 정정됨 (2026-07-15)

**최초 스냅샷(가설)**: "iam 4 + ecommerce 7 = 11 소비자". **이 숫자는 틀렸다.** 착수 재grep(AC-0)이 정정:

- **공유 `CommonGlobalExceptionHandler` 를 실제 `extends`/import 하는 건 iam-platform 4개뿐** — account-service, admin-service(AuthExceptionHandler), auth-service(AuthExceptionHandler), security-service(QueryExceptionHandler).
- **ecommerce 7개(order/product/promotion/search/settlement/shipping/user, 그 외 포함)는 공유 base 를 안 쓴다** — 각자 **독립 `GlobalExceptionHandler` 복사본**에 동일한 `@ExceptionHandler(Exception.class)`→`INTERNAL_ERROR` 버그를 갖고 있고, `libs/java-web` 의 `ErrorResponse` 만 소비한다.

**결과**:
1. 이 라이브러리 fix 는 **iam 4개만** 상속으로 자동 교정 — 그래서 사실상 **cross-project adaptation 이 없다**(ecommerce 는 base 미소비 → 손댈 것 없음). "cross-project 원자 PR" 이라는 최초 AC 는 무효화됨; 다만 여전히 `libs/` 변경이므로 monorepo-level 이고 CI 전 매트릭스가 회귀를 검증한다.
2. **ecommerce 7개의 갈라진 복사본은 이 fix 로 안 고쳐진다** — 별개 결함 클래스(라이브러리 소비가 아니라 *전파된 복사본*). → **후속 `TASK-BE-504`**(ecommerce project) 로 분리. 원자 PR 을 조용히 7개로 확장하지 않았다(에이전트가 플래그).

"없는 경로→500" 단언 화석: iam 4개 소비자에 **0건**(`INTERNAL_ERROR` 히트 3건은 전부 정당 — 진짜 AuditFailure 500 + WireMock 다운스트림 stub 2). ecommerce 의 `isNotFound`/`isInternalServerError` 단언은 매핑된 엔드포인트 대상이라 무영향.

> 교훈: `ready/` 티켓의 "11" 은 *"패키지/클래스명 grep 히트"* 를 *"공유 base 실소비"* 로 혼동한 값이었다. 착수=재측정(AC-0)이 이를 잡았다 — 물려받은 범위는 가설이다.

# Scope

## In Scope (shared path — `libs/`)
- `CommonGlobalExceptionHandler` 에 `NoResourceFoundException`(+ 필요 시 `NoHandlerFoundException`) 전용 `@ExceptionHandler` 추가 → `404` + 표준 `ErrorResponse`(예: `NOT_FOUND`). 포괄 `Exception.class` 핸들러보다 **더 구체적**이므로 우선 매칭됨.
- 포괄 핸들러가 다른 프레임워크 예외(405 `HttpRequestMethodNotSupportedException`, 415 등)를 500 으로 삼키는 곳이 더 있는지 **동반 점검**(스코프 폭주 방지 — 최소한 404 는 확실히, 나머지는 별도 판단).

## In Scope (per-project adaptation — `projects/<name>/`)
- 각 소비 서비스가 자체 `@ExceptionHandler(Exception.class)` 나 override 로 이 동작을 가리지 않는지 확인. iam 은 별도로 서비스별 SecurityConfig 의 인증 실패 경로(401)가 있으나 그것과는 무관.
- 404 응답 형태 변경이 계약/테스트를 깨지 않는지 각 프로젝트에서 확인(특히 "없는 경로 → 500" 을 단언하는 테스트가 있으면 정정 — 그런 테스트 자체가 결함의 화석).

## Out of Scope
- 각 서비스의 도메인 예외 핸들러(정상).
- IAM account/auth-service 의 `/internal/**` 401 경로(별개 SecurityConfig, 무관).

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: 아무 소비 서비스에서 존재하지 않는 경로 호출 → **현재 500 INTERNAL_ERROR** 재현. 그리고 `CommonGlobalExceptionHandler` 소비자 목록을 grep 으로 **재열거**(스냅샷과 대조).
- [ ] 매핑 없는 경로가 **404** + 표준 에러 바디 반환(iam + ecommerce 대표 서비스 각 1개 이상에서 확인).
- [ ] 기존 동작 회귀 0: `VALIDATION_ERROR`(400), `CONFLICT`(409), 진짜 서버 오류(500)는 그대로.
- [ ] **cross-project 원자 PR** — 라이브러리 변경 + 영향 받는 모든 프로젝트가 한 PR 에서 GREEN(staggered PR 로 나누지 말 것; CLAUDE.md § Cross-Project Changes).
- [ ] `libs/java-web-servlet` 에 "없는 경로 → 404" 단위 테스트 추가.
- [ ] 루트 `./gradlew check` GREEN (전 소비자 빌드/테스트).

# Related Specs

> Before reading: 루트 `tasks/INDEX.md` § "When to Use Root vs Project Tasks" (이 결함은 shared `libs/` 이므로 monorepo-level).

- `platform/shared-library-policy.md` (공유 라이브러리 — 프로젝트 특정 내용 금지, 이 변경은 project-agnostic 이어야 함)
- 각 소비 서비스의 `specs/services/<svc>/architecture.md` 의 에러 응답 규약

# Related Contracts

- 각 프로젝트의 HTTP 계약이 404 응답 형태를 어떻게 규정하는지(정합 확인). IAM: `specs/contracts/http/*.md` 의 에러 코드 표.

# Target

- `libs/java-web-servlet` (주) + 소비 서비스 11개(적응/검증)

# Edge Cases

- `NoResourceFoundException` 은 Spring 버전 의존(6.1+) — 프로젝트별 Spring Boot 버전이 다르면 존재 여부 확인. 구버전은 `NoHandlerFoundException`(+ `throw-exception-if-no-handler-found`, `add-mappings=false` 설정 필요).
- 포괄 핸들러를 그대로 두고 구체 핸들러만 추가 — 순서가 아니라 **구체성**으로 매칭되므로 안전. 단 각 서비스가 `Exception.class` 를 자체 override 하면 그쪽이 이김(가려짐 확인).
- 404 바디의 에러 코드 명칭(`NOT_FOUND`)이 기존 프로젝트별 관례와 충돌하지 않는지.

# Failure Scenarios

- 라이브러리만 고치고 소비자 테스트를 안 돌리면, 어느 프로젝트가 "없는 경로 → 500" 을 단언하고 있었을 때 그 프로젝트 CI 가 RED(원자 PR 로 함께 잡아야 함).
- `add-mappings`/`throw-exception-if-no-handler-found` 미설정 서비스에서는 `NoHandlerFoundException` 이 아예 안 던져질 수 있음 — 실제 던져지는 예외 타입을 서비스별로 라이브 확인.
- shared 파일에 프로젝트 특정 내용(서비스명·경로) 유입 → HARDSTOP-03.

# Definition of Done

- [ ] AC-0 재측정 + 소비자 재열거
- [ ] 라이브러리 404 핸들러 추가 + 단위 테스트
- [ ] cross-project 원자 PR, 전 소비자 GREEN
- [ ] Ready for review
