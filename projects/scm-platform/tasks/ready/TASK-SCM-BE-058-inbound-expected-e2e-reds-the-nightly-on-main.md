# Task ID

TASK-SCM-BE-058

# Title

`InboundExpectedLoopE2ETest` 가 **main 의 nightly 를 간헐적으로 빨갛게** 만든다 — 기다리던 inbound-expected 이벤트가 오지 않는다(`Expecting actual not to be null`)

# Status

ready

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

- [ ] **AC-0 (재현 + 모집단 재확인)** — 착수 시 nightly 이력을 **다시** 세고, 실패를
      `Set up job`(인프라) vs 실제 실행 단계로 **갈라서** 적는다. 🔴 위 표를 그대로
      믿지 말 것 — 그 사이 실행이 더 쌓였다
- [ ] **AC-1 (원인)** — "이벤트가 안 왔다" 의 이유를 **브로커에서** 확정한다:
      발행이 없었는가(프로듀서 미도달) / 발행은 됐는데 컨슈머가 못 받았는가(할당·타이밍).
      🔴 이 둘은 소비자 쪽 null 로는 갈리지 않는다 — end-offset 을 봐야 갈린다
      ([[env_empty_detector_output_is_not_absence]] 와 같은 축)
- [ ] **AC-2 (대조군)** — 성공한 실행(`31102486520` 등)의 로그와 **나란히** 비교한다.
      `Connection refused` 와 OTLP 잡음이 **성공 실행에도 있는지** 확인 — 있으면 그것들은
      원인이 아니다
- [ ] **AC-3 (수정 + 가드)** — 대기 조건이나 할당을 고친다. 🔴 단순히 타임아웃을 늘리는
      것은 **원인 없이 증상을 미루는 것**이므로, 늘린다면 **왜 그 값인지** 실측 근거를
      적는다(러너 실측 — [[env_guard_calibrated_on_laptop_fails_on_runner]])
- [ ] **AC-4 (연속 초록)** — 수정 후 nightly 가 **연속 3회** 초록인 것을 확인한다.
      1회 초록은 간헐 결함에 대해 아무것도 증명하지 않는다

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

# Definition of Done

- [ ] AC-0~AC-4 충족
- [ ] `:projects:scm-platform:tests:e2e:e2eFullTest` GREEN
- [ ] Ready for review
