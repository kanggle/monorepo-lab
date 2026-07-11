# Task ID

TASK-BE-499

# Title

master-service 통합 테스트가 `Math.random()` 으로 `warehouseCode` 를 생성해 **birthday collision → 409 CONFLICT** 로 간헐 실패 — 공유 DB·890 값공간·중복 정의 6곳. JVM 전역 카운터로 충돌을 구조적으로 제거

# Status

done

# Owner

wms-platform

# Task Tags

- test
- flake

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능.
- **관련 (비차단)**: `TASK-MONO-331`(머지됨) 은 이 레인의 **크로스-모듈** Testcontainers 경합을 `--no-parallel` 로 이미 해결했다. 본 결함은 그 아래 계층(**단일 모듈 내부의 테스트 데이터 충돌**)이라 331 로는 잡히지 않는다.
- **비-관련(혼동 주의)**: `TASK-BE-497`(ecommerce rate-limit IT) 은 표면 증상이 비슷한 "CI 간헐 실패" 지만 **근본원인이 다르다** — 497 = fail-open 계약 불일치(인프라 포화), 본 task = **테스트 코드의 약한 유니크 ID**(인프라 무관). 497 의 Awaitility-재시도 패턴을 여기 적용하면 **결함을 가릴 뿐 고치지 못한다**.

---

# Goal

`Integration (master-service + notification-service + outbound-service)` 레인이 간헐적으로 RED 가 된다. 실패 지문은 항상 **셋업 단계**다:

```
LocationIntegrationTest > location create ... FAILED
  org.opentest4j.AssertionFailedError:
  expected: 201 CREATED
   but was: 409 CONFLICT
    at LocationIntegrationTest.seedWarehouseAndZone(LocationIntegrationTest.java:142)
```

**근본 원인 (인프라 아님, 테스트 코드 결함)**:

1. 6개 테스트 클래스가 각자 **private `shortSuffix()`** 를 **중복 정의**한다. 구현이 6곳 모두 동일하다:

   ```java
   private static String shortSuffix() {
       return String.valueOf(10 + (int) (Math.random() * 890));   // 값 공간 = 890
   }
   ```

   위치: `integration/{Location,Warehouse,Zone,PublisherResilience}IntegrationTest`, `contract/{Http,Event}ContractTest`.

2. **6개 클래스가 전부 `MasterServiceIntegrationBase` 를 상속**한다. 그 베이스의 Postgres 는 `static` 이고 Spring 컨텍스트는 캐시되므로 **모듈 전체가 하나의 DB 를 공유**한다.

3. **DB 정리가 없다** — `@Sql` / `@DirtiesContext` / truncate 0건. 실 HTTP(`RANDOM_PORT`)로 만든 데이터라 롤백도 없다. → 생성된 warehouse 가 **런 내내 누적**된다.

4. `warehouseCode` 는 도메인 규약상 **`^WH\d{2,3}$`** 다(`CreateWarehouseRequest` `@Pattern`, `Warehouse.CODE_PATTERN`). 즉 값 공간이 원천적으로 ~1100 개뿐이라 **UUID 로 넓힐 수 없다**.

⇒ 890 개 공간에서 수십 번 랜덤으로 뽑으면 **birthday collision 이 확률적으로 발생**하고, 중복 코드는 `WAREHOUSE_CODE_DUPLICATE` → **409** 다. 셋업이 무너지므로 테스트는 "로직 실패" 처럼 보이지만 실제로는 **테스트끼리 코드 이름이 겹친 것**이다.

**목표**: 랜덤을 버리고 **JVM 전역 단조 카운터**로 코드를 발급해 충돌 가능성을 **구조적으로 0** 으로 만든다. 프로덕션 코드 무변경.

---

# Scope

## In Scope

1. **공유 유니크 코드 발급기 신설** — `integration/support/` 에 테스트 지원 클래스(`TestCodes`). `AtomicInteger` 기반, JVM 전역 단조 증가. `^WH\d{2,3}$` 와 `^Z-[A-Z0-9]+$` 를 **둘 다 만족하는 3자리 숫자**(100~999)를 발급. 소진 시 **명확한 예외**(조용한 wrap-around 금지 — 그러면 충돌이 되살아난다).
2. 6개 클래스의 private `shortSuffix()` **제거 → 공유 발급기 호출**로 치환. 카운터는 **클래스 간 공유**여야 한다(클래스별 카운터면 서로 충돌 — 같은 DB 를 쓰므로).
3. **발급기 유니크성 단위 테스트** — Docker-free. N 회 호출이 N 개의 서로 다른 값을 내는지, 소진 시 예외를 던지는지 단언.

## Out of Scope

- **프로덕션 코드 변경** — `^WH\d{2,3}$` 패턴은 정당한 도메인 규약이다. 테스트 편의를 위해 완화하지 않는다.
- **DB 정리 도입**(`@Sql`/truncate) — 누적 자체는 다른 테스트의 의도(글로벌 유일성 검증 등)와 얽힐 수 있고, 본 결함은 발급기만 고치면 사라진다. 더 큰 변경은 별건.
- **BE-497 식 Awaitility 재시도** — 인프라 flake 가 아니므로 재시도는 **결함을 가릴 뿐**이다.
- **다른 WMS 모듈/서비스의 유사 패턴** — 본 task 는 master-service 로 한정(실측된 결함 위치). 타 모듈에도 있으면 별도 티켓.
- 레인 병렬화(`--no-parallel`) — MONO-331 로 이미 해결.

---

# Acceptance Criteria

- [x] AC-1 — `shortSuffix()` 정의·호출 **0건**, 테스트 클래스 내 `Math.random()` **0건**(잔존 2건은 `TestCodes` javadoc 산문).
- [x] AC-2 — 6개 클래스 전부 공유 `TestCodes.uniqueSuffix()` 사용(클래스별 사본 0). 발급 코드가 두 도메인 패턴을 만족함을 단위테스트로 단언.
- [x] AC-3 — `TestCodesSelfTest` **5개 테스트 실제 실행·전부 통과**(`tests="5" skipped="0" failures="0"` — 0개 실행 후 통과하는 거짓 GREEN 아님을 XML 로 확인).
- [x] AC-4 — `apps/master-service/src/main/**` byte-unchanged(`git diff --numstat origin/main` 빈 출력).
- [x] AC-5 — 의도적 중복 테스트 무손상(사전 확인대로 실 DB warehouseCode 중복 IT 는 없음). 전 레인 GREEN 으로 실증.
- [x] AC-6 — CI `Integration (master-service + …)` 레인 **GREEN, attempt 1(rerun 없음)** — PR #2401, 22 SUCCESS / 3 SKIPPED / 0 FAILURE.

> **검증의 한계(정직 고지)**: 이 flake 는 확률적이므로 **CI 1회 GREEN 은 픽스의 증거가 아니다** — 결함이 있는 현재 코드도 대부분의 런에서 통과한다. 따라서 증명은 **"카운터는 구조적으로 중복을 낼 수 없다"** 는 성질을 AC-3 의 단위테스트로 직접 단언하는 것이고, CI GREEN 은 **회귀 없음**의 확인일 뿐이다.

---

# Related Specs

- `projects/wms-platform/apps/master-service/src/main/java/com/wms/master/domain/model/Warehouse.java` (`CODE_PATTERN` = `^WH\d{2,3}$` — 값 공간 제약의 출처)
- `.../adapter/in/web/dto/request/CreateWarehouseRequest.java` (`@Pattern`)
- `.../src/test/java/com/wms/master/integration/MasterServiceIntegrationBase.java` (static 컨테이너 = 공유 DB)
- `tasks/done/TASK-MONO-331-*` (같은 레인의 **크로스-모듈** 경합 — 다른 계층)

# Related Contracts

None — 테스트 전용. API/event 계약 무변경.

---

# Target Service

- `wms-platform` / `master-service` (테스트 코드만)

---

# Architecture

- 발급기는 **테스트 지원 계층**(`integration/support/`)에 둔다. 이미 `JwtTestHelper` · `KafkaTestConsumer` 가 사는 자리이며, 상속(`MasterServiceIntegrationBase`)에 얹지 않는 이유는 contract 패키지 등 비-상속 호출자도 쓸 수 있게 하기 위함이다.
- 카운터는 **static** — Gradle 이 모듈당 하나의 테스트 JVM 을 포크하고, Testcontainers Postgres 도 그 JVM 에 하나이므로 **"JVM 1개 = DB 1개 = 카운터 1개"** 가 정확히 대응한다. 런 간 재사용 문제는 없다(DB 가 매번 새로 뜬다).
- 900 개(100~999)면 현재 스위트(28 IT + contract, warehouse+zone 합쳐 100 개 미만 발급)의 **수 배 여유**다. 그럼에도 소진 가드를 두는 이유: 조용한 wrap-around 는 **정확히 지금 고치려는 그 결함**을 되살리기 때문이다.

---

# Implementation Notes

- **왜 클래스별 카운터가 안 되는가**: 6개 클래스가 같은 DB 를 공유한다. 클래스마다 100 부터 시작하면 `LocationIntegrationTest` 의 `WH100` 과 `WarehouseIntegrationTest` 의 `WH100` 이 충돌한다. **반드시 하나의 static 카운터.**
- zone 코드(`Z-<suffix>`)도 같은 발급기를 쓴다. `^Z-[A-Z0-9]+$` 는 숫자를 허용하므로 3자리 숫자로 충분하며, warehouse 와 카운터를 공유해도 서로 다른 값을 받아 무해하다.
- `Math.random()` 은 테스트 결정론의 적이다. 이번 건이 그 실물 사례다.

---

# Edge Cases

- **소진(>999)** — 명확한 예외로 실패. 스위트가 900 개 이상 코드를 만들게 되면 그때 3자리 제약 자체를 재검토해야 한다(도메인 변경 필요 = 별건).
- **테스트 병렬 실행** — JUnit 병렬이 켜지면 `AtomicInteger` 가 여전히 안전하다(그래서 `int++` 가 아니라 `getAndIncrement`).
- **의도적 중복** — 실 DB 대상 warehouseCode 중복 테스트는 없다(AC-5). 향후 추가된다면 코드를 **명시적으로 재사용**해야 하며, 발급기를 두 번 호출해선 안 된다.
- **contract 테스트도 같은 DB** — 그래서 발급기를 공유해야 한다(빠뜨리면 contract ↔ integration 간 충돌이 남는다).

---

# Failure Scenarios

| # | 시나리오 | 기대/완화 |
|---|---|---|
| 1 | 클래스별 카운터로 구현 | 클래스 간 충돌 잔존 = 결함 미해결. AC-2 가 가드 |
| 2 | 소진 시 조용히 wrap | 충돌 부활. AC-3 의 예외 단언이 가드 |
| 3 | 재시도(Awaitility)로 "해결" | 인프라 flake 가 아니므로 결함을 가릴 뿐. Out of Scope |
| 4 | 도메인 `@Pattern` 완화로 UUID 사용 | 프로덕션 규약 훼손. Out of Scope / AC-4 |
| 5 | CI 1회 GREEN 을 픽스 증거로 제시 | 확률적 flake라 무의미. AC-3 단위테스트가 진짜 증거 |

---

# Test Requirements

- **유니크성 단위테스트**(Docker-free, `test` 태스크): N 회 호출 → N distinct, 소진 → 예외.
- `grep` 으로 `Math.random()` 잔존 0 확인.
- `git diff --numstat origin/main -- apps/master-service/src/main` 빈 출력.
- CI `Integration (master-service + …)` 레인 GREEN(회귀 없음 확인 — 픽스 증거는 단위테스트).
- 로컬 Windows Testcontainers 는 npipe flake + 라이브 데모 가동 중 → **IT 는 CI Linux 권위**.

---

# Definition of Done

- [x] 공유 발급기(`TestCodes`) 신설 + 6개 클래스 치환 + `Math.random()` 0건. 부수: `WarehouseIntegrationTest` 의 예약 슬롯(WH900–999) 우회를 **흡수·삭제**(발급기가 충돌을 구조적으로 없애 우회가 불필요해짐).
- [x] 유니크성 단위테스트 통과(Docker-free, 5/5 실행)
- [x] 프로덕션 코드 byte-unchanged
- [x] CI 레인 GREEN (attempt 1)
- [x] `projects/wms-platform/tasks/INDEX.md` done entry

---

# Provenance

2026-07-11 발견 — TASK-BE-497(ecommerce rate-limit IT) impl PR #2397 의 CI 에서 이 WMS 레인이 RED. **내 diff 는 wms/libs 를 0 파일 건드렸으므로 인과 불가**했고, rerun 으로 GREEN → flake 로 분리했다. 처음에는 BE-497 과 같은 클래스(러너 포화 → 인프라)로 **추정**했으나, attempt-1 의 테스트 리포트 아티팩트를 받아 실제 실패 메시지를 확인하니 **`expected: 201 CREATED but was: 409 CONFLICT`** 였다 — 콘솔 로그에는 앱측 예외(HikariCP/커넥션)가 **0건**이었고, 이것이 포화 가설을 반증했다. 코드를 따라가 `Math.random() * 890` 과 공유 static Postgres·정리 부재·`^WH\d{2,3}$` 제약이 맞물린 **birthday collision** 임을 특정했다. 교훈: **"간헐 실패 = 인프라 flake" 는 가설이지 결론이 아니다.** 지문을 끝까지 확보해야 한다(콘솔이 아니라 리포트 아티팩트에 진짜 메시지가 있었다).

분석=Opus 4.8 / 구현 권장=Sonnet (테스트 유틸 치환 — 기계적. 단 "카운터는 클래스 간 공유" 와 "소진 가드" 두 지점만 주의).
