# Task ID

TASK-MONO-515

# Title

콘솔 운영자는 ERP 결재함을 **영원히** 쓸 수 없다 — assume 토큰의 `sub` 가 클라이언트 id 라 모든 운영자가 한 사람이고, 자기결재 금지가 그것을 막는다

# Status

review

# Owner

monorepo

# Task Tags

- bug
- security
- demo

---

# 배경 — `TASK-MONO-510` 이 erp 시드를 만들다 발견

콘솔 `/erp/approval` 은 **결재 요청 목록 + 호출자의 결재함(inbox)** 을 함께 그리고,
상신·승인·반려·회수 버튼을 제공한다(`features/erp-ops/api/approval-mutations.ts`).
데모 운영자로 열면 목록은 차는데 **결재함은 항상 0** 이고, 어떤 건도 승인할 수 없다.

## 원인 — 세 가지가 겹친다. 셋 다 실측했다

**① 결재함은 actorId 로만 갈린다.** `ApprovalApplicationService.inbox` →
`findInbox(tenantId, actorId)`. `actorId` = JWT `sub`.

**② 자기결재가 금지된다.** `ApprovalRoute.multiStage` 가 *"submitter ∈ any stage"* 를
`ApprovalRouteInvalidException` 으로 거절한다. 즉 제출자 ≠ 승인자여야 한다.

**③ 그런데 assume 토큰의 `sub` 는 계정이 아니라 acting 클라이언트다.**

```
base 로그인 토큰   sub = 0199de70-0000-7000-8000-00000000ad03   (계정 UUID)
assume 후 토큰     sub = platform-console-web                   (클라이언트 id)
```

🔴 **이것은 결함이 아니라 명시된 동작이다.** `AssumeTenantExchangeIntegrationTest` 가
그대로 단언한다:

> *"The assumed token's own sub is the acting console client (platform-console-web) per
> the RFC 8693 flow — the account linkage is the validated subject token, not a sub claim
> on the assumed token."*

메커니즘도 문서화돼 있다: `AssumeTenantAuthenticationProvider` 가 `.principal(clientPrincipal)`
로 컨텍스트를 만들고, `TenantClaimTokenCustomizer.alignSubToAccountId` 는 principal 의
`details` 에 `account_id` 가 있을 때만 `sub` 를 덮는다 — 클라이언트 principal 에는 없다
(그 메서드가 스스로 *"graceful net-zero branch"* 라 부르는 분기).

## 그래서 무엇이 성립하지 않는가

`oauth_clients` 에서 token-exchange grant 를 가진 클라이언트는 **`platform-console-web`
하나뿐**이다(실측). ⇒ 테넌트 `demo-corp` 안에서 가능한 `actorId` 는 **정확히 하나**다.

| | 결과 |
|---|---|
| 운영자가 상신 → 승인자도 운영자 | ② 가 거절 (자기결재) |
| 운영자가 상신 → 승인자는 타인 | 결재함은 그 타인의 것. 운영자 결재함 **0** |
| 타인이 상신 → 승인자가 운영자 | 그 "타인" 을 만들 수 없다(①③) |

⇒ 콘솔의 결재함·승인/반려 버튼은 **어떤 콘솔 사용자에게도 도달 불가**다.

🔴 그리고 이것은 데모만의 문제가 아니다. **모든 콘솔 운영자가 동일한 `actorId` 를 갖는다**
는 뜻이므로, ② 의 직무분리(Separation of Duties)는 콘솔 사용자들 사이에서 **아무것도
분리하지 못한다** — 서로 다른 사람이 상신하고 승인해도 시스템이 보기엔 같은 주체다.
결재 이력(`submitterId`/`approverId`/감사 로그)도 전부 `platform-console-web` 이다.

# Goal

콘솔에서 결재 흐름이 **한 바퀴 돈다**: 누군가 상신하고 **다른** 누군가가 결재함에서
승인한다. 그리고 그 두 사람이 감사 기록에서 **구별된다.**

# Scope

## In Scope

- assume 토큰의 주체 표현 (또는 erp 결재의 actor 해석) 중 **어느 쪽을 고칠지 결정**
- 결정에 따른 iam auth-service 또는 erp approval-service 변경
- 감사 기록의 주체 구별성

## Out of Scope

- 결재 상신이 422 로 막히는 별개 결함 → **`TASK-ERP-BE-041`**
- 프로젝션이 비는 별개 결함 → **`TASK-ERP-BE-042`**

# 🔴 이 티켓은 **ADR 이 필요하다** — 자기 마음대로 고르지 말 것

세 방향이 있고 셋 다 계약을 건드린다:

| | 방향 | 대가 |
|---|---|---|
| **A** | assume 토큰의 `sub` 를 **계정 UUID** 로 (base 토큰과 동일) | `AssumeTenantExchangeIntegrationTest` 가 명시적으로 반대 단언을 하고 있다 ⇒ **문서화된 결정을 뒤집는 것**. 다운스트림 `X-User-Id` 의미가 전 도메인에서 바뀐다 |
| **B** | `sub` 는 두고 **별도 클레임**(`act` / `on_behalf_of`)으로 계정을 싣고, erp 가 그것을 actorId 로 읽는다 | RFC 8693 의 `act` 시맨틱과 맞다. 대신 **읽는 쪽이 도메인마다** 필요하다 — erp 만 고치면 다른 도메인의 감사 기록은 그대로 |
| **C** | 콘솔 운영자에게 **개인 워크로드 신원**을 부여 | 계정 수만큼 클라이언트가 생긴다 — 확장성 없음. 기록만 하고 배제 권장 |

**착수 전에 사용자에게 A/B 를 물어라.** `platform/architecture-decision-rule.md` 대상이며
(토큰 계약 변경), `ADR-MONO-020`(operator multitenant assignment) 과
`platform/contracts/jwt-standard-claims.md` 를 함께 고쳐야 한다.

🔵 **먼저 확인할 것**: 이 문제가 erp 고유인지, 다른 도메인의 "행위자" 기능도 같은지.
wms/scm/finance 에서 `sub` 를 사람 식별자로 쓰는 지점을 전수로 세라 — 모집단을 물려받지
말고 다시 세는 것. B 를 고르는 근거는 그 숫자에서 나온다.

# ✅ AC-0 재측정 (2026-08-07) — 핵심 전제 확인, **모집단은 티켓보다 하나 넓다**

## 핵심 주장을 토큰에서 직접 확인했다

```
assume 토큰 (demo-corp)   sub = "platform-console-web"
                          aud = "platform-console-web"
```

⇒ **`sub` 가 사람이 아니라 acting 클라이언트 id 다.** 티켓의 ③ 그대로다. 어느 운영자가
로그인하든 erp 는 같은 `actorId` 를 본다.

그리고 그 `actorId` 가 어디서 오는지도 공유 라이브러리에서 확인했다 —
`libs/java-security-servlet` `ActorClaims.from(jwt)`: `String accountId = jwt.getSubject();`
⇒ **모든 도메인의 `accountId` 가 곧 `sub`** 다(도메인별 파생이 아니다).

## 모집단 전수 — `sub` 를 **사람 식별자로 쓰는** 지점

🔴 원시 grep 카운트(`actor.accountId()|actorId`)는 erp 78 · fan 38 · finance 5 · scm 13 ·
**wms 733** 이 나왔다. **이건 대리지표다** — wms 의 733 은 전부 `WebhookInbox`
(이벤트 인박스)로 **사람과 무관**하다. 그래서 "조회 필터 / 동등 비교 업무규칙" 두 부류로
분류해서 다시 셌다:

| 도메인 | 조회 필터 | 동등 비교 업무규칙 | 계 |
|---|---|---|---|
| **erp** | `ApprovalApplicationService.inbox → findInbox(tenantId, actorId)` · `notification QueryInboxUseCase.findInbox(tenantId, recipientId)` | 자기결재 금지 | **3** |
| **fan** | `findFeedForFan` · `findByAuthor` · `notification findInbox` | `ChangePostStatus`/`UpdatePost` 저자 판정 · self-follow 금지 | **6** |
| **finance** | 0 | 0 | **0** |
| **scm** | 0 | 0 | **0** |
| **wms** | 0 | 0 | **0** |

finance·scm·wms 는 **0건** 이다(AC-0 이 요구한 대로 "0건" 이라고 적는다).

## 🔴🔴 신규 — 결재함이 **하나가 아니라 둘**이다

티켓은 `ApprovalApplicationService.inbox` 만 지목했다. `erp-platform` 의
**notification-service 도 같은 모양**이다:

```java
// NotificationInboxController
private String recipient(Jwt jwt) { return jwt.getSubject(); }   // ← sub
queryInbox.list(tenantId, recipientId, ...)                       // ← 사람 키 필터
```

⇒ assume 토큰으로 열면 이 인박스도 **구조적으로 같은 운명**이다. AC-1 의 결정과 AC-2 의
구현 범위는 **erp 인박스 2개**를 대상으로 해야 한다. 하나만 고치면 형제가 낙오한다.

🔵 **fan 의 6건은 이 결함의 사정권이 아니다** — 팬은 자기 base 토큰으로 로그인하므로
`sub` 가 실제 계정이다. 영향받는 것은 **assume 토큰으로 도달하는 표면**뿐이다.
그 구분을 하지 않으면 모집단이 9건으로 부풀어 결정이 틀린 크기로 내려간다.

## ⚠️ 라이브 3종(결재함 0 / 승인 실패)은 이 회차에 재현하지 않았다

erp 스택을 띄우지 않았다(메모리 한계로 도메인 슬라이스를 순차 교대). 대신 **기전의 양 끝**
— 토큰의 `sub` 실측 + 필터/규칙 코드 — 을 확인했다. 화면 단 재현은 AC-4 가 이미 요구하고
있으므로 결정 이후 그 단계에서 함께 잰다.

---

---

# ✅ 게이트 해제 — `ADR-MONO-060` **ACCEPTED — A** (2026-08-07)

AC-0 이 이 티켓을 **erp 결재함 기능 갭에서 플랫폼 전역 귀속 문제로** 재분류했다
(게이트웨이 6/6 이 `X-User-Id ← sub`). 그리고 이것은 설계 선호가 아니라
**`jwt-standard-claims.md` § `sub` 계약 위반**이다. 결정은
[`docs/adr/ADR-MONO-060-assumed-token-subject-identity.md`](../../docs/adr/ADR-MONO-060-assumed-token-subject-identity.md)
로 옮겼고, 소유자가 **A(계약 준수 복원 — `sub` = 계정 UUID)** 를 정확형으로 승인했다.

🔴 **이 티켓이 적은 A 안의 대가는 사실이 아니었다** — `AssumeTenantExchangeIntegrationTest`
의 유일한 `sub` 단언은 **base 토큰** 대상이고, assume 토큰에 대한 진술은 **주석**뿐이다.
A 는 테스트가 고정한 결정을 뒤집지 않는다. ⇒ 아래 **AC-3 의 "반대 단언을 함께 갱신" 이라는
문구는 틀렸다** — 갱신할 단언이 없고, 갱신 대상은 **주석**이다(그 주석이 `sub`/`act` 를
RFC 8693 의 반대로 설명하고 있으므로 그대로 두면 다음 세션이 또 속는다).

## A 가 지정하는 후속 (ADR § 결과 A 행)

`jwt-standard-claims.md` § `sub` 확인(**변경 불요일 수 있다 — 이미 그렇게 적혀 있다**) ·
auth-service 토큰 커스터마이저 · `AssumeTenantExchangeIntegrationTest` 주석/단언 갱신 ·
**6게이트웨이 회귀** · 그 결과로 **erp 인박스 2곳이 자동 해결**.

🔴 **erp 인박스는 2개다**(결재 + 알림). ADR § 결과 말미: *"하나만 고치면 형제가 낙오한다."*

## ⚠️ `act` 클레임은 **결정되지 않았다** — 이 티켓이 답해야 한다

소유자에게 `A` 와 `A + act 클레임` 이 나란히 제시됐고 **plain `A`** 가 선택됐다. 싣기로도
안 싣기로도 확정되지 않았고, A 본문 자신이 *"잃어도 되는 정보인지가 이 안의 실질 질문"*
이라고 미결로 명명한다. ⇒ **AC-6**. 조용히 빠뜨리는 것은 답이 아니다.

# Acceptance Criteria

- [x] **AC-0 (재현 + 모집단) — 완료 2026-08-07.** `sub = platform-console-web` 토큰 실측 ✅ ·
      모집단 분류 = erp **3** / fan **6**(사정권 밖) / finance·scm·wms **0건** ·
      🔴 신규: **erp 결재함이 2개**(approval + notification) · ⚠️ 라이브 3종은 미재현(사유 위).
      상세는 위 §
- [x] **AC-1 (결정) — 완료 2026-08-07.** `ADR-MONO-060` **ACCEPTED — A**(`sub` = 계정 UUID)
- [x] **AC-2 (구현) — 완료.** `AssumeTenantAuthenticationProvider` 가 이미 검증해 둔
      `oidcSubject`(= 계정 UUID, 배정 게이트의 키)를 `AssumeTenantAuthenticationToken` 에
      실어 보내고, `TenantClaimTokenCustomizer.customizeForAssumeTenant` 가 그것을
      `sub` 로 세운다. **fail-closed** — 계정 id 가 없으면 발급 거부(없으면 프레임워크
      기본값인 클라이언트 principal 로 조용히 되돌아가 결함을 그대로 재현한다).
      🔵 **읽는 쪽 변경 0** — 게이트웨이 6/6 · `ActorClaims` 는 이미 `sub` 를 읽는다.
      🔵 신원이 없던 게 아니라 **커스터마이저까지 갈 경로가 없었다**(token_exchange 의
      principal 은 클라이언트라 `alignSubToAccountId` 가 net-zero 분기로 빠졌다)
- [x] **AC-2b (계약 확인) — 완료. 행 자체는 변경 불요였다.** `sub` 행은 이미
      *"Account ID, globally unique, immutable **across all platforms**"* 다 — 위반한 건
      계약이 아니라 `token_exchange` 그랜트였다. 새 클레임·타입변경 **0**. 다만 그 사실을
      **적었다**: `sub` 행에 "카브아웃 없음, 의도적"(`email` 행은 assume-tenant 를 명시
      카브아웃하므로 대조군) + 변경 이력 1줄. 🔵 안 적으면 다음 세션이 이 질문을 다시 연다
- [x] **AC-3 (회귀 가드) — 완료.** 유닛 2건(`sub` = 주체 계정 / 계정 부재 → fail-closed,
      claims 를 건드리기 **전에** 거부하는 것까지 strict-stubbing 으로 고정) + IT 2건
      (happy path `sub` 단언 · 통합 시나리오에서 `assumed.sub == base.sub == account`).
      🔴 **틀린 지시를 정정했다** — 갱신할 "반대 단언" 은 없었다(유일한 `sub` 단언은 base
      토큰 대상). 갱신 대상은 **주석**이었고, 그 주석은 RFC 8693 을 거꾸로 읽고 있었다
      (RFC: `sub`=위임 주체 · `act`=행위자 — 구현이 둘을 뒤집어 놨다). 주석을 고쳤다
- [x] **AC-6 (`act` 미결 질문) — 답: `act` 를 싣지 않는다. 잃는 것이 없기 때문이다.**
      ADR § A 는 "acting client 를 잃는다" 를 이 안의 대가로 적었는데 **실측하니 사실이
      아니다**: assume 토큰의 `aud`(및 교환을 인증한 `client_id`)가 여전히
      `platform-console-web` 이다 ⇒ 행위자 정보는 이미 토큰에 남아 있고, `act` 는 그것을
      **중복**시키면서 계약 레지스트리 항목을 하나 늘린다
      (`scripts/check-jwt-claims-registry.sh` 가 minted claims 를 계약과 대조한다).
      🔴 **이 판정을 주석이 아니라 테스트로 고정했다** — `aud ⊇ platform-console-web` 를
      IT 에서 단언한다. 이 전제가 깨지면 그 단언이 먼저 빨개진다.
      🔵 **되돌리는 비용은 한 줄**이다(커스터마이저에 `act` 클레임 + 계약 행 1개).
      소유자가 "acting client 를 별도 클레임으로 보존하라" 고 하면 그때 추가한다
- [ ] ⚠️ **AC-4 (라이브) — 이번 회차 미수행. 그리고 수행했어도 통과할 수 없다(실측).**
      🔴 **막힌 것을 고치자 그 아래가 드러났다.** `sub` 수정으로 actorId 는 이제 **콘솔
      계정마다 하나**다(전에는 통틀어 하나). 그런데 **데모 운영자 계정이 1개뿐**이다 —
      `R__seed_demo_operator.sql` 의 `admin_operators` INSERT **1건**(실측). 자기결재
      금지(②)를 만족시킬 두 번째 신원이 여전히 없으므로 결재함은 여전히 0 이다.
      ⇒ 남은 것은 **토큰 결함이 아니라 데모 데이터 공백**이고, 운영자 계정이 둘이 되는
      순간 루프가 닫힌다. **후속 후보**: 데모 운영자 2번째 계정 시드(별건 티켓).
      ⚠️ 스택 미기동으로 BFF 원소 수 판정은 하지 않았다 — **안 한 것을 했다고 적지 않는다**
- [x] **AC-5 (데모 반영) — 완료.** `seed-erp.sh` 헤더: 사유 **3 → 2**, ③ 을 해소로 표시하고
      🔴 그 헤더가 인용하던 *"결함이 아니라 명시된 동작"* 근거가 **틀린 인용**이었음을 기록.
      `interview-demo-walkthrough.md` 한계 행: 🔴 → 🟡, 남은 사유는 **데모 데이터 1건**임을
      명시. 🔵 두 곳 모두 **눈에 안 보이던 절반**(필터 0건 도메인의 감사 기록 오염)이
      함께 고쳐졌다는 사실을 적었다 — 안 적으면 "erp 화면 하나 고친 일" 로 읽힌다

# Related Specs

- `platform/contracts/jwt-standard-claims.md` — `sub` = account UUID, immutable
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md`
- `projects/erp-platform/specs/services/approval-service/architecture.md` § Approver authorization

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` § Assume-Tenant Exchange
- `projects/erp-platform/specs/contracts/http/approval-api.md`

# Edge Cases

- **A 를 고르면 `X-User-Id` 가 전 도메인에서 의미가 바뀐다** — assume 토큰을 쓰는 모든
  게이트웨이의 헤더 매핑을 확인해야 한다
- 감사 로그에 이미 쌓인 `platform-console-web` 행은 **소급 교정 불가**다. 마이그레이션이
  아니라 경계 시점을 기록하는 편이 정직하다
- erp 결재는 위임(delegation)도 `actorId` 로 판정한다 — 같은 붕괴가 위임 경로에도 있다

# Failure Scenarios

- **erp 쪽만 고치고 닫음** → 결재함은 살아나는데 다른 도메인의 감사 기록은 여전히
  클라이언트 id 다. AC-0 의 전수가 그 범위를 정한다. 🔵 A 는 게이트웨이 6/6 이 이미
  `sub` 를 읽으므로 **한 번에** 돌아온다 — 이 실패 모드는 B 를 골랐을 때의 것이었다
- 🔴 ~~**테스트의 반대 단언을 보지 못한 채 A 구현** → 머지 즉시 iam IT 가 RED~~ —
  **이 시나리오는 실재하지 않는다.** ADR-060 이 실측했다: 그 단언은 base 토큰 대상이다.
  대신 진짜 위험은 **주석을 안 고치는 것**이다(RFC 8693 을 반대로 설명하는 주석이 남으면
  다음 세션이 오독을 상속한다)
- 🔴 **`act` 질문을 답하지 않고 닫는다** → acting client 정보가 **아무 기록 없이** 사라진다.
  그것이 이 ADR 이 방어하려는 실패 모드(무증상 귀속 오염)와 정확히 같은 모양이다. AC-6
- **"결함이니 그냥 고친다"** → 문서화된 결정을 뒤집는 것이므로 ADR 없이는 Hard Stop
  (`HARDSTOP-09`). 🔵 이제 ADR-060 이 ACCEPTED 이므로 **이 경로는 열렸다**

# Definition of Done

- [x] ADR ACCEPTED — `ADR-MONO-060` A (2026-08-07)
- [x] AC-0 · AC-1 · AC-2 · AC-2b · AC-3 · AC-5 · AC-6
- [ ] ⚠️ **AC-4 미충족** — 스택 미기동 + **데모 운영자 계정 1개**(실측)로 현재 데이터에선
      통과 불가. 남은 것은 토큰이 아니라 데모 데이터 공백 ⇒ 별건 후속
- [x] 로컬 유닛 GREEN (`auth-service` `TenantClaimTokenCustomizerTest` +
      `AssumeTenantAuthenticationProviderTest`, 66 tests). IT 는 Testcontainers ⇒ **CI 권위**
- [ ] Ready for review
- [ ] iam + erp 테스트 GREEN
- [ ] Ready for review
