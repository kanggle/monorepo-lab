# Task ID

TASK-MONO-574

# Title

`ADR-MONO-067` **AC-0 ③** — HTTPS 프런트 ↔ 평문 IdP 로그인이 **끝까지** 되는지 실측한다. 이 축이 안 풀리면 단계 3·4 는 못 간다.

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- measurement
- auth

---

# ⏳ 선행

| 선행 | 왜 |
|---|---|
| `TASK-MONO-571`(AC-0 ②) | ② 가 거짓이면 (B) 가 통째로 무너져 이 측정이 무의미해진다 |
| **데모 인스턴스 기동** | 🔴 **사용자 승인 대상.** 실제 IdP 가 떠 있어야 왕복이 성립한다. 컨트롤 API `POST /start` 로만 기동(예산 회계). 부팅 약 11분 |

**② 통과 + 기동 승인 전에는 시작하지 마라.**

---

# Goal

`ADR-MONO-067` § AC-0 3번 항목을 실측한다.

> **OIDC 왕복** — HTTPS 프런트 ↔ 평문 IdP 에서 로그인이 끝까지 되는지. 🔴 *"안 될 것 같다"* 가
> 아니라 **실측**이어야 한다. 이 저장소는 쿠키 축에서 두 방향 모두 데인 적이 있다.

---

# Context — 왜 이것이 프록시로 안 덮이는가

세 앱 다 next-auth v5 OIDC 다. 소비 지점의 **성격이 갈린다**(2026-08-23 fan 실측, console·web-store 도 같은 라이브러리):

| 소비 | 종류 | 프록시로 흡수되나 |
|---|---|---|
| discovery(`.well-known`) · `/oauth2/token` | 서버 fetch | ✅ D2 와 같은 취급 |
| **authorize 리다이렉트** · **`/connect/logout`** | **최상위 내비게이션** | ❌ **불가** |

최상위 내비게이션은 mixed content 규칙 **밖**이라 이동 자체는 막히지 않는다. 그래서 *"열리니까
된다"* 로 오독하기 쉽다 — 그러나 **열리는 것과 왕복이 끝나는 것은 다른 질문**이다.
[[env_top_level_navigation_is_exempt_from_mixed_content]]

## 이미 아는 걸림돌 (실측)

- **`redirect_uri` 가 Flyway 시드다.** fan 의 등록된 앱 루트는 `http://localhost:3002/` 와
  `http://fan-platform.local/`(GAP V0011+V0028, `federated-logout.ts` 주석). Vercel 도메인을
  넣으려면 **마이그레이션 변경**이고, preview URL 은 배포마다 달라서 **production 고정 도메인**이
  전제다.
- **`issuer` 가 부팅마다 바뀐다.** discovery 문서와 토큰의 `iss` 클레임이 함께 움직이는데
  next-auth 는 issuer 를 **설정값**으로 받는다.
- 🔵 **쿠키는 오히려 유리하다** — fan `session.ts` 가 `NEXTAUTH_URL` 이 `https://` 면
  `secureCookie` 를 켜므로 Vercel 에서 자동으로 맞는다.

---

# Acceptance Criteria

## AC-0 — 전제 재확인 (verify-then-act)

착수 시점에 다음이 여전히 참인지 본다. 하나라도 어긋나면 STOP 하고 티켓을 갱신한다.

- `TASK-MONO-571` 의 AC-0 ② 결과가 **참**이다.
- 데모 인스턴스가 `running` 이고 IdP 가 응답한다(`/status` 의 `state`).
- 앱의 `redirect_uri` 등록값이 이 티켓이 적은 것과 같다 — **시드 파일을 grep 해서** 확인한다.
  기억이 아니라 정의 파일이 근거다.

## AC-1 — 왕복을 **단계별로** 기록한다, 성패 한 줄이 아니라

로그인은 여러 홉이고, **어디서 끊기는지가 조치를 가른다.** 각 홉의 상태 코드와 `Location`,
그리고 쿠키의 `Set-Cookie` 속성을 그대로 남긴다.

| 홉 | 무엇을 확인 |
|---|---|
| ① 앱 → `/api/auth/signin` | 302 의 목적지가 IdP 인가 |
| ② 브라우저 → IdP authorize (평문 HTTP) | 페이지가 뜨는가. **여기서 막히면 브라우저 정책** |
| ③ IdP → 앱 콜백 (평문 → HTTPS) | `state`/PKCE 쿠키가 **살아 돌아오는가** |
| ④ 앱 서버 → IdP token | 서버 fetch. ② 와 다른 축이다 |
| ⑤ 세션 쿠키 | `Secure`/`SameSite` 가 무엇으로 붙었나 |

🔴 **③ 이 이 티켓의 본체다.** 크로스사이트 최상위 리다이렉트라 `SameSite=Lax` 면 살아야
하는데, 이 저장소는 쿠키 축에서 **양방향으로** 데인 적이 있다. 추론하지 말고 찍어라.

## AC-2 — 로그아웃도 같이 잰다

`/connect/logout` 은 authorize 와 **같은 종류**(최상위 내비게이션)이고 별도로 깨질 수 있다.
로그인만 재고 "왕복 OK" 라고 적지 않는다.

## AC-3 — 판정과 **판정하지 못한 것**을 함께 적는다

- 참: ①~⑤ 가 전부 통과하고 보호된 페이지가 렌더된다.
- 🔴 **거짓일 때 "무엇이" 거짓인지 적는다** — 브라우저 정책 / 쿠키 / `redirect_uri` 불일치 /
  `iss` 불일치는 **다른 결함**이고 후속 조치가 전부 다르다.
- 잰 앱이 하나면 **그 앱에 대해서만** 적는다. 세 앱이 같은 라이브러리를 쓴다는 것은
  **가설이지 측정이 아니다**.

## AC-4 — 결과를 `ADR-MONO-067` § AC-0 3번에 기록한다

거짓이면 **D4 의 후속 결정**(`TASK-MONO-576`)의 입력이 된다. 이 티켓은 결정을 내리지 않는다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § AC-0 (3), § D4
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/iam-platform/specs/features/consumer-integration-guide.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/` — `redirect_uri` 등록이 계약이면 여기가 근거다.
  **변경은 이 티켓 범위 밖**(측정만 한다).

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 브라우저가 ② 에서 경고만 띄우고 진행한다 | **경고는 실패가 아니다.** 무엇이 떴는지 기록하고 왕복을 계속한다 |
| `redirect_uri` 불일치로 IdP 가 거절한다 | 이건 **설정 미비**이지 스킴 경계 문제가 아니다. 시드를 맞춘 뒤 다시 잰다 — 그 전 결과로 ③ 을 판정하지 않는다 |
| 데모가 부팅 중(`starting`)이라 IdP 가 502 | 측정 아님. `running` 을 기다린다 |
| 한 앱만 쟀다 | AC-3 대로 그 앱에 대해서만 적는다 |
| 프리뷰 URL 로 재려 한다 | ❌ **Deployment Protection 이 302 로 막는다**(2026-08-23 실측). 프로덕션 별칭만 공개다 |

---

# Failure Scenarios

| 실패 | 뜻 | 다음 |
|---|---|---|
| ③ 에서 `state`/PKCE 쿠키가 유실 | 스킴 경계가 진짜 벽이다 | D4 가 (B) 로 안 풀린다 ⇒ **EC2 TLS 종단 축**이 유력해진다 |
| ② 에서 브라우저가 차단 | 내비게이션 예외가 이 조합엔 안 통한다 | 같은 결론, 더 강함 |
| ④ 만 실패 | 서버 fetch 축 = AC-0 ② 와 같은 문제 | ② 결과와 대조한다. 둘이 어긋나면 **하나가 틀린 것** |
| 전부 통과 | (B) 가 인증 축에서도 성립 | D4 를 (B) 로 확정하는 근거. 단 **잰 앱에 한해서다** |
