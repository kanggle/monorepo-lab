# Task ID

TASK-MONO-776

# Title

erp 사람 칸을 직원 id 한 공간으로 — approval(결재함 · E3 · 자기결재 · 위임 · 이력) · notification(수신자) · read-model(위임 scope) · 데모 시드 §6/§7/§9 를 **한 PR** 로 (`TASK-MONO-774` S3)

# Status

done

# Owner

monorepo

# Task Tags

- erp
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 결재 권한 술어 · 세 서비스 동시 이동 · 시드가 같은 PR 이어야 하는 이유(Failure Scenario 1).

---

# Dependency Markers

- **선행**: `projects/erp-platform` `TASK-ERP-BE-044` **`done/`** (masterdata `GET /employees/me` · `/employees/{id}/approver-ref` · 연결 제안/수락 — 이 티켓의 코드와 시드가 그 표면을 부른다).
- **후속**: `projects/platform-console` `TASK-PC-FE-318`(S4 — `meta.actorEmployeeId` · 미연결 표시 · `TASK-PC-FE-311` 보정 걷기).
- 상위: 루트 `TASK-MONO-774`(우산 · `ADR-MONO-080` D7 = E1). 🔵 루트 티켓인 이유: 한 PR 이 erp 세 서비스 + `infra/demo/seed/` 를 함께 바꾼다(교차 표면 — 시드는 프로젝트 밖).

# Goal

계약(`approval-api.md` § v2.4 · `notification-api.md` § v1.1)이 이미 말하는 대로, erp 의 모든 «사람» 칸에 **직원 id** 만 들어가고 호출자 쪽은 «내 `sub` 와 연결된 직원» 으로 푼다. 지금 코드는 그 칸에 계정 `sub` 를 넣는다 — 그래서 계약 E3 · 자기결재 · 위임 · 알림 · read-model 위임 scope 가 **다른 id 공간**을 비교한다(`TASK-MONO-774` AC-0 실측).

# Scope

## In Scope

**approval-service** (`projects/erp-platform/apps/approval-service/`)
- `MasterDataPort` 확장 — `me()`(호출자의 직원: id · status) · `approverRef(employeeId)`(id · status · accountId?). 어댑터는 `MasterDataRestAdapter.java:106-150` 의 호출자 토큰 전파 · 상태 분류 · 원인별 계측을 **그대로** 따른다(404 = 답, 그 외 = «물어보지 못함» 계측 후 거절).
- create — `ApprovalApplicationService.java:92, 98`: 상신자 = 호출자의 직원(없으면 403 `APPROVAL_ACTOR_NOT_LINKED`), `ApprovalRoute.multiStage` 의 자기결재 비교가 직원 id 끼리가 된다.
- submit — `:122-146`: subject E1 다음 단계마다 E3(`approver_unresolved` 422) · 미연결(`APPROVAL_APPROVER_UNLINKED` 422, `details.stageIndex`).
- approve / reject — `:160, 189` → `resolveActingApprover` `:340-369`: 행위자 = 호출자의 직원; 위임 SoD 도 직원 id.
- withdraw — `:208`: 호출자의 직원 == `submitterId`.
- list `?role=` — `:238-239` · inbox — `:247` + `ApprovalRequestJpaRepository.java:61-75`: 참여자/승인자 = 호출자의 직원 id; 미연결 → 빈 페이지 + `meta.actorEmployeeId` ABSENT.
- 위임 — `DelegationApplicationService.java:55-58`(위임자 = `actor.actorId()`), `:100-105`(목록): 위임자 = 호출자의 직원; `delegateId` ACTIVE 검사(`DELEGATION_INVALID` `delegate_unresolved`).
- 이력 `history[].actor` = 직원 id; `audit_log.actor` = `sub` 유지(계약 v2.4).

**notification-service** — `RecipientResolver.java:28-52`(수신자 = payload 직원 id, 그대로) vs `QueryInboxUseCase.java:13-14, 26-39`(`recipient == caller.sub`) → 술어를 «호출자의 직원» 으로(masterdata `/employees/me`, 호출자 토큰 전파). 미연결 = 빈 inbox · detail/mark-read 404.

**read-model-service** — `QueryDelegationFactUseCase.java:117-118`: `delegatorId` 를 `employee_proj` 로 풀어 부서 scope 를 건다. 위임자가 직원 id 가 되면 저절로 풀리는지 **IT 로 확인**(지금은 `sub` 라 아무 직원으로도 안 풀린다 — AC-0 에서 «전» 을 잰다).

**데모 시드** — `infra/demo/seed/seed-erp.sh`
- §4 직원(`:283-295`) 뒤에 연결 단계 신설: 승인자 직원 ↔ `demo@demo.com` 계정은 **`requester@` 토큰이 제안 · `demo@` 토큰이 수락**, 상신자 직원 ↔ `requester@` 계정은 **`demo@` 토큰이 제안 · `requester@` 토큰이 수락** — 시드도 두 사람 규칙을 지킨다. 두 토큰 모두 erp.write + 그 직원 부서 data scope 가 있는지 AC-0 에서 잰다(없으면 제안자를 바꾼다 — 같은 계정이 제안·수락하는 배치는 금지).
- §6(`:313-388`): 승인자 = **연결된 직원 id**. 편법 주석(`:316-319`)과 헤더(`:15-60` «승인자에 사원 마스터 id 를 쓰지 않는다») 를 새 모델로 다시 쓴다. 초안 1건(사원 승인자)은 «연결 없는 직원 → 상신 거절» 을 보여주도록 미연결 직원으로 둘지 AC-0 에서 정한다.
- §7(`:393-419`): 위임자 = 호출자의 직원(바디 그대로), 피위임자 = 연결된 직원.
- §9(`:456-499`): 등식의 «승인자» 를 `APPROVER_SUB` 가 아니라 **승인자 직원 id** 로. 추가 읽기 검증: 연결 단계 뒤 `GET /employees/me`(두 토큰)가 기대한 직원을 돌려주는지 — 이 스크립트의 «추출 0건 = 계측 실패» 규율 그대로.

## Out of Scope

- 콘솔 — `TASK-PC-FE-318`.
- masterdata 표면 자체 — `TASK-ERP-BE-044`.
- 다른 도메인 사람 마스터(wms `admin_user`) — `TASK-MONO-774` § 후속 후보.

# Acceptance Criteria

- [x] **AC-0** — 재측정(위 file:line 전부 · `seed-erp.sh` 절 번호) + 🔴 **기존 행 처리 결정**: approval / delegation / notification DB 의 사람 칸에 계정 UUID 가 저장된 행(이 PR 이전 생성분)을 어떻게 하나 — 선택지(그대로 둔다: 새 술어로는 아무 결재함에도 안 보인다 / 일회성 이전: 연결 표로 `sub → 직원` 을 풀 수 있는 행만 / 데모는 신선 볼륨 재시드로 충분) + 운영 데이터 모집단 실측 → 소유자 결정. 시드 두 토큰의 erp.write · data scope 실측. → § AC-0 기록.
- [x] **AC-1** — 🔴 «전» 을 먼저 단언: 계약대로 승인자 = 직원 id 로 상신한 건이 **현재 술어**에서 결재함 0 임을 테스트로 고정(빨강/초록 기록) → 구현 뒤 그 직원과 연결된 계정의 결재함에 보인다. → § 구현 기록 «전/후».
- [x] **AC-2** — E3: 없는 직원 · `RETIRED` 직원을 승인자로 상신 → 422 `APPROVAL_ROUTE_INVALID`(`approver_unresolved`), 요청은 `DRAFT` 그대로. 승인자 해소가 401/5xx 면 «물어보지 못함» 계측 + 거절.
- [x] **AC-3** — 🔴 자기결재: «내 계정과 연결된 직원» 을 승인자로 → 422 `self_approval`. «전» 단언: 현재 코드에서는 같은 입력이 **통과**함을 먼저 테스트로 보인다.
- [x] **AC-4** — 미연결 승인자로 상신 → 422 `APPROVAL_APPROVER_UNLINKED`(`details.stageIndex`). 미연결 호출자의 create/approve/reject/위임 생성 → 403 `APPROVAL_ACTOR_NOT_LINKED`; inbox · `?role=` → 200 빈 페이지 + `meta.actorEmployeeId` ABSENT.
- [x] **AC-5** — 위임: 위임자 = 내 직원, 피위임자 직원의 연결 계정이 대결 승인 가능; 피위임자 = 상신자 직원이면 SoD 거절(직원 id 비교).
- [x] **AC-6** — 알림: `APPROVAL_SUBMITTED` 알림이 승인자 직원과 연결된 계정의 inbox 에 보이고, 다른 계정에는 404. (단위 초록 · IT 는 ⚪ CI 첫 실행)
- [x] **AC-7** — read-model: 직원 id 위임자를 가진 위임 사실이 위임자 부서 scope 안 운영자에게 보이고 밖 운영자에게 안 보인다(«전»: `sub` 위임자는 scope 판정이 안 됨을 먼저 잰다). (단위 초록 · IT 는 ⚪ CI 첫 실행)
- [x] **AC-8** — 시드: 정적 검증(`bash -n` · 시드 하네스/가드가 있으면 그것) 통과. 🔵 라이브(재굽기 뒤 결재함이 편법 없이 찬다 · §9 등식 · `/me` 읽기 검증)는 ⚪ 재굽기 창.
- [x] **AC-9** — 각 서비스 단위 · 슬라이스 통과; IT 작성(Docker 없는 호스트면 ⚪ «CI 첫 실행»).

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § 새 입력 · D7
- 루트 `tasks/in-progress/TASK-MONO-774-erp-employee-account-link.md` § AC-0
- `projects/erp-platform/specs/services/approval-service/architecture.md` · `notification-service/architecture.md` · `read-model-service/architecture.md` (사람 칸 서술을 같은 PR 에서 갱신)
- `infra/demo/seed/README.md`(시드 규약)

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` § v2.4
- `projects/erp-platform/specs/contracts/http/notification-api.md` § v1.1
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link (`/me` · `/approver-ref`)
- `projects/erp-platform/specs/contracts/events/erp-approval-events.md` · `read-model-subscriptions.md:121` · `notification-subscriptions.md` (이미 직원 id 라고 적는다 — 바뀌지 않는다)

# Edge Cases

- 호출자가 연결은 됐지만 그 직원이 `RETIRED` — 상신자/승인자로 행동 가능한가: `/me` 가 `RETIRED` 를 돌려주므로 거절(403 `APPROVAL_ACTOR_NOT_LINKED` 를 쓸지 별도 원인을 둘지 AC-0 에서 계약과 대조).
- 결재 진행 중 승인자 직원의 연결이 해제됨 — 그 건은 아무 결재함에도 안 보인다(위임으로 처리 가능). 상신 시점 검사만 있다는 것을 화면 쪽(S4)에 넘긴다.
- 연결 대상 계정이 잠김 — 토큰을 못 받으므로 결재함 진입 불가(연결은 남음).

# Failure Scenarios

1. 결재함 술어만 바꾸고 데모 시드를 안 바꾼다 — 다음 굽기에서 데모 결재함이 0 이 된다(그래서 시드가 같은 PR 이다).
2. 자기결재 비교를 한쪽만 직원으로 바꾼다 — 다른 id 공간 비교가 그대로 남는다(AC-3 «전/후»).
3. approval 만 직원 id 로 바꾸고 알림 술어를 그대로 둔다 — 알림함이 0 이 된다(`notification-api.md` § v1.1).
4. 시드가 같은 계정으로 제안하고 수락한다 — 두 사람 규칙 때문에 403 이고, 그 실패가 «결재함 0» 으로만 보인다.

---

# AC-0 기록 (2026-10-07 UTC · 기준 `origin/main` `cf506f6fa`)

## 소유자 결정 — 기존 행 처리 (2026-10-07 UTC, 오케스트레이터 경유 — 원문 그대로)

> «기존 행 처리» = **그대로 두고 데모는 재시드**. Rows created before this PR that hold account UUIDs in person fields (approval / delegation / notification) are left as they are; under the new predicate they appear in no inbox. Write that consequence into the contracts (approval-api.md / notification-api.md — a short «이전 데이터» note) and in the ticket. No migration code. Add only a read-only way to COUNT such rows for the next demo window (a SQL snippet in the ticket is enough) — do not build an endpoint for it.
>
> Orchestrator note for the record: a Flyway migration that maps `sub → employee` via the link table would resolve zero rows anyway, because links are created by propose/accept after deployment — the table is empty when the migration runs.

반영: `approval-api.md` § v2.4 «이전 데이터» · `notification-api.md` § v1.1 «이전 데이터» · `read-model-service/architecture.md`(위임 사실 · 이전 grant 는 bounded scope 에서 안 보임). 마이그레이션 코드 없음.

### 운영 데이터 모집단 — 다음 데모 창에서 셀 읽기 전용 SQL

🔴 masterdata 의 직원 id 는 **맨 UUIDv7** 이다(`MasterdataApplicationService.java:281` `Employee.create(UuidV7.randomString(), …)`) — 계정 UUID 와 **모양이 같다**. 그래서 «UUID 모양이면 이전 행» 같은 형식 술어는 0 을 낸다(거짓 0). 판정은 «직원 표에 없는 사람 값» 이어야 한다. 세 DB 가 같은 MySQL 인스턴스(`erp-platform-mysql`)에 있으므로 교차 스키마 `NOT IN` 으로 센다:

```sql
-- 이전 행 = 사람 칸 값이 erp_db.employees 의 어떤 id 도 아닌 행 (읽기 전용)
SELECT 'approval_request' AS t, COUNT(*) FROM erp_approval_db.approval_request
 WHERE submitter_id NOT IN (SELECT id FROM erp_db.employees)
    OR approver_id  NOT IN (SELECT id FROM erp_db.employees)
UNION ALL
SELECT 'approval_route_stage', COUNT(*) FROM erp_approval_db.approval_route_stage
 WHERE approver_id NOT IN (SELECT id FROM erp_db.employees)
UNION ALL
SELECT 'approval_action', COUNT(*) FROM erp_approval_db.approval_action
 WHERE actor NOT IN (SELECT id FROM erp_db.employees)
UNION ALL
SELECT 'delegation_grant', COUNT(*) FROM erp_approval_db.delegation_grant
 WHERE delegator_id NOT IN (SELECT id FROM erp_db.employees)
    OR delegate_id  NOT IN (SELECT id FROM erp_db.employees)
UNION ALL
SELECT 'notification', COUNT(*) FROM erp_notification_db.notification
 WHERE recipient_id NOT IN (SELECT id FROM erp_db.employees);
```

🔵 술어의 한계(적어 둔다): 이 PR 이후에도 «없는 직원 id» 를 승인자로 넣은 **초안**(DRAFT — create 는 승인자를 안 본다)은 이 술어에 걸린다. 이전 데이터와 섞이지 않게 하려면 `approval_request` 줄에 `AND status <> 'DRAFT'` 를 붙여 따로 세라. ⚪ **실측하지 않았다** — 이 호스트에는 데모 스택이 없다(Docker/Testcontainers 불가). 데모 볼륨은 이 PR 이후 신선 볼륨으로 재시드하므로 그 볼륨에서의 기대값은 0 이다(다음 창에서 위 SQL 로 확인).

## 재측정 — 티켓이 인용한 file:line

| 인용 | 지금 그 자리 (`cf506f6fa`) | 판정 |
|---|---|---|
| `MasterDataRestAdapter.java:106-150` | `isSubjectActive` — 토큰 전파 · tenant 대조 · 상태 분류 · 404=답 · 원인별 계측 | ✅ |
| `ApprovalApplicationService.java:92, 98` | `multiStage(actor.actorId(), …)` · `createDraft(…, actor.actorId(), now)` | ✅ |
| `:122-146` (submit) | E1 subject 검사만, 승인자 안 봄 | ✅ |
| `:160, 189` → `:340-369` | approve/reject 의 `resolveActingApprover(…, actor, …)` — `actor.actorId()` 로 위임 해소 · SoD | ✅ |
| `:208` (withdraw) | `request.withdraw(actor.actorId(), …)` | ✅ |
| `:238-239` · `:247` | `findByParticipant(…, actor.actorId(), …)` · `findInbox(…, actor.actorId(), …)` | ✅ |
| `ApprovalRequestJpaRepository.java:61-75` | `findInboxPending`/`countInboxPending` — `approverId = :approverId AND status IN (SUBMITTED, IN_REVIEW)` | ✅ — 🔵 **술어 자체는 그대로 둔다**: 바뀌는 것은 넘기는 id(`sub` → 호출자의 직원) |
| `DelegationApplicationService.java:55-58` · `:100-105` | 위임자 = `actor.actorId()` · 목록 = `actor.actorId()` | ✅ |
| `RecipientResolver.java:28-52` | 실제 경로 `domain/recipient/RecipientResolver.java:28-53` — payload 직원 id 그대로 | ✅ (경로만 정정) |
| `QueryInboxUseCase.java:13-14, 26-39` | `recipient_id == caller.sub` (컨트롤러 `recipient(jwt) = jwt.getSubject()`) | ✅ |
| `QueryDelegationFactUseCase.java:117-118` | `employeeRepository.findById(fact.delegatorId())` → 부서 | ✅ — 코드 변경 불필요(이미 직원 id 전제) |
| `seed-erp.sh` §4 `:283-295` · §6 `:313-388` · `:316-319` · 헤더 `:15-60` · §7 `:393-419` · §9 `:456-499` | §4 `:282-297` · §6 `:312-390` · 편법 주석 `:316-319` · 헤더 «사원 id 안 씀» `:51-54` · §7 `:392-418` · §9 `:455-498` | ✅ (±1~2줄) |

## 시드 두 토큰의 erp.write · data scope (정적 실측)

- 두 토큰 다 `operator_token demo-corp` — base 토큰 scope `… erp.write`(`infra/demo/seed/lib.sh:222`) → assume-tenant 교환.
- 두 운영자 모두 `operator_tenant_assignment(…, 'demo-corp', …, permission_set_id NULL)` · org_scope NULL = **테넌트 전체**(`R__seed_demo_operator.sql:90-92` demo-operator, `:213-215` demo-requester), ERP_OPERATOR 는 demo-corp 구독에서 파생(`:183-186` 주석). ⇒ 두 토큰은 같은 모양이다.
- masterdata WRITE = `erp.write ∨ isOperator()`(`RoleScopeAuthorizationAdapter.java:95`), 부서 scope 는 `isPlatformScope()` 면 통과(`:105`).
- 라이브 정황 증거: demo@ 토큰은 지금도 §4 사원 생성(부서 scope 걸린 쓰기)을 통과하고, requester@ 토큰은 BE-041 이후 EMPLOYEE subject 상신(직원 상세 = 부서 scope 걸린 읽기)을 통과했다(`seed-erp.sh` 헤더 BE-041 기록).
- ⇒ **배치 그대로**(김본부 ↔ demo@ = requester 제안 · demo 수락 / 이운영 ↔ requester@ = demo 제안 · requester 수락). ⚪ 라이브 확인은 재굽기 창 — 시드의 §4b 가 제안/수락 HTTP 를 그대로 `seed_fail` 메시지에 싣는다.

## 판정 (AC-0 이 정한 것)

- **Edge «연결됐지만 RETIRED»**: 쓰기(create/submit/approve/reject/withdraw/위임 생성) = 403 `APPROVAL_ACTOR_NOT_LINKED`(같은 코드 — 계약 문장 «no linked employee» 의 범위로 읽음, 새 원인 안 둠). 읽기(결재함 · `?role=`) = 그 직원 id 로 그대로 본다. 계약 § v2.4 에 적었다.
- **«호출자의 직원» 을 물어보지 못함**(`/me` 401/403/5xx): 503 `SERVICE_UNAVAILABLE`(platform-common, 등록돼 있음) — 결재·알림 둘 다. «연결 없음» 으로 접지 않는다. 계약에 적었다.
- **초안(사원 승인자)**: 연결 없는 사원(박재무)으로 둔다 — 면접관이 상신하면 422 `APPROVAL_APPROVER_UNLINKED`.
- **STOP 조항**: 해당 없음 — read-model 은 코드 변경 0(IT 만), notification 은 포트 하나 + 술어 교체로 한 PR 에 들어왔다. 승인 술어와 시드는 같은 커밋이다(Failure Scenario 1).

---

# 구현 기록 (2026-10-07 UTC)

## «전/후» — AC-1 · AC-3 · AC-7

| AC | «전» 테스트 (옛 코드) | 결과 | 같은 테스트를 새 코드에서 | 결과 |
|---|---|---|---|---|
| AC-1 | `PersonIdSpaceBeforeTest.before_inboxOfTheLinkedAccountIsEmptyWhenApproverIsAnEmployeeId` — 승인자 `emp-approver` 로 상신, `acc-approver` 결재함 `totalElements == 0` 단언 | 🟢 초록(결함 고정) | 연결 정보만 포트에 알려 주고(옛 코드는 포트를 안 불렀다) 단언 그대로 | 🔴 `expected: 0L but was: 1L` |
| AC-3 | `…before_approvingWithMyOwnLinkedEmployeeIsAccepted` — `acc-me` 가 `emp-me` 를 승인자로 create → DRAFT, `submitterId == acc-me` | 🟢 초록 | 동일 | 🔴 `ApprovalRouteInvalidException: self-approval is forbidden: submitter 'emp-me' …` |
| AC-7 | `QueryDelegationFactUseCaseTest.ac7_before_accountSubDelegatorCannotBeScopedEvenForTheLinkedEmployeesDepartment` | 🟢 초록 (옛 코드 = 새 코드: read-model 은 안 바뀐다) | — | — |

🔵 «전» 파일은 기록 뒤 지웠다(새 코드에서 영구 빨강) — 「후」 는 `PersonIdSpaceTest`(11) · `PersonIdSpaceIntegrationTest`(13, ⚪). 🔴 «후» 테스트를 옛 코드에 돌리는 것은 **표현 불가**였다: 새 포트 메서드(`callerEmployee`/`approverRef`)와 `inbox` 반환형(`ApprovalInboxView`)이 옛 코드에 없어 컴파일이 안 된다 — 그래서 방향을 뒤집어 «전» 단언을 새 코드에 돌렸다(위 표 오른쪽).

## 검증

- `:approval-service:test` · `:notification-service:test` · `:read-model-service:test` rc=0 — JUnit XML 23/23/28 파일, 210/139/178 tests, 실패·오류·스킵 0. (`:masterdata-service:compileTestJava` 도 함께 rc=0 — 코드 변경 없음.)
- ⚪ IT(`@Tag("integration")`) 작성만, 미실행 — 이 호스트에 Docker 없음 ⇒ CI 첫 실행: `PersonIdSpaceIntegrationTest`(approval, MySQL) · `RecipientIsLinkedEmployeeIntegrationTest`(notification) · `DelegationFactProjectionIntegrationTest.ac7_…`(read-model). 기존 approval/notification IT 는 masterdata 스텁에 `/employees/me` · `/approver-ref` 를 «같은 id 규약»(sub `emp-x` ↔ 직원 `emp-x`)으로 추가해 의미를 유지했다 — 🔴 그 규약 아래서는 두 id 공간이 겹치므로 결함을 못 본다; 그래서 새 IT 는 `acc-*`/`emp-*` 를 따로 등록한다.
- 시드: `bash -n infra/demo/seed/seed-erp.sh` rc=0. 시드 전용 하네스/가드: `scripts/` 에 `seed-erp` 를 읽는 가드 0건(grep). ⚪ 라이브(§4b 제안→수락 · `/me` 두 토큰 · §9 등식 + `meta.actorEmployeeId`) = 재굽기 창.

# 닫기 — 4차원 검증 (2026-10-08 UTC, `date -u` 실측)

- (a) PR **#4234** `state=MERGED` · (b) `origin/main` 에 스쿼시 **`64d6070e6`** · (c) 머지 시점 `statusCheckRollup` 실패 **0**.
- (d) AC-1~9 `[x]`. AC-8 의 라이브 ⚪ 를 24차 데모 창(ami-01f1b4b56e4f9e51a · 1c8e203aa · 인스턴스 i-0445d76661ef0013d) 에서 쟀다(`/domain/start erp` 후 시드 출력):
  - §4b 연결 2건(김본부↔demo@ · 이운영↔requester@, 제안→수락) · `/me` 두 토큰 = 각 직원 id · 결재함 **2 = 대기 행 2**(승인자 사원 ↔ sub) · 시드가 `meta.actorEmployeeId` = 승인자 사원임을 단언(실패 시 `seed_fail`) · 요약 **생성 22 · 실패 0**.
  - § «운영 데이터 모집단» SQL(읽기 전용) — 이전 행 **0**: approval_request 0 (DRAFT 제외판도 0) · approval_route_stage 0 · approval_action 0 · delegation_grant 0 · notification 0. 분모(빈 표의 0 이 아님): 직원 4 · 결재 3 · 결재선 3 · 행위 2 · 위임 1 · 알림 3.
