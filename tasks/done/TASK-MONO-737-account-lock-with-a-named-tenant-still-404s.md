# Task ID

TASK-MONO-737

# Status

done

# Title

계정 잠금 호출에 **구체 테넌트**를 실어도 여전히 404다 — account-service 에 실제로 도착하는 `X-Tenant-Id` 값부터 확인한다

# Owner

monorepo (iam-platform · ecommerce-microservices-platform — 호출처가 두 프로젝트에 걸친다)

# Task Tags

- admin-service
- account-service
- product-service
- multi-tenant
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 원인이 「호출자가 잘못된 값을 만든다」인지 「중간에서 값이 덮인다」인지가 먼저 갈려야 하고, 확인에는 실제 내부 워크로드 자격증명 경로 재현이 필요하다(설계 판단 + 배선 양쪽).

---

# Goal

`TASK-MONO-735`(#4048, 2026-09-26 UTC 병합)가 account-service `/lock`·`/unlock`·`/delete` 를 「헤더 없음/공백/`*` → 계정 행에서 테넌트를 푼다(b) · 구체 테넌트 헤더 → 그 테넌트로 한정(교차 테넌트 404 유지)」으로 고쳤다. 17차 AMI 창(2026-09-27 UTC, `a6f0ab791`, `TASK-MONO-672` § 2026-09-27 17차 창 수확)에서 그 AC-3 런북을 실행한 결과, **헤더를 안 싣는 호출은 성공하고 구체 테넌트를 싣는 호출은 여전히 404**다:

| 호출 경로 | 싣는 헤더(설계상) | 대상 계정 테넌트 | 결과 |
|---|---|---|---|
| security-service 자동 잠금(헤더 없음, (b) 경로) | 없음 | ecommerce · fan-platform | 🟢 **LOCKED(~2s)** — 자동 잠금 · 교차 테넌트 세션 재현 둘 다, 대조군(fan-platform)도 LOCKED |
| admin-service 콘솔 잠금(SUPER_ADMIN `demo@demo.com`, 콘솔 테넌트 전환 `ecommerce`, 대상 계정 `78740d21-…`) | `X-Tenant-Id: ecommerce`(구체) | ecommerce | 🔴 **404 「대상 계정을 찾을 수 없습니다」** ×3(07:02:15·07:02:39·07:05:12Z) · admin-service 로그 `account-service returned 404 NOT_FOUND on /internal/accounts/…/lock` · 계정 `ecommerce ACTIVE` 그대로 · `admin_actions` FAILURE |
| product-service 셀러 정지(`AccountServiceSellerProvisioner.lockAccount(tenantId, accountId)`) | `X-Tenant-Id: tenantId`(구체, TASK-MONO-735 § AC-0 표) | ecommerce | 🔴 셀러 `SUSPENDED` 로 전이했지만 계정은 **ACTIVE** 그대로 · product-service 로그 `seller account lock failed (fail-soft) tenant=ecommerce account=… : 404 Not Found` |

패턴은 뚜렷하다 — **헤더가 없는 호출(security-service)은 성공하고, 테넌트를 명시로 싣는 호출(admin-service · product-service, 둘 다 대상은 `ecommerce`)은 같은 모양으로 404**다. account-service 코드(`AccountLockController.namesTenant(header)` → 구체 테넌트면 `changeStatus(cmd, TenantId)` → `AccountRepositoryImpl.findByTenantIdAndId`)는 헤더가 `ecommerce`로 오면 `ecommerce` 테넌트에서 그 계정을 찾아야 하므로, **실제로 도착하는 헤더 값이 `ecommerce`가 아닐 가능성이 높다** — 어느 호출 경로의 인터셉터/테넌트 전파 계층이 값을 덮거나 중복 지정하고 있을 수 있다. 🔴 **단, 확인되지 않은 추정이다.** 라이브에서 확정하려면 실제 내부 워크로드 자격증명으로 admin-service→account-service · product-service→account-service 호출을 재현해야 하고, 이번 창에서는 하지 않았다(운영자 작업 도중의 관찰만).

`TASK-MONO-735`의 IT 스위트(`AccountMutationTenantConfinementIntegrationTest`)에는 **「올바른 구체 테넌트 헤더 → 200」 셀이 없다** — 있는 것은 헤더 없음/`*` → 200과 잘못된 테넌트 헤더 → 404(대조군)뿐이다. 그래서 CI가 이 결함을 잡지 못하고 병합됐다.

# Scope

## In Scope

- **AC-0** — 실제로 account-service 에 도착하는 `X-Tenant-Id` 값을 확인한다. 후보 방법: account-service `AccountLockController`에 요청-스코프 로그(수신 헤더 원문) 추가 · 또는 admin-service/product-service 의 실제 client 빈(테넌트 스탬프 로직 포함)으로 account-service 를 부르는 IT 작성(현재 IT 는 헤더를 직접 구성해 호출자의 스탬프 로직 자체를 통과하지 않는다 — `feedback_assert_injection_before_reading_bite` 축).
- **AC-1** — AC-0 이 찾은 원인을 고친다(호출자 쪽 값 생성 로직 또는 중간 전파 계층). 계약 변경이 필요하면 먼저.
- **AC-1 부속** — admin-service·product-service 각각에 「올바른 구체 테넌트 헤더 → 200」 IT 셀을 추가한다(TASK-MONO-735 가 빠뜨린 셀).

## Out of Scope

- 잠금 해제 경로 자체의 설계(`TASK-BE-608` AC-3 소유자 결정 대기).
- security-service 경로 재설계 — (b) 경로로 이미 라이브에서 동작한다(변경 없음).
- account-service 의 `findByIdResolvingTenant`/`namesTenant` 판정 로직 자체 — 그 로직은 대조군(교차 테넌트 404 유지, 자동 잠금 LOCKED)으로 라이브에서 옳다는 것이 확인됐다. 의심 지점은 **호출자가 만드는/전파되는 값**이다.

# Acceptance Criteria

- [x] **AC-0** — 실제로 도착하는 `X-Tenant-Id` 값을 실측한다(로그 또는 실제 client 빈 재현 IT). 값이 `ecommerce`가 아니면 무엇으로 도착하는지, 그리고 그 값이 어디서 만들어지는지(호출자 코드 vs 공통 인터셉터 vs 테넌트 전파 필터)까지 표로 남긴다. → § AC-0 결과
- [x] **AC-1** — 원인을 고친다. admin-service·product-service 각각에 「올바른 구체 테넌트 헤더 → 200」 단위/IT 셀을 추가한다(양쪽 다 — 하나만 고치면 나머지가 남는다). bite로 되돌려 실패 확인. → § AC-1 결과
- [ ] **AC-2** — 🔴 **결과로 판정**(다음 데모 창): 콘솔에서 `demo@demo.com`(SUPER_ADMIN, 테넌트 전환 `ecommerce`)로 `ecommerce` 계정 잠금 → **200** · `accounts.status=LOCKED`. 셀러 정지(일회용 셀러) → **`accounts.status=LOCKED`**. 이 결과로 `TASK-MONO-735` AC-3 잔여 스텝 2·4, `TASK-MONO-726` 항목 14 ②를 닫는다. ⏳ **창 대기** — 🔴 **재굽기 뒤에만 잴 수 있다**(게이트웨이·admin-service·product-service 이미지가 바뀐다). § AC-2 창 런북 참조.

# Related Specs

- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md`
- `projects/ecommerce-microservices-platform/specs/contracts/http/internal/product-to-account.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` § 격리 회귀 방지
- `tasks/review/TASK-MONO-735-account-lock-calls-drop-the-accounts-tenant.md`(원인 소스 · AC-3 런북)
- `tasks/review/TASK-MONO-726-two-internal-callers-717-left-without-a-home.md`(항목 14 ②)

# Related Contracts

- account-service `POST /internal/accounts/{id}/lock|unlock|delete` 의 `X-Tenant-Id` 해석 — `TASK-MONO-735`가 이미 additive 로 정의했다. 이 티켓은 계약을 바꾸지 않고 **배선 결함**만 고친다(원인이 계약 위반이면 재검토).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 헤더가 실제로 다른 테넌트 값(예: 운영자 홈 테넌트 · 워크로드 client 의 기본 테넌트)으로 도착한다 | 그 값을 만드는 계층을 고친다 — account-service 판정 로직은 손대지 않는다 |
| 헤더가 아예 도착하지 않는다(빈 문자열이 `X-Tenant-Id: ecommerce`로 오인됐을 뿐) | `namesTenant` 판정 자체가 구체값으로 오판하고 있는지 재확인 — 이 경우 (b) 경로로 떨어져야 정상 |
| 같은 이메일이 테넌트마다 다른 계정으로 존재 | 잠금은 그 계정 id 하나만 — 원인 수정이 다른 테넌트 계정에 영향을 주지 않는지 확인 |

# Failure Scenarios

1. **단위 테스트만으로 닫는다** → `TASK-MONO-735`도 단위 테스트 전부 초록이었지만 라이브에서 404였다. 결과 상태(AC-2)로만 판정한다.
2. **헤더 부재 셀만 다시 추가하고 「올바른 구체 테넌트」 셀을 또 빼먹는다** → 같은 구멍이 재발한다(이번 결함이 바로 그 구멍이다).
3. **원인을 확인하지 않고 admin-service·product-service 양쪽에 「테넌트 헤더를 다시 보낸다」는 식으로 겹쳐 고친다** → 실제 원인(예: 공통 전파 계층)을 못 찾은 채 증상만 가린다.

---

# AC-0 결과 (2026-09-29 UTC · 분석=Opus 5.5 · 창 없음 — 코드·배선 판독)

🔴 **같은 모양의 404 둘은 원인이 서로 달랐다.** 둘 다 account-service 판정 로직 문제가 아니고, 둘 다 **배포 토폴로지에만 있는
IAM 게이트웨이 한 홉**에서 생긴다 — 그래서 호출자를 account-service(또는 그 목)에 **직결**하는 모든 스위트가 초록이었다.

| 호출 경로 | 호출자가 싣는 값 | 중간 계층 | account-service 에 **도착하는** 것 | 결과 | 값을 만드는 자리 |
|---|---|---|---|---|---|
| 콘솔 → admin-service → account-service `/lock` | 콘솔 `X-Tenant-Id: ecommerce` (`iam-gateway.ts:311`, 활성 테넌트) | 데모 `IAM_ADMIN_API_BASE=${IAM_PUBLIC_URL}` (`infra/demo/demo.env:240`) ⇒ **IAM 게이트웨이 경유**. `JwtAuthenticationFilter` 1단계가 `X-Tenant-Id` 를 **무조건 제거**하고, `/api/admin/**` 는 public 이라 claim 으로 다시 찍지도 않는다 | admin-service 가 헤더 **없음**을 받아 `QueryTenantScopeGate.resolve` 가 운영자 **홈 테넌트 `demo-corp`** 로 기본값(`QueryTenantScopeGate.java:71-73`) → `AccountServiceClient.lock` 이 `X-Tenant-Id: demo-corp` 를 찍음 | `findByTenantIdAndId(demo-corp, 78740d21…)` = 없음 → **404 ACCOUNT_NOT_FOUND** | 🔴 **공통 전파 계층**(게이트웨이 헤더 제거) — 호출자 코드는 옳았다 |
| product-service → account-service `/lock` | `X-Tenant-Id: ecommerce` + 기본 자격 bearer (`AccountServiceSellerProvisioner.java`, MONO-735) | 데모 `ACCOUNT_SERVICE_BASE_URL=http://iam.${DEMO_DOMAIN}` (`demo.env:92`) ⇒ **IAM 게이트웨이 경유**. 게이트웨이의 내부 라우트는 `/internal/tenants/**` **하나뿐**(`gateway-service/application.yml` `account-service-internal`) | **아무것도 도착하지 않는다** — `/internal/accounts/{id}/lock` 은 라우트가 없다 | 게이트웨이 **no-route 404** → fail-soft 가 삼킴(셀러 SUSPENDED · 계정 ACTIVE) | 🔴 **배선**(라우트 없는 경로를 부름) — 헤더 값은 무관했다 |
| 대조군: security-service 자동 잠금 | 헤더 없음 | 데모 `ACCOUNT_SERVICE_BASE_URL: http://account-service:8082` 직결(`iam docker-compose.e2e.yml:256` — 서비스 env 가 `demo.env` 를 이긴다) | 헤더 없음 → (b) 경로 | 🟢 LOCKED | — |
| 대조군: 목록 조회(콘솔 → admin → account) | 테넌트를 **쿼리 파라미터**로 (`iam-accounts-read.ts:146`) | 게이트웨이는 쿼리를 건드리지 않는다 | `tenantId=ecommerce` | 🟢 ecommerce 계정이 보인다 | ⇒ «보이는데 잠기지 않는» 모양의 이유 |

**판독 근거의 한계(정직하게)**: 이 표는 **코드·배선 판독**이다 — 창이 없어 도착 헤더를 로그로 찍지 못했다. 다만 두 기전 모두
판독만으로 결정적이다(게이트웨이 1단계는 조건 없는 `remove`, 라우트 표는 한 줄). 기전 ① 은 게이트웨이 단위 테스트로 재현했다
(§ AC-1 — 고치기 전 코드에서 `/api/admin/**` 의 `X-Tenant-Id` 가 하류에 **null** 로 도착). 결과 판정은 AC-2 가 한다.

## 🔴 이 티켓 전제의 정정 두 가지

1. **「`AccountMutationTenantConfinementIntegrationTest` 에 올바른 구체 테넌트 → 200 셀이 없다」는 틀렸다.** 있다 —
   `lock_concreteOwnTenantHeader_ecommerceAccount_locks`(X-Tenant-Id=ecommerce · ecommerce 계정 → 200 LOCKED)와
   `lock_sameTenant_succeeds`(wms). 그래서 account-service 판정은 CI 가 이미 검증하고 있었고, 빠진 것은 그 셀이 아니라
   **게이트웨이를 지나는 경로를 재는 셀**이었다. 이 정정이 원인 탐색을 account-service 밖으로 옮긴 첫 단서였다.
2. **「실제로 도착하는 헤더 값이 `ecommerce` 가 아닐 것」은 admin 경로에서만 참이다**(`demo-corp` 로 도착). product 경로는
   **요청 자체가 도착하지 않았다.** 두 증상을 한 원인으로 묶었으면 Failure Scenario 3 을 그대로 밟았을 것이다.

## 🔴 곁발견 — 헤더를 통과시키면 열리는 구멍 둘 (같은 변경에서 막음)

게이트웨이가 `/api/admin/**` 의 `X-Tenant-Id` 를 통과시키면, 그 헤더를 **판정 없이 믿는** admin 엔드포인트는 스푸핑 구멍이 된다.
전수 감사(`X-Tenant-Id` 를 읽는 admin 컨트롤러 메서드 전부)에서 둘이 나왔다 — 둘 다 **헤더가 안 도착해서** 가려져 있었을 뿐이다:

| 엔드포인트 | 이전 | 이후 |
|---|---|---|
| `GET /api/admin/partnerships` | `PartnershipManagementUseCase.list` 가 헤더 테넌트로 바로 조회 — javadoc 은 «D2 read parity» 라 적었고 `admin-api.md` 도 confine 을 요구했지만 **판정 호출이 없었다** | `TenantScopeGuard.requireTenantReadable(partnership.manage)` → 범위 밖 `403 PARTNERSHIP_SCOPE_DENIED` |
| `GET /api/admin/operators/{id}/assignments` | `ManageOperatorOrgScopeUseCase.listAssignments` 가 헤더 테넌트로 바로 조회 — 같은 클래스의 쓰기 형제(`setOrgScope`)는 판정한다 | 같은 판정(`operator.manage`) → 범위 밖 `403 TENANT_SCOPE_DENIED` |

나머지 소비자(계정 잠금·해제·일괄잠금·GDPR 삭제·내보내기·세션 회수·파트너십 변경 6종·org-scope 쓰기)는 전부 사용 전에
`QueryTenantScopeGate` 또는 `TenantScopeGuard` 를 거친다. 읽기 판정은 `GroupAdminUseCase.requireGroupReadable403` 선례대로
**DENIED 감사 행 없이** 403 이다(BE-486 읽기 경로 규칙) — 그래서 새 `ActionCode` 가 필요 없다.

## 소유자 결정 (2026-09-29 UTC)

- admin 경로 = **게이트웨이가 `/api/admin/**` 에서 호출자의 `X-Tenant-Id` 를 유지**(권장안). 대안(데모 Traefik 직결 · 콘솔이 다른 채널로 전송)은 기각.
- product 경로 = **`lockAccount` 를 테넌트 경로 `PATCH …/status LOCKED` 로**(권장안). 대안(게이트웨이에 `/internal/accounts/**` 라우트 추가 — 공개 엣지에 잠금 EP 노출 · 데모 네트워크 직결)은 기각.

# AC-1 결과 (2026-09-29 UTC)

**변경** (계약 먼저 — `gateway-api.md` § Admin Routes · § Request Headers, `admin-api.md` 두 읽기 EP, `product-to-account.md` § 인증 · § 3,
`multi-tenancy.md` § Gateway 검증):

| 자리 | 변경 |
|---|---|
| `gateway-service` `JwtAuthenticationFilter` | `/api/admin/` 접두 경로에서만 호출자의 `X-Tenant-Id` 를 유지(`keepsCallerTenant`). `X-Account-ID` · `X-Device-Id` 는 여전히 제거, 다른 모든 경로는 여전히 제거 |
| `admin-service` `TenantScopeGuard` | 읽기용 `requireTenantReadable` 추가(같은 범위 규칙 · DENIED 행 없음) |
| `admin-service` `PartnershipManagementUseCase.list` · `ManageOperatorOrgScopeUseCase.listAssignments` | 헤더 테넌트를 사용 전에 판정(§ 곁발견) — 시그니처에 호출자 `OperatorContext` 추가, 컨트롤러가 넘김 |
| `product-service` `AccountServiceSellerProvisioner.lockAccount` | `POST /internal/accounts/{id}/lock` → `PATCH /internal/tenants/{t}/accounts/{id}/status {LOCKED}`(CLOSE 와 같은 호출 공유). 이제 쓰지 않는 기본 자격(`IamClientCredentialsTokenProvider`) 생성자 인자 제거 |

**「올바른 구체 테넌트 → 200」 셀 (양쪽 다)**:
- admin: `AccountServiceClientUnitTest.lock_concreteTenant_arrivesVerbatim_200` — 실제 client 빈 → WireMock 이 `X-Tenant-Id=ecommerce` 일 때만 200.
  (admin-service 의 해석 절반은 기존 `QueryTenantScopeGateTest` 두 셀 — 「생략 → 홈 테넌트」「배정된 비-홈 테넌트 → 그대로」— 이 이미 핀한다.)
- gateway: `JwtAuthenticationFilterUnitTest.filter_adminSubtree_keepsCallerTenant_stillStripsIdentityHeaders` (+ 대조군 `…OutsideAdminSubtree_stillStripsTenant` · 경계 `keepsCallerTenant_onlyTheAdminSubtree`).
- product: `AccountServiceSellerProvisionerTest.lockAccount_carriesSellersTenant_headerAndExchangedBearer`(경로 테넌트 · `X-Tenant-Id` · 교환 토큰이 **셋 다** 셀러 테넌트일 때만 200)
  · `lockAccount_patchesTenantPathStatusLocked` · `noCallTargetsTheUnroutedTenantlessSurface`. 🔴 마지막 셀은 이전의
  `tenantlessPathKeepsTheBaseCredential` 을 **대체**했다 — 그 셀은 바로 이 결함(라우트 없는 경로 호출)을 「옳다」고 핀하고 있었다.

**검증 (로컬, 2026-09-29 UTC, 워크트리 `task/mono-737-…`)**: `gateway-service:test` · `admin-service:test` · `product-service:test` **rc=0** —
gateway 116 tests / 0 fail / 0 skip · admin 866 / 0 / 58 skip(Docker IT) · product 383 / 0 / 0. 새 셀이 실제로 실행됐는지 리포트 XML 로 확인(스킵 0).

**bite (원본을 스크래치패드에 백업 → 변이 → 파일 복사로 원복, md5 일치 확인)**: ① 게이트웨이 `keepTenant=false` → 게이트웨이 셀 1개 실패
(`Expecting actual not to be null` — **고치기 전 코드에서 `/api/admin/**` 의 `X-Tenant-Id` 가 하류에 null 로 도착** = AC-0 기전 ① 재현)
② `listAssignments` 판정 제거 → 1개 실패 ③ 파트너십 `list` 판정 제거 → 2개 실패 ④ `lockAccount` 를 옛 `POST /internal/accounts/{id}/lock`
모양으로 → 3개 실패. 합계 **7개 실패 · 기존 셀 0개** → 원복 후 같은 대상 **rc=0**.

⚪ **로컬에서 잰 적 없는 것**: 게이트웨이를 실제로 거치는 end-to-end(데모 토폴로지 — CI/e2e 는 직결이라 재현 경로가 없다) → AC-2.
admin-service `@Tag("integration")` 58건은 Docker 필요 — CI 가 돈다.

# AC-2 창 런북 (다음 데모 창)

🔴 **0단계 — 재굽기 확인.** 바뀐 것은 이미지 안의 코드(gateway-service · admin-service · product-service)다. 백엔드는 구워진
클론에서 돈다 ⇒ **이 PR 이 들어간 커밋으로 구운 AMI** 가 아니면 이 판정은 옛 코드를 잰다. 부팅 점검에서 `RepoCommit` 이 이 PR 의
스쿼시 커밋을 **조상으로 포함**하는지(`git merge-base --is-ancestor <squash> <RepoCommit>` rc=0) 먼저 확인하라.

| 스텝 | 방법 | 🟢 판정 | 대조군 |
|---|---|---|---|
| ① 콘솔 잠금 | `demo@demo.com` 로그인 → 테넌트 전환 `ecommerce` → **일회용** `ecommerce` 계정 잠금(공유 데모 계정 금지) | 콘솔 성공 · `account_db.accounts.status=LOCKED` · admin-service 로그에 `404 … /lock` 없음 · `admin_actions` SUCCESS | 테넌트를 `demo-corp` 로 되돌려 **다른** 일회용 `ecommerce` 계정 잠금 → **404 유지**(격리가 풀리지 않았다는 증거 — 헤더가 이제 도착하므로 이 404 는 «운영자가 고른 테넌트로 한정» 의 결과여야 한다) |
| ② 셀러 정지 | 일회용 셀러 온보딩(PENDING→ACTIVE 확인) → 콘솔에서 정지 | 셀러 `SUSPENDED` **그리고** 그 `account_id` 의 `accounts.status=LOCKED` · product-service 로그에 `seller account lock failed` 없음 · `account_status_history` 최신 행 `reason_code=OPERATOR_PROVISIONING_STATUS_CHANGE` | 같은 셀러의 역투영 루프: `account.locked` 소비 뒤 셀러가 여전히 `SUSPENDED`(이미 정지 — no-op, 오류 로그 없음) |

- 🔴 **유효성 술어**: ① 은 계정이 **잠그기 전 ACTIVE** 였음을 먼저 적어라(이미 LOCKED 면 409 로 갈라져 판정 불가). ② 는 셀러에
  `account_id` 가 **비어 있지 않아야** 한다(null 이면 net-zero 로 호출 자체가 없다 — 그 «성공» 은 아무것도 재지 않았다).
- 결과로 닫는 것: `TASK-MONO-735` AC-3 잔여 스텝 2·4 · `TASK-MONO-726` 항목 14 ②. 둘 다 `review/` 에서 이 판정을 기다린다.

---

## CORRECTION (2026-10-02 UTC) — AC-2 라이브 판정 🟢 (결과 상태로)

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

- **콘솔 계정 잠금**: `demo@demo.com`(SUPER_ADMIN) · 테넌트 `ecommerce` 전환 · 일회용 계정 `0bf5cab4-4c18-447c-9d1a-1696a8fb774f`(🔵 풀 계정 — ecommerce ACTIVE 멤버. 티켓은 풀 이전 작성) → `POST /api/accounts/{id}/lock` **200**(`ACTIVE → LOCKED`, auditId `966a5804-1cbf-42b5-8737-9d2813611d7a`) → API 재조회·UI 재검색 **LOCKED**. DB 재확인: `account_status_history` = `LOCKED · ADMIN_LOCK · operator`(SSM). 풀 계정이 ecommerce 목록·잠금 경로에 보이는 것은 `TASK-BE-616` 의 사이트 조회 확대가 같이 증명한다.
- **셀러 정지**: 일회용 셀러 `live18-1790934860` 등록 `201`(즉시 ACTIVE) → `POST …/suspend` **204** → SUSPENDED → 그 기계 계정 `seller+ecommerce+live18-1790934860@marketplace.local`(`168cd338-cd21-4538-8236-f8a9ade6ddc2`) **LOCKED**(API·UI). ⚪ 정지 **전** 상태는 기록하지 못했다(존재로 `account_id` 배선은 증명).
- ⚪ 미측정: 런북의 대조군(demo-corp 로 전환해 잠금 → 404) · `admin_actions` 행.
⇒ AC-2 의 술어(200 · LOCKED · 셀러 정지 → LOCKED)는 충족 → `done/`. 이 결과로 `TASK-MONO-735` AC-3 스텝 2·4 와 `TASK-MONO-726` 항목 ① 의 결과 상태도 닫힌다(각 파일에 기록).
