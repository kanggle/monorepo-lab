'use client';

import { useEffect, useId, useRef } from 'react';
import { Button } from '@/shared/ui/Button';
import { useOrgScopeForm } from '../hooks/use-org-scope-form';

/**
 * Per-operator org_scope (데이터-스코프) dialog (TASK-PC-FE-050 — sibling of
 * `OperatorConfirmDialog` / `OperatorProfileEditDialog`). Reads the
 * operator's assignment row for the ACTIVE tenant
 * (`GET .../{operatorId}/assignments`) and sets / clears its `org_scope`
 * (`PUT .../assignments/{tenantId}/org-scope`) — the source half of the
 * org_scope end-to-end loop (설정 → IAM 저장(BE-339) → assume-tenant 전파 →
 * erp 소비(ERP-BE-008) → read 카드(PC-FE-049)).
 *
 * TASK-PC-FE-112 split: this component is now a presentational shell — the
 * tri-state form logic (seed / degrade-aware id derivation / submit mutation)
 * lives in `useOrgScopeForm` (the PC-FE-106 fat-container → custom-hook
 * pattern). The dialog owns only the view concerns (ids, focus container,
 * Escape-to-close) + the JSX. 0 behavior change.
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

export interface OrgScopeDialogProps {
  open: boolean;
  /** Target operator id (path var for both GET + PUT). */
  operatorId: string;
  /** Human-friendly label (email or operatorId) for the heading. */
  operatorLabel: string;
  onClose: () => void;
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

  const f = useOrgScopeForm({ open, operatorId, onClose });

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

  if (!open) return null;

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
        {f.assignmentsLoading ? (
          <p
            className="mt-4 text-sm text-muted-foreground"
            data-testid="org-scope-loading"
          >
            배정 정보를 불러오는 중…
          </p>
        ) : !f.hasAssignment ? (
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
              {f.currentSummary ? (
                <span
                  className="text-foreground"
                  data-testid="org-scope-current"
                >
                  {f.currentSummary}
                </span>
              ) : (
                <span
                  className="flex flex-wrap gap-1"
                  data-testid="org-scope-current"
                >
                  {(f.currentScope ?? []).map((id) => {
                    const dept = f.departments.find((d) => d.id === id);
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
                    checked={f.mode === 'all'}
                    onChange={() => f.setMode('all')}
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
                    checked={f.mode === 'subset'}
                    onChange={() => f.setMode('subset')}
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
                    checked={f.mode === 'block'}
                    onChange={() => f.setMode('block')}
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
            {f.mode === 'subset' && (
              <div className="mt-3" data-testid="org-scope-subset-panel">
                {f.deptsFailed ? (
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
                      value={f.manual}
                      onChange={(e) => f.setManual(e.target.value)}
                      rows={3}
                      data-testid="org-scope-manual-input"
                      placeholder={'dept-sales\ndept-eng'}
                      className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    />
                    <p className="mt-1 text-xs text-muted-foreground">
                      입력된 ID: {f.subsetIds.length}개
                    </p>
                  </div>
                ) : f.deptsLoading ? (
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
                    {f.activeDepartments.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        선택할 수 있는 활성 부서가 없습니다.
                      </p>
                    ) : (
                      f.activeDepartments.map((d) => (
                        <label
                          key={d.id}
                          className="flex items-center gap-2 py-1 text-sm text-foreground"
                        >
                          <input
                            type="checkbox"
                            checked={f.selected.includes(d.id)}
                            onChange={() => f.toggleDept(d.id)}
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
                {f.subsetEmpty && (
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
            {f.mode === 'block' && (
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
                    checked={f.blockConfirmed}
                    onChange={(e) => f.setBlockConfirmed(e.target.checked)}
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
                value={f.reason}
                onChange={(e) => f.setReason(e.target.value)}
                required
                aria-required="true"
                rows={2}
                data-testid="org-scope-reason"
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                placeholder="이 변경의 사유를 입력하세요 (감사 기록에 남습니다)"
              />
              {!f.reasonOk && (
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

        {f.submitError && (
          <p
            role="alert"
            data-testid="org-scope-error"
            className="mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {f.submitError}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={f.isPending}
            data-testid="org-scope-cancel"
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={f.submit}
            disabled={!f.canSubmit}
            data-testid="org-scope-save"
          >
            {f.isPending ? '처리 중…' : '저장'}
          </Button>
        </div>
      </div>
    </div>
  );
}
