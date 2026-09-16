# Task ID

TASK-PC-FE-285

# Title

ERP 화면이 샘플로 선다 — 개요·결재·위임·마스터·조직도 (`ADR-MONO-074` 실행 4/8)

# Status

review

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **erp 도메인 GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/erp` · `/erp/approval` · `/erp/delegation` · `/erp/guide`(정적) · `/erp/masters` · `/erp/orgview`

코어: `callFlatEnvelopeGateway` (erp masterdata · approval · delegation 클라이언트). ADR 인벤토리: GET **18** · 쓰기 **20**.

---

# Scope

## In Scope

- 위 화면의 erp GET 픽스처 + 원장 `ready`
- 개요 타일의 집계(마스터 5종 수 · 결재 대기 · 활성 위임)가 **목록 픽스처에서 파생**되어 서로 맞는다

## Out of Scope

- 결재 전이·위임 부여/회수 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [x] **AC-0** 282 AC-0 표·원장에서 erp `pending` GET 목록을 이 파일에 적는다. — § Implementation notes "AC-0 — 재인벤토리" (3 surface; ADR 18 은 다른 단위이지만 우연히 같은 수로 수렴한다).
- [x] **AC-1** 전부 `ready` + 실제 파서 통과 테스트. — `tests/unit/sample-fixtures-schema-erp.test.ts` (3 surface 전부, 실제 프로덕션 zod 스키마로 파싱).
- [x] **AC-2** «(샘플)» 규칙 테스트 초록(부서명·직원명·결재 제목 끝). 🔴 사번·부서 코드·상태 enum 제외. — `sample-label-rule.test.ts` 의 `it.each(SAMPLE_FIXTURE_DOCUMENTS)` 가 erp 문서 3개를 순회, 초록. 새 키 분류는 `label-rule.ts` TASK-PC-FE-285 절.
- [x] **AC-3** 🔴 **개요 수 = 목록 행 수**(마스터 5종·결재 대기·활성 위임). 개요와 목록이 다른 말을 하면 합성이어도 «고장» 이다 — 테스트로 문다. — 별도 SUMMARY 상수를 두지 않고 `getErpOverviewState`(`page=0&size=1`)와 화면(`page=0&size=20`)이 **같은 필터 위에서 같은 배열**을 읽도록 설계 + `sampleResponse` 두 호출을 비교하는 테스트로 증명(§ "AC-3 — router 를 통해" 절), bite 로 발화 증명.
- [x] **AC-4** 조직도의 부서 참조·결재선의 결재자가 마스터 픽스처 안에서 **해석된다**(`masterRefLabel` 가 id 로 되돌아가지 않는다). — § "AC-4" 절: 부서 계층·직원 3종 참조·비용센터 참조·결재 approverId/submitterId/stage 참조·위임 delegatorId/delegateId 전부 실제 detail 조회로 해석, bite 로 발화 증명.
- [x] **AC-5** 대표 쓰기 1개(결재 승인) → «샘플 화면에서는 실행되지 않습니다». — `sample-fixtures-schema-erp.test.ts` "AC-5" 절: 부서 생성/결재 승인/위임 생성 POST 3종 → 403 `SAMPLE_READ_ONLY` → `messageForCode` 매핑 문구 정확히 일치.
- [x] **AC-6** `e2e-smoke` 익명 `/erp/approval` 렌더 1칸. — `e2e-smoke/sample-visitor-erp.spec.ts`, `pnpm e2e:smoke` rc=0 (22 passed, 신규 1건 포함). 행 수는 단언하지 않음(작업지시서 지시).

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `docs/adr/ADR-MONO-050-cross-service-identifiers-are-codes.md` D9 (참조는 코드)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (erp 절)

# Edge Cases

- 결재 이유(`X-Operator-Reason`) 입력 후 제출 → 거부, 입력 유지.
- 404-as-empty 가 없는 도메인이므로 없는 id 는 일반 404.

# Failure Scenarios

- 개요와 목록의 수 불일치 ⇒ AC-3.
- 참조가 id 로 표시 ⇒ AC-4.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [x] 원장의 erp `pending` 0 — `coverage.ts` `SURFACE_COVERAGE`/`SCREEN_COVERAGE` 의 erp 행 전부 `ready` (3 surface + 5 screen; `/erp/guide` 는 이미 282 소유의 `static`).

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-17 UTC)

## AC-0 — 재인벤토리

`TASK-PC-FE-282`~`284` 는 원장 granularity 를 엔드포인트가 아니라 **surface**(게이트웨이 프로필 `logPrefix`)로
잡았다(282 D1) — 경로가 래퍼 여러 겹을 거쳐 조립되어 엔드포인트 추출기가 추출기 자신을 재는 문제가 이 티켓에도
그대로 적용된다. `coverage.ts` 의 erp `pending` 3 surface 를 그대로 인용한다:

| surface | 코드상 `logPrefix` 정의 | 화면 | GET (코드 읽기 스윕) |
|---|---|---|---:|
| `erp` | `erp-client.ts` (masterdata 5종 + read-model org-view + read-model delegation-facts 공용) | `/erp`(개요) · `/erp/masters` · `/erp/orgview` | 5×(list+detail)=10 + org-view(list+detail)=2 + delegation-facts(list+detail)=2 = **14** |
| `erp_approval` | `approval-call.ts` | `/erp`(개요 결재 대기) · `/erp/approval` | requests(list+detail)=2 + inbox=1 = **3** |
| `erp_delegation` | `delegation-api.ts` | `/erp`(개요 활성 위임 — 실은 `erp` surface 의 delegation-facts 를 읽는다, 위임 그랜트 자체는 `/erp/delegation` 화면만) | delegations(list, role 필터)=1 = **1** |

합계 **18** GET 리터럴 — 🔴 ADR 본문의 **18** 과 우연히 같은 숫자로 수렴한다(단위는 여전히 다르다: ADR 은
"호출 함수 이름" 코드 읽기 스윕, 위 표는 "이 티켓이 실제로 만든 핸들러 분기" 세기 — 282/283/284 가 이미
기록한 "단위가 다르다" 원칙을 그대로 따른다. 우연의 일치를 근거로 쓰지 않는다). 이 티켓의 **실제 범위는
원장의 erp `pending` 3 surface**이고, `SCREEN_COVERAGE` 의 erp `pending` 화면은 5개(`/erp` · `/erp/approval` ·
`/erp/delegation` · `/erp/masters` · `/erp/orgview`) — 작업지시서 나열과 일치(`/erp/guide` 는 이미 282 소유의
`static`).

## 스크린 ↔ 서페이스 매핑 (구현 중 실측)

- `/erp`(개요) → `getErpOverviewState` 가 5 마스터 `list*`(erp) + `listApprovalInbox`(erp_approval) +
  `listDelegationFacts({status:'ACTIVE'})`(erp, **erp_delegation 아님** — 활성 위임 카운트는 delegation
  **facts**(read-model) 를 읽지 delegation **grants**(approval-service 관리 화면)를 읽지 않는다) 를
  `?page=0&size=1` 로 fan-out. 이미 위 3 surface 로 커버되어 새 화면 원장 행만 필요.
- `/erp/masters` → `erp`(5 마스터 list+detail, `?page=0&size=20`).
- `/erp/orgview` → `erp`(read-model employee org-view).
- `/erp/approval` → `erp_approval`(requests list+detail+inbox).
- `/erp/delegation` → **두 표면**: `erp_delegation`(그랜트 관리 — `DelegationScreen`, role 필터) + `erp`(위임
  현황 read-model — `DelegationFactCard`, 기본 쿼리는 status 필터 없이 전체를 보여준다).

## 픽스처 세계 (하나로 엮은 데이터)

부서 4(본사 root·영업1팀·개발1팀·**영업2팀=RETIRED**) · 직원 4(김하나 ACTIVE/EMPLOYED·이민준 ACTIVE/ON_LEAVE·
**박지훈 ACTIVE/SEPARATED — RETIRED 부서를 참조**(E1 헤드라인 케이스, 과제 본문이 명시)·최수아 ACTIVE/EMPLOYED,
참조 전부 없음) · 직급 3(사원·대리·**차장=RETIRED**) · 비용센터 3(영업·개발·**폐지=RETIRED**) · 거래처
3(SUPPLIER·CUSTOMER·**BOTH=RETIRED**) · 결재 요청 5(SUBMITTED×2 — 둘 다 「나」(emp-sample-0001)의 미결함·
IN_REVIEW×1 — 다단계, 0단계는 「나」가 이미 승인했지만 현재 단계 결재자는 다른 사람이라 미결함에 **없다**·
APPROVED×1·REJECTED×1(사유 포함)) · 위임 그랜트 3(ACTIVE 무기한·ACTIVE 만료(validTo 과거)·REVOKED) · 위임
facts 3(그랜트와 동일 id, 그 중 1건은 BE-018 「회수가 부여보다 먼저 도착」edge — scope/validFrom 부재).

## 편차 / 구현자 선택

- **D1 「나」(caller) 아이덴티티 관례.** 결재 inbox·위임 grants 의 `role` 필터는 실제로는 producer 가 JWT
  `sub` 로 caller 를 구별하지만, 샘플에는 실제 세션이 없다. `emp-sample-0001` 을 고정 「나」로 관례화하고
  주석에 명시했다 — 뒤집으려면 `fixtures/erp.ts` 의 `ME` 상수 하나만 바꾸면 된다.
- **D2 활성 위임 카운트가 읽는 것은 delegation FACTS 이지 GRANTS 가 아니다.** `overview-state.ts` 를 읽고
  확인: `listDelegationFacts({status:'ACTIVE'})` — read-model, org-scope 인지. `/erp/delegation` 화면의
  `DelegationFactCard` 는 **기본 쿼리에 status 필터가 없어** 전체(ACTIVE+REVOKED)를 보여준다 — AC-3 테스트는
  그래서 `status=ACTIVE` 를 **명시적으로** 걸어 두 쿼리(overview 모양 vs list 모양)를 비교했다(같은 필터,
  다른 page/size). `DelegationFactCard` 자신의 status 드롭다운으로 ACTIVE 를 고르면 화면이 보여주는 수가
  곧 개요 수와 같다 — 화면 코드를 바꾸지 않고 검증했다.
- **D3 결재 「현재 단계 결재자」가 inbox 판정 기준.** 다단계 요청(`appr-sample-0002`)은 stage 0(「나」)가
  이미 APPROVED, stage 1(다른 직원)이 현재 대기 — inbox 는 **현재 단계**만 봐야 하므로 이 요청은 「나」의
  inbox 에 없다. 픽스처 자체가 `approverId` 필드를 "현재 단계 결재자"로 유지하도록 설계해(다단계 시
  `stages[currentStage].approverId` 와 동기화) inbox 판정이 한 필드만 보면 되게 했다 — 실제 producer 가
  `approverId` 를 무엇으로 채우는지는 문서화되어 있지 않아(과제 스코프 밖), 이 관례를 주석으로 남겼다.
- **D4 masterdata 404 코드.** 계약서(§ 2.4.8)가 명시한 `MASTERDATA_NOT_FOUND` 를 5 마스터 전부에 썼다.
  read-model(org-view/delegation-facts)과 delegation 그랜트의 404 코드는 `read-model-api.md` 를 이 워크
  트리에서 읽을 수 없어(erp-platform 프로젝트 스펙, 이 프로젝트 경계 밖) 합리적인 값(`MASTERDATA_NOT_FOUND`
  재사용 / `DELEGATION_NOT_FOUND`)을 골랐다 — 실제 producer 코드와 다를 수 있음을 여기 기록한다(⚪, 아래
  절 참조).
- **D5 `reason` 은 MACHINE 분류(기존 규칙 재확인).** 위임 회수/결재 반려 사유는 사람이 쓰는 자유 텍스트이지만
  `label-rule.ts` 의 `reason` 키는 **이미 IAM/ecommerce 티켓 이전부터** MACHINE 으로 분류되어 있었다(전역
  규칙, 이 티켓이 바꾸지 않음) — 그래서 이 값들에는 «(샘플)» 접미를 붙이지 않았다(처음엔 붙였다가
  `sample-label-rule.test.ts` 가 즉시 잡았다 — 아래 § 구현 중 잡힌 회귀).
- **D6 필터·검색 AC 없음 — 실제 화면과 정합.** 283/284 와 달리 이 티켓의 AC 에는 "노출된 필터·검색·페이지"
  요구가 없다(erp 마스터 5종 목록 화면은 실제로 페이지네이션 + `AsOfPicker` 뿐, 검색/상태 필터 UI 가 없다 —
  `DepartmentList.tsx`/`EmployeeList.tsx`/… 를 읽어 확인). 그래서 이 티켓은 마스터 목록에 `active`/
  `departmentId`/`partnerType` 등 계약상 존재하는 필터를 **픽스처 레벨에서는 지원**하되(정직성 — 나중에
  화면이 필터 UI 를 얻어도 픽스처가 막지 않는다) AC 로 강제하지 않았다. 결재 `status` 필터와 위임 facts 의
  4개 필터(모두 실제 화면 UI 에 존재)는 지원 + 테스트했다.

## AC-3 — router 를 통해 (내부 배열이 아니라)

`getErpOverviewState` 와 각 목록 화면이 **같은 `sampleResponse` 핸들러**를 다른 `page`/`size` 로 부르는
구조이므로(284 의 `*_SUMMARY` 손수-유지 상수 같은 별도 진실이 애초에 없다), AC-3 테스트는 두 호출을 모두
`sampleResponse` 로 실행해 `meta.totalElements` 를 비교한다 — 5 마스터·결재 inbox·활성 위임(facts) 전부.
bite 로 "손으로 적은 수가 필터된 행과 어긋나면 무는가"를 별도로 확인했다(§ Bites B1).

## AC-4 — 참조 해석 (router 를 통해)

`shared/lib/master-ref-label.ts` 의 `masterRefLabel`/`MASTER_REF_UNRESOLVED` 를 그대로 가져와, 모든 참조를
**실제 detail 엔드포인트 조회 결과**로 해석한다(정적 배열 `.find()` 가 아니라 `sampleResponse` 호출):
부서 계층(`parentId`) · 직원 3종 참조(`departmentId`/`jobGradeId`/`costCenterId`, RETIRED 부서 케이스 포함) ·
비용센터의 부서 참조 · 결재 `approverId`/`submitterId`/모든 stage 의 `approverId` · EMPLOYEE/DEPARTMENT
subject 참조 · 위임 grant/fact 의 `delegatorId`/`delegateId`. bite 로 "존재하지 않는 부서를 참조하면 무는가"
확인(§ Bites B2).

## 기존 테스트 파일 수정 — 1개 (283/284 D8 선례)

| # | 파일 | 원인 | 고침 |
|---|---|---|---|
| 1 | `sample-mode-cores.test.ts` › `callFlatEnvelopeGateway` "① pending GET" | `FLAT_PROFILE`(`logPrefix:'erp'`)가 이 티켓에서 ready 로 바뀜 | 282/283/284 D8 과 동일 패턴 — `logPrefix: 'no-such-surface'` 로 교체(단언 불변, 헤더에 이 티켓 인용) |

🔵 **282 가 이미 해 둔 일** — erp 도메인 전용 테스트(`erp-api.test.ts`·`erp-proxy.test.ts`·
`erp-read-model-proxy.test.ts`·`approval-api.test.ts`·`approval-proxy.test.ts`·`delegation-api.test.ts`·
`delegation-proxy.test.ts`)는 282 의 D8 대량 변환에서 **이미** "빈 쿠키 병=세션 없음"을 반쪽 세션(운영자
쿠키만)으로 바꿔 뒀다(각 파일 헤더에 `ADR-MONO-074` 인용 확인) — 이 티켓이 erp 표면을 `ready` 로 뒤집어도
그 파일들은 무수정 초록이다(전부 최소 한 쿠키를 세팅하고, 절대 빈 병으로 샘플 라우터에 닿지 않는다).
`erp-state.test.ts` 는 `eligible` 을 boolean 인자로 직접 주입해 레지스트리/세션을 아예 거치지 않아 원천적으로
무관하다. 이 관찰(§ "기존 erp 테스트 무영향" 확인)이 위 표가 1개뿐인 이유다 — 282/283/284 대비 이례적으로
적지만, 사전에 grep 으로 확인한 사실이지 눈감아 넘긴 것이 아니다.

## 구현 중 잡힌 회귀 (수정 전 `pnpm test` 1회, 4 셀 실패 — 커밋에는 없음, 전부 위 § 편차/D5 에서 고쳤다)

| # | 파일 | 원인 | 고침 |
|---|---|---|---|
| 1 | `sample-label-rule.test.ts` › `flat:erp` | `delegationFacts[2].reason` 에 «(샘플)» 접미(전역 규칙상 `reason`=MACHINE) | D5 — 접미 제거 |
| 2 | `sample-label-rule.test.ts` › `flat:erp_approval` | `requests[4].reason` + `history[1].reason` 동일 원인 | D5 — 접미 제거 |
| 3 | `sample-label-rule.test.ts` › `flat:erp_delegation` | `grants[2].revokedBy` 미분류 키 | `label-rule.ts` `MACHINE_KEYS` 에 `revokedBy` 추가(actor id, `createdBy`/`updatedBy` 와 같은 부류인데 처음에 빠뜨렸다) |
| 4 | `sample-router-isolation.test.ts` | 내 주석 "… distinct\n// from "reference present but retired"." 가 격리 가드의 순진한 정규식(`\bimport\|export\b.*?\bfrom\s*['"]…['"]`, 세미콜론까지 lazy)에 «export … from "…"» 로 오탐 — 배열 리터럴엔 세미콜론이 없어 훨씬 앞의 `export const` 부터 이 주석까지 하나의 매치로 잡혔다 | 주석을 "from "…"." 패턴이 안 생기게 재작성(의미 불변) |

TypeScript 컴파일 오류(커밋에 없음, `npx tsc --noEmit` 1차에서 잡힘): `deptRef` 재귀 함수가 반환 타입
추론 순환(TS7023/7022)을 일으켜 명시적 인터페이스(`DeptRefSeed`/`DeptPathNodeSeed`)를 추가했고, 테스트
파일의 `toBeLessThan(x.meta.totalElements)` 5곳이 `number|undefined` 타입 오류를 내 `?? 0` 을 붙였다(zod
스키마가 `totalElements` 를 `.optional()` 로 선언하기 때문 — 값 자체는 항상 있다).

## ⚪ 측정하지 못한 것 / 확인하지 못한 것

- **read-model(org-view/delegation-facts)·delegation 그랜트의 정확한 404 코드 문자열** — `read-model-api.md`
  는 `erp-platform` 프로젝트 스펙이라 이 워크트리에서 읽을 수 없다(§ D4). `MASTERDATA_NOT_FOUND`/
  `DELEGATION_NOT_FOUND` 를 합리적 추정으로 썼다 — erp-platform 팀이 실제 코드를 공개하면 이 두 상수만
  바꾸면 된다(`erp.ts` 의 `fixtureNotFound(...)` 호출 4곳).
- **`/erp` 개요의 알림벨 딥링크 예시(`sample-approval-1001` 등, `dashboards.ts` 소유)가 이 티켓의 결재
  픽스처 id 세계와 겹치지 않는다** — 282 가 만든 알림 인박스 픽스처는 `sourceId: 'sample-approval-1001'`
  같은 값을 쓰는데 이 티켓의 결재 요청은 `appr-sample-0001..5` 다. 방문자가 그 알림을 클릭해
  `/erp/approval?request=sample-approval-1001` 로 딥링크하면 상세 다이얼로그가 "찾을 수 없음"(graceful
  404)을 보인다 — 크래시는 아니지만 알림과 실제로 이어지진 않는다. `dashboards.ts` 는 282 소유 파일이라
  이 티켓 범위 밖으로 두고 여기 기록만 한다(다음 사람이 알림 픽스처를 건드릴 때 참고).
- **실제 Vercel 배포에서 샘플 방문자** — 로컬 production build + smoke 까지만 쟀다(283/284 와 동일 한계).

## 측정 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

BEFORE 는 이 워크트리를 `git stash push -u`로 되돌린 상태(분기점 `66a994df1` — TASK-PC-FE-283/284 머지 포함,
283/284 가 이미 남긴 것과 동일한 트리)에서 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `pnpm test` | BEFORE(`git stash`로 되돌린 이 워크트리) | rc=0 · **308 files / 3351 tests passed**, 실패 0 |
| `pnpm test` (1차 — 구현 직후) | AFTER | rc=1 · 2 files / 4 failed — 위 § "구현 중 잡힌 회귀" |
| `pnpm test` (2차 — 위 회귀 고친 뒤) | AFTER | rc=0 · 309 files / 3395 tests passed |
| `npx tsc --noEmit` (1차) | AFTER | rc=2 · 7 errors — 위 § "구현 중 잡힌 회귀" TS 절 |
| `npx tsc --noEmit` (최종) | AFTER | rc=0 |
| `pnpm lint` | AFTER(최종) | rc=0 · «No ESLint warnings or errors» |
| `pnpm test` (최종, bite 복원 확인 포함) | AFTER(최종) | rc=0 · **309 files / 3395 tests passed**, 실패 0 |
| `pnpm build` | AFTER(최종) | rc=0 (1차 시도는 `Exit code 255` — 원인 불명의 Windows 일시적 실패, 로그에 컴파일 에러 없음, 재시도에서 통과. 재현되면 별도 env 메모리 후보) |
| `pnpm e2e:smoke` | AFTER(최종, 프로덕션 빌드, 백엔드 전부 loopback 127.0.0.1:1) | rc=0 · **22 passed** — 기존 21개 + 신규 `sample-visitor-erp.spec.ts` 1개(AC-6) |

🔵 알려진 Windows flake(`LedgerOpsScreen.test.tsx`/`OperatorsScreen.test.tsx`)는 BEFORE·AFTER 모든 실행에서
재현되지 않았다(전부 실패 0).

### AC-3 — 개요 대 목록 수 표 (router 가 실제로 돌려주는 값)

| 항목 | 개요 쿼리(`page=0&size=1`) | 목록 화면 쿼리(`page=0&size=20`) | totalElements |
|---|---|---|---:|
| 부서 | `/api/erp/masterdata/departments?page=0&size=1` | `…?page=0&size=20` | 4 |
| 직원 | `/api/erp/masterdata/employees?page=0&size=1` | `…?page=0&size=20` | 4 |
| 직급 | `/api/erp/masterdata/job-grades?page=0&size=1` | `…?page=0&size=20` | 3 |
| 비용센터 | `/api/erp/masterdata/cost-centers?page=0&size=1` | `…?page=0&size=20` | 3 |
| 거래처 | `/api/erp/masterdata/business-partners?page=0&size=1` | `…?page=0&size=20` | 3 |
| 결재 대기 | `/api/erp/approval/inbox?page=0&size=1` | `…?page=0&size=20` | 2 |
| 활성 위임 | `/api/erp/read-model/delegations?status=ACTIVE&page=0&size=1` | `…?status=ACTIVE&page=0&size=20` | 2 |

모든 행에서 개요 쿼리와 목록 쿼리의 `totalElements` 가 **완전히 같다**(둘 다 같은 배열을 같은 필터로 읽고
page/size 만 다르기 때문 — 별도 SUMMARY 상수가 없어 구조적으로 어긋날 수 없다. 어긋나게 만들면 bite B1 이
잡는다).

### Bites (금지/결함 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 | 복원 후 |
|---|---|---|---|---|
| B1 | `sample-fixtures-schema-erp.test.ts` AC-3 (활성 위임 개요=목록) | `delegationFactsFixture` 의 `size===1` 분기에 `totalElements: 999` 하드코딩 | rc=1 · 1 file / 1 failed · "AC-3 — status=ACTIVE at page=0&size=1 … reports the SAME totalElements …" `expected 999 to be 2` | rc=0 · 41/41 |
| B2 | `sample-fixtures-schema-erp.test.ts` AC-4 (직원 참조 해석) | `emp-sample-0001.departmentId` 를 존재하지 않는 `dept-sample-9999` 로 | rc=1 · 3 files 단언 실패(직원 참조 해석 테스트 자신 + 필터 narrows 테스트 + org-view unresolved 테스트, 연쇄) | rc=0 · 41/41 |

B1·B2 모두 단독 주입 → 실행 → 복원 순으로 개별 확인했다. `grep -rn "BITE-" src tests e2e-smoke` = 0건(복원
확인). 전체 스위트 기준 최종 복원 확인은 § 측정의 "최종" 행.

### 병합 트리 관점 — DoD 대조군 (로그인 운영자 경로)

이 티켓이 건드린 파일은 `coverage.ts`(erp 3행 상태만 변경) · `label-rule.ts`(키 분류 추가만) ·
`fixtures/index.ts`(erp 배럴 추가만) · `fixtures/erp.ts`(신규) · `sample-mode-cores.test.ts`(erp 관련
1 셀의 `logPrefix` 만 no-such-surface 로 교체, 단언 불변) 뿐이다 — erp 화면 컴포넌트·hooks·api 클라이언트는
**한 줄도 안 바뀌었다**. 인증 운영자 경로 테스트(erp-api/erp-proxy/approval-api/approval-proxy/
delegation-api/delegation-proxy/erp-state 전부)는 무수정 초록이며, `pnpm test` 최종 실행이 그 안에 포함된
전체 스위트다(별도 대조군 실행을 분리하지 않았다 — 변경 파일이 sample-mode 전용이라 분리해도 같은 결과).
