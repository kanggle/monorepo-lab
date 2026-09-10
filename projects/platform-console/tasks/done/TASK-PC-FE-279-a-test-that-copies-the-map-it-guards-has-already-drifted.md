# Task ID

TASK-PC-FE-279

# Title

🔴 지키려는 맵을 **복제해 둔** 테스트가 이미 어긋났다 — 그리고 그 파일 스스로 *"소스가 바뀌면 고쳐라"* 라고 적어 뒀다

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

`tests/unit/login-error-messages.test.ts` 가 `(auth)/login/page.tsx` 의
`ERROR_MESSAGES` 맵을 **복제**해 두고 그 복제본을 검사한다. 파일 머리에 이렇게 적혀 있다:

> The ERROR_MESSAGES map and GENERIC_ERROR constant are kept in
> `app/(auth)/login/page.tsx` (server component). We replicate the lookup logic
> here … **If the map or constant changes in the source, update this file accordingly.**

🔴🔴 **그 지시는 지켜지지 않았고, 그것을 추측이 아니라 실측으로 안다** —
`TASK-PC-FE-278` 작업 중 두 파일을 나란히 열었더니 `not_provisioned` 이 이미 다르다:

| 자리 | 문구 |
|---|---|
| **소스** (`page.tsx`) | *"아직 소속된 조직이 없습니다. 다시 로그인하면 조직 만들기로 안내됩니다."* |
| **테스트 복제본** | *"운영자 권한이 없는 계정입니다. 관리자에게 권한 부여를 요청하세요."* |

소스 쪽 주석이 그 변경의 이유를 적어 뒀다(`TASK-PC-FE-182` / `ADR-MONO-044` — 운영자가
아닌 로그인 사용자는 `/onboarding` 으로 가므로 콜백이 `not_provisioned` 을 더는 내지
않고, 항목은 손으로 만든 URL 용 방어 폴백으로만 남는다). **테스트는 그 변경을 못 따라왔다.**

🔵 **이 테스트는 초록이다. 앞으로도 계속 초록일 것이다** — 자기 복제본을 검사하니까.
그래서 이 결함은 **테스트가 빨개지는 방식으로는 절대 드러나지 않는다.**

---

# Scope

## In Scope

- `tests/unit/login-error-messages.test.ts` 가 **소스의 맵을 실제로 태우도록** 바꾼다.
- 그 결과로 드러나는 문구 불일치를 **소스 기준**으로 정리한다(소스가 정본이다).

## Out of Scope

- `ERROR_MESSAGES` 문구 자체의 변경 — 소스가 정본이고 이 티켓은 정본을 안 건드린다.
- 다른 복제-검사 테스트 찾기. 🔵 **하고 싶으면 별도 티켓** — 여기서 같이 하면
  「한 결함 고치기」와 「전수 조사」가 한 PR 에 섞이고, 후자는 모집단 정의부터 필요하다.
- `TASK-PC-FE-278` 이 넣은 `session_expired` 항목 — 그것은 **진짜 페이지를 태우는**
  신규 스위트(`relogin-loop.test.tsx`)가 이미 덮는다.

---

# Acceptance Criteria

- [x] **AC-0 (드리프트 실측 고정)** — 착수 시점에 **다시 세서** 어긋난 항목을 전부
      나열한다. 🔴 위 표의 `not_provisioned` **하나만 고치고 끝내지 마라** —
      기안 시점에 눈으로 본 것이 하나일 뿐, 전수 조사는 안 했다.
      🔵 `GENERIC_ERROR` 와 **키 집합**(소스에만 있는 키·복제본에만 있는 키)도 함께.
- [x] **AC-1** — 테스트가 **소스의 맵을 import 해서** 검사한다(복제본 삭제).
      🔴 import 가 안 되면(서버 컴포넌트 로드 부작용) **그 사유를 적고**
      `relogin-loop.test.tsx` 가 쓴 방식(페이지를 직접 렌더 + `next/navigation`·세션 mock)
      을 따라라 — 그 파일이 **콘솔에서 진짜 로그인 페이지를 태우는 것이 가능하다는
      증거**다. 파일 머리의 *"cannot import the Next.js page"* 주장은 **낡았다.**
- [x] **AC-2 (bite)** — 소스의 문구를 **한 글자 바꾸면** 이 테스트가 빨개진다.
      🔴 이것이 이 티켓의 본체다. AC-1 만으로는 「복제를 지웠다」는 알아도
      「이제 드리프트를 문다」는 모른다.
- [x] **AC-3** — 게이트 3종이 **각각 독립 statement + 명시 `rc=$?`** 로 초록
      (`tsc --noEmit` · `next lint` · `vitest run`), 회귀 0.
      🔵 판정은 rc 가 아니라 **몇 개가 돌았나**로 한다(기준선: 292 files / 3004 tests).

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 —
> `PROJECT.md`(`domain: saas`, `traits: [multi-tenant, integration-heavy, audit-heavy]`)
> → `rules/common.md` → 선언된 domain/trait 파일.

- `projects/platform-console/PROJECT.md`
- `TASK-PC-FE-278` (이 드리프트를 발견한 티켓 — `tasks/done/`)
- `TASK-PC-FE-182` / `ADR-MONO-044` (소스 문구가 바뀐 이유)

# Related Contracts

- 없음.

---

# Target App

- `projects/platform-console/apps/console-web`

---

# Implementation Notes

- 🔴 **이 티켓의 교훈은 「복제하지 마라」가 아니다** — 복제는 그때 이유가 있었다
  (서버 컴포넌트를 vitest 에서 못 태운다고 믿었다). 교훈은 **그 이유가 낡았는데
  아무것도 그것을 알려주지 않았다**는 것이다. 복제본은 자기를 검사하므로 영원히 초록이다.
- 🔵 그래서 AC-2(bite)가 AC-1 보다 중요하다. 복제를 지우고도 **소스를 안 태우면**
  같은 자리에 같은 결함이 다시 선다.

---

# Edge Cases

- 소스에만 있는 키 / 복제본에만 있는 키 → 둘 다 AC-0 에 적는다.
- 서버 컴포넌트 import 가 다른 모듈(`DemoBackendNotice` 등)을 끌고 온다
  → `relogin-loop.test.tsx` 처럼 mock 한다. 🔴 **동기 컴포넌트로 mock 해라** —
  `async () => null` 은 React 가 async Client Component 로 읽어 suspend 하고
  렌더 결과가 통째로 비어 `host` 조차 못 찾는다(`278` 이 실제로 밟았다).

---

# Failure Scenarios

- **문구를 «테스트에 맞춰» 고친다** → 🔴 방향이 반대다. 소스가 정본이고,
  복제본의 문구는 `ADR-MONO-044` 이전의 화석이다.
- **AC-1 만 하고 AC-2 를 건너뛴다** → 초록이지만 공허하다(이 티켓이 고치는 상태 그대로).

---

# Test Requirements

- `login-error-messages.test.ts` 가 소스를 태운다
- bite: 소스 문구 한 글자 변경 → 빨개짐

---

# Definition of Done

- [x] 복제본 제거, 소스 기반 검사
- [x] bite 증명
- [x] 게이트 3종 통과 — 각각 `rc=$?` 명시
- [x] Ready for review

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Sonnet** (범위가 한 파일이고 판정 규칙이 위에 박혀 있다)

---

# 구현 기록 (ready → review, 2026-09-10 UTC)

## § AC-0 — 드리프트를 **기계로** 다시 셌다

기안은 눈으로 본 `not_provisioned` 하나만 갖고 있었고, AC-0 이 *"하나만 고치고
끝내지 마라"* 라고 스스로 경고했다. 두 파일에서 `key → 문자열` 을 추출해 대조:

```
소스 키 7개 · 복제본 키 6개

🔴 SOURCE-ONLY SESSION_EXPIRED
     소스 : 세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요.
🔴 MISMATCH   not_provisioned
     소스   : 아직 소속된 조직이 없습니다. 다시 로그인하면 조직 만들기로 안내됩니다.
     복제본 : 운영자 권한이 없는 계정입니다. 관리자에게 권한 부여를 요청하세요.

GENERIC_ERROR : ✅ 일치
합계 — 일치 5 · 값 불일치 1 · 소스에만 1 · 복제본에만 0
```

🔴🔴 **`session_expired` 공백은 `TASK-PC-FE-278` 이 같은 날 만들었다.** 소스에 키를
넣고 이 복제본을 안 고쳤다 — 그 세션은 이 파일의 머리 주석(*"소스가 바뀌면 이 파일도
고쳐라"*)을 **읽었고 티켓 본문에 인용까지 했다.**

> **규율이 실패한 대상이 「그것을 방금 읽은 사람」이었다.**
> 그리고 실패해도 스위트는 초록이었다 — 자기 복제본을 검사하니까.

이것이 이 티켓의 근거로서 기안 시점에 갖고 있던 어떤 것보다 강하다.

### 도달 가능한 코드 전수 (AC-0 의 「키 집합」 축)

| 코드 | 어디서 나오나 | 매핑 |
|---|---|---|
| `provider_error` | `callback/route.ts:83` | ✅ |
| `invalid_state` | `:93` | ✅ |
| `state_mismatch` | `:99` | ✅ |
| `token_exchange_failed` | `:132` · `:237` | ✅ |
| `operator_exchange_unavailable` | `:204` | ✅ |
| `session_expired` | `(console)` 아래 **53개 화면** (`TASK-PC-FE-278` 마커) | ✅ |
| `not_provisioned` | 🔵 **더 이상 안 나온다** — `:187` 이 `/onboarding` 으로 보낸다 | ✅ (방어용) |

⇒ **도달 가능 6 · 미매핑 0.** 🔵 `not_provisioned` 은 매핑돼 있으나 도달 불가이고,
그것은 소스 주석이 적어 둔 의도(`ADR-MONO-044`)와 일치한다.

## § AC-1 — 복제본을 지우고 **진짜 페이지를 태운다**

- `tests/unit/login-error-messages.test.ts` **삭제** → `.test.tsx` 로 신설(렌더가 필요).
- 페이지를 직접 렌더하고 `role="alert"` 로 문구를 읽는다.
  🔵 **소스는 한 줄도 안 고쳤다** — 페이지가 이미 `role="alert"` 를 달고 있어
  `data-testid` 를 새로 심을 이유가 없었다.
- 🔵 파일 머리의 *"cannot import the Next.js page"* 주장은 낡았다는 것이 확인됐다
  (`relogin-loop.test.tsx` 가 이미 하고 있었고, 여기서도 된다).

### 🔵 핀은 남겼다 — 그리고 그것이 복제본과 다른 이유

새 파일에도 문자열이 적혀 있다(`EXPECTED`). 차이는 **무엇과 대조되는가**다:

| | 옛 판 | 지금 |
|---|---|---|
| 비교 대상 | 복제본을 **복제본의 resolve 로직**에 먹임 | 핀을 **진짜 페이지가 렌더한 DOM** 과 대조 |
| 소스가 계산에 들어가나 | **아니오** | **예** |
| 소스가 바뀌면 | 조용히 통과 | **빨개진다** |

대가는 알고 받는다 — 문구를 정당하게 고치면 핀도 같이 고쳐야 한다. 그것이 의도다
(형제 `DemoBackendNotice.test.tsx` 가 *"배너의 문장이 계약이다"* 로 같은 선택을 했다).

## § AC-2 — bite **3종**

```
① 한 글자 (provider_error 의 "IAM" → "IAN")
     rc=1  →  1 failed | 14 passed      ← 바뀐 키 «하나만» 문다
② 화석 복원 (not_provisioned 을 옛 복제본 문구로)
     rc=1  →  2 failed | 13 passed      ← 핀 칸 + 의미 칸이 «동시에» 문다
③ 소스에만 키 추가 (brand_new_code)
     rc=1  →  1 failed | 15 passed      ← 키-집합 칸이 처방 메시지와 함께 문다
```

🔴🔴 **③ 은 원래 계획에 없었다.** ①②를 통과시킨 뒤 다시 물었다 — *"이 스위트가
`session_expired` 드리프트를 잡았을까?"* 답은 **아니오**였다: 핀 칸들은 `EXPECTED` 에
**있는** 키만 렌더하므로, 소스가 키를 새로 얻으면 아무 칸도 안 돈다. callback 커버리지
칸도 못 잡는다(그 코드는 callback 이 아니라 53개 화면에서 온다).
⇒ **소스 맵의 키 집합을 직접 읽어 등호로 맞추는 칸**을 추가해 그 구멍을 닫았다.
**이 스위트는 이제 자기가 태어난 이유가 된 그 드리프트를 잡는다.**

🔴 `git checkout --` 은 쓰지 않았다(미커밋분을 지운다). 파일 복사로 되돌렸고 세 번 모두
복원 후 **바이트 동일성**을 확인했다.

## § AC-3 — 게이트 (각각 독립 statement, 파이프 없음)

```
tsc --noEmit   rc=0
next lint      rc=0   ✔ No ESLint warnings or errors
vitest run     rc=0   292 files / 3010 tests   (이전 292 / 3004)
```

🔵 **판정은 rc 가 아니라 「몇 개가 돌았나」** — 파일 수는 그대로(`.ts` 하나가 빠지고
`.tsx` 하나가 들어왔다), 칸은 **3004 → 3010**(신규 16 − 옛것 10). **회귀 0.**
🔵 `next build` 는 안 돌렸다 — **앱 코드가 한 줄도 안 바뀌었기 때문**이고(변경은
테스트 파일 하나뿐) AC-3 도 3종만 요구한다.

## 🔴 내가 틀렸던 것 — 술어 두 개

첫 실행에서 3칸이 빨갰고 **셋 다 테스트의 결함**이었지 소스의 결함이 아니었다.

1. **`next/link` 목이 props 를 버렸다.** `{children, href}` 만 받아 `data-testid` 가
   사라졌고, 대조군이 *"경고가 없다"* 대신 *"버튼을 못 찾겠다"* 로 죽었다
   ⇒ 「조건이 거짓」과 「렌더가 죽었다」가 구별 안 되는 그 모양이다.
2. 🔴🔴 **커버리지 술어를 인자의 «모양» 에 걸었다.**
   `loginRedirect\([^)]*?'…'` 였는데 첫 인자가 `publicOrigin(env)` 라 그 안의 `)` 에
   걸려 **0건**을 냈다. **비공허성 칸이 그것을 잡았다**(`expected 0 to be greater
   than 0`) — 없었으면 «미매핑 0» 이 **아무것도 안 본 초록**으로 통과했다.
   고친 술어는 인자 모양에 무관하다(선언 제외 + 호출 뒤 120자 안의 첫 리터럴).
   검증: 호출 **6곳** / 사유 **5종** 추출 — 수기 판독과 일치.

## 안 잰 것

- **다른 복제-검사 테스트**는 안 찾았다(티켓 Out of Scope — 전수 조사는 모집단 정의부터
  필요하고, 그것은 별도 티켓이다).
- **문구 자체의 적절성**은 판단하지 않았다. 소스가 정본이고 이 티켓은 정본을 안 건드렸다.
- `session_expired` 를 내보내는 **53개 화면**이 실제로 그 코드를 붙이는지는 여기서 다시
  재지 않았다 — `relogin-marker.test.ts`(`TASK-PC-FE-278`)가 그 축의 가드다.
