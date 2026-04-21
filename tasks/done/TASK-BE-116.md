# Task ID

TASK-BE-116

# Title

전 서비스 GlobalExceptionHandler에 HttpMessageNotReadableException 핸들러 추가 — 잘못된 JSON/UUID 본문 요청 시 500 대신 400 VALIDATION_ERROR 반환

# Status

review

# Owner

backend

# Task Tags

- code
- api
- test

---

# Goal

현재 10개 백엔드 서비스(auth, user, product, order, payment, shipping, review, promotion, notification, search) 중 어느 곳도 `org.springframework.http.converter.HttpMessageNotReadableException`을 명시적으로 처리하지 않는다. 이 때문에 요청 본문이 JSON 문법 오류이거나 필드 타입이 맞지 않을 때(예: `UUID` 필드에 `"mock-1"` 같은 비-UUID 문자열) Spring의 메시지 컨버터가 던지는 예외가 generic `Exception` 핸들러로 떨어져 **500 INTERNAL_ERROR**를 반환한다.

`specs/platform/error-handling.md` 및 서비스별 HTTP 컨트랙트(`specs/contracts/http/*.md`)는 "필드가 누락되었거나 유효하지 않은 경우" 400 + `VALIDATION_ERROR`를 명시한다. 따라서 현재 동작은 플랫폼 에러 계약 위반이다.

이 태스크 완료 시 다음이 참이 되어야 한다:

- 모든 서비스의 `GlobalExceptionHandler`에 `HttpMessageNotReadableException` 핸들러가 존재한다.
- 해당 핸들러는 **HTTP 400**과 **`VALIDATION_ERROR`** 코드를 반환한다.
- 응답 메시지는 민감 정보(스택 트레이스, 내부 클래스명, 원본 JSON 등)를 포함하지 않고, 일반적인 검증 실패 문구로 통일한다.
- 실제 버그 재현 케이스(`POST /api/wishlists` with `{"productId":"mock-1"}`)가 500이 아닌 400을 반환한다.

---

# Scope

## In Scope

- 다음 10개 서비스의 `GlobalExceptionHandler`에 `@ExceptionHandler(HttpMessageNotReadableException.class)` 추가:
  - auth-service (`presentation/advice/GlobalExceptionHandler.java`)
  - user-service (`presentation/exception/GlobalExceptionHandler.java`)
  - product-service (`presentation/advice/GlobalExceptionHandler.java`)
  - order-service (`presentation/GlobalExceptionHandler.java`)
  - payment-service (`adapter/in/rest/GlobalExceptionHandler.java`)
  - shipping-service (`interfaces/rest/controller/GlobalExceptionHandler.java`)
  - review-service (`interfaces/advice/GlobalExceptionHandler.java`)
  - promotion-service (`interfaces/rest/controller/GlobalExceptionHandler.java`)
  - notification-service (`adapter/in/rest/GlobalExceptionHandler.java`)
  - search-service (`adapter/inbound/web/GlobalExceptionHandler.java`)
- 각 서비스에 해당 핸들러 단위 테스트 또는 컨트롤러 웹 계층 테스트(`@WebMvcTest` 혹은 기존 테스트 패턴에 맞춰) 추가. 최소 1케이스: 요청 본문에 잘못된 UUID 문자열을 담아 POST → 400 + `VALIDATION_ERROR` 검증.
- `.claude/skills/backend/exception-handling.md`에 `HttpMessageNotReadableException` 핸들러 예시를 추가하여 이후 신규 서비스가 동일 누락을 반복하지 않도록 한다.

## Out of Scope

- 각 서비스의 기존 예외 처리 로직 리팩토링.
- 새로운 에러 코드 추가(기존 `VALIDATION_ERROR` 재사용).
- `ErrorResponse` 포맷 변경.
- 프론트엔드 측 수정(TASK-FE-061에서 처리).

---

# Acceptance Criteria

- [ ] 10개 서비스 모두 `HttpMessageNotReadableException` 핸들러가 추가되어 있다.
- [ ] 각 서비스에서 잘못된 JSON/타입 본문 요청이 HTTP 400, code `VALIDATION_ERROR`로 응답된다(테스트로 검증).
- [ ] 응답 메시지가 원본 JSON 조각, 스택 트레이스, 내부 Java 타입명을 포함하지 않는다.
- [ ] user-service에 대해 `POST /api/wishlists` + body `{"productId":"not-a-uuid"}` 요청이 400 + `VALIDATION_ERROR`를 반환하는 통합/단위 테스트가 존재한다.
- [ ] `.claude/skills/backend/exception-handling.md`의 "Global Exception Handler" 섹션에 `HttpMessageNotReadableException` 예시가 포함되어 있다.
- [ ] 전체 변경 서비스의 기존 테스트가 모두 통과한다.

---

# Related Specs

- `specs/platform/error-handling.md` (§ HTTP Status Code Mapping, § Validation, § Rules)
- `specs/platform/coding-rules.md`
- `specs/contracts/http/wishlist-api.md` (§ Error responses — `VALIDATION_ERROR`)
- 기타 각 서비스 `specs/contracts/http/*-api.md`

# Related Skills

- `.claude/skills/backend/exception-handling.md` (업데이트 대상 + 참고)
- `.claude/skills/backend/validation.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/*-api.md` (모든 HTTP 컨트랙트의 400 VALIDATION_ERROR 규약)

---

# Target Service

- auth-service
- user-service
- product-service
- order-service
- payment-service
- shipping-service
- review-service
- promotion-service
- notification-service
- search-service

---

# Architecture

각 서비스의 기존 `presentation/advice`, `interfaces/rest/controller`, `adapter/in/rest` 위치를 그대로 유지한다. 핸들러는 기존 `GlobalExceptionHandler` 클래스 내부에 메서드로만 추가한다. 서비스별 아키텍처 규칙(`specs/services/<service>/architecture.md`)을 따른다.

---

# Implementation Notes

- 핸들러 예시:

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body"));
}
```

- `e.getMessage()` 또는 `e.getMostSpecificCause().getMessage()`를 응답 메시지에 그대로 노출하지 말 것. 원본 JSON 조각과 Jackson 내부 타입명(`java.util.UUID`, `com.fasterxml.jackson.databind.exc.InvalidFormatException` 등)이 포함될 수 있어 정보 누출 위험. 고정 문구 `"Malformed request body"` 사용.
- `InvalidFormatException`, `MismatchedInputException` 등 하위 예외는 모두 `HttpMessageNotReadableException`으로 감싸져 올라오므로 상위 예외 하나만 핸들링하면 충분하다.
- 일부 서비스의 `GlobalExceptionHandler`는 `@ResponseStatus` + 반환값 스타일을 쓴다. 서비스별 기존 패턴을 따라 일관성 유지.
- `ErrorResponse`는 각 서비스가 `libs/java-web`의 공용 레코드를 사용하므로 추가 의존성 없음.
- 테스트는 `@WebMvcTest` 혹은 `@SpringBootTest` + `MockMvc` 둘 중 기존 서비스 테스트 컨벤션에 맞추기.

---

# Edge Cases

- 완전히 빈 본문(`""`) → Jackson이 동일 예외를 던짐, 동일하게 400 처리되어야 함.
- 잘못된 JSON 문법(`{"productId":`) → 동일하게 400.
- 필드 타입 불일치(UUID 필드에 숫자, 날짜 필드에 문자열 등) → 동일하게 400.
- 요청 본문에 추가 필드 포함(`UnrecognizedPropertyException`) → 기본 설정상 무시되거나 예외가 던져질 수 있으므로 각 서비스 Jackson 설정 확인(변경은 out of scope, 기존 동작 유지).
- Content-Type 불일치(`HttpMediaTypeNotSupportedException`)는 별개 예외이므로 이 태스크 범위 아님.

---

# Failure Scenarios

- 잘못된 JSON/타입 본문 요청 시 500 반환(현재) → 수정 후 400 반환이어야 함.
- 에러 응답에 스택 트레이스나 원본 JSON 조각이 노출되면 실패.
- 기존 `MethodArgumentNotValidException`(`@Valid` 검증 실패) 경로가 영향받으면 실패 — 두 핸들러는 독립적으로 동작해야 한다.
- 테스트에서 다른 서비스의 기존 통합 테스트가 깨지면 실패.

---

# Test Requirements

- 각 서비스당 최소 1개의 단위/웹 계층 테스트 케이스: 잘못된 본문 요청 → 400 + `VALIDATION_ERROR` 응답 검증.
- user-service는 wishlist 엔드포인트 대상 재현 케이스(`{"productId":"mock-1"}`) 포함.
- 응답 body에 민감 정보가 없음을 확인하는 assertion 권장.

---

# Definition of Done

- [x] 10개 서비스 GlobalExceptionHandler 수정 완료
- [x] 각 서비스 테스트 추가 및 통과 (search-service 제외 — GET-only API라 JSON body 역직렬화 경로 없음. 핸들러만 일관성 차원에서 추가)
- [x] `.claude/skills/backend/exception-handling.md` 업데이트
- [x] 전체 해당 서비스 빌드 및 테스트 통과
- [x] Ready for review

---

# Implementation Summary

## 변경 파일

### 백엔드 GlobalExceptionHandler (10 서비스)
모두 `HttpMessageNotReadableException` → HTTP 400 + `VALIDATION_ERROR` + 고정 메시지 `"Malformed request body"` 핸들러 추가:

- `apps/auth-service/src/main/java/com/example/auth/presentation/advice/GlobalExceptionHandler.java`
- `apps/user-service/src/main/java/com/example/user/presentation/exception/GlobalExceptionHandler.java`
- `apps/product-service/src/main/java/com/example/product/presentation/advice/GlobalExceptionHandler.java`
- `apps/order-service/src/main/java/com/example/order/presentation/GlobalExceptionHandler.java`
- `apps/payment-service/src/main/java/com/example/payment/adapter/in/rest/GlobalExceptionHandler.java`
- `apps/shipping-service/src/main/java/com/example/shipping/interfaces/rest/controller/GlobalExceptionHandler.java`
- `apps/review-service/src/main/java/com/example/review/interfaces/advice/GlobalExceptionHandler.java`
- `apps/promotion-service/src/main/java/com/example/promotion/interfaces/rest/controller/GlobalExceptionHandler.java`
- `apps/notification-service/src/main/java/com/example/notification/adapter/in/rest/GlobalExceptionHandler.java`
- `apps/search-service/src/main/java/com/example/search/adapter/inbound/web/GlobalExceptionHandler.java`

### 테스트 케이스 추가 (9 서비스)
`VALIDATION_ERROR` 응답 + `"Malformed request body"` 메시지 검증:

- `apps/user-service/.../WishlistControllerTest.java` — 2 케이스(mock-1 UUID 파싱 실패, 깨진 JSON). 본 버그의 원래 재현 케이스 포함.
- `apps/review-service/.../GlobalExceptionHandlerTest.java` — 2 케이스
- `apps/auth-service/.../AuthControllerTest.java` — 1 케이스
- `apps/product-service/.../AdminProductControllerTest.java` — 1 케이스
- `apps/order-service/.../OrderControllerTest.java` — 1 케이스
- `apps/payment-service/.../PaymentControllerTest.java` — 1 케이스
- `apps/shipping-service/.../ShippingControllerTest.java` — 1 케이스
- `apps/notification-service/.../TemplateControllerTest.java` — 1 케이스
- `apps/promotion-service/.../PromotionControllerTest.java` — 1 케이스

### 스킬 문서
- `.claude/skills/backend/exception-handling.md` — 예시 코드에 `HttpMessageNotReadableException` 핸들러 추가, "Common Pitfalls" 표에 관련 항목 2개 추가(핸들러 누락, Jackson 메시지 노출 주의).

---

# 부수 작업: @WebMvcTest 인프라 수정

본 태스크의 테스트를 추가하는 과정에서, 여러 서비스의 기존 `@WebMvcTest` 테스트가 **master HEAD에서 이미 깨져 있던** 것을 확인했다. 근본 원인:

1. `@EnableJpaRepositories`가 main `*Application` 클래스에 붙어 있어, `@WebMvcTest` 슬라이스 컨텍스트에서도 `jpaSharedEM_entityManagerFactory` 빈이 등록을 시도하지만 실제 `entityManagerFactory`는 없어 컨텍스트 로딩 실패.
2. order-service는 `TestOrderServiceApplication` 우회 클래스를 이미 갖고 있지만, 몇몇 테스트는 `@ContextConfiguration(classes = ...)`을 걸지 않아 여전히 실패.

본 태스크가 추가한 테스트 케이스가 실행 가능하려면 이 인프라 문제를 같이 풀어야 했다. 다음 경량 수정을 포함:

- `TestProductServiceApplication`, `TestReviewServiceApplication`, `TestShippingServiceApplication`, `TestPromotionServiceApplication` 신규 생성 (기존 `TestOrderServiceApplication` 패턴 복제, @SpringBootApplication만 선언해 `@EnableJpaRepositories`/`@EntityScan` 배제).
- 영향받은 `@WebMvcTest` 클래스들에 `@ContextConfiguration(classes = Test*ServiceApplication.class)` 추가: `OrderControllerTest`, `ProductControllerTest`, `AdminProductControllerTest`, `GlobalExceptionHandlerTest` (review), `ReviewControllerTest`, `ShippingControllerTest`, `PromotionControllerTest`, `CouponControllerTest`.
- 리팩토링 과정에서 누락된 `VariantManagementService` mock도 `ProductControllerTest`와 `AdminProductControllerTest`에 추가(pre-existing `UnsatisfiedDependencyException` 수정).

---

# 확인된 pre-existing 실패(본 태스크 범위 외)

다음 테스트들은 master HEAD부터 깨져 있으며 본 태스크의 변경과 무관하다:

- `apps/order-service/src/test/java/com/example/order/contract/OrderApiContractTest.java` — `@WebMvcTest`가 `@ContextConfiguration` 없이 `TestOrderServiceApplication`과 `OrderServiceApplication`을 모두 찾아 "multiple @SpringBootConfiguration" 에러. 동일한 우회(`@ContextConfiguration` 추가)로 고칠 수 있으나 scope 벗어나므로 제외.
- `apps/user-service/.../UserProfileIntegrationTest` — `AccessDeniedException` 응답 코드가 `FORBIDDEN` vs `ACCESS_DENIED`로 테스트 기대값과 불일치. 핸들러는 `ACCESS_DENIED`를 반환하지만 테스트는 `FORBIDDEN`을 기대. 리팩토링 중 어느 한쪽 변경이 누락된 상태. 별도 fix 태스크로 분리 필요.
- 기타 integration 테스트 일부 (Kafka 연결 필요 등, 로컬 실행 환경 의존성).

본 태스크는 이들을 건드리지 않았고, 본 태스크의 변경이 이들을 추가로 깨뜨리지 않음을 확인했다.
