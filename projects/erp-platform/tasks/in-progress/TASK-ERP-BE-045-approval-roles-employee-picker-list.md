# Task ID

TASK-ERP-BE-045

# Title

결재 권한(`ERP_APPROVAL_WRITE`)으로 직원 «선택용» 축약 목록을 연다 — 기본 «결재 운영» 세트의 운영자가 결재자 · 대결자를 ID 로 손입력하는 상태 해소 (`ADR-MONO-083` P6 인가의 확장 — 결정 먼저)

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test
- authz

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (인가 범위 결정 · 기준정보 서비스 인가 · 계약 — ADR 영향 확인 필요)
> 기안 2026-10-19 UTC — `TASK-PC-FE-349` 소유자 결정 «(가) 지금 + (나) 기안» 의 (나). **결정 티켓이 먼저다** — AC-0 에서 범위를 정하기 전에는 구현하지 않는다.

# Dependency Markers

- 출처: `TASK-PC-FE-349`(콘솔 문구 — 403 을 «권한 없음» 으로 말하게 함. 손입력 불편은 남음).
- 근거 결정: `TASK-MONO-793`(ADR-MONO-083 P6 — ERP 세분 인가) Q1 · Q3 · Q6 · admin `V0057`(«결재 운영» = `ERP_APPROVAL_READ` + `ERP_APPROVAL_WRITE`).
- 선행 없음.

# Goal

기본 세트 «결재 운영»(`erp-approval-operate`)을 받은 운영자는 결재 상신 · 위임 생성을 할 수 있다(서버 허용). 그런데 대상 직원 · 결재자 · 대결자를 고르는 목록은 기준정보 서비스 `GET /employees`(`listEmployees` = `RequiredScope.READ` = 기준정보 역할만)라서 `403` 이다. 그 결과 직원 ID 를 손으로 입력해야 한다. 결재 권한이 지금 여는 것은 직원 **단건** 조회(`SUBJECT_READ`, 793 Q3)뿐이다.

선택에 필요한 최소 정보(id · 이름 · 부서 이름 · 재직 여부)만 결재 권한에 열어 손입력을 없앤다. 기준정보 전체 조회를 결재 담당자에게 주지는 않는다.

# Scope

## In Scope

- **AC-0 결정 (소유자)** — 선택지를 추천과 함께 묻는다.
  - (가) 새 축약 엔드포인트(예: `GET /employees/picker` — id · 이름 · 부서명 · active)를 `ERP_MASTERDATA_READ ∨ ERP_MASTERDATA_WRITE ∨ ERP_APPROVAL_WRITE` 로 연다. 추천 — 최소 권한.
  - (나) 기존 `GET /employees` 를 `ERP_APPROVAL_WRITE` 에도 연다(필드 전부 노출).
  - (다) 세트 «결재 운영» 에 `ERP_MASTERDATA_READ` 를 더한다(admin 마이그레이션 — 부서 · 직원 · 직급 · 비용센터 · 거래처 전부를 읽게 됨).
  - 어느 쪽이든 `ADR-MONO-083` P6 / 793 결정을 바꾸는지 확인한다. 바꾼다면 ADR 개정이 먼저다(`platform/architecture-decision-rule.md`).
- 결정에 따른 구현: 계약(ERP API 계약 · 콘솔 계약 § 2.4.8) 먼저 → 서버 인가 + 시험(대조군: 역할 없음 403 · 결재 읽기만 403 · 결재 쓰기 200 · 기준정보 읽기 200) → 콘솔 4 곳(`DelegationScreen` · `ApprovalCreateDialog` ×2 · `DelegationFactCard`)이 새 목록을 쓰게.
- 🔵 `DelegationFactCard`(위임 사실 필터)는 결재 **읽기** 운영자도 연다 — 그 칸은 쓰기 역할 기준과 다를 수 있다(AC-0 에서 정한다).

## Out of Scope

- 콘솔 403 문구(`TASK-PC-FE-349` — 완료).
- 결재 자격(단계 승인자 · 직무 분리) — 역할과 별개의 서버 집합체 가드.

# Acceptance Criteria

- [x] **AC-0** — 소유자 결정(선택지 · 추천 · ADR 영향 판정)을 적는다. → 아래 § AC-0.
- [x] **AC-1** — 계약 · (필요하면) ADR 개정이 코드보다 먼저. → 커밋 `d0a04d320`(계약만) 이 구현 커밋보다 앞선다. ADR 개정 불필요(§ AC-0).
- [x] **AC-2** — 서버 인가 시험 대조군 4 칸(위) 빨강 → 초록. 응답에 선택용 최소 필드 외의 값이 없다((가) 일 때). → 아래 «AC-2 진행» + ⚪ 였던 `EmployeePickerIntegrationTest` 는 #4421 CI `Integration (erp-platform, Testcontainers)` 로그에서 **6 건 전부 실행 · PASSED**(서브트리 · 자식 서브트리 · 빈 범위 · `'*'` · `active` · 페이징) — 머지 `6bbd0dba4`.
- [ ] **AC-3** — 콘솔: «결재 운영» 운영자가 4 곳에서 목록으로 고른다(폴백 문구가 안 뜬다). 기준정보 운영자 · 데모 운영자 비회귀.
- 🔵 **AC-2 진행 (2026-10-20 UTC)** — 빨강: 시험만 먼저 → 컴파일 실패, 순진한 스텁 → 253 중 28 실패(전부 새 picker 칸, 기존 225 통과). 초록: `:masterdata-service:test` 253/0 · rc=0. 대조군 4 칸 = `EmployeePickerControllerSliceTest`(실제 서비스 + 어댑터, 저장소만 mock) · 응답 키 집합 = 정확히 7(부서 미해소 시 6) · 범위 `'*'`/서브트리/없음. ⚪ `EmployeePickerIntegrationTest`(MySQL Testcontainers) 는 로컬 Docker 꺼짐 → CI `integrationTest` 결과로 닫는다.
- 🔵 **AC-3 진행** — 콘솔 4 곳이 `GET /api/erp/masterdata/employees/picker` 로 읽고 «ID 직접 입력» 토글(범위 ≠ TENANT 또는 잘림). 빨강 37 실패 → 초록 300(9 파일) · 전체 4922/0 · tsc 0 · lint 0. 문구 = `employee-list-fallback.ts` 한 곳. ⚪ 브라우저 · 라이브(«결재 운영» 운영자) 미확인 → 굽기 뒤. 사용자 문구 3 종 = 소유자 승인(2026-10-20 UTC, 머지 전 선택창).
- 🔵 **AC-3 라이브 비회귀 (30차 창, 2026-10-20 UTC)** — 데모 운영자 `demo@demo.com`(`demo-corp`, legacy-compat): `GET /employees/picker` 200 · `meta.scope="TENANT"` · 원소 키 정확히 7 · `?active=true` 200 · 기존 `GET /employees` 200 · `meta.scope` 없음 · 키 그대로. ⚪ 남은 것: «결재 운영» 세트**만** 가진 운영자가 4 곳에서 목록으로 고르는 화면(권한 세트를 바꾼 버리는 대상이 필요 — `TASK-MONO-672` «Still open» 11 의 ④ 와 같은 창) · 기준정보 운영자 비회귀.
- [ ] **AC-4** — 배포 상태(AMI 굽기 · Vercel · 라이브 «Still open» 11 ④)를 따로 적는다.
  - 🔵 2026-10-20 UTC: **AMI** = `ami-01681ef55def7d15c`(`RepoCommit` `e44142d34` ⊇ #4421 `6bbd0dba4`) 굽기 · apply · 위 라이브 비회귀까지. **Vercel** = #4421 의 콘솔 변경이 Production 에 나갔는지 배포 기록 확인은 아직(⚪). 라이브 «Still open» 11 ④ = ⚪.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` P6
- `projects/erp-platform/specs/integration/iam-integration.md` § 업무 역할
- `projects/erp-platform/specs/services/masterdata-service/*`

# Related Contracts

- `projects/erp-platform/specs/contracts/` (masterdata API)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8

# Edge Cases

- 데모 운영자(legacy-compat)는 기준정보 조회가 있어 지금도 목록이 뜬다 — 데모에서는 안 보이는 결함이다.
- 협력사 범위(부서 서브트리)를 가진 운영자 — 선택 목록도 같은 데이터 범위를 따라야 한다(`RoleScopeAuthorizationAdapter` 의 범위 판정과 맞춘다).

# Failure Scenarios

1. 콘솔이 단건 조회를 여러 번 불러 목록을 재구성한다 — 서버가 열지 않은 목록을 사실상 우회한다.
2. (나)/(다)를 «쉬우니까» 고른다 — 결재 담당자에게 거래처 · 비용센터까지 읽기가 열린다. 범위는 AC-0 에서 명시적으로 정한다.
3. 선택 목록이 데이터 범위(부서 서브트리)를 무시한다 — 협력사 운영자가 범위 밖 직원을 본다.
4. 범위를 적용한 목록이 «고르기만» 허용한다 — 범위를 가진 운영자가 범위 밖 결재자 · 대결자를 아예 지정할 수 없게 된다(오늘은 403 폴백으로 ID 입력이 되므로 **회귀**).

---

# AC-0 — 측정 · 결정 (2026-10-20 UTC)

## 측정 (코드 읽기 · 정적)

- 기존 `GET /employees` 목록은 부서 범위를 **걸지 않는다** — `MasterdataApplicationService.listEmployees` = `authorize(READ, null)` → 테넌트 전체(`architecture.md` § E6 «READ-overview granularity»). 범위 판정은 단건(`getEmployee` = `SUBJECT_READ` + 직원 부서)에만 있다.
- 콘솔은 같은 목록으로 **대상 · 결재선(결재자 N 단계) · 대결자 · 위임 사실 필터(위임자/대행자)** 를 채운다(`ApprovalCreateDialog` · `DelegationScreen` · `DelegationFactCard`, 라벨 규칙 `employeeOptionLabel` = 사번 · 이름 · 폐기 · 계정 미연결).
- 결재자는 보통 상신자 범위 **밖**(상위 부서)에 있다 — 그래서 `approver-ref` 는 범위를 걸지 않고 **이름(PII)을 싣지 않는다**(`masterdata-api.md` § approver-ref).
- `DelegationFact` 는 `delegatorId/delegateId` 를 불투명 id 로만 싣는다 — 결재 읽기 운영자에게 이름 목록은 지금 없다.
- `org_scope` NULL(= `'*'`, 기본) 운영자는 어느 선택지든 테넌트 전체 — 차이는 범위를 가진(협력사) 운영자에게만 난다.
- 남는 것(이 티켓 밖): 대상 유형 «부서» 의 선택 목록(`GET /departments`)은 여전히 기준정보 조회 역할만 — 결재 운영 세트에서는 부서 ID 입력 폴백이 남는다.

## 결정 (소유자, 2026-10-20 UTC — 선택창)

| 질문 | 결정 |
|---|---|
| 여는 방식 | **(가)** 새 축약 엔드포인트 `GET /api/erp/masterdata/employees/picker` |
| 판정 | **`ERP_MASTERDATA_READ ∨ ERP_MASTERDATA_WRITE ∨ ERP_APPROVAL_WRITE`**(+ 범위 갈래 `erp.read ∨ erp.write ∨ tenant_id='*'` — 793 Q3 의 `SUBJECT_READ` 와 같은 술어). **`ERP_APPROVAL_READ` 는 열지 않는다** — 결재 읽기 전용 운영자의 위임 사실 필터는 ID 입력으로 남는다 |
| 데이터 범위 | **부서 서브트리 적용** — `'*'` = 테넌트 전체 · 루트 목록 = 그 서브트리 안 직원만 · 범위 없음 = 빈 목록(fail-closed). 범위 밖 결재자 · 대결자는 콘솔에서 «ID 직접 입력» 으로 지정(Failure 4) |

- 응답 원소 = `{ id, employeeNumber, name, departmentId, departmentName, status, accountLinked }` — 콘솔 라벨 규칙에 필요한 것만. `accountId` 대신 **불리언** `accountLinked`(계정 id 를 노출하지 않는다). 비용센터 · 직급 · 유효기간은 없다.
- **ADR 영향 판정 = 개정 불필요.** (가) 는 793 Q3 이 정한 술어(«한 경로만 두 역할»)를 새 경로 하나에 그대로 쓴다 — `ADR-MONO-083` P6 본문도, 793 Q1 ~ Q6 의 답도 바꾸지 않는다. 정본 갱신은 erp `iam-integration.md` § 업무 역할 표 · `masterdata-api.md` · 콘솔 계약 § 2.4.8 (AC-1). (나) · (다) 였다면 793 Q6 · Q3 결정 변경이라 ADR 개정이 먼저였다.
- [x] AC-0 닫힘.
