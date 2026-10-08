# Task ID

TASK-MONO-777

# Title

erp 직원 ↔ 계정 연결 제안에서 **이메일로 계정 찾기** — 인사 담당이 UUID 를 직접 입력하지 않게 (iam 읽기 · 콘솔)

# Status

in-progress

# Owner

monorepo

# Task Tags

- iam
- platform-console
- erp

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 누가 어느 테넌트의 계정을 이메일로 찾을 수 있게 하나(존재 노출 · 범위)가 판정의 전부.

---

# Dependency Markers

- 출처: `TASK-PC-FE-318` AC-0 판단 1 · 후속 후보(2026-10-08 UTC), 소유자 «추천대로 진행».
- 관련: 우산 `TASK-MONO-774`(review) · `TASK-ERP-BE-044`(done — 제안 시 계정 존재를 확인하지 않는다는 소유자 결정 2차).
- ⚠️ 접점(다른 세션 알림, 2026-10-08 UTC): `TASK-MONO-771`(iam 2FA)이 admin-service 게이트에 2FA 요구를 얹을 수 있다 — 이 티켓이 iam 읽기를 쓰거나 더하면 그 게이트와 만난다. 착수 때 771 의 상태를 확인한다.

# Goal

콘솔의 «계정 연결» 대화상자는 지금 **계정 id(UUID)를 직접 입력**받는다(`TASK-PC-FE-318`, `EmployeeAccountLinkDialog.tsx`). 사람이 자기 계정 UUID 를 알 리 없으므로 데모에서 이 기능은 사실상 쓰기 어렵다. 이메일로 계정을 찾아 고르게 한다.

🔴 **PC-FE-318 의 판단과 계약이 어긋난다 — AC-0 이 먼저 잰다.**

| 출처 | 말하는 것 |
|---|---|
| `TASK-PC-FE-318` AC-0 판단 1 | IAM 계정 조회는 별도 IAM 권한이 필요하고, 그 프로필은 403 을 강제 재로그인으로 다룬다 ⇒ 직접 입력으로 갔다 |
| `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/accounts` (`:87-115`) | **`email` 파라미터가 있으면 `account.read` 권한 불필요**(기존 동작 유지) — 단 운영자 토큰(`token_type=admin`) · 해석된 테넌트의 effective scope 게이트는 거친다. `email` 없는 목록만 `account.read` |

⇒ 계약대로라면 **새 iam API 없이** 기존 이메일 단건 검색으로 될 수 있다. 막는 것이 있다면 (a) 테넌트 범위(직원이 속한 erp 테넌트 vs 계정이 사는 풀 — `ADR-MONO-080` «consumer pool» 위의 workforce), (b) 콘솔 쪽 403 처리, (c) erp.write 보유자의 운영자 토큰 모양 중 무엇인지 재야 한다.

# Scope

## In Scope

- AC-0 실측 후 가장 작은 길: 기존 `GET /api/admin/accounts?email=` 재사용이 되면 콘솔 프록시 + 대화상자 «이메일로 찾기» 만. 안 되면 무엇이 막는지(위 a/b/c)를 적고 iam 쪽 최소 변경(계약 먼저)을 고른다 — 새 권한 키가 필요하면 **소유자 결정**으로 멈춘다(권한 행렬은 분류기 게이트 대상이기도 하다).
- 존재 비노출: 찾지 못함과 범위 밖을 같은 문구로.
- 직접 입력은 남긴다(대체 경로).

## Out of Scope

- 제안 시 IAM 존재 확인(소유자 결정 2차 «안 함» 그대로 — 이 티켓은 **고르는 화면**이지 서버 판정이 아니다).
- 승인자 선택기 검색(100 명 한계, 별도 후보).

# Acceptance Criteria

- [x] **AC-0** — (→ § AC-0 기록 · 🔴 결론 = **STOP, 소유자 결정 대기**) 위 표의 모순을 실측으로 푼다: erp.write 만 가진 콘솔 운영자 토큰으로 `GET /api/admin/accounts?email=` 가 어떻게 답하는지(코드 경로 file:line + 시험), 계정이 사는 테넌트와 erp 테넌트의 관계, 콘솔 403 처리 경로. 결론과 고른 길을 적는다.
- [ ] **AC-1** — 대화상자에서 이메일 입력 → 계정 하나를 골라 제안까지(렌더 DOM 시험).
- [ ] **AC-2** — 없는 이메일 · 범위 밖 이메일 → **같은 문구**(존재 비노출) — 한 시험에서 비교.
- [ ] **AC-3** — 조회가 403/401 이어도 **로그아웃되지 않는다**(PC-FE-318 이 지목한 위험) — 시험으로 고정.
- [ ] **AC-4** — bite: AC-2 의 같은 문구를 깨면 AC-2 칸만 빨강.
- [ ] **AC-5** — 콘솔 `tsc` · `lint` · `vitest` rc=0 · e2e grep · 머지 뒤 nightly 콘솔 확인.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` (D7 = E1)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8
- `projects/iam-platform/specs/services/admin-service/rbac.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/accounts
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link

# Edge Cases

- 같은 이메일이 여러 테넌트에 — 어느 테넌트의 계정인지 화면이 보여야 한다(직원의 erp 테넌트와 맞는 것만 고를 수 있게).
- 이미 다른 직원에 연결된 계정 — 고를 수는 있되 서버 409 `account_already_linked` 문구로.

# Failure Scenarios

1. 찾지 못함과 범위 밖이 다른 문구 — 이메일로 남의 테넌트 계정 존재를 캐는 길이 된다.
2. 조회 403 이 콘솔 세션을 끊는다 — 인사 담당이 연결 화면에서 로그아웃된다.
3. 계약을 안 읽고 새 iam API 부터 만든다 — 이미 있는 이메일 검색과 겹치는 두 번째 길이 생긴다.

---

# AC-0 기록 (2026-10-08 UTC · 기준 `origin/main` `6971e88fe`)

착수 시 접점 확인: `TASK-MONO-771`(iam 2FA) · `TASK-MONO-772`(풀 직원 · 운영자 규칙) 둘 다 **`ready/`** — admin-service 게이트에 아직 아무것도 얹히지 않았다.

## 한 줄 결론

🔴 **표의 모순은 계약 쪽이 맞다 — 권한은 막지 않는다. 그런데 그 검색은 «연결에 필요한 id» 를 찾는 검색이 아니다.** 기존 `GET /api/admin/accounts?email=` 은 erp 인사 담당자도 부를 수 있고(새 권한 불필요 · 403 이 로그아웃시키지도 않는다), 대신 **활성 테넌트의 `account_db` 계정 행**을 찾는다. 연결이 필요로 하는 것은 **그 사람이 콘솔에 들고 오는 `sub`**(= 운영자 `oidc_subject`)이고, 둘은 운영자가 만들어진 길에 따라 **같기도 다르기도** 하다. 데모의 연결 대상 둘(`demo@` · `requester@`)은 **활성 테넌트에 계정 행이 아예 없어** 이메일 검색이 항상 빈 결과다. ⇒ 콘솔만 고쳐서는 이 티켓의 목표(«데모에서 쓸 수 있게»)에 닿지 않고, 닿게 하는 길은 전부 **정책 선택**이다 → **AC-0 에서 멈춘다**(아래 선택지).

## (a) 누가 부를 수 있나 — 권한은 막지 않는다 ✅

| 잰 것 | 결과 | 근거 |
|---|---|---|
| 콘솔이 IAM 관리 호출에 쓰는 토큰 | 교환된 **운영자 토큰**(`token_type=admin`) 하나. 콘솔 셸 안의 모든 사람은 이 토큰을 갖는다(`isAuthenticated` = IAM 토큰 ∧ 운영자 토큰) | `console-web/src/shared/lib/session.ts:174-177, 249-251` · `shared/api/iam-accounts-read.ts:47-51` |
| `erp.write` 가 무엇인가 | **assume-tenant 토큰의 scope**(auth-service) — admin-service 권한 키가 아니다. 이 호출의 판정에 아무 역할이 없다 | auth-service `AssumeTenantAuthenticationProvider` · `V0023__add_erp_write_scope_to_platform_console.sql` |
| 게이트웨이 | `/api/admin/**` 는 JWT 검증을 admin-service 에 통째로 위임(경로 열거 없음) | iam `gateway-service/src/main/resources/application.yml:154-201` |
| 권한 aspect | GET 은 `@RequiresPermission` fail-closed 대상이 아니다 | `admin-service/.../aspect/RequiresPermissionAspect.java:214` |
| 이메일 분기 | 권한 검사 **없이** `accountServiceClient.search(resolvedTenant, email)` | `AccountAdminController.java:91-95` (목록 분기만 `:105-111` 에서 `account.read`) |
| 테넌트 게이트 | 운영자 행 존재 + 요청 테넌트 ∈ (홈 ∪ 배정). 생략 → 홈. 밖 → `403 TENANT_SCOPE_DENIED` | `QueryTenantScopeGate.java:54-84` |
| 콘솔이 보내는 테넌트 | `tenantId` = **활성 테넌트**(쿠키) — 인사 담당이 erp 를 쓰는 바로 그 테넌트라 범위 안이다 | `iam-accounts-read.ts:150-152` |
| 🔵 시험 | 새 칸 `AccountAdminControllerSliceTest` › `search_withEmail_operatorWithNoPermissions_returns_200_in_resolved_tenant` — **권한 0** 운영자의 이메일 검색이 200, 검색 테넌트 = 게이트가 푼 테넌트. 기존 `search_withEmail_delegates_to_search_client` 는 `@BeforeEach grantAll()` 위라 «권한 없는 분기» 를 구별하지 못했다 | 아래 «bite» |

⇒ `TASK-PC-FE-318` AC-0 판단 1 의 ⓐ «IAM `account.*` 권한을 요구한다» 는 **틀렸다**(이메일 분기 한정).

## (c) 콘솔 403/401 처리 — 403 은 로그아웃시키지 않는다 ✅

| 잰 것 | 결과 | 근거 |
|---|---|---|
| 브라우저에서 로그아웃을 일으키는 유일한 곳 | `apiFetch` — **401** 이면 `/api/auth/refresh` 1회 → 실패 시 `/login?redirect=` | `shared/api/client.ts:100-116` |
| 403 | `parseError` → `ApiError(403)` 로 던질 뿐, 재로그인 없음 | 같은 파일 `:116` |
| `forbiddenMode: 'auth'` 가 하는 일 | 서버 쪽에서 403 을 `ApiError(403, code, 'not permitted')` 로 바꿀 뿐 — 세션을 지우지 않는다 | `shared/api/iam-gateway.ts:183-194` |
| 기존 `/api/accounts` 프록시 | 403 을 **403 그대로** 돌려준다(본문 메시지만 `'session expired'`) | `app/api/accounts/route.ts:41-46` |

⇒ PC-FE-318 판단 1 의 ⓑ «403 을 재로그인으로 다룬다» 는 **낡은 주석을 읽은 것**이다 — `iam-accounts-read.ts:59-60` · `app/api/accounts/route.ts:15` · `AccountsScreen.tsx:38` 의 «401/403 → re-login» 문구가 코드와 다르다(`features/accounts/api/accounts-state.ts:14-16` 은 이미 «403 은 재로그인이 아니다» 로 고쳐져 있다). 🔵 AC-3 의 위험은 **401** 쪽에만 남는다 — 운영자 토큰 만료면 refresh 가 맞는 동작이고, 새 프록시가 IAM 의 403 을 401 로 바꾸지만 않으면 된다.

## (b) 어느 테넌트의 무엇을 찾나 — 🔴 여기서 막힌다

연결(`employees.account_id`)이 필요로 하는 값 = **그 사람의 콘솔 assume 토큰 `sub`**(결재함 술어 «승인자 직원의 `account_id` = 내 `sub`», 수락 = «JWT `sub` = accountId» — `ADR-MONO-060` A · `seed-erp.sh:161-162, 317`). 그 `sub` 는 콘솔 로그인이 고른 **자격 행의 `account_id`** 이고(`TenantClaimTokenCustomizer#alignSubToAccountId`), 콘솔 client 의 자격 고르기는:

| 순서 | 자격 | 근거 |
|---|---|---|
| 1 | `iam` 테넌트 자격이 있으면 그것 | auth-service `CredentialAuthenticationProvider.java:204-213` |
| 2 | 없으면 이메일로 **전 테넌트** 조회 — 하나면 그것, 여럿이면 fail-closed | 같은 파일 `:219-235` |

이메일 검색은 **활성 테넌트의 `account_db.accounts` 행**(+ 소비자 사이트면 그 사이트의 풀 멤버 — `AccountServiceClient.java:46-62`)을 찾는다. 그래서 운영자가 어떻게 만들어졌냐에 따라:

| 운영자의 출처 | 콘솔 `sub` | 활성(회사) 테넌트 이메일 검색 | 연결에 쓸 수 있나 |
|---|---|---|---|
| `/operators` 생성(`TASK-MONO-334` — 그 테넌트에 가입 계정 필수) | 그 회사 테넌트 계정 id(2번 경로) — `oidc_subject` 도 같은 id(`OperatorOidcSubjectBackfillUseCase.java:114-139`) | 그 행을 찾는다 | ✅ 같은 id |
| 🔴 데모 시드 운영자 `demo@` · `requester@` | `iam` 자격 `…ad03` · `…ad04`(1번 경로) | **빈 결과** — `account_db` 에 이 둘의 행이 없다(`R__07_seed_demo_corp_new_hire_account.sql:15-19` 실측 «`tenant_id='demo-corp'` count = 0», 그 뒤 `demo-corp` 계정은 `newhire@` 하나) | ❌ — 연결 대상 **둘 다** «찾지 못함» |
| 셀프 온보딩 운영자(`ADR-MONO-044` D5) | 소비자 계정 id(2번 경로, `CredentialAuthenticationProvider.java:190-192`) | 회사 테넌트엔 그 행이 없다 | ❌ |
| 🔴 `TASK-MONO-772` 이후(풀 직원, `ADR-MONO-080` D5 «계정의 `tenant_id` 는 `consumer-pool`») | 풀 계정 id | 회사 테넌트엔 행이 없고, `tenantId=consumer-pool` 은 일반 운영자 범위 밖 → `403 TENANT_SCOPE_DENIED` | ❌ — 080 의 목표 모델에서 **구조적으로** 못 찾는다 |

🔴 거꾸로 위험도 있다: 데모에서 `demo@demo.com` 을 소비자 사이트 테넌트(`ecommerce`)에서 찾으면 풀 계정 `…ec01` 이 나온다(`R__01_seed_demo_single_identity_credentials.sql:103-107`) — **콘솔 `sub`(`…ad03`)와 다른 id** 다. 그걸 고르면 제안은 수락될 수 없다(수락자 `sub` ≠ `accountId`). 실패는 안전 쪽(두 사람 규칙)이지만 화면은 «찾았다» 고 말한다.

⇒ **검색이 답하는 질문(«이 테넌트에 이 이메일의 계정 행이 있나»)과 연결이 묻는 질문(«이 사람이 이 테넌트에 운영자로 들어올 때 들고 오는 `sub` 는 무엇인가»)이 다르다.** 후자의 정답 모집단 = **이 테넌트의 운영자 측면**(`admin_operators` 홈 ∪ 배정)이고, 그 `oidc_subject` 가 곧 `sub` 다(`ADR-MONO-080` § 위험 ③ «`oidc_subject` = 계정 UUID, `TokenExchangeService.java:77-88`»). 수락할 수 있는 사람도 정의상 그 모집단뿐이다(erp assume 토큰이 있어야 수락한다).
그 모집단을 읽는 기존 표면 `GET /api/admin/operators` 는 `operator.manage` 를 요구하고(`OperatorAdminController.java:93-94`) 행에 `oidc_subject` 가 없다(PC-FE-318 AC-0 ①) — 인사 담당자에게 열려면 **권한 행렬 변경** 또는 **새 읽기 + 그 인가 규칙**이 필요하다.

## 선택지 — 🔴 소유자 결정 (티켓 Scope «새 권한 키가 필요하면 소유자 결정으로 멈춘다» · 권한 행렬은 분류기 게이트 대상)

| | 무엇 | iam 변경 | 데모 `demo@`·`requester@` | 772 이후 | 대가 |
|---|---|---|---|---|---|
| **L1 콘솔만 — 기존 이메일 검색** | 프록시 + 대화상자 «이메일로 찾기», 직접 입력 유지 | 없음 | ❌ 항상 «찾지 못함» | ❌ | 334 로 만든 운영자에게만 맞는 id. 목표(«데모에서 쓰게»)에 안 닿는다. 소비자 사이트 테넌트에서는 다른 id 를 «찾았다» 고 보일 수 있다 |
| **L2 운영자 측면 조회 — 새 읽기** | `GET /api/admin/operators/lookup?email=`(가칭) — 활성 테넌트(홈 ∪ 배정)의 운영자 중 이메일 정확 일치 1건 → `{ accountId = oidc_subject, displayName, tenantId }`. 인가 = **기존 이메일 계정 검색과 같은 규칙**(권한 키 없음 · `QueryTenantScopeGate` · 없음/범위 밖 = 같은 빈 응답) | 새 엔드포인트 + 계약 절(새 권한 키 **없음**, 행렬 변경 **없음**) | ✅ (`demo-corp` 운영자 둘) | ✅ (측면은 그대로, `oidc_subject` 만 풀 id 가 된다) | «운영자 이메일 → 계정 id» 를 **모든 테넌트 운영자**에게 여는 것이 정책 선택이다(지금 운영자 목록은 `operator.manage` 전용). 이메일 정확 일치 1건 · 테넌트 범위로 열거는 막힌다 |
| **L3 `operator.read`(가칭) 새 권한 + 기존 목록에 `accountId` 칸** | 운영자 목록을 인사 담당에게 열기 | 새 권한 키 + 역할 행렬 + 계약 | ✅ | ✅ | 인사 담당이 테넌트 운영자 **전체**를 본다(열거). 행렬 편집 = 분류기 게이트 |
| **L4 콘솔만 — «내 계정 ID» 보이기** | 수락 카드/내 정보에 본인 `sub` 를 복사 버튼으로 — 본인이 인사 담당에게 건넨다. 직접 입력 유지 | 없음 | ✅ (사람 손으로) | ✅ | «이메일로 찾기» 가 아니다 — 티켓 제목의 목표를 바꾸는 것 |
| **L5 흐름 뒤집기 — 본인 요청** | 계정 주인이 «내 계정을 이 직원에 연결 요청» → 인사 담당 승인. `sub` 는 토큰에서 | erp 계약 변경 | ✅ | ✅ | `TASK-ERP-BE-044` 소유자 결정(«인사 제안 + 본인 수락»)을 뒤집는다 |

**추천(구현자 선호, 소유자 결정 아님): L2.** 연결이 실제로 필요로 하는 id(`sub` = `oidc_subject`)를, 수락할 수 있는 사람의 모집단(이 테넌트 운영자)에서, 이미 허용된 것과 **같은 인가 모양**(이메일 정확 일치 · 권한 키 없음 · 테넌트 게이트 · 존재 비노출)으로 찾는다 — 새 권한 키도 행렬 변경도 없다. 그리고 L1 과 달리 `TASK-MONO-772`(풀 직원) 뒤에도 그대로 맞다. 🔴 단 «운영자 이메일 → 계정 id» 를 테넌트 운영자 누구에게나 여는 것 자체가 정책이라 내가 정하지 않는다. 급하면 L4 를 L2 와 별개로 먼저 낼 수 있다(콘솔만, 정책 없음).

## 하지 않은 것 (멈춘 범위)

- 콘솔 프록시 · 대화상자 · AC-1~AC-5 — 위 선택 전에는 무엇을 부를지 정해지지 않는다. 🔵 어느 선택지든 AC-2(없음 = 범위 밖 같은 문구)·AC-3(403 은 로그아웃 아님 — 위 (c) 실측으로 이미 성립, 새 프록시가 403 을 401 로 바꾸지 않게 시험으로 고정) 은 그대로 적용된다.
- 낡은 «401/403 → re-login» 주석 셋(`iam-accounts-read.ts:59-60` · `app/api/accounts/route.ts:15` · `AccountsScreen.tsx:38`) — 이 티켓의 구현 PR 에서 고친다(지금 고치면 멈춘 티켓이 콘솔 코드를 바꾼다).

## bite (AC-0 의 새 시험)

`AccountAdminController.java` 이메일 분기에 `account.read` 검사를 한 줄 넣고 `AccountAdminControllerSliceTest` 를 돌렸다 → rc=1, **17 중 정확히 1 실패**: 새 칸 «`search_withEmail_operatorWithNoPermissions_returns_200_in_resolved_tenant` — Status expected:<200> but was:<403>». 🔴 기존 `search_withEmail_delegates_to_search_client` 는 **초록 그대로** — 그 칸이 이 성질을 지키지 못했다는 증거다. Edit 로 되돌린 뒤(`git diff -- …/src/main` 0줄) 17/17 rc=0.
