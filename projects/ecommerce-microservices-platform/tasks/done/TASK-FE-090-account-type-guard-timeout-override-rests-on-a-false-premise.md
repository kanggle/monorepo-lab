# Task ID

TASK-FE-090

# Title

`account-type-guard.spec.ts` 의 120s timeout override 를 제거한다 — **그 근거는 CI 가 반증했다** (`TASK-MONO-381` 이 남긴 선언↔진실 드리프트, 자진 신고)

# Status

done

# Owner

ecommerce-microservices-platform

# Task Tags

- test
- cleanup
- declaration-truth-drift

---

# Goal

`TASK-MONO-381` (`8dabdb38d`) 이 `account-type-guard` e2e 스펙을 되살리면서, 커밋 `556ff90e0` 으로 **이 스펙에만 120s timeout** 을 부여했다. 그 주석은 이렇게 단언한다:

> *"This file sorts FIRST in the lane, so it is the run's first GAP login: it pays the cold-JVM cost of the Spring Authorization Server … 60s is not a real budget for a cold full OIDC round-trip; 120s is."*

**그 단언은 거짓이다.** 머지 직후 `nightly-e2e.yml` 의 **push 트리거**로 실제 full-stack 레인이 돌았고(런 `29244678278`, job *Frontend E2E full-stack*), 실측은 이렇다:

```
account-type-guard.spec.ts             1        0     ← ran=1 / skipped=0
OK — all 5 required specs ran (12 tests collected).
12 passed (46.4s)
```

| spec | CI 실측 |
|---|---|
| **`account-type-guard.spec.ts`** (레인의 첫 GAP 로그인) | **4.0s** |
| `golden-flow.spec.ts` | 3.8s |
| `cart-management.spec.ts` | 6.9s |
| `rp-initiated-logout.spec.ts` | 17.6s |

**레인의 "첫 GAP 로그인" 이 4.0초다 — 기본 60s 예산에 15배 여유.** cold-SAS 비용이라는 이론은 성립하지 않는다.

## 왜 틀렸나 — 오염된 호스트에서 잰 시간을 결론으로 썼다

로컬 검증 당시 이 스펙은 28.5s / 33.6s 로 통과했고 **60s 를 두 번 넘겼다**(그중 한 번은 Playwright 재시도가 초록으로 덮었다). 그러나 그 측정은 **다른 세션의 컨테이너 19개**(fed-e2e 24개 + testcontainers + fulfillment-demo)가 같은 호스트에서 CPU 를 먹는 중에 이뤄졌다. **이 저장소엔 이미 같은 함정이 기록돼 있다** (`env_webstore_local_lighthouse_host_saturation` — *"fed-e2e 가동 중 = 가짜 시간메트릭"*).

MONO-381 은 그 유보를 **정직하게 적어 뒀다** (*"30s 급등을 호스트 경합과 분리하지 못해 증명하지 못한 근본 원인은 주장하지 않았다"*). **그런데 근본 원인을 주장하지 않으면서, 근본 원인을 단언하는 주석을 코드에 남겼다.** 유보는 PR 본문에 있었고 코드에는 확신만 남았다 — 그게 이 티켓이 존재하는 이유다.

⇒ **이 저장소가 반복해 대가를 치른 그 클래스를, MONO-381 이 스스로 하나 심었다: 선언↔진실 드리프트.** 코드 주석이 사실이 아닌 인과를 단언하고, 그것을 읽는 다음 사람은 그것을 근거로 판단한다. (MONO-381 자신이 *"코드 주석도 출처가 아니다"* 를 BE-506 에서 배운 task 다.)

---

# Scope

## In Scope

- `apps/web-store/e2e/account-type-guard.spec.ts` — `test.describe.configure({ timeout: 120_000 })` **와 그것을 정당화하는 주석 블록**을 제거한다. 스펙은 다른 5개와 같이 `playwright.config.ts` 의 기본 60s 를 쓴다.

## Out of Scope

- `playwright.config.ts` 의 전역 `timeout` / `retries` 조정 — 나머지 5개 스펙은 이 예산으로 오래 살아왔고, 바꿀 근거가 없다.
- 가드 로직(`seedFor`) · 시드(`iam-consumer-seed.sql`) · `assert-specs-ran.mjs` REQUIRED 목록 — **전부 정상 동작이 실증됐다**(위 CI 런). 건드리지 않는다.
- 로컬 Docker 호스트 경합 자체 — 별개 문제이고 CI 는 영향받지 않는다.

---

# Acceptance Criteria

- [x] **AC-1** — `account-type-guard.spec.ts` 에서 `test.describe.configure({ timeout: ... })` 와 그 근거 주석이 제거됐다(10줄 삭제, 순삭). 남은 주석은 **CI 가 실증한 것만** 말한다(가드가 무는 이유, 왜 이 스펙이 예전엔 못 돌았는지). 성능에 대한 인과 주장은 0.
- [ ] **AC-2 (여전히 도는가 — 이 티켓의 본체)** — 머지 후 push-트리거 nightly 의 *Frontend E2E full-stack* 잡에서 `assert-specs-ran.mjs` 가 여전히 **`account-type-guard.spec.ts ran≥1 / skipped=0`** 을 출력한다. **"잡이 초록" 은 증거가 아니다 — 그 표의 행을 직접 읽어 확인할 것**(이 도구가 존재하는 이유가 정확히 그것이다, `TASK-MONO-373`).
- [ ] **AC-3 (예산 안에 드는가)** — 같은 런에서 이 스펙의 소요가 60s 기본 예산 안이다. **flaky 로 통과한 것은 통과가 아니다** — 리포트에 `flaky` 가 0이어야 한다(재시도가 초록으로 덮는 것이 MONO-381 에서 실제로 일어났던 실패 모드다).
- [x] **AC-4 (무손실)** — `tsc --noEmit` exit 0, `next lint` **경고/에러 0**.

## 검증

- **권위 = CI**(push 트리거로 즉시 실측 가능). **로컬 Playwright 타이밍은 이 티켓에서 증거로 쓰지 않는다** — 애초에 이 결함을 만든 것이 로컬 타이밍이다.
- **AC-2 / AC-3 은 구조상 머지 후에만 관측된다** — `nightly-e2e.yml` 의 full-stack 레인은 **PR 에서 돌지 않는다**(schedule + push-to-main). 그래서 impl PR 의 CI 초록은 이 두 AC 에 대해 **아무것도 말하지 않는다**. close chore 전에 push-트리거 런의 `assert-specs-ran` 표와 `flaky` 카운트를 **직접 읽어** 채운다. (이 비대칭 자체가 MONO-381 이 물린 자리이고, 이 티켓이 다시 그 자리를 지난다.)

---

# Related Specs

- `apps/web-store/e2e/account-type-guard.spec.ts` — 대상
- `apps/web-store/playwright.config.ts` — `timeout: 60_000`, `retries: CI ? 1 : 0`
- `apps/web-store/e2e/tools/assert-specs-ran.mjs` — AC-2 의 관측 도구
- `.github/workflows/nightly-e2e.yml` — *Frontend E2E full-stack* 잡 (schedule + **push**)
- `tasks/../../tasks/done/TASK-MONO-381-web-store-role-guard-vacuous-seed-grants-customer.md` (root) — 이 결함의 출처

# Related Contracts

- 없음 (테스트 전용 변경. 계약·API·이벤트 무영향)

---

# Edge Cases

- **제거 후 CI 에서 실제로 60s 를 넘기면** — 그때는 **패딩이 아니라 측정**이 답이다. 4.0s → 60s+ 는 성능 회귀이지 예산 문제가 아니다. 그 경우 override 를 되돌리지 말고 **원인 티켓**을 끊을 것.
- **`retries: 1`(CI)이 여전히 살아 있다** — 즉 이 스펙이 느려지면 *재시도가 다시 덮을 수 있다*. AC-3 이 `flaky=0` 을 요구하는 이유다. (전역 `retries` 를 건드리는 것은 Out of Scope — 다른 5개 스펙의 문제까지 끌고 들어온다.)

# Failure Scenarios

- **F1 — "초록이니 됐다"** 로 AC-2 를 통과 처리한다. `assert-specs-ran` 표를 안 읽으면, 스펙이 skip 되어도 초록이다. **이 저장소가 이걸로 몇 달을 속았다**(MONO-373).
- **F2 — 로컬에서 재현하려다 또 오염된 시간을 잰다.** 다른 세션/데모 컨테이너가 떠 있으면 Playwright 타이밍은 무의미하다. **로컬 측정으로 이 티켓의 결론을 내리지 말 것.**

---

# Test Requirements

- 새 테스트 없음. 이 티켓의 단언은 **CI 런의 관측**이다(AC-2/AC-3).

---

# Definition of Done

- [ ] AC-1 ~ AC-4.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

자진 신고 — `TASK-MONO-381` 종결 직후 후속을 점검하다 발굴(2026-07-13). 그 task 는 **로컬 Docker 를 살려 e2e 를 직접 돌린 것이 옳았고**(가드가 실제로 문다는 것, 기존 소비자가 안 깨진다는 것을 PR CI 가 증명 못 하는 자리에서 증명했다), **그 과정에서 잰 시간만이 틀렸다.** 오염된 호스트의 숫자를 인과로 승격시킨 것이 유일한 실수다.

**교훈은 "로컬에서 재지 마라" 가 아니다** — 로컬 실행이 이 task 의 가장 큰 값이었다. 교훈은 **"기능은 로컬이 증명할 수 있지만 성능은 아니다"**, 그리고 **"PR 본문에 유보를 적었다면 코드 주석에도 적어라"** 이다. 유보는 리뷰어가 읽고 주석은 다음 구현자가 읽는데, 확신만 코드에 남았다.

분석=Opus 4.8 / 구현 권장=**Haiku** (주석 블록 + 한 줄 제거. 판단은 이미 끝났고 관측만 남았다).
