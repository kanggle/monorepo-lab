# Task ID

TASK-MONO-705

# Title

🔴 콘솔이 30분 유휴 뒤 조용히 갱신하면 **`console_id_token` 이 다시 서지 않는다** — 그 뒤의 로그아웃은 IdP 세션을 못 끝낸다

# Status

ready

# Owner

monorepo

# Task Tags

- console
- iam
- oidc
- logout

---

# Goal

`TASK-MONO-674` 가 30분 유휴 뒤 조용한 갱신(`/api/auth/refresh`)을 넣었고, 2026-09-17 두 창에서 그 경로가 화면을 살린다는 것은 확인됐다. 그 티켓의 마지막 측정 칸(«id_token 쿠키가 갱신 뒤 다시 서는가»)의 답은 **«안 선다»** 였다(2026-09-17 UTC 둘째 창, AMI `af0018aa6`):

| UTC | 콘솔 쿠키(이름 · 남은 초) |
|---|---|
| 16:44:13 로그인 | access 1787 · **id_token 1787** · operator 3588 · refresh 30일 |
| 17:15:13 유휴 31분 뒤 | operator · refresh 만 남음 |
| 17:18:42 갱신 뒤(`307 /api/auth/refresh` → 원래 화면) | access 1779 · operator 3580 · refresh 30일 · **id_token 없음** |

코드상 콘솔은 **갱신 응답에 `id_token` 이 있을 때만** 쿠키를 세운다(`console-web/src/shared/lib/session-refresh.ts:148-150`, 콜백 `app/api/auth/callback/route.ts:149` 과 같은 조건) ⇒ **IAM 의 `refresh_token` 그랜트 응답에 `id_token` 이 없다**고 읽힌다(🔴 응답 본문 자체는 아직 안 봤다 — AC-0).

결과: 로그인 30분 뒤부터 로그아웃이 `id_token_hint` 없이 **로컬 로그아웃으로 폴백**한다(`app/api/auth/logout/route.ts:31-32,86`) — 브라우저 쿠키는 지워지지만 **IdP(IAM) 세션은 남는다.** 같은 브라우저에서 다시 «로그인» 을 누르면 비밀번호 없이 들어갈 수 있는지가 이 결함의 사용자 쪽 모양이다(AC-0 에서 확인). 표시 이름은 액세스 토큰으로 폴백해 **눈에 보이는 변화는 없다** — 그래서 조용하다.

---

# Scope

## 포함

- IAM 갱신 응답에 `id_token` 이 정말 없는지, 없다면 왜인지(Spring Authorization Server 설정 · 클라이언트 등록 · scope) 관측으로 지목.
- 갈래 비교 후 **소유자 결정**: ⓐ IAM 이 `openid` scope 의 refresh 응답에 `id_token` 을 싣게 한다 ⓑ 콘솔이 `id_token` 쿠키를 로그인 시점 값으로 **갱신하지 않고 유지**한다(만료를 refresh 쿠키 수명에 맞춘다 — `id_token_hint` 는 만료된 id_token 도 받는 IdP 가 많다, 🔴 IAM 이 받는지 먼저 잰다) ⓒ 수용(로컬 로그아웃 폴백을 문서화).
- 로그아웃이 IdP 세션까지 끝내는지의 판정 술어.

## 제외

- 조용한 갱신 경로 자체(`TASK-MONO-674` 에서 끝났다).
- 다른 콘솔 클라이언트(fan · store)의 같은 축 — AC-0 에서 같은 모양이면 기록만.

---

# Acceptance Criteria

- [ ] **AC-0 — 재측정.** (1) IAM `refresh_token` 그랜트 응답에 `id_token` 필드가 있는가를 **응답 본문의 키 목록**으로 본다(값 출력 금지) — 로컬 IAM(Testcontainers/슬라이스)으로 재현되면 그것이 첫 판정, 안 되면 창. (2) 로그인 30분 뒤 로그아웃 → 같은 브라우저에서 «로그인» 을 누르면 IAM 폼이 다시 뜨는가(뜨면 IdP 세션이 끝난 것). 🔴 두 번째는 창이 필요하다 — 없으면 ⚪ + `TASK-MONO-672`.
- [ ] **AC-1 — 갈래를 고른다 (🔴 소유자 결정).** 위 ⓐ/ⓑ/ⓒ 를 AC-0 결과와 함께 추천을 붙여 묻는다. 🔴 추천을 결정으로 적지 마라. ⓐ 는 IAM(다른 프로젝트) 설정 변경이라 계약·보안 축을 적는다.
- [ ] **AC-2 — 고친다 + bite.** 테스트: «갱신 뒤에도 로그아웃이 `id_token_hint` 를 싣는다»(ⓐ/ⓑ) 또는 «로컬 폴백이 사유를 말한다»(ⓒ). 고친 것을 되돌리면 빨개진다.
- [ ] **AC-3 — 창 판정.** 로그인 → 31분 유휴(손대지 않은 세션) → 이동 → 로그아웃 → «로그인» 이 IAM 폼을 다시 띄우는가. 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# Related Specs

- `tasks/done/TASK-MONO-674-thirty-minutes-of-idle-logs-you-out-and-says-nothing.md` § 창 실측(항목 8) · `:182`
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6 (세션 · 로그아웃)
- `TASK-PC-FE-033` — 로그아웃 `id_token_hint`

# Related Contracts

- IAM OIDC 토큰 엔드포인트의 refresh 응답 · 콘솔 로그아웃(RP-initiated logout). 🔴 바꾸면 계약 먼저.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 30분 안에 로그아웃 | 지금도 `id_token_hint` 가 실린다 — 대조군 |
| 60분 유휴(operator 까지 만료) 뒤 로그아웃 | 30분 칸과 같은 판정 |
| IAM 이 만료된 `id_token_hint` 를 거부 | ⓑ 는 불가 — AC-0 에서 먼저 잰다 |

# Failure Scenarios

1. **콘솔만 보고 «IAM 이 안 준다» 로 확정한다** → 응답 본문 키를 안 봤다(AC-0 (1)).
2. **쿠키가 다시 선 것으로 닫는다** → 판정은 «로그아웃이 IdP 세션을 끝내는가» 다(AC-3).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus 5** (IAM 설정·보안 축 판단. 갈래가 ⓒ 면 Sonnet 5)
