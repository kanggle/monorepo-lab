# Task ID

TASK-MONO-776

# Title

erp 사람 칸을 직원 id 한 공간으로 — approval(결재함 · E3 · 자기결재 · 위임 · 이력) · notification(수신자) · read-model(위임 scope) · 데모 시드 §6/§7/§9 를 **한 PR** 로 (`TASK-MONO-774` S3)

# Status

ready

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

- [ ] **AC-0** — 재측정(위 file:line 전부 · `seed-erp.sh` 절 번호) + 🔴 **기존 행 처리 결정**: approval / delegation / notification DB 의 사람 칸에 계정 UUID 가 저장된 행(이 PR 이전 생성분)을 어떻게 하나 — 선택지(그대로 둔다: 새 술어로는 아무 결재함에도 안 보인다 / 일회성 이전: 연결 표로 `sub → 직원` 을 풀 수 있는 행만 / 데모는 신선 볼륨 재시드로 충분) + 운영 데이터 모집단 실측 → 소유자 결정. 시드 두 토큰의 erp.write · data scope 실측.
- [ ] **AC-1** — 🔴 «전» 을 먼저 단언: 계약대로 승인자 = 직원 id 로 상신한 건이 **현재 술어**에서 결재함 0 임을 테스트로 고정(빨강/초록 기록) → 구현 뒤 그 직원과 연결된 계정의 결재함에 보인다.
- [ ] **AC-2** — E3: 없는 직원 · `RETIRED` 직원을 승인자로 상신 → 422 `APPROVAL_ROUTE_INVALID`(`approver_unresolved`), 요청은 `DRAFT` 그대로. 승인자 해소가 401/5xx 면 «물어보지 못함» 계측 + 거절.
- [ ] **AC-3** — 🔴 자기결재: «내 계정과 연결된 직원» 을 승인자로 → 422 `self_approval`. «전» 단언: 현재 코드에서는 같은 입력이 **통과**함을 먼저 테스트로 보인다.
- [ ] **AC-4** — 미연결 승인자로 상신 → 422 `APPROVAL_APPROVER_UNLINKED`(`details.stageIndex`). 미연결 호출자의 create/approve/reject/위임 생성 → 403 `APPROVAL_ACTOR_NOT_LINKED`; inbox · `?role=` → 200 빈 페이지 + `meta.actorEmployeeId` ABSENT.
- [ ] **AC-5** — 위임: 위임자 = 내 직원, 피위임자 직원의 연결 계정이 대결 승인 가능; 피위임자 = 상신자 직원이면 SoD 거절(직원 id 비교).
- [ ] **AC-6** — 알림: `APPROVAL_SUBMITTED` 알림이 승인자 직원과 연결된 계정의 inbox 에 보이고, 다른 계정에는 404.
- [ ] **AC-7** — read-model: 직원 id 위임자를 가진 위임 사실이 위임자 부서 scope 안 운영자에게 보이고 밖 운영자에게 안 보인다(«전»: `sub` 위임자는 scope 판정이 안 됨을 먼저 잰다).
- [ ] **AC-8** — 시드: 정적 검증(`bash -n` · 시드 하네스/가드가 있으면 그것) 통과. 🔵 라이브(재굽기 뒤 결재함이 편법 없이 찬다 · §9 등식 · `/me` 읽기 검증)는 ⚪ 재굽기 창.
- [ ] **AC-9** — 각 서비스 단위 · 슬라이스 통과; IT 작성(Docker 없는 호스트면 ⚪ «CI 첫 실행»).

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
