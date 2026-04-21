---
name: ecommerce-microservices-platform
domain: ecommerce
traits: [transactional, content-heavy, read-heavy, integration-heavy]
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

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **marketplace**: 단일 판매자 구조 — 셀러 온보딩/정산/수수료 없음
- **regulated**: PCI-DSS 카드 정보 직접 처리 안 함 (PG 위임). GDPR/PIPA 등 컴플라이언스 프레임워크는 아직 미적용
- **audit-heavy**: 감사 로그 의무화된 도메인 아님. 변경 이력 보관은 기술적 옵션이지 규제 요구는 아님
- **multi-tenant**: 단일 테넌트
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
