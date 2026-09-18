# Task ID

TASK-MONO-716

# Title

🔴 iam `admin-service` 의 `/internal/**` 은 **판별자가 하나도 없다** — 같은 issuer 가 사용자 토큰도 민팅하므로 «서명+issuer+시간+`.authenticated()`» 는 «아무 토큰이나» 와 같다. 형제 셋은 `RequiredScopeValidator` 로 막는다

# Status

ready

# Owner

monorepo

# Task Tags

- security
- code

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus (AC-0 의 호출자 실측이 이 티켓의 무게다 — 켜는 것 자체는 형제 셋의 한 줄 복제이고, 잘못 켜면 정당한 내부 호출이 401 된다)
>
> 📎 **출처**: `TASK-MONO-698` § AC-0 (b) S5↔S6 비대칭 + § AC-4 (c) ② 판정. 🔴 698 은 이것을 **결함으로 단정하지 않았다**(«필터 층 보완 미측정») — 그 ⚪ 는 698 § AC-4 (c) ② 에서 닫혔고, 이 티켓은 그 판정 **위에** 선다.
>
> 🔵 **이 티켓은 `TASK-MONO-698` § AC-3 항목 6 트리거 ⓑ 가 이름 부른 «오늘의 그 하나» 다.** 이것이 닫히면 그 집합이 비고, 트리거 ⓑ 는 «새로 생기면» 이라는 원래 의미로 작동한다.

# Goal

iam 의 서블릿 `/internal/**` 디코더는 넷이다. 그중 **셋**(`account`·`auth`·`security`)은 `RequiredScopeValidator("internal.invoke")` 를 달았고(`TASK-MONO-422`/`TASK-BE-514`), 그 주석이 이유를 적어 뒀다 — *"같은 issuer 가 시스템 토큰과 사용자 토큰을 둘 다 민팅하므로 서명+issuer 로는 구별되지 않는다"*.

🔴 **`admin-service` 만 그 판별자가 없다.** 2026-09-18 UTC 저장소 실측(`TASK-MONO-698` § AC-4 (c) ②, 본 곳을 이름으로 댄다):

- `apps/admin-service/.../infrastructure/config/SecurityConfig.java:104-108` — 디코더는 `createDefaultWithIssuer(issuer)` **단 하나**.
- 같은 파일 `:123-140` — `@Order(0)` `/internal/**` 사슬의 게이트는 **`.requestMatchers("/internal/**").authenticated()`** 한 줄.
- 같은 패키지 `InternalApiFilter.java:21-35, 60-68` — **운영 프로파일에서는 비활성(non-terminal)** 이며 *"It never rejects"* 라고 스스로 밝힌다(dev/test 바이패스 전용) ⇒ **보완 필터가 아니다**.
- `presentation/internal/` 두 컨트롤러(`OperatorOidcSubjectBackfillController` · `OperatorAssignmentCheckController`) — `@PreAuthorize` **0건**.
- 🔴 **모집단의 한계**: 본 것은 위 세 자리뿐이다. 전역 method-security 설정이나 다른 인터셉터는 **안 봤다** ⇒ AC-0 이 다시 센다.

⇒ 이 사슬은 **IdP 가 발급한 아무 토큰**(운영자의 브라우저 access token 포함)이나 `/internal/**` 에 들여보낸다. 이 티켓이 끝나면: 이 사슬도 형제 셋과 **같은 축으로** 워크로드를 판별한다.

# Scope

## In Scope

- AC-0 재측정(보완 장치의 부재 + **호출자 실측**)
- `admin-service` `/internal/**` 사슬에 판별자 추가 — 기본안은 형제 셋과 **같은** `RequiredScopeValidator("internal.invoke")`
- 그 결정에 맞춘 호출자 쪽 scope 확인(호출자가 그 scope 을 실제로 받아 오는가)
- 영향받는 테스트

## Out of Scope

- **audience allowlist 를 여기에 넣는 것** — `TASK-MONO-698` § AC-3 항목 1 이 «엣지 뒤에는 적용 안 함» 으로 결정했고, 계약서 rule 5 *Behind the edge* 가 그 이유를 적는다. 이 티켓이 메우는 것은 **판별자의 부재**이지 `aud` 가 아니다
- 형제 셋(`account`·`auth`·`security`) — 이미 판별자가 있다
- `/internal/**` 밖의 admin-service 표면(`/api/admin/**` 등)
- iam gateway 엣지 — `TASK-MONO-713`

# Acceptance Criteria

- [ ] **AC-0 (선행) — «보완 장치가 정말 없는가» 를 넓은 모집단으로 다시 잰다.** 위 Goal 의 네 줄을 착수 시점 `origin/main` 에서 재확인하고, 🔴 **698 이 보지 않은 자리까지** 본다: 전역 method-security 설정(`@EnableMethodSecurity` 와 그 기본값) · 다른 `OncePerRequestFilter`/`HandlerInterceptor` · 컨트롤러 상위 클래스. 🔵 **보완 장치가 있으면 이 티켓은 «대상 소멸» 로 닫는다** — 그것은 미충족이 아니라 **기록**이고, 698 § AC-4 (c) ② 의 판정을 정정하는 것이 그 자체로 산출물이다.
- [ ] **AC-1 (선행) — 호출자를 실측한다. 🔴 이걸 건너뛰고 켜면 정당한 내부 호출이 401 된다.** `admin-service` 의 `/internal/**` 두 엔드포인트를 **누가** 부르는지, 그 호출자의 토큰이 **`internal.invoke` scope 을 갖는지**를 **설정된 base URL + client 등록**으로 댄다(`TASK-MONO-696` § AC-1 (b) 와 같은 규율 — **부재 grep 으로 «안 온다» 를 말하지 않는다**). 못 대는 칸은 ⚪ 로 남기고 이유를 적는다. 🔴 ⚪ 가 남은 채 켜지 않는다 — 형제 셋이 이미 같은 scope 을 요구하므로 **호출자가 그 scope 을 이미 받고 있을 가능성이 높지만, 「높다」는 측정이 아니다**.
- [ ] **AC-2 — 판별자 추가.** 기본안: 형제 셋과 **같은** `RequiredScopeValidator("internal.invoke")` 를 디코더 사슬에 추가. 🔵 **일부러 같은 것을 고른다** — 넷이 같은 축으로 판별하면 그 축 하나만 감시하면 되고, 네 번째만 다른 축을 쓰면 «어느 것이 무엇을 막는지» 를 매번 다시 읽어야 한다. 🔴 다른 축(예: subject allowlist)을 고르려면 **AC-1 의 측정이 그 근거**여야 하고 사유를 적는다.
- [ ] **AC-3 — bite.** 판별자를 빼면 빨개지는 칸이 있다: **사용자 access token 모양**(같은 issuer · 같은 서명 · `internal.invoke` scope 없음)으로 `/internal/**` 을 부르면 **거절**된다는 단언. 🔴 형제 셋에 같은 칸이 있는지 먼저 보고(있으면 그 형태를 그대로 쓴다), **없으면 그 사실 자체를 기록한다** — 셋이 판별자를 갖고도 테스트가 없다면 그건 별개의 관측이다(이 티켓에서 고칠지는 판단해 적는다).
- [ ] **AC-4 — 검증.** `:projects:iam-platform:apps:admin-service:check` rc=0(파이프 금지, rc 명시). Testcontainers IT 는 CI 가 권위. 🔵 데모가 떠 있으면 admin 내부 호출 경로 1회 성공을 확인한다(AC-1 의 호출자가 실재하면).

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 ***Behind the edge*** — 특히 *"판별자가 하나도 없는 사슬은 이 규칙 아래에서 **결함**이다"* 줄(이 티켓이 그 줄의 유일한 알려진 대상이다)
- `platform/contracts/jwt-standard-claims.md` § Gateway Enforcement Rules — `client_credentials` 토큰의 scope 축, 그리고 *"Surfaces that gate on scope or on a subject allow-list (`/internal/**`) keep doing exactly that"*
- `platform/service-types/identity-platform.md` § Integration Rules 규칙 3
- `tasks/review/TASK-MONO-698-audience-behind-the-gateway-and-at-the-other-edges.md` § AC-0 (b) S5↔S6 · § AC-1 (b) iam 행 · § AC-3 항목 6 · § AC-4 (c) ②

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (형제 방향의 내부 계약 — 이 표면의 호출자 규약을 대는 출발점)

# Target Service

- iam `admin-service`

# Edge Cases

- **호출자가 `internal.invoke` 를 안 받고 있으면** — 켜는 순간 401 이다. AC-1 이 그걸 먼저 잰다. 이 경우의 선택지(호출자 client 등록에 scope 추가 / 다른 축)는 **측정 후** 정한다.
- **dev/test 바이패스** — `InternalApiFilter` 는 `test`/`standalone` 프로파일에서 인증을 **만들어 준다**. 🔴 그러므로 **슬라이스 테스트가 초록인 것은 운영 동작의 증거가 아니다**(바이패스가 켜져 있으면 판별자를 지나지도 않는다). AC-3 의 칸은 바이패스가 **꺼진** 경로에서 돌아야 한다.
- **`tenant_id` 는 여기서 의도적으로 안 본다** — `internalJwtDecoder` 주석이 *"`tenant_id` is intentionally NOT pinned"* 라고 적는다. 이 티켓은 그 결정을 **뒤집지 않는다**(판별자 축만 더한다).
- **두 엔드포인트의 성질이 다르다** — 하나는 백필(쓰기), 하나는 조회다. 같은 사슬이므로 같이 막히지만, AC-1 의 호출자 표는 **엔드포인트별**로 적는다.

# Failure Scenarios

- 🔴 **측정 없이 켜기** — 운영자 프로비저닝 경로가 조용히 401 이 되고, 증상은 «콘솔에서 운영자 배정이 안 된다» 로 나타나 원인이 이 사슬로 안 보인다. AC-1 이 막는다.
- 🔴 **관측을 결함으로 승격한 채 착수** — 보완 장치가 실은 있는데 «없다» 로 적고 고치면, 같은 판정이 두 곳에 생긴다. AC-0 이 막는다(그리고 **대상 소멸로 닫는 갈래**를 미리 적어 둔 이유다).
- **audience allowlist 로 메우기** — 698 의 결정과 계약서 *Behind the edge* 에 정면으로 반한다. § Out of Scope 가 막는다.
- **바이패스가 켜진 프로파일에서 초록을 보고 닫기** — 판별자를 지나지도 않은 초록이다. AC-3 Edge Case 가 막는다.

# Test Requirements

- **거절 칸**: 같은 issuer·서명의 **사용자 access token 모양**(판별 scope 없음) → `/internal/**` **거절**
- **통과 칸**: `internal.invoke` scope 을 가진 `client_credentials` 토큰 → 200
- **대조군**: 기존 401 계약(토큰 없음 / 잘못된 issuer)이 **그대로**
- 형제 셋에 같은 형태의 칸이 있으면 그 형태를 복제한다(새 모양을 발명하지 않는다)
- 🔴 파이프로 판정하지 않는다 — 출력은 파일로, rc 는 명시

# Definition of Done

- [ ] AC-0 ~ AC-4 (또는 AC-0 이 «대상 소멸» 로 판정하고 그 판정이 이 파일에 기록됨)
- [ ] `admin-service` `/internal/**` 이 형제 셋과 **같은 축으로** 워크로드를 판별한다
- [ ] 🔵 `TASK-MONO-698` § AC-3 항목 6 트리거 ⓑ 의 집합이 **비었다**(또는 비지 않았다면 남은 원소를 이름으로 적었다)
