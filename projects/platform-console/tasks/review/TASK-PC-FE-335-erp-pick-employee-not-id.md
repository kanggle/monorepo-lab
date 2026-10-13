# Task ID

TASK-PC-FE-335

# Title

콘솔 ERP 직원 ID 입력 4곳 — 직원 ID 를 치는 대신 사번 · 이름으로 고르기 (묶음 ②)

# Status

review — 구현 완료(2026-10-13 UTC). AC-1~7 ✅. 라이브 확인은 AC 밖(데모 창).

# Owner

platform-console

# Task Tags

- erp-ops
- ux
- pickers

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 같은 화면의 결재자 선택(TASK-PC-FE-318) 패턴을 세 컴포넌트에 복사. 백엔드 변경 없음.

---

# Dependency Markers

- 선행 없음. 선례: `TASK-PC-FE-318`(결재 생성의 결재선 = 직원 선택기, `ApprovalCreateDialog` — 최대 `ERP_MAX_PAGE_SIZE` 명 한 페이지 · 목록 실패 시 같은 testid 의 ID 입력으로 대체 · `approverOptionLabel`).
- 같은 계열: `TASK-PC-FE-334`(묶음 ① 운영자 · 계정), 후속 묶음 ③ 셀러 · 테넌트.

# Goal

소유자 요청(2026-10-13 «모든 도메인», 묶음 ①→②→③): ERP 화면에 직원 ID 를 직접 치게 하는 칸이 4곳 남아 있다. `ADR-MONO-080` D7 = E1(approval-api v2.4 개정, `TASK-MONO-774`)로 아래 칸은 **전부 erp 직원 id** 다 — 사람은 사번 · 이름을 안다.

| 화면 | 지금 | 쓸 읽기 |
|---|---|---|
| `ApprovalCreateDialog` 대상 ID (`subjectType` = 부서 / 직원) | `dept-… / emp-…` 입력 | 부서 → `useDepartments`, 직원 → `useEmployees` |
| `DelegationScreen` 위임 생성 — 대결자 ID | `emp-…` 입력 | `useEmployees` |
| `DelegationFactCard` 필터 — 위임자 ID · 대행자 ID | ID 입력 ×2 | `useEmployees` (선택 안 함 = 전체) |

# Scope

## In

- 위 4곳을 `<select>` 선택기로 교체(결재자 선택과 같은 모양 · 같은 대체 규칙). 요청에는 **지금과 같은 id** 가 같은 이름으로 간다.
- 대상 ID: 대상 유형을 바꾸면 고른 대상을 해제하고 그 유형의 목록으로 바꾼다.
- 목록 실패 → 안내 + 기존 ID 입력(같은 testid) — 흐름 유지. 목록이 `ERP_MAX_PAGE_SIZE` 보다 많으면 «처음 N명만 표시» 안내(결재자와 같다).
- 직원 표시 = `approverOptionLabel` 재사용(사번 · 이름 · 폐기 · 계정 미연결). 위임 대결자 후보에서 `RETIRED` 직원은 표시하되 고를 수 없다(계약: `delegateId` 는 `ACTIVE` 직원이어야 한다 — 422 `delegate_unresolved`).
- 단위 시험 갱신 · 추가.

## Out

- 백엔드 · 계약 변경(직원 목록에 검색 조건 추가 등). 직원이 100명을 넘는 규모의 서버 검색은 별도.
- 묶음 ① · ③.

# Acceptance Criteria

- [x] **AC-1** — 4곳의 ID 텍스트 입력이 직원(대상 유형이 부서면 부서) 선택기로 바뀌고, 생성 요청 본문 / 필터 요청에 **고른 항목의 id** 가 기존 이름으로 간다. 실측: 화면 시험이 요청 본문 · 쿼리를 단언. 실측 결과: `ApprovalScreen.test.tsx`(본문 `subjectId=emp-a`) · `DelegationScreen.test.tsx`(본문 `delegateId=emp-delegate`) · `DelegationFactCard.test.tsx`(URL `delegatorId=emp-001`).
- [x] **AC-2** — 대상 유형 전환 시 선택 해제 + 목록 전환. 실측: 시험. 실측 결과: `ApprovalScreen.test.tsx` — 부서 선택 후 직원으로 전환 시 값 `''` · 부서 id 가 선택지에서 사라짐.
- [x] **AC-3** — 목록 실패 시 ID 입력으로 대체돼 같은 흐름이 끝까지 된다. 실측: 시험(실패 모의). 실측 결과: 화면마다 1칸(대상 ID 부서 · 직원 경로, 대결자, 필터 둘) — 대체 안내 뒤 `<input>` 이고 입력값이 본문에 실림.
- [x] **AC-4** — 위임 대결자 후보에서 폐기 직원은 선택 불가로 표시. 실측: 시험. 실측 결과: `DelegationScreen.test.tsx` — RETIRED 선택지 `disabled` + «폐기된 직원».
- [x] **AC-5** — 위임 사실 필터는 «전체»(미선택)일 때 그 파라미터를 보내지 않는다. 실측: 시험. 실측 결과: `DelegationFactCard.test.tsx` — 미선택 시 URL 에 `delegatorId=` · `delegateId=` 없음.
- [x] **AC-6** — 각각 독립 실행 · 명시 rc: `vitest run` · `pnpm lint` · `tsc --noEmit` · `next build`. 실측 결과: 구현 에이전트 전체 `vitest run` 369 파일 중 1 실패 = `WmsRefPicker.test.tsx` 디바운스(이 변경과 무관 · 단독 재실행 16/16) · 오케스트레이터 재실행 `tests/unit/features/erp-ops` rc=0(7 파일 · 114) · `tsc --noEmit` rc=0 · `pnpm lint` rc=0 · `next build` rc=0(구현 에이전트). 전체 스위트는 CI 권위.
- [x] **AC-7** — e2e 스펙이 바뀐 testid · 라벨을 쓰는지 grep 해 기록(쓰면 같이 고친다). 실측 결과: `tests/e2e` 에 네 testid · 라벨 0건 — 고칠 것 없음.

# Related Specs

- `specs/services/console-web/architecture.md` — `features/erp-ops` 안에서 끝낸다(다른 feature import 금지).

# Related Contracts

- erp `approval-api.md` v2.4 개정(사람 칸 = 직원 id) · § delegations(`delegateId` = ACTIVE 직원).
- erp `masterdata-api.md` § `GET /employees`(`?active=&departmentId=&page=&size=`, 검색 없음) · departments.
- erp `read-model-api.md` § delegation facts(`delegatorId` · `delegateId` = 직원 id 필터).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 직원 100명 초과 | 처음 N명 + 안내(결재자와 같음) |
| 계정 미연결 직원 | 고를 수 있고 «연결된 계정 없음» 표시(결재자와 같음) |
| 부서 0개 | 빈 선택기 + 안내 |

# Failure Scenarios

| 실패 | 기대 |
|---|---|
| 직원 / 부서 목록 503 · 403 | ID 직접 입력으로 대체 |
| 생산자 422 `delegate_unresolved` | 기존 오류 문구 그대로 |

# Notes

- 공용 부품 `components/ErpEmployeeSelect.tsx`(선택기 + 목록 실패 시 같은 testid 의 ID 입력). 라벨 규칙 `employeeOptionLabel` 하나 — 기존 `approverOptionLabel` 은 그것을 부른다(두 곳에 같은 규칙을 두지 않는다). 결재선(결재자) 동작 · testid 는 그대로.
- 부서 선택기는 호출처가 하나라 `ApprovalCreateDialog` 안에 둔다.
