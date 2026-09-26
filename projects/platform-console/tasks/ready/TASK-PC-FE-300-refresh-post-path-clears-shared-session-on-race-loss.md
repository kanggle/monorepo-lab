# Task ID

TASK-PC-FE-300

# Status

ready

# Title

refresh POST 경로가 경쟁에서 진 400 에 **공유 쿠키 세션 전체를 지운다** — 멀티 탭 경쟁의 패자가 승자 탭까지 로그아웃시킨다

# Owner

platform-console

# Task Tags

- console-web
- auth
- session

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — GET 경로에 이미 있는 «회전 의심 → 대기 후 재시도» 를 POST 에 맞추는 일.

---

# Goal

`TASK-BE-606`(iam, 2026-09-26 UTC, #4036)이 refresh 재사용 탐지에 **30초 유예**를 넣었다: 같은 refresh 토큰으로 동시에 들어온 두 요청 중 진 쪽은
폐기·이벤트 없이 **400 `invalid_grant`** 만 받는다. 콘솔 BFF 는 이 경우를 두 경로에서 다르게 처리한다:

| 경로 | 400 처리 | 근거 |
|---|---|---|
| GET `/api/auth/refresh`(내비게이션) | 쿠키를 지우지 않고 2초 기다린 뒤 승자 쿠키로 복귀(`rotationSuspect`) | `console-web/src/app/api/auth/refresh/route.ts:120-123,147-156` · `shared/lib/session-refresh.ts:68,127` |
| **POST** `/api/auth/refresh`(API 클라이언트의 401 뒤) | 🔴 **`grant_rejected` → 즉시 `clearFullSession`** | `route.ts:70-71` |

쿠키는 탭끼리 공유된다 ⇒ POST 경로로 진 탭이 **승자가 방금 쓴 새 세션 쿠키까지 지운다** ⇒ 두 탭 모두 로그아웃. 탭 안 single-flight(`shared/api/client.ts:69-88`)는
탭 **사이** 경쟁을 막지 않는다. BE-606 이전에는 이 경쟁이 드물게 계정 전체 폐기·잠금으로 번졌으므로 더 큰 문제에 가려져 있었다.

# Scope

## 포함

- POST 경로의 `grant_rejected` 를 GET 과 같은 «회전 의심» 처리로 — 지우기 전에 짧게 기다렸다가 현재 쿠키로 다시 판정(승자가 이미 새 쿠키를 썼으면 성공으로).
- 단위 테스트: 경쟁 패자(400) + 쿠키가 그 사이 바뀜 → 세션 유지 · 진짜 폐기(쿠키 불변 + 400) → 세션 삭제(대조군).

## 제외

- iam 쪽 유예 정책(`TASK-BE-606` 에서 결정·구현).

# Acceptance Criteria

- [ ] **AC-1** — POST 경로가 경쟁 패자 400 에서 세션을 지우지 않는다(단위, 쿠키가 바뀐 경우).
- [ ] **AC-2** — 진짜 거부(쿠키 불변)는 지금처럼 세션을 끝낸다(대조군 단위).
- [ ] **AC-3** — GET·POST 두 경로가 같은 판정 함수를 쓴다(한 곳만 고쳐지는 것 방지).

# Related Specs

- `TASK-BE-606`(iam · 유예 결정 · § 후속) · `specs/`(콘솔 세션 갱신 절이 있으면 그곳)

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 대기 중 승자 응답도 실패 | 세션 삭제(지금과 같음) |
| 단일 탭에서 진짜 폐기된 토큰 | 대기 후에도 쿠키 불변 → 삭제 |

# Failure Scenarios

1. **POST 만 고치고 판정을 복제한다** → 두 경로가 다시 갈린다(AC-3).
2. **대기 없이 무조건 유지** → 진짜 폐기된 세션이 좀비로 남는다(AC-2).
