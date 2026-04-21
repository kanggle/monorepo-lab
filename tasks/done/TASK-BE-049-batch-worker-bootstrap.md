# Task ID

TASK-BE-049

# Title

batch-worker 부트스트랩 — 프로젝트 구조, DB 스키마, Spring Batch 설정, 초기 잡 구성

# Status

done

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

batch-worker 서비스의 초기 프로젝트 구조를 생성한다.

Spring Boot + Spring Batch 기반으로 스케줄링 인프라를 구성하고, 잡 실행 이력을 저장할 DB 스키마를 정의하며, Layered Architecture에 맞는 패키지 구조를 갖춘다.

이 태스크 완료 후 batch-worker에 개별 잡을 추가할 수 있는 상태가 된다.

---

# Scope

## In Scope

- Spring Boot + Spring Batch 프로젝트 구성 (build.gradle, application.yml)
- Layered Architecture 패키지 구조 생성 (scheduling, application, domain, infrastructure)
- PostgreSQL 연결 설정 및 Spring Batch 메타 테이블 초기화
- batch_job_execution 커스텀 테이블 스키마 (V1 migration)
- Kafka 컨슈머/프로듀서 기본 설정
- docker-compose에 batch-worker 서비스 추가
- Dockerfile 작성
- Health check 엔드포인트 (actuator)
- java-observability, java-common 라이브러리 연동

## Out of Scope

- 개별 비즈니스 잡 구현 (별도 태스크)
- HTTP API 엔드포인트
- 프론트엔드 연동

---

# Acceptance Criteria

- [ ] Spring Boot 애플리케이션이 정상 기동된다
- [ ] Spring Batch 메타 테이블이 자동 생성된다
- [ ] batch_job_execution 커스텀 테이블이 Flyway 마이그레이션으로 생성된다
- [ ] Kafka 연결이 설정되어 있다 (컨슈머/프로듀서 bean 생성 확인)
- [ ] Layered Architecture 패키지 구조가 아키텍처 스펙과 일치한다
- [ ] docker-compose up으로 batch-worker가 정상 기동된다
- [ ] Actuator health 엔드포인트가 응답한다
- [ ] 구조화 로깅 및 MDC 필터가 적용되어 있다

---

# Related Specs

- `specs/platform/architecture.md`
- `specs/services/batch-worker/architecture.md`
- `specs/platform/observability.md`
- `specs/platform/deployment-policy.md`
- `specs/platform/coding-rules.md`
- `specs/platform/naming-conventions.md`

# Related Skills

- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- 없음 (batch-worker는 HTTP API를 노출하지 않음)

---

# Target Service

- `batch-worker`

---

# Architecture

Follow:

- `specs/services/batch-worker/architecture.md`

---

# Implementation Notes

- Spring Batch의 기본 메타 테이블(BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION 등)은 `spring.batch.jdbc.initialize-schema=always`로 자동 생성
- 커스텀 batch_job_execution 테이블은 Flyway로 관리
- 기존 서비스들의 build.gradle, application.yml 패턴을 참고하여 일관성 유지
- `@EnableScheduling`은 설정하되, 초기에는 빈 잡 상태로 기동만 확인

---

# Edge Cases

- Spring Batch 메타 테이블과 커스텀 테이블의 이름 충돌 방지
- 다중 인스턴스 실행 시 잡 중복 실행 방지 (ShedLock 또는 Spring Batch의 JobInstance 유일성 보장)
- Kafka 브로커 미기동 시에도 애플리케이션 기동 가능해야 함

---

# Failure Scenarios

- PostgreSQL 연결 실패 시 기동 실패 + 명확한 에러 로그
- Kafka 브로커 미연결 시 기동은 성공하되 컨슈머 재연결 시도
- Flyway 마이그레이션 실패 시 기동 중단

---

# Test Requirements

- 애플리케이션 컨텍스트 로드 테스트
- Flyway 마이그레이션 검증 테스트
- Spring Batch 메타 테이블 존재 확인 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
