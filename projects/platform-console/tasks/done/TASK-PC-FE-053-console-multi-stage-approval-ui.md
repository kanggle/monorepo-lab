# Task ID

TASK-PC-FE-053

# Title

**콘솔 다단계 결재 UI — approval v2.0/v2.1 백엔드를 결재함에 노출 (PC-FE-051 결재함 확장).** TASK-ERP-BE-012(다단계 결재선 + IN_REVIEW) + TASK-ERP-BE-013(대결/위임)이 라이브화한 백엔드를 콘솔 결재함이 소비: create 시 **1~N 단계 결재선**(approverIds 순서 입력) + 상세에 **단계 진행 타임라인**(각 단계 결재자·PENDING/APPROVED·현 단계 표시) + **`IN_REVIEW` 상태** badge + history `stage`/`actingForApproverId`(대결 승인) 표시. **하위호환**: 단일 결재자 입력 = 1단계(기존 동작). `다단계 결재(백엔드) → 콘솔(단계 진행 가시화)` 루프 완결 — 이번 세션 백엔드 3-증분의 사용자-가시화.

# Status

done

# Owner

frontend-engineer (platform-console console-web; TASK-ERP-BE-012/013 approval v2.0/v2.1 라이브 — 이 task 는 콘솔 UI + proxy 확장)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **선행 (cross-project, erp)**: TASK-ERP-BE-012 (다단계 + IN_REVIEW, main 머지됨 `b749c1f9`) + TASK-ERP-BE-013 (대결/위임, main 머지됨 `650ae89c`). 콘솔이 런타임 호출(approval-api.md v2.0/v2.1 amendment: create `approverIds`, detail `stages`/`currentStage`/`totalStages`, status `IN_REVIEW`, 전이 `actingForApproverId`).
- **builds on**: TASK-PC-FE-051 (콘솔 결재함 — 단일단계 list/detail/inbox/create/4 전이; 이 task 가 다단계로 확장). PC-FE-048 generic write dialog 선례.
- **realises**: 이번 세션 approval 백엔드 3-증분(BE-009/012/013)의 콘솔 parity — 단일단계만 처리하던 PC-FE-051 을 다단계+IN_REVIEW 로 확장.
- **decision (user, 2026-06-05)**: 다음 작업 = 콘솔 다단계 결재 UI(#1).

# Goal

운영자-admin 이 콘솔 결재함에서 1~N 단계 결재선을 가진 결재 요청을 생성하고, 상세에서 각 단계의 결재자·승인 여부·현 단계를 타임라인으로 보며, `IN_REVIEW`(검토중) 상태와 대결 승인(`actingForApproverId`)을 인지할 수 있다. approval-service 의 다단계 상태기계가 콘솔 운영 화면에서 end-to-end demonstrable.

# Scope

## In Scope

- **types(zod) 확장**(`features/erp-ops/api/approval-types.ts`):
  - `APPROVAL_STATUSES` 에 `IN_REVIEW` 추가(non-terminal — `TERMINAL_APPROVAL_STATUSES` 불변); `allowedTransitionsFor('IN_REVIEW')` = `['approve','reject','withdraw']`(SUBMITTED 와 동일, 현 단계 결재자 가정).
  - `ApprovalRequestSchema`/`ApprovalSummarySchema` 에 **`stages`**(`[{stageIndex:int, approverId, status:'PENDING'|'APPROVED'(자유문자 tolerant)}]`, optional/`.passthrough()`) + **`currentStage`**(optional int) + **`totalStages`**(optional int) 가산. **NON_NULL absent**(finalized 시 currentStage absent → optional).
  - `ApprovalHistoryEntrySchema` 에 **`stage`**(optional int) + **`actingForApproverId`**(optional, 대결 시 onBehalfOf) 가산.
  - `CreateApprovalInput` 에 **`approverIds?: string[]`**(순서 1~N) 추가(기존 `approverId` 유지); `CreateApprovalBodySchema` 가 `approverIds`(non-empty 배열) ∨ `approverId` 정확히 하나 수용(proxy 검증). tolerant 파싱 — 미래 enum/필드 비throw.
- **api client**(`approval-api.ts`): `createApprovalRequest` 가 `input.approverIds?.length` 시 body `{approverIds}`, 아니면 `{approverId}` 전송(legacy 하위호환). 나머지(submit/approve/reject/withdraw/detail/list/inbox) 와이어 불변.
- **proxy**(`app/api/erp/approval/requests/route.ts` POST): create body 검증을 `approverIds` ∨ `approverId` 둘 다 허용으로 확장(기존 에러매핑·server-only 토큰 불변).
- **components**(`ApprovalScreen.tsx`):
  - **create dialog**: 단일 `결재자 ID` 입력 → **순서있는 결재선 입력**(stage 행 list: 각 행 approver id + 추가/삭제 버튼; 최소 1행; 순서=결재 단계). 1행이면 legacy `approverId` 전송, 2+면 `approverIds`. (subject/employee picker 고도화는 범위 밖 — id 입력 + 가능 시 기존 masterdata 재사용 degrade.)
  - **detail**: `결재자` 단일 행 → **단계 진행 타임라인**(`stages` 순서대로 stage k · approverId · PENDING/APPROVED badge · `currentStage` 표시[현 단계 강조]); `IN_REVIEW` status badge(검토중) + STATUS_LABEL 추가. history 항목에 `stage`(N단계) + `actingForApproverId` 있으면 "(대결: <approverId> 대신)" 표시. `currentStage`/`totalStages` "k/N 단계" 요약.
  - list/inbox row 의 status badge 가 `IN_REVIEW` 정상 렌더(STATUS_LABEL/tone). 상태 필터에 IN_REVIEW 포함(APPROVAL_STATUSES 자동).
- **에러 graceful 보존**: 기존 approval-error 매핑 그대로(403 not-authorized-approver=현 단계 결재자 아님/대결 무권한 inline 등). 다단계로 인한 신규 크래시 0.
- **tests**: 컴포넌트(다단계 create=approverIds 전송·1단계=approverId / detail 단계 타임라인·currentStage 강조·IN_REVIEW badge·history stage+actingForApproverId / list IN_REVIEW badge) + api client 단위(approverIds vs approverId payload, stages/currentStage absent 파싱) + proxy 단위(approverIds ∨ approverId 검증). console-web **vitest + tsc --noEmit + lint + build** GREEN(MONO-166).

## Out of Scope

- approval backend — TASK-ERP-BE-012/013(소비만).
- **위임 grant 관리 UI**(create/revoke delegation, "나에게 위임된" 목록) — 별 PC-FE 후속(BE-013 grant 관리; 이 task 는 다단계 + 대결 승인 read-only 표시만).
- subject/approver employee picker 고도화(드롭다운 자동완성) — 기본 id 입력 + degrade 허용.
- read-model 의 단계 투영 — read-model 무변경(approval REST 가 단계 권위, E5).

# Acceptance Criteria

- [ ] **AC-1** create dialog 에서 **순서있는 결재선**(N 행 approver id, 추가/삭제) 입력 → 2+ 단계면 `approverIds` 전송, 1 단계면 `approverId`(legacy) 전송. 콘솔이 다단계 결재 요청 생성 가능.
- [ ] **AC-2** detail 에 **단계 진행 타임라인**: `stages` 순서·각 단계 approverId·PENDING/APPROVED·현 단계(`currentStage`) 강조·"k/N 단계" 요약. `IN_REVIEW`(검토중) status badge 정상.
- [ ] **AC-3** history 에 `stage`(N단계 시) + `actingForApproverId` 있으면 대결 표시("<delegate> (대결: <approver> 대신)"). absent 필드(stage/actingForApproverId/currentStage) → 정상 렌더(crash 0, NON_NULL).
- [ ] **AC-4** 하위호환: 1단계 입력 = 기존 단일 approverId 동작(회귀 0); 기존 PC-FE-051 list/detail/inbox/4 전이/에러 inline 보존. list/inbox 의 IN_REVIEW badge.
- [ ] **AC-5** proxy create = `approverIds`(non-empty) ∨ `approverId` 정확히 하나 검증; 토큰 server-only·에러매핑 불변. api client payload 정확.
- [ ] **AC-6** console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). 기존 erp-ops/approval/operators 회귀 0.

# Related Specs

- consume: `projects/erp-platform/specs/contracts/http/approval-api.md` v2.0/v2.1 amendment(ERP-BE-012/013 — create approverIds, detail stages/currentStage/totalStages, IN_REVIEW, actingForApproverId). console-integration-contract.md §2.4.8(approval surface — multi-stage note 추가).

# Related Contracts

- consume: approval-api.md(v2.0/v2.1). console-web 자체 proxy(requests POST 가 approverIds∨approverId).

# Edge Cases

- 1단계 결재선: legacy `approverId` 전송(기존 동작); detail 단계 타임라인은 1행.
- `stages`/`currentStage`/`totalStages` absent(구버전 응답·finalized): optional 파싱, UI degrade("단계 정보 없음" 또는 단일 결재자 표시).
- IN_REVIEW 상태: 검토중 badge(non-terminal); 현 단계 결재자만 approve 가능(producer authz, 콘솔은 액션 노출 + 403 graceful).
- 대결 승인(history `actingForApproverId`): "<delegate> (대결: <approver> 대신)" 표시; absent 시 일반 표시.
- 중복/빈 결재선 입력: 클라 사전 검증(최소 1행·blank 제거) + producer 422 `APPROVAL_ROUTE_INVALID`(duplicate/self) graceful inline.
- 다단계 create 후 submit→stage1 approve→IN_REVIEW→stage2 approve→APPROVED: detail 새로고침으로 currentStage 전진·타임라인 갱신.

# Failure Scenarios

- approval-service 불가(503/timeout): 섹션 degrade(기존 패턴).
- 빈 결재선 제출: 클라 차단(최소 1 approver) + producer 422 graceful.
- 잘못된 메서드 proxy: 405/미노출.
- 다단계 응답 파싱 실패(미래 필드): tolerant `.passthrough()` — crash 0.

# Test Requirements

- 컴포넌트: `ApprovalScreen.test.tsx` 확장(다단계 create approverIds·1단계 approverId / detail 단계 타임라인·currentStage 강조·IN_REVIEW badge·history stage+actingForApproverId / list IN_REVIEW). api client 단위(approverIds vs approverId payload, stages/currentStage absent). proxy 단위(approverIds∨approverId 검증, 메서드 불변).
- console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). 기존 회귀 0.
- Local(선택): erp 재배포 후 라이브 다단계 create→submit→다단계 approve 루프 확인.

# Definition of Done

- [ ] types(IN_REVIEW/stages/currentStage/totalStages/approverIds/history stage+actingForApproverId) + api client(approverIds payload) + proxy(검증) + ApprovalScreen(create 결재선 입력 + detail 단계 타임라인 + IN_REVIEW badge + 대결 표시).
- [ ] console-web vitest + tsc + lint + build GREEN(MONO-166); 회귀 0.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (UI slice — PC-FE-051 결재함 + PC-FE-048 generic write dialog 선례 재사용; 신규 도메인 로직 0, 백엔드 소비만; 다단계 입력 list + 단계 타임라인 표시 + IN_REVIEW badge + 대결 read-only 표시). 사용자 "콘솔 다단계 결재 UI" 선택. 메타: 이번 세션 approval 백엔드 3-증분(단일→다단계→위임)의 사용자-가시화 — `다단계 결재 → 콘솔 단계 진행` 루프 완결. 위임 grant 관리 UI = 별 후속(이 task 는 다단계 + 대결 승인 read-only 표시). [[project_platform_console_adr_013]]
