# Task ID

TASK-ERP-BE-044

# Title

masterdata — 직원 ↔ IAM 계정 연결 모델: `employees.account_id` · 연결 제안 표 · 제안/수락/거절/철회/해제 · 🔴 두 사람 규칙 (`TASK-MONO-774` S2)

# Status

review

# Owner

erp-platform

# Task Tags

- masterdata-service
- backend
- api

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 교차 컨텍스트 참조 · 두 사람 규칙(권한 술어) · 생성 열 유니크.

---

# Dependency Markers

- **선행**: 루트 `TASK-MONO-774` **S1 머지**(계약 `masterdata-api.md` § Employee ↔ IAM account link · 오류 코드 `EMPLOYEE_LINK_*` 5개 등록).
- **후속**: 루트 `TASK-MONO-776`(S3 — approval · notification · read-model · 시드가 이 표면을 쓴다) → `projects/platform-console` `TASK-PC-FE-318`(S4).
- 상위: 루트 `TASK-MONO-774`(우산 · `ADR-MONO-080` D7 = E1).

# Goal

직원 마스터가 그 직원으로 로그인하는 IAM 계정을 안다. 연결은 **인사 제안 + 본인 수락**(소유자 결정 2026-10-08 UTC)으로만 쓰이고, 제안자와 수락자는 **달라야 한다**. 지금은 `employees` 에 계정 칸이 없다(`src/main/resources/db/migration/V1__init.sql:38-55`).

# Scope

## In Scope

계약 `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link 를 **그대로** 구현한다.

- Flyway `V3__…`: `employees.account_id VARCHAR(64) NULL` + `UNIQUE (tenant_id, account_id)` · `employee_account_link_proposals`(계약 § Data model 열) + 직원당 `PENDING` 하나(`status='PENDING'` 일 때만 값을 갖는 생성 열 + `UNIQUE (tenant_id, <생성 열>)` — MySQL 에 부분 인덱스가 없다).
- 도메인: `Employee.accountId` · 연결 제안 애그리거트(상태 `PENDING → ACCEPTED | DECLINED | REVOKED`, `PENDING` 아닌 행 불변) — 프레임워크 import 없음.
- 응답: `EmployeeView` 에 `accountId`(NON_NULL). `POST/PATCH /employees` 요청 DTO 에는 넣지 않는다.
- 읽기: `GET /employees/me`(data scope 없음) · `GET /employees/{id}/approver-ref`(data scope 없음, `{id,status,accountId?}`) · `GET /account-link-proposals/mine` · `GET /employees/{id}/account-link-proposals`.
- 쓰기(전부 `Idempotency-Key` · audit · 직원 변화 시 `employee.changed` 아웃박스 — 같은 Tx): 제안 · 수락 · 거절 · 철회 · 해제. 검사 순서는 계약 그대로.
- 오류: `EMPLOYEE_LINK_PROPOSAL_NOT_FOUND`(404) · `EMPLOYEE_LINK_CONFLICT`(409, `details.cause`) · `EMPLOYEE_LINK_INVALID`(422, `employee_not_active`) · `EMPLOYEE_LINK_NOT_ADDRESSEE`(403) · `EMPLOYEE_LINK_SELF_ACCEPT`(403) — `GlobalExceptionHandler` 매핑.
- `architecture.md` 엔드포인트 표(`specs/services/masterdata-service/architecture.md:705-720` 근처) 에 새 행 · 권한 열(수락/거절 = «계정 주인», data scope 없음 이유).

## Out of Scope

- 🔴 **제안 시점 IAM 계정 존재 확인 — 하지 않는다**(소유자 결정 2026-10-08 UTC (b)). erp 에 IAM 워크로드 자격을 만들지 않는다. `accountId` 는 형식(빈 값 · 64자 초과 → 400)만 본다.
- approval / notification / read-model / 시드 / 콘솔 — `TASK-MONO-776` · `TASK-PC-FE-318`.
- 수락에 인증 이메일 직접 검사 — 소유자 결정: 간접 게이트(`TASK-MONO-772` `done/` 뒤 완성).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정: `V1__init.sql:38-55`(계정 칸 없음) · `MasterdataApplicationService.java:321-326`(직원 상세가 `READ, e.getDepartmentId()` 로 scope 를 건다 — `/approver-ref` · `/me` 가 그 경로를 타면 안 되는 이유) · `:328-334`(목록은 `READ, null`). 마지막 Flyway 버전 번호(`V2__masterdata_outbox_v2.sql`) 확인. — ✅ 아래 § AC-0 기록(전부 그대로 + `employee.changed` 판정).
- [x] **AC-1** — 제안 → 수락이 `employees.account_id` 를 쓴다: 수락 뒤 `GET /employees/{id}` 와 `GET /employees/me`(수락자 토큰)가 같은 `accountId` 를 돌려준다. 수락 전에는 `accountId` ABSENT · `/me` 404. — ✅ 단위 `EmployeeAccountLinkServiceTest$Accept.happy` · `$Reads.me/meNotLinked` · 슬라이스 `accountIdAbsentWhenUnlinked`/`accountIdPresentWhenLinked`/`meRoute`/`meNotLinked`. ⚪ HTTP 종단(`EmployeeAccountLinkIntegrationTest.proposeThenAccept`)은 작성·**미실행**(Docker 없음 — CI 첫 실행).
- [x] **AC-2** — 🔴 **두 사람 규칙 + bite**: 제안자 `sub` == 수락자 `sub` 인 수락 → 403 `EMPLOYEE_LINK_SELF_ACCEPT`, `account_id` 불변. 이 테스트는 규칙(수락 쪽 검사)을 지우면 **빨강**이 돼야 한다 — 검사를 주석 처리해 빨강을 실제로 보고 되돌린 기록을 티켓에 남긴다(제안 쪽 조기 거절만 남겨도 이 테스트가 빨강이어야 한다 — 시나리오: 제안 단계 검사를 우회한 행을 저장소에 직접 넣고 수락). — ✅ § Bite 기록(빨강 3건, 제안 쪽 조기 거절은 켜 둔 채). ⚪ 직접 INSERT 한 행으로 HTTP 수락하는 IT(`twoPersonRuleOnAcceptAgainstADirectRow`)는 미실행.
- [x] **AC-3** — 계정 주인 아닌 호출자의 수락/거절 → 403 `EMPLOYEE_LINK_NOT_ADDRESSEE`. — ✅ 도메인 `acceptByNonAddressee`/`decline` · 단위 `$Accept.notAddressee` · `$DeclineRevokeUnlink.declineByNonOwner` · 슬라이스 `declineNotAddressee403`. ⚪ IT `notAddressee` 미실행.
- [x] **AC-4** — 유니크: 한 계정을 같은 테넌트의 두 번째 직원에 연결하려는 제안/수락 → 409 `EMPLOYEE_LINK_CONFLICT`(`account_already_linked`); 직원당 두 번째 `PENDING` 제안 → 409 (`proposal_pending`) — 🔴 DB 유니크로도 막힌다(동시 제안 두 건 IT). — ✅ 애플리케이션 경로·번역 경로: 단위 `$Propose.accountAlreadyLinked/secondPending/dbUniqueOnInsert` · `$Accept.accountLinkedMeanwhile/raceLostAtFlush` · 슬라이스 `conflict409WithCause`. 🔴 ⚪ **DB 유니크 자체는 이 호스트에서 증명되지 않았다** — `V3` 의 생성 열 유니크와 `uq_employees_tenant_account` 는 MySQL 에서만 잴 수 있고, IT 3건(`onePendingPerEmployeeIsADatabaseRule` · `concurrentProposals` 8스레드 · `uniquenessConflicts` 의 직접 UPDATE)은 작성·**미실행**. CI `Integration (erp-platform, Testcontainers)` 첫 실행이 이 AC 의 DB 절반을 닫는다.
- [x] **AC-5** — `RETIRED` 직원에 제안 → 422 `EMPLOYEE_LINK_INVALID`; 이미 연결된 직원이 퇴사해도 `account_id` 는 **남는다**(Edge Case). — ✅ 도메인 `retiredCannotLink`/`retireKeepsLink` · 단위 `$Propose.retiredEmployee` · `$Accept.employeeRetiredMeanwhile` · 슬라이스 `invalid422`. ⚪ IT `retiredEmployee` 미실행.
- [x] **AC-6** — `/approver-ref` 는 호출자의 data scope 밖 직원도 돌려준다(scope 밖 operator 토큰으로 200), `GET /employees/{id}` 는 같은 토큰으로 403 — 둘의 차이를 한 테스트에서 단언. — ✅ `ApproverRefDataScopeTest` — **목 포트가 아니라 실제 `RoleScopeAuthorizationAdapter`** 로 한 테스트 안에서 `getEmployee` → `DATA_SCOPE_FORBIDDEN`, `getApproverRef` · `getMyEmployee` → 통과. ⚪ 서명 토큰 HTTP 판(`approverRefHasNoDepartmentScope`) 미실행.
- [x] **AC-7** — 철회(`erp.write` + scope) · 거절(주인) · 해제(scope 또는 주인) 각각 상태 전이 + audit 1행 + 해제 시 `employee.changed` 1건. — ✅ 단위 `$DeclineRevokeUnlink.*`(7건 — 감사 행 `ArgumentCaptor` 로 정확히 1, 해제 `publishEmployeeChanged(UPDATED)` 1, 거절·철회 이벤트 0). ⚪ IT `revokeDeclineUnlink`(DB `audit_log` · `masterdata_outbox` 행 수) 미실행.
- [x] **AC-8** — 단위 · 슬라이스(`@WebMvcTest` + `SecurityConfig` + `GlobalExceptionHandler`) 통과; IT(Testcontainers MySQL, `integration/` 형제 패턴)는 작성 — Docker 없는 호스트면 ⚪ «CI 첫 실행» 으로 정직하게 적는다. — ✅ 단위·슬라이스 200/0(새 65). 🔵 슬라이스는 형제 `DepartmentControllerSliceTest` 의 하네스(`addFilters=false` + `GlobalExceptionHandler`, `SecurityConfig` 미적재)를 그대로 따랐다 — 실제 필터 체인 + RS256 토큰은 IT 몫. ⚪ IT 9건 작성, 이 호스트 실행 결과 **9 SKIPPED**(`DockerAvailableCondition` — «Could not find a valid Docker environment») ⇒ CI 첫 실행.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § 새 입력 · D7
- 루트 `tasks/in-progress/TASK-MONO-774-erp-employee-account-link.md` § AC-0 (소유자 결정 1차 · 2차 원문)
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (엔드포인트 표 · 권한 매트릭스)
- `rules/domains/erp.md` E1 · E6 · E8

# Related Contracts

- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link (S1 이 쓴 것 — 구현이 다르면 계약을 먼저 고친다)
- `platform/error-handling.md` § Master Data (`EMPLOYEE_LINK_*`)
- `projects/erp-platform/specs/contracts/events/` 의 `employee.changed` — `accountId` 가 payload 에 실린다면 additive 로 그 문서도 고친다(AC-0 에서 판정)

# Edge Cases

- 한 계정 · 같은 테넌트 · 직원 둘 — 유니크로 막힌다(NULL 은 여럿 허용 — MySQL 유니크의 NULL 의미 확인).
- 퇴사 직원과 그 계정 — 연결은 남는다; 결재 쪽 E3 가 막는다(S3).
- 계정 삭제 · 잠금 — 연결은 남는다; 그 계정은 토큰을 못 받아 `/me` 를 부를 수 없다.
- 존재하지 않는 계정 앞 제안 — 수락될 수 없다; 철회로만 치운다(확인 안 함 — 소유자 결정).
- 수락 사이에 다른 수락이 그 계정을 다른 직원에 묶음 — 수락 Tx 의 유니크 위반을 409 로 번역(500 금지).

# Failure Scenarios

1. 두 사람 규칙을 **제안 쪽에만** 둔다 — 제안 뒤 다른 경로(직접 DB · 미래의 일괄 도구)로 들어온 제안을 제안자가 수락한다. 권위 있는 검사는 수락 쪽이다(AC-2 bite).
2. `/me` · `/approver-ref` 가 기존 `getEmployee` 를 재사용한다 — 부서 scope 가 따라붙어 결재선이 정상인데 «승인자 확인 불가» 가 된다(AC-6).
3. «직원당 PENDING 하나» 를 애플리케이션 검사로만 둔다 — 동시 제안 두 건이 둘 다 통과한다(AC-4 동시성).

---

# AC-0 기록 (2026-10-07 UTC — `date -u` 실측, 호스트 KST 10-08 03시 · 기준 `origin/main` `e27acc829`)

착수 시 재측정 — 티켓의 file:line 은 전부 그대로였다.

- `apps/masterdata-service/src/main/resources/db/migration/V1__init.sql:38-55` — `employees` 열은 `id … version` 13개, **계정 칸 없음** ✅. 유니크는 `uq_employees_tenant_number` 하나.
- 마지막 Flyway 버전 = `V2__masterdata_outbox_v2.sql`(`db/migration/` 안 파일 2개) ⇒ 새 파일은 `V3__employee_account_link.sql`.
- `MasterdataApplicationService.java:321-326` — `getEmployee` 가 `authorize(actor, READ, e.getDepartmentId())` 로 **직원의 부서**에 scope 를 건다 ✅. 그래서 `/approver-ref` · `/me` 는 이 메서드를 재사용하지 않고 `authorize(actor, READ, null)`(역할만) 로 따로 간다(Failure Scenario 2).
- `MasterdataApplicationService.java:328-334` — 목록은 `authorize(actor, READ, null)` ✅.
- 🔵 **`employee.changed` 판정** — 소비자 `read-model-service` `EnvelopeToCommandMapper.java:30-33` 는 **모르는 `changeKind` 를 DLT 로 보낸다**. 그러므로 연결/해제는 새 종류(`ACCOUNT_LINKED` 등)를 만들지 않고 **`UPDATED`** 로 내고, `before`/`after` 스냅샷에 `accountId` 를 **additive** 로 싣는다(`MasterEventEnvelope` 는 `after` 를 Map 으로 읽으므로 모르는 키는 무해). ⇒ `specs/contracts/events/erp-masterdata-events.md` employee payload 에 `accountId?` 를 additive 로 적는다.
- 🔵 도메인 엔티티의 JPA 애너테이션 — 티켓 Scope 는 «프레임워크 import 없음» 이라 적었지만 `architecture.md` § Boundary rules 는 «JPA annotations on entities are the single allowed exception» 이고 형제 `Employee.java` 가 그 형태다. 우선순위(architecture > task)대로 제안 애그리거트도 **Spring import 0 · JPA 애너테이션만** 으로 둔다. 상태 전이·두 사람 규칙은 순수 Java 메서드다.
- 🔵 유니크 위반 번역 — `libs/java-common` `DataIntegrityViolations.isUniqueViolation`(MySQL `1062` 포함)을 영속 어댑터(`infrastructure/`)에서 써서 도메인 예외로 바꾼다. 애플리케이션 계층은 Spring DAO 예외를 import 하지 않는다.

---

# 구현 기록 (2026-10-07 UTC)

- **마이그레이션** `V3__employee_account_link.sql` — `employees.account_id VARCHAR(64) NULL` + `uq_employees_tenant_account (tenant_id, account_id)`(InnoDB 유니크는 NULL 을 서로 다르게 본다 ⇒ 미연결 직원 여럿 허용) · `employee_account_link_proposals`(계약 열 그대로) + `pending_employee_id` **STORED 생성 열**(`status='PENDING'` 일 때만 `employee_id`) + `uq_link_proposals_one_pending (tenant_id, pending_employee_id)`. 생성 열은 JPA 에 매핑하지 않는다(`ddl-auto=validate` 는 매핑된 열만 본다).
- **도메인** — `Employee.accountId` + `linkAccount`/`unlinkAccount`(퇴사는 연결을 건드리지 않는다) · `employee/link/EmployeeAccountLinkProposal`(+ `LinkProposalStatus`). 🔴 두 사람 규칙은 `EmployeeAccountLinkProposal#ensureTwoPersonRule` **한 곳**이고 `accept` 와 비변경 사전 검사 `ensureAcceptableBy` 가 둘 다 그 메서드를 탄다 — 그래서 그 한 줄을 끄면 두 경로가 함께 열린다(아래 bite 가 그것을 잰다).
- **애플리케이션** — `MasterdataApplicationService` 에 사용 사례 9개(유일한 `@Transactional` 경계 규칙 유지). 모든 사용 사례가 저장소 호출 **전에** 역할 게이트 `authorize(…, null)` 를 먼저 타고, 계약이 부서 scope 를 거는 곳(제안 · 이력 · 철회 · HR 해제)만 직원 부서로 두 번째 `authorize`. `/me` · `/approver-ref` · `/mine` · 수락 · 거절은 부서 scope 없음(`getEmployee` 재사용 안 함 — Failure Scenario 2).
- **이벤트** — 수락 · 해제만 `employee.changed`(`UPDATED`, 스냅샷에 `accountId`). 제안 · 거절 · 철회는 감사 행만.
- **감사** — 사용 사례당 정확히 1행. 제안 · 거절 · 철회 = `employee_account_link_proposal`, 수락 · 해제 = `employee`(수락 `after_state` 에 `proposalId` · `proposedBy`).
- **멱등 페이로드** — 새 쓰기 엔드포인트 5개는 경로 id 를 페이로드 해시에 넣는다. 🔵 형제 엔드포인트(`PATCH /employees/{id}` 등)는 요청 본문만 해시하므로 «같은 키 + 다른 id» 가 첫 응답을 재생한다 — 이 티켓 범위 밖이라 고치지 않았다(관찰만 기록).

## 🔴 Bite 기록 (AC-2, 2026-10-07 UTC)

1. `EmployeeAccountLinkProposal#ensureTwoPersonRule` 의 `throw new EmployeeLinkSelfAcceptException(...)` 두 줄을 Edit 로 주석 처리. **제안 쪽 조기 거절(`proposeAccountLink` 의 `accountId == actorId` 검사)은 켜 둔 채.**
2. `./gradlew :projects:erp-platform:apps:masterdata-service:test` → **rc=1, `200 tests completed, 3 failed`** — 빨강이 된 것은 정확히 이 셋:
   - `EmployeeAccountLinkServiceTest > accept > 🔴 AC-2 two-person rule: proposer == acceptor on a row that bypassed the proposal check → 403 SELF_ACCEPT, account_id unchanged`
   - `EmployeeAccountLinkProposalTest > 🔴 AC-2 two-person rule: the proposer cannot accept its own proposal → SELF_ACCEPT, state unchanged`
   - `EmployeeAccountLinkProposalTest > 🔴 AC-2 two-person rule also on the non-mutating pre-check`
3. Edit 로 두 줄을 되살림(`git checkout` 아님) → 같은 명령 **rc=0, 200/0**.

⇒ 제안 쪽 검사만 남은 상태에서 «우회해 들어온 자기 제안 행» 의 수락이 빨강으로 잡힌다(Failure Scenario 1). 다른 197건은 그 줄에 의존하지 않는다 — 이 규칙을 지키는 테스트가 셋뿐이라는 뜻이기도 하다. ⚪ IT 판(`twoPersonRuleOnAcceptAgainstADirectRow` — 실제 `INSERT` 한 행 + 서명 토큰)은 미실행이라 bite 대상에 넣지 못했다.

## 검증 명령 (전부 worktree `monorepo-lab-erpbe044`)

| 명령 | rc | 실측 |
|---|---|---|
| `./gradlew :projects:erp-platform:apps:masterdata-service:compileJava` | 0 | — |
| `… :test` (구현 직후) | 0 | JUnit XML **27** 파일 · tests **200** · failures 0 · errors 0 · skipped 0(새 클래스 8개 = 65건) |
| `… :test` (bite — 검사 꺼짐) | **1** | `200 tests completed, 3 failed` (위 셋) |
| `… :test` (복원 후) | 0 | XML 27 · 200/0/0/0 |
| `… :integrationTest --tests '*EmployeeAccountLinkIntegrationTest'` | 0 | XML 1 · **9 SKIPPED** — Docker 없음 ⚪ |
| `… :check` | 0 | (test 결과 재사용) |

## 계약 · 스펙 변경 (같은 커밋 — 구현이 계약과 갈리지 않게)

- `specs/contracts/http/masterdata-api.md` — ① 머리말 «`details` 를 싣는 코드는 `MASTERDATA_REFERENCE_VIOLATION` 하나» 가 S1 이후 **틀린 문장**이 됐다(`EMPLOYEE_LINK_CONFLICT`/`INVALID` 가 `details.cause` 를 싣는다) ⇒ 고침. ② `EmployeeAccountLinkProposal` **전체 모양**(`decidedBy?`/`decidedAt?`/`decisionReason?` — 이력 목록에 필요한데 201 부분집합만 적혀 있었다) · `employeeName`/`employeeNumber` 는 `/mine` 에만 · 두 목록의 `?page=&size=` 와 정렬. ③ 수락 본문 `{}` 생략 허용. ④ 해제의 계정 주인 경로도 `erp.read` 이상. ⑤ 이벤트 `changeKind = UPDATED` + 감사 행 귀속. — 전부 **명시화(additive)** 이고 S1 이 정한 규칙(쓰기 경로 · 두 사람 규칙 · 검사 순서 · 오류 코드)은 바꾸지 않았다.
- `specs/contracts/events/erp-masterdata-events.md` — employee 스냅샷에 `accountId` additive(AC-0 판정 — 새 `changeKind` 는 소비자가 DLT 로 보낸다).
- `specs/services/masterdata-service/architecture.md` — 엔드포인트 표 9행(권한 열에 «data scope 없음» 이유) · 엔드포인트 수 26→35.
