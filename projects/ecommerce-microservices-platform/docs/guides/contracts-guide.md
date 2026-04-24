# 계약 가이드 (Contracts Guide)

이 문서는 이 프로젝트의 HTTP API 계약 및 이벤트 계약 전체를 한국어로 요약한 참조 가이드입니다.
영어 원본 계약 파일을 읽지 않고도 API·이벤트 전반을 파악할 수 있도록 작성되었습니다.

> 원본 계약 파일 위치: `specs/contracts/http/`, `specs/contracts/events/`, `specs/contracts/schemas/`

> **인터랙티브 탐색 (Swagger UI)**: 서비스 기동 후 각 백엔드의 `/swagger-ui.html`로 자동 생성된 OpenAPI 3.0 문서를 확인할 수 있습니다.
> 적용 서비스: product(8082), auth(8081), user(8084), order(8086), payment(8087).
> 단, **계약의 source of truth는 여전히 `specs/contracts/`** 입니다. Swagger는 보조 탐색 도구입니다.

---

## 1. 계약이란?

**계약(Contract)** 은 서비스 간 통신의 공식 약속입니다.

- **HTTP API 계약**: 어떤 엔드포인트가 존재하고, 어떤 요청/응답 형식을 사용하는지 정의합니다.
- **이벤트 계약**: 어떤 이벤트가 발행되고, 어떤 페이로드를 포함하는지 정의합니다.

### 왜 계약이 중요한가?

| 이유 | 설명 |
|---|---|
| 구현 전 합의 | 계약은 구현 전에 정의되어야 합니다. 코드가 계약을 따릅니다. |
| 서비스 간 독립성 | 소비자는 계약만 보고 구현할 수 있습니다. 내부 구현을 알 필요가 없습니다. |
| 변경 관리 | 계약을 먼저 변경하지 않으면 API나 이벤트를 변경할 수 없습니다. |
| 파손 방지 | 계약에 없는 필드에 의존하면 안 됩니다. 계약이 안전망입니다. |

### 계약 우선 원칙

```
계약 정의 → 구현 → 테스트
```

계약 없이 구현하거나, 계약을 나중에 업데이트하는 것은 금지입니다.

---

## 2. HTTP API 계약 구조

모든 HTTP API 계약은 아래 공통 구조를 따릅니다.

### 공통 구조

| 항목 | 설명 |
|---|---|
| Base Path | 해당 서비스의 기본 URL 경로 (예: `/api/orders`) |
| 인증 방식 | Bearer JWT 토큰 (`Authorization: Bearer <token>`) |
| 사용자 식별 | gateway가 `X-User-Id` 헤더를 주입하여 서비스로 전달 |
| 페이지네이션 | `page`, `size` 쿼리 파라미터 사용 (기본값: page=0, size=20) |
| 에러 형식 | 전 서비스 동일한 JSON 에러 형식 사용 (아래 참조) |

### 에러 응답 형식

```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

- `code`: 기계가 읽는 에러 코드 (`UPPER_SNAKE_CASE`)
- `message`: 사람이 읽는 설명 (민감 정보 포함 금지)
- `timestamp`: 에러 발생 시각 (UTC, ISO 8601)

### 페이지네이션 응답 형식

목록 조회 엔드포인트는 공통 페이지네이션 형식을 사용합니다.

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 100
}
```

---

## 3. API 계약 요약

### auth-service — `/api/auth`

| 메서드 | 경로 | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/auth/signup` | 신규 회원 가입 | 불필요 |
| POST | `/api/auth/login` | 로그인 및 JWT 토큰 발급 | 불필요 |
| POST | `/api/auth/refresh` | 액세스 토큰 재발급 (리프레시 토큰 사용) | 불필요 |
| POST | `/api/auth/logout` | 로그아웃 (리프레시 토큰 즉시 폐기) | 필요 (Bearer JWT) |

**주요 토큰 규칙:**
- 액세스 토큰: JWT (HS256), TTL 1시간
- 리프레시 토큰: 불투명 UUID, Redis 저장, TTL 30일
- 리프레시 시 토큰 로테이션 필수 (구 토큰 즉시 폐기 → 신 토큰 발급)
- 이미 사용된 리프레시 토큰 재사용 시 `REFRESH_TOKEN_REVOKED` 응답

---

### user-service — `/api/users`, `/api/admin/users`

| 메서드 | 경로 | 설명 | 인증 필요 |
|---|---|---|---|
| GET | `/api/users/me` | 내 프로필 조회 | 필요 |
| PATCH | `/api/users/me` | 내 프로필 수정 (nickname, phone, profileImageUrl) | 필요 |
| GET | `/api/users/me/addresses` | 내 배송지 목록 조회 | 필요 |
| POST | `/api/users/me/addresses` | 배송지 추가 (최대 10개) | 필요 |
| PATCH | `/api/users/me/addresses/{addressId}` | 배송지 수정 | 필요 |
| DELETE | `/api/users/me/addresses/{addressId}` | 배송지 삭제 | 필요 |
| POST | `/api/users/me/withdrawal` | 회원 탈퇴 신청 | 필요 |
| GET | `/api/admin/users` | 전체 회원 목록 조회 (관리자) | 필요 (admin role) |
| GET | `/api/admin/users/{userId}` | 특정 회원 프로필 조회 (관리자) | 필요 (admin role) |

---

### product-service — `/api/products`, `/api/admin/products`

| 메서드 | 경로 | 설명 | 인증 필요 |
|---|---|---|---|
| GET | `/api/products` | 상품 목록 조회 (필터, 페이지네이션) | 불필요 |
| GET | `/api/products/{productId}` | 상품 상세 조회 (옵션 포함) | 불필요 |
| POST | `/api/admin/products` | 신규 상품 등록 | 필요 (admin role) |
| PATCH | `/api/admin/products/{productId}` | 상품 정보 수정 | 필요 (admin role) |
| PATCH | `/api/admin/products/{productId}/stock` | 재고 조정 | 필요 (admin role) |

**상품 상태값:**

| 상태 | 설명 |
|---|---|
| `ON_SALE` | 판매 중 |
| `SOLD_OUT` | 품절 |
| `HIDDEN` | 숨김 처리 |

---

### order-service — `/api/orders`

| 메서드 | 경로 | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/orders` | 신규 주문 생성 | 필요 |
| GET | `/api/orders` | 내 주문 목록 조회 (상태 필터, 페이지네이션) | 필요 |
| GET | `/api/orders/{orderId}` | 주문 상세 조회 | 필요 |
| POST | `/api/orders/{orderId}/cancel` | 주문 취소 (PENDING, CONFIRMED 상태만 가능) | 필요 |

**주문 상태값:**

| 상태 | 설명 |
|---|---|
| `PENDING` | 주문 접수, 확인 대기 중 |
| `CONFIRMED` | 주문 확인 완료 |
| `SHIPPED` | 배송 중 |
| `DELIVERED` | 배송 완료 |
| `CANCELLED` | 주문 취소됨 |

---

### payment-service — `/api/payments`

| 메서드 | 경로 | 설명 | 인증 필요 |
|---|---|---|---|
| GET | `/api/payments/orders/{orderId}` | 주문에 대한 결제 정보 조회 | 필요 (`X-User-Id` 헤더) |

**결제 상태값:**

| 상태 | 설명 |
|---|---|
| `PENDING` | 결제 처리 중 |
| `COMPLETED` | 결제 완료 |
| `FAILED` | 결제 실패 |
| `REFUNDED` | 환불 완료 |

---

### search-service — `/api/search`

| 메서드 | 경로 | 설명 | 인증 필요 |
|---|---|---|---|
| GET | `/api/search/products` | 키워드 및 필터로 상품 검색 | 불필요 |

**주요 쿼리 파라미터:** `q` (필수), `categoryId`, `minPrice`, `maxPrice`, `status`, `sort`, `page`, `size` (최대 100)

> 검색 결과는 product-service와 최종 일관성(eventual consistency)을 가집니다. 최근 변경 사항이 즉시 반영되지 않을 수 있습니다.

---

## 4. 이벤트 계약 구조

### 이벤트 엔벨로프 형식

모든 이벤트는 다음 공통 JSON 구조를 사용합니다.

```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601 UTC)",
  "source": "string",
  "payload": {}
}
```

| 필드 | 설명 |
|---|---|
| `event_id` | 중복 제거용 고유 식별자 (UUID) |
| `event_type` | 이벤트 유형 이름 (예: `OrderPlaced`, `PaymentCompleted`) |
| `occurred_at` | 이벤트 발생 시각 (발행 시각이 아님) |
| `source` | 이벤트를 발행한 서비스명 (예: `auth-service`, `order-service`) |
| `payload` | 이벤트별 데이터 |

### 이벤트 명명 규칙

| 규칙 | 설명 |
|---|---|
| 이벤트 이름 | PascalCase, 과거형 동사 사용 (예: `OrderPlaced`, `UserWithdrawn`) |
| 토픽 이름 | `{service}.{entity}.{event}` 형식의 kebab-case (예: `order.order.placed`) |
| DLQ 토픽 | `{원본-토픽}.dlq` (예: `order.order.placed.dlq`) |

### 메시지 브로커

Apache Kafka 사용

---

## 5. 이벤트 계약 요약

### auth-service 이벤트

| 이벤트명 | 발행 조건 | 소비자 | 핵심 페이로드 필드 |
|---|---|---|---|
| `UserSignedUp` | 신규 회원 가입 성공 | user-service, notification-service(미래) | `userId`, `email`, `name` |
| `UserLoggedIn` | 로그인 성공 | audit-service(미래), analytics(미래) | `userId`, `email`, `ipAddress`, `userAgent` |
| `UserLoggedOut` | 명시적 로그아웃 | audit-service(미래), analytics(미래) | `userId`, `sessionId` |
| `TokenRefreshed` | 리프레시 토큰 로테이션 성공 | audit-service(미래) | `userId`, `sessionId` |
| `LoginFailed` | 로그인 실패 (잘못된 자격 증명) | audit-service(미래), security-monitoring(미래) | `email`, `ipAddress`, `reason` |
| `SessionLimitExceeded` | 동시 세션 한도 초과로 구 세션 만료 | audit-service(미래) | `userId`, `evictedSessionId`, `newSessionId` |

---

### user-service 이벤트

| 이벤트명 | 발행 조건 | 소비자 | 핵심 페이로드 필드 |
|---|---|---|---|
| `UserProfileUpdated` | 사용자가 프로필을 수정함 | admin-dashboard(미래), notification-service(미래) | `userId`, `nickname`, `phone`, `profileImageUrl`, `updatedAt` |
| `UserWithdrawn` | 회원 탈퇴 처리 완료 | order-service, auth-service | `userId`, `withdrawnAt` |

---

### product-service 이벤트

| 이벤트명 | 발행 조건 | 소비자 | 핵심 페이로드 필드 |
|---|---|---|---|
| `ProductCreated` | 신규 상품 등록 성공 | search-service | `productId`, `name`, `price`, `status`, `categoryId`, `variants` |
| `ProductUpdated` | 상품 정보 수정 (이름/설명/가격/상태) | search-service | `productId`, `name`, `price`, `status`, `categoryId` |
| `ProductDeleted` | 상품 영구 삭제 | search-service | `productId` |
| `StockChanged` | 재고 수량 변경 (증가 또는 감소) | search-service, order-service | `productId`, `variantId`, `previousStock`, `currentStock`, `delta`, `reason`, `orderId` |

`StockChanged` reason 값: `RESTOCK`, `ORDER_RESERVED`, `ORDER_CANCELLED`, `ADMIN_ADJUSTMENT`

---

### order-service 이벤트

| 이벤트명 | 발행 조건 | 소비자 | 핵심 페이로드 필드 |
|---|---|---|---|
| `OrderPlaced` | 신규 주문 생성 성공 | payment-service | `orderId`, `userId`, `totalPrice`, `items`, `shippingAddress` |
| `OrderCancelled` | 주문 취소 처리 완료 | payment-service | `orderId`, `userId`, `cancelledAt` |

---

### payment-service 이벤트

| 이벤트명 | 발행 조건 | 소비자 | 핵심 페이로드 필드 |
|---|---|---|---|
| `PaymentCompleted` | 결제 성공 처리 완료 | order-service | `paymentId`, `orderId`, `userId`, `amount`, `paidAt` |
| `PaymentFailed` | 결제 처리 실패 | order-service | `paymentId`, `orderId`, `userId`, `reason`, `failedAt` |
| `PaymentRefunded` | 환불 처리 완료 | order-service | `paymentId`, `orderId`, `userId`, `amount`, `refundedAt` |

---

## 6. 공유 스키마

`specs/contracts/schemas/` 디렉토리는 HTTP 계약과 이벤트 계약에서 공통으로 참조하는 재사용 가능한 스키마를 관리합니다.

### 허용된 스키마 종류

| 종류 | 설명 |
|---|---|
| 공통 에러 스키마 | `{ code, message, timestamp }` 형식 |
| 페이지네이션 스키마 | `{ content, page, size, totalElements }` 형식 |
| 이벤트 엔벨로프 스키마 | `{ event_id, event_type, occurred_at, source, payload }` 형식 |
| 재사용 요청/응답 단편 | 여러 계약에서 공통으로 사용되는 필드 묶음 |

### 허용되지 않는 스키마 종류

| 종류 | 이유 |
|---|---|
| 서비스 내부 DTO | 외부에 공개되지 않는 내부 전송 객체 |
| 미공개 내부 페이로드 | 계약에서 참조되지 않는 데이터 |
| DB 지향 모델 | 데이터베이스 엔티티 구조 |

> 이 디렉토리의 모든 스키마는 반드시 `specs/contracts/http/` 또는 `specs/contracts/events/` 의 공식 계약에서 하나 이상 참조되어야 합니다.

---

## 7. 공통 규칙

### 페이지네이션 형식

목록 조회 응답은 반드시 아래 형식을 사용합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `content` | array | 현재 페이지의 항목 목록 |
| `page` | integer | 현재 페이지 번호 (0부터 시작) |
| `size` | integer | 페이지당 항목 수 |
| `totalElements` | integer | 전체 항목 수 |

### 에러 응답 형식

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | 기계 판독 에러 코드 (`UPPER_SNAKE_CASE`) |
| `message` | string | 사람 판독 설명 (민감 정보 포함 금지, 스택트레이스 금지) |
| `timestamp` | string | UTC 기준 에러 발생 시각 (ISO 8601) |

### 주요 HTTP 상태 코드

| 상태 | 설명 |
|---|---|
| 400 | 검증 실패 (필드 누락 또는 유효하지 않은 값) |
| 401 | 인증 실패 (토큰 없음, 유효하지 않음) |
| 403 | 권한 없음 (소유자 불일치, 관리자 권한 필요) |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복 이메일, 낙관적 잠금 충돌) |
| 422 | 비즈니스 규칙 위반 (취소 불가 주문, 탈퇴 불가 상태 등) |
| 429 | 요청 한도 초과 |

### 명명 규칙 요약

| 대상 | 규칙 | 예시 |
|---|---|---|
| JSON 필드 (요청/응답 바디) | camelCase | `userId`, `createdAt`, `totalPrice` |
| 이벤트 엔벨로프 필드 | snake_case | `event_id`, `event_type`, `occurred_at` |
| 이벤트 이름 | PascalCase + 과거형 | `OrderPlaced`, `PaymentCompleted` |
| 에러 코드 | UPPER_SNAKE_CASE | `ORDER_NOT_FOUND`, `VALIDATION_ERROR` |
| Kafka 토픽 | kebab-case, `{service}.{entity}.{event}` | `order.order.placed` |
| API 경로 세그먼트 | kebab-case, 복수 명사 | `/api/orders`, `/api/auth/refresh` |

### UUID 사용 규칙

- 모든 엔티티 식별자(ID)는 UUID(v4) 형식을 사용합니다.
- 이벤트의 `event_id`도 UUID입니다. 중복 이벤트 처리(멱등성)에 사용합니다.
- UUID는 문자열 형식으로 전달합니다 (예: `"3fa85f64-5717-4562-b3fc-2c963f66afa6"`).

### 이벤트 소비자 공통 규칙

| 규칙 | 설명 |
|---|---|
| 멱등성 | 같은 이벤트를 두 번 처리해도 동일한 결과가 나와야 합니다. `event_id`로 중복 감지 |
| DLQ 처리 | 처리 실패 이벤트는 파이프라인을 중단하지 않고 DLQ로 이동 |
| 재시도 정책 | 최대 3회, 지수 백오프 (1초 → 2초 → 4초, 최대 30초) |
| 계약 외 필드 의존 금지 | 계약에 정의되지 않은 필드에 의존하지 않음 |
| 보상 HTTP 호출 금지 | 이벤트 데이터 부족 시 생산자 HTTP API를 호출하지 않음 |

### 이벤트 생산자 공통 규칙

| 규칙 | 설명 |
|---|---|
| 트랜잭션 커밋 후 발행 | 이벤트는 DB 트랜잭션 커밋 이후에 발행합니다 (Transactional Outbox 패턴 권장) |
| 롤백 가능 구간 내 발행 금지 | 롤백될 수 있는 트랜잭션 경계 내에서 이벤트를 발행하지 않습니다 |
| 계약 변경 선행 | 이벤트 페이로드 변경은 계약 업데이트 후 구현합니다 |
