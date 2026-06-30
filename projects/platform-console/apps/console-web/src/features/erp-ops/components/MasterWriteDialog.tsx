'use client';

import { useState, type ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  useMasterWriteForm,
  masterWriteErrorMessage,
  PAYMENT_METHODS,
} from './use-master-write';

/**
 * Generic master write dialog (TASK-PC-FE-048) — drives create / update /
 * retire for the four non-department masters (employees / job-grades /
 * cost-centers / business-partners) from a field config, so the four masters
 * share ONE dialog instead of four near-identical clones. Mirrors the
 * department dialog's discipline: confirm gate, retire-requires-reason,
 * console-generated `Idempotency-Key`, inline producer-error mapping. FK
 * fields (departmentId / jobGradeId / costCenterId) render as dropdowns sourced
 * from the section's loaded lists (the TASK-PC-FE-047 "no raw UUID" rule);
 * create/update never send `X-Operator-Reason` (no producer slot — only retire
 * carries `reason`, in the body).
 *
 * The owning list supplies a `controller` (built from its own create/update/
 * retire hooks) so this component stays decoupled from the per-master hooks.
 *
 * TASK-PC-FE-152 — behaviour-preserving HOOK-ONLY split (complements the
 * earlier `master-write-configs.ts` config extraction): all values /
 * validation / body-building / mutations / `onConfirm` / option-resolution
 * / error mapping live in `use-master-write.ts`; this file keeps the public
 * type surface, the presentational dialog, and the `useMasterWrite`
 * request-state helper. Render output / DOM / data-testid / ARIA / wire
 * bodies are unchanged.
 */

export type MasterFieldKind =
  | 'text'
  | 'number'
  | 'date'
  | 'select'
  | 'payment-terms';

export interface MasterFieldDef {
  key: string;
  label: string;
  kind: MasterFieldKind;
  required?: boolean;
  /** Static options for `kind: 'select'`. */
  options?: { value: string; label: string }[];
  /** Dynamic options for `kind: 'select'` — a key into `optionSources`. */
  optionSource?: 'departments' | 'jobGrades' | 'costCenters';
}

export interface MasterWriteConfig {
  /** Korean master label, e.g. '직원'. */
  label: string;
  createFields: MasterFieldDef[];
  updateFields: MasterFieldDef[];
}

export interface MasterOption {
  id: string;
  code?: string;
  name: string;
}

export interface MasterWriteController {
  pending: boolean;
  error: unknown;
  create: (values: Record<string, unknown>, idempotencyKey: string) => Promise<unknown>;
  update: (id: string, values: Record<string, unknown>, idempotencyKey: string) => Promise<unknown>;
  retire: (id: string, reason: string, idempotencyKey: string) => Promise<unknown>;
}

export interface MasterWriteRequest {
  mode: 'create' | 'update' | 'retire';
  /** Target row for update / retire (absent for create). */
  target?: { id: string; label: string };
}

export interface MasterWriteDialogProps {
  config: MasterWriteConfig;
  request: MasterWriteRequest;
  controller: MasterWriteController;
  onClose: () => void;
  optionSources?: {
    departments?: MasterOption[];
    jobGrades?: MasterOption[];
    costCenters?: MasterOption[];
  };
  /** testid prefix, e.g. `erp-employee`. */
  testid: string;
}

export function MasterWriteDialog({
  config,
  request,
  controller,
  onClose,
  optionSources = {},
  testid,
}: MasterWriteDialogProps) {
  const {
    mode,
    target,
    fields,
    values,
    reason,
    setReason,
    destructive,
    title,
    setField,
    dynamicOptions,
    requiredOk,
    canConfirm,
    onConfirm,
  } = useMasterWriteForm(config, request, controller, onClose, optionSources);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid={`${testid}-write-overlay`}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        data-testid={`${testid}-write-dialog`}
        data-mode={mode}
        className="w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <h2
          className={
            destructive
              ? 'text-lg font-semibold text-destructive'
              : 'text-lg font-semibold text-foreground'
          }
        >
          {title}
        </h2>
        {target && (
          <p className="mt-1 text-sm text-muted-foreground">
            대상: <span className="font-medium">{target.label}</span>
          </p>
        )}

        {/* create / update fields */}
        {!destructive &&
          fields.map((f) => {
            const id = `${testid}-field-${f.key}`;
            return (
              <div className="mt-4" key={f.key}>
                <label
                  htmlFor={id}
                  className="block text-sm font-medium text-foreground"
                >
                  {f.label}
                  {f.required ? <span aria-hidden="true"> *</span> : null}
                </label>
                {f.kind === 'payment-terms' ? (
                  <div className="mt-1 flex gap-2">
                    <input
                      data-testid={`${id}-termDays`}
                      type="number"
                      placeholder="결제일수 (예: 30)"
                      value={values[`${f.key}.termDays`] ?? ''}
                      onChange={(e) => setField(`${f.key}.termDays`, e.target.value)}
                      className="w-1/2 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                    />
                    <select
                      data-testid={`${id}-method`}
                      value={values[`${f.key}.method`] ?? ''}
                      onChange={(e) => setField(`${f.key}.method`, e.target.value)}
                      className="w-1/2 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                    >
                      <option value="">— 결제수단 —</option>
                      {PAYMENT_METHODS.map((m) => (
                        <option key={m} value={m}>
                          {m}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : f.kind === 'select' ? (
                  <select
                    id={id}
                    data-testid={id}
                    value={values[f.key] ?? ''}
                    onChange={(e) => setField(f.key, e.target.value)}
                    className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                  >
                    <option value="">{f.required ? '— 선택 —' : '— 없음 —'}</option>
                    {(f.options ??
                      dynamicOptions(f.optionSource).map((o) => ({
                        value: o.id,
                        label: o.code ? `${o.code} · ${o.name}` : o.name,
                      }))).map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    id={id}
                    data-testid={id}
                    type={f.kind === 'date' ? 'date' : f.kind === 'number' ? 'number' : 'text'}
                    value={values[f.key] ?? ''}
                    onChange={(e) => setField(f.key, e.target.value)}
                    className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                  />
                )}
              </div>
            );
          })}

        {/* retire reason */}
        {destructive && (
          <div className="mt-4">
            <label
              htmlFor={`${testid}-reason`}
              className="block text-sm font-medium text-foreground"
            >
              사유 <span aria-hidden="true">*</span>
            </label>
            <textarea
              id={`${testid}-reason`}
              data-testid={`${testid}-reason`}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              maxLength={256}
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
              placeholder="폐기 사유를 입력하세요 (감사 기록에 남습니다)"
            />
            {!requiredOk && (
              <p
                className="mt-1 text-xs text-destructive"
                data-testid={`${testid}-reason-error`}
                role="status"
              >
                폐기에는 사유가 필요합니다.
              </p>
            )}
          </div>
        )}

        {controller.error ? (
          <p
            className="mt-4 text-sm text-destructive"
            data-testid={`${testid}-write-error`}
            role="status"
          >
            {masterWriteErrorMessage(controller.error)}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={controller.pending}
            data-testid={`${testid}-write-cancel`}
          >
            취소
          </Button>
          <Button
            variant="primary"
            onClick={onConfirm}
            disabled={!canConfirm}
            data-testid={`${testid}-write-submit`}
            className={
              destructive
                ? 'bg-destructive text-destructive-foreground hover:opacity-90'
                : undefined
            }
          >
            {controller.pending ? '처리 중…' : '확인'}
          </Button>
        </div>
      </div>
    </div>
  );
}

/**
 * Encapsulates the write-dialog request state + rendering for a master list,
 * so each List only wires its create/update/retire hooks into a `controller`
 * and drops in the returned `dialog` + `openCreate`/`openUpdate`/`openRetire`
 * handlers. Keeps the four lists' write affordance DRY.
 */
export function useMasterWrite(
  controller: MasterWriteController,
  config: MasterWriteConfig,
  testid: string,
  optionSources?: MasterWriteDialogProps['optionSources'],
): {
  openCreate: () => void;
  openUpdate: (id: string, label: string) => void;
  openRetire: (id: string, label: string) => void;
  dialog: ReactNode;
} {
  const [req, setReq] = useState<MasterWriteRequest | null>(null);
  return {
    openCreate: () => setReq({ mode: 'create' }),
    openUpdate: (id, label) => setReq({ mode: 'update', target: { id, label } }),
    openRetire: (id, label) => setReq({ mode: 'retire', target: { id, label } }),
    dialog: req ? (
      <MasterWriteDialog
        config={config}
        request={req}
        controller={controller}
        onClose={() => setReq(null)}
        optionSources={optionSources}
        testid={testid}
      />
    ) : null,
  };
}
