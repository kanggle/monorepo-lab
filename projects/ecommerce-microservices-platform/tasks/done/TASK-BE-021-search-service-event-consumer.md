# Task ID

TASK-BE-021

# Title

search-service 상품 이벤트 소비 및 인덱스 동기화 — ProductCreated, ProductUpdated, ProductDeleted, StockChanged

# Status

ready

# Owner

backend

# Task Tags

- code
- event

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

product-service가 발행하는 도메인 이벤트를 소비하여 Elasticsearch 검색 인덱스를 동기화한다. ProductCreated 시 인덱스에 추가, ProductUpdated 시 업데이트, ProductDeleted 시 삭제, StockChanged 시 재고 필드를 갱신한다.

이 태스크 완료 후: product-service 이벤트가 발생하면 검색 인덱스가 자동으로 최신 상태로 유지된다.

---

# Scope

## In Scope

- Spring ApplicationEvent 기반 인바운드 이벤트 컨슈머 어댑터 구현
  - `ProductCreatedConsumer` — ProductCreated 이벤트 소비 → 인덱스 upsert
  - `ProductUpdatedConsumer` — ProductUpdated 이벤트 소비 → 인덱스 upsert
  - `ProductDeletedConsumer` — ProductDeleted 이벤트 소비 → 인덱스 delete
  - `StockChangedConsumer` — StockChanged 이벤트 소비 → totalStock 갱신 및 status 반영
- Application service: `IndexSyncService` — 인덱스 동기화 유스케이스
- Outbound adapter: `ElasticsearchIndexAdapter` — `SearchIndexPort` 구현체 (Testcontainers로 테스트)
- 멱등성 처리: 동일 이벤트 중복 소비 시 upsert로 안전하게 처리
- 실패 이벤트 처리: 소비 실패 시 로깅 (DLQ는 Out of Scope)

## Out of Scope

- 외부 메시지 브로커(Kafka) 연동 (현재 단계: Spring ApplicationEvent 기반)
- DLQ 처리
- 검색 API (TASK-BE-022)
- 인덱스 재구축(reindex) API

---

# Acceptance Criteria

- [ ] `ProductCreated` 이벤트 소비 시 Elasticsearch에 `SearchDocument`가 upsert된다
- [ ] `ProductUpdated` 이벤트 소비 시 해당 document의 name, description, price, status, categoryId가 갱신된다
- [ ] `ProductDeleted` 이벤트 소비 시 해당 document가 인덱스에서 삭제된다
- [ ] `StockChanged` 이벤트 소비 시 totalStock이 갱신되고, status가 SOLD_OUT/ON_SALE로 자동 반영된다
- [ ] `IndexSyncService`가 application 레이어에 위치하고 `SearchIndexPort`를 통해서만 Elasticsearch에 접근한다
- [ ] `ElasticsearchIndexAdapter`가 outbound adapter 레이어에 위치한다
- [ ] 동일 eventId로 중복 소비 시 upsert로 안전하게 처리된다 (멱등성)
- [ ] 존재하지 않는 productId에 대한 ProductUpdated/StockChanged 이벤트 → 신규 upsert 또는 무시 (정책: upsert 시도)
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/search-service/architecture.md`
- `specs/services/search-service/dependencies.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/messaging/idempotent-consumer.md`
- `.claude/skills/messaging/consumer-retry-dlq.md`
- `.claude/skills/search/elasticsearch-index.md`
- `.claude/skills/search/index-sync.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/product-events.md`

---

# Target Service

- `search-service`

---

# Architecture

Follow:

- `specs/services/search-service/architecture.md`

Hexagonal Architecture 계층 배치:
- Domain: `SearchDocument`, `SearchIndexPort` (TASK-BE-020에서 정의됨)
- Application: `IndexSyncService`, `IndexSyncCommand`
- Inbound Adapters: `ProductCreatedConsumer`, `ProductUpdatedConsumer`, `ProductDeletedConsumer`, `StockChangedConsumer`
- Outbound Adapters: `ElasticsearchIndexAdapter`

---

# Implementation Notes

### IndexSyncService 인터페이스

```java
public class IndexSyncService {
    public void upsert(SearchDocument document) { ... }
    public void delete(String productId) { ... }
    public void updateStock(String productId, int totalStock, String status) { ... }
}
```

### StockChanged 처리

- `currentStock == 0` → status를 `SOLD_OUT`으로 인덱스 갱신
- `currentStock > 0` && 현재 인덱스 status가 `SOLD_OUT` → `ON_SALE`로 갱신
- `HIDDEN` 상태는 StockChanged로 변경하지 않음

### 멱등성 처리

Elasticsearch upsert는 기본적으로 멱등. eventId 기반 중복 체크는 불필요 (ES document ID = productId).

---

# Edge Cases

- `ProductDeleted` 이벤트에서 이미 삭제된 productId → 무시 (Elasticsearch delete는 존재하지 않아도 성공)
- `StockChanged`에서 totalStock 계산: 이벤트에 variantId별 currentStock만 포함 → 인덱스의 기존 totalStock 대신 currentStock을 직접 반영 (단일 variant 기준)
- 이벤트 payload의 필수 필드 누락 → 로깅 후 무시
- Elasticsearch 일시 장애 시 → 예외 발생, 이벤트 처리 실패 로깅

---

# Failure Scenarios

- Elasticsearch 저장 실패 → 예외 발생, 로그 기록 (현재 단계 허용)
- 이벤트 역직렬화 실패 → 로깅 후 무시
- 인덱스 매핑 불일치 → Elasticsearch가 400 에러 반환, 로깅 후 무시

---

# Test Requirements

- 단위 테스트: `IndexSyncServiceTest` — upsert 성공, delete 성공, stock 갱신, SOLD_OUT 전환, 포트 호출 검증
- 단위 테스트: `ProductCreatedConsumerTest` — 이벤트 수신 시 IndexSyncService 호출 검증
- 통합 테스트: `ElasticsearchIndexAdapterTest` — Testcontainers ES로 upsert/delete/조회 검증
- 통합 테스트: `IndexSyncIntegrationTest` — 이벤트 발행 → 인덱스 동기화 전체 흐름 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
