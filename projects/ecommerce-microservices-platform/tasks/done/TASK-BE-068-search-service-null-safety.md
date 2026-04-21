# Task ID

TASK-BE-068

# Title

search-service Elasticsearch 응답 null 안전성 및 예외 처리 개선

# Status

done

# Owner

backend

# Task Tags

- code, test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-INT-012 크로스 리뷰에서 발견된 Major 이슈 수정. ElasticsearchQueryAdapter의 null 안전성 부족, 제네릭 RuntimeException 래핑, 중복 헬퍼 메서드 문제를 수정한다.

---

# Scope

## In Scope

- ElasticsearchQueryAdapter.toResult(): response.hits() null 체크 추가
- ElasticsearchQueryAdapter: generic RuntimeException → 커스텀 SearchException으로 변경
- ElasticsearchQueryAdapter/ElasticsearchIndexAdapter: 중복 헬퍼 메서드(getString, toLong, toInt) 공통 유틸로 추출

## Out of Scope

- Elasticsearch 클러스터 설정 변경

---

# Acceptance Criteria

- [ ] Elasticsearch 응답의 null 안전성이 확보된다
- [ ] 커스텀 SearchException이 도입되어 에러 유형을 구분할 수 있다
- [ ] 중복 헬퍼 메서드가 하나의 유틸리티 클래스로 통합된다
- [ ] 단위 테스트가 추가된다

---

# Related Specs

- `specs/services/search-service/architecture.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- Elasticsearch가 빈 응답을 반환하는 경우
- aggregation 결과가 예상과 다른 타입인 경우

---

# Failure Scenarios

- Elasticsearch 연결 실패 시 SearchException으로 래핑

---

# Test Requirements

- null/빈 응답에 대한 단위 테스트
- SearchException 발생 시나리오 테스트
