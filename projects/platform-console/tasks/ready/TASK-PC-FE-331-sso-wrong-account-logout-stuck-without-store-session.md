# Task ID

TASK-PC-FE-331

# Title

`sso_wrong_account` 안내의 탈출구가 «스토어 탭 로그아웃» 하나뿐 — 스토어 세션이 없으면 사용자가 갇힌다

# Status

ready

# Owner

platform-console

# Task Tags

- platform-console
- iam
- auth

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — IdP 로그아웃 표면 또는 토큰교환 거절 코드 설계 판단, 소유자 결정 가능성 높음.

---

# Dependency Markers

- 출처: 25차 데모 창(2026-10-09 UTC) — `TASK-PC-FE-324` 가 만든 `sso_wrong_account` 안내를 실제로 밟다가 발견.
- 관련: `TASK-PC-FE-324`(done — `sso_wrong_account` 코드·문구·로그아웃 버튼 도입) · `widgets/sso-wrong-account-logout/SsoWrongAccountLogout.tsx`(한계 문서화) · `TASK-BE-610` 계열(콘솔 재인증).

# Goal

25차 창 실측: IAM 세션은 `shopper1@demo.com`(소비자) 로 유효하지만 **스토어 탭 세션이 없는**(예: `TASK-FE-108` 로 스토어 로그인 자체가 실패한 경우, 또는 스토어 탭을 이미 닫은 경우) 상태에서 콘솔에 로그인을 시도하면 `sso_wrong_account` 안내가 뜬다. 안내문의 유일한 탈출구는 «스토어(쇼핑몰) 탭에서도 로그아웃» 인데, 그 탭이 없으면 밟을 동작이 없다 — 콘솔의 로그아웃 버튼을 누르고 다시 로그인해도 같은 안내가 반복된다(auth 로그: 콘솔 거절 18:29:44 · 18:30:33 · 18:30:42Z, 세 번 동일). 시크릿 창을 모두 닫는 것 외에는 벗어날 길이 없었다.

원인은 이미 `widgets/sso-wrong-account-logout/SsoWrongAccountLogout.tsx` 에 문서화돼 있다 — 이 실패는 콘솔이 토큰을 받기 전에 나므로 `id_token` 이 없고, IAM 이 쓰는 Spring Authorization Server(1.4.1) 의 `OidcLogoutAuthenticationProvider` 는 `id_token_hint` 없는 end_session 요청을 세션 레지스트리 우회 없이 즉시 `invalid_token` 거절한다. 즉 콘솔에는 **IdP 세션을 끝낼 수 있는 토큰이 아예 없다** — 안내문이 "스토어 탭 로그아웃" 을 가리키는 것도 그 id_token 이 스토어 탭에만 있기 때문인데, 그 탭이 없으면 가리킬 곳이 없다.

# Scope

## In Scope

- **AC-0 = 재측정 + 방향 평가(결정 아님)** — 다음 후보를 실측 기반으로 비교하고 장단점·필요 변경 범위를 표로 남긴다(소유자 결정 전제):
  1. IAM 이 **폼 로그인 기반** 로그아웃을 노출하는 경로가 있는가(쿠키/세션 로그아웃, id_token 불필요) — `auth-service`(또는 그 앞단)에 이미 그런 엔드포인트가 있는지 확인. 있으면 콘솔이 그쪽으로 링크.
  2. IdP 측(`BE-610` 계열) 에서 콘솔 클라이언트가 소비자 세션을 아예 재사용하지 않고(`prompt=login` 강제 등) 거절 대신 **재인증을 요구**하게 바꾸는 안 — 거절이 "같은 계정으로 다시 로그인하라" 로 바뀌면 안내·탈출구 문제 자체가 없어질 수 있다.
  3. 콘솔이 쿠키 전부(세션·로그인 상태)를 지우는 "로컬 초기화"만으로 재로그인 시도 시 IdP 가 다시 소비자 세션을 SSO 재사용하는지(안 한다면 로컬 초기화만으로도 충분할 수 있다) — 실측.
- 판정된 방향의 구현(크로스 프로젝트면 별도 ADR/티켓으로 분리 — Failure Scenario 참조) + 시험(스토어 탭 없음 시나리오 · 있음 시나리오 대조군).

## Out of Scope

- `sso_wrong_account` 자체의 도입 로직 재작업(`TASK-PC-FE-324` 로 이미 닫힘) — 이 티켓은 탈출구가 없는 경우만 다룬다.

# Acceptance Criteria

- [ ] **AC-0** — 위 세 후보의 실측 비교 표 + 추천(소유자 결정 전 단계). IAM 쪽 변경이 필요하면 범위·영향(다른 콘솔 클라이언트·스토어 자체 로그인에 미치는 영향)을 함께 적는다.
- [ ] **AC-1** — 소유자 결정 뒤 구현: 스토어 탭이 없는 사용자가 콘솔에서 벗어날 수 있는 경로가 생긴다(단위/통합 시험).
- [ ] **AC-2** — 대조군: 스토어 탭이 있는 기존 경로(`TASK-PC-FE-324` AC-3 가 라이브로 확인한 흐름)는 그대로 동작.
- [ ] **AC-3** — 라이브 데모 창: 스토어 탭을 닫은 상태에서 콘솔 재진입 → 새 경로로 로그아웃 → 운영자 계정 로그인 성공. ⚪ 다음 데모 창.

# Related Specs

- `projects/platform-console/apps/console-web/src/widgets/sso-wrong-account-logout/SsoWrongAccountLogout.tsx`
- `projects/iam-platform/specs/contracts/http/auth-api.md` § 로그아웃/end_session
- `docs/adr/` — `BE-610` 계열 ADR(있다면)

# Related Contracts

- OIDC RP-Initiated Logout(end_session_endpoint) — SAS 1.4.1 제약(`id_token_hint` 필수) 확인 필요.

# Edge Cases

- 폼 로그인과 소셜 로그인 양쪽에서 같은 탈출구가 동작해야 한다(둘 다 콘솔이 id_token 을 못 가진 채 거절되는 경우가 있을 수 있음 — AC-0 에서 확인).
- "로컬 초기화" 만으로 해결된다면 IAM 변경 없이 끝낼 수도 있다 — AC-0 의 3번 후보가 그것을 가린다. 성급히 IAM 변경부터 설계하지 않는다.

# Failure Scenarios

1. IdP(auth-service) 코드를 이 티켓(platform-console 소유) 범위에서 바로 바꾼다 — 크로스 프로젝트 원자적 PR 이 아니라 별도 ADR/티켓이 맞다(`CLAUDE.md` Cross-Project Changes).
2. "시크릿 창을 쓰라" 를 안내문에 추가하는 것으로 끝낸다 — 증상을 가릴 뿐 탈출구를 만들지 않는다.
3. AC-0 없이 `prompt=login` 강제부터 넣는다 — 스토어 자체의 SSO 편의(같은 브라우저에서 콘솔·스토어 전환)를 조사 없이 깰 수 있다.
