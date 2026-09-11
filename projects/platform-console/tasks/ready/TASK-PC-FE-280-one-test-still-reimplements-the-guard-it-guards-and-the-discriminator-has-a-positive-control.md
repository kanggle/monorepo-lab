# Task ID

TASK-PC-FE-280

# Title

테스트 하나가 **가드하려는 로직을 아직도 재구현**한다 — 그리고 그것을 찾는 술어에는 **양성 대조군이 있다**

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

`TASK-PC-FE-279` 는 *"다른 복제-검사 테스트 찾기 → 별도 티켓"* 이라고 **산문으로** 미뤘다.
🔴 산문은 큐가 아니다. close chore 가 그 자리에서 **세었고**, 세었더니 미룰 일이 아니라
**이미 한 건이 확정된 일**이었다.

## close chore 가 잰 것 (2026-09-10 UTC, `9bffd85a1` 트리)

| 축 | 값 |
|---|---:|
| 자기선언 복제 문구(`replicate`·`mirrors the`·`keep in sync`·`update this file accordingly`·`copy of the`·`duplicated from`)가 있는 테스트 파일 | **14** |
| 그중 `@/` 에서 **아무것도 import 하지 않는** 것 | **1** |

**그 하나 = `tests/unit/layout-login-redirect.test.ts`.** 파일이 스스로 이렇게 적어 뒀다:

> */** Mirrors the sanitisation logic in layout.tsx `buildLoginRedirect()`. */*

그리고 그 아래에 `buildLoginRedirectFrom()` 을 **로컬에 재구현**해 두고 그것을 검사한다.
⇒ `(console)/layout.tsx` 의 진짜 `buildLoginRedirect()` 가 **계산에 한 번도 안 들어간다.**
layout 의 규칙이 바뀌어도 이 스위트는 **초록이다.**

## 🟢 술어에 **양성 대조군**이 있다 — 이것이 이 티켓의 근거다

`@/` import **0건** 이라는 판별자를, `TASK-PC-FE-279` 가 **이미 고친** 결함에 대고 재봤다:

```
git show 9bffd85a1^:…/tests/unit/login-error-messages.test.ts | grep -c "from '@/"
→ 0
```

⇒ **그 술어는 279 가 고친 그 결함을 실제로 잡았을 것이다.** 추측으로 고른 판별자가 아니라
**알려진 양성에 대고 검증한** 판별자다.

## 🔴 그러나 이 숫자는 «출발 모집단» 이지 판정이 아니다

- **14 는 느슨하다** — 선의의 `mirrors the` 주석과 진짜 복제본을 못 가른다.
- **13 이 «깨끗하다» 는 뜻이 아니다** — `@/` 에서 *무언가* 를 import 한다고 해서
  **자기가 검사하는 그 상수**를 import 한다는 보장은 없다. 한 모듈을 import 하면서
  다른 모듈의 상수를 복제할 수 있다.
- 🔴 **자기선언이 없는 복제본은 이 grep 에 안 걸린다.** 즉 이 14 는 하한이고,
  *"복제본은 14개 이하다"* 라고 읽으면 틀린다.

---

# Scope

## In Scope

- `tests/unit/layout-login-redirect.test.ts` — 재구현을 걷어내고 **진짜 로직**을 태운다.
- AC-0 에서 나머지 13개를 **한 번 훑어** 같은 부류가 더 있는지 판정한다(판정만).

## Out of Scope

- **13개를 고치기.** AC-0 이 결함으로 판정한 것이 있으면 **별도 티켓**으로 기안한다
  (`TASK-MONO-632` 관례 — 재는 것과 고치는 것을 한 PR 에 섞지 않는다).
- 🔴 **`buildLoginRedirect` 를 `sanitizeReturnPath` 로 통일하기.** close chore 가
  둘을 나란히 읽었고 **규칙이 다르다**(`sanitizeReturnPath` 는 `/\` 를 거르고
  `buildLoginRedirect` 는 안 거른다). 🔵 **그것은 결함이 아니다** — layout 은
  `?redirect=` 를 **만드는** 쪽이고, 소비자(`/login` 페이지·`/api/auth/login`)가
  `sanitizeReturnPath` 로 **다시** 거른다. 통일은 리팩토링 결정이지 이 티켓의 수리가
  아니다. 🔴 **여기서 «보안 결함» 이라고 적지 마라 — 실측으로 아니다.**
- 다른 앱(web-store·fan)의 같은 부류 — 모집단이 다르다.

---

# Acceptance Criteria

- [ ] **AC-0 (모집단 재측정 + 판정)** — 착수 시점 트리에서 위 두 숫자를 **다시 세고**,
      나머지 13개를 열어 *"자기가 검사하는 대상을 실제로 태우는가"* 를 파일별로 판정한다.
      🔴 **`@/` import 유무로 판정하지 마라** — 그것은 **찾는** 술어이지 **판정하는**
      술어가 아니다(한 모듈을 import 하면서 다른 상수를 복제할 수 있다).
      🔵 판정 불가면 ⚪ 로 남기고 **왜** 인지 적어라.
- [ ] **AC-1** — `layout-login-redirect.test.ts` 가 `layout.tsx` 의 **진짜** 로직을
      태운다. 🔴 로컬 재구현(`buildLoginRedirectFrom`) **삭제**.
      🔵 함수가 서버 컴포넌트 안의 비-export 라 직접 import 가 안 되면,
      `login-error-messages.test.tsx`(279)가 쓴 길을 따라라 — **레이아웃을 렌더**하거나,
      아니면 그 함수를 `shared/lib/` 로 **뽑아** 양쪽이 같은 것을 쓰게 하라.
      🔴 후자를 고르면 그것은 **소스 변경**이므로 `next build` 게이트가 추가된다.
- [ ] **AC-2 (bite)** — `layout.tsx` 의 sanitisation 규칙을 **한 줄 바꾸면** 이 스위트가
      빨개진다. 🔴 이것이 본체다. AC-1 만으로는 「재구현을 지웠다」는 알아도
      「이제 드리프트를 문다」는 모른다.
- [ ] **AC-3 (음성 대조군)** — 기존 8칸(`//evil`·`http://evil`·`/login` 자기참조 등)이
      **그대로 초록**이다. 🔴 재구현을 걷어내면서 커버리지를 줄이면 «고쳤다» 가
      «덜 잰다» 로 바뀐다.
- [ ] **AC-4** — 게이트가 **각각 독립 statement + 명시 `rc=$?`** 로 초록
      (`tsc --noEmit` · `next lint` · `vitest run`, 소스를 건드렸으면 `next build` 추가).
      🔵 판정은 rc 가 아니라 **몇 개가 돌았나**(기준선: **292 files / 3010 tests**).

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 —
> `PROJECT.md`(`domain: saas`, `traits: [multi-tenant, integration-heavy, audit-heavy]`)
> → `rules/common.md` → 선언된 domain/trait 파일.

- `projects/platform-console/PROJECT.md`
- `TASK-PC-FE-279` (`tasks/done/`) — 같은 부류의 첫 건, 그리고 이 티켓 술어의 **양성 대조군**
- `TASK-PC-FE-115` (Gap D / F6 — 이 테스트가 태어난 티켓)
- `TASK-PC-FE-253` (`sanitizeReturnPath` 공유 술어 — § Out of Scope 의 근거)

# Related Contracts

- 없음.

---

# Target App

- `projects/platform-console/apps/console-web`

---

# Implementation Notes

- 🔴 **279 가 남긴 교훈을 그대로 쓴다**: 「복제를 지웠다」와 「이제 드리프트를 문다」는
  다른 명제이고, 후자는 **bite 로만** 증명된다.
- 🔵 **핀을 남기는 것 자체는 결함이 아니다** — 279 가 그렇게 했다. 결함은 **핀이
  소스가 아니라 자기 사본과 대조되는 것**이다.
- 🔵 279 에서 실제로 밟은 함정 둘을 미리 피해라: ① `next/link` 목이 props 를 버리면
  `data-testid` 가 사라져 「조건이 거짓」과 「렌더가 죽었다」가 구별 안 된다
  ② 소스를 스캔하는 술어를 **인자의 모양**에 걸면 조용히 0건을 낸다 —
  **비공허성 칸을 반드시 같이 둬라**(279 에서 그 칸이 실제로 잡았다).

---

# Edge Cases

- `buildLoginRedirect` 가 `headers()` 를 읽으므로 렌더 경로를 고르면 `next/headers` 목이 필요하다.
- `shared/lib/` 추출을 고르면 layout 의 import 가 늘고 **`next build` 게이트가 붙는다**.
- 나머지 13개 중 판정 불가가 나오면 ⚪ + 사유(추측을 판정으로 적지 마라).

---

# Failure Scenarios

- **재구현만 지우고 커버리지를 줄인다** → AC-3 이 잡는다.
- **`@/` import 유무를 판정 술어로 쓴다** → 13개를 «깨끗하다» 로 오판한다(AC-0 이 금지).
- **`sanitizeReturnPath` 와의 규칙 차이를 결함으로 적는다** → 실측으로 아니다
  (소비자가 다시 거른다). § Out of Scope 참조.

---

# Test Requirements

- `layout-login-redirect` 가 진짜 로직을 태운다
- bite: 소스 규칙 한 줄 변경 → 빨개짐
- 기존 8칸 초록 유지

---

# Definition of Done

- [ ] 재구현 제거, 소스 기반 검사
- [ ] bite 증명
- [ ] 음성 대조군 유지 확인
- [ ] 게이트 통과 — 각각 `rc=$?` 명시
- [ ] Ready for review

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Sonnet** (한 파일 + 판정 규칙이 위에 박혀 있다.
🔴 단 AC-1 에서 «`shared/lib/` 추출» 을 고르게 되면 소스 구조 변경이므로 **Opus**)
