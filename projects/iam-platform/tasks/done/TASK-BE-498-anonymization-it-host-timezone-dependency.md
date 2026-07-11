# Task ID

TASK-BE-498

# Title

`AccountAnonymizationSchedulerIntegrationTest` 가 호스트 타임존에 의존한다 — raw `Timestamp.from()` 쓰기 ↔ Hibernate `Instant` 읽기 규약 불일치 (KST 호스트에서 로컬 RED)

# Status

done

# Owner

backend

# Task Tags

- test
- bugfix

---

# Dependency Markers

- **선행 (prerequisite)**: 없음.
- **후속**: 없음.

---

# Goal

`AccountAnonymizationSchedulerIntegrationTest.runAnonymizationBatch_alreadyAnonymized_isNotReprocessed()` 가 **JVM 기본 타임존이 UTC 가 아닌 호스트에서 결정론적으로 실패**한다. CI(Linux, UTC)는 오프셋 0 이라 GREEN 이므로 가려져 있고, KST 개발 호스트에서만 빨갛다.

테스트를 **호스트 타임존에 무관**하게 만든다. 프로덕션 코드 버그가 아니므로 main 코드는 건드리지 않는다.

---

# 근본 원인 (측정으로 확정)

관측된 실패:

```
expected: 2026-06-30T15:02:41Z
 but was: 2026-07-01T00:02:41Z        ← 정확히 +9h (KST = UTC+9)
```

테스트가 **쓰기와 읽기에 서로 다른 타임스탬프 규약**을 쓴다:

| | 경로 | 규약 |
|---|---|---|
| **쓰기** (테스트만) | raw `JdbcTemplate` + `java.sql.Timestamp.from(instant)` | 드라이버가 **JVM 기본 TZ** 로 포맷 → 벽시계 `X+9h` 가 naive `DATETIME` 으로 저장 |
| **읽기** (앱과 동일) | Hibernate `Instant` 매핑 | naive `DATETIME` 을 **UTC 로 해석** → `Instant = X+9h` |

→ 읽은 값이 의도한 값보다 정확히 JVM TZ 오프셋만큼 앞선다. UTC 호스트에서는 오프셋이 0 이라 우연히 통과한다.

**프로덕션은 무사하다** (착수 전 검증 완료):

- account-service `src/main/java` 에 `JdbcTemplate` 도 `java.sql.Timestamp` 도 **없다** — 모든 타임스탬프가 JPA/Hibernate `Instant` 로 일관 처리된다.
- `AccountAnonymizationScheduler.runAnonymizationBatch()` 는 `Instant threshold` 를 **JPQL**(native 아님) `findAnonymizationCandidates(threshold)` 에 바인딩한다 → 쓰기·읽기 규약 동일.

즉 규약 불일치는 **테스트 픽스처에만** 존재한다.

---

# Scope

## In Scope

`projects/iam-platform/apps/account-service/src/test/java/com/example/account/infrastructure/scheduler/AccountAnonymizationSchedulerIntegrationTest.java` 단일 파일:

- `Timestamp.from(...)` 쓰기 2곳을 **읽기 규약과 일치**하는 형태로 교체:
  - line ~189 — `masked_at` (멱등성 가드 픽스처)
  - line ~277 — `setDeletedAt()` 헬퍼 (모든 테스트가 쓰는 유예기간 픽스처)
- 교체 형태: `LocalDateTime.ofInstant(instant, ZoneOffset.UTC)` 를 바인딩한다. Connector/J 는 `LocalDateTime` 을 **TZ 변환 없이 그대로** 기록하므로 naive UTC 벽시계가 저장되고, Hibernate 가 그것을 UTC 로 읽어 **원래 `Instant` 로 정확히 round-trip** 한다 — 호스트 TZ 무관.
- round-trip 이 정확함을 단언해 규약 회귀를 잡는다(현재의 `masked_at` 동등성 단언이 그 역할을 하되, 이제 모든 호스트에서 성립).

## Out of Scope

- **main 코드 변경** — 프로덕션 버그가 아니다(위 검증 참조).
- **`libs/java-test-support` 의 `AbstractIntegrationTest` 변경** — 아래 § Follow-up 참조. 공유 파일이라 전 서비스 IT 에 파급되므로 별도 root task 감이다.
- 다른 서비스의 IT — 이 task 는 관측된 실패 1건에 한정한다.

---

# Acceptance Criteria

- [ ] **AC-1**: `AccountAnonymizationSchedulerIntegrationTest` 4개 테스트가 **KST 호스트(`-Duser.timezone=Asia/Seoul`)에서 GREEN**.
- [ ] **AC-2**: 같은 테스트가 **UTC(`-Duser.timezone=UTC`)에서도 GREEN** — 즉 한쪽을 고치려고 다른 쪽을 깨지 않는다. 두 TZ 를 명시적으로 돌려 확인한다.
- [ ] **AC-3**: `masked_at` 동등성 단언이 **의도한 `Instant` 와 정확히 일치**한다(오프셋 보정·완화가 아니라 round-trip 이 실제로 정확해야 한다). truncation 은 MySQL 정밀도 한계까지만 허용.
- [ ] **AC-4**: 테스트 파일에 `java.sql.Timestamp` import 가 **남지 않는다** — 규약이 한 가지만 존재해야 재발하지 않는다.
- [ ] **AC-5**: `setDeletedAt` 픽스처도 함께 고친다. 지금은 30일 임계까지 24h 여유가 있어 9h 왜곡을 우연히 견디지만, 경계 테스트(예: 30일 ± 수 시간)를 누가 추가하는 순간 TZ 로 flake 된다.
- [ ] **AC-6**: main 코드 diff **0줄**.
- [ ] **AC-7**: account-service `:integrationTest` 전체 GREEN (CI Linux 권위).

---

# Related Specs

> `platform/entrypoint.md` Step 0 first.

- `projects/iam-platform/specs/services/account-service/retention.md` § 2.5 (익명화 배치 — 30일 유예기간)
- `platform/testing-strategy.md`

# Related Contracts

- 없음 (테스트 전용 변경).

---

# Target Service

- `account-service`

# Architecture

Follow `projects/iam-platform/specs/services/account-service/architecture.md`.

---

# Edge Cases

- **읽기 경로가 UTC 로 해석한다는 전제**가 이 수정의 근거다. `LocalDateTime.ofInstant(x, UTC)` 로 쓰고 다시 읽었을 때 `x` 가 그대로 나오는지를 **테스트가 직접 단언**하므로, 전제가 틀리면 조용히 통과하는 게 아니라 실패한다(AC-3).
- MySQL `DATETIME(6)` 정밀도 — `Instant.now()` 의 나노초는 저장되지 않는다. 마이크로초 이하 절삭은 허용하되, **초 단위 이상 차이는 실패**여야 한다(9h 스큐를 잡아야 하므로 관대한 절삭 금지).
- 이 테스트 클래스는 컨테이너를 다른 클래스와 공유한다 — 픽스처는 매번 새 계정(UUID 이메일)을 만들므로 교차 오염은 없다.

---

# Failure Scenarios

- **오프셋을 보정해서 "고친다"** (예: 단언 쪽에 `plus(9, HOURS)` 나 `ZoneId.systemDefault()` 를 끼워넣음) → KST 에서만 통과하고 CI(UTC)에서 깨지거나, 저장된 값이 여전히 틀린 채로 테스트만 초록이 된다. **쓰기 규약 자체를 읽기와 일치시켜야 한다.**
- **단언을 완화해서 "고친다"** (예: 절삭을 시간/일 단위로 키우거나 `isCloseTo` 로 바꿈) → 멱등성 가드(= `masked_at` 이 전진하지 않았다)가 더 이상 증명되지 않는다. AC-3 가 가드.
- `setDeletedAt` 를 안 고치고 `masked_at` 만 고침 → 잠복 결함 잔존. 30일 경계 근처 테스트가 추가되는 순간 TZ flake 로 부활한다. AC-5 가 가드.

---

# Follow-up (이 task 범위 밖 — 별도 판단 필요)

**테스트/프로덕션 JDBC 연결 설정 divergence**: 프로덕션 `application.yml` 은 JDBC URL 에 `serverTimezone=UTC` 를 명시하지만, IT 의 datasource URL 은 `MySQLContainer.getJdbcUrl()`(공유 `AbstractIntegrationTest` 의 `@DynamicPropertySource`)에서 오며 **그 파라미터가 없다**. 즉 IT 는 프로덕션과 **다른 드라이버 TZ 동작**을 검증한다.

정렬하면 이 종류의 버그가 전 서비스에서 구조적으로 사라지지만, `libs/java-test-support` 는 **공유 파일**이라 모든 서비스의 IT 에 파급되고(잠복 TZ 가정이 여러 서비스에서 동시에 드러날 수 있다) monorepo-level root task 가 필요하다. 본 task 는 관측된 실패에만 한정하고, 이 정렬은 **신호가 더 모이면** 별도 티켓으로 판단한다(verify-then-act).
