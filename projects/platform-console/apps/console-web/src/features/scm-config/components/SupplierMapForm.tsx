'use client';

import { useEffect, useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useSupplierMap,
  useUpsertSupplierMap,
} from '../hooks/use-scm-config';
import {
  SupplierMapInputSchema,
  type SupplierMapInput,
  COMMON_CURRENCIES,
} from '../api/types';
import { ConfigConfirmDialog } from './ConfigConfirmDialog';

/**
 * SKU→supplier mapping inspect (GET) + upsert (PUT) form for a single SKU
 * (TASK-PC-FE-080 / § 2.4.6.2). This is the operational fix-path for FE-077's
 * `SKU_SUPPLIER_UNMAPPED` — adding the mapping here lets the operator return to
 * 보충 and approve. 404 = "not configured yet → create" (NOT an error toast);
 * PUT is confirm-gated, full-row, idempotent. `supplierId` is FREE-TEXT/uuid (no
 * supplier master in v1 — validate shape only). Edits affect FUTURE evaluation
 * only.
 */

export interface SupplierMapFormProps {
  skuCode: string;
}

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

export function SupplierMapForm({ skuCode }: SupplierMapFormProps) {
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

  return (
    <section
      aria-labelledby="map-heading"
      data-testid="supplier-map-form"
      className="rounded-lg border border-border p-4"
    >
      <h2 id="map-heading" className="text-lg font-semibold text-foreground">
        공급사 매핑 (sku-supplier-map)
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        이 SKU 의 공급사·기본 발주 수량·리드타임·통화를 설정합니다. 보충 추천
        승인 시 공급사 매핑이 없으면 발주(PO)를 생성할 수 없으므로
        (<code>SKU_SUPPLIER_UNMAPPED</code>) 여기서 매핑을 추가합니다.
      </p>

      {q.isLoading ? (
        <p className="mt-4 text-sm text-muted-foreground" data-testid="map-loading">
          불러오는 중…
        </p>
      ) : forbidden ? (
        <p
          role="status"
          data-testid="map-forbidden"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </p>
      ) : rateLimited ? (
        <p
          role="status"
          data-testid="map-ratelimited"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          {messageForCode('RATE_LIMIT_EXCEEDED')}
        </p>
      ) : degraded ? (
        <p
          role="status"
          data-testid="map-degraded"
          className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
        >
          공급사 매핑을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속
          사용할 수 있습니다.
        </p>
      ) : (
        <>
          {notConfigured && (
            <p
              role="status"
              data-testid="map-not-configured"
              className="mt-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
            >
              이 SKU 에는 아직 공급사 매핑이 설정되어 있지 않습니다. 아래에서
              값을 입력해 <strong>생성</strong>하세요.
            </p>
          )}

          <form onSubmit={openConfirm} className="mt-4 grid gap-4">
            <div>
              <label
                htmlFor={supplierIdId}
                className="block text-sm font-medium text-foreground"
              >
                공급사 ID (supplierId · free-text/uuid)
              </label>
              <input
                id={supplierIdId}
                type="text"
                value={fields.supplierId}
                onChange={(e) =>
                  setFields((f) => ({ ...f, supplierId: e.target.value }))
                }
                data-testid="map-supplierId"
                aria-invalid={fieldErrors.supplierId ? true : undefined}
                aria-describedby={
                  fieldErrors.supplierId ? `${supplierIdId}-err` : undefined
                }
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              />
              {fieldErrors.supplierId && (
                <p
                  id={`${supplierIdId}-err`}
                  role="alert"
                  data-testid="map-supplierId-error"
                  className="mt-1 text-sm text-destructive"
                >
                  {fieldErrors.supplierId}
                </p>
              )}
            </div>

            <NumberField
              id={defaultOrderQtyId}
              label="기본 발주 수량 (defaultOrderQty)"
              testid="map-defaultOrderQty"
              value={fields.defaultOrderQty}
              error={fieldErrors.defaultOrderQty}
              onChange={(v) =>
                setFields((f) => ({ ...f, defaultOrderQty: v }))
              }
            />
            <NumberField
              id={leadTimeDaysId}
              label="리드타임 (leadTimeDays)"
              testid="map-leadTimeDays"
              value={fields.leadTimeDays}
              error={fieldErrors.leadTimeDays}
              onChange={(v) => setFields((f) => ({ ...f, leadTimeDays: v }))}
            />

            <div>
              <label
                htmlFor={currencyId}
                className="block text-sm font-medium text-foreground"
              >
                통화 (currency)
              </label>
              <input
                id={currencyId}
                type="text"
                list={`${currencyId}-options`}
                maxLength={3}
                value={fields.currency}
                onChange={(e) =>
                  setFields((f) => ({
                    ...f,
                    currency: e.target.value.toUpperCase(),
                  }))
                }
                data-testid="map-currency"
                aria-invalid={fieldErrors.currency ? true : undefined}
                aria-describedby={
                  fieldErrors.currency ? `${currencyId}-err` : undefined
                }
                className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm uppercase text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              />
              <datalist id={`${currencyId}-options`}>
                {COMMON_CURRENCIES.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
              {fieldErrors.currency && (
                <p
                  id={`${currencyId}-err`}
                  role="alert"
                  data-testid="map-currency-error"
                  className="mt-1 text-sm text-destructive"
                >
                  {fieldErrors.currency}
                </p>
              )}
            </div>

            {submitError && (
              <p
                role="alert"
                data-testid="map-submit-error"
                className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {submitError}
              </p>
            )}

            <div>
              <Button type="submit" data-testid="map-save">
                {notConfigured ? '매핑 생성' : '매핑 저장'}
              </Button>
            </div>
          </form>
        </>
      )}

      <ConfigConfirmDialog
        open={pendingBody !== null}
        title="공급사 매핑을 저장할까요?"
        description={`SKU ${skuCode} 의 공급사 매핑을 아래 값으로 설정(upsert)합니다.`}
        confirmLabel="저장"
        summary={
          pendingBody
            ? [
                { label: '공급사 ID', value: pendingBody.supplierId },
                {
                  label: '기본 발주 수량',
                  value: String(pendingBody.defaultOrderQty),
                },
                { label: '리드타임', value: String(pendingBody.leadTimeDays) },
                { label: '통화', value: pendingBody.currency },
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
