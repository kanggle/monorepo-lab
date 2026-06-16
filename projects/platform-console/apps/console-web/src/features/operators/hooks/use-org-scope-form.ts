'use client';

import { useEffect, useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
// Pure (zod-only, client-safe) erp department type + retired predicate —
// imported from the erp-ops types module directly (NOT the barrel, which is
// server-coupled; same client-safety reason as the hooks' ERP_KEY import).
import { isRetired, type Department } from '@/features/erp-ops/api/types';
import {
  useOperatorAssignments,
  useOrgScopeDepartments,
  useSetOperatorOrgScope,
} from './use-operators';
import type { OperatorAssignment } from '../api/types';

/**
 * Form-state logic for the per-operator org_scope dialog (TASK-PC-FE-112 split
 * of `OrgScopeDialog.tsx` — the PC-FE-106 "fat container → custom hook"
 * pattern). Owns the tri-state selection, the reactive seed from the current
 * assignment, the degrade-aware effective-id derivation, and the submit
 * mutation. The component is left a presentational shell that renders from this
 * hook's result. 0 behavior change — `OrgScopeDialog.test.tsx` (component
 * behavior) passes unchanged.
 *
 * TRI-STATE (null ≠ [] unambiguous in BOTH the UI and the wire):
 *   - 전체 (net-zero)        → `orgScope: null`  (clear).
 *   - 선택 부서 (subtree)     → `orgScope: [<dept-id>...]` (subtree-root set).
 *   - 차단 (zero-scope, adv.) → `orgScope: []`     (explicit; extra confirm).
 */

export type ScopeMode = 'all' | 'subset' | 'block';

export interface UseOrgScopeFormArgs {
  open: boolean;
  operatorId: string;
  onClose: () => void;
}

/** Parses the manual-entry textarea (degrade fallback) into a clean id list:
 *  split on newline / comma, trim, drop blanks, dedupe (order-preserving). */
function parseManualIds(raw: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const tok of raw.split(/[\n,]/)) {
    const t = tok.trim();
    if (t && !seen.has(t)) {
      seen.add(t);
      out.push(t);
    }
  }
  return out;
}

export function useOrgScopeForm({
  open,
  operatorId,
  onClose,
}: UseOrgScopeFormArgs) {
  const assignments = useOperatorAssignments(open ? operatorId : null);
  const setOrgScope = useSetOperatorOrgScope();

  // erp departments for the picker (active-tenant scoped erp read; reuses the
  // existing `/api/erp/masterdata/departments` proxy). Only fetched while the
  // dialog is open. A fetch failure (503 / not erp-entitled) is surfaced via
  // `deptsFailed` → the manual id-entry fallback (degrade; the dialog never
  // fails wholesale).
  const deptsQuery = useOrgScopeDepartments(open);
  const departments: Department[] = useMemo(
    () => deptsQuery.data?.data ?? [],
    [deptsQuery.data],
  );
  const deptsLoading = deptsQuery.isLoading;
  const deptsFailed = deptsQuery.isError;

  // Active (non-retired) departments only as picker options; a retired
  // dept-id already in org_scope is still rendered as a chip (preserved).
  const activeDepartments = useMemo(
    () => departments.filter((d) => !isRetired(d.effectivePeriod)),
    [departments],
  );

  // Resolve the single active-tenant assignment row (0 or 1).
  const assignment: OperatorAssignment | null = useMemo(() => {
    const rows = assignments.data?.assignments ?? [];
    return rows[0] ?? null;
  }, [assignments.data]);
  const hasAssignment = assignment !== null;
  const currentScope = assignment?.orgScope ?? null; // null=전체, []=차단, [ids]

  const [mode, setMode] = useState<ScopeMode>('all');
  const [selected, setSelected] = useState<string[]>([]);
  const [manual, setManual] = useState('');
  const [reason, setReason] = useState('');
  const [blockConfirmed, setBlockConfirmed] = useState(false);

  // Initialise the tri-state from the current assignment whenever the dialog
  // opens OR the assignment data settles (reactive — a late GET fills it in).
  useEffect(() => {
    if (!open) return;
    if (currentScope === null) {
      setMode('all');
      setSelected([]);
      setManual('');
    } else if (currentScope.length === 0) {
      setMode('block');
      setSelected([]);
      setManual('');
    } else {
      setMode('subset');
      setSelected(currentScope);
      setManual(currentScope.join('\n'));
    }
    setReason('');
    setBlockConfirmed(false);
  }, [open, currentScope]);

  const trimmedReason = reason.trim();
  const reasonOk = trimmedReason.length > 0;

  // The effective subtree ids: the manual textarea when departments
  // degraded, else the multi-select.
  const subsetIds = useMemo(
    () => (deptsFailed ? parseManualIds(manual) : selected),
    [deptsFailed, manual, selected],
  );

  // Compute the payload for the chosen mode (null / [] / [ids]).
  const payload: string[] | null = useMemo(() => {
    if (mode === 'all') return null;
    if (mode === 'block') return [];
    return subsetIds;
  }, [mode, subsetIds]);

  const subsetEmpty = mode === 'subset' && subsetIds.length === 0;
  const blockNotConfirmed = mode === 'block' && !blockConfirmed;

  const canSubmit =
    open &&
    hasAssignment &&
    reasonOk &&
    !subsetEmpty &&
    !blockNotConfirmed &&
    !setOrgScope.isPending;

  const submitError =
    setOrgScope.error instanceof ApiError
      ? messageForCode(
          (setOrgScope.error as ApiError).code,
          setOrgScope.error.message,
        )
      : setOrgScope.error
        ? '조직 스코프를 저장하지 못했습니다. 잠시 후 다시 시도하세요.'
        : null;

  // Current-scope summary (null / [] map to a label; [ids] renders chips in
  // the component, so the summary is null in that case).
  const currentSummary =
    currentScope === null
      ? '전체 (net-zero — 데이터-스코프 미적용)'
      : currentScope.length === 0
        ? '차단 (zero-scope — 어떤 부서도 아님)'
        : null;

  function toggleDept(id: string) {
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  }

  function submit() {
    if (!canSubmit || !assignment) return;
    setOrgScope.mutate(
      {
        operatorId,
        tenantId: assignment.tenantId,
        orgScope: payload,
        reason: trimmedReason,
      },
      { onSuccess: () => onClose() },
    );
  }

  return {
    assignmentsLoading: assignments.isLoading,
    hasAssignment,
    currentScope,
    currentSummary,
    departments,
    activeDepartments,
    deptsLoading,
    deptsFailed,
    mode,
    setMode,
    selected,
    toggleDept,
    manual,
    setManual,
    reason,
    setReason,
    reasonOk,
    blockConfirmed,
    setBlockConfirmed,
    subsetIds,
    subsetEmpty,
    canSubmit,
    isPending: setOrgScope.isPending,
    submitError,
    submit,
  };
}
