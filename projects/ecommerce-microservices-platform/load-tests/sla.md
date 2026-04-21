# 성능 기준 (SLA) 정의

TASK-INT-018에 의해 정의된 성능/부하 테스트 기준.

---

## 공통 기준

| 지표 | 기준 | 비고 |
|---|---|---|
| P95 응답시간 | 500ms 이하 | 모든 API 기본 기준 |
| P99 응답시간 | 1,000ms 이하 | 모든 API 기본 기준 |
| 에러율 | 1% 이하 | HTTP 4xx/5xx 비율 |
| 최소 RPS | 10 req/s 이상 | 전체 기본 기준 (시나리오별 상이) |

---

## API별 성능 기준

### 인증 (auth-service)

| 엔드포인트 | P95 | P99 | 최소 RPS | 비고 |
|---|---|---|---|---|
| POST /api/auth/login | 400ms | 800ms | 20 | Redis 조회 포함 |
| POST /api/auth/refresh | 300ms | 600ms | 20 | Redis 조회 + 갱신 |
| POST /api/auth/signup | 500ms | 1,000ms | 20 | DB 쓰기 포함 |
| POST /api/auth/logout | 500ms | 1,000ms | 20 | Redis 삭제 |

### 상품 조회/검색 (product-service, search-service)

| 엔드포인트 | P95 | P99 | 최소 RPS | 비고 |
|---|---|---|---|---|
| GET /api/products | 400ms | 800ms | 50 | 페이지네이션 목록 |
| GET /api/products/{id} | 300ms | 600ms | 50 | 단건 조회 |
| GET /api/search/products | 500ms | 1,000ms | 50 | Elasticsearch 검색 |

### 주문 (order-service)

| 엔드포인트 | P95 | P99 | 최소 RPS | 비고 |
|---|---|---|---|---|
| POST /api/orders | 800ms | 1,500ms | 10 | 재고 확인 + DB 쓰기 + 이벤트 발행 |
| GET /api/orders | 400ms | 800ms | 10 | 페이지네이션 목록 |
| GET /api/orders/{id} | 400ms | 800ms | 10 | 단건 조회 |

### 결제 (payment-service)

| 엔드포인트 | P95 | P99 | 최소 RPS | 비고 |
|---|---|---|---|---|
| GET /api/payments/orders/{orderId} | 500ms | 1,000ms | 10 | 결제 정보 조회 |

---

## 부하 수준 정의

| 단계 | 동시 사용자 수 (VU) | 목적 |
|---|---|---|
| Smoke | 3~10 | 기본 동작 확인 |
| Load | 20~80 | 예상 트래픽 수준 검증 |
| Stress | 50~150 | 한계 부하 파악 |

---

## E2E 흐름 기준

| 지표 | 기준 |
|---|---|
| 전체 흐름 P95 | 5,000ms 이하 |
| 전체 흐름 P99 | 8,000ms 이하 |
| 에러율 | 5% 이하 |
| 최소 RPS | 5 req/s 이상 |

E2E 흐름: 회원가입 → 로그인 → 검색 → 상품조회 → 주문 → 결제조회

---

## 측정 환경

- docker-compose 기반 로컬 환경
- 서비스별 리소스 제한 적용 (docker-compose.yml 참조)
- k6 부하 테스트 도구 사용

---

## 주의사항

- 로컬 docker-compose 환경은 프로덕션 환경과 리소스가 다르므로 절대적 수치보다는 상대적 추이 파악에 활용
- 서비스 간 의존성으로 인한 병목 구간 식별이 주요 목적
- DB 커넥션 풀, Kafka 메시지 처리 지연 등 인프라 병목 확인
