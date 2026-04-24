import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type {
  PromotionDetail,
  DiscountType,
  CreatePromotionRequest,
  UpdatePromotionRequest,
} from '@repo/types';
import { useSubmitAction } from '@/shared/hooks/use-async-action';
import { useCreatePromotion } from './use-create-promotion';
import { useUpdatePromotion } from './use-update-promotion';

function toDateInputValue(isoString: string): string {
  return isoString.slice(0, 10);
}

export function usePromotionForm(promotion?: PromotionDetail) {
  const isEdit = !!promotion;
  const router = useRouter();
  const createPromotion = useCreatePromotion();
  const updatePromotion = useUpdatePromotion();

  const [name, setName] = useState(promotion?.name ?? '');
  const [description, setDescription] = useState(promotion?.description ?? '');
  const [discountType, setDiscountType] = useState<DiscountType>(promotion?.discountType ?? 'FIXED');
  const [discountValue, setDiscountValue] = useState(promotion?.discountValue ?? 0);
  const [maxDiscountAmount, setMaxDiscountAmount] = useState(promotion?.maxDiscountAmount ?? 0);
  const [maxIssuanceCount, setMaxIssuanceCount] = useState(promotion?.maxIssuanceCount ?? 0);
  const [startDate, setStartDate] = useState(promotion ? toDateInputValue(promotion.startDate) : '');
  const [endDate, setEndDate] = useState(promotion ? toDateInputValue(promotion.endDate) : '');
  const { error, isSubmitting, runSubmit } = useSubmitAction();

  const isValid =
    name.trim().length > 0 &&
    discountValue > 0 &&
    maxIssuanceCount > 0 &&
    startDate.length > 0 &&
    endDate.length > 0 &&
    startDate < endDate;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting) return;

    await runSubmit(async () => {
      const formData = {
        name: name.trim(),
        description: description.trim(),
        discountType,
        discountValue,
        maxDiscountAmount,
        maxIssuanceCount,
        startDate: startDate + 'T00:00:00.000Z',
        endDate: endDate + 'T23:59:59.999Z',
      };

      if (isEdit) {
        const data: UpdatePromotionRequest = formData;
        await updatePromotion.mutateAsync({ promotionId: promotion.promotionId, data });
        router.push(`/promotions/${promotion.promotionId}`);
      } else {
        const data: CreatePromotionRequest = formData;
        const created = await createPromotion.mutateAsync(data);
        router.push(`/promotions/${created.promotionId}`);
      }
    }, '저장에 실패했습니다.');
  }

  return {
    name, setName,
    description, setDescription,
    discountType, setDiscountType,
    discountValue, setDiscountValue,
    maxDiscountAmount, setMaxDiscountAmount,
    maxIssuanceCount, setMaxIssuanceCount,
    startDate, setStartDate,
    endDate, setEndDate,
    error,
    isSubmitting,
    isEdit,
    isValid,
    handleSubmit,
  };
}
