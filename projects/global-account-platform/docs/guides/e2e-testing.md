# E2E Testing Guide

이 문서는 `tests/e2e` 모듈(TASK-BE-041c)의 **운영자(사람) 관점** 가이드이다.
AI 에이전트용 규칙은 `platform/testing-strategy.md`, 태스크 파일, 그리고
`tests/e2e/README.md`에 있다.

## 언제 쓰는가

E2E 스위트는 다음 경우에 돌린다:

- 플랫폼의 인증 플로우(enroll / login / refresh / logout)를 건드린 PR
- `docker-compose.e2e.yml`, 각 서비스의 `Dockerfile`, Kafka/DB migration 변경
- `admin-service` → `account-service` → `security-service`의 이벤트 경로
  (예: `account.locked` 소비자) 변경
- Resilience4j circuit breaker 설정, Spring Kafka DLQ 라우팅 설정 변경

기능 단위 검증은 여전히 단위·슬라이스·Integration 테스트가 1차 방어선이다.
E2E는 “붙여놨을 때 진짜로 붙는가”만 증명한다.

## 실행 절차

1. Docker Desktop 켜두기 (Windows/macOS) 또는 `docker info` 성공 확인.
2. 서비스별 bootJar 생성:
   ```bash
   ./gradlew :apps:auth-service:bootJar \
             :apps:account-service:bootJar \
             :apps:security-service:bootJar \
             :apps:admin-service:bootJar
   ```
3. E2E 실행:
   ```bash
   ./gradlew :tests:e2e:test
   ```
4. 실패 시 로그 위치:
   - `tests/e2e/build/reports/tests/test/index.html`
   - `docker compose -p gap-e2e logs <service>` (suite JVM이 아직 살아있는 동안)

## 자주 보는 실패 패턴

| 증상 | 원인 | 조치 |
|---|---|---|
| `Connection refused` 초반 타임아웃 | 서비스 기동 전 Awaitility poll 시작 | `ComposeFixture`의 health wait이 충분히 길어야 함(현재 4분). 시계/Docker 리소스 확인 |
| `INVALID_2FA_CODE` 간헐 | 30s window 경계 | `TotpTestUtil.codeAtOffset(secret, -1)`로 fallback 고려 — 현재는 2s guard sleep로 완화 |
| `DLQ message did not arrive` | 서비스의 `DefaultErrorHandler`가 DLQ 비활성 또는 토픽 명명 다름 | `DlqHandlingE2ETest.DLQ_TOPIC` 조정 및 서비스 kafka error handler 설정 확인 |
| `Port 13306 in use` | 호스트에 다른 MySQL 기동 중 | 충돌 프로세스 종료 또는 `docker-compose.e2e.yml`의 ports 매핑 조정 |

## 격리 정책

- compose 스택은 **suite 1회 기동** (클래스당 recreate 금지 — 5분 예산).
- 테스트 간 상태 격리는 **고유 UUID 생성**(accountId 등)으로 확보한다.
- dev SUPER_ADMIN operator(`00000000-0000-7000-8000-00000000dev1`)는 enrolment
  상태가 1회성으로 compose 스택 수명 동안 유지된다.
- 여러 클래스가 동시에 enroll 하지 않도록 `OperatorSessionHelper`는 JVM 싱글톤
  캐시로 secret을 공유한다.

## 로컬에서 compose만 띄워 보기

시나리오 없이 서비스만 띄우려면:

```bash
docker compose -f docker-compose.e2e.yml -p gap-e2e up -d
docker compose -p gap-e2e ps
curl http://localhost:18085/actuator/health
```

정리:

```bash
docker compose -p gap-e2e down -v
```

## 테스트 작성 시 주의

- `tests/e2e`는 **검증 스위트**이다. 운영 대시보드·부하 테스트용 코드는
  `load-tests/` 모듈에 둔다.
- admin-service 내부 코드(예: `TotpGenerator`)에 의존하지 않는다. TOTP
  계산은 `TotpTestUtil`로 **독립 재구현**되어 있으며, 이는 041c §6의 요건이다.
- 테스트 내 sleep/polling은 **반드시 Awaitility**로. `Thread.sleep`은 TOTP step
  경계 guard처럼 **시간 정렬이 본질**인 곳에만 사용한다.
