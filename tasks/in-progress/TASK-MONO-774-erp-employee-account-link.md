# Task ID

TASK-MONO-774

# Title

`ADR-MONO-080` D7 = **E1** — erp 직원 마스터가 IAM 계정을 안다(`employees.account_id`) · 결재함·자기결재·계약 E3 를 한 id 공간으로

# Status

in-progress

# Owner

monorepo

# Task Tags

- erp
- iam
- platform-console
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (교차 컨텍스트 참조 · 결재 권한 술어)

---

# Dependency Markers

- **선행**: 없음 — 단, AC-0 에서 «연결을 누가 쓰나» 를 **«D6 초대 수락의 부산물»** 로 고르면 `TASK-MONO-772` `done/` 이 선행이다.
- 관련: `TASK-PC-FE-311`(콘솔 «나 (현재 운영자)» 보정 — 이 티켓이 걷는다) · `TASK-PC-FE-309`

# Goal

«누가 결재자인가» 를 계약은 **직원**으로(`approval-api.md:253, 323-326`), 코드는 **계정 `sub`** 로(`ApprovalRequestJpaRepository.java:61-67`) 답한다. 직원 마스터에 계정 칸이 없고(`V1__init.sql:38-55`), 계약 E3(승인자 = 살아 있는 직원)를 코드가 지키지 않으며(`ApprovalApplicationService.java:122-146`), 자기결재는 다른 id 공간을 비교한다(`:92, 98`). 데모는 승인자에 운영자 `sub` 를 넣는 편법으로 결재함을 채운다(`seed-erp.sh:316-319`). E1 로 정리한다:

- `employees.account_id`(NULL 허용 · 테넌트 안 유니크 · FK 없음 — 쓰기 때 IAM 으로 검증)
- 결재함 술어 = «승인자 직원의 `account_id` = 내 `sub`» · 계약 `approverId` = 직원 id **유지**
- 상신 때 E3 집행(승인자 = 살아 있는 직원) · 자기결재 = 상신자의 직원(내 `sub` 로 푼 직원) vs 승인자 직원, **같은 id 공간**
- 상신자 · 이력 처리자 · 위임 피위임자 칸도 같은 모델(위임 한 행의 두 id 공간 `seed-erp.sh:393, 410`)

# Scope

## In Scope

- 계약 먼저: erp masterdata 직원 계약(`account_id` 필드 · 연결 쓰기 API) · `approval-api.md`(결재함 술어 서술 · E3 거절 코드 · 자기결재)
- erp masterdata: 마이그레이션 · 연결 쓰기(누가 쓰나 — AC-0) · IAM 계정 존재 검증
- erp approval: 결재함 · 상신 E3 · 자기결재 · 위임 · 이력 처리자
- 데모 시드 §6: 편법(승인자 = 운영자 `sub`) 대신 «운영자 계정과 연결된 직원» 을 승인자로 — 🔵 소유자 결정(2026-10-07)의 «시드가 모델을 몰래 정하지 않게» 는 모델이 정해졌으므로 이제 해소된다
- 콘솔: `TASK-PC-FE-311` 보정 걷기 · 직원 상세/목록에 «연결된 계정» 표시 · 연결 쓰기 화면(AC-0 결정에 따라)

## Out of Scope

- 다른 도메인(wms 작업자 · scm 담당자)의 사람 마스터 — ADR-080 Context 가 erp 만 쟀다. AC-0 에서 모집단만 세어 후속 티켓으로.

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정(위 file:line 전부) + 🔴 **소유자 결정: 연결을 누가 쓰나** — ⓐ 인사 담당(erp 권한) · ⓑ 본인 수락 · ⓒ D6 초대 수락의 부산물(772 선행) — 선택지와 추천을 내고 결정을 받는다. 다른 도메인 사람 마스터 모집단을 센다.
- [ ] **AC-1** — 🔴 «전» 상태를 먼저 단언: 계약대로 승인자 = 직원 id 로 상신하면 결재함이 0 이 된다(현재) → 구현 뒤 연결된 계정으로 결재함에 보인다.
- [ ] **AC-2** — E3: 살아 있지 않은 직원 · 존재하지 않는 직원을 승인자로 상신 → 거절.
- [ ] **AC-3** — 자기결재: 내 계정과 연결된 직원을 승인자로 → 거절(지금은 다른 id 공간이라 통과한다 — 그 «전» 도 단언).
- [ ] **AC-4** — 연결 없는 직원(`account_id` NULL)은 승인자로 지정될 수는 있으나 결재함에 나타날 사람이 없다는 것을 화면이 말한다(또는 상신 거절 — AC-0 에서 결정).
- [ ] **AC-5** — 데모 시드 재굽기 뒤 결재함이 편법 없이 채워지고, `TASK-PC-FE-311` 보정 코드가 제거돼도 화면이 이름을 보인다(라이브 ⚪ 가능).

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § 새 입력 · D7
- `docs/adr/ADR-MONO-060`(assume 토큰 `sub` = 계정 UUID)
- `projects/erp-platform/specs/contracts/http/approval-api.md` · `projects/erp-platform/specs/`(masterdata)

# Related Contracts

- erp `approval-api.md` · erp masterdata 직원 계약 · IAM 계정 조회(내부)

# Edge Cases

- 한 계정이 같은 테넌트의 직원 둘에 연결 — 테넌트 안 유니크로 막는다.
- 퇴사 직원(상태 비활성)과 그 계정 — 연결을 남기되 E3 가 막는다.
- 계정 삭제·잠금 — 연결은 남고 결재함에는 들어올 수 없다(로그인 불가).

# Failure Scenarios

1. 결재함 술어만 바꾸고 데모 시드를 안 바꾼다 — 데모 결재함이 0 이 된다.
2. 자기결재 비교를 한쪽만 직원으로 바꾼다 — 다른 id 공간 비교가 그대로 남는다.

---

# AC-0 기록 (2026-10-08 UTC · 기준 `origin/main` `4cd6c9c85`)

## 소유자 결정 (2026-10-08 UTC, 오케스트레이터 경유 — 원문 그대로)

1. «연결을 누가 쓰나» = **ⓑ 인사 제안 + 본인 수락**. An `erp.write` holder (within department data scope, same as other employee writes) PROPOSES «employee E ↔ account A»; the link takes effect only when the owner of account A (JWT `sub` = A) ACCEPTS it. Not ⓐ (HR alone) and not ⓒ (by-product of the D6 invite; so TASK-MONO-772 is NOT a prerequisite).
   - 🔴 Required consequence (orchestrator, reported to owner): the proposer and the accepting account must differ (two-person rule). Without it, an HR user proposes their OWN account for the CFO employee, accepts it themselves, and takes the CFO's approval inbox — ⓑ would collapse into ⓐ. Reject acceptance when acceptor `sub` == proposer `sub` (new error code). Add a test that fails without this rule (bite).
2. «미연결 결재자» (AC-4) = **상신 거절**: submitting with an approver employee whose `account_id` is NULL is rejected at submission with a new error code; the console approver picker marks unlinked employees «연결된 계정 없음». Demo seed links the approver employees to operator accounts so the inbox fills without the old hack (ticket Failure Scenario 1).

⇒ **선행** 표기 정정: ⓒ 가 아니므로 `TASK-MONO-772` 는 선행이 **아니다**.

## 재측정 — 티켓이 인용한 file:line 전부

| 인용 | 지금 그 자리 | 판정 |
|---|---|---|
| `approval-api.md:253` | `` `approverId` — required, the single-stage approver (employee id) `` | ✅ 일치 |
| `approval-api.md:323-326` | submit 2. «`approverId` must resolve to a live employee … approver unresolvable / not an eligible approver → `APPROVAL_NOT_AUTHORIZED_APPROVER`» | ✅ 일치 |
| `ApprovalRequestJpaRepository.java:61-67` | `findInboxPending` — `r.approverId = :approverId AND r.status IN (SUBMITTED, IN_REVIEW)` | ✅ 일치 |
| `ApprovalApplicationService.java:122-146` | `submit` — `masterDataPort.isSubjectActive(...)`(:131) 하나뿐, 승인자 안 봄 | ✅ 일치 |
| `ApprovalApplicationService.java:92, 98` | `ApprovalRoute.multiStage(actor.actorId(), …)` · `createDraft(…, actor.actorId(), now)` — 상신자 = `sub` | ✅ 일치 |
| `ApprovalApplicationService.java:247`(ADR 표) | `findInbox(actor.tenantId(), actor.actorId(), …)` | ✅ 일치 |
| masterdata `V1__init.sql:38-55` | `employees(id, tenant_id, employee_number, name, department_id, cost_center_id, job_grade_id, status, effective_from, effective_to, created_at, updated_at, version)` — 계정 칸 없음 | ✅ 일치 |
| `seed-erp.sh:316-319` | «approverId 는 참조 검증을 받지 않는다 … 승인자에 계정 UUID 를 넣을 수 있고, 그것이 결재함을 채우는 유일한 방법이다» | ✅ 일치 |
| `seed-erp.sh:21-22 · 51-52 · 150 · 380-385` | 결재함 = `actorId` = `sub` · 사원 승인자 안 씀 · `APPROVER_SUB=jwt_sub` · 상신 2건 승인자 = `$APPROVER_SUB` | ✅ 일치 |
| `seed-erp.sh:393, 410` | §7 «위임자는 호출자(`sub`)… 피위임자는 사원 id» · 바디 `delegateId` = 사원 id | ✅ 일치 |
| masterdata `architecture.md:712-716` | employee `POST/GET/GET/PATCH/retire` 행 — 쓰기 = JWT `erp.write` | ✅ 일치 |
| (추가) `DelegationApplicationService.java:55-58` | `DelegationGrant.create(…, actor.actorId(), cmd.delegateId(), …)` — 한 행에 `sub` + 직원 id | ✅ ADR 표의 위임 행과 같은 사실을 코드에서 |

## 티켓·ADR 이 안 센 것 (정적 실측)

1. 🔴 **알림도 같은 결함이다 — 계약에까지 적혀 있다.** `RecipientResolver.java:28-52` 는 수신자를 payload 의 `approverId`/`submitterId`/`delegateId`(직원 id)로 고르고, `QueryInboxUseCase.java:13-14` 는 `recipient_id == caller.sub` 로 거른다. `notification-api.md:29-31` 이 그 둘을 한 문장으로 붙였다: *"`recipient == caller.sub` (the employee id in the JWT `sub` claim)"*. ⇒ 결재 쪽만 직원 id 로 바꾸면 **알림함이 0 이 된다**(Failure Scenario 2 의 다른 얼굴). 이 PR 이 `notification-api.md` § v1.1 개정으로 술어를 «내 `sub` 와 연결된 직원» 으로 못박았다.
2. 🔴 **read-model 위임 scope 도 직원 id 를 전제한다.** `QueryDelegationFactUseCase.java:117-118` 이 `delegatorId` 를 `employee_proj` 로 풀어 부서 scope 를 건다(`read-model-subscriptions.md:121` 도 «an employee id»). 지금 저장되는 `delegatorId` 는 `sub` 라 아무 직원으로도 안 풀린다. 이 PR 은 계약을 안 바꾼다(계약이 이미 맞다) — 코드가 계약을 따라오면 저절로 풀린다. ⚪ 런타임 영향은 재지 않았다.
3. 🔴 **직원 상세 조회는 그 직원의 부서로 data scope 를 건다**(`MasterdataApplicationService.java:323` `authorize(actor, READ, e.getDepartmentId())`). 승인자는 보통 상신자의 scope 밖(상위 부서)에 있으므로, E3 를 기존 상세로 풀면 정상 결재선이 «승인자 확인 불가» 로 거절된다 ⇒ 계약에 scope 없는 최소 조회 `GET /employees/{id}/approver-ref` 를 넣었다. (목록 `GET /employees` 는 `READ, null` — scope 를 안 건다: `:331`.)
4. 🔴 **erp → IAM 계정 확인 배선이 없다.** account-service `/internal/**` 는 `internal.invoke` 스코프 토큰만 받는다(`SecurityConfig.java:59, 128`). erp 의 유일한 OAuth 클라이언트 `erp-platform-internal-services-client` 는 `erp.read`/`erp.write` 뿐이다(`iam-integration.md:61`). 선례: `V0036__seed_product_service_workload_client.sql`(ecommerce product-service 전용 워크로드 클라이언트). ⇒ 제안 시점 IAM 확인은 **iam 마이그레이션 + erp 어댑터 + compose/데모 배선**을 부른다 — 분할 S2 의 판정 항목.
5. 🔵 **access token 에 `email_verified` 가 없다.** auth-service 에서 그 이름은 UserInfo 매퍼에만 있다(`OidcUserInfoMapper.java:88`); 토큰 커스터마이저가 싣는 클레임은 `tenant_id`/`tenant_type` 계열. ⇒ R1 질문은 § 열린 항목.
6. 🔵 콘솔의 erp masterdata 쓰기는 지금 **부서뿐**이다(`console-integration-contract.md:1976-1979` create · retire · move-parent). 직원 쓰기는 없다. `iam-integration.md:120` 은 아직 «write/mutation 미소비» 라고 적는다(부서 쓰기 이후 낡은 문장 — 이 PR 은 안 고쳤다). 제안·수락은 콘솔의 **첫 직원 쓰기**다 ⇒ 콘솔 슬라이스가 콘솔 계약 § 2.4.8 에 행을 더하고 그 낡은 문장도 함께 고친다.
7. 🔵 `TASK-PC-FE-311`(«나 (현재 운영자)» 보정)은 아직 `review/` 다 — 코드: `approval-refs.tsx:70-79` · `ApprovalScreen.tsx:65` · `ApprovalDetail.tsx:34` · `ErpApprovalScreen.tsx:19` · `erp-state.ts:32, 243`. 걷기는 콘솔 슬라이스가 그 티켓의 닫힘 상태를 보고 한다.

## 다른 도메인의 «사람 마스터» 모집단 (Out of Scope — 세기만)

술어: 서비스 마이그레이션의 `CREATE TABLE` 중 «사람(작업자·담당자·직원·사용자)을 도메인 쪽 행으로 두는 표», 소비자 프로필과 IAM 자신은 제외.

| 프로젝트 | 표 | IAM 계정 칸 | 비고 |
|---|---|---|---|
| erp | `employees` (`masterdata V1:38`) | 없음 | ← 이 티켓 |
| wms | `admin_user` (`wms admin-service V1__init.sql:33`) | **없음** — `email`(소문자 유니크) · `user_code` · `name` · `status` | **1** |
| scm · finance · fan | — | — | 0 (finance `account_id` 는 원장 계좌) |
| ecommerce | `user_profiles` | — | 소비자 프로필 — 직원 마스터 아님, 제외 |

🔵 wms 의 사람 칸(`*_by` · `actor_id`, 15 파일 44 곳)은 JWT 행위자를 **그대로** 적는다 — 도메인 사람 마스터를 거치지 않으므로 «두 id 공간» 이 없다. ⇒ 다른 도메인 모집단 = **wms `admin_user` 하나**. 후속(이 티켓에서 기안하지 않음 — 이름만): **«wms `admin_user` ↔ IAM 계정 — 연결이 필요한가 판정»** (AC-0 = wms 의 어떤 결정 경로가 `admin_user.id` 를 JWT `sub` 와 비교하는가를 먼저 센다; 0 이면 연결 불필요로 닫는다).

## 🔴 STOP — 한 PR 이 감당할 크기가 아니다 (분할 제안)

실측한 범위: iam(워크로드 자격 · 선택) · erp masterdata(표 2 · 엔드포인트 8 · 두 사람 규칙) · erp approval(사람 칸 전부 · E3 · 미연결 · 위임) · erp notification(수신자 술어 — 위 1) · 데모 시드 §6·§7·§9(제안 → 수락을 두 토큰으로) · 콘솔(목록·상세·제안·수락·승인자 선택·311 걷기 + 콘솔 계약 개정). 착수 지시의 STOP 조항(«contracts + AC-0 뒤 멈추고 분할을 보고») 에 따라 **이 PR = S1** 이다.

| 슬라이스 | 담는 것 | 닫는 AC | 선행 |
|---|---|---|---|
| **S1 (이 PR)** | AC-0 · 계약 먼저: `masterdata-api.md` § Employee ↔ IAM account link · `approval-api.md` § v2.4 · `notification-api.md` § v1.1 · 오류 코드 7개 등록(`platform/error-handling.md` · `rules/domains/erp.md`) | AC-0 | — |
| **S2 masterdata 연결 모델** | V3 마이그레이션(`employees.account_id` + `(tenant_id, account_id)` 유니크 · `employee_account_link_proposals` + 직원당 PENDING 하나 생성 열 유니크) · `accountId` 응답 · `/employees/me` · `/approver-ref` · 제안/조회/수락/거절/철회/해제 · 🔴 두 사람 규칙 bite 테스트 · IAM 확인(아래 판정 ①) | (AC-4 의 데이터 전제) | S1 |
| **S3 approval + notification + 시드** | `MasterDataPort` 확장(`me` · `approver-ref`) · 사람 칸 전부 직원 id · E3 · `APPROVAL_APPROVER_UNLINKED` · `APPROVAL_ACTOR_NOT_LINKED` · inbox `meta.actorEmployeeId` · 위임 · 알림 수신자 술어 · 🔴 AC-1·AC-3 «전» 단언(옛 술어로 먼저 빨강/통과를 재고 바꾼다) · **데모 시드를 같은 PR 에**(Failure Scenario 1 — 술어만 바꾸면 다음 굽기에서 결재함 0): §6 승인자 = 연결된 직원, 연결은 `demo@` 토큰 제안 → `requester@` 토큰 수락 / 그 반대(두 사람 규칙을 시드도 지킨다) · §9 등식을 «승인자 직원 id» 로 · 기존 행 처리 판정(아래 ②) | AC-1 · AC-2 · AC-3 · AC-4(거절) · AC-5(시드 절반) | S2 |
| **S4 콘솔** | 콘솔 계약 § 2.4.8 개정(첫 직원 쓰기) · 직원 목록/상세 «연결된 계정» · 제안 화면(`erp.write`) · 수락 화면(내 앞 제안) · 승인자 선택기 «연결된 계정 없음» · 결재함 빈 이유 문구(`meta.actorEmployeeId` 부재) · `TASK-PC-FE-311` 보정 걷기 · e2e 스펙 grep | AC-4(화면) · AC-5(콘솔 절반, 라이브 ⚪ 재굽기) | S2 · S3 |

### 슬라이스가 판정할 것 (이 PR 은 정하지 않는다)

- ① **제안 시점 IAM 계정 확인** — (a) iam 에 erp 워크로드 클라이언트(`internal.invoke`) 마이그레이션 + `WorkloadRoleCatalog` 빈 맵 + erp 어댑터 + compose/데모 배선 · (b) 하지 않는다: 연결이 쓰이는 순간(수락)은 IAM 이 서명한 토큰의 `sub` 자체가 계정 실재의 증거이고, 오타 제안은 아무도 수락할 수 없어 철회로 치운다. 계약은 (a) 의 단계를 적어 두었다(`masterdata-api.md` 제안 4번). 🔵 구현자 의견: (b) — 티켓 문장 «쓰기 때 IAM 으로 검증» 의 «쓰기» 는 ⓑ 모델에서 수락이고, 그 순간은 이미 검증된다. 결정은 소유자.
- ② **기존 결재·위임·알림 행**(사람 칸에 계정 UUID) — 자동 이전 불가(approval DB 는 연결을 모른다). 데모는 신선 볼륨 재시드. 운영 데이터가 없다는 것을 S3 AC-0 이 재고 적는다.

## 열린 항목

- 🔵 **R1(인증된 이메일)을 수락에 거는가** — erp 가 알 길이 지금 없다: access token 에 `email_verified` 없음(위 5), IAM 조회는 워크로드 자격 배선이 필요(위 4). 만들지 않았다. 🔵 구현자 판단: 수락자는 이미 이 테넌트의 erp 참여자여야 하고(수락 = `erp.read` 이상), 그 운영자 측면이 붙는 순간이 `ADR-MONO-080` D3 가 무는 자리다(770 · 772). 연결은 **새 회사 권한을 붙이지 않고** 이미 붙은 측면 안에서 «어느 직원인가» 를 정한다 — 그래서 게이트는 전이적으로 성립한다. 단 772 가 `done/` 이 되기 전에는 그 전이가 아직 없다. 결정은 소유자.
- 🔵 S2~S4 티켓 기안 · ID 할당은 오케스트레이터 몫(동시 세션 ID 충돌 회피 — 이 세션은 ID 를 잡지 않았다).
