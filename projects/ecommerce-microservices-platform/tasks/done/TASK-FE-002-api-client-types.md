# TASK-FE-002: @repo/api-client 및 @repo/types 구현 — Gateway API 연동 기반

## Goal
백엔드 HTTP 계약(`specs/contracts/http/`)을 기반으로 공유 타입 패키지(`@repo/types`)와 API 클라이언트 패키지(`@repo/api-client`)를 구현한다.
web-store와 admin-dashboard가 동일한 타입과 API 호출 로직을 공유할 수 있도록 한다.

## Scope
- `packages/types/`: 모든 HTTP 계약의 요청/응답 타입을 TypeScript 인터페이스로 정의
  - auth: SignupRequest, LoginRequest, TokenResponse 등
  - product: ProductResponse, ProductListResponse, CreateProductRequest 등
  - order: OrderResponse, PlaceOrderRequest 등
  - search: SearchRequest, SearchResponse, FacetResult 등
  - payment: PaymentResponse 등
  - 공통: ApiErrorResponse, PaginationParams, PaginatedResponse
- `packages/api-client/`: Axios 또는 fetch 기반 API 클라이언트
  - 기본 설정: baseURL(gateway), 인터셉터(JWT 토큰 주입, 에러 핸들링)
  - 서비스별 API 함수: authApi, productApi, orderApi, searchApi, paymentApi
  - 타입 안전한 요청/응답 처리

## Acceptance Criteria
- 모든 HTTP 계약에 정의된 요청/응답이 TypeScript 타입으로 존재한다
- API 클라이언트가 gateway-service의 각 엔드포인트를 호출할 수 있다
- JWT 토큰이 Authorization 헤더에 자동 주입된다
- API 에러 응답이 공통 타입으로 파싱된다
- `@repo/types`와 `@repo/api-client` 패키지가 빌드되고 앱에서 import 가능하다

## Related Specs
- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/order-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/http/payment-api.md`
- `specs/platform/error-handling.md`
- `specs/platform/api-gateway-policy.md`

## Related Contracts
- `specs/contracts/http/` 전체

## Edge Cases
- 백엔드 API 계약이 변경될 경우 타입도 동기화해야 한다
- 인증되지 않은 요청(로그인, 회원가입)은 토큰 주입을 건너뛰어야 한다
- 401 응답 시 토큰 갱신 후 원래 요청을 재시도해야 한다

## Failure Scenarios
- gateway-service 미기동 시 적절한 네트워크 에러 메시지를 반환해야 한다
- 토큰 갱신 실패 시 로그인 페이지로 리다이렉트 처리
