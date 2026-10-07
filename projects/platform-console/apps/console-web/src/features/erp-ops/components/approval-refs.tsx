'use client';

import { useDepartment, useEmployee } from '../hooks/use-erp-ops';
import {
  masterRefLabel,
  type MasterRefTarget,
} from '@/shared/lib/master-ref-label';
import { SUBJECT_LABEL } from './approval-common';

/**
 * 결재(approval) 화면의 참조 칸 — TASK-PC-FE-309.
 *
 * `ApprovalSummary`/`ApprovalRequest`(+ `ApprovalStage`/`ApprovalHistoryEntry`)
 * 는 `subjectId`/`submitterId`/`approverId`/`actor`/`actingForApproverId` 를
 * **id 로만** 싣는다 — `approval-api.md` 실측(이름/코드 필드가 없다, `TASK-PC-FE-276`/
 * `277` 의 census 가 이 화면을 본 적이 없어 놓친 바로 그 자리, `TASK-PC-FE-309` 배경).
 *
 * 🔵 그러나 그 대상은 둘 다 **같은 feature(`erp-ops`)의 마스터**다 — 새 계약도, 새
 * feature-import 도 필요 없다:
 *  - `subjectId` → `DEPARTMENT|EMPLOYEE` 마스터. `submit` 이 "subjectId must
 *    resolve to a live master" 를 검증한다(계약 § POST .../submit) — 대상은
 *    항상 부서 또는 직원이다.
 *  - `submitterId`/`approverId`/`actor`/`actingForApproverId` → **직원
 *    (employee)**. 계약의 모든 예시가 `emp-...` 이고, create 요청의 `approverId`
 *    설명이 "the single-stage approver (**employee id**)" 다. `history.actor` 는
 *    "JWT sub / approver / submitter id" — 즉 언제나 submitterId 또는 approverId
 *    공간이다.
 *
 * 그 마스터들은 이미 id 단건 조회 훅(`useDepartment`/`useEmployee`)이 있고,
 * `DepartmentDetail`/`EmployeeDetail`/`CostCenterDetail` 가 **자기** 참조 칸
 * (`parentId`/`departmentId`/`jobGradeId`/`costCenterId`)을 똑같은 방법으로 푼다
 * (`TASK-PC-FE-276` Method A). id 단건 GET 이라 **페이지네이션 함정도 없다** —
 * "목록 밖의 참조" 라는 개념 자체가 없다(276/277 이 목록-기반 조회에서 겪은 함정).
 *
 * 🔴 라이브 데모 시드는 결재함(inbox)을 채우려고 승인자 자리에 **운영자 로그인
 * 계정의 `sub`** 를 넣기도 한다(`infra/demo/seed/seed-erp.sh` §6 실측 주석:
 * "approverId 는 참조 검증을 받지 않는다 ... 그래서 승인자에 계정 UUID(콘솔
 * 로그인의 sub)를 넣을 수 있고, 그것이 결재함을 채우는 유일한 방법이다") — 계약이
 * 정한 "employee id" 를 어기는 **데모 전용 편법**이다. 그 행은 직원 마스터에 없으므로
 * 여기서도 정직하게 `이름 확인 불가` 가 된다 — **id 로 되돌아가지 않는다**
 * (`masterRefLabel` 의 계약, `TASK-PC-FE-276` Edge Case). 이것은 이 컴포넌트의
 * 결함이 아니라 데모 시드의 알려진 한계를 honest 하게 보여주는 것이다.
 */
export function ApprovalSubjectRef({
  subjectType,
  subjectId,
}: {
  subjectType: string;
  subjectId: string;
}) {
  const deptQ = useDepartment(subjectType === 'DEPARTMENT' ? subjectId : null);
  const empQ = useEmployee(subjectType === 'EMPLOYEE' ? subjectId : null);

  let resolved: MasterRefTarget | undefined;
  if (subjectType === 'DEPARTMENT' && deptQ.data) {
    resolved = { code: deptQ.data.code, name: deptQ.data.name };
  } else if (subjectType === 'EMPLOYEE' && empQ.data) {
    resolved = { code: empQ.data.employeeNumber, name: empQ.data.name };
  }

  const label = SUBJECT_LABEL[subjectType] ?? subjectType;
  return (
    <span data-master-ref="approval.subjectId" title={subjectId}>
      {label} · {masterRefLabel(subjectId, resolved)}
    </span>
  );
}

/** 직원 조회가 비었는데 그 id 가 **현재 로그인한 운영자 자신의 sub** 와 같을 때의
 *  표시(`TASK-PC-FE-311`). 기존 화면 어휘(`masterRefLabel`)에 맞춘 문구. */
export const APPROVAL_SELF_LABEL = '나 (현재 운영자)';

/**
 * 직원(employee) 참조 한 칸 — 기안자(`submitterId`) / 결재선·결재자(`approverId`,
 * 단계별 또는 legacy) / 이력의 처리자(`actor`) / 대결 대상(`actingForApproverId`)
 * 이 전부 이 모양이다. `field` 는 호출부를 가르는 `data-master-ref` 접미사일 뿐,
 * 조회 방법은 넷 다 같다(`useEmployee`) — 새 포맷을 만들지 않는다.
 *
 * ## `TASK-PC-FE-311` — 직원 조회가 비었을 때, «그게 바로 나 아닌가」를 한 번 더 본다
 *
 * 결재함(inbox)의 결재자 자리에 라이브 데모 시드가 넣는 값은 **직원 마스터 id 가
 * 아니라 운영자 로그인 계정의 sub**다(`TASK-PC-FE-309` 배경 — 자기결재 금지 게이트를
 * 피하려고 시드가 쓰는 편법, `infra/demo/seed/seed-erp.sh` §6). 직원 마스터에 그
 * id 가 없으므로 `masterRefLabel` 은 정직하게 `이름 확인 불가` 를 그린다 — 그 자체는
 * 옳다. 하지만 **그 결재함을 보는 사람이 바로 그 결재자**다(결재함은 정의상 "내가
 * 결재자인 건"만 모은다) — 콘솔 서버는 운영자 세션 토큰으로 "내 sub" 를 이미 알고
 * 있다. `mySub` 는 그 값을 (서버에서 디코드된 문자열만, 토큰 자체가 아니라) 받은
 * props 다 — `getErpApprovalState`(`erp-state.ts`) → `ErpApprovalScreen` →
 * `ApprovalScreen` → `ApprovalDetail` → 여기, 한 방향으로만 흐른다.
 *
 * **순서가 핵심이다** — 직원 조회가 **성공하면 그쪽이 항상 이긴다**(보정은 조회가
 * 실패했을 때의 폴백일 뿐, 실제 직원으로 등록된 결재자를 "나" 로 덮어쓰지 않는다).
 * 로딩 중에는(`empQ.isLoading`) 아직 "못 찾았다" 가 확정되지 않았으므로 보정도
 * 먼저 그리지 않는다(결과 확정 뒤 판정). `mySub` 가 없거나(샘플 방문자 등) id 가
 * 그것과 다르면 기존 그대로 `이름 확인 불가` — 바뀌는 자리는 정확히 "직원 조회
 * 없음 + id === mySub" 뿐이다.
 */
export function ApprovalEmployeeRef({
  employeeId,
  field,
  mySub,
}: {
  employeeId: string;
  field: string;
  /** 현재 로그인한 운영자 자신의 sub(없으면 보정을 적용하지 않는다). */
  mySub?: string | null;
}) {
  const empQ = useEmployee(employeeId);
  const resolved: MasterRefTarget | undefined = empQ.data
    ? { code: empQ.data.employeeNumber, name: empQ.data.name }
    : undefined;
  const isMe =
    !resolved &&
    !empQ.isLoading &&
    Boolean(mySub) &&
    employeeId === mySub;
  const label = isMe
    ? APPROVAL_SELF_LABEL
    : masterRefLabel(employeeId, resolved);
  return (
    <span data-master-ref={`approval.${field}`} title={employeeId}>
      {label}
    </span>
  );
}
