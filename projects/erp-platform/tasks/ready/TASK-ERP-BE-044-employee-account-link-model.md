# Task ID

TASK-ERP-BE-044

# Title

masterdata — 직원 ↔ IAM 계정 연결 모델: `employees.account_id` · 연결 제안 표 · 제안/수락/거절/철회/해제 · 🔴 두 사람 규칙 (`TASK-MONO-774` S2)

# Status

ready

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

- [ ] **AC-0** — 착수 시 재측정: `V1__init.sql:38-55`(계정 칸 없음) · `MasterdataApplicationService.java:321-326`(직원 상세가 `READ, e.getDepartmentId()` 로 scope 를 건다 — `/approver-ref` · `/me` 가 그 경로를 타면 안 되는 이유) · `:328-334`(목록은 `READ, null`). 마지막 Flyway 버전 번호(`V2__masterdata_outbox_v2.sql`) 확인.
- [ ] **AC-1** — 제안 → 수락이 `employees.account_id` 를 쓴다: 수락 뒤 `GET /employees/{id}` 와 `GET /employees/me`(수락자 토큰)가 같은 `accountId` 를 돌려준다. 수락 전에는 `accountId` ABSENT · `/me` 404.
- [ ] **AC-2** — 🔴 **두 사람 규칙 + bite**: 제안자 `sub` == 수락자 `sub` 인 수락 → 403 `EMPLOYEE_LINK_SELF_ACCEPT`, `account_id` 불변. 이 테스트는 규칙(수락 쪽 검사)을 지우면 **빨강**이 돼야 한다 — 검사를 주석 처리해 빨강을 실제로 보고 되돌린 기록을 티켓에 남긴다(제안 쪽 조기 거절만 남겨도 이 테스트가 빨강이어야 한다 — 시나리오: 제안 단계 검사를 우회한 행을 저장소에 직접 넣고 수락).
- [ ] **AC-3** — 계정 주인 아닌 호출자의 수락/거절 → 403 `EMPLOYEE_LINK_NOT_ADDRESSEE`.
- [ ] **AC-4** — 유니크: 한 계정을 같은 테넌트의 두 번째 직원에 연결하려는 제안/수락 → 409 `EMPLOYEE_LINK_CONFLICT`(`account_already_linked`); 직원당 두 번째 `PENDING` 제안 → 409 (`proposal_pending`) — 🔴 DB 유니크로도 막힌다(동시 제안 두 건 IT).
- [ ] **AC-5** — `RETIRED` 직원에 제안 → 422 `EMPLOYEE_LINK_INVALID`; 이미 연결된 직원이 퇴사해도 `account_id` 는 **남는다**(Edge Case).
- [ ] **AC-6** — `/approver-ref` 는 호출자의 data scope 밖 직원도 돌려준다(scope 밖 operator 토큰으로 200), `GET /employees/{id}` 는 같은 토큰으로 403 — 둘의 차이를 한 테스트에서 단언.
- [ ] **AC-7** — 철회(`erp.write` + scope) · 거절(주인) · 해제(scope 또는 주인) 각각 상태 전이 + audit 1행 + 해제 시 `employee.changed` 1건.
- [ ] **AC-8** — 단위 · 슬라이스(`@WebMvcTest` + `SecurityConfig` + `GlobalExceptionHandler`) 통과; IT(Testcontainers MySQL, `integration/` 형제 패턴)는 작성 — Docker 없는 호스트면 ⚪ «CI 첫 실행» 으로 정직하게 적는다.

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
