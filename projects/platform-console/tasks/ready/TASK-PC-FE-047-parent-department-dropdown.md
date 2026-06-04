# Task ID

TASK-PC-FE-047

# Title

**erp 부서 write 다이얼로그의 상위 부서 입력을 UUID 텍스트 → 기존 부서 드롭다운(`<select>`)으로 교체.** create 의 "상위 부서" + move-parent 의 "새 상위 부서" 둘 다. 운영자가 raw UUID 를 타이핑하는 대신 기존 부서를 목록에서 선택한다. console-web 프런트만 변경(API/proxy/producer 불변).

# Status

ready

# Owner

frontend-engineer (console-web only — project-internal)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **follows / UX-fix-of**: TASK-PC-FE-046 (erp 부서 write 다이얼로그). 그 다이얼로그의 상위 부서 칸이 UUID 텍스트 입력이라 — 목록 화면이 부서 ID 를 노출하지 않아 — 운영자가 무엇을 넣을지 알 수 없는 UX 갭. TASK-BE-336/BE-337 (write 권한 활성화 — 이제 실제로 쓰므로 상위 선택 UX 가 의미 있어짐).
- **decision (user, 2026-06-04)**: "상위 부서 드롭다운 (UUID 직접 입력 대신 기존 부서를 목록에서 선택)".

# Goal

create/move-parent 다이얼로그에서 상위 부서를 드롭다운으로 선택한다. 옵션 = 현재 로드된 부서 중 active(비폐기); 기본값 "최상위(상위 없음)". move-parent 는 대상 부서 자신을 옵션에서 제외(자기 자신을 상위로 둘 수 없음; producer 도 deeper cycle 은 `MASTERDATA_PARENT_CYCLE` 로 거부).

# Scope

## In Scope

- `DepartmentWriteDialog.tsx` — `departments?: Department[]` prop 추가. create 의 `parentId` `<input>` + move-parent 의 `newParentId` `<input>` → `<select>`(testid `erp-dept-parent-id`/`erp-dept-new-parent-id` 보존; 값=부서 id 또는 '' = 최상위). active 필터(`isRetired` 제외) + move-parent 는 target 자신 제외.
- `DepartmentList.tsx` — 로드된 `rows` 를 `departments` prop 으로 전달.
- 테스트 — 드롭다운이 active 부서 나열(retired 제외) + 선택 시 parentId 전송 + move-parent 가 target 제외 단언.

## Out of Scope

- API/proxy/hook/contract/producer (전부 불변 — UI 입력 위젯만 교체; 전송 body 의 parentId/newParentId 의미 동일).
- 전체 부서 fetch(현재 로드된 page 사용 — 파일럿 규모 충분; 다중 page full-list fetch 는 향후 enhancement, 본 task 에 명시).
- `ChangePasswordForm` 등 무관.

# Acceptance Criteria

- [ ] **AC-1** create 다이얼로그 상위 부서 = `<select>` (기본 "최상위", 옵션 = active 부서 `code · name`); 선택 시 body `parentId` = 선택 부서 id, 미선택 시 null.
- [ ] **AC-2** move-parent 다이얼로그 새 상위 부서 = `<select>`; 대상 부서 자신 옵션에서 제외; 미선택 시 최상위 승격(null).
- [ ] **AC-3** retired 부서는 상위 옵션에서 제외.
- [ ] **AC-4** console `pnpm test` GREEN(기존 다이얼로그 테스트 보존 + 드롭다운 신규), `tsc`/`lint`/`build` GREEN.

# Related Specs

- console-integration-contract §2.4.8 *Department write binding (PILOT)* (입력 위젯만 변경, 와이어 불변). ADR-MONO-013/015(console UI).

# Edge Cases

- 부서 0개(현 globex) → create 드롭다운은 "최상위"만; 첫 부서는 최상위로 생성(정상).
- 로드된 page 에 없는 부서는 옵션 미표시(파일럿 규모에선 page 1=전체; 향후 full-list fetch).
- move-parent 의 깊은 cycle(손자→조상)은 UI 가 못 막음 → producer `MASTERDATA_PARENT_CYCLE` inline(기존 동작).

# Failure Scenarios

- `<select>` 가 `<input>` 잔재와 testid 충돌 없이 교체 — 기존 테스트(parent 미입력 경로) 보존 확인.

# Test Requirements

- `DepartmentWriteDialog.test.tsx`: 드롭다운이 active 나열·retired 제외, 선택→parentId 전송, move-parent target 제외. 기존 create/retire/move 테스트 보존.
- `pnpm test` + `tsc` + `lint` + `build`.
- Local: console-web 재빌드+재기동; create 다이얼로그에서 상위 부서 드롭다운 동작 확인.

# Definition of Done

- [ ] DepartmentWriteDialog select 교체 + DepartmentList 전달 + 테스트.
- [ ] console `pnpm test`/`tsc`/`lint`/`build` GREEN.
- [ ] Local 재빌드+재기동 확인.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 "상위 부서 ID 어떤걸 넣어야돼?" → 목록이 ID 미노출이라 UUID 입력 불가 = UX 갭 → 드롭다운 선택. 메타: write affordance 를 추가할 때 참조 입력(부모 등)은 raw-id 텍스트가 아니라 기존 엔터티 선택 위젯이어야 — 사용자가 식별자를 알 수 없음. [[project_platform_console_adr_013]]
