# Task ID

TASK-PC-FE-289

# Title

🔴 로그인 픽스처가 **러너가 이미 켠 트레이싱을 또 켜서**, `TASK-PC-FE-282` 의 전환 e2e 가 단언에 닿기도 전에 죽고 `main` nightly 가 빨갛다

# Status

done

# Owner

platform-console

# Task Tags

- test
- e2e
- fix

---

# Goal

**`TASK-PC-FE-282` 의 fix task** (AC-14). 282 머지 커밋 `35bd9d293` 의 `nightly-e2e.yml` 런
[`34971551224`](https://github.com/kanggle/monorepo-lab/actions/runs/34971551224) 에서
`Platform Console E2E full-stack (Playwright + docker compose)` 가 **failure** 다(자동 이슈 #3846). 직전 `main` 커밋 셋
(`4ad767498` · `c8f5d4898` · `7d8312925`)에서는 같은 잡이 **실제로 돌아서 success** 였다 ⇒ 282 가 만든 빨강이다.

실패는 **8 테스트 중 1** — 282 가 새로 실은 `tests/e2e/sample-visitor-transition.spec.ts` 하나이고, 기존 7 은 통과했다.
3회 시도 전부(`××F`) 같은 에러:

```
Error: tracing.start: Tracing has been already started
    at tests/e2e/sample-visitor-transition.spec.ts:31:5
```

**원인 (코드로 확인):**

| 사실 | 근거 |
|---|---|
| CI 에서 러너가 **테스트마다** 컨텍스트 트레이싱을 켠다 | `playwright.config.ts:39` `trace: process.env.CI ? 'on' : 'on-first-retry'` |
| 로그인 픽스처도 CI 에서 **스스로** `context.tracing.start()` 를 부른다 | `tests/e2e/fixtures/login.ts:142-149` — `globalSetup` 은 config 트레이스가 닿지 않아서 넣은 것(TASK-PC-FE-027) |
| 282 의 스펙은 그 픽스처를 **테스트 안에서, 테스트의 `context` 로** 부른 첫 호출자다 | `sample-visitor-transition.spec.ts:31` `loginAsSuperAdmin(context)` |

⇒ 제품 코드 결함이 아니다. 🔴 그러나 결과는 같다 — **AC-14(익명 → 같은 브라우저 로그인 → 샘플 문자열 0)는 한 번도 단언에 닿지 못했다.** 282 의
«⚪ 머지 후 첫 nightly 가 첫 측정» 은 측정이 아니라 **실행 실패**로 끝났다.

---

# Scope

## In Scope

- `tests/e2e/fixtures/login.ts` — 트레이싱을 **자기가 켰을 때만** 끄고, 이미 켜져 있으면 **켜지도 끄지도 않는다**
- 수정 브랜치에서 `nightly-e2e.yml` 을 `workflow_dispatch` 로 돌려 **머지 전에** 콘솔 full-stack 잡을 초록으로 만든다

## Out of Scope

- 제품 코드(`src/**`) — 변경 0
- `sample-visitor-transition.spec.ts` 의 단언 — 변경 0 (그 단언이 이번에 **처음** 돈다)
- `playwright.config.ts` 의 `trace` 정책

---

# Acceptance Criteria

- [ ] **AC-1** 픽스처는 `context.tracing.start()` 가 «이미 시작됨» 으로 거절되면 그 컨텍스트의 트레이싱을 **건드리지 않는다**(stop 도 안 한다 — 러너의 트레이스 산출물을 망가뜨리지 않기 위해). 🔴 그 밖의 에러는 **그대로 던진다**.
- [ ] **AC-2** `globalSetup` 경로(러너 트레이싱 없음)는 **예전 그대로** CI 에서 `test-results/global-setup-driveOidcPkceLogin/trace.zip` 을 남긴다.
- [ ] **AC-3** 수정 브랜치 dispatch 런에서 `Platform Console E2E full-stack` = **success**, 그리고 로그에서 `sample-visitor-transition.spec.ts` 가 **passed** 로 보인다(= 282 AC-14 의 첫 실측).
- [ ] **AC-4** 머지 후 `main` 의 `nightly-e2e` 런에서 같은 잡 success. 자동 이슈 #3846 의 상태를 기록한다.
- [ ] **AC-5** 코드 diff 는 `tests/e2e/fixtures/login.ts` 뿐(+ 이 티켓·INDEX).

---

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` (A10)
- `projects/platform-console/tasks/review/TASK-PC-FE-282-anonymous-visitors-enter-the-real-console-and-the-gateways-answer-with-samples.md` (AC-14)
- `platform/testing-strategy.md`

# Related Contracts

- 없음

---

# Edge Cases

- 🔴 **로컬 게이트가 이 파일을 안 잰다** — `tsconfig.json` 이 `tests/e2e/**` 를 exclude 하고 `next lint` 는 `src` 만 본다. ⇒ 권위는 dispatch 런이다(«로컬 초록» 을 주장하지 않는다).
- 로컬(`CI` 미설정)에서는 픽스처가 원래 트레이싱을 안 켠다 — 변경 없음.
- 한 컨텍스트에서 픽스처를 두 번 부르는 스펙: 첫 호출이 켠 트레이싱은 첫 호출이 끈다(자기가 켠 것만 끈다).

# Failure Scenarios

- 🔴 dispatch 에서 트레이싱은 통과했는데 **AC-14 단언이 빨강**(로그인 뒤에도 배너/«(샘플)» 이 남는다) ⇒ 이것은 **제품 결함**이다. 머지하지 않고 282 계보로 되돌려 보고한다(테스트를 고치지 않는다).
- dispatch 가 무관한 잡에서 빨강 ⇒ 콘솔 full-stack 잡의 결론만으로 판정하고, 무관한 빨강은 원인과 함께 PR 에 적는다.

# Test Requirements

- `nightly-e2e.yml` `workflow_dispatch` (수정 브랜치) — 콘솔 full-stack 잡
- 머지 후 `main` nightly 1회 확인

# Definition of Done

- [ ] AC-1~5
- [ ] `main` 의 콘솔 full-stack nightly 초록

분석=Opus 5 / 구현=Opus 5 (한 파일 수정이지만 판정이 dispatch 런에 달려 있다).

## CORRECTION — 닫기 판정 (조정자, 2026-09-15 UTC)

🔴 위 체크박스는 `[ ]` 로 남아 있다(`review/` 동결). **판정은 아래 표다.**

| AC | 닫힘 | 증거 |
|---|---|---|
| AC-1 | ✅ | `login.ts` — `start` 가 «already started» 로 거절되면 그 컨텍스트를 안 건드리고, 그 밖의 에러는 `throw`. 🔵 dispatch 트레이스에 **그 거절 이벤트 1건**이 찍혀 있고 그 뒤로 테스트가 계속됐다 = 이 경로를 실제로 탔다 |
| AC-2 | ✅ | 같은 런 아티팩트에 `test-results/global-setup-driveOidcPkceLogin/trace.zip` **존재** |
| AC-3 | ✅ | dispatch 런 34974736858 (sha `1ab6bd9e3`) — 콘솔 full-stack **success** · **8 passed** · 잡 로그의 `Tracing has been already started` **0**. 🔴 `list` 리포터는 통과 이름을 안 찍으므로 스펙 확인은 아티팩트로: `sample-visitor-transition…/trace.zip` **1개 · 재시도 폴더 없음** · 이벤트 203(에러 1 = 위 의도된 거절) · `fill` 4 · `goto` 3 · `toHaveCount` 6 · `toContain` 1 |
| AC-4 | ✅ | 머지 커밋 `46fae6fe7` 의 main nightly 34976921564 콘솔 full-stack **success**(다음 런 `5bae1b1f5` 도 success). 🔴 자동 이슈 #3846 은 **13:37:27Z 에 닫혔고 이 머지(13:44:01Z)보다 앞선다** — `nightly-red-arrives` 가 **브랜치 dispatch 런**(`1ab6bd9e3`)의 초록을 보고 닫았다. 결과는 옳았지만 **그 잡이 잰 것은 «main 이 초록» 이 아니다** |
| AC-5 | ✅ | 코드 diff = `tests/e2e/fixtures/login.ts` 한 파일(+ 티켓·INDEX) · PR #3848 |

🔴 **남기는 의무 (루트 후속)**: `nightly-red-arrives`(TASK-MONO-655)는 ref 를 안 보고 초록 런 하나로 이슈를 닫는다 ⇒ «브랜치에서 증명 → 이슈 닫힘 → main 은 아직 빨감» 이 표현 가능하다(이번에 7분간 실제로 그 상태였다). `.github/workflows/` 는 루트 경로이므로 **별도 루트 티켓**으로 기안한다 — 이 절이 그 인계다.
