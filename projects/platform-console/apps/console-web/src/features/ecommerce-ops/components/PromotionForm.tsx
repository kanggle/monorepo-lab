'use client';

import { Button } from '@/shared/ui/Button';
import { type PromotionDetail } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { PromotionFormFields } from './PromotionFormFields';
import { usePromotionForm } from '../hooks/use-promotion-form';

/**
 * Create / update promotion form (TASK-PC-FE-086 — ADR-031 Phase 3b).
 * Used by both the `new` and `[id]/edit` pages.
 *
 *   - CREATE (no `existing`): all fields required per producer contract.
 *     On success → `/ecommerce/promotions`.
 *   - UPDATE (`existing` set): PUT full replace (all fields required).
 *     On success → `/ecommerce/promotions/{id}`.
 *
 * Confirm-gated submit; 422 family surfaced inline (NO `Idempotency-Key`).
 * Producer uses PUT (full replace) for updates — NOT PATCH.
 *
 * TASK-PC-FE-141: form state/validation/submit (incl. the day→Instant widening)
 * live in {@link usePromotionForm}. TASK-PC-FE-216: the flat fieldset region is
 * rendered by the presentational {@link PromotionFormFields} leaf (fed the hook's
 * own `ids`/`fields` bundles); this container only wires the hook to the markup
 * and owns the error/actions/confirm chrome (behavior-preserving split).
 */

export interface PromotionFormProps {
  /** When set, the form is in UPDATE mode for this promotion. */
  existing?: PromotionDetail;
}

export function PromotionForm({ existing }: PromotionFormProps) {
  const {
    router,
    isEdit,
    ids,
    fields,
    confirmOpen,
    error,
    pending,
    formValid,
    onSubmit,
    confirmSubmit,
    cancelConfirm,
  } = usePromotionForm(existing);

  return (
    <form
      onSubmit={onSubmit}
      className="max-w-2xl space-y-5"
      data-testid="promotion-form"
    >
      <PromotionFormFields ids={ids} fields={fields} />

      {error && !confirmOpen && (
        <p
          role="alert"
          className="text-sm text-destructive"
          data-testid="promotion-form-error"
        >
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <Button
          type="submit"
          disabled={!formValid || pending}
          data-testid="promotion-form-submit"
        >
          {isEdit ? '변경 저장' : '프로모션 등록'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
          data-testid="promotion-form-cancel"
        >
          취소
        </Button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title={isEdit ? '프로모션 변경을 저장할까요?' : '프로모션을 등록할까요?'}
        description={
          isEdit
            ? '입력한 내용으로 프로모션을 수정합니다.'
            : '입력한 내용으로 새 프로모션을 등록합니다.'
        }
        confirmLabel={isEdit ? '저장' : '등록'}
        pending={pending}
        errorMessage={error}
        onConfirm={confirmSubmit}
        onCancel={cancelConfirm}
      />
    </form>
  );
}
