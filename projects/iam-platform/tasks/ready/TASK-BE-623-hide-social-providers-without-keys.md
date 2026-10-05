# Task ID

TASK-BE-623

# Status

ready

# Title

로그인 화면이 **키가 설정되지 않은 소셜 제공자 버튼을 그리지 않는다** — 지금은 `OAuthProvider` 넷(Google·Kakao·Microsoft·Naver)을 무조건 그려서, 키가 없는 제공자의 버튼을 누르면 제공자 쪽 오류로 끝난다

# Owner

iam-platform

# Task Tags

- auth-service
- oauth-social
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (표시 술어 하나 + 시험)

---

# Dependency Markers

- 출처: 2026-10-06 소유자 대화 «현재 소셜 로그인이 작동돼?» — 데모 로그인 화면의 소셜 버튼 넷이 전부 가짜 키(`test-*-client-id`)로 동작하지 않는다는 것을 확인.
- 관계: `TASK-BE-617`(소셜 → 풀) · `TASK-MONO-763`(데모 배선)과 **같은 재굽기**에 실린다. 순서 제약은 없다 — 이 티켓은 버튼을 **줄이기만** 하므로 먼저 머지돼도 안전하다(키가 없으면 버튼이 없을 뿐).

# Goal

로그인 화면이 **실제로 쓸 수 있는 제공자만** 보여 준다. 키를 배포 환경에 넣으면 그 제공자의 버튼이 따로 손대지 않아도 나타난다.

# 실측 (2026-10-06 UTC)

- `LoginPageController.java:42` — `PROVIDERS = Arrays.stream(OAuthProvider.values())…` 로 **설정과 무관하게** 넷을 모두 모델에 싣는다. `login.html:52-57` 은 그 목록이 비어 있지 않으면 «또는 다음으로 계속» 구분선과 버튼을 그린다.
- `application.yml:82·97·107·119` — 네 제공자 모두 `client-id: ${OAUTH_<P>_CLIENT_ID:test-<p>-client-id}`. 데모·운영 어디에도 실제 값이 주입되지 않는다(`TASK-BE-617` AC-00, 2026-10-02 측정과 같음).
- ⇒ 지금 데모 로그인 화면엔 **누르면 실패하는 버튼 넷**이 있고, `projects/iam-platform/README.md:95` 는 «OAuth 소셜 로그인 — Google, Kakao, Microsoft» 를 기능으로 내세운다.

# Scope

## In Scope

- «설정된 제공자» 판정 하나: client-id 가 비어 있지 않고 `test-` 로 시작하지 않으며, client-secret 도 같은 조건을 만족한다. 판정은 `OAuthProperties`(설정 어댑터) 쪽에 둔다 — 화면이 설정 문자열 규칙을 알지 않게.
- `LoginPageController` 가 판정을 통과한 제공자만 `providers` 에 싣는다(목록이 비면 구분선도 안 나온다 — 템플릿은 그대로).
- 버튼이 없는 제공자로 `GET /login/oauth/{provider}` 를 직접 치면: 지금 동작(키가 가짜면 제공자 쪽 실패)을 **바꿀지** 결정해 적는다 — 추천: 같은 판정으로 `/login?error=provider_unavailable` 로 돌려보낸다(화면에 없는 문을 URL 로 열 수 없게).
- 시험: 판정 단위 시험 + 로그인 화면 MVC 시험(설정 0개 → 버튼·구분선 없음 / 하나 → 그 버튼만).
- 명세: `specs/features/oauth-social-login.md` 에 «버튼은 설정된 제공자만» 한 줄.

## Out of Scope

- 키 주입 자체(`TASK-MONO-763`) · 소셜 → 풀(`TASK-BE-617`).
- Meta 등 새 제공자(2026-10-06 소유자 결정: 보류).
- 버튼 디자인(제공자 브랜드 가이드) — 네이버 검수 때 별도 판단.

# Acceptance Criteria

- [ ] **AC-1** — 네 제공자 모두 기본값(`test-*`)이면 로그인 화면에 소셜 버튼 0개 · «또는 다음으로 계속» 구분선 없음.
- [ ] **AC-2** — Google 만 실제 형식 값이면 Google 버튼 하나만 보인다. 🔴 대조군: client-id 만 진짜이고 secret 이 `test-*` 면 **안 보인다**(반쪽 설정은 로그인을 못 끝낸다).
- [ ] **AC-3** — 버튼 없는 제공자로 직접 `GET /login/oauth/{provider}` → 위 Scope 결정대로(추천안이면 `provider_unavailable`).
- [ ] **AC-4** — bite: 판정을 «항상 참» 으로 바꾸면 AC-1 이 빨강.
- [ ] **AC-5 (라이브, ⚪)** — 재굽기 뒤 데모 로그인 화면: 키를 넣은 제공자 버튼만 보인다.

# Related Specs

- `projects/iam-platform/specs/features/oauth-social-login.md`
- `projects/iam-platform/specs/services/auth-service/architecture.md`

# Related Contracts

- 없음(화면 표시와 auth-service 내부 판정만 바뀐다. 외부 API·이벤트 무변경).

# Edge Cases

- client-id 가 공백만 있는 문자열 → 미설정으로 본다.
- Microsoft 는 `OAUTH_MICROSOFT_TENANT` 기본값(`common`)이 있어도 키 판정과 무관.
- 실제 키 형식은 제공자마다 다르다(Google `…apps.googleusercontent.com` 등) — 형식 검사는 하지 않는다. «기본값이 아니다» 만 본다(형식을 박으면 제공자가 형식을 바꾸는 날 버튼이 조용히 사라진다).

# Failure Scenarios

1. **판정을 화면 템플릿에 넣는다** — 설정 규칙이 두 곳으로 갈라지고 `/login/oauth/{provider}` 직접 진입은 여전히 열린다.
2. **client-id 만 본다** — secret 이 가짜인 반쪽 설정에서 버튼이 보이고 토큰 교환에서 실패한다(AC-2 대조군이 막는다).
3. **형식 정규식으로 판정한다** — 제공자 형식 변경에 조용히 깨진다.
</content>
</invoke>
