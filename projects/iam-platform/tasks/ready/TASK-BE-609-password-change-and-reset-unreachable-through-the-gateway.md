# Task ID

TASK-BE-609

# Status

ready

# Title

비밀번호 변경 · 재설정이 **게이트웨이를 거치면 쓸 수 없다** — 재설정은 `public-paths` 누락(401), 변경은 사용자 Bearer 를 auth-service 가 내부 자격으로 거부(401)

# Owner

iam-platform

# Task Tags

- gateway-service
- auth-service
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 두 결함 모두 설정/체인 순서 한 곳이다. 단 ② 의 원인은 AC-0 에서 코드로 확정한다(아래는 라이브 재현까지).

---

# Goal

`TASK-BE-607` AC-3 창(2026-09-26 UTC, 16차 AMI `58d4920c4`)에서 발견. 서비스 자체는 정상이다(직접 호출: 변경 204 · 재설정 204 · 결과 상태 확인).
그러나 **사용자가 닿는 유일한 문**인 iam 게이트웨이(`iam-gateway-service`)를 거치면:

| 호출 | 게이트웨이 경유 | 층 |
|---|---|---|
| `POST /api/auth/password-reset/request` (비로그인) | **401 `TOKEN_INVALID`** | 게이트웨이 — `public-paths` 에 없다(`gateway-service/src/main/resources/application.yml` § public-paths: signup · refresh · `/oauth2/**` 뿐) |
| `POST /api/auth/password-reset/confirm` | 같음 | 같음 |
| `PATCH /api/auth/password` + 유효한 사용자 Bearer | **401 `Missing or invalid internal credentials`** | auth-service — 직접 재현: `X-Account-Id` 만 → 400(정상 판정) · 같은 요청 + `Authorization: Bearer <사용자 토큰>` → **401** |

auth-service `SecurityConfig` 는 두 경로를 `permitAll` 로 열어 두었다(`:160-161`). ② 는 사용자 Bearer 가 붙으면 내부 자격(GAP `client_credentials`) 검사가 먼저 도는 것으로 보인다 — ⚪ 체인 판독은 AC-0.
🔵 이 두 API 를 부르는 프런트는 **0**(저장소 grep) — 그래서 드러나지 않았다. `AccountSessionController`(`/api/accounts/me/sessions`)가 같은 «게이트웨이가 `X-Account-Id` 주입» 패턴이므로 **같은 모양인지** 함께 본다.

# Scope

## In Scope

- AC-0: ② 의 원인을 코드로 확정(어느 체인이 `Authorization` 헤더를 보고 거부하나) + 형제(`/api/accounts/me/sessions/**`) 판정.
- 게이트웨이 `public-paths` 에 재설정 두 경로 추가(POST 만) + 레이트 리밋 검토(재설정 요청은 이미 서비스 쪽 제한이 있다 — `RequestPasswordResetUseCase` rate limit).
- ② 수정.

## Out of Scope

- 프런트 화면(비밀번호 변경 UI)을 새로 만드는 일 — 필요하면 별도 티켓.

# Acceptance Criteria

- [ ] **AC-0** — 원인 확정 + 형제 경로 판정.
- [ ] **AC-1** — 게이트웨이 경유 IT(또는 슬라이스): 재설정 요청 204 · 확인 204 · 변경(사용자 Bearer) 204 · 대조군: 토큰 없는 변경은 여전히 401.
- [ ] **AC-2** — 🔴 **결과로 판정**(창): 일회용 계정으로 게이트웨이 주소(`https://auth.hubwang.com` 또는 iam 게이트웨이)에서 변경 → 새 비밀번호 로그인 성공 · 재설정 → 새 비밀번호 로그인 성공.

# Related Specs

- `specs/contracts/http/auth-api.md` § PATCH /api/auth/password · password-reset
- `specs/features/password-management.md`
- `TASK-BE-607` § CORRECTION (2026-09-26)

# Related Contracts

- `auth-api.md` — 인증 방식 서술(«Bearer Access Token 필수»)이 실제 경로와 맞는지.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 재설정 요청을 공개로 열었다 | 계정 존재와 무관하게 204(열거 오라클 없음 — 기존 계약) |

# Failure Scenarios

1. **auth-service 직접 호출로 IT 를 짠다** → 이번에 드러난 두 층을 다시 못 본다. 게이트웨이 경유여야 한다.
2. **`/api/auth/**` 전체를 public 으로** → 변경 API 가 인증 없이 열린다.
