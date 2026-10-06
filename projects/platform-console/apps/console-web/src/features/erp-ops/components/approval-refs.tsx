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

/**
 * 직원(employee) 참조 한 칸 — 기안자(`submitterId`) / 결재선·결재자(`approverId`,
 * 단계별 또는 legacy) / 이력의 처리자(`actor`) / 대결 대상(`actingForApproverId`)
 * 이 전부 이 모양이다. `field` 는 호출부를 가르는 `data-master-ref` 접미사일 뿐,
 * 조회 방법은 넷 다 같다(`useEmployee`) — 새 포맷을 만들지 않는다.
 */
export function ApprovalEmployeeRef({
  employeeId,
  field,
}: {
  employeeId: string;
  field: string;
}) {
  const empQ = useEmployee(employeeId);
  const resolved: MasterRefTarget | undefined = empQ.data
    ? { code: empQ.data.employeeNumber, name: empQ.data.name }
    : undefined;
  return (
    <span data-master-ref={`approval.${field}`} title={employeeId}>
      {masterRefLabel(employeeId, resolved)}
    </span>
  );
}
