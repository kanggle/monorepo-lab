# Task ID

TASK-MONO-515

# Title

콘솔 운영자는 ERP 결재함을 **영원히** 쓸 수 없다 — assume 토큰의 `sub` 가 클라이언트 id 라 모든 운영자가 한 사람이고, 자기결재 금지가 그것을 막는다

# Status

ready

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

# Acceptance Criteria

- [ ] **AC-0 (재현 + 모집단)** — 위 세 실측을 다시 하고, **`sub` 를 사람 식별자로 쓰는
      지점**을 5개 도메인 전수로 센다. 0건이면 "0건" 이라고 적는다
- [ ] **AC-1 (결정)** — ADR 작성 + 사용자 ACCEPT. 🔴 에이전트가 스스로 ACCEPT 할 수 없다
- [ ] **AC-2 (구현)** — 결정된 방향 구현
- [ ] **AC-3 (회귀 가드)** — 상신자와 승인자가 **서로 다른 actorId** 로 기록되는지
      단언한다. 🔴 기존 `AssumeTenantExchangeIntegrationTest` 의 반대 단언을 **함께
      갱신**해야 한다(A 를 고를 경우) — 갱신 없이 머지하면 그 스위트가 RED 다
- [ ] **AC-4 (라이브)** — 콘솔에서 상신 → 다른 신원으로 로그인 → 결재함에서 승인이
      실제로 성공한다. BFF 원소 수로 판정한다(콘솔은 클라이언트 렌더 — SSR HTML grep 은
      깨진 탐지기)
- [ ] **AC-5 (데모 반영)** — `infra/demo/seed/seed-erp.sh` 헤더의 "결재함은 채울 수 없다"
      설명과 `docs/guides/interview-demo-walkthrough.md` 의 한계 표를 갱신한다

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
  클라이언트 id 다. AC-0 의 전수가 그 범위를 정한다
- **테스트의 반대 단언을 보지 못한 채 A 구현** → 머지 즉시 iam IT 가 RED. AC-3 이 막는다
- **"결함이니 그냥 고친다"** → 문서화된 결정을 뒤집는 것이므로 ADR 없이는 Hard Stop
  (`HARDSTOP-09`)

# Definition of Done

- [ ] ADR ACCEPTED + AC-0~AC-5 충족
- [ ] iam + erp 테스트 GREEN
- [ ] Ready for review
