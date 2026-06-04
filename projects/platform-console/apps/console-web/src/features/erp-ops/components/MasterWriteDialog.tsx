'use client';

import { useState, type ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError } from '@/shared/api/errors';

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
 */

export type MasterFieldKind = 'text' | 'number' | 'date' | 'select';

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

function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'MASTERDATA_DUPLICATE_KEY':
        return '같은 코드/번호가 이미 있습니다.';
      case 'MASTERDATA_REFERENCE_VIOLATION':
        return '이 항목을 참조하는 다른 마스터가 있어 폐기할 수 없습니다.';
      case 'MASTERDATA_EFFECTIVE_PERIOD_INVALID':
        return '유효 시작일이 올바르지 않습니다.';
      case 'CONCURRENT_MODIFICATION':
      case 'IDEMPOTENCY_KEY_CONFLICT':
        return '동시 변경이 감지되었습니다. 새로고침 후 다시 시도하세요.';
      case 'MASTERDATA_NOT_FOUND':
        return '대상 또는 참조 항목을 찾을 수 없습니다.';
      case 'PERMISSION_DENIED':
      case 'DATA_SCOPE_FORBIDDEN':
      case 'TENANT_FORBIDDEN':
        return '이 작업을 수행할 권한이 없습니다.';
      default:
        return err.message || '요청을 처리하지 못했습니다.';
    }
  }
  return '요청을 처리하지 못했습니다.';
}

export function MasterWriteDialog({
  config,
  request,
  controller,
  onClose,
  optionSources = {},
  testid,
}: MasterWriteDialogProps) {
  const { mode, target } = request;
  const fields =
    mode === 'create' ? config.createFields : mode === 'update' ? config.updateFields : [];
  const [values, setValues] = useState<Record<string, string>>({});
  const [reason, setReason] = useState('');

  const destructive = mode === 'retire';
  const title =
    mode === 'create'
      ? `${config.label} 생성`
      : mode === 'update'
        ? `${config.label} 수정`
        : `${config.label} 폐기 (파괴적)`;

  function setField(key: string, v: string) {
    setValues((s) => ({ ...s, [key]: v }));
  }

  function dynamicOptions(src?: MasterFieldDef['optionSource']): MasterOption[] {
    if (src === 'departments') return optionSources.departments ?? [];
    if (src === 'jobGrades') return optionSources.jobGrades ?? [];
    if (src === 'costCenters') return optionSources.costCenters ?? [];
    return [];
  }

  // Required-field validation (create) / at-least-one (update) / reason (retire).
  const requiredOk =
    mode === 'create'
      ? fields.filter((f) => f.required).every((f) => (values[f.key] ?? '').trim() !== '')
      : mode === 'update'
        ? fields.some((f) => (values[f.key] ?? '').trim() !== '')
        : reason.trim() !== '';
  const canConfirm = !controller.pending && requiredOk;

  /** Builds the wire body from the field values — omits empty optionals;
   *  coerces number fields. */
  function buildBody(): Record<string, unknown> {
    const body: Record<string, unknown> = {};
    for (const f of fields) {
      const raw = (values[f.key] ?? '').trim();
      if (raw === '') continue; // omit empty (optional / unchanged)
      body[f.key] = f.kind === 'number' ? Number(raw) : raw;
    }
    return body;
  }

  function onConfirm() {
    if (!canConfirm) return;
    const idem = newIdemKey();
    const done = () => onClose();
    if (mode === 'create') {
      controller.create(buildBody(), idem).then(done).catch(() => {});
    } else if (mode === 'update' && target) {
      controller.update(target.id, buildBody(), idem).then(done).catch(() => {});
    } else if (mode === 'retire' && target) {
      controller.retire(target.id, reason.trim(), idem).then(done).catch(() => {});
    }
  }

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
                {f.kind === 'select' ? (
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
            {errorMessage(controller.error)}
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
