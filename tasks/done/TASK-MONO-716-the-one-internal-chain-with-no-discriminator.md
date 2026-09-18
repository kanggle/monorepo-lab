# Task ID

TASK-MONO-716

# Title

🔴 iam `admin-service` 의 `/internal/**` 은 **판별자가 하나도 없다** — 같은 issuer 가 사용자 토큰도 민팅하므로 «서명+issuer+시간+`.authenticated()`» 는 «아무 토큰이나» 와 같다. 형제 셋은 `RequiredScopeValidator` 로 막는다

# Status

done (2026-09-18 UTC — AC-0 ~ AC-4 닫힘 · AC-1 의 ⚪ 는 계약서 명문화로 닫았다)

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

- [x] **AC-0 (선행) — «보완 장치가 정말 없는가» 를 넓은 모집단으로 다시 잰다.** 위 Goal 의 네 줄을 착수 시점 `origin/main` 에서 재확인하고, 🔴 **698 이 보지 않은 자리까지** 본다: 전역 method-security 설정(`@EnableMethodSecurity` 와 그 기본값) · 다른 `OncePerRequestFilter`/`HandlerInterceptor` · 컨트롤러 상위 클래스. 🔵 **보완 장치가 있으면 이 티켓은 «대상 소멸» 로 닫는다** — 그것은 미충족이 아니라 **기록**이고, 698 § AC-4 (c) ② 의 판정을 정정하는 것이 그 자체로 산출물이다.
- [x] **AC-1 (선행) — 호출자를 실측한다. 🔴 이걸 건너뛰고 켜면 정당한 내부 호출이 401 된다.** `admin-service` 의 `/internal/**` 두 엔드포인트를 **누가** 부르는지, 그 호출자의 토큰이 **`internal.invoke` scope 을 갖는지**를 **설정된 base URL + client 등록**으로 댄다(`TASK-MONO-696` § AC-1 (b) 와 같은 규율 — **부재 grep 으로 «안 온다» 를 말하지 않는다**). 못 대는 칸은 ⚪ 로 남기고 이유를 적는다. 🔴 ⚪ 가 남은 채 켜지 않는다 — 형제 셋이 이미 같은 scope 을 요구하므로 **호출자가 그 scope 을 이미 받고 있을 가능성이 높지만, 「높다」는 측정이 아니다**.
- [x] **AC-2 — 판별자 추가.** 기본안: 형제 셋과 **같은** `RequiredScopeValidator("internal.invoke")` 를 디코더 사슬에 추가. 🔵 **일부러 같은 것을 고른다** — 넷이 같은 축으로 판별하면 그 축 하나만 감시하면 되고, 네 번째만 다른 축을 쓰면 «어느 것이 무엇을 막는지» 를 매번 다시 읽어야 한다. 🔴 다른 축(예: subject allowlist)을 고르려면 **AC-1 의 측정이 그 근거**여야 하고 사유를 적는다.
- [x] **AC-3 — bite.** 판별자를 빼면 빨개지는 칸이 있다: **사용자 access token 모양**(같은 issuer · 같은 서명 · `internal.invoke` scope 없음)으로 `/internal/**` 을 부르면 **거절**된다는 단언. 🔴 형제 셋에 같은 칸이 있는지 먼저 보고(있으면 그 형태를 그대로 쓴다), **없으면 그 사실 자체를 기록한다** — 셋이 판별자를 갖고도 테스트가 없다면 그건 별개의 관측이다(이 티켓에서 고칠지는 판단해 적는다).
- [x] **AC-4 — 검증.** `:projects:iam-platform:apps:admin-service:check` rc=0(파이프 금지, rc 명시). Testcontainers IT 는 CI 가 권위. 🔵 데모가 떠 있으면 admin 내부 호출 경로 1회 성공을 확인한다(AC-1 의 호출자가 실재하면).

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

- [x] AC-0 ~ AC-4 (또는 AC-0 이 «대상 소멸» 로 판정하고 그 판정이 이 파일에 기록됨)
- [x] `admin-service` `/internal/**` 이 형제 셋과 **같은 축으로** 워크로드를 판별한다
- [x] 🔵 `TASK-MONO-698` § AC-3 항목 6 트리거 ⓑ 의 집합이 **비었다**(또는 비지 않았다면 남은 원소를 이름으로 적었다)

---

# 🟢 AC-0 — 보완 장치는 **정말 없다** (넓힌 모집단, 2026-09-18 UTC · 분석=Opus 5)

698 이 본 세 자리를 재확인하고, **698 이 보지 않은 자리까지** 봤다. 결과는 «대상 소멸» 이 아니라
**결함 확정**이다.

| 후보 | 판정 |
|---|---|
| 디코더 사슬 | `createDefaultWithIssuer(issuer)` **하나뿐** — 확인 |
| `@Order(0)` `/internal/**` 게이트 | `.requestMatchers("/internal/**").authenticated()` **한 줄** — 확인 |
| `InternalApiFilter` | non-terminal, **거절하지 않는다**(dev/test 바이패스 전용) — 확인 |
| **`@EnableMethodSecurity`** (698 미확인) | 🔴 **의도적으로 부재**하다. 클래스 javadoc 이 그렇게 적어 뒀다 — *"intentionally NOT present: all authorization decisions flow through the single aspect path"* |
| **`@PreAuthorize` / `@Secured`** (698 미확인) | **0건** |
| **`@RequiresPermission`** (698 미확인) | 🔴 **0건.** 이것이 결정적이다 — 이 애스펙트가 이 서비스의 **유일한 인가 축**인데, `/internal/**` 두 컨트롤러가 그것을 **안 단다** |
| 컨트롤러 상위 클래스 | 없음(둘 다 `public class X` 단독) |
| 다른 필터 | `Bootstrap*` · `Operator*` 는 **다른 체인**(`@Order(2)` `/api/admin/**`)에 붙는다 |

⇒ 이 사슬에 워크로드 판별자가 **한 겹도 없다**. 계약서 rule 5 *Behind the edge* 가
«판별자가 하나도 없는 사슬은 결함» 이라고 적은 그 상태다.

🔵 **«대상 소멸» 갈래를 미리 적어 둔 것이 값을 했다** — 그 갈래를 확인하는 과정에서
`@RequiresPermission` 0건이라는 **가장 강한 증거**가 나왔다. 「보완 장치가 있는지」를 묻지 않았다면
«인가 애스펙트가 있는 서비스니까 어딘가 걸리겠지» 로 넘어갔을 자리다.

# 🟢 AC-1 — 호출자 실측

## 엔드포인트 1: `GET /internal/operator-assignments/check`

| 항목 | 실측 |
|---|---|
| 호출자 | auth-service `AdminAssignmentClient` (토큰 발급 시 운영자 배정 확인) |
| 자격 | `IamClientCredentialsTokenProvider` — **형제 셋이 쓰는 그 provider** |
| 요청 scope | `IamTokenProviderConfig:28` **`INTERNAL_INVOKE_SCOPE = "internal.invoke"`** (주석이 *"Scope requested for every `internal.invoke`-gated `/internal/**` call"*) |
| client id | `auth-service-client` (`application.yml` `iam.internal-client.client-id` 기본값) |
| 그 client 가 scope 을 **받는가** | 🟢 **받는다** — `V0019__seed_internal_service_workload_clients.sql` 이 그 client 에 `'["internal.invoke"]'` 를 시드한다 |

⇒ **켜도 이 경로는 401 이 되지 않는다.** Failure Scenario 가 경고한 «운영자 프로비저닝 경로가
조용히 401» 은 이 측정으로 닫힌다.

## 엔드포인트 2: `POST /internal/admin/operator-oidc-subject-backfill` — ⚪ 였고, **정의로 닫았다**

🔴 저장소 안에 **자동 호출자가 없다**. 그리고 그 «없음» 을 grep 으로 주장하지 않았다 —
696 AC-1 (b) 규율이 금지하는 부재 판정이다. 대신 **계약서가 스스로 말하는 것**을 읽었다:

- `admin-maintenance-internal.md` — *"**호출 방향**: 운영/배포 도구 (client) → admin-service"*,
  *"**인증**: GAP `client_credentials` Bearer JWT (fail-closed)"*.
- `ADR-MONO-040` 실행 로그 — 이 백필의 일회성 목적은 **2026-06-18 EXECUTED (P3 part A)** 로
  이미 끝났다. 남아 있는 것은 재실행 가능한 idempotent 도구다.

🔴 **그런데 «GAP client_credentials 면 다 통과» 는 거짓이다.** 마이그레이션을 전수하니 다른 플랫폼의
워크로드 client(wms · scm · finance · erp · community)가 **`internal.invoke` 없이** 존재한다.
즉 «워크로드면 scope 이 있다» 는 성립하지 않는다.

**⇒ 그래서 ⚪ 를 «남긴 채 켜지» 않고, 계약으로 없앴다.** AC-1 이 금지한 것은 «미측정 호출자를
추측으로 통과시키는 것» 이다. 계약서에 **요구 scope 을 명문화**하면 호출자 요건은 *추측*이 아니라
*정의*가 된다 — 형제 셋이 간 길 그대로다(`TASK-MONO-422` / `TASK-BE-514`). 개정한 내용:

> `admin-maintenance-internal.md` § 인증에 **«요구 scope: `internal.invoke`, 없으면 401»** 을
> 추가하고, 🔴 그것이 «강화» 가 아니라 **«누락의 보정»** 임을 적었다 — 그 문서는 이미
> «GAP `client_credentials` Bearer JWT» 를 요구하고 있었고, **집행되지 않았을 뿐**이다.
> 호출 도구 제작자를 위해 **어느 client 가 그 scope 을 갖는지**(V0019 의 넷)와 **갖지 않는지**
> (타 플랫폼 워크로드)를 이름으로 적었다.

🔵 **남는 위험을 축소하지 않고 적는다**: 저장소 밖에서 누군가 `internal.invoke` 없는 client 로 이
백필을 호출하고 있었다면 이 변경으로 401 이 된다. 그것은 (a) 이미 실행이 끝난 일회성 도구이고
(b) 수동 호출이라 **즉시 보이며** (c) 애초에 계약서가 요구하던 자격이 아니었다. 운영자 프로비저닝
같은 **조용한 회귀**의 모양이 아니다.

# 🟢 AC-2 — 판별자 추가: 형제와 **같은 것**

`RequiredScopeValidator("internal.invoke")` 를 디코더 사슬에 더했다(`libs/java-web`). 속성 이름도
형제와 같은 `internal.api.jwt.required-scope`. 🔵 **일부러 같은 것을 골랐다** — 넷이 한 축으로
판별하면 그 축 하나만 감시하면 되고, 네 번째만 다른 축이면 «무엇이 무엇을 막는지» 를 매번 다시
읽어야 한다. 다른 축(subject allowlist 등)을 고를 근거가 AC-1 측정에 없었다.

🔵 사슬을 **package-private `internalTokenValidator()`** 로 떼어냈다 — 형제의 형태 그대로이고,
이유도 같다: 테스트가 **이 클래스가 실제로 조립하는 사슬**을 물어야지, 재구현한 사본을 물면
`internalJwtDecoder()` 가 사슬 설치를 그만둬도 초록이다.

# 🟢 AC-3 — bite, 그리고 **형제에 이미 있던 형태**

AC-3 이 시킨 대로 **형제를 먼저 봤다**: `SecurityConfigInternalValidatorTest` 가 account-service ·
auth-service · security-service 에 **이미 있다**. 새 모양을 발명하지 않고 그대로 복제했다.

🔴 **이 파일이 «그 게이트가 검증되는 유일한 자리» 인 이유**(Edge Case 가 경고한 것): admin-service 의
`/internal/**` 통합 테스트는 `test` 프로파일에서 돌고, 그 프로파일은 `InternalApiFilter` 바이패스를
**켠다** — 요청이 bearer 필터 **이전에** 인증되어 **디코더에 도달하지 않는다**. 그러므로 그 스위트의
초록은 이 게이트에 대해 **아무 말도 하지 않는다**.

| 칸 | 성격 |
|---|---|
| auth-service 워크로드 토큰(`internal.invoke`) → 통과 | 대조군 — AC-1 이 실측한 그 호출자 |
| **운영자 브라우저 토큰 모양**(같은 issuer · scope 없음) → 거절 | **bite** — 716 이전엔 **통과했다** |
| **타 플랫폼 워크로드 토큰**(`wms.read`) → 거절 | **bite** — «scope 만 있으면 된다» 가 아님을 못박는다 |
| 잘못된 issuer + 올바른 scope → 거절 | 대조군 — 사슬을 **교체**하지 않고 **덧붙였는지** |

**bite 실측**: `RequiredScopeValidator` 한 줄을 사슬에서 빼고 돌리니 **4칸 중 정확히 2칸 빨강**
(운영자 토큰 · 타 플랫폼 토큰), 대조군 2칸은 **초록 유지**. 🔵 전부 빨개졌다면 그것은 «칸이 4개» 가
아니라 «사실상 1개» 라는 뜻이다. 복원 후 그 줄이 제자리인 것도 확인했다.

# 🟡 AC-4 — 검증

- `:projects:iam-platform:apps:admin-service:check` → **rc=0** (파이프 없음)
- 🔵 **실제로 돌았는지 셌다**: 전체 **854칸 실행**(skipped 58 · 실패 0), 그중 **신규 스위트 4칸이
  실행**(skipped 0). rc 만 보고 넘기지 않는다.
- 🔴 Testcontainers IT 는 CI 가 권위 — 다만 위에 적은 대로, 이 게이트에 관해서는 **IT 초록이 증거가
  아니다**(바이패스가 켜져 디코더를 지나지 않는다). 이 티켓의 판정은 **bite 가 있는 단위 칸**이다.
- ⚪ **데모 창 확인은 못 했다** — AC-4 의 «데모가 떠 있으면» 조건부 항목이고, 창이 없다.
  🔵 다만 AC-1 이 «호출자가 그 scope 을 받는다» 를 **설정과 시드로** 댔으므로, 이 ⚪ 는 확인이지
  판정이 아니다.

# 🟢 트리거 ⓑ 의 집합 — **비었다.** 다만 그것을 **두 번째 술어**로 확인했다

`TASK-MONO-698` § AC-3 항목 6 트리거 ⓑ 는 «판별자 없는 내부 사슬» 의 집합이고, 716 은 그 집합의
**유일한 알려진 원소**였다. 「비었다」를 선언하려면 세야 한다 — 저장소 전체에서
`securityMatcher("/internal/**")` 사슬을 전수했다(**5개**):

| 사슬 | 판별자 | 축 |
|---|---|---|
| iam `account-service` | 🟢 있음 | `RequiredScopeValidator("internal.invoke")` |
| iam `auth-service` | 🟢 있음 | 〃 |
| iam `admin-service` | 🟢 **이 티켓이 추가** | 〃 |
| fan `artist-service` | 🟢 있음 | `.hasRole("INTERNAL")` + `WorkloadIdentityAuthoritiesConverter` |
| fan `membership-service` | 🟢 있음 | 〃 |

⇒ **원소 0개. 트리거 ⓑ 는 이제 «새로 생기면» 이라는 원래 의미로 작동한다.**

## 🔴 첫 술어가 틀렸고, 하마터면 결함 두 건을 지어낼 뻔했다

처음 센 술어는 **«`RequiredScopeValidator` 를 참조하는가»** 였다. 그 술어로는 fan 의 두 사슬이
**판별자참조=0** 으로 나왔다 — 즉 «판별자 없는 사슬이 둘 더 있다» 는 결론이 나온다. **거짓이다.**

그 둘은 판별자가 **있고**, 축이 다를 뿐이다: `.anyRequest().hasRole("INTERNAL")` 이고 그 역할은
`WorkloadIdentityAuthoritiesConverter` 가 부여하는데, 그 클래스 javadoc 이 *"lacks the required
scope gets no authority"* 라고 적는다 — **결국 같은 scope 축**을 권한 부여의 형태로 표현한 것이다
(membership 의 javadoc 도 *"End-user token → 403"* 을 명시한다).

🔵 이 저장소가 이름 붙인 «가드의 술어가 틀림» 이 내 인구조사에서 그대로 재현됐다. 묻고 싶었던 것은
**«판별자가 있는가»** 인데 잰 것은 **«이 클래스를 쓰는가»** 였다. 0 을 보고 멈추지 않고 **그 파일을
열어 본 것**이 유일한 차이였다 — 열지 않았으면 이 티켓이 «fan 두 건도 같은 결함» 이라는 오보를
남겼을 것이다.

🔵 그리고 이 관측에는 부수 효과가 있다: 네 번째를 형제 셋과 **같은 축**에 맞춘 지금도, 저장소의
내부 사슬은 **두 축**(scope 검증기 / 역할 컨버터)으로 갈려 있다. 통합할지는 이 티켓의 범위가
아니지만, «한 축만 감시하면 된다» 는 AC-2 의 근거가 **iam 셋 안에서만** 참이라는 것은 적어 둔다.
