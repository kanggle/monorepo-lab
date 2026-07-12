---
name: ecommerce-microservices-platform
domain: ecommerce
traits: [transactional, content-heavy, read-heavy, integration-heavy, multi-tenant]
service_types: [rest-api, event-consumer, batch-job, frontend-app]
compliance: []
data_sensitivity: pii
scale_tier: startup
taxonomy_version: 0.1
---

# ecommerce-microservices-platform

## Purpose

모놀리식에서 벗어난 도메인 기반 마이크로서비스 플랫폼으로, 상품 카탈로그·주문·결제·프로모션·배송·리뷰·알림·검색을 포함하는 end-to-end 이커머스 시스템의 기준 구현. 동시에 **분류(taxonomy) 기반 규칙 시스템**의 첫 dogfood 프로젝트이기도 함 — 여기서 검증된 구조가 후속 프로젝트의 템플릿이 된다.

## Domain Rationale

`ecommerce`를 선택한 이유:

- 핵심 bounded context가 Product / Order / Cart / Payment / Promotion / Shipping / Review / Notification / Wishlist로, 전형적인 B2C 이커머스 구조와 일치
- 단일 판매자 기반(marketplace 아님). 셀러 온보딩, 정산, 수수료 같은 marketplace 고유 개념은 현재 스코프 밖
- fintech의 거래 엄격성이 필요하지만 도메인 자체는 금융이 아니라 **상거래**가 중심이므로 `fintech`가 아닌 `ecommerce`로 분류

## Trait Rationale

- **transactional**: Order/Payment/Inventory가 강한 일관성 요구. Saga + idempotency + distributed transaction 패턴이 필수. [apps/order-service/](apps/order-service/), [apps/payment-service/](apps/payment-service/), [specs/services/order-service/architecture.md](specs/services/order-service/architecture.md)
- **content-heavy**: Product 카탈로그, Review, 배너/프로모션 콘텐츠가 읽기 트래픽의 핵심 자산. CMS 패턴, 미디어 처리, 검색 인덱싱이 평균 이상으로 중요. [apps/product-service/](apps/product-service/), [apps/review-service/](apps/review-service/), [apps/search-service/](apps/search-service/)
- **read-heavy**: 쇼핑/검색/상품 상세 조회가 쓰기보다 수십 배 많음. 캐시·CDN·읽기 복제·페이지네이션 최적화가 설계 제약으로 작용
- **integration-heavy**: PG(결제), 택배 트래킹, 알림 채널(SMS/Email/Push), 소셜 로그인, 검색 엔진 등 외부 시스템 연동 다수. Circuit breaker, retry, DLQ, idempotent side-effect 패턴이 반복 적용. [apps/notification-service/](apps/notification-service/), [apps/shipping-service/](apps/shipping-service/)
- **multi-tenant**: ecommerce 를 멀티벤더 마켓플레이스 SaaS 로 승격하는 **바깥(tenant) 축** ([ADR-MONO-030](../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) Step 2). row-level `tenant_id` + 3-layer 격리(gateway entitlement-trust → `X-Tenant-Id` 컨텍스트 → persistence `WHERE tenant_id`) + M3 404 + M5 이벤트 봉투 전파. **product-service + order-service(Step 2 M1) + user-service(V4) + promotion-service(V6) + shipping-service(V7) + notification-service(V5) 에 적용 완료**; 나머지 6개 서비스는 named migration backlog (아래 Out of Scope "in-migration"). default-tenant 시드로 net-zero(D8). SoT = [specs/features/multi-tenancy-and-marketplace.md](specs/features/multi-tenancy-and-marketplace.md) §2, [rules/traits/multi-tenant.md](../../rules/traits/multi-tenant.md). [apps/product-service/](apps/product-service/), [apps/order-service/](apps/order-service/)

## Service Map

12 backend (`settings.gradle` 의 `projects:ecommerce-microservices-platform:apps:*`) + 1 frontend. `Service Type` 은 각 서비스의 `specs/services/<service>/architecture.md` 선언을 그대로 옮긴 것이다(그쪽이 권위 — 미선언 시 HARDSTOP-10).

| Service | Service Type | 역할 |
|---|---|---|
| `gateway-service` | `rest-api` | 엣지 라우팅, IAM RS256 JWT 검증 (OAuth2 Resource Server), 신원 헤더 strip→enrich, rate limit, CORS, 통일 에러 envelope. `libs/java-gateway` 소비 (ADR-MONO-048) |
| `user-service` | `rest-api + event-consumer` | 회원 프로필·주소·위시리스트. 자체 인증은 없음 (IAM OIDC 로 대체) |
| `product-service` | `rest-api` | 상품·카탈로그·재고. wms `inventory.{received,adjusted}` 구독으로 창고 재고 반영 (ADR-MONO-022 §D4) |
| `search-service` | `rest-api + event-consumer` | Elasticsearch 색인·검색 |
| `order-service` | `rest-api + event-consumer` | 주문 saga. 결제·배송·프로모션 오케스트레이션 |
| `payment-service` | `rest-api + event-consumer` | PG 연동, 결제·환불 |
| `promotion-service` | `rest-api + event-consumer` | 쿠폰·프로모션·할인 정책 |
| `settlement-service` | `event-consumer + rest-api` | 마켓플레이스 셀러 정산/수수료. terminal consumer (no outbox) — ADR-MONO-030 Step 4b |
| `shipping-service` | `rest-api + event-consumer` | 배송·택배 트래킹. wms 풀필먼트 루프의 ecommerce 측 ACL (ADR-MONO-022) |
| `notification-service` | `event-consumer` | SMS/Email/Push 발송 |
| `review-service` | `rest-api` | 상품 리뷰·평점 |
| `batch-worker` | `batch-job` | 비동기 배치 |
| `web-store` | `frontend-app` | Next.js 15 스토어프런트. 운영자 UI 는 platform-console 로 흡수됨 (ADR-MONO-031 Phase 6) |
| ~~`auth-service`~~ | ~~`rest-api`~~ | **RETIRED** (TASK-BE-132) — `settings.gradle` include 제외, IAM OIDC 로 대체. 소스는 이력 보존 목적으로 `apps/auth-service/` 에 잔존 |

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **marketplace — 슬라이스 적용(ADR-MONO-030 Step 3/4)**: 더 이상 순수 단일 판매자 구조 아님. **안쪽 `seller_id` 축**(product/order 라인 귀속, Step 3 / TASK-BE-363) + **셀러 정산/수수료**(신규 `settlement-service` — order/payment 이벤트 소비 → order-line 단위 플랫폼 수수료 vs 셀러 순수익 accrual + 환불 reversal, ADR-MONO-030 Step 4 facet b / TASK-BE-365) + **셀러 온보딩/실 IAM provisioning**(`Seller` 애그리거트가 실 IAM seller-operator 계정 + born-unified identity 발급, fail-soft, suspend/close 시 계정 비활성화, ADR-MONO-030 Step 4 facet f / ADR-MONO-042 / TASK-BE-402)가 적용됨. trait 으로 선언하진 않음(taxonomy 미보유 태그) — net-zero degrade(플랫폼 기본율 0 = 단일 스토어 경제 동치, D8; authz net-zero D6)로 기존 동작 보존. **여전히 out**: 기간마감/payout/셀러 뱅킹·지급, 부분/비례 환불 clawback, multi-currency, 티어/카테고리 수수료, IAM→셀러 역방향 `account.status.changed` 투영 — ADR-MONO-030 §3.4 Step 4 나머지 facet.
- **regulated**: PCI-DSS 카드 정보 직접 처리 안 함 (PG 위임). GDPR/PIPA 등 컴플라이언스 프레임워크는 아직 미적용
- **audit-heavy**: 감사 로그 의무화된 도메인 아님. 변경 이력 보관은 기술적 옵션이지 규제 요구는 아님
- **multi-tenant — 슬라이스 적용(ADR-MONO-030 Step 2/4)**: 더 이상 단일 테넌트 아님. **product-service + order-service(Step 2 M1) + user-service(V4, TASK-BE-367) + promotion-service(V6, TASK-BE-368) + shipping-service(V7, TASK-BE-369) + notification-service(V5, TASK-BE-370) 는 row-level `tenant_id` 격리 적용 완료**(trait 활성). 나머지 6개 서비스(cart/payment/review/search/auth/web-store)는 **in-migration** — named backlog (ADR-MONO-030 §3.4 Step 4). 안쪽 `seller_id` 마켓플레이스 축도 미적용(Step 3 / TASK-BE-358). 슬라이스 밖 서비스는 멀티테넌트 가드 부재 — 신규 작업 시 본 trait 의 M1-M7 을 적용할 것.
- **admin-dashboard(frontend-app) 폐기**: ADR-MONO-031 Phase 6 / TASK-MONO-259 — 독립 admin-dashboard 앱 제거(301 파일/16473줄 삭제), platform-console 일원화 완료.
- **real-time**: 초단위 reactive 시스템 아님. 실시간성이 중요해지면 trait 재분류 필요
- **batch-heavy**: 배치가 존재하지만([apps/batch-worker/](apps/batch-worker/)) 시스템 중심이 아님
- **data-intensive**: 아직 대규모 분석 플랫폼 수준 아님

이 경계가 바뀌면 [PROJECT.md](PROJECT.md)의 traits를 수정하고 해당 `rules/traits/<trait>.md`를 로딩 범위에 포함시킬 것.

## Overrides

현재 명시적 override 없음. 공통/도메인/특성 규칙을 모두 기본값대로 따른다.

예외가 필요한 경우 이 섹션에 다음 형식으로 기록:

```
- **rule**: specs/rules/traits/transactional.md#idempotency-key-required
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
```
