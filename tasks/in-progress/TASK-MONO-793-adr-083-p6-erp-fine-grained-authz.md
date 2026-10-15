# Task ID

TASK-MONO-793

# Title

`ADR-MONO-083` P6 — ERP 세분 서버 인가: 기준정보 · 결재의 조회/변경 역할 · `ERP_OPERATOR` 는 진입 역할로 · iam 카탈로그 확장

# Status

in-progress

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (앱 단 인가 포트 · 직무 분리 가드와의 관계 · 원자 PR)
> 🔴 **cross-project 원자 PR**: erp 인가 + iam 카탈로그 · «이전 호환» ERP 세트 갱신.

# Dependency Markers

- **선행**: `TASK-BE-631` · `TASK-BE-632`(🔴 P3 가 운영자 토큰의 `erp.write` 를 이미 걷었어야 한다 — 남아 있으면 이 단계의 세분이 범위로 우회된다).
- 형제: `TASK-MONO-792` · `794` · `795`.

# Goal

ERP 쓰기 판정은 `erp.write` 범위 ∨ 운영자 역할 하나다(`masterdata-service …/RoleScopeAuthorizationAdapter.java:80-96` · `approval-service …/JwtBackedAuthorizationAdapter.java:32-35`). 기준정보 · 결재를 조회/변경으로 나눈 역할을 앱 단 `AuthorizationPort` 에 넣는다.

# Scope

## In Scope

- 계약 먼저: erp 서비스 명세 · 계약에 역할 · 판정 표.
- `RoleScopeAuthorizationAdapter` · `JwtBackedAuthorizationAdapter` · `ReadAuthorizationGate` — 업무 역할 판정. 결재 자격(직무 분리, `ApprovalRoute`)은 역할과 **별개로 유지**. `org_scope` 부서 범위 판정 무변경.
- `ERP_OPERATOR` = 진입 역할(업무 API 단독 개방 없음). 워크로드 `erp.write`(내부 서비스 클라이언트)는 무변경.
- iam 카탈로그 · «이전 호환» ERP 세트 · 시스템 프리셋.

## Out of Scope

- 관리 등급 신설 · `entitled_domains` 읽기 합성(기존 READ 확장 규칙)의 재설계 — AC-0 에서 «구독만으로 READ» 가 세분 조회 세트를 무력화하는지 측정만 하고, 무력화하면 이 티켓 범위로 끌어온다.

# Acceptance Criteria

- [x] **AC-0** — 재측정: 쓰기 엔드포인트 전수 · 🔴 `entitled_domains∋erp` 가 역할 없이 READ 를 여는 규칙(`RoleScopeAuthorizationAdapter.java:80-96`)이 «조회 세트 없음 → 조회 403» 을 깨는지 · 업무 구분안 대조(OD-6). → § AC-0 기록(2026-10-15 UTC) — **깬다**(실측) · OD-6 을 글자 그대로 못 가는 점 여섯 → § 질문과 추천. 🔴 STOP — 소유자 결정 전 구현 커밋 없음.
- [ ] **AC-1** — 🔴 대조군: 결재 세트만 → 기준정보 변경 403 · 결재 200 / 조회 세트 → 변경 403 / `ERP_OPERATOR` 만 → 업무 변경 403 / 결재 자격(직무 분리) 거절은 역할과 무관하게 유지.
- [ ] **AC-2** — 이전 호환 대조군 · 부서 범위(`org_scope`) 판정 불변.
- [ ] **AC-3** — 배포 상태를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3
- `projects/erp-platform/specs/services/*/architecture.md` (`TASK-ERP-BE-021` 역할 전용 모델)

# Related Contracts

- `projects/erp-platform/specs/contracts/http/*` · iam `admin-api.md` § 카탈로그

# Edge Cases

- 플랫폼 와일드카드(`tenant_id='*'`) GET — 지금 규칙 유지.
- 읽기 모델 서비스의 «`org_scope` 없음 = 좁히지 않음» 비대칭 — 무변경.

# Failure Scenarios

1. `erp.write` 가 운영자 토큰에 남은 채 착수 — 세분이 범위로 우회된다.
2. 구독 합성 READ 를 그대로 두고 «조회 세트» 를 만든다 — 세트 없이도 조회가 된다.

---

# AC-0 기록 — 2026-10-15 UTC (base `81d00faf5` = 792 머지 직후) · 🔴 **STOP — 착수 전 소유자 결정 필요**

> 측정 = 코드 읽기(정적). 라이브 DB · 라이브 토큰은 열지 않았다(⚪). 이 기록 뒤 구현 커밋은 **없다** — 아래 Q1~Q6 의 답을 받은 뒤 계약부터 시작한다. 선례 = `TASK-MONO-792`(같은 모양 · 소유자 결정 Q1~Q5 · 파트너십 확장 승인).

## 0. 선행 조건 (Failure 1) — 충족

- 운영자 assume 토큰의 `erp.write` 는 **이미 걷혔다**: `AssumeTenantAuthenticationProvider.java:105` `OPERATOR_EXCLUDED_SCOPES = Set.of("erp.write")`(BE-632). 워크로드 `erp-platform-internal-services-client` 의 `erp.read/erp.write`(auth `V0018:74`)는 그대로 — 티켓대로 무변경.
- `erp.approval.create` · `erp.approval.approve`(`approval …/ActorContext.java:62-63` 의 쓰기 판정)는 **발급처 0**(auth-service `src/main` grep 0 — 이벤트 이름 외 등장 없음). 판정에 남아 있어도 열리는 길이 없다(기록).

## 1. 지금 판정 (4 서비스 · 필터 체인은 전부 `authenticated("/api/erp/**")` — `*/SecurityConfig.java:59/63`)

| 서비스 | 판정 위치 | READ | WRITE | 출처 |
|---|---|---|---|---|
| masterdata | 앱 단 `AuthorizationPort` (`MasterdataApplicationService.authorize` → `RoleScopeAuthorizationAdapter`) + 대상 부서 `org_scope` 서브트리 판정 | `erp.read` ∨ `erp.write` ∨ `isOperator()` ∨ **`entitled_domains∋erp`** ∨ `tenant_id='*'` | `erp.write` ∨ `isOperator()` | `RoleScopeAuthorizationAdapter.java:80-96` · `ActorContext.java:54-57`(`ERP_OPERATOR`/`ERP_ADMIN`/`SUPER_ADMIN`) |
| approval | 앱 단 `ActorContext.canReadErp/canWriteErp` 단락 → `JwtBackedAuthorizationAdapter`(같은 술어) · 결재 자격 = 집합체 가드(직무 분리) | `canWriteErp` ∨ `erp.read` ∨ **`entitled_domains∋erp`** | `isOperator()` ∨ `erp.write` ∨ `erp.approval.*` | `ApprovalApplicationService.java:331-349` · `DelegationApplicationService.java:173,181` · `ActorContext.java:59-75` |
| read-model | 컨트롤러가 `ReadAuthorizationGate.requireRead` · `org_scope` 읽기 좁힘(«없음 = 좁히지 않음») | `erp.read` ∨ `erp.write` ∨ 운영자 역할 ∨ **`entitled_domains∋erp`** ∨ `'*'` | (변경 없음) | `read-model …/ReadAuthorizationGate.java:58-81` |
| notification | 같은 모양의 `ReadAuthorizationGate` · 받는 사람 = `sub` | 위와 같음 | `POST /{id}/read` 도 READ 게이트(자기 영수증) | `notification …/ReadAuthorizationGate.java:45-70` |

## 2. 🔴 구독 합성 READ 는 «조회 세트 없음 → 조회 403» 을 **깬다** (Failure 2 — 실측)

- 운영자 assume 토큰은 선택 테넌트의 ACTIVE 구독을 `entitled_domains` 로 **항상** 싣는다(`TenantClaimTokenCustomizer.java:975` `populateEntitledDomains` — 역할 판정과 무관, BE-632 뒤에도). ERP 를 구독한 테넌트에 assume 한 운영자는 ERP 세트가 0 이어도 `entitled_domains=[…,erp,…]` 를 갖는다.
- ⇒ 지금 규칙으로는 **ERP 업무 세트가 없는 운영자도 ERP 4 서비스의 READ 전부가 200**. 역할을 세분해도 «조회 세트» 는 서버에서 구분되지 않는다. 티켓 Out of Scope 의 조건(«무력화하면 이 티켓 범위로 끌어온다»)이 성립 ⇒ **이 티켓 범위**. 단, 걷는 순간 아래 Q2 · Q3 · Q5 의 호출자가 함께 깨진다 — 그래서 질문이다.

## 3. 엔드포인트 전수 (JWT 경로 **55** = 조회 26 · 변경 29 — 컨트롤러 `@*Mapping` 전수)

| 서비스 | 조회 | 변경 |
|---|---|---|
| masterdata (35) | job-grades `GET` · `GET /{id}` · employees `GET` · `GET /{id}` · `GET /{id}/account-link-proposals` · departments `GET` · `GET /{id}` · cost-centers `GET` · `GET /{id}` · business-partners `GET` · `GET /{id}` (11) + 🔵 **개인/호출 전용** `GET /employees/me` · `GET /employees/{id}/approver-ref` · `GET /account-link-proposals/mine` (3) | 5 기준정보 × 생성 · 수정 · 퇴역(+ departments `move-parent`) = 16 · 계정 연결 HR 쪽 `POST /employees/{id}/account-link-proposals` · `POST /account-link-proposals/{id}/revoke` (2) + 🔵 **개인** `POST …/{id}/accept` · `POST …/{id}/decline` · `POST /employees/{id}/account-link/unlink`(본인이면 READ, 아니면 WRITE + 부서 범위 — `MasterdataApplicationService.java:548-555`) (3) |
| approval (11) | requests `GET` · `GET /{id}` · inbox `GET` · delegations `GET` (4) | requests `POST` · `submit` · `approve` · `reject` · `withdraw` · delegations `POST` · `revoke` (7) |
| read-model (6) | employees `GET` · `GET /{id}` · approvals `GET` · `GET /{id}` · delegations `GET` · `GET /{id}` (6) | — |
| notification (3) | `GET` · `GET /{id}` (2) | `POST /{id}/read`(자기 영수증) (1) |

## 4. OD-6 기본값과 대조 — 그대로 못 가는 점 여섯

- 🔴 **Q1 — 구독 합성 READ 를 걷을 범위.** § 2 대로 걷어야 세분이 선다. 그러나 이 규칙을 기대는 호출자가 셋 있다(Q2 · Q3 · Q5). 그리고 **콘솔의 «테넌트 미선택» 운영자**는 기본 로그인 토큰(역할 0, 집 테넌트 `entitled_domains`)으로 ERP 를 부른다(`console-web …/session.ts:310-321` `getDomainFacingToken` — 선택 테넌트가 없으면 base 토큰). 이 사람의 ERP 조회는 **오직** 구독 합성으로 열린다 ⇒ 걷으면 ERP 전 화면 403. 🔵 SCM 은 792 에서 이미 같은 결과가 됐다(기본 토큰 = 역할 0 → SCM 403 — federation 스펙 수정의 원인). `'*'` 와일드카드 GET 은 티켓 Edge Case 대로 유지.
- 🔴 **Q2 — 개인 엔드포인트는 «기준정보 조회» 가 아니다.** `GET /employees/me` · `GET /account-link-proposals/mine` · `accept` · `decline` · 본인 `unlink` 는 «내 직원 기록 · 나에게 온 연결 제안» 이고 지금 READ 로 열린다(사실상 구독 합성). 기준정보 역할을 요구하면 **결재 세트만 가진 직원은 자기 계정 연결 제안을 수락할 수 없고**, 그러면 결재 상신자 · 승인자가 될 수 없다(결재는 연결된 직원만 — approval-api § v2.4).
- 🔴 **Q3 — 결재는 운영자 토큰을 그대로 실어 기준정보를 부른다**(792 Q2 와 같은 모양). `MasterDataRestAdapter.java:119-182` — 상신 · 승인 · 반려 · 회수마다 `GET /employees/me`(행위자), 상신 때 `GET /employees/{id}/approver-ref`(단계 승인자) · `GET /departments/{id}` 또는 `GET /employees/{id}`(결재 대상, **부서 범위 판정 포함**). notification-service 도 같은 토큰으로 `GET /employees/me`(`MasterDataCallerEmployeeAdapter.java:92`). 기준정보 READ 에 `ERP_MASTERDATA_READ` 를 걸면 **결재 세트만 가진 운영자의 상신은 422 `subject_unresolved`/`approver_unresolved`, 승인 · 반려는 403**(행위자 미해석) — AC-1 의 «결재 세트만 → 결재 200» 이 이 선택 없이는 성립하지 않는다.
- 🔴 **Q4 — 결재 «테넌트 전체 목록» 의 판정.** `ApprovalApplicationService.java:284` — `isOperator()`(= `ERP_OPERATOR`) 면 테넌트 전체, 아니면 내가 상신자 · 승인자인 것만. 진입 역할을 파생하면(792 Q4 (a)) ERP 업무 역할이 하나라도 있는 모든 사람이 `isOperator()=true` ⇒ 결재 조회 역할을 받은 사람은 **전부 테넌트 전체 목록**을 본다(지금 legacy 운영자 전원과 같은 동작). 결재 «참여자 범위» 를 따로 둘지(역할 하나 더) 아니면 그대로 둘지.
- 🔴 **Q5 — 다른 도메인(IAM) 화면이 ERP 를 부른다.** 콘솔 운영자 화면의 `org_scope` 부서 선택기(`features/operators/hooks/use-operator-assignments.ts:67-91` · `use-org-scope-form.ts:67`)가 활성 테넌트 토큰으로 `GET /api/erp/masterdata/departments` 를 부른다 — **IAM 관리자**의 화면. ERP 기준정보 조회 역할이 없는 관리자는 403 → 설계된 저하(수동 id 입력, `OrgScopeDialog.tsx:34`). 그 밖의 콘솔 집계 소비자: 알림 종(`shared/composition/notification-inbox.ts:69-73` — 기본 도메인 `erp`, 403 → `degradedDomains`) · 운영 개요 ERP 카드 · 도메인 상태. 서버 쪽 교차 도메인 호출자는 **0**(다른 프로젝트 `apps/*/src/main` grep — 콘솔 외 0).
- 🔴 **Q6 — read-model · 알림의 역할 귀속.** OD-6 은 «조회 모델 · 알림은 조회» 라고만 한다. read-model 은 두 갈래(직원 조직 보기 = 기준정보 · 결재/위임 사실 = 결재), 알림은 **받는 사람 범위**(종은 콘솔 셸에서 모든 사용자에게 뜬다).

부수 사실(질문 아님, 기록):
- 🔵 **데이터 이전은 792 Q5 와 같은 모양**(소유자 상시 결정): `ERP_OPERATOR` 를 담은 세트 = 정적으로 `legacy-compat` · `erp-full`(`V0052:132,154`) · `all-domains-full` + ⚪ 관리자가 만든 테넌트 세트 · 파트너십 `delegated_scope`/`participant_scope`. 라이브 행 수 ⚪(라이브 DB 미접속) → 672 다음 창. Flyway 다음 빈 번호 = **`V0057`**(origin/main `81d00faf5` — `V0056` 이 마지막, 2026-10-15 22:44 UTC).
- 🔵 **시드 · e2e 는 이전으로 따라온다**: 데모 `seed-erp.sh` 는 두 운영자 assume 토큰(`:139,149`)으로 쓰고, 두 사람은 `legacy-compat`(`R__seed_zz_demo_domain_permission_sets.sql:36-63`). federation `erp-golden-path.spec.ts` 는 SUPER_ADMIN `'*'` 토큰으로 `/erp` 화면만 연다(와일드카드 GET 유지 ⇒ 무영향 — 추론, 브랜치 실행으로 확인 예정). console e2e(`tests/e2e/*.spec.ts` 5) 에 ERP 호출 스펙 0.
- 🔵 **기계 클라이언트**: `erp-platform-internal-services-client`(범위 `erp.read/erp.write`, 역할 없음)는 범위 갈래가 남으므로 **계속 통한다**(티켓 «워크로드 무변경»). SCM 과 달리 403 이 되지 않는다.
- 🔵 결재 자격(직무 분리 · `ApprovalRoute` · 대결 해석)은 집합체 가드 — 역할 판정과 겹치지 않는다(유지). `org_scope`: masterdata 쓰기 = 서브트리 판정 fail-closed · read-model 읽기 = «없음 = 좁히지 않음» — 둘 다 무변경.

## 5. 질문과 추천 (소유자)

역할 이름(추천, 계약에서 확정): `ERP_MASTERDATA_READ` · `ERP_MASTERDATA_WRITE` · `ERP_APPROVAL_READ` · `ERP_APPROVAL_WRITE`(4). `ERP_OPERATOR` = auth 파생 진입 역할(792 Q4 (a) 의 `ENTRY_ROLES` 에 한 행) · 카탈로그에서 제거 · 배정 · 위임된 것은 버림 · 위임 → 422. 판정: masterdata READ = MD_READ ∨ MD_WRITE, WRITE = MD_WRITE · approval READ = AP_READ ∨ AP_WRITE, WRITE = AP_WRITE · 범위 갈래(`erp.read/erp.write`)와 `'*'` GET 은 유지. 프리셋 추천: `erp-read`(조회 둘) · `erp-masterdata-operate` · `erp-approval-operate` · `erp-full` · `all-domains-full` · `legacy-compat` 확장.

| Q | 질문 | 추천 (구현자 선호) |
|---|---|---|
| Q1 | 구독 합성 READ | **4 서비스 모두에서 걷는다**(`'*'` GET 유지). 테넌트 미선택 운영자의 ERP 403 은 **수용**(792 의 SCM 과 같은 결과 — 업무 권한은 테넌트별 세트로만). 대안: 개인 엔드포인트(Q2)에만 남기기 |
| Q2 | 개인 엔드포인트 | **«ERP 업무 역할이 하나라도 있으면»(= 파생 `ERP_OPERATOR`) ∨ 범위** — `GET /employees/me` · `GET /account-link-proposals/mine` · `accept` · `decline` · 본인 `unlink` · 알림 3 엔드포인트. 진입 역할을 판정 입력으로 쓰는 **유일한** 자리(업무 데이터가 아닌 «나에 관한 것»). 대안: 기준정보 조회 역할 요구(결재 세트만으론 연결 수락 불가) |
| Q3 | 결재 → 기준정보(운영자 토큰) | **`GET /employees/{id}/approver-ref` = Q2 와 같은 규칙** · **`GET /departments/{id}` · `GET /employees/{id}` = MD_READ ∨ MD_WRITE ∨ AP_WRITE**(결재 대상 확인 — 792 Q2 의 `from-suggestion` 과 같은 «한 경로만 두 역할»). 부서 범위 판정은 그대로. 대안: 결재 프리셋에 MD_READ 를 넣기(관리자가 손으로 만든 결재 세트는 상신 불가로 남는다) |
| Q4 | 결재 테넌트 전체 목록 | **그대로**(`isOperator()` 무변경 — 결재 조회 역할 보유자 = 테넌트 전체, 지금 legacy 와 같음). «참여자만 보는 결재자» 는 별도 역할이 필요하면 후속 |
| Q5 | IAM 화면의 부서 선택기 · 콘솔 집계 | **엄격**(792 Q3 과 같은 방향) — 기준정보 조회 역할 없는 관리자는 수동 id 입력으로 저하. 알림 종은 Q2 규칙으로 ERP 역할 보유자에게만 열리고, 없는 사람은 `degradedDomains∋erp` — 콘솔 후속이 «ERP 업무 권한 없음» 이면 그 갈래를 부르지 않게 |
| Q6 | read-model · 알림 귀속 | read-model `employees*` = MD_READ ∨ MD_WRITE · `approvals*` · `delegations*` = AP_READ ∨ AP_WRITE · 알림 = Q2 |
| 순서 | 콘솔 데이터 | **별도 콘솔 PR, 굽기 라이브 뒤**(792 와 같음 — `permission-map.ts` ERP `serverRequires/writeRequires` · 계약 § 2.4.3.4) |

# 소유자 결정 — 2026-10-15 UTC (코디네이터 경유 · § 5 추천 그대로)

| Q | 결정 |
|---|---|
| 역할 | `ERP_MASTERDATA_READ/_WRITE` · `ERP_APPROVAL_READ/_WRITE`. `ERP_OPERATOR` = auth 파생 진입 역할(`ENTRY_ROLES` 한 행) · 카탈로그에서 제거 · 진입 역할 위임 → 422. V0057 이 `ERP_OPERATOR` 를 담은 모든 세트 · 파트너십 scope 를 옮긴다(넓힌 뒤 지운다) |
| Q1 | 구독 합성 READ(`entitled_domains ∋ erp` ⇒ READ)를 **4 서비스 모두에서 걷는다** · `'*'` 와일드카드 GET 유지 · 기계 클라이언트 범위는 계속 통한다 |
| Q2 | 개인 엔드포인트(`/employees/me` · 연결 제안 mine/accept/decline · 본인 unlink · 알림 3) = ERP 업무 역할 하나라도(파생 `ERP_OPERATOR`) ∨ 범위 |
| Q3 | 결재 → 기준정보: `approver-ref` = Q2 규칙 · 대상 GET `/departments/{id}` · `/employees/{id}` = MD_READ ∨ MD_WRITE ∨ AP_WRITE |
| Q4 | 결재 테넌트 전체 목록 = `isOperator()` 무변경 |
| Q5 | IAM `org_scope` 부서 선택기 저하(수동 id) 수용 · 알림 종이 역할 없는 사용자에게 ERP 갈래를 건너뛰는 것은 콘솔 후속 티켓(793 을 실은 굽기 라이브 확인 뒤) — ERP 내비 `serverRequires/writeRequires` · 계약 § 2.4.3.4 표와 함께 |
| Q6 | read-model `employees*` = MD_READ ∨ MD_WRITE · `approvals*`/`delegations*` = AP_READ ∨ AP_WRITE · 알림 = Q2 |
