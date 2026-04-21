# Task ID

TASK-INT-006

# Title

docker-compose 리소스 제한 및 프론트엔드 헬스체크 추가

# Status

done

# Owner

backend

# Task Tags

- deploy
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

docker-compose.yml에 모든 서비스의 리소스 제한(CPU, Memory)을 추가하고, 프론트엔드 앱의 헬스체크를 추가하여 로컬 개발 환경의 안정성을 개선한다.

현재 상태: 컨테이너에 리소스 제한이 없어 단일 서비스가 호스트 전체 리소스를 소모할 수 있다. 프론트엔드 컨테이너에 헬스체크가 없다.

---

# Scope

## In Scope

- 모든 서비스 컨테이너에 `deploy.resources.limits` (memory, cpus) 추가
- 인프라 컨테이너 (Kafka, PostgreSQL, Redis, Elasticsearch) 리소스 제한 추가
- web-store, admin-dashboard 컨테이너 헬스체크 추가
- OTel Agent JAR 다운로드를 빌드 캐싱이 가능하도록 개선

## Out of Scope

- Kubernetes 매니페스트 작성
- Docker Swarm 모드 구성
- 프로덕션 리소스 사이징

---

# Acceptance Criteria

- [ ] 모든 서비스 컨테이너에 memory, cpus 제한이 설정된다
- [ ] 인프라 컨테이너 (postgres, redis, kafka, elasticsearch, jaeger, prometheus) 에 리소스 제한이 설정된다
- [ ] web-store, admin-dashboard에 HTTP 기반 헬스체크가 추가된다
- [ ] `docker compose up` 으로 전체 서비스가 정상 기동된다
- [ ] 리소스 제한 내에서 기본 기능이 동작한다

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `docker-compose.yml`

---

# Architecture

_(해당 없음)_

---

# Implementation Notes

권장 리소스 제한 (로컬 개발 기준):
- 백엔드 서비스: 512MB~768MB memory, 0.5 cpus
- Elasticsearch: 1GB memory, 1.0 cpus
- Kafka + Zookeeper: 512MB memory each
- PostgreSQL: 256MB memory
- Redis: 128MB memory
- 프론트엔드: 256MB memory
- Jaeger/Prometheus: 256MB memory

Docker Compose v3 문법: `deploy.resources.limits` 사용

---

# Edge Cases

- 리소스 제한이 너무 낮아 OOM Kill 발생
- Elasticsearch JVM 힙이 컨테이너 메모리 제한과 충돌
- Docker Desktop 메모리 할당이 전체 합보다 적은 경우

---

# Failure Scenarios

- 컨테이너 OOM Kill 시 자동 재시작 (restart: unless-stopped)
- Docker Compose v2 환경에서 deploy 섹션 무시 — 경고 로그 확인

---

# Test Requirements

- `docker compose config` 로 문법 검증
- `docker compose up -d` 후 전체 서비스 healthy 상태 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
