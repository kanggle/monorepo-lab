# Task ID

TASK-MONO-398

# Title

`TASK-MONO-393` 이 저장소에 **거짓 근거 한 문장**을 남겼다 (자진 신고) — 그것을 정정하고, 같은 완화책이 빠진 **나머지 5개 레인**을 실측한다 (전면 적용 금지, verify-then-act)

# Status

review

> **실측 완료 (2026-07-14). AC-0 의 답: 5개 레인 중 *하나만* 이 병을 앓는다 — `ecommerce`.** 나머지 넷(fan·erp·scm·finance)은 **자원고갈 증거 0건**이라 **건드리지 않았다**. § "AC-0 실측 결과" 참조.

# Owner

monorepo

# Task Tags

- ci
- correctness
- declaration-truth-drift

---

# Goal

## A. 정정 — 내가 심은 거짓 근거 (결정론적, 이 티켓의 확실한 부분)

`TASK-MONO-393` (PR #2520, `e3432674c`) 은 iam CI 레인의 자원 고갈을 진단하고 `--no-parallel` 로 고쳤다. **그 진단은 옳고, 수정은 iam 레인 3회 GREEN 으로 실증됐다**(PR 2회 + `main` 에서 **실제 실행** 1회 — skip 아님).

**그러나 곁가지 근거 하나가 거짓이다.** `.github/workflows/ci.yml` 주석과 `tasks/INDEX.md` 의 DONE 노트가 이렇게 말한다:

> *"scm's lane failed in the same attempt, which is what runner-wide exhaustion looks like."*
> *"같은 attempt 에 scm 레인도 동반 실패한 것이 러너 전체 자원 고갈의 지문이다."*

**scm 잡 로그(36줄)를 열면 이렇다:**

```
##[error]Bad Gateway
##[error]Failed to resolve action download info.
```

⇒ **scm 은 GitHub Actions 액션 해석 장애로 죽었고 코드를 한 줄도 실행하지 않았다.** 두 레인이 같이 실패한 것을 보고 **두 번째 로그를 열지 않은 채** 공통 원인으로 단정했다.

**정정된 규칙은 원래 쓴 것의 거의 반대다:**

> **"두 레인이 같이 실패했다" 는 러너 고갈의 지문이 아니다.** Actions 장애는 여러 잡을 **한꺼번에** 죽이므로, **동시 실패의 가장 흔한 원인은 오히려 인프라 장애다.** 판별은 **실패 *지점*** 으로 한다 — `Set up job` / action resolve 단계면 인프라, **실패 테스트의 출력 창 *안*** 이면 진짜.

**iam 진단은 이것에 의존하지 않는다** — 근거는 실패 테스트 창 안의 `Redis unavailable for token blacklist check, fail-closed` **정확히 2회**(refresh 호출 1건당 1회) + 같은 잡의 `RedisConnectionFailureException` 10회+ 였다. 무너지는 것은 곁가지 문장과 거기서 끌어낸 **일반화**뿐이다.

**왜 지금 고치는가**: 다음 사람이 그 주석을 읽고 **틀린 휴리스틱을 물려받는다.** 이 저장소가 반복해 대가를 치른 **선언↔진실 드리프트**를, MONO-393 이 (드리프트를 잡는 task 를 하면서) 스스로 하나 심었다. [[feedback_local_proves_behaviour_not_performance]] 의 재발 — *유보를 적을 자리에 확신을 적었다*.

## B. 실측 — 완화책이 빠진 레인 5개 (verify-then-act, **전면 적용 금지**)

`ci.yml` 의 integration caller 별 실측 (2026-07-14, `origin/main`):

| caller | 모듈 수 | `--no-parallel` |
|---|---|---|
| `integration-tests` (wms) | 4 | ✅ `TASK-MONO-331` |
| `wms-inventory-inbound-integration-tests` | 2 | ✅ |
| `iam-integration-tests` | 7 | ✅ `TASK-MONO-393` |
| **`ecommerce-integration-tests`** | **12** | ❌ |
| `fan-integration-tests` | 4 | ❌ |
| `erp-integration-tests` | 4 | ❌ |
| `scm-integration-tests` | 3 | ❌ |
| `finance-integration-tests` | 2 | ❌ |

**`ecommerce` 는 12모듈로 완화된 어느 레인보다 크다.**

### ⚠️ 그러나 "모듈이 많다" 는 착수 근거가 아니다 — 양쪽 실패 모드가 다 있다

- **과소 수정**: `TASK-MONO-331` 은 iam(7모듈)을 보고 *"iam 잡은 7모듈이나 통과=풋프린트 차이"* 라고 **명시적으로 넘어갔다.** 일주일 뒤 그 판단이 `TASK-MONO-393` 을 낳았다. **"지금 초록"은 완화책을 빼도 되는 근거가 아니다.**
- **과잉 수정**: `--no-parallel` 은 **공짜가 아니다.** iam 레인은 직렬화 후 눈에 띄게 느려졌고, wms 4모듈 레인은 MONO-331 이후 **30분 timeout 경계를 넘겨 CANCELLED** 되는 일이 관측됐다(`env_wms_notification_seed_cluster_ci_flake` § 잔존 flake). **증거 없이 5개 레인에 바르면 CI 벽시계만 늘리고 timeout-CANCELLED 를 새로 만든다.**

⇒ **AC-0 (verify-then-act) 이 이 티켓의 절반이다.** 각 레인이 **실제로 이 병을 앓고 있는가**를 이력으로 실측하고, **근거가 있는 레인만** 직렬화한다. **0건이면 아무것도 바꾸지 않는 것이 옳은 결과다** — 그 사실 자체를 기록한다.

---

# Scope

## In Scope

- `.github/workflows/ci.yml` — iam caller 주석의 거짓 문장 제거 + 정정된 판별 규칙으로 교체.
- `tasks/INDEX.md` — MONO-393 DONE 노트의 같은 문장 정정. (**티켓 파일 `tasks/done/TASK-MONO-393-*.md` 는 HARDSTOP-05 로 동결** ⇒ 정정은 INDEX 에 남긴다.)
- 완화 없는 5개 레인의 **실패 이력 실측** + 근거 있는 레인에 `gradle-args: --no-parallel` (AC-2/AC-3).

## Out of Scope

- **`--no-parallel` 의 무조건 전면 적용.** AC-0 이 근거를 못 대면 하지 않는다.
- fail-closed 정책(TASK-BE-062 §B) — 의도된 보안 결정, 바꾸려면 ADR.
- 레인 timeout 상향 / Testcontainers 풋프린트 축소(Hikari 캡·Kafka heap) — 별건. AC-0 이 timeout 이 진짜 병목이라고 말하면 **그때 티켓을 판다**.

---

# Acceptance Criteria

- [x] **AC-1 (정정 — 결정론적)** — `ci.yml` iam caller 주석 + `tasks/INDEX.md` 의 MONO-393 DONE 노트에서 거짓 문장을 제거하고 **정정된 판별 규칙**으로 교체했다. **`ci.yml` 거짓 문장 잔존 = grep 0건.** ⚠️ **정정문이 원문을 인용하면 가드가 그것을 잔존으로 센다** ⇒ 인용 대신 풀어썼다(가드 술어는 "인용"과 "주장"을 구분하지 못한다 — 이 저장소가 반복 학습한 실패 모드).
- [x] **AC-0 (실측 — verify-then-act, 이 티켓의 절반)** — 완료. § "AC-0 실측 결과". **탐지식이 세 번 침묵했고 세 번 고쳤다**(`gh run list` 가 재실행에 가려진 실패를 못 봄 / jq `\(` 보간으로 필터 사망 → 0건 / 분류기가 자원고갈을 인프라로 오분류). **결과: `ecommerce` 만 (ii) 자원고갈. fan·erp·finance = 실패 0건, scm = (i) 인프라.**
- [x] **AC-2 (근거 있는 레인만 직렬화)** — **`ecommerce` 에만** `gradle-args: --no-parallel`. 근거 = 고갈 지문 **151**회 + 테스트 실제 실행 + `review-service` 가 레이스에서 짐(MONO-331 서명과 동일). **나머지 넷은 증거 0건이라 건드리지 않았다** — 근거를 `ci.yml` 주석과 이 티켓에 적었다.
- [x] **AC-3 (직렬화의 대가를 실측한다)** — ecommerce 병렬 **8분**(8런 평균, max 9분) vs `timeout-minutes: 30` ⇒ **3배 이상 여유**. 참고로 직렬화된 iam(7모듈)이 9~10분. **직렬화 후 실제 소요는 이 PR 의 CI 가 잰다.** wms 가 겪은 timeout-CANCELLED 는 여기선 위험이 낮다.
- [ ] **AC-4 (무손실)** — 변경한 ecommerce 레인이 CI 에서 **실제로 실행되어**(skip 아님) GREEN. **`markdown-only` 커밋에서는 SKIP 되고 skip 은 초록으로 보고된다** ⇒ `gh run view --json jobs` 로 잡이 정말 돌았는지 확인한다.

## 검증

- **AC-1 은 grep 으로 못 박는다** — 거짓 문장의 특징어(`runner-wide exhaustion looks like`)가 `ci.yml` 에서 **0건**. ⚠️ **정정문이 원문을 인용하면 가드가 그걸 잔존으로 센다** — 인용 대신 풀어써서 기계적 가드가 살아있게 했다(가드 술어는 "인용"과 "주장"을 구분 못 한다).
- **AC-0 의 권위는 CI 로그** — "flake 겠지" 로 분류하지 말 것(`env_ci_flake_is_a_hypothesis_not_a_verdict`). **실패 지점을 열어서** 분류한다. 이 티켓이 존재하는 이유가 정확히 *로그를 안 열고 단정한 것* 이다.

---

# AC-0 실측 결과 (2026-07-14) — **5개 중 하나만 아프다**

## 🔴 탐지식이 세 번 침묵했다 (결과를 믿기 전에 세 번 고쳤다)

1. **`gh run list` 는 *최종 attempt* 의 결론만 보고한다** ⇒ **재실행으로 초록이 된 실패는 통째로 안 보인다.** 최근 200런에서 integration 실패 **0건**으로 나왔다 — 그런데 내가 찾는 게 정확히 그 flake 다. **아는 답(MONO-393 의 iam 실패)에 돌려보고 안 잡혀서 알았다.** 해법 = `run_attempt > 1` 인 런을 뽑아 **각 attempt 의 잡**을 조회.
2. **jq 가 `\(` 를 문자열 보간으로 파싱**해 필터가 죽었는데 `2>/dev/null` 이 에러를 삼켰다 ⇒ **0건**. 그대로 믿었으면 *"어느 레인도 이 병을 앓은 적 없다 = 아무것도 고치지 마라"* — **이 티켓이 F2 로 경고한 그 결론**을 낼 뻔했다. 정규식을 걷어내(`startswith`) 해결.
3. **분류기 술어가 뒤집혀 있었다** — iam 실패 로그에는 초반의 일시적 action-resolve 에러 **와** 진짜 Redis 절단이 **둘 다** 들어 있는데, 인프라를 먼저 보게 돼 있어 **자원고갈을 인프라로 오분류**했다. **양성 증거(고갈 지문)를 먼저 보도록** 뒤집어 고쳤다. (아는 답으로 검증: iam=🔴 로 나와야 한다 → 나왔다.)

## 분류 결과 (재실행 안에 숨어있던 integration 실패 전량)

| 날짜 | 레인 | 로그 | 고갈 지문 | 판정 |
|---|---|---|---|---|
| 07-13 att2 | **iam** | 23,964줄 | **13** | 🔴 자원고갈 (= MONO-393, 이미 고침) |
| **07-12 att1** | **ecommerce** | 88,683줄 | **151** | 🔴 **자원고갈 — 확정** |
| 07-13 att2 | scm | **36줄** | 0 | 🔵 인프라 (`Bad Gateway`) |
| 07-13 att1 | ecommerce | 237줄 | 0 | 🔵 인프라 (Actions 장애) |
| 07-13 att1 | iam | 31줄 | 0 | 🔵 인프라 (Actions 장애) |
| — | **fan · erp · finance** | — | **0** | ⚪ **증거 0건** |

## 🎯 ecommerce — 교과서적 서명

실패 잡 `86703554954` (2026-07-12, **재실행에 가려져 빨간 런으로 뜬 적조차 없다**):

- `Connection is not available` **×151** + `SQLTransientConnectionException` **×31**
- **테스트는 실제로 실행됐다** (인프라 장애 아님)
- 죽은 것은 `review-service:integrationTest` **하나** — *"레이스에서 진 모듈이 통째로 실패"* 라는 `TASK-MONO-331` 의 서술 그대로

⇒ **MONO-331 이 WMS 에서 기록한 Hikari/Postgres 커넥션 고갈과 문자 그대로 같은 서명이고, 지문 수는 iam(13)보다 한 자릿수 더 크다.** ecommerce 는 **12모듈**(각자 Postgres + Kafka, search 는 nori ES 까지)로 **저장소에서 가장 큰 레인**이고, **완화책이 없는 마지막 레인**이었다.

## ⚪ 나머지 넷은 건드리지 않았다 — 0건도 결과다

fan·erp·finance 는 재실행 이력에서 **실패 자체가 없고**, scm 의 유일한 실패는 **Actions 장애**였다. **`--no-parallel` 은 공짜가 아니다**(wms 는 직렬화 후 30분 timeout 경계를 넘겨 CANCELLED 된 이력이 있다) ⇒ **증거 없는 레인에 바르면 CI 벽시계만 늘고 새 결함을 만든다.** 이 티켓의 F1 이 경고한 그것이다.

**그리고 scm 이 인프라였다는 사실이, MONO-393 의 거짓 주장(A 파트)을 *독립적으로* 재확인한다.**

## AC-3 — 직렬화의 대가

| 레인 | 병렬(실측 8런 평균) | timeout | 판단 |
|---|---|---|---|
| ecommerce | **8분** (max 9분) | **30분** | 3배 이상 여유 ⇒ 직렬화 안전 |
| iam (직렬화 후) | 9분 (max 10분) | 30분 | 참고: 7모듈 직렬이 10분 내 |

**직렬화 후 실제 소요는 이 PR 의 CI 가 잰다**(ecommerce 레인이 `workflows` 필터로 발동).

---

# Related Specs

- `.github/workflows/ci.yml` — integration caller 8개
- `.github/workflows/_integration.yml` — `gradle-args` input (기본값 `''`, MONO-331 신설)
- `gradle.properties` — `org.gradle.parallel=true` (직렬화가 의미를 갖는 전제)
- `tasks/done/TASK-MONO-393-single-session-revoke-kills-the-other-session.md` — 동결(HARDSTOP-05)
- `tasks/done/TASK-MONO-331-*` — 원 완화책

# Related Contracts

- 없음 (CI 전용. 계약·API·이벤트 무영향)

---

# Edge Cases

- **`--no-parallel` 이 timeout-CANCELLED 를 만든다** — wms 4모듈 레인이 MONO-331 이후 실제로 겪었다. **CANCELLED 는 테스트 실패가 아니지만 초록도 아니다.** AC-3 가 이것을 본다.
- **`ecommerce` 12모듈을 직렬화하면 가장 크게 느려진다.** 그 레인이야말로 AC-0 의 근거가 가장 필요하다 — 모듈 수는 가설이지 증거가 아니다.
- **레인마다 스택 무게가 다르다** — wms=Postgres + confluent cp-kafka(무겁다) / iam=MySQL + Kafka + Redis + WireMock. **모듈 수보다 스택 풋프린트가 실제 압력이다.** AC-0 에서 컨테이너 수를 함께 세면 더 정확하다.

# Failure Scenarios

- **F1 — 근거 없이 5개 다 바른다** → CI 벽시계 증가 + timeout-CANCELLED 신설. **과잉수정도 결함이다.**
- **F2 — "지금 초록이니 괜찮다" 로 전부 넘긴다** → MONO-331 이 iam 에 대해 내린 바로 그 판단이고, 그것이 MONO-393 을 낳았다.
- **F3 — 실패 로그를 안 열고 분류한다** → **이 티켓을 낳은 그 실수를 반복한다.** 실패 *지점*을 봐야 인프라 장애와 자원 고갈이 갈린다.

---

# Test Requirements

- 새 테스트 없음. AC-0 의 단언은 **CI 실패 이력의 관측**이고, AC-4 의 단언은 **잡이 실제로 돌았다는 관측**이다.

---

# Definition of Done

- [ ] AC-0 ~ AC-4.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

자진 신고 — `TASK-MONO-393` 종결 직후 *"관련 작업이 전부 완료됐는가"* 를 점검하다 발굴(2026-07-14).

**MONO-393 은 *"인프라 장애가 보안 결함의 옷을 입었다"* 를 밝힌 task 다. 그리고 그 task 를 하면서, 나는 또 다른 인프라 장애(Actions `Bad Gateway`)를 자원 고갈로 오독해 저장소에 적었다.** 같은 세션 안에서 같은 실수를 두 번 했다 — **로그를 열지 않고 공통 원인을 단정하는 것.** 두 번째는 곁가지라 결론을 바꾸지 않았을 뿐이다.

**교훈은 "동시 실패를 의심하라" 가 아니라 "잡마다 실패 *지점*을 열어라" 다.** 그리고 이 티켓의 B 파트는 그 교훈을 자기 자신에게 적용한다 — **모듈이 12개라는 사실은 ecommerce 레인이 아프다는 증거가 아니다.** 증거는 로그에 있고, 없으면 없는 것이다.

분석=Opus 4.8 / 구현 권장=**Sonnet** (A=결정론적 편집. B=`gh` 로그 조회+분류. 판단 기준은 티켓이 이미 정했다).
