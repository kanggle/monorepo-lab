# Task ID

TASK-BE-622

# Status

review (2026-10-04 UTC — AC-0~4 ✅ · AC-5 🟡 통합 시험은 CI 실측 대기)

# Title

내부 프로비저닝 상태 변경(`PATCH /internal/tenants/{t}/accounts/{id}/status`)도 풀 멤버에게는 **그 사이트 멤버십만** 바꾼다

# Owner

iam-platform

# Task Tags

- backend
- consumer-pool
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 상태 전이 · 기계 경로의 권한 범위. `TASK-BE-621` 의 설계를 그대로 잇는다. 🔴 데모 반영은 AMI 재굽기 뒤.

---

# Dependency Markers

- **선행**: `TASK-BE-621`(done) — 멤버십 `LOCKED`(V0033) · `SiteMembershipLockUseCase` · 응답 `scope`. `TASK-BE-619`(done) — 운영자 삭제 = `LEFT OPERATOR`.
- **후속**: 없음.

# 소유자 결정 (2026-10-04 UTC, 원문 그대로)

`TASK-BE-621` § 소유자 결정 필요 2 에 대한 답: **«별도 티켓으로 적용 (Recommended)»** — 셀러 정지처럼 사이트 백엔드가 내부 경로(프로비저닝 PATCH)로 계정 상태를 바꾸는 경로에도 «사이트 단위» 규칙을 적용한다. 다음 재굽기에 넣을지는 크기를 보고 정한다.

같은 날 결정 1 의 답: **«계정 전체 유지 (Recommended)»** — security-service 자동 잠금(`AUTO_DETECT`)은 이 티켓의 범위가 아니다(계정 전체 그대로).

# Goal

사이트 백엔드가 자기 테넌트 토큰으로 `PATCH /internal/tenants/{t}/accounts/{id}/status` 를 불러 **소비자 풀 멤버**의 상태를 바꿀 때, 계정 전체가 아니라 **사이트 `t` 의 멤버십**만 바뀐다 — 콘솔 사이트 운영자 경로(621)와 같은 규칙. 풀 멤버가 아닌 사이트 자기 계정(예: 셀러 운영 계정)은 지금처럼 계정이 바뀐다.

# Scope

## In Scope

- account-service 의 프로비저닝 상태 변경 처리: 대상이 풀 계정이고 경로 테넌트 `t` 가 사이트일 때
  - `LOCKED` → 멤버십 `ACTIVE → LOCKED`(621 의 전이 재사용)
  - `ACTIVE` → 멤버십 `LOCKED → ACTIVE`(계정 전체 잠금은 풀지 못한다 — 621 D-3 와 같음)
  - `DELETED` → 멤버십 `LEFT OPERATOR`(619 와 같음; 계정 삭제 아님)
  - 그 밖의 상태 값 → 계약이 정하는 거절(지금 계약 확인 후 결정 — 아래 AC-0)
- 응답에 `scope`(`SITE_MEMBERSHIP` | `ACCOUNT`) — 621 의 `/lock` 응답과 같은 어휘.
- 계약 문서 `account-internal-provisioning.md` 먼저.

## Out of Scope

- 자동 잠금(`AUTO_DETECT`) — 소유자 결정으로 계정 전체 유지.
- 본인 복구 해제(`USER_RECOVERY`).
- 콘솔 화면(목록의 멤버십 상태 표시) · 사이트 앱의 거절 문구 — `TASK-BE-621` § 후속.
- product-service 의 호출 코드 — 셀러 운영 계정은 풀 계정이 아니므로 동작이 바뀌지 않아야 한다(AC-3 대조군).

# Acceptance Criteria

- [x] **AC-0 (전수 먼저)** — 이 경로의 호출자 전부를 `main` 에서 다시 센다(621 의 표 #4 를 물려받지 않는다): 누가 부르나 · 어떤 상태 값을 보내나 · 대상이 풀 계정일 수 있나. 프로비저닝 계약 파일과 지금 허용 상태 값 목록을 적는다. 풀 계정을 겨누는 호출자가 이미 있으면 그 호출자의 의도(정말 사이트 범위인가)를 적고, 의도가 계정 전체라면 STOP — 소유자 결정.
- [x] **AC-1 (계약 먼저)** — 계약 문서가 코드보다 먼저(같은 PR, 앞 커밋) 풀 멤버의 사이트 범위 처리 · 응답 `scope` 를 적는다.
- [x] **AC-2 (풀 멤버)** — 사이트 `ecommerce` 백엔드가 스토어·팬 둘 다 멤버인 풀 계정에 `LOCKED` → 스토어 멤버십 `LOCKED` · 팬 `ACTIVE` · 계정 `ACTIVE` · `account.locked` 0. `ACTIVE` → 멤버십 해제. `DELETED` → 스토어 멤버십 `LEFT OPERATOR` · 계정·팬 그대로.
- [x] **AC-3 (대조군 — 사이트 자기 계정)** — 셀러 운영 계정(풀 아님)에 같은 호출 → 지금처럼 **계정** 상태가 바뀐다(`scope = ACCOUNT`) — product-service 셀러 정지·폐점 흐름이 그대로.
- [x] **AC-4 (넘지 못함)** — 경로 테넌트 `t` 의 멤버십이 없는 풀 계정에 대한 호출은 다른 사이트의 멤버십이나 계정을 바꾸지 않는다(계약이 정하는 오류).
- [ ] **AC-5 (시험 · bite)** — 단위 + 슬라이스 + 통합(Testcontainers: 풀 멤버 사이트 범위 · 셀러 운영 계정 대조군). 사이트 갈래를 끄면 정확히 그 칸이 빨개지는 bite 를 기록. 로컬에서 못 돈 통합은 ⚪ 로 적고 CI 로그에서 실제 실행을 확인해 닫는다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 (621 이 고친 «사이트 잠금 vs 계정 잠금»)
- `projects/iam-platform/specs/services/account-service/architecture.md`
- `projects/iam-platform/specs/services/account-service/data-model.md` § consumer_site_memberships

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (상태 변경 PATCH — AC-0 에서 해당 절과 허용 상태 값을 확인)
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (621 의 `scope` 어휘)

# Edge Cases

1. 풀 멤버가 사이트 `t` 에서 이미 `LEFT` — `LOCKED`/`ACTIVE` 요청은 무변경(되살리지 않는다), `DELETED` 는 멱등.
2. 계정 자체가 이미 `LOCKED`(플랫폼 관리자·자동) — 사이트 범위 `ACTIVE` 는 계정 잠금을 풀지 않는다.
3. 같은 사람이 사이트 `t` 의 자기 계정(풀 아님)도 따로 가진 경우 — 대상 id 로 판별(풀 계정 id 인가).

# Failure Scenarios

1. 셀러 운영 계정까지 «풀 멤버» 로 오판해 셀러 정지가 계정을 안 잠근다 — AC-3 대조군.
2. 사이트 백엔드 한 곳의 호출이 풀 계정 전체를 잠가 그 사람의 다른 사이트까지 막는다(지금의 결함 — 이 티켓이 없애는 것).
3. 통합 시험이 로컬에서 skip 됐는데 «통과» 로 닫는다 — CI 로그의 실행 줄로만 닫는다.

---

# AC-0 — 호출자 전수 (2026-10-04 UTC, 브랜치 `feat/be-622-provisioning-site-scope` = `origin/main` `c32e56e7b` 에서 다시 셈)

621 의 표 #4 를 물려받지 않고 다시 셌다. 찾은 방법(Grep 도구, 전부 이 트리):

| # | 무엇을 grep | 범위 | 결과 |
|---|---|---|---|
| g1 | `internal/tenants/` | `projects/**/src/main/**` | 경로를 **만드는** 곳(`.uri(…)` · 문자열 결합) 중 `…/accounts/{id}/status` 는 product-service 1곳뿐. 나머지는 다른 하위 경로(`entitled-domains` · `consumer-members` · `roles` · `identity` · `site-roles:*` · 테넌트 GET/PATCH) 또는 javadoc · 서버 측 매핑 |
| g2 | `"/status"` · `+ "/status` · `accounts/{x}/status` | `projects/**/src/main/**` | 호출은 product-service 1곳. auth-service 의 `/internal/accounts/{id}/status`·`status-with-tenant` 는 **다른 경로**(GET, 테넌트 없음) |
| g3 | `internal/tenants` | `projects/**` 의 `*.ts`·`*.tsx`·`*.js`·`*.mjs`·`*.sh`·`*.ps1`·`*.py`·`*.sql`·`*.http` | 0 — 프런트 BFF · 인프라 스크립트 · 시드 어디에도 이 호출 없음(마이그레이션 주석 1줄뿐) |
| g4 | `internal/tenants/[^"' ]*/status` | 저장소 전체의 `infra/**` · `scripts/**` · yml/json/sh/ps1/py/kt | 0 |
| g5 | 테스트 코드의 호출 | `**/src/test/**` · `projects/iam-platform/tests/e2e` | account-service 자기 시험(슬라이스 · `TenantProvisioningIntegrationTest` · `PoolMemberSiteLookupTest`)과 product-service 어댑터 시험뿐. e2e 는 `/internal/accounts/{id}/status`(GET) 만 |
| g6 | 이 경로를 부를 **자격**이 있는 워크로드 | auth-service `WorkloadTenantCatalog.ASSUMABLE` | `product-service-client`(`ecommerce`·`demo-corp`) · `artist-service-client`(`ecommerce`). artist-service 의 하류 호출은 `GET {store}/internal/sellers/{id}`(이커머스 게이트웨이) 뿐 — account-service 상태 PATCH 없음 |

## 호출자 표

| 호출자 | 코드 | 보내는 `status` | 대상 | 풀 계정일 수 있나 | 의도 |
|---|---|---|---|---|---|
| ecommerce **product-service** — 셀러 SUSPEND (`RegisterSellerService` → `SellerAccountProvisioner#lockAccount`) | `AccountServiceSellerProvisioner#changeStatusToLocked` (`.uri("/internal/tenants/{tenantId}/accounts/{accountId}/status")`, 테넌트 토큰 · `X-Tenant-Id` = 경로) | `LOCKED` 만 (`STATUS_DEACTIVATED = "LOCKED"`), `operatorId = "product-service"` | `seller.getAccountId()` — 오직 `Seller#markProvisioned` 가 채운다 = 같은 어댑터의 `POST /internal/tenants/{t}/accounts` 가 만든 **셀러 운영 계정**(`seller+<t>+<sellerId>@marketplace.local`, 무작위 비밀번호) | **아니다.** 그 계정은 경로 테넌트(`ecommerce`)에 사는 **사이트 자기 계정**이다. `POST /internal/tenants/{t}/accounts` 는 풀에 계정을 만들지 않는다(`ConsumerAccountPool#signupGoesToPool` 은 공개 가입만; 내부 생성은 풀 이메일이면 오히려 409 — `refuseIfEmailHasPoolAccount`). 셀러를 풀로 옮기는 `TASK-MONO-745` 는 **구현 없이 종결**(전제 거짓 — 셀러 계정은 기계 계정). 사람 셀러 멤버(풀 계정)는 `site-roles:grant/revoke`(MONO-752)로만 다뤄지고 이 경로로 잠기지 않는다 | 셀러 정지 = 그 **운영 계정** 잠금. 이 티켓 뒤에도 `scope = ACCOUNT` 로 그대로(AC-3 대조군) |
| 같은 서비스 — 셀러 CLOSE (`SellerAccountProvisioner#deactivateAccount`) | 같은 메서드 | `LOCKED` | 같은 셀러 운영 계정 | 아니다 | 같다 |

**풀 계정을 겨누는 호출자: 0.** 그러므로 «의도가 계정 전체인 기존 호출자» 도 없다 → STOP 조건 미충족, 진행한다.
이 티켓의 사이트 갈래는 **지금은 어떤 운영 호출도 타지 않는다** — 앞으로 사이트 백엔드가 풀 멤버를 이 경로로 부를 때의 규칙을 정해 두는 것이다(소유자 결정 «별도 티켓으로 적용»).

## 계약 파일과 지금 허용 값 (코드가 이긴다 — 둘이 달랐다)

- 계약: `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` § `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status`.
- 계약이 적은 것: `status` = `ACTIVE`, `LOCKED`, `DELETED` · `reason` **Required**(«유효한 `StatusChangeReason`») · `operatorId` ≤ 36.
- 코드(`ProvisionStatusChangeRequest` · `TenantProvisioningController#changeStatus` · `ProvisionStatusChangeUseCase`):
  - `status` = `@NotBlank` 문자열 → `AccountStatus.valueOf` — 열거형 아닌 값은 `IllegalArgumentException` → **400 `VALIDATION_ERROR`**(`CommonGlobalExceptionHandler`). `DORMANT` 는 파싱되지만 사유 `OPERATOR_PROVISIONING_STATUS_CHANGE` 로는 `AccountStatusMachine` 이 허용하지 않아 **409 `STATE_TRANSITION_INVALID`**. 실제로 성공하는 값 = `ACTIVE`·`LOCKED`·`DELETED`(계약과 같다) + 같은 상태 멱등.
  - 🔴 **`reason` 필드는 요청 DTO 에 없다** — 보내도 무시되고(알 수 없는 JSON 필드), 기록되는 사유는 늘 `OPERATOR_PROVISIONING_STATUS_CHANGE` 다. product-service 계약(`product-to-account.md` § 3 · § 4)도 «EP 가 사유를 고정한다» 고 적고 `reason` 을 보내지 않는다. → 계약의 «`reason` Required» 는 틀린 문장이다. AC-1 커밋에서 실제 동작으로 고친다(동작 변경 0).
- 지금 동작(이 티켓 전): 경로 테넌트 `t` 가 사이트이고 대상이 그 사이트의 풀 멤버(`ACTIVE ∨ LOCKED`, `SiteAccountLookup`)면 **풀 계정 하나**의 상태가 바뀐다 — 그 사람의 모든 소비자 사이트에서(이벤트 · 이력 행 `consumer-pool`). `PoolMemberSiteLookupTest#provisionStatusChange_poolMember_historyOnPool` 이 그 동작을 핀으로 박고 있다 → 이 티켓이 뒤집는 칸.

# 설계 (621 · 619 를 그대로 잇는다 — 스키마 변경 없음)

`V0033` 의 멤버십 `LOCKED`(잠금 기록 두 컬럼)와 `V0032` 의 `LEFT`(`left_by`)로 충분하다 — **새 마이그레이션 없음**.

| 경로 테넌트 `t` · 대상 | `LOCKED` | `ACTIVE` | `DELETED` | 그 밖(`DORMANT`) | 응답 `scope` |
|---|---|---|---|---|---|
| 사이트 · **풀 멤버**(그 사이트 멤버십 `ACTIVE ∨ LOCKED`) | 멤버십 `ACTIVE → LOCKED`(`SiteMembershipLockUseCase`, 잠금 기록 = 호출자) · `LOCKED` 면 멱등 | 멤버십 `LOCKED → ACTIVE` · `ACTIVE` 면 멱등. **계정 전체 잠금은 풀지 않는다**(621 D-3) | 멤버십 → `LEFT`(`OPERATOR`) + 그 사이트 `consumer_site_roles` 삭제(`LeaveConsumerSiteUseCase`, 619) | 409 `STATE_TRANSITION_INVALID` | `SITE_MEMBERSHIP` — `previousStatus`/`currentStatus` = **멤버십** 상태 |
| 사이트 · 풀 계정이고 그 사이트 멤버십 `LEFT` | 404 `ACCOUNT_NOT_FOUND`(되살리지 않는다 — § 5 «LEFT 는 멤버 아님», 621 `/lock` 과 같다) | 404 | **200 멱등** — `LEFT → LEFT`(본인 탈퇴였으면 `OPERATOR` 로 다시 기록 — 619 «내보냄이 남는다») | 404 | `SITE_MEMBERSHIP` |
| 사이트 · 그 사이트 멤버십 없는 풀 계정 | 404 | 404 | 404 | 404 | — (AC-4: 다른 사이트 · 계정 무변경) |
| 사이트 · **사이트 자기 계정**(셀러 운영 계정 등) | 계정 상태 기계 그대로 | 그대로 | 그대로 | 409 | `ACCOUNT` (AC-3) |
| `consumer-pool` 자신 | 풀 계정 그대로(정확 조회) | 그대로 | 그대로 | 409 | `ACCOUNT` |

## 구현자 기본값 (소유자 결정 밖 — 기록)

| # | 선택 | 값 | 이유 |
|---|---|---|---|
| D-1 | 어디서 가르나 | `ProvisionStatusChangeUseCase#execute` 안에서 «찾은 계정이 `consumer-pool` ∧ 경로 테넌트가 `consumer-pool` 아님» 이면 사이트 갈래 | 621 `changeStatusAsTenantOperator` · 619 `deleteAccountAsTenantOperator` 와 같은 술어. 이 엔드포인트는 늘 경로가 테넌트를 말하므로 «테넌트를 말하지 않는 호출자»(플랫폼 관리자 `'*'`) 갈래가 없다 — `'*'` 는 테넌트 슬러그 패턴이 아니라 이 경로로 올 수 없다 |
| D-2 | 사이트 범위의 감사 | `account_status_history` 행 **안 씀** · 이벤트 **안 냄** · 멤버십 행에 행위자(`locked_by_actor_id` / `left_by_actor_id`) = `operatorId`, 없으면 경로 테넌트 | 621 D-4·D-5 · 619 D-4 와 같다 — 계정 상태가 바뀌지 않았다. 이 경로엔 admin-service `admin_actions` 가 없으므로 멤버십 행이 유일한 기록이다(계약에 적음) |
| D-3 | `LEFT` 멤버에 `DELETED` | 200 멱등 — 풀 계정 + 그 사이트 `LEFT` 행이 있을 때만, 그 밖은 404 | 티켓 Edge Case 1 «`DELETED` 는 멱등». 기계 호출자는 fail-soft 재시도를 한다(product-service 처럼) — 두 번째 호출이 404 면 «이미 지워졌다» 와 «없다» 를 가를 수 없다. 조회는 여전히 경로 사이트의 멤버십 행으로만(테넌트 없는 조회 아님) |
| D-4 | `LEFT` 멤버에 `LOCKED`/`ACTIVE` | 404 — 무변경 | 621 `/lock`·`/unlock` 과 같은 답. «되살리지 않는다» 를 200 으로 답하면 «그 사이트에서 잠겼다/풀렸다» 로 읽힌다 |
| D-5 | 응답 | `scope`(`ACCOUNT` \| `SITE_MEMBERSHIP`) 한 필드 추가. 사이트는 이미 `tenantId`(경로) 로 실려 있어 `siteTenantId` 는 더하지 않는다 | 621 `/lock` 과 같은 어휘. 없는 필드(옛 account-service)는 `ACCOUNT` 로 읽는다 |

---

# 구현 기록 (2026-10-04 UTC)

> 분석=Opus 5.5 / 구현=Opus 5.5. 한 PR, 브랜치 `feat/be-622-provisioning-site-scope`. 커밋 1 = 티켓(AC-0 전수 · 설계) + 계약(코드 0) · 커밋 2 = 코드 + 시험 + 티켓 review.

## 바꾼 것

**계약 (커밋 1 — 코드보다 먼저)**: `account-internal-provisioning.md` § PATCH status(응답 `scope` · § Consumer-pool member 표 · `reason` 행 정정 · Outbox 표) · `multi-tenancy.md` § 5(«사이트 잠금 vs 계정 잠금» 표에 «사이트 백엔드» 행 · «쓰기의 범위» 문장 · 621 의 «아직 계정 전체다» 줄 교체) · account-service `data-model.md`(`locked_by_actor_id` · `left_by_actor_id` 에 622 행위자) · ecommerce `product-to-account.md` § 4(영향 없음 메모 — 셀러 운영 계정은 사이트 자기 계정).

**account-service (커밋 2)** — 스키마 변경 없음(V0032 · V0033 재사용, 새 마이그레이션 0).
- `ProvisionStatusChangeUseCase` — «찾은 계정 = `consumer-pool` ∧ 경로 ≠ `consumer-pool`» 이면 사이트 갈래: `LOCKED`/`ACTIVE`/그 밖 → `SiteMembershipLockUseCase`(621, 사유 `OPERATOR_PROVISIONING_STATUS_CHANGE`), `DELETED` → `LeaveConsumerSiteUseCase`(619, `OPERATOR`). 조회 실패 + `DELETED` + 풀 계정 + 그 사이트 `LEFT` 행 → 200 멱등(D-3). 계정 갈래는 그대로.
- `ProvisionedStatusChangeResult` / `ProvisionedStatusChangeResponse` + `scope`(5-인자 생성자 유지 = `ACCOUNT`, `siteMembership(…)` 팩토리).
- 컨트롤러 무변경.

## 시험

| 새/바뀐 시험 | 무엇을 문다 |
|---|---|
| `ProvisionStatusChangeUseCaseTest` 12 (신설 — 621 `SiteMembershipLockUseCase` · 619 `LeaveConsumerSiteUseCase` 는 **실물**, 저장소만 mock) | AC-2 `LOCKED`·`ACTIVE`·`DELETED`(멤버십만 · 계정 저장/이력/이벤트 0) · operatorId 없음 → 행위자 = 경로 테넌트 · Edge 2(계정 전체 잠금은 안 풀림) · `DORMANT` 409 · **AC-3 대조군**(셀러 운영 계정 → 계정 LOCKED · 이력 행 `ecommerce` · `account.locked` · 멤버십 0) · AC-4 비멤버 `LOCKED`/`DELETED` 404 · Edge 1 `LEFT` 에 `DELETED` 200 멱등(OPERATOR 재기록) / `LOCKED` 404 · 플래그 OFF |
| `PoolMemberSiteLookupTest` (16, 1칸 뒤집음) | `provisionStatusChange_poolMember_historyOnPool`(경로 `ecommerce` → 계정 전체 — **이 티켓이 없앤 동작을 핀으로 박던 칸**)을 `provisionStatusChange_poolPath_historyOnPool`(경로 `consumer-pool` → 계정 전체 · 이력 행 `consumer-pool` · 사이트 갈래 0)로 |
| `TenantProvisioningControllerSliceTest` +3 (24) | 응답 `scope=ACCOUNT` · `SITE_MEMBERSHIP` 직렬화 · `reason` 보내도 200(계약 정정의 근거) · 열거형 아닌 `status` → 400 `VALIDATION_ERROR` |
| `PoolMemberSiteSurfacesIntegrationTest` (1칸 뒤집음) | `statusChange_nonMemberRefused_memberLocksTheOneAccount` → `…memberLocksTheSiteOnly`: 팬(비멤버) 404 그대로 · 스토어 200 → `scope=SITE_MEMBERSHIP` · 계정 ACTIVE · 스토어 멤버십 LOCKED · 이력 행 0 |
| `ProvisionStatusSiteScopeIntegrationTest` 3 (신설, `AbstractConsumerPoolIntegrationTest` 하위 — 공유 컨텍스트) | **실 MySQL**: AC-2 스토어 백엔드 `LOCKED` → 스토어만 · 팬 ACTIVE · `ACTIVE` 해제 · `DELETED` → `LEFT OPERATOR`(행위자) · 재시도 `DELETED` 멱등 · 이후 `LOCKED` 404 · 계정/이력/이벤트 0 / **AC-3** 셀러 운영 계정(`POST /internal/tenants/ecommerce/accounts` 로 만든 사이트 계정) `LOCKED` → 계정 LOCKED · `scope=ACCOUNT` · `account.locked` 1 · 이력 1 / AC-4 팬 백엔드 → 스토어만 멤버인 풀 계정 404 ×2 · 무변경 |

## 게이트 (각각 단독 · `cmd > log 2>&1; echo rc=$?`)

| 게이트 | rc | 비고 |
|---|---|---|
| `:projects:iam-platform:apps:account-service:test` | 0 | test-results: `ProvisionStatusChangeUseCaseTest` 12/0 실패 · `PoolMemberSiteLookupTest` 16/0 · `TenantProvisioningControllerSliceTest` 24/0 |
| `:projects:iam-platform:apps:account-service:check` | 0 | `check` = `test`(+ 컴파일). 새 IT 클래스는 컴파일됨(`build/classes/java/test/…/ProvisionStatusSiteScopeIntegrationTest.class`) |
| `@Tag("integration")` (`ProvisionStatusSiteScopeIntegrationTest` 3 · 뒤집은 `PoolMemberSiteSurfacesIntegrationTest` 칸) | ⚪ 로컬 미실행 | Docker 없음 — **CI 첫 실측**. 통과했다고 적지 않는다 |
| product-service | — | 코드 변경 없음(계약 메모만) → 돌리지 않음 |

## bite (되돌린 뒤 `test` 재실행 rc=0 · `false &&` / `true ||` 잔존 grep 0)

같은 5 클래스(`ProvisionStatusChangeUseCaseTest` · `PoolMemberSiteLookupTest` · `TenantProvisioningControllerSliceTest` · `SiteMembershipLockUseCaseTest` · `LeaveConsumerSiteUseCaseTest`, 66칸)로.

| 끈 것 | 결과 |
|---|---|
| (A) 사이트 갈래 — `if (false && account.getTenantId().isConsumerPool() && …)` | 66 중 **정확히 5** — AC-2 `LOCKED` · `ACTIVE` · `DELETED` · Edge 2 · operatorId 없음. 예측과 같다. 🔵 `DORMANT` 409 칸은 초록 그대로 — 계정 상태 기계도 그 사유로 `→DORMANT` 를 409 하므로 갈래를 끄든 안 끄든 같은 답(이 칸은 갈래가 아니라 «409 를 지킨다» 를 문다). AC-3 대조군 · AC-4 · Edge 1 은 갈래 밖이라 초록 — 맞다 |
| (B) `LEFT` 멤버 `DELETED` 멱등 — `if (true \|\| …)` | 66 중 2 — Edge 1 `DELETED` 멱등(동작) + AC-4 `DELETED` 비멤버(🔵 **동작이 아니라 STRICT_STUBS «unnecessary stubbing»** — 그 칸이 스텁한 멤버십 읽기가 쓰이지 않게 됐다; 판정 404 자체는 그대로). 동작으로 무는 칸은 1 |

## AC

| AC | 판정 | 증거 |
|---|---|---|
| AC-0 | ✅ | 위 § AC-0 — 호출자 1(product-service, `LOCKED`, 셀러 운영 계정 = 사이트 자기 계정). 풀 계정을 겨누는 호출자 0 → STOP 아님. 계약 drift(`reason`) 기록 |
| AC-1 | ✅ | 커밋 1 = 티켓 + 계약(코드 0), 코드는 커밋 2 |
| AC-2 | ✅ 단위·슬라이스 / ⚪ 통합은 CI | `ProvisionStatusChangeUseCaseTest` AC-2 칸 셋 · IT `storeBackend_poolMember_changesTheStoreMembershipOnly` |
| AC-3 | ✅ 단위 / ⚪ 통합은 CI | `locked_sellerOperatorAccount_locksTheAccount` · IT `storeBackend_sellerOperatorAccount_locksTheAccount`. product-service 코드 무변경 |
| AC-4 | ✅ 단위 / ⚪ 통합은 CI | 비멤버 `LOCKED`/`DELETED` 404 · IT `fanBackend_nonMemberPoolAccount_notFound`(스토어 멤버십·계정 무변경) |
| AC-5 | 🟡 | 단위·슬라이스 ✅ · bite (A) 5칸 · (B) 1칸(+스텁 1) ✅ · 통합 ⚪ — **CI `Integration (iam …)` 잡에서 `ProvisionStatusSiteScopeIntegrationTest` 3칸과 `PoolMemberSiteSurfacesIntegrationTest#statusChange_nonMemberRefused_memberLocksTheSiteOnly` 가 실제로 돈 줄을 확인해야 닫힌다** |

## 배포 · 남은 것

- account-service 단독(다른 서비스 코드 변경 없음, 마이그레이션 없음). 응답 필드 추가는 하위 호환(유일한 호출자는 본문을 읽지 않는다).
- 🔵 데모 반영은 다음 AMI 재굽기 뒤(백엔드는 구워진 클론에서 돈다). 지금 이 갈래를 타는 운영 호출은 없다(AC-0) — 재굽기에 넣는 급함은 없다.
- 🔵 관찰(고치지 않음): 계정 갈래의 `DELETED`(사이트 자기 계정)는 `deleted_at` 은 찍지만(`Account#changeStatus`) `account.deleted` 이벤트는 내지 않는다 — `account.status.changed` 만(`AccountStatusEvents`). `/delete` 경로의 `applyDelete`(유예 기간 + `account.deleted`)와 다르다. 이 티켓 밖 — 지금 호출자는 `DELETED` 를 보내지 않는다.
