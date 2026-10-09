# Task ID

TASK-FE-108

# Status

ready

# Title

가입 직후 이어지는 첫 스토어 로그인이 «인증 서버 설정에 문제가 있습니다» (NextAuth `Configuration`) 로 실패한다 — 재시도는 성공

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- auth
- bug
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 가입 직후 재방문 로그인 경로의 쿠키/상태 흐름을 가르는 인증 설계 판단.

---

# Dependency Markers

- 출처: 25차 데모 창(2026-10-09 UTC, AMI `ami-03cc7efda4b0a7809`) — 오케스트레이터 측정(auth_db · auth-service 로그).
- 관련: `TASK-FE-067`(gap oauth cutover) · `TASK-FE-070`(rp-initiated logout) · IAM 가입(signup) → 인증 메일 → 콘솔/스토어 재방문 경로 전반.

---

# Goal

25차 창 실측(auth_db · auth-service 로그, UTC):

| 사실 | 값 |
|---|---|
| 신규 소비자 계정 `shopper1@demo.com` 가입 | 18:25:08Z |
| 그 가입 직후, 같은 브라우저로 스토어 로그인 시도 | `oauth2_authorization`(client `ecommerce-web-store-client-id`) — authorization code 발급 18:25:19Z, **`access_token` NULL**(코드가 콜백에서 교환되지 않았거나 교환이 실패) |
| 그 시각 auth-service 쪽 ERROR 로그 | 없음 |
| 스토어 화면 | NextAuth `Configuration` 오류 — «인증 서버 설정에 문제가 있습니다» |
| 같은 계정으로 재시도 | 18:39:46Z code 발급 → 18:39:47Z **access_token 발급 성공**(즉시 교환) |

⇒ 가입 직후 이어지는 **첫** 로그인 시도만 실패하고, 재시도는 1초 안에 성공한다. 이메일 미인증은 원인이 **아니다** — 재시도 성공 시점에도 이메일은 여전히 미인증 상태였다(인증 여부가 바뀌지 않았는데 결과가 바뀌었다).

가설(미검증, AC-0 가 재측정): 가입 → 로그인 재개(resume) 경로가 스토어 콜백으로 돌아올 때 스토어 자신의 state/PKCE 쿠키가 없거나(가입 흐름이 다른 오리진/리다이렉트를 거치며 쿠키가 비었거나) 기존 쿠키와 값이 어긋나, NextAuth 가 이를 `Configuration` 오류로 보고한다. 이 가설이 맞는지는 실제 NextAuth 에러 타입(`OAuthCallbackError`/`PKCE`/`state` 불일치 등)을 서버 로그에서 읽어야 판정된다 — 이 티켓의 AC-0 가 그 재측정이다.

# Scope

## In Scope

- **AC-0 = 재측정(가정 금지)** — 로컬 또는 e2e 로 "가입 → 즉시 첫 로그인" 경로를 재현하고, 스토어 서버(NextAuth) 로그에서 실제 에러 타입·메시지를 읽는다(현재 `config_error` 매핑이 가리키는 원문 로그 포함). 가입 흐름이 거치는 리다이렉트/쿠키 쓰기 지점을 file:line 으로 추적해 "어떤 쿠키가 없거나 어긋나는가" 를 표로 남긴다.
- AC-0 가 가리키는 지점의 구현(쿠키 수명/경로/도메인 정합, 또는 가입→로그인 재개 리다이렉트 순서 보정 등 — 판정 뒤 결정).
- 단위/통합 시험: 가입 직후 첫 로그인 재현 시나리오(대조군: 재시도·기존 계정 로그인 무변화).

## Out of Scope

- 이메일 인증 자체의 흐름 변경(이 결함과 무관함이 이미 실측으로 배제됨).
- IAM 쪽 가입/인증 코드 변경 — AC-0 가 원인을 IAM 가입 리다이렉트 모양으로 지목하면 범위를 다시 열고 크로스 프로젝트 티켓으로 분리한다(Failure Scenario 참조).

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: 로컬/e2e 로 "가입 → 즉시 첫 로그인" 을 재현하고 스토어 서버 로그의 실제 NextAuth 에러 타입을 읽는다. 가정하지 않고 file:line 으로 쿠키/리다이렉트 경로 표를 남긴다.
- [ ] **AC-1** — 🔴 대조군: "가입 직후 첫 로그인"은 실패(현재) → 구현 뒤 성공. "이미 가입된 계정의 일반 로그인"(대조군)은 이전·이후 모두 변화 없이 성공.
- [ ] **AC-2** — 재시도 경로(현재 성공) 가 구현 뒤에도 그대로 성공 — 회귀 없음.
- [ ] **AC-3** — 라이브 데모 창에서 신규 가입 → 즉시 첫 로그인이 재시도 없이 성공. ⚪ 다음 데모 창.

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth.ts`
- `projects/ecommerce-microservices-platform/apps/web-store/src/features/auth/ui/LoginForm.tsx`(`config_error` 매핑)
- IAM 가입(signup) → 로그인 재개(resume) 경로 — `projects/iam-platform/specs/contracts/http/auth-api.md`

# Related Contracts

- OAuth2/OIDC 인가 코드 흐름(RFC 6749 § 4.1), PKCE(RFC 7636) — 스토어 NextAuth 클라이언트 측.

# Edge Cases

- 가입 직후 **다른 브라우저/탭**에서의 첫 로그인은 같은 결함을 재현하지 않을 수 있다(세션 쿠키가 처음부터 없는 경우와 "있지만 어긋난" 경우가 다른 경로일 수 있음) — AC-0 에서 구분해서 잴 것.
- 이메일 인증 여부와 독립적임을 — 재측정 때도 — 대조군으로 다시 확인할 것(이번 실측이 n=1 이다).

# Failure Scenarios

1. "이메일 미인증이 원인" 으로 다시 가정하고 그 경로만 고친다 — 재시도 성공 사실이 이미 이 가정을 반증한다.
2. 원인이 IAM 가입 리다이렉트 모양(크로스 프로젝트)인데 스토어 쪽 쿠키 설정만 넓혀 덮는다 — 증상은 가려지고 다른 클라이언트(콘솔 등)의 같은 경로는 안 고쳐진다.
3. AC-0 없이 "쿠키 수명을 늘린다" 류의 추측 수정부터 한다 — 재현 없이는 고쳤는지 알 수 없다.
