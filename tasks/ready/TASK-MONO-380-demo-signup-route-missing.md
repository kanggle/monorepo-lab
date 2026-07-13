# Task ID

TASK-MONO-380

# Title

데모의 유일한 입구가 404 였다 — OIDC 엣지 라우터에 `/signup` 이 없다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

---

# Goal

새 AMI 로 부팅한 데모에서 **아무도 로그인할 수 없다.** 컨테이너 96개는 전부 healthy 하고, `/login` 은 200 을 주고, 라우터도 "있다".

`iam` OIDC 엣지 라우터 규칙은 **경로를 열거한다**:

```
Host(`iam.<도메인>`) && (PathPrefix(`/oauth2`) || PathPrefix(`/login`) || PathPrefix(`/.well-known`))
```

**`/signup` 이 없다.** 그 요청은 iam **게이트웨이** 라우터로 떨어지고, 게이트웨이엔 그런 경로가 없어 **404** 다.

**그런데 로그인 폼 자신이 `/signup` 으로 링크를 건다.**

## 왜 치명적인가 — 가입이 유일한 입구다

**갓 부팅한 데모의 `credentials` 테이블은 비어 있다.** 실측: 새 AMI 로 부팅한 인스턴스에서 `SELECT email FROM credentials` 가 **0행**.

⇒ 방문자가 데모에 들어가는 **유일한 경로가 가입**이고, **그 입구가 404 다.**

**`TASK-MONO-358` 의 로그인 증명이 통했던 이유는 그 인스턴스의 DB 에 계정이 손으로 누적돼 있었기 때문이다.** 새 부팅에는 없다 — 그리고 **방문자가 도착하는 상태가 바로 그 새 부팅 상태다.**

---

# 관측 (2026-07-13, `TASK-MONO-366` 실기동 증명 — 새 AMI `ami-051cd83db9a46eea2`)

부팅 계약 자체는 **완전히 동작했다**(SSM 접속 0회):

| | |
|---|---|
| IMDSv2 도메인 파생 | `[boot] profile=full DEMO_DOMAIN=15-165-79-252.sslip.io` ✅ |
| Traefik 라우터 | 파생 도메인으로 등록 확인 ✅ |
| `seed-demo-domain.sh` | 부팅 중 실행, DB 에 `http://console.<도메인>/api/auth/callback` 등록 ✅ |
| console | **200**, PKCE·state 쿠키 **저장됨**(`secure=False`) ✅ |
| `/oauth2/authorize` | redirect_uri **수용** → **302 → 파생 도메인의 로그인 폼** ✅ |
| 로그인 폼 | **200**, CSRF·username 필드 존재 ✅ |
| **`/signup`** | **404** ❌ |

**358 의 결함 C·D·E·F·G·H 는 전부 부팅 경로에서 해소된 채로 떴다.** 366 이 하려던 일은 됐고, **막는 것은 이 결함 하나다.**

## 함께 기록 — 프로브가 멀쩡한 시스템을 결함으로 보고할 뻔했다

`/oauth2/authorize` 가 처음엔 **401** 을 냈다. `TASK-MONO-358` 이 기록한 *"결함 E(redirect_uri 미등록) → 401"* 의 지문과 같아서 재발로 보고할 뻔했다.

**아니었다 — 프로브 탓이다.** Spring Security 는 `Accept` 헤더에 `text/html` 이 없으면 로그인 페이지로 리다이렉트하는 대신 **401** 을 던진다. PowerShell `Invoke-WebRequest` 의 기본 `Accept` 는 `*/*` 다. 브라우저 헤더를 주자 **302 → `/login`** 이 나왔다.

**탐지식이 아는 답을 찾아내는지 먼저 확인할 것** — 이 저장소가 이미 여러 번 데인 클래스(`grep -c` 가 Javadoc 을 셈 / `jq` 의 `\(` 보간 / `perl` 치환이 CRLF 에 실패).

---

# 설계

## 1) 라우터 규칙에 `/signup` 추가

한 줄이다. GET `/signup`(폼) + POST `/signup`(제출) 둘 다 같은 프리픽스로 덮인다.

## 2) 가드 (p) — 열거하지 말고 대조하라

**경로를 손으로 열거하는 것이 드리프트의 원인**이므로, 가드가 다시 열거하면 같은 실수를 반복한다.

**템플릿이 실제로 링크하는 경로**(`login.html` / `signup.html` 의 Thymeleaf `@{/...}`)를 뽑아 **라우터 규칙의 `PathPrefix` 와 대조**한다. 링크가 새로 생기면 가드가 먼저 운다.

**mutation 필수** — 이번 세션에서 가드가 **세 번** 가짜였다(자기 주석 매치 / 도달 불가 / `grep -P` 실패를 `|| true` 가 삼킴). 통과는 증거가 아니다.

---

# Scope

## In Scope

- `infra/demo/iam-traefik.override.yml` — `iam-oidc` 규칙에 `PathPrefix(/signup)`.
- `infra/demo/verify-demo-wrapper.sh` — 가드 (p).
- **AMI 재빌드 + `TASK-MONO-366` 의 무인 로그인 왕복 완주**(가입 → 로그인).

## Out of Scope

- **데모 계정 시딩** — 방문자가 가입할 수 있으면 충분하다. 샘플 데이터 시딩은 `MONO-366` 이 이미 별개 증분으로 분리했다.
- auth-service 의 브라우저 표면 재설계 — 라우팅만 고친다.

---

# Acceptance Criteria

- [ ] **`/signup` 이 데모 도메인에서 200** — 라우터가 덮는다.
- [x] **가드 (p) 가 실제로 문다 — mutation 4방향 확인.** 통과는 증거가 아니다:
      - P1 규칙에서 `/signup` 제거(= 원래 결함) → **FAIL** ✅ (누락 경로를 지목)
      - P2 템플릿에 새 링크 추가(미래 드리프트) → **FAIL** ✅ (`/reset-password` 지목)
      - P3 규칙에서 `/login` 제거 → **FAIL** ✅
      - P5 vacuity: 정상 트리 **PASS** ✅
      **⚠️ P2 의 첫 시도는 무효였다** — `perl` 정규식이 `@{...}` 의 중괄호에서 깨져(`Search pattern not terminated`) **mutation 이 적용조차 되지 않았고**, 그 상태의 `ok` 는 *"가드가 물지 않는다"* 는 증거가 **아니었다**(아무것도 안 바꿨으니 통과가 당연하다). `sed` 로 다시 넣고 **적용 여부를 먼저 확인한 뒤**(`reset-password` 가 링크 집합에 등장) 가드가 무는 것을 봤다. ***mutation 이 적용됐는지부터 확인하라*** — 이 저장소 4번째 재발.
- [ ] **무인 로그인 왕복 — `TASK-MONO-366` 의 핵심 AC.**
      새 AMI → `terraform apply` → `POST /start` → **SSM·SSH 접속 없이** 브라우저로 **가입 → 로그인 → 인증된 세션.**
- [ ] CI GREEN.

---

# Edge Cases

- **POST `/signup`** 은 같은 `PathPrefix` 로 덮인다 — 별도 규칙 불필요.
- **`/login/oauth/{provider}`** 는 이미 `PathPrefix(/login)` 이 덮는다.
- **`Accept` 헤더** — 검증 프로브는 브라우저 헤더를 보내야 한다. `*/*` 면 Spring Security 가 401 을 주고, 그건 **시스템 결함처럼 보인다**(§ 관측).
- **CSRF** — 가입/로그인 폼 제출은 `_csrf` 토큰이 필요하다. curl+grep 는 XOR 토큰을 오추출하므로 **PowerShell `iwr -SessionVariable` 왕복**으로 검증한다.

# Failure Scenarios

- **`/signup` 없이 배포** → 컨테이너 전부 healthy, `/login` 200, **그런데 아무도 데모에 못 들어온다.** 포트폴리오 방문자가 보는 것은 로그인 폼과 죽은 가입 링크뿐이다.
- **가드가 경로를 손으로 열거** → 다음에 템플릿이 링크를 하나 더 걸면 **같은 결함이 조용히 재발**한다. 가드는 **템플릿과 대조**해야 한다.
- **누적된 DB 로 검증** → 358 이 그랬다. 계정이 이미 있으면 가입 경로가 죽어도 로그인이 된다 ⇒ **검증은 반드시 새 부팅에서.**

# Test Requirements

- 정적: `verify-demo-wrapper.sh` (a)~(p) PASS + **(p) mutation 4방향**.
- **실기동**: 새 AMI 로 부팅 → **SSM 미접속** → 가입 → 로그인 → 인증 세션.

# Definition of Done

- [ ] 위 AC 전부
- [ ] CI GREEN
- [ ] `tasks/INDEX.md` done entry
- [ ] **`TASK-MONO-366` 을 닫을 수 있게 된다**

---

# Provenance

2026-07-13, `TASK-MONO-366` 의 무인 실기동 증명 중 발견. **부팅 계약은 전부 동작했고, 이 결함 하나가 로그인을 막았다.**

`TASK-MONO-379`(AMI 를 굽지 못하게 하던 em dash)와 **같은 실기동에서 연달아 나왔다** — 둘 다 *"저장소는 옳다고 선언하는데 실제로 돌려본 적이 없다"* 클래스다. 379 는 **빌드**가, 380 은 **부팅 결과**가 검증된 적이 없었다.

**선행**: `TASK-MONO-379`(그것 없이는 AMI 를 구울 수 없다).

분석=Opus 4.8 / 구현 권장=Sonnet (라우터 한 줄 + 가드 — 다만 **가드가 열거하지 않고 대조해야** 한다).
