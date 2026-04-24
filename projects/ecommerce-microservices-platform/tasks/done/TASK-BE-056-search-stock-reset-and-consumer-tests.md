# Task ID

TASK-BE-056

# Title

search-service ProductUpdated 소비 시 재고 초기화 버그 수정 및 누락 컨슈머 테스트 추가

# Status

done

# Owner

backend

# Task Tags

- code
- event
- test

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

search-service의 ProductUpdatedConsumer에서 상품 업데이트 이벤트 소비 시 `totalStock = 0`으로 하드코딩하여 검색 인덱스의 재고가 초기화되는 버그를 수정한다. 또한 ProductUpdated, ProductDeleted, StockChanged 컨슈머의 누락된 단위 테스트를 추가한다.

현재 상태: ProductUpdatedConsumer에서 상품 수정 시 재고를 0으로 설정하여, 검색 결과에서 모든 수정된 상품이 품절로 표시된다.

---

# Scope

## In Scope

- ProductUpdatedConsumer의 totalStock 처리 로직 수정 (기존 인덱스 재고 유지 또는 이벤트 페이로드에서 수신)
- ProductUpdatedConsumer 단위 테스트 추가
- ProductDeletedConsumer 단위 테스트 추가
- StockChangedConsumer 단위 테스트 추가
- ElasticsearchQueryAdapter의 타입 캐스팅 안전성 개선

## Out of Scope

- Elasticsearch 클러스터 구성
- 검색 쿼리 성능 최적화
- 인덱스 스키마 변경

---

# Acceptance Criteria

- [ ] ProductUpdated 이벤트 소비 시 기존 인덱스의 재고 값이 유지된다
- [ ] ProductUpdatedConsumer에 대한 단위 테스트가 추가된다 (정상, 누락 필드, 역직렬화 실패)
- [ ] ProductDeletedConsumer에 대한 단위 테스트가 추가된다
- [ ] StockChangedConsumer에 대한 단위 테스트가 추가된다
- [ ] ElasticsearchQueryAdapter에서 예상 외 타입 반환 시 NumberFormatException 대신 안전한 기본값 처리
- [ ] 기존 ProductCreatedConsumer 테스트가 통과한다

---

# Related Specs

- `specs/services/search-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal.md`
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

---

# Implementation Notes

- ProductUpdated 이벤트에 totalStock이 포함되어 있으면 사용, 없으면 기존 인덱스에서 조회하여 유지
- 인덱스에 문서가 없는 경우 (이전에 인덱싱 안 된 상품) totalStock = 0 허용
- ElasticsearchQueryAdapter의 `Long.parseLong(val.toString())` → try-catch 또는 타입 체크 추가

---

# Edge Cases

- ProductUpdated 이벤트가 ProductCreated보다 먼저 도착한 경우
- StockChanged 이벤트와 ProductUpdated 이벤트가 동시에 도착하여 재고 값 충돌
- Elasticsearch에 해당 상품 문서가 없는 경우

---

# Failure Scenarios

- Elasticsearch 연결 실패 시 재시도 처리
- 역직렬화 실패 시 DLT로 전송
- 기존 인덱스 조회 실패 시 totalStock = 0 fallback 후 로그 경고

---

# Test Requirements

- ProductUpdatedConsumer 단위 테스트 (재고 유지 검증, 누락 필드, 역직렬화 실패)
- ProductDeletedConsumer 단위 테스트 (인덱스 삭제 확인)
- StockChangedConsumer 단위 테스트 (재고 업데이트 확인)
- ElasticsearchQueryAdapter 타입 캐스팅 안전성 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
