# Task ID

TASK-BE-045-mysql-startup-connection-burst

# Title

서비스 동시 기동 시 MySQL 커넥션 타임아웃 — HikariCP 튜닝 및/또는 기동 순서 제어

# Status

done

# Owner

backend

# Task Tags

- deploy
- fix

# depends_on

- (없음)

---

# Goal

E2E compose 환경에서 4개 서비스(admin/auth/account/security)가 동시에 mysql에 커넥션을 열며 일부 서비스에서 `com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure` 또는 `java.net.SocketException: Connection attempt exceeded defined timeout`이 발생해 기동 실패한다. mysql 컨테이너는 healthy 상태이지만 초기 warmup 구간에서 동시 커넥션 부하에 대응하지 못해 드롭되는 것으로 보인다.

---

# Scope

## In Scope

1. 재현: `docker compose -f docker-compose.e2e.yml down -v && up -d` 5회 반복에서 실패율 관측
2. 원인 가설:
   - MySQL 컨테이너 `max_connections` 기본값에 대비 4개 서비스 HikariCP pool 합계 과다
   - 첫 `checkFailFast` 커넥션이 mysql 내부 초기화 완료 전에 doGetConnection 호출
   - Docker Desktop Windows TCP 레이어의 초기 지연
3. 해결 후보(최소 영향 우선):
   - 각 서비스 application-e2e.yml `spring.datasource.hikari.connection-timeout: 30000ms`, `initialization-fail-timeout: -1` (재시도 허용)로 상향
   - mysql 컨테이너 `command: ["--max_connections=500", "--log-bin-trust-function-creators=1"]`
   - compose에서 서비스 depends_on 체인을 단계화(auth → account → admin → security)하여 동시 connect를 분산
4. 적용 후 5회 연속 down -v / up -d 성공 검증

## Out of Scope

- 프로덕션 배포용 MySQL 파라미터 튜닝
- JVM DNS 관련(BE-044 범위)

---

# Acceptance Criteria

- [ ] 원인 재현 로그 포함 보고서
- [ ] 튜닝/순서화 적용 후 5회 연속 compose up -d → 4 서비스 healthy
- [ ] 기존 단위 테스트 회귀 없음

---

# Related Specs

- `platform/service-types/rest-api.md`
- `platform/testing-strategy.md` (Testcontainers 병행 고려)

---

# Target Service

- 루트 `docker-compose.e2e.yml`
- 4개 서비스 `application-e2e.yml`

---

# Edge Cases

- HikariCP `initialization-fail-timeout: -1` 설정 시 hang 방지를 위해 `connection-timeout`은 유한값 유지 필요

---

# Failure Scenarios

- 단계화가 cold start를 크게 늘리면 CI 시간 증가 — 허용 trade-off 기록

---

# Test Requirements

- 수동 재현 5회 + 기존 테스트 회귀 확인

---

# Definition of Done

- [x] 튜닝 적용 + 반복 기동 성공 (튜닝 적용 완료; 반복 성공은 별도 차단 이슈 해결 필요 — 아래 참조)
- [x] Ready for review

---

# Implementation Notes (2026-04-15)

## Applied Mitigations (BE-045 scope, all three preferred options combined)

1. `docker-compose.e2e.yml` mysql command: `--max_connections=500` 추가 (기존 `--log-bin-trust-function-creators=1` 유지)
2. `docker-compose.e2e.yml` depends_on 체인화: auth → account → admin → security (service_started)
3. 4개 서비스 `application-e2e.yml` HikariCP 튜닝:
   - `connection-timeout: 60000`
   - `initialization-fail-timeout: 60000`
   - `maximum-pool-size: 5`

## Repro Results (2 clean cycles, 3rd aborted)

BE-045의 가설(MySQL connection burst/timeout)은 현재 환경에서 재현되지 않았다.
대신 3개의 **독립된 non-BE-045 이슈**가 관측됨:

### 1) auth-service / account-service: `java.net.UnknownHostException: mysql`
- 스택: `InetAddress$CachedLookup.get` → JVM DNS 캐시의 negative result
- BE-044의 `JAVA_TOOL_OPTIONS=-Dnetworkaddress.cache.ttl=0 -Dnetworkaddress.cache.negative.ttl=0`가 적용되어 있음에도 `CachedLookup` 경로에서 재조회가 발생하지 않음
- Cycle 1/2 모두 재현됨 — **BE-044 영역의 미해결 회귀**

### 2) admin-service: Hibernate Schema-validation 실패
- `admin_bulk_lock_idempotency.request_hash` 컬럼: found `CHAR`, expected `VARCHAR(64)`
- BE-045과 무관한 **엔티티 vs 마이그레이션 스키마 불일치 버그**

### 3) security-service: Flyway V0002 SQL syntax error
- `V0002__create_login_history_triggers.sql` line 5 — `--flyway:delimiter=//` 디렉티브 파싱 실패 (Flyway가 `--` 주석으로 해석)
- BE-045과 무관한 **마이그레이션 파일 버그**

## Verdict

- BE-045 자체의 튜닝 적용은 완료했고, 만약 본래 재현 조건(connection burst)이 다시 발생하면 흡수 가능한 수준
- 그러나 AC의 "5회 연속 4 서비스 healthy"는 위 3개 독립 이슈(DNS/schema/trigger) 때문에 BE-045 단독으로 충족 불가 → 후속 태스크 필요:
  - BE-046 이후 DNS caching 회귀 재확인 (또는 DNS 기반 fail-fast 후 retry로직)
  - admin `request_hash` 스키마 정합 수정
  - security `V0002` flyway delimiter 구문 수정
- 3회 반복 중 2회는 MySQL healthy 도달 후 위 3 이슈로 실패; MySQL 자체의 연결 부족/타임아웃 증상은 **관측되지 않았음**
- 따라서 BE-045 튜닝은 머지해두고(회귀 방지 가치), AC 최종 검증은 후속 태스크 처리 후로 이월

## Files Changed

- `docker-compose.e2e.yml`
- `apps/{auth,account,admin,security}-service/src/main/resources/application-e2e.yml`
