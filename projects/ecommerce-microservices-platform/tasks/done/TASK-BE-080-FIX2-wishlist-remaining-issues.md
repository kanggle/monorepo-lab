# Task ID

TASK-BE-080-FIX2-wishlist-remaining-issues

# Title

위시리스트 FIX1 미완료 항목 수정 (계약 파일, N+1, RestTemplate Bean, ExceptionHandler, Architecture)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- fix

---

# Goal

TASK-BE-080-FIX 리뷰에서 발견된 미완료 항목 4건을 수정한다.

1. `specs/contracts/http/wishlist-api.md` 계약 파일이 존재하지 않음
2. `RestProductInfoProvider`의 N+1 루프 HTTP 호출이 여전히 존재함
3. `RestTemplate`이 여전히 `new RestTemplate()`으로 직접 생성됨 (Bean 주입 미적용)
4. `GlobalExceptionHandler`의 `MissingServletRequestParameterException` 핸들러가 `INVALID_WISHLIST_REQUEST`를 반환하고 있음 (`VALIDATION_ERROR`로 변경 필요)
5. `specs/services/user-service/architecture.md` Domain Scope에 위시리스트가 포함되지 않음

---

# Scope

## In Scope

- `specs/contracts/http/wishlist-api.md` 신규 작성 (모든 엔드포인트 포함)
- `RestProductInfoProvider` 리팩터링: for 루프 개별 호출 → 배치 또는 병렬(CompletableFuture) 처리
- `RestTemplate` Bean 설정 클래스 추가 (`WebClientConfig` 또는 `RestTemplateConfig`) 및 `RestProductInfoProvider` 생성자에서 Bean 주입으로 전환, 커넥션/읽기 타임아웃 설정
- `GlobalExceptionHandler.handleMissingRequestParameter`: `INVALID_WISHLIST_REQUEST` → `VALIDATION_ERROR` 로 코드 변경
- `specs/services/user-service/architecture.md` Domain Scope에 위시리스트 추가

## Out of Scope

- 위시리스트 기능 자체의 로직 변경
- 프론트엔드 변경

---

# Acceptance Criteria

- [ ] `specs/contracts/http/wishlist-api.md` 파일이 존재하며 모든 위시리스트 API 엔드포인트를 정의한다
- [ ] `RestProductInfoProvider`가 for 루프 개별 HTTP 호출을 제거하고 단일 배치 요청 또는 CompletableFuture 병렬 처리로 상품 정보를 조회한다
- [ ] `RestTemplate`이 스프링 Bean으로 주입되며 커넥션 타임아웃과 읽기 타임아웃이 설정된다 (`new RestTemplate()` 직접 생성 제거)
- [ ] `GlobalExceptionHandler`의 `MissingServletRequestParameterException` 핸들러가 `VALIDATION_ERROR` 코드를 반환한다
- [ ] `specs/services/user-service/architecture.md`의 Domain Scope에 위시리스트가 포함된다
- [ ] 기존 위시리스트 단위 테스트 및 통합 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/error-handling.md`
- `specs/services/user-service/architecture.md`

---

# Related Contracts

- `specs/contracts/http/wishlist-api.md` (신규 작성 대상)
- `specs/contracts/http/product-api.md`

---

# Edge Cases

- product-service가 배치 조회 API를 제공하지 않는 경우 CompletableFuture 병렬 처리 방식 적용
- RestTemplate Bean 설정 시 기존 다른 HTTP 클라이언트에 영향 없도록 위시리스트 전용 Bean(`@Qualifier`)으로 분리 검토
- `MissingServletRequestParameterException` 에러 코드 변경으로 인해 관련 테스트(`WishlistControllerTest` 등) 기대값 수정 필요

---

# Failure Scenarios

- product-service 배치 API 없을 경우 product-service contract 확인 후 CompletableFuture 방식 적용
- RestTemplate Bean 등록 후 기존 테스트 실패 시 테스트 픽스처 수정

---

# Test Requirements

- `RestProductInfoProvider` 단위 테스트: 배치/병렬 호출 검증 (for 루프 사라진 것 확인)
- `WishlistControllerTest`: `VALIDATION_ERROR` 코드 반환 검증
- 기존 `WishlistIntegrationTest` 통과 유지

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `specs/contracts/http/wishlist-api.md` 작성 완료
- [ ] `specs/services/user-service/architecture.md` Domain Scope 업데이트 완료
- [ ] Ready for review
