'use client';

import { Button } from '@/shared/ui/Button';
import { useSupplierMapForm } from '../hooks/use-supplier-map-form';
import { COMMON_CURRENCIES } from '../api/types';
import { ConfigConfirmDialog } from './ConfigConfirmDialog';
import { SeedFormStates } from './SeedFormStates';
import { SeedNumberField } from './SeedNumberField';
import { SeedTextField } from './SeedTextField';

/**
 * SKU→supplier mapping inspect (GET) + upsert (PUT) form for a single SKU
 * (TASK-PC-FE-080 / § 2.4.6.2). This is the operational fix-path for FE-077's
 * `SKU_SUPPLIER_UNMAPPED` — adding the mapping here lets the operator return to
 * 보충 and approve. 404 = "not configured yet → create" (NOT an error toast);
 * PUT is confirm-gated, full-row, idempotent. `supplierId` is FREE-TEXT/uuid (no
 * supplier master in v1 — validate shape only). Edits affect FUTURE evaluation
 * only.
 *
 * The form-model (field state, per-SKU hydration, validation, confirm-gated
 * upsert) lives in {@link useSupplierMapForm}; the loading/forbidden/rate-limited/
 * degraded banner in the shared {@link SeedFormStates} leaf (TASK-PC-FE-215
 * split). This component keeps the JSX + wiring only.
 */

export interface SupplierMapFormProps {
  skuCode: string;
}

export function SupplierMapForm({ skuCode }: SupplierMapFormProps) {
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
  } = useSupplierMapForm(skuCode);

  const blocked = isLoading || forbidden || rateLimited || degraded;

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

      {blocked ? (
        <SeedFormStates
          testidPrefix="map"
          isLoading={isLoading}
          forbidden={forbidden}
          rateLimited={rateLimited}
          degradedMessage="공급사 매핑을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속 사용할 수 있습니다."
        />
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
            <SeedTextField
              id={ids.supplierIdId}
              label="공급사 ID (supplierId · free-text/uuid)"
              testid="map-supplierId"
              value={fields.supplierId}
              error={fieldErrors.supplierId}
              onChange={(v) => setFields((f) => ({ ...f, supplierId: v }))}
            />

            <SeedNumberField
              id={ids.defaultOrderQtyId}
              label="기본 발주 수량 (defaultOrderQty)"
              testid="map-defaultOrderQty"
              value={fields.defaultOrderQty}
              error={fieldErrors.defaultOrderQty}
              onChange={(v) =>
                setFields((f) => ({ ...f, defaultOrderQty: v }))
              }
            />
            <SeedNumberField
              id={ids.leadTimeDaysId}
              label="리드타임 (leadTimeDays)"
              testid="map-leadTimeDays"
              value={fields.leadTimeDays}
              error={fieldErrors.leadTimeDays}
              onChange={(v) => setFields((f) => ({ ...f, leadTimeDays: v }))}
            />

            <SeedTextField
              id={ids.currencyId}
              label="통화 (currency)"
              testid="map-currency"
              value={fields.currency}
              error={fieldErrors.currency}
              onChange={(v) => setFields((f) => ({ ...f, currency: v }))}
              options={COMMON_CURRENCIES}
              maxLength={3}
              uppercase
            />

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
        pending={upsertPending}
        errorMessage={null}
        onConfirm={confirmUpsert}
        onCancel={() => setPendingBody(null)}
      />
    </section>
  );
}
