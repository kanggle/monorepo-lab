'use client';

import { useEffect, useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { usePolicy, useUpsertPolicy } from '../hooks/use-scm-config';
import {
  ReorderPolicyInputSchema,
  type ReorderPolicyInput,
} from '../api/types';
import { ConfigConfirmDialog } from './ConfigConfirmDialog';

/**
 * Reorder-policy inspect (GET) + upsert (PUT) form for a single SKU
 * (TASK-PC-FE-080 / § 2.4.6.2). 404 = "not configured yet → create" (NOT an
 * error toast); PUT is confirm-gated, full-row, idempotent. Edits affect FUTURE
 * evaluation only.
 */

export interface PolicyFormProps {
  skuCode: string;
}

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

export function PolicyForm({ skuCode }: PolicyFormProps) {
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

  return (
    <section
      aria-labelledby="policy-heading"
      data-testid="policy-form"
      className="rounded-lg border border-border p-4"
    >
      <h2 id="policy-heading" className="text-lg font-semibold text-foreground">
        재주문 정책 (reorder policy)
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        재주문점·안전재고·재주문 수량을 설정합니다. 이 값은 이후 보충 추천
        평가에만 반영됩니다.
      </p>

      {q.isLoading ? (
        <p className="mt-4 text-sm text-muted-foreground" data-testid="policy-loading">
          불러오는 중…
        </p>
      ) : forbidden ? (
        <p
          role="status"
          data-testid="policy-forbidden"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </p>
      ) : rateLimited ? (
        <p
          role="status"
          data-testid="policy-ratelimited"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          {messageForCode('RATE_LIMIT_EXCEEDED')}
        </p>
      ) : degraded ? (
        <p
          role="status"
          data-testid="policy-degraded"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          재주문 정책을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속
          사용할 수 있습니다.
        </p>
      ) : (
        <>
          {notConfigured && (
            <p
              role="status"
              data-testid="policy-not-configured"
              className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
            >
              이 SKU 에는 아직 재주문 정책이 설정되어 있지 않습니다. 아래에서
              값을 입력해 <strong>생성</strong>하세요.
            </p>
          )}

          <form onSubmit={openConfirm} className="mt-4 grid gap-4">
            <NumberField
              id={reorderPointId}
              label="재주문점 (reorderPoint)"
              testid="policy-reorderPoint"
              value={fields.reorderPoint}
              error={fieldErrors.reorderPoint}
              onChange={(v) =>
                setFields((f) => ({ ...f, reorderPoint: v }))
              }
            />
            <NumberField
              id={safetyStockId}
              label="안전재고 (safetyStock)"
              testid="policy-safetyStock"
              value={fields.safetyStock}
              error={fieldErrors.safetyStock}
              onChange={(v) => setFields((f) => ({ ...f, safetyStock: v }))}
            />
            <NumberField
              id={reorderQtyId}
              label="재주문 수량 (reorderQty)"
              testid="policy-reorderQty"
              value={fields.reorderQty}
              error={fieldErrors.reorderQty}
              onChange={(v) => setFields((f) => ({ ...f, reorderQty: v }))}
            />

            {submitError && (
              <p
                role="alert"
                data-testid="policy-submit-error"
                className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {submitError}
              </p>
            )}

            <div>
              <Button type="submit" data-testid="policy-save">
                {notConfigured ? '정책 생성' : '정책 저장'}
              </Button>
            </div>
          </form>
        </>
      )}

      <ConfigConfirmDialog
        open={pendingBody !== null}
        title="재주문 정책을 저장할까요?"
        description={`SKU ${skuCode} 의 재주문 정책을 아래 값으로 설정(upsert)합니다.`}
        confirmLabel="저장"
        summary={
          pendingBody
            ? [
                { label: '재주문점', value: String(pendingBody.reorderPoint) },
                { label: '안전재고', value: String(pendingBody.safetyStock) },
                { label: '재주문 수량', value: String(pendingBody.reorderQty) },
              ]
            : []
        }
        pending={upsert.isPending}
        errorMessage={null}
        onConfirm={confirmUpsert}
        onCancel={() => setPendingBody(null)}
      />
    </section>
  );
}

interface NumberFieldProps {
  id: string;
  label: string;
  testid: string;
  value: string;
  error?: string;
  onChange: (v: string) => void;
}

function NumberField({
  id,
  label,
  testid,
  value,
  error,
  onChange,
}: NumberFieldProps) {
  const errId = `${id}-err`;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <input
        id={id}
        type="number"
        inputMode="numeric"
        min={0}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        data-testid={testid}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      />
      {error && (
        <p
          id={errId}
          role="alert"
          data-testid={`${testid}-error`}
          className="mt-1 text-sm text-destructive"
        >
          {error}
        </p>
      )}
    </div>
  );
}
