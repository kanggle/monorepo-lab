'use client';

import { Button } from '@/shared/ui/Button';
import { usePolicyForm } from '../hooks/use-policy-form';
import { ConfigConfirmDialog } from './ConfigConfirmDialog';
import { SeedFormStates } from './SeedFormStates';
import { SeedNumberField } from './SeedNumberField';

/**
 * Reorder-policy inspect (GET) + upsert (PUT) form for a single SKU
 * (TASK-PC-FE-080 / § 2.4.6.2). 404 = "not configured yet → create" (NOT an
 * error toast); PUT is confirm-gated, full-row, idempotent. Edits affect FUTURE
 * evaluation only.
 *
 * The form-model (field state, per-SKU hydration, validation, confirm-gated
 * upsert) lives in {@link usePolicyForm}; the loading/forbidden/rate-limited/
 * degraded banner in the shared {@link SeedFormStates} leaf (TASK-PC-FE-215
 * split). This component keeps the JSX + wiring only.
 */

export interface PolicyFormProps {
  skuCode: string;
}

export function PolicyForm({ skuCode }: PolicyFormProps) {
  const {
    ids,
    fields,
    setFields,
    fieldErrors,
    pendingBody,
    setPendingBody,
    submitError,
    isLoading,
    forbidden,
    rateLimited,
    degraded,
    notConfigured,
    upsertPending,
    openConfirm,
    confirmUpsert,
  } = usePolicyForm(skuCode);

  const blocked = isLoading || forbidden || rateLimited || degraded;

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

      {blocked ? (
        <SeedFormStates
          testidPrefix="policy"
          isLoading={isLoading}
          forbidden={forbidden}
          rateLimited={rateLimited}
          degradedMessage="재주문 정책을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속 사용할 수 있습니다."
        />
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
            <SeedNumberField
              id={ids.reorderPointId}
              label="재주문점 (reorderPoint)"
              testid="policy-reorderPoint"
              value={fields.reorderPoint}
              error={fieldErrors.reorderPoint}
              onChange={(v) =>
                setFields((f) => ({ ...f, reorderPoint: v }))
              }
            />
            <SeedNumberField
              id={ids.safetyStockId}
              label="안전재고 (safetyStock)"
              testid="policy-safetyStock"
              value={fields.safetyStock}
              error={fieldErrors.safetyStock}
              onChange={(v) => setFields((f) => ({ ...f, safetyStock: v }))}
            />
            <SeedNumberField
              id={ids.reorderQtyId}
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
        pending={upsertPending}
        errorMessage={null}
        onConfirm={confirmUpsert}
        onCancel={() => setPendingBody(null)}
      />
    </section>
  );
}
