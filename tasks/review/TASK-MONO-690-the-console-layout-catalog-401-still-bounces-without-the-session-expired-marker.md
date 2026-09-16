# Task ID

TASK-MONO-690

# Title

🔴 콘솔 `(console)` 레이아웃의 **카탈로그 401** 분기가 아직 사유 표지 없이 `/login?redirect=` 로 보낸다 — `TASK-PC-FE-278` 이 53곳에서 끊은 재로그인 루프의 **남은 한 자리**

# Status

review

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

- [x] **AC-0 — 재측정.** `projects/platform-console/apps/console-web/src/app/(console)/layout.tsx` 에서 `getCatalog()` 401 을 잡는 자리와 그 redirect 를 **직접 읽었다** — 보고된 `:102-103` 이 아니라 **`:161-177`**(674 머지 뒤 줄 번호가 실제로 움직였다). 수정 전 원문:
  ```
  161:  try {
  162:    const catalog = await getCatalog();
  163:    tenants = selectableTenants(catalog.products);
  164:  } catch (err) {
  165:    if (err instanceof ApiError && err.status === 401)
  166:      redirect(await buildLoginRedirect());
  167:    tenants = []; // degraded — switcher hidden, shell still usable
  168:  }
  ```
  표지가 **없었다**(`buildLoginRedirect()` → `/login?redirect=…`, `error=` 파라미터 없음) — phantom 아님, Goal 표 그대로.
  🔴 **엣지케이스 ①(503/레지스트리 불가)**: 코드 구조상 `err.status === 401` 이 아니면 조건이 `false`, `:177` 의 `tenants = [];` 로만 떨어진다 — 리다이렉트 없이 스위처만 숨겨진다. 수정 전/후 동일, 회귀 없음(직접 읽어서 확인).
  🔴 **엣지케이스 ②(운영자 토큰만 만료, 액세스 토큰은 유효) — 674 갱신 경로와 안 겹친다.** `isAuthenticated()`(`shared/lib/session.ts:243`)는 `getAccessToken() !== null && getOperatorToken() !== null` — **두 쿠키 다** 있어야 `true`다. 이 카탈로그-401 분기는 `!isAuthenticated()` 가드(674 의 갱신/로그인 갈래, :144)를 **통과한 뒤에만** 실행되므로, 도달했다는 것 자체가 `isAuthenticated() === true`(액세스+운영자 쿠키 둘 다 존재)라는 뜻이다. 운영자 토큰만 만료된 경우는 `getOperatorToken()` 이 쿠키를 여전히 반환하므로(서버는 쿠키 존재만 보고 JWT 유효성을 로컬에서 검사하지 않는다 — `getOperatorToken()`/`getAccessToken()` 은 쿠키 존재 여부만 읽는다, :158-171) `isAuthenticated()` 는 여전히 `true` — 즉 674 분기(:144-152, `!isAuthenticated()`)에는 **애초에 들어가지 않고** 곧장 이 카탈로그-401 분기로 온다. 구조적으로 상호배타적: 674 는 `isAuthenticated() === false` 일 때만, 이 분기는 `isAuthenticated() === true` 인데 백엔드가 그 살아있는 쿠키를 거절할 때만 실행된다. 겹침 없음(코드로 확정).
- [x] **AC-1 — 고친다.** `src/shared/lib/re-login.ts` 의 `RE_LOGIN_PATH` 상수를 import 해 `redirect(RE_LOGIN_PATH)` 로 바꿨다(layout.tsx:15, :176). 문자열을 손으로 쓰지 않았다. 🔵 **편차**: 기존 53곳(`TASK-PC-FE-278`)은 의도적으로 **리터럴**을 쓴다(그 티켓의 Implementation Notes — "상수 import 가 아니라 리터럴 + 가드"). 이 자리만 AC-1 문구가 명시한 대로 **상수를 import**했다 — 53곳과 다른 모양이지만 이 티켓이 명시적으로 요구한 형태다(아래 § 편차 참조).
- [x] **AC-2 — 루프가 끊겼음을 실행으로 본다.** 신규 `tests/unit/console-guard-catalog-401.test.tsx`(실제 `@/app/(console)/layout` import, `console-guard-idle-refresh.test.tsx` 의 원칙을 그대로 따름) — 쿠키 있음(`isAuthenticated()` 목 `true`) + `getCatalog()` 가 진짜 `ApiError(401, …)` 를 던지는 조건에서 레이아웃의 최종 리다이렉트 목적지가 `RE_LOGIN_PATH`(`/login?error=session_expired`)임을 4칸으로 증명. `/login` 페이지가 그 표지를 보고 되튕기지 않는다는 절반은 **이미 존재하는** `relogin-loop.test.tsx`("루프의 심장" 칸, `error: SESSION_EXPIRED` 목으로 `isAuthenticated() === true` 인데도 `redirect` 가 안 불림을 증명)가 잰다 — 두 파일이 **같은 상수** `RE_LOGIN_PATH`/`SESSION_EXPIRED` 를 잡고 있어 둘 중 하나가 어긋나면 나머지가 빨개진다(파일 끝 칸이 이를 명시적으로 검증). **bite**: `redirect(RE_LOGIN_PATH)` 를 `redirect(await buildLoginRedirect())` 로 되돌리자 `console-guard-catalog-401.test.tsx` 4칸 중 목적지를 재는 2칸이 빨강(`expected '/login?redirect=%2Fecommerce%2Forders…' to be '/login?error=session_expired'`), 되돌리지 않은 대조군 2칸(fetch 미호출·상수 대조)은 초록으로 남았다. 복원 후 수동 편집(파일 복사/`git checkout --` 미사용)으로 원문 바이트 동일 확인, 4/4 재통과.
- [x] **AC-3 — 가드 모집단.** `relogin-marker.test.ts` 에 새 `describe` 블록을 추가해 **이 자리를 보게 했다**(대체하지 않고 기존 리터럴 스캔과 나란히 추가). Failure Scenario 1(리터럴 하나 더 추가하면 다음 모양이 또 샌다)을 피하려고 술어를 문자열 모양이 아니라 **의미**로 다시 세웠다: "`err.status === 401` 을 검사하는 코드 행 아래 8줄 창 안에서 `redirect(...)` 호출을 찾고, 그 인자에 `SESSION_EXPIRED`(`'session_expired'`) 또는 `RE_LOGIN_PATH` 식별자가 없으면 위반". 리터럴이든 상수든 어떤 호출 모양이든 잡는다 — `redirect(await buildLoginRedirect())` 같은 함수-호출 형태였던 이번 결함도, 그리고 기존 53곳의 리터럴 형태도 모두 population 안. **bite**: 같은 되돌리기로 새 describe 블록의 첫 칸이 `expected [ 'app/(console)/layout.tsx:176' ] to deeply equal []` 로 빨강, 비공허성 칸(및 기존 BARE/MARKED 두 칸)은 초록 유지. 복원 후 재통과.

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

1. **리터럴을 가드에 하나 더 넣어 이 자리만 잡는다** → 다음 모양 변형이 또 모집단 밖이다(AC-3). **피함**: § AC-3 의 창(window) 술어는 모양과 무관하다.
2. **표지만 달고 `/login` 이 그 표지를 존중하는지 안 본다** → 루프가 그대로일 수 있다(AC-2). **피함**: `relogin-loop.test.tsx` 가 이미 그 절반을 잰다 — 새 테스트는 상수 동일성으로 맞물린다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (한 분기 + 가드 술어. 선례가 53곳 있다)

---

# 구현 기록 (ready → review, 2026-09-16 UTC)

## 무엇을 바꿨나

| 파일 | 내용 |
|---|---|
| `src/app/(console)/layout.tsx` | `RE_LOGIN_PATH` import(`:15`) + 카탈로그 401 분기(`:164-176`)를 `redirect(RE_LOGIN_PATH)` 로 교체, 근거 주석 |
| `tests/unit/console-guard-catalog-401.test.tsx` | **신규** — 실제 레이아웃을 부르는 4칸(AC-2) |
| `tests/unit/relogin-marker.test.ts` | **+2칸** — 401 분기 창(window) 술어 + 비공허성(AC-3) |

## § 게이트 — 각각 독립 statement, 파이프 없음

```
npx tsc --noEmit                                                          rc=0
npx next lint                                                              rc=0   ✔ No ESLint warnings or errors
npx vitest run tests/unit/relogin-marker.test.ts tests/unit/relogin-loop.test.tsx \
  tests/unit/console-guard-idle-refresh.test.tsx tests/unit/layout-login-redirect.test.ts
                                                                            rc=0   4 files / 45 tests
npx vitest run tests/unit/console-guard-catalog-401.test.tsx              rc=0   1 file / 4 tests
npx vitest run (전체 console-web 유닛)                                     rc=1 → rc=1 → (부분 재실행) rc=0
```

🔴 **전체 스위트 판정은 rc 단독이 아니라 「실패 파일이 매번 바뀌는가」로 했다** — 3회 전체 실행 중 2회는 각기 다른 5개/2개 파일이 5초 타임아웃으로 실패했고(`LedgerOpsScreen`·`OperatorsScreen`·`SeedConfigScreen`·`MasterWriteDialog`·`OperatorGroupsScreen`, 이어서 `OperatorsScreen`·`WmsInboundScreen`), 실패 목록이 겹치지 않고 그중 어느 파일도 auth/redirect/layout 코드를 만지지 않는다 — 이 저장소 메모리가 이름 붙인 병렬 vitest 타임아웃 플레이크와 일치. 매번 **고립 재실행하면 0 실패**(재확인 2회, `rc=0`). 이 티켓이 만진 4개 파일(`relogin-marker`·`relogin-loop`·`console-guard-idle-refresh`·`console-guard-catalog-401`·`layout-login-redirect`)은 3회 전부 초록이었다 — 회귀 0.

## § bite (AC-2 · AC-3)

```
redirect(RE_LOGIN_PATH) → redirect(await buildLoginRedirect()) 로 수동 되돌림(git checkout -- 미사용)
  → console-guard-catalog-401.test.tsx : BITE rc=1, 4개 중 2개 실패(목적지 불일치) · 대조군 2개(fetch 0회·상수 동일성) 초록 유지
  → relogin-marker.test.ts               : BITE rc=1, 신규 AC-3 칸 1개 실패(정확히 `layout.tsx:176` 지목) · 기존 BARE/MARKED 2칸 + 비공허성 칸 초록 유지
수동 편집으로 복원(파일 내용 직접 대조) → 두 스위트 재실행 rc=0, 전부 통과
```

## § 편차 (플래너 문구 대비)

- **AC-1 이 상수 import 를 명시**해 이 한 자리만 `TASK-PC-FE-278` 의 53곳(의도적 리터럴)과 다른 모양이 됐다 — 티켓 문구를 그대로 따른 결과이며 기존 스타일과의 불일치는 § AC-1 에 기록했다.
- AC-3 은 "가드가 이 자리를 보게 하거나, 대체 술어를 적는다"의 **전자**를 택했다(기존 `relogin-marker.test.ts` 확장) — 별도 파일로 대체 술어를 적는 안은 검토했으나, 같은 파일 안에서 401→redirect 창 술어로 일반화하는 쪽이 "가드 모집단"이라는 AC-3 문구에 더 가깝다고 판단했다.

## 안 잰 것

- 🔴 **라이브에서 이 수리를 보지 못했다** — 데모 인스턴스를 켜서 실제 401(예: 만료된 카탈로그 토큰)을 유발해 `/ecommerce` → `/login?error=session_expired` 왕복을 브라우저로 확인하는 것은 이 세션에서 하지 않았다(유닛 테스트로만 증명). 다음 데모 창에서 확인 권장.
- 🔴 **e2e 스모크는 이 분기를 덮지 않는다** — `TASK-PC-FE-278` 의 done 기록과 같은 한계.
