# Task ID

TASK-SCM-BE-058

# Title

`InboundExpectedLoopE2ETest` 가 **main 의 nightly 를 간헐적으로 빨갛게** 만든다 — 이벤트는 발행·수신됐는데 **테스트의 지역 누산기가 그것을 버린다**(`drain()` 은 버퍼를 비운다)

# Status

review

# Owner

scm-platform

# Task Tags

- bug
- ci
- integration

---

# 배경 — 다른 티켓(`TASK-ERP-BE-041`) 작업 중 main 을 확인하다 발견

`main` 의 nightly 워크플로(`Nightly E2E (full-stack web-store + 5 backend full suites +
cross-project fulfillment)`)가 **여러 번 연속 빨갰다.** 실패 잡은 매번 같다:

```
E2E full (scm-platform v1 cross-service, Testcontainers) / e2e
  @ Build service images, run e2e suite        ← 실제 실행 단계에서의 실패
```

**대표 실행** `31116904911`(`078d230c6`, 2026-08-06T15:40Z):

```
InboundExpectedLoopE2ETest > two POs to the same warehouse code
                             -> two inbound-expected events with that one code   FAILED
  Expecting actual not to be null
11 tests completed, 1 failed
```

## 🔴 "flake" 는 아직 **가설**이다 — 확정하지 말 것

`main` 의 nightly 이력(같은 날):

| run | commit | 결과 | 실패 잡 |
|---|---|---|---|
| `31116904911` | `078d230c6` | ❌ | scm e2e (+ Actions 장애 3건) |
| `31116442999` | `c2ea87e06` | ❌ | **전부 `Set up job`** = GitHub Actions 장애 |
| `31110230964` | `5e2ee9d52` | ❌ | scm e2e (실제 실행 단계) |
| `31102486520` | `6497d4e77` | ✅ | — |
| `31100763173` | `86c54a1a4` | ✅ | — |
| `31100453504` | `353a7c54e` | ❌ | scm e2e (실제 실행 단계) |
| `31095324213` | `8f78b9589` | ❌ | scm e2e (실제 실행 단계) |

🔵 **판정에 필요한 두 가지 구분이 이미 서 있다:**

1. `31116442999` 의 빨강은 **이 결함이 아니다** — `Failed to resolve action download info.
   Error: Service Unavailable` 로 잡이 `Set up job` 에서 죽었다(GitHub Actions 장애).
   **"nightly 빨강" 을 한 덩어리로 세면 안 된다.**
2. 나머지 넷은 **실제 실행 단계**에서 죽었고, 성공 둘이 그 사이에 끼어 있다 ⇒ 결정론적
   회귀는 아니다. 그러나 [[env_ci_flake_is_a_hypothesis_not_a_verdict]] — **간헐적이라는
   사실이 "인프라 탓" 을 증명하지 않는다.**

## 관측된 정황 (원인 아님 — 착수자가 확정할 것)

- 실패 클래스는 매번 `InboundExpectedLoopE2ETest`(메서드는 실행마다 다를 수 있음).
- 단언 형태는 `Expecting actual not to be null` — **기다리던 이벤트가 안 왔다**는 뜻이지
  값이 틀렸다는 뜻이 아니다.
- 같은 로그에 `scm-e2e.demand-planning - STDOUT: Caused by: java.net.ConnectException:
  Connection refused` 가 있다.
- 🔵 그리고 **OTLP export 실패**(`Failed to connect to localhost/…:4318`)가 전 서비스에서
  쏟아진다 — 이건 e2e 에 collector 가 없어서 나는 정상 잡음일 가능성이 높다. **원인으로
  오인하지 말 것**(대조군: 성공한 실행에도 같은 줄이 있는지 먼저 확인하라).

# Goal

`main` 의 nightly 가 이 테스트 때문에 빨개지지 않는다. 그리고 **왜 안 왔는지**가
기록된다 — "재시도하니 통과했다" 는 닫는 근거가 아니다.

# Scope

## In Scope

- `projects/scm-platform/tests/e2e/` 의 `InboundExpectedLoopE2ETest`
- 그 테스트가 의존하는 컨슈머 파티션 할당 / 대기 조건
- 필요 시 nightly 워크플로의 해당 잡 설정

## Out of Scope

- GitHub Actions 장애로 인한 `Set up job` 실패 — 제품 결함 아님
- 다른 프로젝트의 e2e 스위트

# Acceptance Criteria

- [x] **AC-0 (재현 + 모집단 재확인)** — `main` 최근 30 실행 재계수: **실패 16 / 성공 13**
      (≈55% 실패). 실패 잡은 매번 `E2E full (scm-platform v1 cross-service, Testcontainers)`
      의 **실제 실행 단계**(`Build service images, run e2e suite`)이고, `Set up job` 인프라
      실패는 따로 세어 분리했다. 🔴 **같은 커밋 `078d230c6` 이 두 번 실패**했으므로 커밋
      특정도 아니다 ⇒ 간헐이 맞다
- [x] **AC-1 (원인) — 확정. 그런데 이 AC 가 제시한 두 갈래 중 어느 쪽도 아니다.**
      AC-1 은 *"발행이 없었는가 / 발행은 됐는데 컨슈머가 못 받았는가"* 를 물었다.
      정답은 **제3의 것**이다: **발행됐고, 컨슈머가 정상 수신했고, 테스트가 그걸 버렸다.**

      `KafkaTestConsumer.drain()` 은 문서 그대로 *"returns all records buffered so far,
      **clearing the buffer**"* — 한 번 건네준 레코드는 컨슈머에서 영구히 사라진다.
      그런데 `awaitEnvelope` 는 누산기를 **호출마다 새로 만드는 지역 변수**로 두고 있었다:

      ```java
      JsonNode payloadA = awaitInboundExpected(consumer, poA);   // ① drain() → A,B 둘 다 가져감
      JsonNode payloadB = awaitInboundExpected(consumer, poB);   // ② 빈 버퍼를 drain → 30s 타임아웃
      ```

      ①이 A·B 를 **같은 배치**로 빼가면 B 는 ①의 지역 리스트에만 남고 스코프와 함께 버려진다.
      ②는 재전달받지 못한다.

      **관측된 모든 성질이 이 하나로 설명된다** — 이게 이 진단의 근거다:
      | 관측 | 이 기전이 설명하는가 |
      |---|---|
      | 간헐적(≈55%) | ✅ 두 이벤트가 **같은 배치**에 들어와야 발생 |
      | 실패가 **항상 두-이벤트 테스트** | ✅ 단일 await 는 찾는 것을 잃을 수 없다 |
      | 단일 이벤트 테스트는 **한 번도** 실패 없음 | ✅ 같은 이유 |
      | 두 메서드가 번갈아 실패(3/5, 2/5) | ✅ 둘의 모양이 동일 |
      | `Expecting actual not to be null` (둘 중 **하나**) | ✅ 두 번째 await |
      | 두 PO 를 **모두** CONFIRMED 로 몬 뒤 await 시작 | ✅ 동시 배치 확률 최대화 |
- [x] **AC-2 (대조군) — 두 용의자 모두 원인이 아니다.** 성공 실행 `31102486520` 과 대조:
      🔴 **먼저 계측이 도는지부터 확인했다**(`scm-e2e.procurement` 초록 1865줄 / 빨강 2006줄
      — 양쪽에 있으므로 **유효한 대조군**이다. 같은 세션의 `TASK-MONO-516` 에서는 초록 로그에
      컨테이너 로그가 0줄이라 대조 자체가 불가능했다).
      | 패턴 | 초록 | 빨강 |
      |---|---|---|
      | `Connection refused` | 78 | 130 |
      | OTLP `Failed to connect to localhost` | 117 | 195 |
      둘 다 **성공 실행에도 있다** ⇒ 원인이 아니다. 티켓이 예측한 그대로다
- [x] **AC-3 (수정 + 가드) — 타임아웃은 건드리지 않았다.** 누산기를 테스트 스코프 필드로
      올렸다(3줄). 타임아웃 증가는 이 결함에 **아무 효과가 없다** — 레코드는 늦게 오는 게
      아니라 **이미 버려졌다**. 🔵 **형제 파리티가 이 판단을 뒷받침한다**: 같은 헬퍼를 쓰는
      fan `ArtistAndPostFlowE2ETest` 는 await 3개가 누산기 **하나를 공유**하고,
      wms `GatewayMasterE2ETest` 는 *"The accumulator is declared OUTSIDE the Awaitility
      lambda so it persists across polling cycles"* 라고 **이유까지 주석에 적어 뒀다.**
      scm 만 await 를 헬퍼로 뽑으면서 누산기를 함께 안으로 넣었다 — **추상화가 결함을 만들었다.**
      전수 조사 결과 이 패턴의 낙오자는 `InboundExpectedLoopE2ETest` **하나뿐**이다
      (나머지 후보는 전부 단일 await 이거나 이미 누산기를 공유한다)
- [ ] **AC-4 (연속 초록)** — 수정 후 nightly **연속 3회** 초록 확인. ⏳ **머지 후에만 판정 가능.**
      🔴 기저 실패율이 ≈55% 이므로 3연속 초록의 우연 확률은 ≈9% — 3회는 최소선이고
      더 볼수록 좋다

# Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `projects/scm-platform/specs/contracts/events/` — `scm.procurement.inbound-expected.*`

# Related Contracts

- `scm.procurement.inbound-expected.third-party.v1`

# Edge Cases

- 🔴 **cross-project 토픽이 섞여 있다** — 로그에 `wms.inventory.received.v1` 등
  wms 토픽이 같은 브로커에 미리 생성된다(`Pre-created cross-project wms.inventory.*
  topics for e2e`). 컨슈머 그룹/파티션 할당이 그 영향을 받는지 확인할 것
- 러너의 자원 압박(이미지 빌드 + 다중 컨테이너)이 타이밍을 바꾼다 — 로컬 재현은
  **권위가 아니다**

# Failure Scenarios

- **"flake 다" 로 닫고 재실행** → 같은 빨강이 다음 주에 돌아오고, 그 사이 **진짜 회귀가
  이 빨강에 묻힌다.** 이것이 이 티켓의 존재 이유다
- **타임아웃만 늘려 초록** → AC-3 이 실측 근거를 요구한다
- **1회 초록으로 닫음** → AC-4 가 막는다

# 구현 기록

## 고친 것 (1파일 · 3줄)

`InboundExpectedLoopE2ETest`
- `drained` 를 테스트 스코프 필드로 도입(JUnit 기본 PER_METHOD 라 명시적 reset 불필요)
- `awaitEnvelope` 의 `List<...> seen = new ArrayList<>()` → `= drained`
- 두 곳에 **왜** 그래야 하는지(= `drain()` 이 버퍼를 비운다) javadoc 으로 남김.
  기존 형제 주석들은 *"across polling cycles"* 까지만 말한다 — 이 결함이 요구한 것은
  **across await calls** 라는 더 강한 조건이고, 그 차이가 헬퍼 추출 때 사라졌다

## 검증

- `compileTestJava` 통과
- 🔴 **로컬 물기 실측은 못 했다** — 이건 경합이고(두 이벤트가 같은 배치에 들어와야 발동),
  재현엔 Testcontainers 전체 스택이 필요하다. 기전 논증 + 형제 파리티가 근거이며
  **최종 판정은 AC-4(연속 3회 초록)** 이다. 그 전까지 이 수정은 미검증이다
- 🔵 확신의 근거는 **관측 6종이 전부 이 하나로 설명된다**는 것이다(AC-1 표). 특히
  *"단일 이벤트 테스트는 한 번도 실패한 적이 없다"* 는 다른 가설(브로커 불안정, 자원 압박,
  파티션 할당 지연)로는 설명되지 않는다 — 그것들이라면 단일 테스트도 때때로 깨져야 한다

# Definition of Done

- [x] AC-0~AC-3 충족
- [ ] **AC-4 — nightly 연속 3회 초록** (머지 후 확인)
- [ ] `:projects:scm-platform:tests:e2e:e2eFullTest` GREEN (nightly)
- [x] Ready for review
