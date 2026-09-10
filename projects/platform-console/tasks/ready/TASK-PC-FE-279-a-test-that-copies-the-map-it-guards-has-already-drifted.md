# Task ID

TASK-PC-FE-279

# Title

🔴 지키려는 맵을 **복제해 둔** 테스트가 이미 어긋났다 — 그리고 그 파일 스스로 *"소스가 바뀌면 고쳐라"* 라고 적어 뒀다

# Status

ready

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

- [ ] **AC-0 (드리프트 실측 고정)** — 착수 시점에 **다시 세서** 어긋난 항목을 전부
      나열한다. 🔴 위 표의 `not_provisioned` **하나만 고치고 끝내지 마라** —
      기안 시점에 눈으로 본 것이 하나일 뿐, 전수 조사는 안 했다.
      🔵 `GENERIC_ERROR` 와 **키 집합**(소스에만 있는 키·복제본에만 있는 키)도 함께.
- [ ] **AC-1** — 테스트가 **소스의 맵을 import 해서** 검사한다(복제본 삭제).
      🔴 import 가 안 되면(서버 컴포넌트 로드 부작용) **그 사유를 적고**
      `relogin-loop.test.tsx` 가 쓴 방식(페이지를 직접 렌더 + `next/navigation`·세션 mock)
      을 따라라 — 그 파일이 **콘솔에서 진짜 로그인 페이지를 태우는 것이 가능하다는
      증거**다. 파일 머리의 *"cannot import the Next.js page"* 주장은 **낡았다.**
- [ ] **AC-2 (bite)** — 소스의 문구를 **한 글자 바꾸면** 이 테스트가 빨개진다.
      🔴 이것이 이 티켓의 본체다. AC-1 만으로는 「복제를 지웠다」는 알아도
      「이제 드리프트를 문다」는 모른다.
- [ ] **AC-3** — 게이트 3종이 **각각 독립 statement + 명시 `rc=$?`** 로 초록
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

- [ ] 복제본 제거, 소스 기반 검사
- [ ] bite 증명
- [ ] 게이트 3종 통과 — 각각 `rc=$?` 명시
- [ ] Ready for review

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Sonnet** (범위가 한 파일이고 판정 규칙이 위에 박혀 있다)
