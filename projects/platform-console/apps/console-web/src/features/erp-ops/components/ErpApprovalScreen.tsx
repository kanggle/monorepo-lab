import type { ApprovalListResponse } from '../api/approval-types';
import { ApprovalScreen } from './ApprovalScreen';

/**
 * erp **결재함** route screen (`/erp/approval` — TASK-PC-FE-076 drill-in
 * split; the approval-workflow slice of the former `ErpOpsScreen`,
 * TASK-PC-FE-051). Wraps the `<ApprovalScreen>` (requests + the caller's
 * inbox) with the route heading. approval has no `?asOf=` concept
 * (single-stage workflow, not an effective-dated master) — no
 * `<AsOfPicker>` here.
 */
export interface ErpApprovalScreenProps {
  initialApprovalRequests?: ApprovalListResponse | null;
  initialApprovalInbox?: ApprovalListResponse | null;
  /** Deep-link target from `/erp/approval?request=<id>` (notification bell
   *  approval fallback, PC-FE-230) — the request to open on mount. */
  initialSelectedId?: string | null;
  /** The signed-in operator's own `sub`, server-resolved
   *  (`getErpApprovalState`, TASK-PC-FE-311) — threaded down to
   *  `ApprovalEmployeeRef` so a reference cell that is unresolvable in
   *  the employee master but equals the viewer's own identity renders a
   *  human label instead of `이름 확인 불가`. */
  mySub?: string | null;
}

export function ErpApprovalScreen({
  initialApprovalRequests,
  initialApprovalInbox,
  initialSelectedId,
  mySub,
}: ErpApprovalScreenProps) {
  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-2 text-2xl font-semibold">
        ERP 결재함
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        결재 요청 목록과 내가 처리할 결재(inbox)를 조회하고 상신/승인/반려/회수
        할 수 있습니다. 권한이 없는 작업은 실행 시 안내됩니다.
      </p>

      <ApprovalScreen
        initialRequests={initialApprovalRequests ?? undefined}
        initialInbox={initialApprovalInbox ?? undefined}
        initialSelectedId={initialSelectedId ?? null}
        mySub={mySub ?? null}
      />
    </section>
  );
}
