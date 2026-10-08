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
 * 🔵 (TASK-PC-FE-318) 한때 데모 시드는 결재함을 채우려고 승인자 자리에 **계정
 * `sub`** 를 넣었다 — approval v2.4(`TASK-MONO-776`)가 사람 칸을 직원 id 한
 * 공간으로 묶고 시드를 «직원 ↔ 계정 연결» 로 바꾸면서 그 편법은 사라졌다. 그 이전에
 * 만들어진 행(계약 § v2.4 «이전 데이터»)은 직원 마스터에 없는 id 라 여기서
 * 정직하게 `이름 확인 불가` 가 된다 — **id 로 되돌아가지 않는다**
 * (`masterRefLabel` 의 계약, `TASK-PC-FE-276` Edge Case).
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

/** 이 칸의 직원이 바로 나(결재함 `meta.actorEmployeeId`)일 때 이름 뒤에 붙는 표시. */
export const APPROVAL_SELF_SUFFIX = '(나)';
/** 결재자 칸의 직원에게 연결된 IAM 계정이 없을 때 이름 뒤에 붙는 표시. */
export const APPROVAL_APPROVER_UNLINKED_LABEL = '연결된 계정 없음';

/**
 * 직원(employee) 참조 한 칸 — 기안자(`submitterId`) / 결재선·결재자(`approverId`,
 * 단계별 또는 legacy) / 이력의 처리자(`actor`) / 대결 대상(`actingForApproverId`)
 * 이 전부 이 모양이다. `field` 는 호출부를 가르는 `data-master-ref` 접미사일 뿐,
 * 조회 방법은 넷 다 같다(`useEmployee`) — 새 포맷을 만들지 않는다.
 *
 * ## `TASK-PC-FE-318` — `TASK-PC-FE-311` 보정을 걷었다
 *
 * 311 은 데모 시드가 승인자 칸에 **계정 `sub`** 를 넣던 시절, «직원 조회 실패 + id
 * === 내 sub» 일 때 `나 (현재 운영자)` 로 그렸다. approval v2.4(`TASK-MONO-776`)
 * 이후 사람 칸은 전부 **직원 id** 다 — 직원 조회(`useEmployee`)가 이름을 내고,
 * 직원 id(UUIDv7)는 어떤 계정 `sub` 와도 같지 않아 그 보정은 영원히 발화하지 않는
 * 분기가 됐다(AC-0 ③). 토큰 디코드로 얻던 `mySub` 대신, 결재함 응답이 알려 주는
 * **내 직원 id**(`meta.actorEmployeeId`)를 `myEmployeeId` 로 받아 «(나)» 를 붙인다.
 * 이름 해소는 그대로 직원 조회가 권위다 — «(나)» 는 이름을 **덮지 않고 덧붙는다**.
 *
 * `markUnlinked` (결재자 칸만): 조회된 직원에게 `accountId` 가 없으면 «연결된 계정
 * 없음» 을 덧붙인다 — 그 결재자는 결재함에서 이 건을 볼 수 없다(상신은 거절되고,
 * 진행 중 연결이 해제됐다면 아무 결재함에도 안 보인다 — 티켓 Edge Case). 조회가
 * 실패했으면(`이름 확인 불가`) 연결 여부를 알 수 없으므로 아무것도 덧붙이지 않는다.
 */
export function ApprovalEmployeeRef({
  employeeId,
  field,
  myEmployeeId,
  markUnlinked = false,
}: {
  employeeId: string;
  field: string;
  /** 내 직원 id(결재함 `meta.actorEmployeeId`). 없으면 «(나)» 를 붙이지 않는다. */
  myEmployeeId?: string | null;
  /** 결재자 칸: 연결된 계정이 없는 직원을 표시한다. */
  markUnlinked?: boolean;
}) {
  const empQ = useEmployee(employeeId);
  const resolved: MasterRefTarget | undefined = empQ.data
    ? { code: empQ.data.employeeNumber, name: empQ.data.name }
    : undefined;
  const isMe = Boolean(myEmployeeId) && employeeId === myEmployeeId;
  const unlinked = markUnlinked && Boolean(empQ.data) && !empQ.data?.accountId;
  return (
    <span
      data-master-ref={`approval.${field}`}
      title={employeeId}
      data-self={isMe ? 'true' : undefined}
      data-unlinked={unlinked ? 'true' : undefined}
    >
      {masterRefLabel(employeeId, resolved)}
      {isMe ? ` ${APPROVAL_SELF_SUFFIX}` : null}
      {unlinked ? (
        <span className="ml-1 text-xs text-destructive">
          · {APPROVAL_APPROVER_UNLINKED_LABEL}
        </span>
      ) : null}
    </span>
  );
}
