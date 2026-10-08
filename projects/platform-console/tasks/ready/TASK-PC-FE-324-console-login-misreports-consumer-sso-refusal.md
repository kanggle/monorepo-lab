# Task ID

TASK-PC-FE-324

# Title

같은 브라우저에 스토어(소비자) 로그인 세션이 있으면 IdP 가 콘솔 토큰 발급을 거절하는데, 콘솔은 그것을 **«인증 서버에 연결할 수 없습니다»** 로 보인다

# Status

ready

# Owner

platform-console

# Task Tags

- platform-console
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 콜백이 토큰 교환 실패의 **종류**를 가르는 일. IdP 쪽 동작(거절)은 옳다.

---

# Dependency Markers

- 출처: 24차 데모 창(2026-10-08 UTC) — `TASK-FE-107` 확인 뒤 같은 브라우저로 콘솔 로그인 시도.
- 관련: `TASK-BE-610`(done — 콘솔 SSO 가 소비자 세션을 재사용하던 결함) · `TenantClaimTokenCustomizer.refuseConsumerPoolTenant`(iam auth-service).

# Goal

관측(IdP 로그, 18:46–18:47Z 7회):

```
SECURITY: refused to mint a token whose tenant_id is the reserved pool value.
grantType=authorization_code, clientId=platform-console-web
```

1. 소유자가 스토어에서 소비자 계정으로 로그인 → `auth.hubwang.com` 세션 = 소비자 풀 계정.
2. 콘솔 로그인 → IdP 가 그 세션을 SSO 로 재사용 → authorization_code 교환에서 `invalid_grant`(풀 tenant 는 발급 금지 — 옳은 거절).
3. 콘솔 콜백은 그것을 `token_exchange_failed` 로 뭉개고 `/login` 이 **«인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.»** 를 보인다(`app/(auth)/login/page.tsx:22-23`).

⇒ 사용자는 «기다리면 된다» 로 읽고 계속 실패한다. 실제 해법은 «다른 계정으로 로그인돼 있다 — 로그아웃 후 다시». 데모 동선(스토어 ↔ 콘솔 같은 브라우저)에서 매번 밟힌다. 시크릿 창으로 우회했다.

# Scope

## In Scope

- 콜백이 토큰 응답의 `error=invalid_grant` + 풀 tenant 거절을 **별도 코드**로 구분해 `/login` 에 «다른(소비자) 계정으로 로그인돼 있습니다 — 로그아웃 후 운영자 계정으로 다시 로그인» + IdP 로그아웃(end_session) 경로를 보인다.
- 네트워크/5xx 실패는 지금 문구 유지.

## Out of Scope

- IdP 가 콘솔 authorize 에서 소비자 세션을 아예 재사용하지 않게(prompt=login 강제 등) — 별도 판단(BE-610 계열).

# Acceptance Criteria

- [ ] **AC-0** — 콜백이 토큰 오류 본문에서 무엇을 받을 수 있는지(error · error_description) 재측정 · 구분 술어 결정(문자열 일치 vs IdP 전용 코드).
- [ ] **AC-1** — 새 오류 코드 + 문구 + 로그아웃 링크(단위 · 콜백 라우트 시험, 두 갈래: 풀 거절 / 연결 실패).
- [ ] **AC-2** — bite: 구분 분기를 지우면 시험 하나만 빨강.
- [ ] **AC-3** — ⚪ 데모 창: 스토어 로그인 → 같은 브라우저 콘솔 로그인 → 새 문구 → 로그아웃 → demo@ 로그인 성공.

# Related Specs

- `projects/platform-console/specs/` § 인증 콜백 · `projects/iam-platform/specs/contracts/` § 토큰 엔드포인트 오류

# Related Contracts

- OAuth2 토큰 응답 오류(RFC 6749 § 5.2) — iam auth-service

# Edge Cases

- error_description 문구가 바뀌면 문자열 일치가 조용히 빗나간다 — AC-0 에서 정한다.

# Failure Scenarios

1. 모든 invalid_grant 를 «다른 계정» 으로 읽는다 — 만료 코드도 그 문구가 된다.
2. 문구만 바꾸고 로그아웃 경로를 안 준다 — 사용자가 여전히 갇힌다.
