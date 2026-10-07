# Task ID

TASK-PC-FE-309

# Title

ERP 결재 목록·상세가 부서·직원·기안자·결재선을 UUID 그대로 찍는다 — `TASK-PC-FE-276`/`277` 의 census 둘 다 이 화면을 안 봤다

# Status

done

# Owner

platform-console

# Task Tags

- erp-ops
- ui
- readability
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 16 — erp 결재 한 건 처리).
- 선행 읍을 것 — **이 티켓은 중복이 아니다, 이유를 적는다**:
  - `TASK-PC-FE-276`(DONE) 은 `/erp/masters` **8곳**만 고쳤다(`masterRefLabel`/`codeName`
    헬퍼 + `data-master-ref` 마커 + 회귀 가드를 그 자리에서 만들었다).
  - `TASK-PC-FE-277`(DONE) 은 다른 12개 도메인의 48(재집계 66)곳을 census 했지만
    **`features/erp-ops/` 를 통째로 제외**했다 — 이유는 "276 이 이미 고쳤다" 였는데, 276
    의 실제 범위는 `/erp/masters` 뿐이었다. 즉 **결재(`approval-*`) 화면은 276 의 범위에도
    277 의 census 모집단에도 들어간 적이 없다** — 이것은 두 티켓 중 하나가 "결함 아님"으로
    판정한 재방문이 아니라, 둘 다 들여다본 적이 없는 census 공백이다.

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

- `/erp/approvals` 목록: 대상(`대상`) 칸이 `부서 · 01a10fd2-742e-…` / `직원 · 01a10fd2-78b3-…`
  로 렌더된다.
- 결재 상세: 기안자가 `0199de70-…ad04`, 결재선/이력의 처리자(actor)가 `0199de70-…ad03` 로
  렌더된다.

둘 다 **다른 엔티티(부서/직원/운영자)를 가리키는 참조 칸**이다 — `TASK-PC-FE-277` 이
세운 판정 기준(자기 식별자 vs 참조, Edge Cases 참조)으로는 명백히 "참조" 쪽이고, 그
기준으로는 결함 후보다.

관련 파일(확인됨, `git ls-files`):

```
features/erp-ops/components/ApprovalScreen.tsx
features/erp-ops/components/ApprovalDetail.tsx
features/erp-ops/components/approval-common.tsx
features/erp-ops/api/approval-types.ts / approval-reads.ts
```

`shared/lib/master-ref-label.ts` (276 이 만들고 277 이 `erp-ops/lib/` → `shared/lib/` 로
옮긴 공용 헬퍼)가 이미 존재한다 — **새 포맷을 만들 필요가 없다.**

---

# Goal

ERP 결재 목록·상세의 참조 칸(부서·직원·기안자·결재선 처리자)이 UUID 대신 이름(또는
`CODE · 이름`)을 보여준다 — `/erp/masters` 와 같은 패턴으로.

---

# Scope

## In Scope

- `TASK-PC-FE-277` 과 같은 술어로, `features/erp-ops/components/Approval*.tsx` +
  `approval-common.tsx` 범위만 다시 센다(census, 추측하지 않는다).
- 각 참조 칸이 **자기 식별자인지 참조인지** 가른다(277 의 기준 그대로: 결재 자신의 id는
  자기 식별자, 부서/직원/기안자/처리자는 참조).
- 참조로 판정된 칸에 대해 **런타임 값**(시드/픽스처)으로 UUID 인지 읍을 수 있는 코드인지
  확인한다 — 콘솔 zod 는 전부 `z.string()` 이라 선언으로는 못 가른다(277 이 이미 실측).
- 결함으로 판정된 칸만 `shared/lib/master-ref-label.ts` 의 `masterRefLabel`/`codeName` 으로
  교체하고 `data-master-ref` 마커를 달아, `tests/unit/erp-master-ref-names.test.tsx`
  (276/277 의 회귀 가드)의 모집단을 넓힌다.

## Out of Scope

- 새 포맷/헬퍼 생성 — `masterRefLabel`/`codeName` 을 재사용한다.
- 새 가드 작성 — 기존 `erp-master-ref-names.test.tsx` 를 넓힌다.
- 결재 자신의 id(목록/상세에 그 결재 건을 식별하는 자리) — 운영자가 그 id 로 검색/지원
  요청을 받을 수 있으므로 이름으로 바꾸지 않는다.
- DTO 에 이름/코드가 없어 화면에 닿지 않는 자리의 백엔드 계약 변경 — 발견되면 후속 티켓
  (`TASK-PC-FE-277` 이 `TASK-MONO-659` 를 그렇게 분리한 것과 같은 패턴).

---

# Acceptance Criteria

- [x] **AC-0 (census)** — `TASK-PC-FE-277` 의 술어로 `approval-*` 범위를 다시 세고, 몇
      곳이 있는지·그중 몇이 참조인지 표로 남긴다.
- [x] **AC-1 (가르기)** — 참조로 판정된 칸마다, 그 이름의 출처가 **그 화면에 실제로
      닿는지** 확인한다(277 AC-1 이 겪은 함정: "시스템 어딘가에 이름이 있다"와 "이 화면이
      받는 props/스키마에 이름이 있다"는 다른 질문이다).
- [x] **AC-2** — 이름이 닿는 칸만 `masterRefLabel`/`codeName` 으로 교체 + `data-master-ref`
      마커. 이름이 안 닿는 칸은 추측하지 않고 ⚪ + 후속 티켓으로 분리.
- [x] **AC-3** — `erp-master-ref-names.test.tsx` 의 모집단이 늘어난다(새 가드를 짓지
      않는다). bite(한 곳을 되돌리면 빨강) 확인.
- [x] **AC-4** — 결재 자신의 id 는 손대지 않았음을 확인(회귀 — 자기 식별자 보존).

---

# 🟢 착수 기록 (2026-10-06/07 UTC)

## AC-0 — census (술어: 277 와 동일 — 「다른 엔티티의 id 를 보이는 텍스트 자리에
그대로 렌더」, `key=`/`data-testid=`/`value=`/prop 전달 제외)

`features/erp-ops/components/ApprovalScreen.tsx` + `ApprovalDetail.tsx` +
`approval-common.tsx` 를 다시 셌다. **7곳 · 2파일(실제 렌더가 있는 파일) · 5종류**,
전부 참조(자기 식별자 0):

| # | 자리 | 필드 | 종류 |
|---|---|---|---|
| 1 | `ApprovalScreen.tsx:205`(목록) | `subjectType`+`subjectId` | 대상(부서/직원) |
| 2 | `ApprovalDetail.tsx:85`(상세) | `subjectType`+`subjectId` | 대상(부서/직원) — 목록과 같은 종류 |
| 3 | `ApprovalDetail.tsx:89` | `submitterId` | 기안자 |
| 4 | `ApprovalDetail.tsx:150`(stages 루프) | `stage.approverId` | 결재선 단계별 승인자 |
| 5 | `ApprovalDetail.tsx:171`(legacy fallback) | `approverId` | 결재자(단일단계, stages 없을 때) |
| 6 | `ApprovalDetail.tsx:201`(history 루프) | `h.actor` | 이력 처리자 |
| 7 | `ApprovalDetail.tsx:213`(delegation marker) | `h.actingForApproverId` | 대결 대상(원 승인자) |

`approval-common.tsx` 자체에는 참조 렌더가 없다(순수 라벨/배지 헬퍼 — 처음부터
그렇게 설계됨, 기안의 전제가 틀렸다: `approval-common.tsx` 는 **범위 파일**이 아니라
**공유 프리젠테이션 헬퍼**일 뿐이었다). `api/approval-types.ts`/`approval-reads.ts`
는 렌더가 없는 타입/조회 파일이라 census 모집단 밖(기안이 "확인됨" 으로 적은 파일
목록 그대로, 렌더 라인만 추렸다).

결재 자신의 id(`r.id`/`data.id`)는 **어디서도 보이는 텍스트로 렌더되지 않는다** —
`key=`/`data-testid=` 전달뿐이다. 그래서 AC-4(자기 식별자 보존)는 "안 건드렸다" 로
자명하게 닫힌다 — 애초에 건드릴 자리가 없었다.

## AC-1 — 가르기: 「이름이 이 화면에 닿는가」

277 AC-1 이 겪은 함정("시스템 어딘가에 이름이 있다" ≠ "이 화면의 props/스키마에
이름이 있다")을 그대로 적용했다. `approval-api.md` 실측: `ApprovalSummarySchema`/
`ApprovalRequestSchema`/`ApprovalStageSchema`/`ApprovalHistoryEntrySchema` 전부
참조 필드가 **id 뿐**이다(이름/코드 필드가 없다) — 1차 판정은 "안 닿는다" 였다.

🔴🔴 **그런데 "DTO 에 이름이 없다" 는 "콘솔이 못 푼다" 와 같은 질문이 아니었다** —
`TASK-PC-FE-276` 의 Method A(이미 있는 **id 단건 조회 훅**으로 참조를 푼다,
`DepartmentDetail`/`EmployeeDetail`/`CostCenterDetail` 가 자기 `parentId`/
`departmentId`/`jobGradeId`/`costCenterId` 를 그렇게 푼다)가 **여기도 그대로 적용된다**:

- **대상(subjectId)** — `approval-api.md` § POST `.../submit` 이 "subjectId must
  resolve to a live master **of subjectType**" 을 검증한다 — 대상은 항상
  `DEPARTMENT|EMPLOYEE` 마스터다. `erp-ops` 안에 이미 `useDepartment(id)`/
  `useEmployee(id)` 가 있다(같은 feature, 새 import 경계 없음). **id 단건 GET**
  이라 276/277 이 목록-기반 조회에서 겪은 페이지네이션 함정도 없다.
- **기안자/결재자/이력 처리자/대결 대상** — `approval-api.md` 의 전 예시가
  `emp-...` 형이고, create 요청의 `approverId` 설명이 *"the single-stage approver
  (**employee id**)"*, history `actor` 설명이 *"JWT sub / **approver / submitter
  id**"* 다(즉 언제나 submitterId 또는 approverId 공간) — 전부 **직원(employee)**
  이다. 같은 `useEmployee(id)` 로 푼다.

🔴 **콘솔이 떠 있는 창에서 실측(라이브 축)은 이 세션에서 열리지 않았다** — 대신
① `approval-api.md`(계약, 1차 소스) ② `infra/demo/seed/seed-erp.sh`(실제 시드가
넣는 값의 모양) ③ 이미 커밋돼 있던 `tests/unit/sample-fixtures-schema-erp.test.ts`
(샘플 고정픽스처가 이미 "모든 approverId/submitterId/stage.approverId 는 직원으로
풀린다" 를 단언하고 있다, 277 이후 자리)를 **셋 다 열어서** 대조했다 — 셋이
일치했다(AC-1 이 요구한 "⚪ 로 적고 추측하지 마라" 의 반대 — 여기서는 셋이 합치해
**판정이 됐다**).

🔴🔴 **그런데 라이브 데모 시드는 계약을 어긴다** — `infra/demo/seed/seed-erp.sh`
§6 실측 주석: *"approverId 는 참조 검증을 받지 않는다 ... 그래서 승인자에 계정
UUID(콘솔 로그인의 sub)를 넣을 수 있고, 그것이 결재함을 채우는 유일한 방법이다"*.
결재함(inbox)에 뜨는 결재 2건은 **승인자 자리에 운영자 로그인 계정의 IAM sub**
(직원 마스터 id 가 아니다)를 넣는다 — 배경의 라이브 캡처(`0199de70-…`)가 UUID 로
보인 이유가 이것이다. 세 번째 건(DRAFT, "결재 재무팀 초안")만 진짜 직원 id 를 쓴다.
⇒ **이것은 이 화면의 결함이 아니라 데모 시드의 알려진 편법**이고, `useEmployee`
가 그 자리를 찾지 못하면 `masterRefLabel` 이 정직하게 `이름 확인 불가` 를
그린다(id 로 안 돌아간다) — 결함을 "가끔" 되살리지 않는다.

## AC-2 — 교체

7곳 전부 `shared/lib/master-ref-label.ts` 의 `masterRefLabel`/`codeName` 으로
교체했다(새 포맷 0개). 공용 컴포넌트 둘을 새로 만들었다(`approval-refs.tsx` —
`approval-common.tsx` 는 "no hooks, no data fetching" 이 문서화된 계약이라 거기
넣지 않았다):

- `ApprovalSubjectRef({ subjectType, subjectId })` — `subjectType` 에 따라
  `useDepartment`/`useEmployee` 로 풀고 `masterRefLabel`.
- `ApprovalEmployeeRef({ employeeId, field })` — `useEmployee` 로 풀고
  `masterRefLabel`. `field` 는 `data-master-ref` 접미사일 뿐(기안자/결재자/
  이력 처리자/대결 대상 넷 다 같은 조회).

마커: `approval.subjectId` / `approval.submitterId` / `approval.approverId`
(legacy) / `approval.stageApproverId` / `approval.actor` /
`approval.actingForApproverId`. 원본 id 는 `title` 속성으로 남긴다(276 의
관례 — 보이는 텍스트가 아니므로 가드가 안 문다). `data-testid` 는 **한 글자도
안 바꿨다**(`approval-approverId` 등 기존 e2e/유닛이 잡고 있다).

⚪ 로 분류한 자리는 **0개다** — AC-1 의 판정이 "셀 수 있다" 였으므로 모두 고쳤다
(277 처럼 콘솔 밖 자리로 분류된 것이 없다). ⇒ **후속 티켓 불필요** —
Out-of-Scope 의 "DTO 에 이름/코드가 없어 안 닿는 자리" 조건이 발생하지 않았다.

## AC-3 — 가드 확장 (새 가드를 짓지 않았다)

`tests/unit/erp-master-ref-names.test.tsx` 에 두 `describe` 를 추가했다(**45 →
57 tests**, +12). 술어·하한·대조군은 기존 `assertNoUuidInRefCells`/`refCells`
를 그대로 재사용 — 파일마다 단언을 복제하지 않았다.

- `useDepartment`/`useEmployee` 만 `vi.mock` 으로 결정적 룩업으로 막았다(이
  파일이 재는 축은 "이름이 셀에 그려지는가" 이지 "조회가 되나" 가 아니다 — 파일
  머리의 `use-org-nodes` 부분모의와 같은 이유). `DepartmentList`/`EmployeeList`
  등 기존 describe 가 쓰는 `useDepartments`/`useEmployees`(복수, list)/mutation
  훅은 전부 `...actual` 로 실 구현 그대로 남겼다 — 간섭 없음(45개 기존 테스트
  재실행 확인, 전부 그대로 green).
- **대조군**: 조회 밖(마스터에 없는) 대상 → `이름 확인 불가`, id 로 안 돌아감.
  데모 편법(운영자 sub, 직원 마스터에 없음)의 `actingForApproverId` → 같은
  `이름 확인 불가`, 원본 UUID 는 `title` 에만.
- **bite** — 주입·실행·물기를 **실제 코드 되돌리기**로 직접 증명했다(스크래치
  백업 → `ApprovalScreen.tsx`/`ApprovalDetail.tsx` 를 각각 구 버전으로 되돌림 →
  빨강 확인(목록: 참조 셀 0개 — 하한 3 / 상세: 참조 셀 5개 — 하한 6) → 원복 →
  `cmp` 로 바이트 동일 확인 → 재실행 초록). 기존 파일의 합성 bite(마크업 직접
  그리기)와는 다른, 이 티켓만의 **실제 소스 되돌리기** 증명.

`ApprovalScreen.test.tsx`(상태기계 전용 스위트, 23 tests)는 그대로 쓴다 — 거기
픽스처는 department/employee 조회 모의가 없어 전부 `이름 확인 불가` 로 정직하게
떨어진다(새 결함이 아니라 "이 스위트는 마스터 참조를 안 잰다" 는 사실이 이제
눈에 보인다). 리터럴 `'emp-a'` 텍스트를 기대하던 단언 2곳(legacy approverId 폴백
· 대결 마커)만 `MASTER_REF_UNRESOLVED` 로 갱신했다 — 상태기계 단언(전이/바디
payload)은 전부 그대로. `beforeEach` 에 안전 기본 fetch 스텁(404)을 추가해
`initialRequests`/`initialInbox` 만 시딩하던 기존 테스트들이 새 `useDepartment`/
`useEmployee` 호출로 실 네트워크를 안 태우게 했다(개별 테스트의 `vi.stubGlobal`
재호출이 그대로 덮어써 간섭 없음).

## AC-4 — 자기 식별자 회귀

AC-0 에서 이미 확인: 결재 자신의 id 는 어디서도 보이는 텍스트가 아니었다
(렌더 전부가 `key=`/`data-testid=`). 손댄 적이 없고, 새 가드에도 전용 회귀
케이스를 추가했다(`AC-4 회귀` — `container.textContent` 에 `'appr-1'` 없음을
직접 단언).

## 검증

- `tsc --noEmit` rc=0.
- `pnpm lint` (`next lint`) — No ESLint warnings or errors.
- `erp-master-ref-names.test.tsx` **57/57**(45 에서 +12) · `ApprovalScreen.test.tsx`
  **23/23** · 콘솔 유닛 전량 **337 files / 3812 tests** 전부 초록(`--minWorkers 2
  --maxWorkers 2`).
- `scripts/` **손대지 않았다** — 가드 개수 가드의 분모가 안 움직인다.

## ⚪ 이 세션이 못 잰 것

- **라이브 화면 실측** — 23차 창 배경의 캡처와 같은 조건(콘솔이 뜬 세션)에서
  고친 화면을 다시 보지 않았다. 판정은 계약 문서 + 시드 스크립트 + 기존 샘플
  픽스처 테스트 3종 대조로 했다(위 AC-1). 오케스트레이터가 다음 창에서 측정.
- **`TASK-MONO-764` 23차 창 점검표의 흐름 16(결재 한 건 처리) 재확인** — 이
  변경이 그 흐름을 다시 통과시키는지는 다음 라이브 창의 몫이다.

---

# Related Specs

- `projects/erp-platform/specs/contracts/http/`(결재 목록/상세 DTO — 이름/코드 필드 유무 확인)
- `projects/platform-console/apps/console-web/src/features/erp-ops/lib/` 는 더 이상 쓰지
  않는다 — `shared/lib/master-ref-label.ts` 가 정본(`TASK-PC-FE-277` AC-0 이동).

# Related Contracts

- 없음(발견되면 Out of Scope 의 후속 티켓이 가져간다)

---

# Edge Cases

- 기안자/결재선 처리자는 **운영자**(이름 있음) 참조, 부서/직원은 **마스터데이터** 참조 —
  서로 다른 조회 경로일 수 있다.
- 결재 이력(history)의 처리자가 **이미 탈퇴/삭제된 운영자**일 경우 이름 조회가 실패할 수
  있다 — `TASK-PC-FE-277` 의 `이름 확인 불가` 폴백 패턴을 따른다(id 로 조용히 되돌아가지
  않는다).

# Failure Scenarios

- **새 라벨 포맷을 만든다** — `codeName`/`masterRefLabel` 이 이미 있다(277 Failure 4 와
  동일한 함정).
- **결재 자신의 id 를 이름으로 바꾼다** — 운영자가 그 id 로 검색/지원 요청을 받는 경로를
  깨뜰린다.
- **`data-master-ref` 마커를 안 단다** — 고쳐도 회귀 가드 밖이라 다음에 되돌아가도 안 보인다.
- **선언(zod 스키마)만 보고 UUID 여부를 판정한다** — 콘솔 zod 는 전부 `z.string()`,
  런타임 값을 확인해야 한다(277 이 실측으로 증명).

---

# 닫기 기록 (2026-10-07 UTC, 4차원 검증)

- (a) PR #4187 `state=MERGED`, squash `b49f114d3`.
- (b) `origin/main` 이 `b49f114d3` 를 포함.
- (c) 머지된 PR 의 `statusCheckRollup` 실패 0 · 대기 0.
- (d) AC 절을 열어 동사대로 읽음: AC-0~AC-4 전부 [x]. 착수 기록의 «⚪ 이 세션이 못 잰 것»(라이브 화면 재확인)은 AC 가 아니다 — 데모 시드의 결재자 칸은 후속 `TASK-PC-FE-311`(#4202)이 맡았다.
