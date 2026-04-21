# Task ID

TASK-R-03

# Title

libs/java-common에 PageQuery/PageResult 공통 추출

# Status

ready

# Owner

backend

# Task Tags

- refactor
- shared-library

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

order-service, shipping-service, user-service, promotion-service에 중복된 PageQuery/PageResult를 libs/java-common으로 추출한다. PageQuery/PageResult는 페이지네이션 요청/응답을 표현하는 공통 기술 DTO이며, shared-library-policy의 "common DTO primitives only if they are truly cross-service and stable" 허용 범주에 해당한다.

---

# Scope

## In Scope

- libs/java-common에 PageQuery record 생성 (page, size 필드, 기본값 및 검증 로직 포함)
- libs/java-common에 PageResult record 생성 (content, page, size, totalElements, totalPages 필드)
- order-service, shipping-service, user-service, promotion-service의 중복 PageQuery/PageResult 클래스 제거
- 4개 서비스의 build.gradle에 libs/java-common 의존성 추가 (이미 있는 경우 확인만)
- Application/Presentation 레이어에서 공통 PageQuery/PageResult를 참조하도록 import 경로 변경

## Out of Scope

- PageQuery/PageResult 필드 변경
- 페이지네이션 로직 변경
- PageQuery/PageResult를 사용하지 않는 서비스에 적용
- Repository 레이어의 Spring Data Pageable 변환 로직 변경

---

# Acceptance Criteria

- [ ] libs/java-common에 PageQuery record가 존재한다
- [ ] libs/java-common에 PageResult record가 존재한다
- [ ] PageQuery는 page(int), size(int) 필드를 가지며 검증 로직을 포함한다
- [ ] PageResult는 content(List<T>), page(int), size(int), totalElements(long), totalPages(int) 필드를 가진다
- [ ] order-service의 중복 PageQuery/PageResult가 제거되고 libs/java-common을 참조한다
- [ ] shipping-service의 중복 PageQuery/PageResult가 제거되고 libs/java-common을 참조한다
- [ ] user-service의 중복 PageQuery/PageResult가 제거되고 libs/java-common을 참조한다
- [ ] promotion-service의 중복 PageQuery/PageResult가 제거되고 libs/java-common을 참조한다
- [ ] 모든 기존 테스트가 통과한다
- [ ] API 응답 포맷이 변경되지 않았다

---

# Related Specs

- `specs/platform/shared-library-policy.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음. 페이지네이션 응답 JSON 구조는 기존과 동일)

---

# Target Service

- `libs/java-common`
- `order-service`
- `shipping-service`
- `user-service`
- `promotion-service`

---

# Architecture

Follow:

- `specs/platform/shared-library-policy.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- PageQuery, PageResult는 Java record로 구현한다.
- PageQuery의 검증 로직: page >= 0, size >= 1, size <= 상한값 (기존 서비스 로직 기준).
- PageResult는 제네릭 타입 파라미터를 사용한다: `PageResult<T>`.
- Spring Data의 `Page` -> `PageResult` 변환 유틸리티 메서드를 제공한다 (`PageResult.from(Page<T>)` 등).
- 도메인 레이어에서 사용 가능하도록 프레임워크 의존성 없이 구현한다 (Spring Data Page 변환은 인프라 레이어 유틸로 분리).

---

# Edge Cases

- 서비스별 PageQuery/PageResult 필드가 미세하게 다른 경우 -> 공통 필드만 추출하고, 추가 필드가 있는 서비스는 확장 또는 래핑
- size 상한값이 서비스마다 다른 경우 -> 공통 라이브러리에서는 기본 상한만 제공하고 서비스별 오버라이드 허용
- libs/java-common이 아직 존재하지 않는 경우 -> 라이브러리 모듈 신규 생성

---

# Failure Scenarios

- import 경로 변경 누락으로 컴파일 오류 -> 전 서비스 빌드 확인
- PageResult 직렬화 동작 변경으로 API 응답 구조 달라짐 -> 슬라이스 테스트에서 응답 JSON 검증
- 제네릭 타입 파라미터 미스매치로 컴파일 오류
- libs/java-common 의존성 추가 누락으로 클래스 찾기 실패

---

# Test Requirements

- libs/java-common PageQuery 단위 테스트 (유효/무효 값, 경계값 검증)
- libs/java-common PageResult 단위 테스트 (생성, 제네릭 타입 확인)
- 각 서비스의 기존 페이지네이션 관련 테스트 통과 확인
- 각 서비스의 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
