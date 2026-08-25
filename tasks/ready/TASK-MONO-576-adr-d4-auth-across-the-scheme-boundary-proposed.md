# Task ID

TASK-MONO-576

# Title

`ADR-MONO-067` **D4**(OIDC·쿠키 축)를 **PROPOSED ADR** 로 분리해 쓴다. 이 축이 안 풀리면 단계 3·4 는 못 간다.

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- auth
- decision

---

# ⏳ 선행

`TASK-MONO-574`(AC-0 ③ OIDC 왕복 실측)의 **결과가 이 ADR 의 근거**다.
🔴 **실측 없이 쓰지 마라** — `ADR-MONO-067` § AC-0 이 못 박았다: *"「안 될 것 같다」가 아니라
실측이어야 한다. 이 저장소는 쿠키 축에서 두 방향 모두 데인 적이 있다."*

---

# Goal

`ADR-MONO-067` 이 **의도적으로 미룬** D4 를 별도 ADR(`ADR-MONO-068`, PROPOSED)로 쓴다.

ADR-067 본문:

> **D4(OIDC/쿠키)는 별도 결정.** 로그인 리다이렉트는 최상위 내비게이션이라 프록시로 못 감싼다.
> 이 축이 안 풀리면 단계 3·4 는 못 간다.

---

# Scope

## 포함

- `docs/adr/ADR-MONO-068-*.md` **PROPOSED** 작성.
- 선택지 열거 + 각각의 근거·비용·되돌리기 비용.
- `docs/adr/INDEX.md` 행 추가.

## 제외

- 🔴 **ACCEPT 하지 않는다.** 소유자의 **정확형 지정**만이 ACCEPT 이고, 제 추천은 선택이 아니다.
- 구현. 이 티켓은 결정 문서만 만든다.
- ADR-067 본문 수정 — D4 해소는 **후속 ADR 이 가리키는** 방식으로 한다.

---

# Context — 실측된 제약 (2026-08-23)

세 앱 다 next-auth v5 OIDC. 소비 지점의 성격이 갈린다:

| 소비 | 종류 | 프록시로 흡수 |
|---|---|---|
| discovery · `/oauth2/token` | 서버 fetch | ✅ |
| **authorize 리다이렉트** · **`/connect/logout`** | **최상위 내비게이션** | ❌ |

최상위 내비게이션은 mixed content 규칙 **밖**이라 이동은 막히지 않는다 — 그래서
*"열리니까 된다"* 로 오독되기 쉽다. [[env_top_level_navigation_is_exempt_from_mixed_content]]

딸린 제약:

- **`redirect_uri` 가 Flyway 시드**(fan: `http://localhost:3002/`·`http://fan-platform.local/`,
  GAP V0011+V0028) ⇒ Vercel 도메인을 넣으려면 마이그레이션 변경, 그리고 **production 고정 도메인**이
  전제(preview URL 은 배포마다 다르다).
- **`issuer` 가 부팅마다 바뀐다** — discovery 와 `iss` 클레임이 함께 움직이는데 next-auth 는
  issuer 를 설정값으로 받는다.
- 🔵 쿠키는 유리하다 — `NEXTAUTH_URL` 이 `https://` 면 `secureCookie` 가 자동으로 켜진다.

---

# Acceptance Criteria

## AC-0 — 574 의 결과를 읽고 시작한다

574 가 **어느 홉에서** 무엇을 관측했는지에 따라 선택지 집합이 달라진다. 통과했다면 (B) 유지가
후보에 남고, ③ 에서 쿠키가 유실됐다면 (B) 는 인증 축에서 성립하지 않는다.

🔴 574 가 없으면 **STOP**. 추론으로 채우지 않는다.

## AC-1 — 선택지를 **최소 3개** 열거하고, 각각 반증 가능하게 쓴다

최소한 아래를 다룬다(더 있으면 추가):

| # | 축 | 핵심 질문 |
|---|---|---|
| A | (B) 유지 — 인증도 평문 IdP 로 왕복 | 574 가 참인가. `redirect_uri`·`issuer` 를 어떻게 고정하나 |
| B | **EC2 에 TLS 종단**(Traefik ACME + `sslip.io`) | AC-0 ②·③ 과 D4 가 **동시에** 사라진다. 비용=부팅 시간·LE rate limit |
| C | IdP 만 별도로 안정 도메인 뒤에 둔다 | 앱은 (B), IdP 만 고정. 부분 해결의 경계가 어디인가 |

각 선택지마다 **되돌리기 비용**을 적는다. 시드 마이그레이션이 걸린 선택지는 되돌리기가 비싸다.

## 🔴🔴 AC-1.5 — **움직이는 issuer** 를 어떻게 고정할지 답해야 한다 (2026-08-25 추가)

`TASK-MONO-574` 의 선행을 실측하다 드러났다. 선택지 열거에 **이 축이 빠져 있었다.**

`OIDC_ISSUER_URL` 은 Vercel env 라 **배포 시점에 고정**되는데, 데모 IdP 주소는
`iam.<ip-대시>.sslip.io` 라 **부팅마다 바뀐다.** 한 번 채워도 다음 부팅에 낡는다.

⇒ **D2(주소는 런타임 조회여야 한다)가 인증 축에서 그대로 재현된다. 그런데 더 어렵다** —
next-auth 는 `issuer` 를 **설정값**으로 받고, 그 값은 **discovery 문서**와 토큰의 **`iss` 클레임**
양쪽에 묶인다. 데이터 평면처럼 "부를 때마다 조회" 로 우회되지 않는다.

각 선택지는 이 질문에 답해야 한다:

| 선택지 | 움직이는 issuer 를 어떻게 다루나 |
|---|---|
| A (B 유지) | 부팅마다 Vercel env 갱신? 그러면 **재배포가 필요**하고 rate limit 을 먹는다 |
| B (EC2 TLS 종단) | 안정 도메인이 생기면 **이 문제도 같이 사라진다** — 이 축에서 B 의 값이 올라간다 |
| C (IdP 만 고정) | 정확히 이 문제만 겨냥한 안. 경계가 어디인지 적어라 |

🔵 이 관측 하나가 **B 의 무게를 바꾼다.** ADR 은 그것을 근거로 적되, **고르지는 않는다.**

## AC-2 — "무엇이 이 결정을 무효로 만드는가"를 적는다

🔴 각 선택지에 **반증 조건**을 붙인다. 조건 없는 결정은 나중에 왜 그렇게 골랐는지 알 수 없게 된다.
예: A 는 *"IdP 주소가 부팅마다 바뀌지 않게 되면 근거가 약해진다"*.

## AC-3 — 세 앱을 **한 덩어리로 다루지 않는다**

574 가 한 앱만 쟀다면, 나머지 둘에 대해서는 **"같은 라이브러리이므로 같을 것"이 가설임을 명시**한다.
[[feedback_independent_surfaces_never_measure_their_intersection]]

## AC-4 — PROPOSED 로 남긴다

- Status: **PROPOSED**. `## Decision` 은 비워 두거나 *"소유자 지정 대기"* 로 적는다.
- 🔴 **self-ACCEPT 0.** 그 사실을 ADR 본문에 명시한다(이 저장소의 선례 형식).
- `docs/adr/INDEX.md` 에 행 추가 — ADR 인덱스 드리프트 가드가 있다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § D4, § AC-0 (3)
- `platform/architecture-decision-rule.md` (ADR 작성 규정)
- `projects/iam-platform/specs/features/consumer-integration-guide.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/` — `redirect_uri` 등록이 계약이면 선택지마다
  **계약 변경이 필요한지**를 적는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 574 가 "전부 통과" 로 나온다 | A 가 유력해지지만 **B·C 를 지우지 않는다** — 통과가 곧 최선은 아니다(부팅마다 시드 갱신 비용은 남는다) |
| 574 가 판정 불가 | ADR 을 쓰되 **"근거 미확보"** 를 명시하고 ACCEPT 를 574 재측정에 건다 |
| B 를 고르면 ADR-067 이 무너지나 | 아니다 — 067 의 *"화면은 Vercel"* 은 유지되고 **스킴 축만** 바뀐다. 그 경계를 ADR 에 정확히 적는다 |
| ADR 번호가 068 이 아니게 된다 | 착수 시점에 `docs/adr/` 을 다시 세라. 번호는 **인용되면 식별자**가 되므로 나중에 못 바꾼다 |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 추측으로 채운 ADR | 선택지에 실측 인용이 없다 | AC-0 위반. 574 결과 없이는 쓰지 않는다 |
| 내가 고른 것을 결정으로 적는다 | Status 가 ACCEPTED | 🔴 **정확형 게이트 위반.** 추천은 추천으로만 적는다 |
| 067 과 모순되게 쓴다 | 두 ADR 이 같은 축에 다른 말 | D4 는 067 이 **미룬** 축이다. 067 을 뒤집는 선택지라면 **SUPERSEDE 를 명시**해야 하고, 그건 더 큰 결정이다 |
