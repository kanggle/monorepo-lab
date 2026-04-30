# Task ID

TASK-BE-052-e2e-testcontainers-container-name

# Title

E2E 테스트 — Testcontainers `container_name` 비호환 수정

# Status

ready

# Owner

backend

# Task Tags

- e2e
- fix

# depends_on

- (없음)

---

# Goal

`./gradlew :tests:e2e:test` 실행 시 4개 테스트 모두 `initializationError`로 실패한다. 원인: Testcontainers 1.20.4의 `ComposeContainer`가 `docker-compose.e2e.yml`에 선언된 `container_name` 속성을 거부 (`IllegalStateException: 'container_name' property is not supported by Testcontainers`).

BE-050/051 수정 이후 서비스는 수동 `docker compose up`으로 4개 모두 healthy 확인됨. 이 태스크는 Testcontainers 경유 자동화 테스트가 통과하도록 수정.

---

# Scope

## In Scope

1. `docker-compose.e2e.yml`에서 모든 `container_name` 속성 제거
2. 수동 실행 시 `COMPOSE_PROJECT_NAME=gap-e2e`로 예측 가능한 컨테이너 이름 유지 확인
3. E2E 테스트 4개 통과 확인

## Out of Scope

- `docker-compose.yml` (로컬 dev 인프라) 변경
- E2E 테스트 시나리오 로직 변경

---

# Acceptance Criteria

- [ ] `docker-compose.e2e.yml`에 `container_name` 속성 없음
- [ ] `./gradlew :tests:e2e:test` 4개 테스트 통과
- [ ] 수동 `docker compose -f docker-compose.e2e.yml up -d`도 정상 기동

---

# Related Specs

- 없음

---

# Related Contracts

- 없음

---

# Target Service

- `tests/e2e`

---

# Edge Cases

- Testcontainers가 compose project name을 자동 생성 시 서비스 간 네트워크 통신 확인

---

# Failure Scenarios

- Testcontainers가 compose 실행 시 포트 충돌 (이미 떠있는 컨테이너와) — 기존 컨테이너 정리 필요

---

# Test Requirements

- E2E 테스트 스위트 전체 통과

---

# Definition of Done

- [ ] container_name 제거 + E2E 테스트 통과
- [ ] Ready for review
