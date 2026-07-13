# Task ID

TASK-MONO-398

# Title

`TASK-MONO-393` 이 저장소에 **거짓 근거 한 문장**을 남겼다 (자진 신고) — 그것을 정정하고, 같은 완화책이 빠진 **나머지 5개 레인**을 실측한다 (전면 적용 금지, verify-then-act)

# Status

ready

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

- [ ] **AC-1 (정정 — 결정론적)** — `ci.yml` 주석과 `tasks/INDEX.md` DONE 노트에서 *"scm 도 동반 실패 = 러너 고갈의 지문"* 문장이 사라지고, **정정된 판별 규칙**(실패 *지점*으로 가른다: `Set up job`/action resolve = 인프라 / 테스트 출력 창 안 = 진짜)이 그 자리에 들어간다. **거짓 문장 잔존 = grep 0건**으로 확인한다.
- [ ] **AC-0 (실측 — verify-then-act, 이 티켓의 절반)** — 완화 없는 5개 레인 각각에 대해 **최근 실패 이력을 실제로 조회**하고(예: `gh run list --workflow=ci.yml` + 실패 잡 로그), **실패 *지점*으로 분류**한다:
  - (i) 인프라 장애(`Set up job` / action resolve / `Bad Gateway`) → **무관, 세지 않는다.**
  - (ii) 자원 고갈 지문(`SQLTransientConnectionException` · `Connection is not available` · `I/O error sending to the backend` · `Unable to connect to Redis` · `ContainerLaunchException`) → **이 병이다.**
  - (iii) 테스트 코드 결함 → 다른 문제.
  - **(ii) 가 0건인 레인은 건드리지 않는다.** 0건이라는 사실도 결과이므로 기록한다.
- [ ] **AC-2 (근거 있는 레인만 직렬화)** — AC-0 이 (ii) 를 보인 레인에만 `gradle-args: --no-parallel` 을 넣는다. 넣은 레인/안 넣은 레인과 **각각의 근거**를 티켓에 적는다.
- [ ] **AC-3 (직렬화의 대가를 실측한다)** — `--no-parallel` 을 넣은 레인의 **CI 소요 시간 전/후**를 기록하고, 잡 `timeout-minutes` 안에 드는지 확인한다. **timeout 경계를 넘기면 그것은 새 결함이다**(wms 가 실제로 겪었다) ⇒ timeout 상향을 같은 PR 에 포함하거나, 직렬화를 철회하고 별건 티켓으로 돌린다.
- [ ] **AC-4 (무손실)** — 변경한 레인이 CI 에서 **실제로 실행되어**(skip 아님) GREEN. **`markdown-only` 커밋에서는 이 레인들이 SKIP 되고 skip 은 초록으로 보고된다** ⇒ 잡이 정말 돌았는지 `gh run view --json jobs` 로 확인한다.

## 검증

- **AC-1 은 grep 으로 못 박는다** — 거짓 문장의 특징어(`scm` + `exhaustion` / `동반 실패` + `지문`)가 저장소에서 0건.
- **AC-0 의 권위는 CI 로그** — "flake 겠지" 로 분류하지 말 것(`env_ci_flake_is_a_hypothesis_not_a_verdict`). **실패 지점을 열어서** 분류한다. 이 티켓이 존재하는 이유가 정확히 *로그를 안 열고 단정한 것* 이다.

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
