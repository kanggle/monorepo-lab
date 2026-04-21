# Task ID

TASK-BE-080-FIX2

# Title

위시리스트 구현 리뷰 지적 사항 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- fix

---

# Goal

TASK-BE-080 코드 리뷰에서 발견된 Critical 및 Warning 이슈를 수정한다.

---

# Scope

## In Scope

- `GlobalExceptionHandler`의 `MissingServletRequestParameterException` 핸들러가 전역에서 `INVALID_WISHLIST_REQUEST`를 반환하는 문제 수정
- `RestProductInfoProvider`에서 N+1 HTTP 요청 문제 개선 (단건 루프 → 배치 또는 병렬 호출)
- `product-service.base-url` 설정 키가 `application.yml`에 누락된 문제 수정

## Out of Scope

- 위시리스트 기능 신규 추가
- 다른 서비스 변경

---

# Acceptance Criteria

- [ ] `MissingServletRequestParameterException` 핸들러가 wishlist 관련 엔드포인트(`/api/wishlists/**`)에서만 `INVALID_WISHLIST_REQUEST`를 반환하거나, 범용 코드(`VALIDATION_ERROR`)로 변경되어 컨트랙트와 일치한다
- [ ] `RestProductInfoProvider.getProductInfos()`가 단건 루프 대신 병렬 또는 배치 방식으로 동작한다
- [ ] `application.yml`에 `product-service.base-url` 설정 키가 환경변수(`${PRODUCT_SERVICE_BASE_URL:...}`)로 추가된다

---

# Review Findings

## Critical

없음

## Warning

### [GlobalExceptionHandler.java:83-86] MissingServletRequestParameterException 핸들러가 전역으로 INVALID_WISHLIST_REQUEST 반환

`GlobalExceptionHandler`는 모든 컨트롤러에 적용되는 글로벌 핸들러다.
`MissingServletRequestParameterException` 핸들러가 `INVALID_WISHLIST_REQUEST`를 하드코딩으로 반환하면,
`/api/users`, `/api/addresses` 등 다른 엔드포인트에서 파라미터 누락 시에도 동일한 코드가 반환되어 컨트랙트를 위반한다.

수정 방안:
- 핸들러를 `VALIDATION_ERROR`로 변경하거나
- 요청 URI에 `/api/wishlists`가 포함될 때만 `INVALID_WISHLIST_REQUEST`를 반환하도록 분기

### [RestProductInfoProvider.java:28-54] 상품 정보 조회 시 N+1 HTTP 요청

`getProductInfos()`가 `for (UUID productId : productIds)` 루프로 product-service에 단건씩 HTTP 요청을 보낸다.
위시리스트에 상품이 N개 있으면 N번의 동기 HTTP 호출이 순차적으로 발생한다.

수정 방안:
- `CompletableFuture` 또는 `parallelStream`을 사용해 병렬 호출로 개선
- 또는 product-service에 배치 조회 API가 있다면 단일 호출로 처리

### [application.yml] product-service.base-url 설정 키 누락

`RestProductInfoProvider`에서 `@Value("${product-service.base-url:http://localhost:8082}")`로 참조하지만
`application.yml`에 해당 키가 없어 기본값에만 의존한다.
운영 환경에서 환경변수로 주입할 수 없다.

수정 방안:
```yaml
product-service:
  base-url: ${PRODUCT_SERVICE_BASE_URL:http://product-service:8082}
```

## Suggestion

### [RestProductInfoProvider.java] RestTemplate 직접 생성

`new RestTemplate()`을 생성자 내부에서 직접 생성하고 있다. Spring Bean으로 등록된 `RestTemplate`을 주입받거나,
연결 타임아웃, 읽기 타임아웃 설정을 추가하는 것이 권장된다.

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/contracts/http/wishlist-api.md`
- `specs/services/user-service/architecture.md`

# Related Contracts

- `specs/contracts/http/wishlist-api.md`

---

# Edge Cases

- 위시리스트 외 다른 엔드포인트에서도 파라미터 누락 시 올바른 에러 코드가 반환되어야 한다

---

# Failure Scenarios

- product-service 타임아웃 설정 없는 경우 위시리스트 조회가 무기한 블록될 수 있다

---

# Test Requirements

- `GlobalExceptionHandler`의 파라미터 누락 에러 코드 반환 동작 테스트 (기존 테스트 수정 필요 없으면 skip)
- `RestProductInfoProvider` 병렬 호출 동작 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added or updated
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
