# Task ID

TASK-INT-012

# Title

코드 품질 크로스 리뷰 — 전체 서비스 기술 부채 점검 및 개선 태스크 도출

# Status

review

# Owner

backend

# Task Tags

- code

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

전체 서비스의 코드 품질을 점검하여 기술 부채, 패턴 불일치, 보안 취약점, 테스트 커버리지 부족 등을 발견하고 개선이 필요한 항목을 태스크로 도출한다.

---

# Scope

## In Scope

### 백엔드 서비스 점검 (8개)
- auth-service, product-service, search-service, order-service, payment-service, user-service, batch-worker, gateway-service
- 점검 항목:
  - 아키텍처 패턴 일관성 (레이어 의존성, DDD 적용 여부)
  - 에러 핸들링 패턴 통일성
  - 로깅 패턴 통일성 (한국어/영어 혼용 등)
  - 테스트 커버리지 및 품질
  - 보안 취약점 (SQL injection, 민감 데이터 노출 등)
  - 이벤트 발행/소비 패턴 일관성
  - 불필요한 의존성 또는 사용하지 않는 코드

### 프론트엔드 서비스 점검 (2개 + 공유 패키지)
- web-store, admin-dashboard, @repo/api-client, @repo/types, @repo/ui, @repo/utils
- 점검 항목:
  - FSD 아키텍처 준수 여부
  - 컴포넌트 재사용성
  - 타입 안전성
  - 에러 바운더리 및 로딩 상태 처리
  - 메모리 누수 패턴

### 인프라 점검
- docker-compose.yml 최적화 여지
- Dockerfile 빌드 캐시 효율성

## Out of Scope

- 발견된 이슈 직접 수정 (태스크 도출만)
- 성능 프로파일링

---

# Acceptance Criteria

- [ ] 전체 백엔드 서비스 코드 리뷰가 완료된다
- [ ] 전체 프론트엔드 서비스 코드 리뷰가 완료된다
- [ ] 발견된 이슈가 심각도(Critical/Major/Minor)별로 분류된다
- [ ] Critical/Major 이슈는 각각 별도 fix 태스크로 생성되어 `tasks/ready/`에 배치된다
- [ ] 리뷰 결과 요약 보고서가 출력된다

---

# Related Specs

- `specs/platform/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/platform/naming-conventions.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/security-rules.md`

# Related Skills

_(없음)_

---

# Related Contracts

- `specs/contracts/` (전체 계약 대비 구현 준수 여부)

---

# Target Service

- 전체 서비스 (읽기 전용 리뷰)
- `tasks/ready/` (도출된 fix 태스크 생성)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- 서비스마다 아키텍처가 다른 경우 (Layered vs DDD) 각각의 스펙 기준으로 평가
- 이미 done 태스크에서 수정된 이슈가 재발견될 경우 회귀 버그로 분류

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- 리뷰 태스크이므로 별도 테스트 불필요
- 도출된 fix 태스크는 각각 자체 테스트 요구사항을 포함해야 함

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
