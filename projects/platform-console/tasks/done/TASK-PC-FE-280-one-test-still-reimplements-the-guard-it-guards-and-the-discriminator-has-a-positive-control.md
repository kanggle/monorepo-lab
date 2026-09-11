# Task ID

TASK-PC-FE-280

# Title

테스트 하나가 **가드하려는 로직을 아직도 재구현**한다 — 그리고 그것을 찾는 술어에는 **양성 대조군이 있다**

# Status

done

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

- [x] **AC-0 (모집단 재측정 + 판정)** — 착수 시점 트리에서 위 두 숫자를 **다시 세고**,
      나머지 13개를 열어 *"자기가 검사하는 대상을 실제로 태우는가"* 를 파일별로 판정한다.
      🔴 **`@/` import 유무로 판정하지 마라** — 그것은 **찾는** 술어이지 **판정하는**
      술어가 아니다(한 모듈을 import 하면서 다른 상수를 복제할 수 있다).
      🔵 판정 불가면 ⚪ 로 남기고 **왜** 인지 적어라.
- [x] **AC-1** — `layout-login-redirect.test.ts` 가 `layout.tsx` 의 **진짜** 로직을
      태운다. 🔴 로컬 재구현(`buildLoginRedirectFrom`) **삭제**.
      🔵 함수가 서버 컴포넌트 안의 비-export 라 직접 import 가 안 되면,
      `login-error-messages.test.tsx`(279)가 쓴 길을 따라라 — **레이아웃을 렌더**하거나,
      아니면 그 함수를 `shared/lib/` 로 **뽑아** 양쪽이 같은 것을 쓰게 하라.
      🔴 후자를 고르면 그것은 **소스 변경**이므로 `next build` 게이트가 추가된다.
- [x] **AC-2 (bite)** — `layout.tsx` 의 sanitisation 규칙을 **한 줄 바꾸면** 이 스위트가
      빨개진다. 🔴 이것이 본체다. AC-1 만으로는 「재구현을 지웠다」는 알아도
      「이제 드리프트를 문다」는 모른다.
- [x] **AC-3 (음성 대조군)** — 기존 8칸(`//evil`·`http://evil`·`/login` 자기참조 등)이
      **그대로 초록**이다. 🔴 재구현을 걷어내면서 커버리지를 줄이면 «고쳤다» 가
      «덜 잰다» 로 바뀐다.
- [x] **AC-4** — 게이트가 **각각 독립 statement + 명시 `rc=$?`** 로 초록
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

- [x] 재구현 제거, 소스 기반 검사
- [x] bite 증명
- [x] 음성 대조군 유지 확인
- [x] 게이트 통과 — 각각 `rc=$?` 명시
- [x] Ready for review

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Sonnet** (한 파일 + 판정 규칙이 위에 박혀 있다.
🔴 단 AC-1 에서 «`shared/lib/` 추출» 을 고르게 되면 소스 구조 변경이므로 **Opus**)

---

# 구현 기록 (ready → review, 2026-09-11 UTC)

## § AC-0 — 모집단 재측정 + **열어서** 판정

착수 시점 트리(`4c235631b`)에서 다시 세니 기안과 같다: 문구 매치 **14** · `@/` import
0건 **1**(`layout-login-redirect.test.ts`).

🔴 **나머지 13개는 `@/` import 유무로 판정하지 않았다**(AC-0 이 금지한 그것). 각 파일의
*"mirrors/replicate"* 문장을 읽고, 애매한 것은 열었다:

| 부류 | 파일 | 판정 |
|---|---|---|
| *"형제 테스트의 구조를 따른다"* | `sidebar-drilldown` · `tenants-page` · `tenants-detail-page` · `operator-groups-page` · `erp-read-model-proxy` · `WmsRecentAdjustments` · `wms-shipments-state` · `OperatorProfileEditDialog` | ✅ 이 결함 부류 아님 — 복제 대상이 **로직이 아니라 테스트 구성**이다 |
| *"BE Javadoc 을 mirror"* | `domain-health-api` · `operator-overview-api` | ✅ **열어서 확인** — 둘 다 `fetchDomainHealth` / `fetchOperatorOverview` 를 `@/` 에서 import 해 **실제로 호출**한다. mirror 는 «단언이 인코딩한 생산자 계약» 을 가리킨다 |
| 상수/픽스처 | `ecommerce-images-upload` · `me-profile-route` | ✅ **열어서 확인** — 전자는 `IMAGE_MAX_BYTES` 를 **소스에서 import** 해 리터럴과 대조(핀), 후자는 라우트가 **실제로 보낸 body** 를 단언 |
| 279 가 고친 것 | `login-error-messages` | ✅ 소스를 태운다 |

⇒ **이 결함 부류는 14개 중 정확히 1개.**
🔵 그 결과가 기안의 경고를 확증한다 — `@/` import 는 **찾는** 술어로는 정확했지만
(1건을 정확히 집었다), **판정하는** 술어로 썼다면 13개를 «깨끗하다» 로 넘겼을 것이고
그 판단은 근거가 달랐다(실제로는 열어 봐야 알 수 있었다).

## § AC-1 — 고친 것은 테스트가 아니라 **정의의 수**

`buildLoginRedirect()` 의 **순수한 부분**을 `src/shared/lib/login-redirect.ts` 로 뺐다.
layout 은 헤더를 읽어 그 함수에 넘기기만 한다. 이제 테스트와 제품이 **같은 함수**를 쓴다.

🔵 **`return-path.ts` 에 합치지 않았다** — 그 파일이 이미 그 결정을 적어 뒀다:
*"The layout guard `buildLoginRedirect()` … is the PRODUCE side … so it stays a
**deliberately separate, stricter predicate** rather than a call site here."*
합쳤으면 기록된 결정을 거스르는 것이다. 별도 모듈의 doc 에 그 인용을 박아 뒀다.

🔵 **레이아웃 렌더 대신 추출을 고른 이유**: layout 을 렌더하려면 `next/headers` ·
세션 4종 · `getCatalog` · `listOrgNodes` · 위젯 다수를 목해야 하고, 그러면 테스트의
주제가 «리다이렉트 규칙» 이 아니라 «레이아웃 조립» 이 된다. 추출은 테스트가 원래
재려던 것(`(raw) => string`)과 모양이 같다.

## § AC-3 — 커버리지를 **줄이지 않았다**

기존 **10칸 전부 유지**(`//evil` · `http://evil` · `https://evil` · `/login` 자기참조 ·
`/api/**` · null · 쿼리 보존 · 인코딩 · 파라미터 이름). 바뀐 것은 **함수 이름 하나**다.
추가된 것은 § 배선 **3칸**.

## § AC-2 — bite **2종**

```
① 규칙 한 줄 변경 (login-redirect.ts 에서 `//` 거부 제거)
     rc=1 → 1 failed | 12 passed     ← 「rejects open-redirect via // prefix」 만
② layout 에 규칙 재인라인 (= 이 티켓의 수정을 되돌린 세계)
     rc=1 → 2 failed | 11 passed     ← § 배선 칸 둘, 메시지가 발견한 조각 셋을 나열
```

🔴🔴 **② 가 계획에 없던 칸을 낳았다.** ①만 있으면 「테스트가 진짜 규칙을 잰다」는 알아도
**「layout 이 그 규칙을 쓴다」는 모른다** — 누군가 규칙을 layout 안에 다시 인라인해도
기능 10칸은 전부 초록이다. 그러면 **이 티켓이 고친 결함이 그대로 돌아온다.**
⇒ § 배선 절(비공허성 1 + import·호출 1 + 재인라인 금지 1)을 더했다.

🔴 `git checkout --` 은 안 썼다. 파일 복사로 되돌렸고 두 파일 모두 복원 후 **바이트
동일성**을 확인했다.

## § AC-4 — 게이트 (각각 독립 statement, 파이프 없음)

```
tsc --noEmit    rc=0
next lint       rc=0   ✔ No ESLint warnings or errors
vitest run      rc=0   292 files / 3013 tests   (기준선 292 / 3010)
next build      rc=0   ← 🔴 소스를 건드렸으므로 AC-4 가 요구한 추가 게이트
```

🔵 칸 수 **3010 → 3013**(배선 3칸), 파일 수 불변, **회귀 0**.

### 🔴 vitest 첫 전체 실행이 2칸 빨갰다 — 내 것이 아니다

`CreateOrganizationForm` · `AccountSelfService`, 지문은 `Test timed out in 5000ms` +
`Not implemented: navigation (except hash changes)`. 판별: **격리 재실행 2회 모두
9/9 초록** → **전체 재실행 292/292 · 3013/3013 초록**. 두 파일은 온보딩·계정
셀프서비스이고 내 변경(순수 함수 추출 + 그 테스트)과 닿지 않는다.
🔵 **원인은 단정하지 않는다** — 이 저장소의 콘솔 flake 카탈로그 ④(*"격리 실행 시 통과,
full-suite 순서/공유상태에서만 간헐"*)와 지문이 같다는 것까지만 적는다.

## 🔴 안 잰 것

- **`layout.tsx` 를 렌더해서** 리다이렉트가 실제로 일어나는지는 안 쟀다. § 배선 절은
  **소스를 읽어** import·호출을 확인할 뿐이다 — 「배선돼 있다」와 「런타임에 그 경로를
  탄다」는 다른 명제다. 후자는 e2e(`console-guard.spec.ts`)가 덮는 축이다.
- **나머지 13개를 «완전히» 감사하지 않았다** — 각 파일의 mirror 문장을 읽고 애매한 넷을
  열었다. 파일 전체를 줄 단위로 읽지는 않았으므로, 문장이 가리키지 않는 **다른** 복제가
  숨어 있을 가능성은 배제하지 못한다.
- **자기선언이 없는 복제본** — 기안이 적은 그대로, 이 모집단은 **하한**이다.
