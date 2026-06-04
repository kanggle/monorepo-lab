'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
// Pure (zod-only, client-safe) erp department type + retired predicate —
// imported from the erp-ops types module directly (NOT the barrel, which is
// server-coupled; same client-safety reason as the hooks' ERP_KEY import).
import { isRetired, type Department } from '@/features/erp-ops/api/types';
import {
  useOperatorAssignments,
  useOrgScopeDepartments,
  useSetOperatorOrgScope,
} from '../hooks/use-operators';
import type { OperatorAssignment } from '../api/types';

/**
 * Per-operator org_scope (데이터-스코프) dialog (TASK-PC-FE-050 — sibling of
 * `OperatorConfirmDialog` / `OperatorProfileEditDialog`). Reads the
 * operator's assignment row for the ACTIVE tenant
 * (`GET .../{operatorId}/assignments`) and sets / clears its `org_scope`
 * (`PUT .../assignments/{tenantId}/org-scope`) — the source half of the
 * org_scope end-to-end loop (설정 → GAP 저장(BE-339) → assume-tenant 전파 →
 * erp 소비(ERP-BE-008) → read 카드(PC-FE-049)).
 *
 * TRI-STATE (null ≠ [] is unambiguous in BOTH the UI and the wire):
 *   - 전체 (net-zero)        → `orgScope: null`  (clear; default for a
 *                             currently-null assignment; "전 부서 — 데이터-
 *                             스코프 미적용").
 *   - 선택 부서 (subtree)     → `orgScope: [<dept-id>...]` (department
 *                             multi-select; subtree-root; active depts only,
 *                             label `code · name`).
 *   - 차단 (zero-scope, adv.) → `orgScope: []`     (explicit; an extra warning
 *                             + confirm — the operator will see/write NOTHING
 *                             in this tenant).
 *
 * DEGRADE (green-wash 금지):
 *   - erp departments fetch fails (503 / tenant not erp-entitled) → the
 *     dialog does NOT fail; it falls back to a manual id entry (textarea,
 *     one id per line / comma-separated) + a warning banner.
 *   - the operator has NO assignment row in the active tenant (GET returns an
 *     empty array → the BE returns 404 on PUT) → guidance ("이 테넌트에 명시
 *     배정 없음 → org_scope 부적용(전체)") + Save disabled.
 *
 * reason-gated (same posture as the other operator mutations): Save is
 * disabled until a non-empty reason is entered; the proxy forwards it as
 * `X-Operator-Reason` (the producer rejects an empty reason with
 * `400 REASON_REQUIRED`).
 */

type ScopeMode = 'all' | 'subset' | 'block';

export interface OrgScopeDialogProps {
  open: boolean;
  /** Target operator id (path var for both GET + PUT). */
  operatorId: string;
  /** Human-friendly label (email or operatorId) for the heading. */
  operatorLabel: string;
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

export function OrgScopeDialog({
  open,
  operatorId,
  operatorLabel,
  onClose,
}: OrgScopeDialogProps) {
  const titleId = useId();
  const descId = useId();
  const reasonId = useId();
  const manualId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

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

  if (!open) return null;

  // Render current-scope summary chips.
  const currentSummary =
    currentScope === null
      ? '전체 (net-zero — 데이터-스코프 미적용)'
      : currentScope.length === 0
        ? '차단 (zero-scope — 어떤 부서도 아님)'
        : null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="org-scope-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        data-testid="org-scope-dialog"
        className="w-full max-w-lg overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-lg"
        style={{ maxHeight: '90vh' }}
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          조직 스코프 (org_scope) — {operatorLabel}
        </h2>
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          이 운영자가 활성 테넌트에서 접근할 수 있는 데이터 범위(부서
          subtree-root)를 설정합니다. 변경은 다음 테넌트 재선택 시 발급되는
          토큰부터 적용됩니다 (대상 운영자가 기존 세션을 보유한 경우 재로그인
          또는 테넌트 재선택이 필요합니다). 이 작업은 감사 사유와 함께
          기록됩니다.
        </p>

        {/* Loading / no-assignment / current-scope summary */}
        {assignments.isLoading ? (
          <p
            className="mt-4 text-sm text-muted-foreground"
            data-testid="org-scope-loading"
          >
            배정 정보를 불러오는 중…
          </p>
        ) : !hasAssignment ? (
          <div
            role="status"
            data-testid="org-scope-no-assignment"
            className="mt-4 rounded-md border border-border bg-muted px-3 py-3 text-sm text-muted-foreground"
          >
            이 테넌트에 명시 배정이 없습니다 → org_scope 부적용 (전체). 운영자를
            이 테넌트에 먼저 배정해야 데이터-스코프를 설정할 수 있습니다.
          </div>
        ) : (
          <>
            <div className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm">
              <span className="font-medium text-foreground">현재 스코프: </span>
              {currentSummary ? (
                <span
                  className="text-foreground"
                  data-testid="org-scope-current"
                >
                  {currentSummary}
                </span>
              ) : (
                <span
                  className="flex flex-wrap gap-1"
                  data-testid="org-scope-current"
                >
                  {(currentScope ?? []).map((id) => {
                    const dept = departments.find((d) => d.id === id);
                    return (
                      <span
                        key={id}
                        className="rounded bg-background px-2 py-0.5 text-xs text-foreground"
                        data-testid={`org-scope-current-chip-${id}`}
                      >
                        {dept ? `${dept.code} · ${dept.name}` : id}
                      </span>
                    );
                  })}
                </span>
              )}
            </div>

            {/* Tri-state selector */}
            <fieldset className="mt-4">
              <legend className="text-sm font-medium text-foreground">
                데이터 스코프
              </legend>
              <div className="mt-2 space-y-2">
                <label className="flex items-start gap-2 text-sm text-foreground">
                  <input
                    type="radio"
                    name="org-scope-mode"
                    checked={mode === 'all'}
                    onChange={() => setMode('all')}
                    data-testid="org-scope-mode-all"
                    className="mt-1"
                  />
                  <span>
                    <strong>전체 (net-zero)</strong> — 전 부서. 데이터-스코프를
                    적용하지 않습니다 (저장 시 <code>null</code>).
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm text-foreground">
                  <input
                    type="radio"
                    name="org-scope-mode"
                    checked={mode === 'subset'}
                    onChange={() => setMode('subset')}
                    data-testid="org-scope-mode-subset"
                    className="mt-1"
                  />
                  <span>
                    <strong>선택 부서 (subtree)</strong> — 선택한 부서 및 그
                    하위 트리만 접근합니다.
                  </span>
                </label>
                <label className="flex items-start gap-2 text-sm text-foreground">
                  <input
                    type="radio"
                    name="org-scope-mode"
                    checked={mode === 'block'}
                    onChange={() => setMode('block')}
                    data-testid="org-scope-mode-block"
                    className="mt-1"
                  />
                  <span>
                    <strong>차단 (zero-scope, advanced)</strong> — 어떤 부서도
                    아님. 이 운영자는 이 테넌트에서 아무 데이터도 보거나 쓸 수
                    없습니다 (저장 시 <code>[]</code> — <code>null</code>(전체)과
                    다릅니다).
                  </span>
                </label>
              </div>
            </fieldset>

            {/* Subset picker */}
            {mode === 'subset' && (
              <div className="mt-3" data-testid="org-scope-subset-panel">
                {deptsFailed ? (
                  <div>
                    <div
                      role="status"
                      data-testid="org-scope-depts-degraded"
                      className="mb-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive"
                    >
                      부서 목록을 불러올 수 없습니다 (erp 미가용 또는 테넌트
                      미-entitled). 부서 ID를 직접 입력하세요 (한 줄에 하나 또는
                      쉼표로 구분). GAP은 ID 존재를 검증하지 않으며 erp가 소비
                      시점에 검증합니다.
                    </div>
                    <label
                      htmlFor={manualId}
                      className="block text-sm font-medium text-foreground"
                    >
                      부서 ID 직접 입력
                    </label>
                    <textarea
                      id={manualId}
                      value={manual}
                      onChange={(e) => setManual(e.target.value)}
                      rows={3}
                      data-testid="org-scope-manual-input"
                      placeholder={'dept-sales\ndept-eng'}
                      className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    />
                    <p className="mt-1 text-xs text-muted-foreground">
                      입력된 ID: {subsetIds.length}개
                    </p>
                  </div>
                ) : deptsLoading ? (
                  <p
                    className="text-sm text-muted-foreground"
                    data-testid="org-scope-depts-loading"
                  >
                    부서 목록을 불러오는 중…
                  </p>
                ) : (
                  <div
                    className="max-h-48 overflow-y-auto rounded-md border border-border p-2"
                    data-testid="org-scope-dept-list"
                  >
                    {activeDepartments.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        선택할 수 있는 활성 부서가 없습니다.
                      </p>
                    ) : (
                      activeDepartments.map((d) => (
                        <label
                          key={d.id}
                          className="flex items-center gap-2 py-1 text-sm text-foreground"
                        >
                          <input
                            type="checkbox"
                            checked={selected.includes(d.id)}
                            onChange={() => toggleDept(d.id)}
                            data-testid={`org-scope-dept-${d.id}`}
                          />
                          <span>
                            {d.code} · {d.name}
                          </span>
                        </label>
                      ))
                    )}
                  </div>
                )}
                {subsetEmpty && (
                  <p
                    className="mt-1 text-xs text-muted-foreground"
                    data-testid="org-scope-subset-empty"
                  >
                    부서를 하나 이상 선택해야 합니다. (어떤 부서도 아님을
                    원하면 &quot;차단&quot;을 선택하세요.)
                  </p>
                )}
              </div>
            )}

            {/* Block confirm */}
            {mode === 'block' && (
              <div
                className="mt-3 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2"
                data-testid="org-scope-block-warning"
              >
                <p className="text-sm text-destructive">
                  주의: 차단(zero-scope)을 저장하면 이 운영자는 이 테넌트에서
                  어떤 부서 데이터도 보거나 쓸 수 없습니다. 전체 권한을
                  의도했다면 &quot;전체&quot;를 선택하세요.
                </p>
                <label className="mt-2 flex items-center gap-2 text-sm text-foreground">
                  <input
                    type="checkbox"
                    checked={blockConfirmed}
                    onChange={(e) => setBlockConfirmed(e.target.checked)}
                    data-testid="org-scope-block-confirm"
                  />
                  위 내용을 이해했으며 차단을 적용합니다.
                </label>
              </div>
            )}

            {/* Reason */}
            <div className="mt-4">
              <label
                htmlFor={reasonId}
                className="block text-sm font-medium text-foreground"
              >
                감사 사유 <span aria-hidden="true">*</span>
                <span className="sr-only">(필수)</span>
              </label>
              <textarea
                id={reasonId}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
                aria-required="true"
                rows={2}
                data-testid="org-scope-reason"
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                placeholder="이 변경의 사유를 입력하세요 (감사 기록에 남습니다)"
              />
              {!reasonOk && (
                <p
                  className="mt-1 text-xs text-muted-foreground"
                  data-testid="org-scope-reason-required"
                >
                  사유를 입력해야 작업을 진행할 수 있습니다.
                </p>
              )}
            </div>
          </>
        )}

        {submitError && (
          <p
            role="alert"
            data-testid="org-scope-error"
            className="mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {submitError}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={setOrgScope.isPending}
            data-testid="org-scope-cancel"
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={submit}
            disabled={!canSubmit}
            data-testid="org-scope-save"
          >
            {setOrgScope.isPending ? '처리 중…' : '저장'}
          </Button>
        </div>
      </div>
    </div>
  );
}
