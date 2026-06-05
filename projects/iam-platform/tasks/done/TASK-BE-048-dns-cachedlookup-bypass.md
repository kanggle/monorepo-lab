# Task ID

TASK-BE-048-dns-cachedlookup-bypass

# Title

auth/account-service UnknownHostException — JVM InetAddress CachedLookup 경로 TTL=0 미적용 회귀

# Status

ready

# Owner

backend

# Task Tags

- deploy
- fix

---

# Goal

Fix issue found in TASK-BE-044.

BE-044에서 `JAVA_TOOL_OPTIONS=-Dnetworkaddress.cache.ttl=0 -Dnetworkaddress.cache.negative.ttl=0`를 적용했음에도,
auth-service와 account-service가 `java.net.UnknownHostException: mysql`로 기동 실패한다.
스택트레이스 상 `InetAddress$CachedLookup.get` 경로가 negative TTL=0을 무시하고 캐시된 실패 결과를 재사용하는 것으로 보인다.
BE-044 적용 이후 `restart: on-failure:5`(BE-046에서 롤백됨)에 의존하는 self-heal 경로가 제거됐기 때문에 DNS 해상도 실패가 startup 블록으로 남는다.

이 태스크는 docker-compose e2e 환경에서 cold start 5회 연속 auth/account-service healthy 달성을 목표로 한다.

---

# Scope

## In Scope

1. 재현: `docker compose -f docker-compose.e2e.yml down -v && up -d` 실행 시 auth/account-service에서 UnknownHostException 발생 여부 확인
2. 원인 분석:
   - `-Dnetworkaddress.cache.ttl=0`이 `InetAddress$CachedLookup` 코드 경로에서 왜 효과가 없는지 확인
   - JVM 버전별 `InetAddress` 캐시 동작 차이 (Java 11 vs 17 vs 21) 검토
   - Docker Desktop for Windows 내부 DNS resolver의 응답 타이밍 문제 여부 확인
3. 해결 후보 (최소 영향 우선):
   - `JAVA_TOOL_OPTIONS`에 추가 JVM 플래그 적용 (예: `-Dsun.net.inetaddr.ttl=0`)
   - compose `healthcheck` 기반 depends_on으로 auth/account-service가 mysql healthy 이후에만 기동하도록 강화 (현재 `service_started` 조건 확인)
   - `application-e2e.yml`의 JDBC URL에 `connectTimeout` 파라미터 추가로 MySQL 연결 재시도 허용
4. 적용 후 5회 연속 `down -v && up -d` 성공 확인

## Out of Scope

- JVM DNS 캐시 동작 수정 (코드/Dockerfile 변경 최소화 원칙)
- 프로덕션 환경의 DNS 설정 변경

---

# Acceptance Criteria

- [ ] `docker compose -f docker-compose.e2e.yml down -v && up -d` 5회 연속에서 auth-service, account-service healthy 달성
- [ ] UnknownHostException: mysql 스택트레이스 미발생
- [ ] 기존 단위 테스트 회귀 없음

---

# Related Specs

- `platform/testing-strategy.md`

# Related Skills

- (없음)

---

# Related Contracts

- (없음)

---

# Target Service

- `docker-compose.e2e.yml`
- `apps/auth-service/src/main/resources/application-e2e.yml`
- `apps/account-service/src/main/resources/application-e2e.yml`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`
- `specs/services/account-service/architecture.md`

---

# Implementation Notes

- BE-044에서 적용한 `JAVA_TOOL_OPTIONS`가 현재 `x-app-env` anchor에 정의되어 있고 docker-compose.e2e.yml의 모든 서비스에 merge됨 — 덮어쓰거나 제거하지 말 것
- BE-046 롤백으로 `restart: on-failure:5`가 제거된 상태이므로 별도 재시도 메커니즘 없음
- BE-045에서 적용된 `initialization-fail-timeout: 60000`은 유지 (connection 레벨 timeout이지 DNS 해상도 실패 재시도가 아님)

---

# Edge Cases

- `InetAddress$CachedLookup`은 JDK 구현 내부 클래스 — JVM 버전에 따라 property 이름이 다를 수 있음
- depends_on `condition: service_healthy` 적용 시 mysql healthcheck interval/retries가 충분한지 확인 필요

---

# Failure Scenarios

- JVM property 추가로도 CachedLookup 경로가 bypass되지 않으면: compose healthcheck 기반 depends_on 강화로 전환
- compose 변경이 CI 기동 시간을 크게 늘리면: 허용 trade-off로 기록

---

# Test Requirements

- 수동 재현 5회 반복
- 기존 단위/통합 테스트 회귀 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
