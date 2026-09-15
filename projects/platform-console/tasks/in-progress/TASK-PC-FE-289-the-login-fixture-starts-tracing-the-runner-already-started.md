# Task ID

TASK-PC-FE-289

# Title

🔴 로그인 픽스처가 **러너가 이미 켠 트레이싱을 또 켜서**, `TASK-PC-FE-282` 의 전환 e2e 가 단언에 닿기도 전에 죽고 `main` nightly 가 빨갛다

# Status

in-progress

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
