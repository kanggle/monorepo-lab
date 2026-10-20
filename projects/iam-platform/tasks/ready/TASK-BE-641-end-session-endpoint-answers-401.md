# Task ID

TASK-BE-641

# Title

IdP 가 discovery 로 알리는 `end_session_endpoint`(`/connect/logout`)가 **401 «Missing or invalid internal credentials»** 를 낸다 — 콘솔 로그아웃이 IdP 세션을 끝내지 못하고 JSON 오류 화면에서 멈춘다

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus(auth-service 다중 보안 체인 · SAS OIDC 로그아웃 설정)
> 기안 2026-10-20 UTC — 32차 데모 창(`TASK-MONO-672` § 32nd window — harvest)의 발견. 측정 먼저(AC-0).

# Dependency Markers

- 출처: 32차 창 — `requester@demo.com` 이 콘솔에서 로그아웃 → 브라우저가 `https://auth.hubwang.com/connect/logout?id_token_hint=…&post_logout_redirect_uri=https%3A%2F%2Fconsole.hubwang.com%2Flogin&client_id=platform-console-web` 로 가서 `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}` 를 받았다(소유자).
- 관련: `TASK-MONO-801` AC-0 — iam auth-service `SecurityConfig.java:188`(Order 2) 의 진입점이 같은 문구 «Missing or invalid internal credentials» 를 낸다(추론으로 적혀 있던 것).
- 선행 없음.

# Goal

RP-initiated logout 이 동작한다: `GET /connect/logout`(유효한 `id_token_hint` + 등록된 `post_logout_redirect_uri`)이 IdP 세션을 끝내고 그 주소로 보낸다. 잘못된 요청은 OIDC 규약의 오류 응답(내부 자격 오류가 아니라)을 낸다.

# Scope

## In Scope

- **AC-0 측정**:
  - 실측(에이전트, 2026-10-20 18:45Z): `GET https://auth.hubwang.com/.well-known/openid-configuration` → `end_session_endpoint = https://auth.hubwang.com/connect/logout`. 같은 주소를 `id_token_hint` 없이 부르면 **401 `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}`**, 소유자 브라우저(`id_token_hint` 있음)도 같은 401. auth-service 로그에 그 줄 없음.
  - 요청이 어느 체인에 걸리는가 — SAS 체인의 `securityMatcher` 가 `/connect/logout` 을 덮는가, OIDC logout 이 켜져 있는가(`oidc(...)` · `logoutEndpoint`), Order 2 체인이 그것을 가로채는가(file:line).
  - 왜 지금까지 안 보였나 — nightly «Web-store GAP logout e2e (RP-initiated end_session, real GAP)» 가 초록인데 무엇을 부르는가(같은 주소 · 같은 메서드 · 같은 호스트인가). 콘솔 로그아웃 경로(`app/api/auth/logout` 류)가 이 주소를 언제부터 쓰는가.
  - 공개 호스트 `auth.hubwang.com` 앞단(Cloudflare/Traefik)이 경로를 바꾸는가.
- 수선(AC-0 이 정한다) + 시험(실제 체인으로 — 슬라이스 금지).

## Out of Scope

- 콘솔 쪽 로그아웃 흐름 변경 — 서버가 고쳐지면 그대로 동작해야 한다. AC-0 이 콘솔 결함을 보이면 콘솔 티켓.

# Acceptance Criteria

- [ ] **AC-0** — 위 측정을 file:line 으로. nightly 가 왜 못 잡았는지 포함.
- [ ] **AC-1** — `GET /connect/logout` 정상 요청 → 302 `post_logout_redirect_uri` · IdP 세션 종료. 잘못된 요청 → OIDC 오류(내부 자격 401 아님).
- [ ] **AC-2** — 실제 보안 체인을 지나는 시험 빨강 → 초록 · 대조(내부 경로는 여전히 내부 자격 401).
- [ ] **AC-3** — 배포(굽기) · 라이브(콘솔 로그아웃 → `console.hubwang.com/login` 착지, 다음 창).

# Related Specs

- `projects/iam-platform/specs/services/auth-service/architecture.md`

# Related Contracts

- OIDC RP-Initiated Logout 1.0 · `projects/iam-platform/specs/contracts/http/` 의 auth 계약(AC-0 이 이름을 적는다)

# Edge Cases

- `id_token_hint` 가 만료된 경우 — 규약상 처리(거절 또는 확인 화면)를 AC-0 이 적는다.
- 32차 창에서 로그아웃 직후 콘솔로 직접 들어가면 IdP 세션이 남아 있어 비밀번호 없이 다시 로그인될 수 있다 — 지금의 실질적 영향.

# Failure Scenarios

1. Order 2 체인을 `/connect/**` 전체에 열어 내부 경로가 노출된다 — 대조 시험이 막는다.
2. 슬라이스 시험으로 초록 — 실제 다중 체인 순서를 안 지난다.
