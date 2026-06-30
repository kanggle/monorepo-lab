'use client';

import { useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import type {
  MasterFieldDef,
  MasterOption,
  MasterWriteController,
  MasterWriteConfig,
  MasterWriteRequest,
  MasterWriteDialogProps,
} from './MasterWriteDialog';

/**
 * `<MasterWriteDialog>` container hook (TASK-PC-FE-152 — behaviour-
 * preserving split of the former 383-line `MasterWriteDialog.tsx`,
 * complementing the earlier `master-write-configs.ts` config extraction).
 * The dialog is a single flat config-driven form (one `fields.map`, no
 * separately-reusable sub-block), so the split is HOOK-ONLY (PC-FE-141
 * PromotionForm pattern): all values / validation / body-building /
 * mutations / `onConfirm` / option-resolution / error mapping live here;
 * the component is presentation only. No value/setter prop-drilling is
 * introduced.
 */

/** Method enum for BusinessPartner paymentTerms (producer enum). */
export const PAYMENT_METHODS = ['BANK_TRANSFER', 'CREDIT_CARD', 'CASH', 'CHECK'];

function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export function masterWriteErrorMessage(err: unknown): string {
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

export function useMasterWriteForm(
  config: MasterWriteConfig,
  request: MasterWriteRequest,
  controller: MasterWriteController,
  onClose: () => void,
  optionSources: NonNullable<MasterWriteDialogProps['optionSources']>,
) {
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

  /** A field is "filled" — for payment-terms BOTH sub-inputs must be set. */
  function fieldFilled(f: MasterFieldDef): boolean {
    if (f.kind === 'payment-terms') {
      return (
        (values[`${f.key}.termDays`] ?? '').trim() !== '' &&
        (values[`${f.key}.method`] ?? '').trim() !== ''
      );
    }
    return (values[f.key] ?? '').trim() !== '';
  }

  // Required-field validation (create) / at-least-one (update) / reason (retire).
  const requiredOk =
    mode === 'create'
      ? fields.filter((f) => f.required).every(fieldFilled)
      : mode === 'update'
        ? fields.some(fieldFilled)
        : reason.trim() !== '';
  const canConfirm = !controller.pending && requiredOk;

  /** Builds the wire body from the field values — omits empty optionals;
   *  coerces number fields; assembles the nested paymentTerms object. */
  function buildBody(): Record<string, unknown> {
    const body: Record<string, unknown> = {};
    for (const f of fields) {
      if (f.kind === 'payment-terms') {
        const termDays = (values[`${f.key}.termDays`] ?? '').trim();
        const method = (values[`${f.key}.method`] ?? '').trim();
        if (termDays !== '' || method !== '') {
          body[f.key] = {
            ...(termDays !== '' ? { termDays: Number(termDays) } : {}),
            ...(method !== '' ? { method } : {}),
          };
        }
        continue;
      }
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

  return {
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
  };
}
