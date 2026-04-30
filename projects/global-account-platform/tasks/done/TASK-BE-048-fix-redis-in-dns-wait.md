# Task ID

TASK-BE-048-fix-redis-in-dns-wait

# Title

Dockerfile ENTRYPOINT DNS wait — redis 호스트 체크 추가 (auth/admin/security)

# Status

ready

# Owner

backend

# Task Tags

- deploy
- fix

# depends_on

- TASK-BE-048-dns-cachedlookup-bypass (완료됨)

---

# Goal

BE-048은 Dockerfile ENTRYPOINT에 `getent hosts mysql`/`getent hosts kafka` 선행 체크를 추가했으나 `redis` 호스트는 누락됐다. auth-service/admin-service/security-service는 Redis에 의존하며 depends_on에도 `redis: { condition: service_healthy }`를 선언한다. Redis에 대해서도 동일한 InetAddress$CachedLookup negative-cache 회귀가 발생할 수 있다. account-service는 Redis 비의존이라 제외.

---

# Scope

## In Scope

- `apps/auth-service/Dockerfile`, `apps/admin-service/Dockerfile`, `apps/security-service/Dockerfile` ENTRYPOINT getent 루프에 `&& getent hosts redis >/dev/null` 추가
- `apps/account-service/Dockerfile`은 변경 없음 (Redis 비의존)

## Out of Scope

- 다른 DNS wait 로직 재설계
- ENTRYPOINT 문자열 → 별도 entrypoint.sh 추출 (Suggestion 수준)

---

# Acceptance Criteria

- [ ] 3개 Dockerfile ENTRYPOINT에 redis 체크 포함
- [ ] `docker compose -f docker-compose.e2e.yml down -v && up -d` 3회 연속 4개 서비스 healthy
- [ ] 기존 단위 테스트 회귀 없음

---

# Related Specs

- 없음

---

# Target Service

- `apps/auth-service`, `apps/admin-service`, `apps/security-service`

---

# Edge Cases

- getent 실패 시 메시지 로그에 redis/mysql/kafka 구분 표시 유지

---

# Failure Scenarios

- 없음

---

# Test Requirements

- 수동 3회 compose 사이클

---

# Definition of Done

- [ ] 3 Dockerfile 수정 + 검증
- [ ] Ready for review
