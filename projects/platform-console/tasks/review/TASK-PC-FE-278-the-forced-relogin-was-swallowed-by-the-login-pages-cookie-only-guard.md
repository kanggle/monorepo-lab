# Task ID

TASK-PC-FE-278

# Title

강제 재로그인이 **한 번도 일어나지 않았다** — `/login` 의 쿠키-전용 가드가 401 을 삼켜 카탈로그로 되튕겼다

# Status

review

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

`401` 을 만난 콘솔 화면이 **실제로 재로그인을 일으키도록** 한다.

계약(`specs/contracts/console-integration-contract.md`)은 **스무 곳 넘게** 같은 말을 적어 뒀다:

> `401` → forced **whole-session** re-login (**no partial authed state**)

그리고 § 2.4.6 · § 2.4.7 은 아예 **"never a re-login loop"** 라고 못박았다.
그런데 코드가 하던 일은 `redirect('/login')` 하나였고, `/login` 페이지의 첫 줄은

```ts
if (await isAuthenticated()) redirect('/console');
```

이며 `isAuthenticated()` 는 **쿠키만** 읽는다 — 백엔드에 묻지 않는다
(`src/shared/lib/session.ts:234`). 백엔드가 토큰을 거절해도 쿠키는 멀쩡하므로:

```
  /ecommerce ──401──▶ /login ──쿠키 있음──▶ /console
```

**재로그인하러 보낸 사람이 아무 말도 없이 카탈로그로 되돌아온다.** 방문자가 보는 것은
반짝임 하나이고, 죽은 세션은 그대로 남는다. 계약이 이름 붙여 금지한 그 루프다.

## 왜 이커머스에서 먼저 보였나

이 루프는 **모든 섹션이 갖고 있다**(401 지점 53곳 전부 같은 모양). 이커머스가 먼저
드러난 것은 정책이 달라서가 아니라 **폭**이 달라서다 — `/ecommerce` 만 console-bff 를
안 거치고 **직접 fan-out** 한다: 영역 7 + 주문상태 6 + 최근주문·최근셀러·인사이트·
셀러명맵 4 = **17 leg**, 여기에 카탈로그 pre-flight 1. leg 이 많을수록 그중 하나가
401 날 확률이 높다.

🔴 **소유자 신고 그대로**: *"다른 서비스는 메뉴 눌렀을 때 해당 메뉴를 보여주는데
이커머스는 반짝이며 서비스 선택 전 화면으로 나왔다. 또 안 그러네."* — 간헐성까지
이 기전과 맞는다(401 여부가 토큰 상태에 달렸다).

---

# Scope

## In Scope

- 401 지점 **53곳**: `redirect('/login')` → `redirect('/login?error=session_expired')`
- `/login` 페이지: 마커가 붙어 오면 단락 회로를 타지 않는다 + 사유 문구
- `/api/auth/login` 라우트: 재로그인 시작 시 `clearFullSession()`
- 새 상수 모듈 `src/shared/lib/re-login.ts`
- 테스트: 루프 스위트 · 마커 가드 · 라우트 clear 2칸 · 기존 단언 27건 갱신

## Out of Scope

- **어느 leg 이 401 을 냈는지의 규명** — 데모가 `stopped` 라 재현 불가(§ 안 잰 것).
  🔵 그러나 이 수리는 **그 답과 무관하게** 옳다: 어느 leg 이든 계약이 요구한 재로그인이
  일어나지 않았다는 것이 결함이다.
- **`getDomainFacingToken()` 의 토큰 계층별 처방 분화**(assumed 만 만료된 경우
  `clearTenantSelection` 으로 좁히기). 🔴 그것은 **새 결정**이고 계약이 정한 바가
  없으므로 ADR 사안이다. 이 티켓은 계약이 **이미 적어 둔 것을 이행**만 한다.
- 서버 사이드 401 자동 refresh (`client.ts:90` 이 `isBrowser()` 로 막아 둔 축) — 별건.
- 주석 60곳의 옛 인용 문구 — 고쳐 쓰면 당시 기록이 거짓이 된다(§ Implementation Notes).

---

# Acceptance Criteria

- [x] **AC-0 (기전 확정)** — 루프가 **코드로** 확정된다: `redirect('/login')` 지점 53곳,
      `/login` 의 단락 회로가 `isAuthenticated()`(쿠키 전용)에 달려 있음,
      `/ecommerce` 가 17 leg 직접 fan-out 이고 401 이 셀-로컬 degrade 가 아니라
      전세션 재로그인으로 승격됨. 🔴 **계약 인용을 근거로 든다** — 내 추론이 아니라
      저장소가 스스로 적어 둔 문장이 기준이다.
- [x] **AC-1** — 마커를 달고 온 방문은 `/console` 로 **되튕기지 않는다**.
- [x] **AC-2 (대조군)** — 마커가 **없는** 방문(운영자가 직접 `/login` 을 침)은
      **예전 그대로** `/console` 로 간다. 🔴 이 칸이 없으면 「루프를 끊었다」와
      「단락 회로를 통째로 지웠다」가 같은 초록으로 보인다.
- [x] **AC-3** — 방문자에게 **왜** 로그아웃됐는지 말한다(반짝임만 남기지 않는다).
- [x] **AC-4** — 죽은 세션이 **실제로 지워진다**. 🔴 서버 컴포넌트는 쿠키를 못 바꾸므로
      (Next 는 Route Handler / Server Action 에서만 허용 — **이 결함이 생긴 구조적
      이유**) `/api/auth/login` 이 PKCE 를 걸기 **전에** `clearFullSession()` 을 부른다.
      그리고 방금 세운 PKCE/state 쿠키는 **살아남는다**(순서 함정 전용 칸).
- [x] **AC-5 (가드)** — 마커 없는 `redirect('/login')` 이 코드에 하나라도 생기면
      **테스트가 빨개진다**. 🔴 공유 상수로는 이걸 못 한다(새 코드가 상수를 안 쓰면
      그만이다) — 그래서 강제는 소스를 읽는 테스트가 한다. 비공허성 칸도 함께.
- [x] **AC-6** — 게이트 4종이 **각각 독립 statement + 명시 `rc=$?`** 로 초록,
      그리고 **회귀 0**. 🔵 판정은 rc 가 아니라 **몇 개가 돌았나**로 한다.
- [x] **AC-7 (bite)** — ① 단락 회로를 되돌리면 루프 스위트가 빨개진다
      ② 마커를 하나 떼면 가드가 문다. 둘 다 **대조군은 초록으로 남는다**.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md`
> (`domain: saas`, `traits: [multi-tenant, integration-heavy, audit-heavy]`) →
> `rules/common.md` → 선언된 domain/trait 파일.

- `projects/platform-console/PROJECT.md`
- `projects/platform-console/specs/contracts/console-integration-contract.md`
  — § 2.5 resilience taxonomy · § 2.6 fail-closed · § 2.4.6/§ 2.4.7 *"never a re-login loop"*
- `docs/adr/ADR-MONO-020-*` (§ D4 / § 2.7 — assumed 토큰과 활성 테넌트)

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- `specs/contracts/console-integration-contract.md` — **변경 없음.**
  🔵 이 티켓은 계약을 바꾸지 않는다. 계약이 스무 곳에서 요구한 것을 **코드가 안 하고
  있었을 뿐**이므로, 고침은 구현을 계약에 맞추는 쪽이다. 리뷰어가 확인할 것은
  「계약 diff 0건」이고, 그것이 이 수리가 ADR 사안이 **아니라는** 근거다.

---

# Target App

- `projects/platform-console/apps/console-web`

---

# Implementation Notes

- **왜 상수 import 가 아니라 리터럴 + 가드인가**: 53곳은 이미 `'/login'` 을 리터럴로
  쓰고 있었다. 상수를 도입하면 51개 파일에 import 가 붙어 그 51줄이 수리의 내용을
  덮는다. 그리고 🔴 **상수는 규율을 강제하지 못한다** — 새 코드가 그냥
  `redirect('/login')` 을 써도 상수는 아무 말도 안 한다. 강제는 `relogin-marker.test.ts`
  가 한다.
- **주석 60곳은 그대로 뒀다.** JSDoc 이 §2.5 분류를 서술하며 옛 형태를 인용하는데,
  그것은 당시의 서술이고 고쳐 쓰면 기록이 거짓이 된다. 가드는 **코드 행만** 본다 —
  주석에서 복사해 온 것도 코드가 되는 순간 걸리므로 구멍이 아니다.
- 🔴 **발견 하나(이 티켓 범위 밖, 기록만)**: `tests/unit/login-error-messages.test.ts` 는
  `ERROR_MESSAGES` 맵을 **복제**해 두고 *"소스가 바뀌면 이 파일도 고쳐라"* 라고 적어
  뒀는데, 그 복제본의 `not_provisioned` 문구는 **이미 소스와 다르다**(소스는
  *"아직 소속된 조직이 없습니다…"*, 복제본은 *"운영자 권한이 없는 계정입니다…"*).
  드리프트가 실제로 일어났다는 증거다. ⇒ 이 티켓의 신규 테스트는 복제본이 아니라
  **진짜 페이지를 태운다.** 복제본 정리는 별건.

---

# Edge Cases

- 마커 + 쿠키 있음 → 로그인 화면 (루프 차단)
- 마커 + 쿠키 없음 → 로그인 화면 (익명과 동일)
- 마커 없음 + 쿠키 있음 → `/console` (**대조군** — 편의 유지)
- 다른 `error` 코드 + 쿠키 있음 → `/console` (마커만 특별하다)
- 재로그인 시작 시 죽은 쿠키 전부 삭제, PKCE/state 는 생존

---

# Failure Scenarios

- **`clearFullSession` 을 PKCE 쿠키 뒤에 부른다** → 방금 만든 verifier 를 지워 콜백이
  실패한다. 🔴 그 증상은 *"로그인이 안 된다"* 라 이 티켓이 고치려던 것과 **육안으로
  구별되지 않는다** ⇒ 순서 전용 테스트 칸을 뒀다.
- **401 이 사실은 일시적이었다** → 이제 강제 로그아웃이 된다. 🔵 계약이 그렇게
  정해 뒀다(*"no partial authed state"*). 서버 사이드 refresh 부재(`client.ts:90`)를
  손보는 것은 별건이고, 그것을 이 티켓에서 같이 바꾸면 **두 결정이 한 PR 에 섞인다.**
- **새 섹션이 마커를 잊는다** → `relogin-marker.test.ts` 가 빨개진다 (AC-5).

---

# Test Requirements

- 루프 스위트(`relogin-loop.test.tsx`) — 대조군 포함
- 마커 가드(`relogin-marker.test.ts`) — 비공허성 칸 포함
- 라우트 clear + 순서(`auth-routes.test.ts` 에 2칸 추가)
- 기존 단언 27건 갱신
- bite 2종

---

# Definition of Done

- [x] 구현
- [x] 테스트 추가/갱신
- [x] 게이트 4종 통과 — 각각 `rc=$?` 명시
- [x] Ready for review

---

# 구현 기록 (ready → review, 2026-09-10 UTC)

## 무엇을 바꿨나

| 파일 | 내용 |
|---|---|
| 51개 파일, **53 지점** | `redirect('/login')` → `redirect('/login?error=session_expired')` |
| `src/shared/lib/re-login.ts` | **신규** — `SESSION_EXPIRED` / `RE_LOGIN_PATH` + 왜 상수가 아니라 가드인지 |
| `src/app/(auth)/login/page.tsx` | 마커면 단락 회로 미실행 + `session_expired` 문구 |
| `src/app/api/auth/login/route.ts` | PKCE **앞에** `clearFullSession(jar)` |
| `tests/unit/relogin-loop.test.tsx` | **신규 6칸** (대조군 2 포함) |
| `tests/unit/relogin-marker.test.ts` | **신규 2칸** (가드 + 비공허성) |
| `tests/unit/auth-routes.test.ts` | **+2칸** (clear · 순서) |
| 23개 테스트 파일 | 단언 **27건** 갱신 |

🔵 **치환의 모집단을 두 번 좁혔다.** `REDIRECT:/login'` 은 소스에 27곳 있었지만 실패는
14건이었다 — 나머지는 `toContain`/`toThrow(str)` 이라 **부분문자열로 이미 통과**한다.
일괄 치환했으면 통과 중인 12곳까지 흔들었을 것이다. 그래서 실제로 깨진 두 형태
(`toBe(...)` 14 · `toHaveBeenCalledWith('/login')` 13)만 바꿨고, **14+13=27 · 23개 파일**이
실패 건수·실패 파일 수와 정확히 일치하는 것으로 모집단을 검증했다.

## § 게이트 (AC-6) — 각각 독립 statement, 파이프 없음

```
tsc --noEmit     rc=0
next lint        rc=0   ✔ No ESLint warnings or errors
vitest run       rc=0   292 files / 3004 tests   (이전 290 / 2994)
next build       rc=0
```

🔵 **판정은 rc 가 아니라 「몇 개가 돌았나」로 했다** — 이 저장소가 이름 붙인 함정
(일을 하나도 안 하고 rc=0 으로 끝나는 러너)을 피하기 위해서다. 290→292 파일,
2994→3004 칸이고 **회귀 0**.

## § bite (AC-7)

```
① 단락 회로 되돌림 + ② 마커 하나 제거
   → BITE rc=1 : 3 failed | 5 passed
     × 루프의 심장 (마커인데 /console 로 튕김)
     × 사유 문구
     × 마커 가드 — 처방 메시지를 그대로 출력
     ✓ 대조군 2칸 (마커 없으면 /console) — 초록 유지
     ✓ 비공허성 칸 — 초록 유지 (나머지 52 마커가 살아 있다)
```

🔵 **대조군과 비공허성 칸이 초록으로 남은 것이 이 bite 의 절반이다** — 전부 빨개지는
bite 는 「스위트가 무언가를 재고 있다」를 증명하지 못한다.
🔴 `git checkout --` 은 쓰지 않았다(미커밋분을 지운다). 파일 복사로 되돌렸고 복원 후
**두 파일 모두 바이트 동일성**을 확인했으며, 복원 트리에서 8/8 초록을 재확인했다.

## 안 잰 것

- 🔴 **어느 leg 이 401 을 냈는지 못 쟀다.** 데모 인스턴스가 `stopped`(분 예산 543/600)라
  재현할 수 없었다. **경로는 코드로 확정, 트리거는 가설**이다.
- 🔴 **라이브에서 이 수리를 보지 못했다.** 데모가 켜진 창에서
  `/ecommerce` → 401 → `/login?error=session_expired` 를 한 번 밟아 보는 것이 남았다.
- 🔴 **e2e 스모크는 이 분기를 덮지 않는다** — 세션이 살아 있는 축만 돈다.
- 서버 사이드 401 자동 refresh 부재(`client.ts:90` 의 `isBrowser()` 게이트)는 **손대지
  않았다.** 그것을 같이 바꾸면 두 결정이 한 PR 에 섞인다.
