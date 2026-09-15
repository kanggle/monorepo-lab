# Task ID

TASK-MONO-685

# Title

🔴 콘솔 `(console)` 레이아웃의 **카탈로그 401** 분기가 아직 사유 표지 없이 `/login?redirect=` 로 보낸다 — `TASK-PC-FE-278` 이 53곳에서 끊은 재로그인 루프의 **남은 한 자리**

# Status

ready

# Owner

monorepo

# Task Tags

- console
- auth
- relogin-loop

---

# Goal

`TASK-MONO-674` 구현(2026-09-15)이 같은 파일을 고치다 발견했다. 674 는 **액세스 쿠키가 없을 때**의 분기만 고쳤고,
그 바로 아래 **쿠키는 있는데 백엔드가 401 을 주는** 분기는 건드리지 않았다(범위 밖).

| 분기 | 지금 | 기대 |
|---|---|---|
| 쿠키 없음 + 리프레시 쿠키 있음 | 🟢 674 가 갱신 핸들러로 보낸다 | — |
| **쿠키 있음 + `getCatalog()` 401** | 🔴 `redirect(await buildLoginRedirect())` = `/login?redirect=…` (표지 없음) | `/login?error=session_expired…` (`RE_LOGIN_PATH`) |

🔴 왜 루프인가: `/login` 은 쿠키가 살아 있으면 `/console` 로 되돌린다(`TASK-MONO-660` AC-2 가 라이브로 확인한 «편의»).
표지가 없으면 그 편의가 발동해 `/console` → 레이아웃 → 카탈로그 401 → `/login` → `/console` … 이다.
`TASK-PC-FE-278` 이 서버 401 지점 53곳에서 바로 이것을 표지로 끊었다.

🔴 왜 기존 가드가 못 봤나: `tests/unit/relogin-marker.test.ts` 는 리터럴 `redirect('/login')` 을 찾는데
이 자리는 `redirect(await buildLoginRedirect())` 라 **문자열이 달라 모집단 밖**이다.

---

# Scope

## 포함

- 레이아웃의 카탈로그 401 분기를 재로그인 표지 경로로 바꾼다.
- 이 부류가 다시 가드 모집단 밖으로 새지 않게 한다(리터럴이 아니라 «표지 없는 `/login` 행 redirect» 를 무는 술어).

## 제외

- 유휴 만료(`TASK-MONO-674`).
- 53곳의 기존 수정(`TASK-PC-FE-278`).

---

# Acceptance Criteria

- [ ] **AC-0 — 재측정.** `projects/platform-console/apps/console-web/src/app/(console)/layout.tsx` 에서 `getCatalog()` 401 을 잡는 자리와 그 redirect 를 **직접 읽어라**(보고한 두 에이전트는 `:102-103` 근처라고 했다 — 674 머지 뒤 줄 번호가 움직였을 것이다). 이미 표지를 달았으면 phantom 으로 닫는다.
- [ ] **AC-1 — 고친다.** 그 분기가 `RE_LOGIN_PATH`(또는 674 가 만든 `session_expired&redirect=` 형태)로 간다. 🔴 문자열을 손으로 쓰지 말고 상수를 쓴다.
- [ ] **AC-2 — 루프가 끊겼음을 실행으로 본다.** 쿠키가 있는 상태 + 카탈로그 401 을 주는 단위 테스트에서 최종 목적지에 표지가 있고, `/login` 페이지 로직이 그 표지를 보고 **`/console` 로 되돌리지 않는다**. bite: 표지를 빼면 빨강.
- [ ] **AC-3 — 가드 모집단.** `relogin-marker.test.ts` 가 이 자리를 **보게** 하거나, 못 보는 이유와 대체 술어를 적는다. 🔴 «가드가 있다» 를 grep 으로 답하지 마라 — 되돌린 코드로 가드가 빨개지는지 돌려서 답한다.

---

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.6 / § 2.4.7 *"never a re-login loop"* · § 2.6.1(`TASK-MONO-674`)
- `projects/platform-console/tasks/done/TASK-PC-FE-278-*` — 53곳 수정의 선례

# Related Contracts

- 없음 — 계약이 이미 «루프 금지» 를 요구한다. 코드가 그 계약에 못 미친 자리다.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 카탈로그가 401 이 아니라 503(레지스트리 불가) | 지금처럼 테넌트 스위처만 숨긴다 — 로그인으로 보내지 않는다 |
| 운영자 토큰만 만료, 액세스 토큰은 유효 | 674 의 갱신 경로와 겹치는지 AC-0 에서 확인 |

# Failure Scenarios

1. **리터럴을 가드에 하나 더 넣어 이 자리만 잡는다** → 다음 모양 변형이 또 모집단 밖이다(AC-3).
2. **표지만 달고 `/login` 이 그 표지를 존중하는지 안 본다** → 루프가 그대로일 수 있다(AC-2).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (한 분기 + 가드 술어. 선례가 53곳 있다)
