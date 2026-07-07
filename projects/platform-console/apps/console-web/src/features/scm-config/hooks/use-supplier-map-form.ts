'use client';

import { useEffect, useId, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useSupplierMap, useUpsertSupplierMap } from './use-scm-config';
import {
  SupplierMapInputSchema,
  type SupplierMapInput,
} from '../api/types';

/**
 * Form-model hook for {@link SupplierMapForm} (TASK-PC-FE-215 split — the
 * PC-FE-196 "flat form → hook, no forced component split" precedent). Owns the
 * field state, the per-SKU hydration effect, the zod validation → confirm gate,
 * and the confirm-gated idempotent upsert. Behaviour byte-preserved from the
 * pre-split component (same read/upsert hooks, same validation copy, same
 * `hydratedFor` re-seed guard). The component keeps the JSX + wiring only.
 */

interface FieldState {
  supplierId: string;
  defaultOrderQty: string;
  leadTimeDays: string;
  currency: string;
}

const EMPTY_FIELDS: FieldState = {
  supplierId: '',
  defaultOrderQty: '',
  leadTimeDays: '',
  currency: 'KRW',
};

export function useSupplierMapForm(skuCode: string) {
  const supplierIdId = useId();
  const defaultOrderQtyId = useId();
  const leadTimeDaysId = useId();
  const currencyId = useId();

  const q = useSupplierMap(skuCode);
  const upsert = useUpsertSupplierMap();

  const [fields, setFields] = useState<FieldState>(EMPTY_FIELDS);
  const [fieldErrors, setFieldErrors] = useState<Partial<FieldState>>({});
  const [pendingBody, setPendingBody] = useState<SupplierMapInput | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [hydratedFor, setHydratedFor] = useState<string | null>(null);

  useEffect(() => {
    if (q.isSuccess && hydratedFor !== skuCode) {
      if (q.data.found) {
        const m = q.data.value;
        setFields({
          supplierId: m.supplierId ?? '',
          defaultOrderQty: m.defaultOrderQty?.toString() ?? '',
          leadTimeDays: m.leadTimeDays?.toString() ?? '',
          currency: m.currency ?? 'KRW',
        });
      } else {
        setFields(EMPTY_FIELDS);
      }
      setFieldErrors({});
      setSubmitError(null);
      setHydratedFor(skuCode);
    }
  }, [q.isSuccess, q.data, skuCode, hydratedFor]);

  const apiError = q.error instanceof ApiError ? (q.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const rateLimited = apiError?.code === 'RATE_LIMIT_EXCEEDED';
  const degraded = q.isError && !forbidden && !rateLimited;
  const notConfigured = q.isSuccess && !q.data.found;

  function openConfirm(e: React.FormEvent) {
    e.preventDefault();
    setSubmitError(null);
    const parsed = SupplierMapInputSchema.safeParse({
      supplierId: fields.supplierId,
      defaultOrderQty: Number(fields.defaultOrderQty),
      leadTimeDays: Number(fields.leadTimeDays),
      currency: fields.currency,
    });
    if (!parsed.success) {
      const errs: Partial<FieldState> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof FieldState;
        if (key) {
          errs[key] =
            key === 'supplierId'
              ? '공급사 ID 를 입력하세요 (free-text/uuid).'
              : key === 'currency'
                ? '3자리 통화 코드를 입력하세요 (예: KRW).'
                : '0 이상의 올바른 정수를 입력하세요.';
        }
      }
      (['defaultOrderQty', 'leadTimeDays'] as const).forEach((k) => {
        if (fields[k].trim() === '' || Number.isNaN(Number(fields[k]))) {
          errs[k] = '0 이상의 올바른 정수를 입력하세요.';
        }
      });
      setFieldErrors(errs);
      return;
    }
    setFieldErrors({});
    setPendingBody({ ...parsed.data, currency: parsed.data.currency.toUpperCase() });
  }

  function confirmUpsert() {
    if (!pendingBody) return;
    upsert.mutate(
      { skuCode, body: pendingBody },
      {
        onSuccess: () => {
          setPendingBody(null);
          setSubmitError(null);
        },
        onError: (err) => {
          const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
          setSubmitError(
            messageForCode(code, '매핑을 저장하지 못했습니다.'),
          );
          setPendingBody(null);
        },
      },
    );
  }

  return {
    ids: { supplierIdId, defaultOrderQtyId, leadTimeDaysId, currencyId },
    fields,
    setFields,
    fieldErrors,
    pendingBody,
    setPendingBody,
    submitError,
    isLoading: q.isLoading,
    forbidden,
    rateLimited,
    degraded,
    notConfigured,
    upsertPending: upsert.isPending,
    openConfirm,
    confirmUpsert,
  };
}
