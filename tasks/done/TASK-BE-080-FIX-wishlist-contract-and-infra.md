# Task ID

TASK-BE-080-FIX-wishlist-contract-and-infra

# Title

위시리스트 계약 누락 및 인프라 결함 수정

# Status

<<<<<<<< HEAD:tasks/done/TASK-BE-080-FIX-wishlist-contract-and-infra.md
done
========
review
>>>>>>>> worktree-agent-a250ba6d:tasks/review/TASK-BE-080-FIX-wishlist-contract-and-infra.md

# Owner

backend

# Task Tags

- code
- api
- fix

---

# Goal

TASK-BE-080 리뷰에서 발견된 Critical 이슈 4건을 수정한다.

1. `specs/contracts/http/wishlist-api.md` 계약 파일 작성
2. 위시리스트 전용 에러 코드 `specs/platform/error-handling.md` 등록
3. `RestProductInfoProvider` N+1 HTTP 호출 패턴 해소 (배치 조회로 전환)
4. `RestTemplate` 직접 생성 제거 및 Bean 주입으로 교체

---

# Scope

## In Scope

- `specs/contracts/http/wishlist-api.md` 신규 작성 (모든 엔드포인트 포함)
- `specs/platform/error-handling.md`에 `ALREADY_IN_WISHLIST`, `WISHLIST_ITEM_NOT_FOUND`, `INVALID_WISHLIST_REQUEST` 에러 코드 추가
- `RestProductInfoProvider` 리팩터링: 개별 루프 호출 → 배치/병렬 호출 또는 product-service 배치 조회 API 활용
- `RestProductInfoProvider`의 `RestTemplate` 직접 생성 → 스프링 Bean 주입으로 교체 (커넥션 풀, 타임아웃 설정 포함)
- `GlobalExceptionHandler`의 `MissingServletRequestParameterException` 핸들러 에러 코드 교정 (`INVALID_WISHLIST_REQUEST` → `VALIDATION_ERROR`)
- `specs/services/user-service/architecture.md`의 Domain Scope에 위시리스트 추가

## Out of Scope

- 위시리스트 기능 자체의 로직 변경
- 프론트엔드 변경
- 위시리스트 상한(upper limit) 구현 (별도 결정 필요)

---

# Acceptance Criteria

- [ ] `specs/contracts/http/wishlist-api.md` 파일이 존재하며 모든 위시리스트 API 엔드포인트를 정의한다
- [ ] `specs/platform/error-handling.md`에 `ALREADY_IN_WISHLIST` (409), `WISHLIST_ITEM_NOT_FOUND` (404), `INVALID_WISHLIST_REQUEST` (400) 에러 코드가 등록된다
- [ ] `RestProductInfoProvider`가 N+1 루프 HTTP 호출을 제거하고 단일 배치 요청 또는 병렬 처리로 상품 정보를 조회한다
- [ ] `RestTemplate`이 스프링 Bean으로 주입되며 커넥션 타임아웃과 읽기 타임아웃이 설정된다
- [ ] `GlobalExceptionHandler`의 `MissingServletRequestParameterException` 핸들러가 `VALIDATION_ERROR` 코드를 반환하거나 파라미터 명에 따른 적절한 코드를 반환한다
- [ ] `specs/services/user-service/architecture.md`의 Domain Scope에 위시리스트가 포함된다
- [ ] 기존 위시리스트 단위 테스트 및 통합 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/architecture-decision-rule.md`
- `specs/platform/error-handling.md`
- `specs/platform/service-boundaries.md`
- `specs/services/user-service/architecture.md`

---

# Related Contracts

- `specs/contracts/http/wishlist-api.md` (신규 작성 대상)
- `specs/contracts/http/product-api.md`

---

# Edge Cases

- product-service가 배치 조회 API를 제공하지 않는 경우 병렬 처리(CompletableFuture) 방식 적용
- RestTemplate Bean 설정 변경 시 기존 다른 HTTP 클라이언트에 영향 없도록 위시리스트 전용 Bean으로 분리 검토
- 에러 코드 변경으로 인해 WishlistControllerTest의 INVALID_WISHLIST_REQUEST 기대값 수정 필요 여부 확인

---

# Failure Scenarios

- product-service 배치 API 없을 경우 product-service contract 확인 후 적절한 대안 선택
- RestTemplate Bean 등록 후 기존 테스트 실패 시 테스트 픽스처 수정

---

# Test Requirements

- `RestProductInfoProvider` 단위 테스트: 배치 호출 검증
- `WishlistControllerTest`: 에러 코드 변경분 반영
- 기존 `WishlistIntegrationTest` 통과 유지

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `specs/contracts/http/wishlist-api.md` 작성 완료
- [ ] `specs/platform/error-handling.md` 에러 코드 등록 완료
- [ ] `specs/services/user-service/architecture.md` Domain Scope 업데이트 완료
- [ ] Ready for review
