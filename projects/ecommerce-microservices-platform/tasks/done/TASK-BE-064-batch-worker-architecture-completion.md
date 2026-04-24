# Task ID

TASK-BE-064

# Title

batch-worker 아키텍처 완성 — 빈 설정 클래스 제거, 도메인 검증 추가

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

TASK-INT-012 크로스 리뷰에서 발견된 Critical 이슈 수정. batch-worker의 빈 설정 클래스(BatchConfig, KafkaConfig)가 내용 없이 존재하는 dead code 문제와 BatchJobExecution 도메인 모델의 입력 검증 누락 문제를 수정한다.

---

# Scope

## In Scope

- BatchConfig.java: 빈 클래스 제거 또는 실제 설정 추가
- KafkaConfig.java: 빈 클래스 제거 또는 실제 설정 추가
- BatchJobExecution.start(): jobName null/blank 검증 추가
- BatchJobExecution.fail(): errorMessage 검증 추가
- BatchWorkerApplication @EnableScheduling: 실제 스케줄 잡이 없으면 어노테이션 제거
- 도메인 모델 단위 테스트 추가

## Out of Scope

- 배치 잡 비즈니스 로직 구현 (별도 태스크 범위)
- Kafka 컨슈머/프로듀서 구현

---

# Acceptance Criteria

- [x] 빈 설정 클래스가 제거되거나 실제 설정으로 채워진다
- [x] BatchJobExecution 도메인 모델에 입력 검증이 추가된다
- [x] 불필요한 @EnableScheduling이 제거되거나 스케줄 잡이 구현된다
- [x] 단위 테스트가 추가된다

---

# Related Specs

- `specs/services/batch-worker/architecture.md`
- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- BatchJobExecution.start()에 null jobName 전달 시 명확한 예외

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- BatchJobExecution 도메인 모델 단위 테스트
- null/blank 입력에 대한 예외 발생 테스트
