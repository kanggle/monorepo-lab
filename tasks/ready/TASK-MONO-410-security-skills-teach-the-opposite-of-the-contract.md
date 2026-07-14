# Task ID

TASK-MONO-410

# Title

보안 스킬 3종이 계약과 **반대되는 것**을 가르친다 — 단수 `role` · 대칭키 HMAC · 인가 체크 없는 게이트웨이 필터

# Status

ready

# Owner

monorepo

# Task Tags

- code
- security

---

# Goal

`/validate-rules`(2026-07-15)가 `.claude/skills/` 의 **보안 스킬이 정경 계약을 정면으로 거스르는** 것을 찾았다. 스킬은 에이전트가 코드를 쓸 때 **실제로 읽고 따라 쓰는** 표면이다 — 여기가 틀리면 결함이 **새 코드로 계속 재생산된다.**

**① `backend/jwt-auth/SKILL.md`** (실측 확인)
- `:50` — `.claim("role", user.getRole().name())` **단수 `role`**. 계약 `platform/contracts/jwt-standard-claims.md` 는 **`roles` 집합이 "the sole authorization axis"**(ADR-MONO-032, 한 계정이 `CUSTOMER` + `WMS_OPERATOR` 를 동시에 가진다). **단일 값 클레임으로는 표현 자체가 불가능하다.**
- `:32,135` — **대칭키 `SecretKey` / HMAC-SHA256**. 계약 `:36` — **RSA 비대칭 + JWKS 검증**.

**② `backend/gateway-search`… 아니라 `backend/gateway-security/SKILL.md`**
- `:51-56` — `claims.get("role", String.class)` 로 읽어 `X-User-Role` 로 포워딩. 계약은 **`roles` 배열**(`X-User-Role ← comma-separated roles array`).
- `:30-59` — 참조 `JwtAuthenticationFilter` 에 **역할 기반 admission 체크가 아예 없다.** 계약 `:128` — *"Admit iff the token carries ≥ 1 role valid for the requested surface; otherwise `403`."* **스킬대로 쓰면 인증만 하고 인가는 안 하는 게이트웨이가 나온다.**
- `:150-158` — rate-limit 키가 **모든 라우트에서 무조건 client IP**. `platform/api-gateway-policy.md` 는 인증 트래픽을 **`acct:<sub>`** 로 키잉하라고 한다(IP 키잉은 NAT 뒤 전원이 한 버킷을 공유하고, IP 를 도는 남용자는 영원히 안 걸린다 — `MONO-368/370` 이 실제로 치른 대가).

**③ `backend/rate-limiting/SKILL.md:85-99`** — `/api/auth/refresh` 를 IP+path 로 키잉. fleet 표는 refresh 를 **`acct:<sub>`** 로 규정한다.

**④ `service-types/identity-platform-setup/SKILL.md:21-23`** — 엔드포인트 경로 3개가 스펙과 다름(`/oauth/token` vs `/v1/oauth/token`, 별도 `/refresh` 경로 — 스펙은 하나의 `/v1/oauth/token` 이 두 grant 를 처리, revoke 는 `/v1/oauth/revoke`).

**⑤ `service-types/rest-api-setup/SKILL.md:40,68`** — 자기 예제가 `/orders`, `/v1/orders/**` — **mandatory 한 `/api/` 접두사 누락**.

---

# Scope

## In Scope

- `.claude/skills/backend/jwt-auth/SKILL.md` · `backend/gateway-security/SKILL.md` · `backend/rate-limiting/SKILL.md` · `service-types/identity-platform-setup/SKILL.md` · `service-types/rest-api-setup/SKILL.md` — **계약에 맞게 정정**.
- 각 스킬이 **자기가 인용하는 계약을 실제로 가리키게** 한다(`platform/contracts/jwt-standard-claims.md` · `platform/api-gateway-policy.md`).
- **jwt-auth 의 존재 이유를 결정한다(AC-2)** — 대칭키 HMAC 발급이 *잘못된 게* 아니라 **다른 문맥**(standalone 로컬 서비스)일 수 있다. 그렇다면 스킬 상단에 **적용 범위를 못박아라**(*"IdP 가 발급하는 플랫폼 토큰에는 쓰지 말 것 — 그건 identity-platform"*). 지금은 아무 경계가 없어 **플랫폼 토큰을 만들려는 사람이 그대로 따라 쓴다.**

## Out of Scope

- **프로덕션 코드 수정 없음.** 이 task 는 **가르치는 문서**를 고친다. 실제 서비스가 단수 `role` 을 쓰고 있다면 그건 **별건 티켓**이다(AC-4 가 그 여부를 *조사만* 한다).
- `libs/java-security`·`libs/java-gateway` 의 실제 구현(이미 `ADR-MONO-049` 로 정경화됨 — 스킬이 그 라이브러리를 가리키게 하는 것으로 충분할 수 있다. AC-3 참조).

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** 5개 스킬을 **직접 열어** 위 인용이 오늘도 참인지 확인한다. **하나라도 이미 고쳐져 있으면 phantom 으로 기록하고 건드리지 않는다** — 감사 보고서는 가설이다. (이 티켓의 ①은 실측 확인됨, 나머지는 **PLAUSIBLE 등급**이다.)
- [ ] **AC-1** 5개 스킬이 계약과 모순 0. 특히 **`roles`(복수·배열)**, **RSA/JWKS**, **역할 admission 체크 존재**, **인증 트래픽 = `acct:<sub>` 키**.
- [ ] **AC-2** `jwt-auth` 스킬의 **적용 범위 선언** — 플랫폼 IdP 토큰용인가, standalone 용인가. 후자면 그 경계를 스킬 상단에 명시하고 IdP 경로를 `identity-platform-setup` 으로 라우팅한다.
- [ ] **AC-3 (사본을 만들지 마라)** 스킬이 보안 로직을 **다시 쓰는 대신** 이미 있는 정경 라이브러리(`libs/java-security`, `libs/java-gateway`)를 가리킬 수 있는지 먼저 검토한다. **`ADR-MONO-049` 가 servlet 보안 사본 49개를 0으로 만든 이유가 바로 "아무도 안 보는 사본"** 이다 — 스킬이 손으로 쓴 필터를 가르치면 **사본 50번째를 만드는 법을 가르치는 것**이다.
- [ ] **AC-4 (청구서 조사 — 고치지는 말 것)** 이 스킬들을 따라 쓴 **살아있는 코드가 있는가**를 grep 으로 조사만 한다(단수 `"role"` 클레임 · IP-only rate-limit 키 · admission 체크 없는 게이트웨이 필터). **발견되면 별건 티켓을 제안**하고 이 task 에서 고치지 않는다(프로덕션 보안 변경은 자기 티켓·자기 검증이 필요하다). **0건이면 0건이라고 적는다.**

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (**정경 — `roles` 단일 축, RSA/JWKS, admission 규칙**)
- `platform/api-gateway-policy.md` § Rate Limiting (**정경 — 인증=`acct:<sub>`, 익명=`ip`**)
- `platform/service-types/{identity-platform,rest-api}.md`
- `docs/adr/ADR-MONO-032-*`(roles 단일 축) · `ADR-MONO-049`(보안 사본 → lib)
- `tasks/done/TASK-MONO-368-*` / `TASK-MONO-370-*` (rate-limit 키 전략의 실제 인시던트)

# Related Skills

- 대상 그 자체(위 5개).

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

---

# Target Service

N/A — `.claude/skills/` 문서.

---

# Implementation Notes

- **이 결함은 CI 가 절대 못 잡는다** — 스킬은 컴파일되지 않고 테스트되지 않는다. **검증은 계약 문서와의 대조뿐**이고, 그래서 AC-0(재측정)과 AC-1(모순 0)이 이 티켓의 전부다.
- `.claude/skills/` 편집이 classifier 에 막히는지 **먼저 확인**하라(실측 지도상 `hooks/`·`agents/`·`commands/` 는 차단, `skills/` 는 불확실). 막히면 `MONO-409` 와 같은 절차(패치를 사용자에게).

---

# Edge Cases

- **단수 `role` 이 의도된 곳이 있는가** — 없다. 계약이 *"sole authorization axis"* 라고 못박았고 `account_type` 파티션은 ADR-032 로 제거됐다. 다만 **레거시 토큰 호환 서술**이 스킬에 필요한지는 계약의 § Migration Compatibility 를 읽고 판단.
- **rate-limit IP 키가 옳은 경우가 있다** — 익명 트래픽(login/signup, pre-auth)은 **IP 가 맞다**. 규칙은 "IP 금지" 가 아니라 **"인증 트래픽은 principal 로"** 다. 과잉 교정하면 로그인 폭주를 못 막는다.

---

# Failure Scenarios

- **스킬만 고치고 살아있는 코드를 안 본다** → 문서는 옳고 프로덕션은 틀린 상태. 완화 = AC-4(조사·별건 제안).
- **스킬에 보안 필터를 더 잘 써 준다** → 사본 50번째. 완화 = AC-3(lib 을 가리켜라).
- **감사 보고를 그대로 믿고 이미 옳은 스킬을 "고친다"** → phantom churn. 완화 = AC-0.

---

# Test Requirements

- N/A(문서). 대조 = 계약 문서.

---

# Definition of Done

- [ ] 5개 스킬 정정 + 계약 포인터.
- [ ] AC-3 lib 라우팅 판단 기록, AC-4 조사 결과(0건이면 0건) 기록.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules`. ①은 **직접 실측 확인**(`jwt-auth/SKILL.md:50` 단수 `role`, `:32` `SecretKey`), ②~⑤는 서브에이전트 보고(**PLAUSIBLE — 착수 시 재확인 필수**).

분석=Opus 4.8 / 구현 권장=**Opus**(보안 계약 해석 + lib 라우팅 판단. 문장 교체가 아니라 *무엇을 가르칠 것인가* 결정이다).
