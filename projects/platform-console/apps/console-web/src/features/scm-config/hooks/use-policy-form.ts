'use client';

import { useEffect, useId, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { usePolicy, useUpsertPolicy } from './use-scm-config';
import {
  ReorderPolicyInputSchema,
  type ReorderPolicyInput,
} from '../api/types';

/**
 * Form-model hook for {@link PolicyForm} (TASK-PC-FE-215 split — the PC-FE-196
 * "flat form → hook, no forced component split" precedent). Owns the field
 * state, the per-SKU hydration effect, the zod validation → confirm gate, and
 * the confirm-gated idempotent upsert. Behaviour byte-preserved from the
 * pre-split component (same read/upsert hooks, same validation copy, same
 * `hydratedFor` re-seed guard). The component keeps the JSX + wiring only.
 */

interface FieldState {
  reorderPoint: string;
  safetyStock: string;
  reorderQty: string;
}

const EMPTY_FIELDS: FieldState = {
  reorderPoint: '',
  safetyStock: '',
  reorderQty: '',
};

export function usePolicyForm(skuCode: string) {
  const reorderPointId = useId();
  const safetyStockId = useId();
  const reorderQtyId = useId();

  const q = usePolicy(skuCode);
  const upsert = useUpsertPolicy();

  const [fields, setFields] = useState<FieldState>(EMPTY_FIELDS);
  const [fieldErrors, setFieldErrors] = useState<Partial<FieldState>>({});
  const [pendingBody, setPendingBody] = useState<ReorderPolicyInput | null>(
    null,
  );
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Tracks which SKU's row we've hydrated the form from, so re-entering a SKU
  // re-seeds the inputs from the fetched row (but a user's in-progress edits
  // are not clobbered on a background refetch of the SAME sku).
  const [hydratedFor, setHydratedFor] = useState<string | null>(null);

  // Seed the form from the fetched row once per SKU (a found row → its values;
  // a not-found → blank create form).
  useEffect(() => {
    if (q.isSuccess && hydratedFor !== skuCode) {
      if (q.data.found) {
        const p = q.data.value;
        setFields({
          reorderPoint: p.reorderPoint?.toString() ?? '',
          safetyStock: p.safetyStock?.toString() ?? '',
          reorderQty: p.reorderQty?.toString() ?? '',
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
    const parsed = ReorderPolicyInputSchema.safeParse({
      reorderPoint: Number(fields.reorderPoint),
      safetyStock: Number(fields.safetyStock),
      reorderQty: Number(fields.reorderQty),
    });
    if (!parsed.success) {
      const errs: Partial<FieldState> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof FieldState;
        if (key) errs[key] = '0 이상의 올바른 정수를 입력하세요.';
      }
      // A non-numeric input produces NaN → also a field error.
      (['reorderPoint', 'safetyStock', 'reorderQty'] as const).forEach((k) => {
        if (fields[k].trim() === '' || Number.isNaN(Number(fields[k]))) {
          errs[k] = '0 이상의 올바른 정수를 입력하세요.';
        }
      });
      setFieldErrors(errs);
      return;
    }
    setFieldErrors({});
    setPendingBody(parsed.data);
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
          // VALIDATION_ERROR (422) → inline; the entered values are preserved
          // (we keep `fields` untouched, only close the confirm step).
          setSubmitError(
            messageForCode(code, '설정을 저장하지 못했습니다.'),
          );
          setPendingBody(null);
        },
      },
    );
  }

  return {
    ids: { reorderPointId, safetyStockId, reorderQtyId },
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
