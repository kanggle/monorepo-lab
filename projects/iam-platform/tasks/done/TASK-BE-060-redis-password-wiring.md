# Task ID

TASK-BE-060

# Title

Redis 비번 주입 일관화 — compose `requirepass`와 앱 config 정합

# Status

ready

# Owner

backend

# Task Tags

- code

# depends_on

(없음)

---

# Goal

로컬 docker compose는 `--requirepass ${REDIS_PASSWORD:-redispass}`로 Redis를 보호하지만, 서비스 앱(`application.yml`)은 Redis 비번을 읽지 않아 부팅 시 `NOAUTH Authentication required`로 실패한다. 매번 `docker exec gap-redis redis-cli CONFIG SET requirepass ""`로 런타임에 비번을 해제하는 워크어라운드를 제거하고, 앱 config에 `REDIS_PASSWORD`를 주입하여 두 경로를 정합한다.

발견 시점: TASK-BE-058 검증 중 로컬 전체 스택 기동 시 auth-service / admin-service 헬스 체크 `DOWN` (`redis: RedisConnectionFailureException`).

---

# Scope

## In Scope

Redis를 사용하는 5개 서비스 `application.yml`에 비번 주입 추가:

- `apps/auth-service/src/main/resources/application.yml`
- `apps/admin-service/src/main/resources/application.yml`
- `apps/security-service/src/main/resources/application.yml`
- `apps/gateway-service/src/main/resources/application.yml`
- `apps/membership-service/src/main/resources/application.yml`

변경 형태 (기존 블록에 `password:` 한 줄 추가):

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}     # ← 추가
      timeout: 50ms
```

기본값 `""`(빈 문자열)로 두어 비번이 없는 환경(CI Linux Docker, Testcontainers)에서도 그대로 동작 유지.

`.env.example`에 `REDIS_PASSWORD=redispass` 이미 존재 (compose와 동일) → `.env`를 복사하면 자동 매칭.

## Out of Scope

- Redis TLS 활성화
- Redis ACL 사용자/그룹 도입
- Redis Sentinel/Cluster 전환
- `REDIS_HOST` / `REDIS_PORT` 네이밍 통일 (이미 `REDIS_*` 접두어로 일관)

---

# Acceptance Criteria

- [ ] 5개 서비스 `application.yml` 모두 `spring.data.redis.password: ${REDIS_PASSWORD:}` 포함
- [ ] `.env.example`의 `REDIS_PASSWORD` 값이 compose 기본값과 일치 (`redispass`)
- [ ] `docker compose up -d` 후 `.env`가 기본값인 상태에서
  `./gradlew :apps:auth-service:bootRun` 기동 시 `/actuator/health` = 200 (Redis UP)
- [ ] 런타임 `docker exec gap-redis redis-cli CONFIG SET requirepass ""` 워크어라운드 없이 동작
- [ ] Testcontainers 기반 통합 테스트(`@EnabledIf("isDockerAvailable")`)에서 Redis container는 비번 없이 기동되므로 `REDIS_PASSWORD` 미설정 환경 그대로 유지 (`application-test.yml`은 변경 불필요)
- [ ] `./gradlew test`로 각 서비스 단위·슬라이스 테스트 회귀 없음

---

# Related Specs

- `specs/services/auth-service/redis-keys.md`
- `specs/services/gateway-service/architecture.md`
- `specs/services/admin-service/redis-keys.md` (존재 시)
- `platform/security-rules.md`

---

# Related Contracts

(없음 — 내부 인프라 설정)

---

# Target Service

- `apps/auth-service`, `apps/admin-service`, `apps/security-service`, `apps/gateway-service`, `apps/membership-service`

---

# Architecture

각 서비스 아키텍처 변경 없음. Spring Boot `spring.data.redis.password`는 Lettuce/Jedis 클라이언트가 AUTH 커맨드로 사용.

---

# Edge Cases

- `.env` 없이 bootRun하는 개발자: 환경변수 미설정 → 빈 문자열 → Redis가 비번 강제 시 여전히 실패. 해결: `.env` 사용을 README에 명시 (이미 존재)
- Windows + WSL Redis: WSL에 Redis 직접 설치 시 compose와 다른 경로, 이 태스크 영향 없음
- Reactive Redis (gateway-service의 `ReactiveStringRedisTemplate`)도 동일 property 읽음 — 별도 처리 불필요

---

# Failure Scenarios

- compose와 앱 config 비번이 불일치 → 현재와 동일 `NOAUTH` 오류. `.env` 파일이 `.env.example`과 싱크되어 있는지 docs에 명시 필요
- Redis 컨테이너가 passwordless 모드인데 앱이 비번 전송 → Lettuce는 무시 (`password:` 빈 문자열이면 AUTH 커맨드 스킵)

---

# Test Requirements

순수 config 주입이라 새 테스트 불요. 기존 단위 테스트 회귀 없음 확인.

- `./gradlew :apps:auth-service:test :apps:admin-service:test :apps:security-service:test :apps:gateway-service:test :apps:membership-service:test`
- 수동 smoke:
  ```bash
  docker compose up -d mysql redis
  ./gradlew :apps:auth-service:bootRun   # 백그라운드
  curl http://localhost:8081/actuator/health | grep '"status":"UP"'
  ```

---

# Definition of Done

- [ ] 5개 서비스 `application.yml` 업데이트
- [ ] 단위 테스트 회귀 없음
- [ ] 로컬 smoke (auth-service bootRun + health 200) 통과
- [ ] Ready for review
