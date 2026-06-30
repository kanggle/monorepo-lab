'use client';

import type { Variant } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { VariantTable } from './VariantTable';
import { VariantAddRow } from './VariantAddRow';
import { useVariantEditor } from './use-variant-editor';

/**
 * Inline variant CRUD for a product detail (TASK-PC-FE-081 — § 2.4.10
 * #6 add / #7 update / #8 delete). Add is an inline row; update is in-place;
 * delete is confirm-gated. Stock is NOT edited here (UpdateVariantRequest has
 * no stock field — stock is the separate adjust surface, StockAdjustDialog).
 *
 * Each mutation invalidates the product detail query so the variant table
 * reflects the new state.
 *
 * TASK-PC-FE-142: CRUD state + mutations live in {@link useVariantEditor}, the
 * editable list in {@link VariantTable}, the add row in {@link VariantAddRow}.
 * This container wires them + the delete ConfirmDialog (behavior-preserving split).
 */
export interface VariantEditorProps {
  productId: string;
  variants: Variant[];
}

const inputCls =
  'w-full rounded-md border border-border bg-background px-2 py-1 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

export function VariantEditor({ productId, variants }: VariantEditorProps) {
  const { edit, openDelete, add, del } = useVariantEditor(productId);

  return (
    <div data-testid="variant-editor">
      <h3 className="mb-2 text-base font-medium text-foreground">옵션(variant)</h3>

      <VariantTable
        variants={variants}
        edit={edit}
        openDelete={openDelete}
        inputCls={inputCls}
      />

      <VariantAddRow add={add} inputCls={inputCls} />

      <ConfirmDialog
        open={del.toDelete !== null}
        title="옵션을 삭제할까요?"
        description={del.description}
        confirmLabel="삭제"
        tone="destructive"
        pending={del.pending}
        errorMessage={del.delError}
        onConfirm={del.confirmDelete}
        onCancel={del.cancelDelete}
      />
    </div>
  );
}
