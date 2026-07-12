# Task ID

TASK-BE-505

# Title

`DetectionE2EIntegrationTest` 의 `verify()` 가 유일하게 기다리지 않는다 — 비행 중 요청을 실패로 읽는 경합

# Status

done

# Owner

backend

# Task Tags

- test
- ci

---

# Goal

`security-service` 의 `DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E` 가 **확률적으로 실패한다.** 결함은 프로덕션 코드가 아니라 **테스트**에 있다: `await` 가 끝난 뒤 **DB 를 다시 조회해 다른 행을 검증 대상으로 집고**(163), 그 검증만이 **유일하게 `await` 밖**이라(169) 재시도 없이 즉사한다. (착수 전 가설은 후자를 원인으로 봤으나 **코드가 순서를 뒤집었다** — § *"코드가 진단을 확정한다"*.)

**지금 고쳐야 하는 이유는 `TASK-MONO-374` 다.** 374 이전에는 main 의 push 런들이 서로를 취소해서 통합 잡이 거의 실행되지 않았고(최근 main 12 런에서 `Integration (iam)` 이 실제로 완주한 것은 **2 회**), 이 결함은 **보이지 않았다.** 374 가 기준선을 되살렸으므로 **이제 코드가 머지될 때마다 이 스위트가 완주하고, 이 경합은 main 을 상습적으로 RED 로 만든다.**

374 자신이 wms 에 대해 이 위험을 명시적으로 경고한다 — *"기준선을 되살리면 스위트가 매 머지마다 완주하므로, 먼저 고치지 않으면 첫 완주가 flake RED 이고 **첫날 RED 인 가드는 꺼진다**"*. **374 는 wms 축(MONO-376)만 선행 처리했고 iam 축은 아무도 보지 않았다.** 이 task 가 그 자리다.

---

# 관측 (2026-07-13, PR #2469 CI — `TASK-MONO-366` 머지 직전)

`Integration (iam, Testcontainers)` → `security-service:integrationTest` **20 건 중 1 건 실패**. 같은 잡을 **재실행하니 GREEN** — 결정론적 파손이 아니라 **확률적 결함**이다.

**콘솔 로그에는 실패한 테스트 이름조차 없다.** 실제 메시지는 `iam-integration-test-reports` 아티팩트의 JUnit XML 에만 있다:

```
com.github.tomakehurst.wiremock.client.VerificationException:
No requests exactly matched. Most similar request was:
  expected:<POST /internal/accounts/70d4806d-.../lock
            Idempotency-Key: 14c05d49-...
            Content-Type: application/json>
  but was: <POST /internal/accounts/70d4806d-.../lock
            Idempotency-Key: 14c05d49-...
            Content-Type: application/json>
  at DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E(DetectionE2EIntegrationTest.java:169)
```

**expected 와 actual 이 글자 그대로 동일하다.** 이 모양이 진단의 열쇠다.

# 진단 — `verify()` 가 기다리지 않는다

테스트의 나머지 단언은 전부 `await().untilAsserted(...)` 안에 있다:

| 줄 | 단언 | 기다리는가 |
|---|---|---|
| 150 | `suspicious_events` 행에 `AUTO_LOCK` / `riskScore ≥ 80` / `lockRequestResult=SUCCESS` | ✅ `await` 30s |
| **169** | **WireMock 이 lock POST 를 받았는가** | ❌ **없음** |
| 181 | outbox 행 `security.auto.lock.triggered` | ✅ `await` 10s |

**`verify()` 만 즉시 단언한다.** 그런데 lock 호출은 **비동기 경로**를 탄다 — Kafka 컨슈머 → 탐지 → `AccountServiceClient.lock()`(HTTP, 재시도 3회). DB 행이 보이는 시점과 **HTTP 요청이 WireMock 저널에 기록되는 시점은 같지 않다.**

**동일 렌더링이 바로 "비행 중" 의 지문이다.** WireMock 은 매칭에 실패한 **뒤에** near-miss 를 계산하면서 저널을 다시 읽는다. 그 사이(마이크로초 단위)에 요청이 도착하면:

1. 매칭 시점 저널 → 해당 요청 없음 → *"No requests exactly matched"*
2. near-miss 계산 시점 저널 → 요청 있음 → **expected 와 동일한 actual 을 출력**

**요청은 결국 도착했다. 단언이 너무 일찍 물었을 뿐이다.** 그래서 "기대와 실제가 같은데 실패" 라는, 논리적으로 불가능해 보이는 메시지가 나온다.

## 🔴 코드가 진단을 확정한다 — 그리고 주·부가 뒤집힌다

**착수 전 가설**("verify 가 안 기다려서 비행 중 요청을 놓친다")만으로는 증거와 안 맞는 구석이 있었다: `await`(150) 는 `lockRequestResult == SUCCESS` 를 단언하는데, **SUCCESS 가 쓰였다는 건 HTTP 가 이미 응답까지 받았다는 뜻**아닌가? 그러면 요청은 저널에 있어야 한다.

`IssueAutoLockCommandUseCase:20-27` 이 답한다:

```java
AccountLockClient.LockResult result = accountLockClient.lock(event);   // ① HTTP 호출이 먼저
...
persistenceService.updateLockResult(updated);                          // ② 결과를 그 다음에 기록
```

`updateLockResult` 는 **update** 다 — **행은 그 전에 이미 존재한다**(`lockRequestResult` = null/`PENDING`; `SuspiciousEvent:27` 이 그 어휘를 선언한다). 여기서 두 가지가 따라온다:

1. **`SUCCESS` 가 찍힌 행은 요청이 이미 완료됐음을 함의한다** → 그 행의 요청은 **반드시** 저널에 있다.
2. **그러나 행은 lock 호출 전에도 존재한다** → `await` 이후 **재조회가 집는 "최신 행"은 아직 호출이 나가지 않은 행일 수 있다.**

⇒ **진짜 원인은 행 재조회(163)** 이고, **`await` 없는 `verify()`(169) 는 그것을 재시도 없이 즉사시키는 역할**이다. 두 축은 독립이 아니라 **원인과 증폭기**다.

**관측된 "expected == actual" 도 이것으로 설명된다**: 재조회가 집은 최신 행의 lock 호출이 막 나가는 중 → `verify` 시점 저널엔 없음 → 매칭 실패 → 그 직후 요청 도착 → WireMock 이 near-miss 를 계산하며 저널을 **다시 읽어** 방금 도착한 그 요청을 출력 → **기대와 동일한 actual.**

## 이 수정이 결정론적으로 옳은 이유 (mutation 재현보다 강한 근거)

**`await` 가 통과시킨 행은 `SUCCESS` 이므로, 그 행의 요청은 이미 저널에 있다**(①). 따라서 **그 행의 id 를 고정해 검증하면 경합이 구조적으로 사라진다** — 확률이 낮아지는 게 아니라 **불가능해진다.**

이 명제는 `IssueAutoLockCommandUseCase` 의 **호출 순서에서 따라 나오는 증명**이지, 초록 실행 몇 번의 귀납이 아니다. 확률적 결함에서 재현 실험은 **음성이 무의미하다**(원래 대부분 초록이었다) — 그래서 이 task 의 근거는 **재현이 아니라 순서 증명**에 둔다.

⇒ 수정: **기다림이 통과시킨 행을 고정** + **검증도 `await` 안으로**(TOCTOU 에 대한 이중 방어이자, 다른 두 단언과 같은 규율).

---

# Scope

## In Scope

- `projects/iam-platform/apps/security-service/src/test/java/com/example/security/integration/DetectionE2EIntegrationTest.java`
  - `verify()`(169) 를 `await().untilAsserted(...)` 안으로.
  - 검증 대상 `suspiciousEventId` 를 **`await`(150) 가 통과시킨 그 행**에서 고정(재조회로 다른 행을 집지 않게).

## Out of Scope

- **프로덕션 코드 변경 없음.** 탐지·lock·outbox 경로는 정상이다 — 요청은 실제로 도착했다. **테스트만 틀렸다.**
- 다른 iam 통합 테스트의 유사 패턴 전수 조사 — 이 task 는 **관측된 결함 하나**를 닫는다. 전수 스윕이 필요하다고 판단되면 별도 task.
- `TASK-MONO-374`(기준선 복구) 자체 — 이미 main 에 있다.

---

# Related Specs

- `projects/iam-platform/specs/services/security-service/architecture.md` — 탐지 → 자동 잠금 경로.

# Related Contracts

- 없음(계약 변경 없음 — 테스트 단독 수정).

# Target Service

`projects/iam-platform/apps/security-service`

---

# Acceptance Criteria

- [x] **`verify()` 가 `await` 안에 있다** — `wireMockServer.findAll(pattern)` 을 `await().untilAsserted` 안에서 단언한다. **`verify()` 를 쓰지 않는 이유**: 미스가 `VerificationException`(비-`AssertionError`)으로 나면 Awaitility 가 재시도하지 않을 뿐 아니라, 그 메시지가 **적극적으로 오해를 부른다**(§ 관측 — 기대와 실제가 동일하게 찍힌다).
- [x] **검증 대상 행이 `await` 가 검증한 행과 동일하다** — `AtomicReference` 로 **기다림이 통과시킨 행의 id 를 그 자리에서 고정**한다. 재조회 제거.
- [x] **🔴 근거는 재현이 아니라 순서 증명이다 — 초록은 증거가 아니다.**
      이 결함은 **원래 대부분의 실행에서 초록이었다.** 그러므로 "고친 뒤 초록" 도, "옛 코드로 되돌렸더니 초록" 도 **아무것도 증명하지 못한다**(재현 실험의 음성은 무의미하다). 근거는 코드 순서에서 따라 나와야 한다:
      **`IssueAutoLockCommandUseCase:20` 이 `lock()` 을, `:27` 이 `updateLockResult()` 를 부른다 ⇒ `SUCCESS` 행의 요청은 이미 저널에 있다 ⇒ 그 행을 고정하면 경합은 확률이 낮아지는 게 아니라 구조적으로 불가능해진다.**
      task 본문 § *"이 수정이 결정론적으로 옳은 이유"* 가 이 증명을 담고, PR 이 그것을 근거로 제시한다.
- [x] **반복 실행으로 회귀 없음 확인** — `integrationTest --rerun-tasks` **연속 3 회, `gradle_exit=0` + `BUILD SUCCESSFUL`**, JUnit XML 합산 **tests=20 / skipped=0 / failures=0**. **이것은 결함 부재의 증거가 아니라 회귀 부재의 확인**이다(위 증명이 정당화를 담당한다).
      **초록의 출처를 확인해야 한다**: 첫 시도의 `exit=$?` 는 파이프라인 뒤라 **gradle 이 아니라 `head` 의 종료코드**였고 3회 모두 `0` 을 찍었지만 **아무것도 증명하지 않았다.** 이 저장소는 *"Docker 다운 → 전건 SKIPPED → 그런데 `BUILD SUCCESSFUL`"* 을 이미 겪었다 — 그래서 **XML 의 `skipped=0` 까지 확인**했다.
- [ ] CI GREEN.

---

# Edge Cases

- **`await` 안에서 WireMock `verify()` 를 부르면 실패 시 `VerificationException`(비-`AssertionError`)이 난다** — `untilAsserted` 가 그것을 재시도한다는 보장에 기대지 않는다. **해결: `assertThat(wireMockServer.findAll(pattern)).isNotEmpty()`** — 미스가 `AssertionError` 로 남아 확실히 재시도되고, **오해를 부르는 그 메시지 자체를 아예 만들지 않는다.**
- **재시도 3회** — `AccountServiceClient` 는 실패 시 최대 3회 재시도한다(`maxAttempts`). 성공 경로에서는 요청이 1건이지만, 검증을 "정확히 1건" 으로 조이면 재시도가 있는 세계에서 새 flake 를 만든다. **"적어도 1건"** 을 유지할 것.
- **버스트가 만드는 행이 여러 개** — 10 이벤트 중 임계치를 넘는 것마다 행이 생긴다. 특정 행 하나를 고정해 검증하되, **다른 행들의 lock 호출이 함께 도착하는 것**은 정상이다. 전체 요청 수를 단언하지 말 것.

# Failure Scenarios

- **`await` 로 감싸기만 하고 행 고정을 빠뜨림** → 재조회가 여전히 비행 중인 행을 집을 수 있다. 빈도는 낮아지지만 결함은 남고, **낮아진 빈도가 "고쳤다" 로 오독된다** — 확률적 결함에서 가장 위험한 실패 모드.
- **타임아웃만 늘림**(`await` 없이 `Thread.sleep`) → 느려지고, 부하가 걸린 러너에서 다시 깨진다. **기다림은 시간이 아니라 조건에 걸어야 한다.**
- **수정 후 한 번 초록인 것을 증거로 삼음** → 이 결함은 **원래 대부분의 실행에서 초록이었다.** CI-GREEN 은 확률적 결함의 부재를 증명하지 못한다(AC-3 가 이것을 막는다).
- **방치** → 374 가 기준선을 되살렸으므로 **main 이 상습적으로 RED** 가 된다 → 사람들이 잡을 끄거나 RED 를 무시한다 → **처음보다 나쁘다**(374 가 스스로 경고한 실패 모드).

# Test Requirements

- `:projects:iam-platform:apps:security-service:integrationTest` — Testcontainers(MySQL + Kafka) + WireMock.
- **mutation 실측** — AC-3. `verify()` 를 `await` 밖으로 되돌리거나 스텁에 지연을 넣어 **옛 형태가 RED** 임을 보인다.
- **연속 3 회 GREEN** — AC-4.
- 로컬 Windows 는 Testcontainers 가 FLAKY 하므로 **CI Linux 가 권위**다.

# Definition of Done

- [ ] 위 AC 전부
- [ ] CI GREEN
- [ ] `projects/iam-platform/tasks/INDEX.md` done entry

---

# Provenance

2026-07-13, `TASK-MONO-366`(PR #2469) 의 CI 에서 드러났다. 그 PR 은 `infra/demo` · `ci.yml` · `tasks/` 만 건드렸고 **iam 코드는 한 줄도 손대지 않았다** — 즉 이 결함은 그 변경이 만든 것이 아니라, **그 변경이 우연히 밟은 것**이다.

**왜 지금까지 아무도 못 봤나**: `TASK-MONO-374` 가 밝힌 대로 main 의 push 런들이 서로를 취소하고 있었다(`concurrency.group` 이 모든 main push 에서 동일). 통합 잡은 `cancelled` / `skipped` 로 지나갔고, **`cancelled` 는 `failure` 가 아니라서 알림도 빨간 X 도 없었다.** 최근 main 12 런 중 `Integration (iam)` 이 실제로 완주한 것은 **2 회**뿐이다.

**계보**: `359`/`360` 이 *가드* 에 대해 확립한 명제 — ***실행되지 않는 검사는 초록으로 보고된다*** — 가 `374` 에서 *CI 자신의 사후 검증 런* 으로, 그리고 이 task 에서 ***그 런이 되살아나자 드러난 확률적 결함*** 으로 이어진다. 374 는 wms 축(`MONO-376`)을 선행 처리했지만 **iam 축은 비어 있었다.**

분석=Opus 4.8 / 구현 권장=**Opus**(경합 진단이 반직관적이다 — "expected 와 actual 이 같은데 실패" 를 인프라 flake 로 오독하기 쉽고, **`await` 로 감싸기만 하면 빈도만 낮아진 채 결함이 남는데 그것이 '고쳤다' 로 보인다**).
