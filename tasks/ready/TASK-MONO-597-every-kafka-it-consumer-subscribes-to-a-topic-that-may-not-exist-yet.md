# Task ID

TASK-MONO-597

# Title

🔴 **Kafka IT 소비자 전부가 `subscribe()` 로 «아직 없을 수도 있는» 토픽을 기다린다** —
`assign()` + `seekToBeginning` 을 쓰는 파일이 **22개 중 0개**다. 간헐 RED 하나가 관측됐고,
🔴 **원인은 아직 확정되지 않았다.**

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- flake
- testing
- cross-project

---

# Goal

`main` 의 한 커밋에서 `AdjustmentTransferIntegrationTest` 가 30초 폴 마감에 걸려 실패했고,
**같은 커밋의 재실행은 통과했다.** 이 티켓은 그것을 «flake 였다» 로 닫으려는 것이 **아니다** —
관측이 증발하지 않게 붙잡아 두고, **모집단이 22개 파일이라는 사실**을 먼저 세운 뒤에
고칠지 말지를 정하기 위한 것이다.

🔴 **«flake=인프라» 는 판정이 아니라 가설이다.** 이 티켓의 AC-0 은 고치는 일이 아니라
**표본을 늘리는 일**이다.

---

# Context — 실측 (2026-08-28 UTC, `origin/main` = `f10ae43a6`)

## ① 관측된 것 — 한 커밋, 세 번의 실행, 2 green / 1 red

`TASK-MONO-592` 의 impl 머지 커밋 `1a7f7b54f` 에서:

| 실행 | 이 잡의 결과 |
|---|---|
| PR #3497 의 PR 런 | **success** (3m2s) |
| `main` push 런 (`33151761319`) | **failure** |
| 같은 런 `--failed` 재실행 | **success** |

실패 지점:

```
AdjustmentTransferIntegrationTest > POST /adjustments → inventory.adjusted on Kafka FAILED
    java.lang.AssertionError at AdjustmentTransferIntegrationTest.java:84
25 tests completed, 1 failed
```

84행은 `assertThat(envelope).isNotNull()` 이다 — 즉 **어서션이 틀린 게 아니라
`pollOne(consumer, TOPIC_ADJUSTED, 30)` 이 30초 안에 아무것도 못 받고 `null` 을 냈다.**

🔵 **인과적으로 그 커밋의 diff 와 무관하다**: `1a7f7b54f` 는 빈 `.gitkeep` 6개 + bash 스크립트
1개 + `ci.yml` + 마크다운이고 Java·gradle·Kafka 를 **한 줄도** 건드리지 않는다. 잡이 돈 이유는
`code-changed` 필터가 `**/*.sh` 와 `**/*.yml` 을 포함하기 때문이다.

🔴 **그러나 «무관하다» 는 «원인을 안다» 가 아니다.** 아래 ③ 은 기전 *후보*일 뿐이다.

## ② 🔴 그 잡의 main 이력 — 표본이 **얇다**. 이 숫자로 성질을 주장하면 안 된다

최근 main CI 런 20개 중 이 잡이 **실제로 돈 것은 5개**뿐이다(나머지는 path-filter 로 skip):

| commit | 결과 |
|---|---|
| `8e43a4db9` | success |
| `7465c8499` | success |
| `4e750c183` | success |
| `37e234616` | success |
| `1a7f7b54f` | **failure** → 재실행 success |

⇒ **관측 6~7건 중 red 1건.** 🔴 이것은 «빈도» 가 아니다. 표본이 5개인데 비율을 말하면
[[feedback_local_proves_behaviour_not_performance]] 의 함정이다. **AC-0 이 표본을 만든다.**

🔴 **`skip` 을 `pass` 로 읽지 마라 — 이 티켓을 쓰는 중에 실제로 한 번 그렇게 읽었다.**
다음 커밋 `f10ae43a6` 이 초록이길래 «같은 테스트가 다음 커밋에서 통과했다» 고 적었는데,
그 커밋에서 이 잡은 **skip 이었다**(markdown-only diff). 즉 그 시점 main 의 이 잡에 대한
마지막 관측은 여전히 **RED** 였다. 판정을 만든 것은 **재실행**이지 다음 커밋이 아니다.

## ③ 🔵 기전 *후보* — 확정 아님. 그러나 모집단이 이것 때문에 커진다

`AdjustmentTransferIntegrationTest:271-281`:

```java
props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(List.of(topic));      // ← assign() 아님
```

생산 측은 `OutboxPublisher` 의 `@Scheduled(fixedDelayString = "…:500")` 이다. `subscribe()`는
**그룹 조인/리밸런스가 끝나야 할당을 받고**, 토픽이 그 시점에 아직 생성 전이면 빈 할당을
받은 뒤 메타데이터 갱신을 기다린다. 새 그룹 + `earliest` 라 보통은 곧 따라잡지만, 러너가
포화되면 30초 마감이 그 창을 못 덮을 수 있다.

🔴 **이것이 원인이라는 증거는 없다.** 로그에 리밸런스 타임라인이 남지 않았고, 지금 있는
것은 «30초 안에 레코드 0건» 뿐이다. 다른 후보 — 아웃박스 폴러가 그 사이 안 돌았다,
컨테이너 기동이 느렸다, `@AfterEach` 의 `TRUNCATE` 가 이웃 테스트와 얽혔다 — 를 배제하지
못한다. [[feedback_a_verifiable_mechanism_is_not_the_cause]]

## ④ 🔴 모집단 — **이건 테스트 하나짜리 문제가 아니다**

```
projects/*/apps/*/src/test 에서 GROUP_ID_CONFIG 를 쓰는 파일   = 22
그 중 랜덤 group id                                          = 17
assign() + seekToBeginning 을 쓰는 파일                       =  0
```

`AbstractLedgerIntegrationTest` · `AbstractLogisticsIntegrationTest` · `KafkaTestSupport` ·
`KafkaTestConsumer` 같은 **공유 베이스**가 그 안에 있다 ⇒ 한 곳을 고치면 여러 서비스가 같이
움직이고, 반대로 한 곳만 고치면 형제가 낙오한다.
[[feedback_grep_the_siblings_before_fixing_it_yourself]]

---

# Acceptance Criteria

## AC-0 — 🔴 **고치기 전에 표본을 만든다. 이것이 이 티켓의 본체다**

- ② 의 표를 **다시** 만들되 20런이 아니라 **이 잡이 실제로 돈 런만 N≥30** 이 되도록 거슬러
  올라간다. 🔴 **`skip` 을 분모에 넣지 마라** — ②의 실수가 그것이다. 분모는 «돈 런» 이다.
- 같은 방식으로 **④ 의 22개 파일이 속한 다른 통합 잡들**도 센다. 이 실패 모양(폴 마감 →
  `null`)이 `inventory` 에만 있는지, 여러 잡에 흩어져 있는지가 **모집단 질문**이다.
- 🔵 **양성 대조군**: 세는 술어가 살아 있는지 먼저 증명한다 — `1a7f7b54f` 의 원본 실패가
  반드시 잡혀야 한다. 0건이 나오면 그것은 «없다» 가 아니라 **술어가 죽은 것**이다.
  [[env_empty_detector_output_is_not_absence]] [[env_a_resolver_that_cannot_answer_returns_a_plausible_one]]
- 🔴 이 티켓을 쓰며 `jq` 정규식 이스케이프가 깨져 **20행 전부 `<absent>`** 가 나왔고 그것이
  데이터처럼 보였다. **집계 스크립트는 양성 대조군을 통과한 뒤에만 믿어라.**

**AC-0 의 출력이 «red 0건» 이면 이 티켓은 여기서 닫는다** — 관측 1건은 고칠 근거가 아니다.
그때의 산출물은 코드 변경이 아니라 **이 관측의 기록**이다.

## AC-1 — 재현을 시도한다 (실패해도 그것이 결과다)

- 로컬/CI 에서 이 잡을 반복 실행해 red 를 다시 만든다. Docker 가 필요하다
  ([[project_testcontainers_docker_desktop_blocker]]).
- 🔴 **못 만들면 «재현 불가» 로 적는다.** ③ 의 후보를 «확인됨» 으로 승격시키지 마라.
- 🔵 재현되면 그 실행에서 **리밸런스/할당 타임라인을 로그로 남긴다** — 그것이 ③ 을
  가설에서 원인으로 바꿀 수 있는 유일한 증거다.

## AC-2 — 고칠 경우: **모집단 전체를 한 번에**, 아니면 손대지 마라

AC-0/AC-1 이 고칠 근거를 만들었을 때만 진행한다.

- 🔴 ④ 가 세듯 shape 이 **22개 파일에 균일**하다. 한 파일만 `assign()` 으로 바꾸면
  **형제 21개가 낙오 명단**이 된다. 공유 베이스(`KafkaTestSupport` 등)부터 고치고
  그것을 쓰지 않는 파일을 **명시적으로 열거**한다.
- 🔴 **최적화/단축이 아니라 «더 기다리는» 쪽으로 실패해야 한다** — 마감을 늘리는 변경은
  느려질 뿐 거짓 초록을 만들지 않지만, `assign()` 전환은 **파티션 수를 잘못 읽으면
  조용히 아무것도 안 읽는다.** 그 경우 테스트는 통과가 아니라 **실패**해야 한다.
  [[feedback_an_optimisation_must_fail_toward_more_work]]
- 🔵 **음성 대조군**: 고친 뒤 그 테스트가 여전히 **진짜 결함을 잡는지** 확인한다 —
  이벤트 발행을 일부러 끄고 RED 가 나오는지 찍어라. 안 그러면 «절대 실패 안 하는 테스트» 를
  만든 것이다. [[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]

## AC-3 — 어디에 기록되는가

이 저장소에는 flake 추적 대장이 **없다**(`ready/` grep 0건). AC-0 의 표가 갈 자리를
**하나 정한다** — 이 티켓 본문 / `platform/testing-strategy.md` / 별도 대장 중 하나.
🔴 정하지 않으면 다음 red 도 똑같이 증발한다. 이 티켓이 존재하는 이유가 그것이다.

---

# Related Specs

- `projects/wms-platform/apps/inventory-service/src/test/java/com/wms/inventory/integration/AdjustmentTransferIntegrationTest.java` — 271-295 (`newConsumer` / `pollOne`)
- `projects/wms-platform/apps/inventory-service/src/main/java/com/wms/inventory/adapter/out/messaging/OutboxPublisher.java` — `@Scheduled(fixedDelay 500ms)`
- `platform/testing-strategy.md` — AC-3 후보 자리
- `.github/workflows/ci.yml` — `code-changed` 필터가 `**/*.sh` · `**/*.yml` 을 포함(①이 왜 돌았나)

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| AC-0 이 red 0건을 낸다 | 🔵 **정상 종료다.** 관측 1건으로 22개 파일을 건드리지 않는다 — 기록만 남기고 닫는다 |
| AC-0 이 술어 버그로 0건을 낸다 | 🔴 양성 대조군(`1a7f7b54f`)이 반드시 잡혀야 한다. 안 잡히면 «없음» 이 아니라 **술어 사망** |
| `skip` 을 분모에 넣는다 | 🔴 이 잡은 path-filter 로 대부분 skip 이다. 분모는 «돈 런» — ②가 실제로 틀렸던 지점 |
| 재현이 안 된다 | AC-1 이 «재현 불가» 를 결과로 인정한다. ③ 을 승격시키지 마라 |
| 한 파일만 고친다 | 🔴 형제 21개가 낙오. AC-2 가 공유 베이스부터 요구한다 |
| 마감을 30초 → 120초로만 늘린다 | 🔵 «더 기다리는» 쪽 실패라 안전하지만 **원인을 안 고친다.** 임시방편이면 그렇게 적어라 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| «flake 였다» 로 닫는다 | 다음 red 도 증발하고 아무도 빈도를 모른다 | AC-0 — 표본이 먼저 |
| 표본 없이 22개 파일을 고친다 | 큰 diff, 검증 불가, 새 결함 유입 | AC-0 이 «red 0건이면 닫는다» 를 명시 |
| ③ 을 원인으로 적는다 | 틀린 원인이 문서에 화석화 | AC-1 — 리밸런스 타임라인 없이는 가설 |
| 한 파일만 `assign()` 으로 바꾼다 | 형제 21개 낙오, 다음 세션이 다시 발견 | AC-2 — 공유 베이스부터 |
| 마감만 늘려 초록을 산다 | 테스트가 아무것도 안 지키게 될 수 있다 | AC-2 음성 대조군 |
| 집계 스크립트를 검증 없이 믿는다 | 🔴 `<absent>` 20행이 데이터로 보인다(실제 발생) | AC-0 양성 대조군 |
