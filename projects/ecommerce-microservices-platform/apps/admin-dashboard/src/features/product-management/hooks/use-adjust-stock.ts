import { adjustStock } from '../api/product-api';
import { productKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { StockAdjustmentRequest } from '@repo/types';

export function useAdjustStock() {
  return useInvalidatingMutation({
    mutationFn: ({ productId, data }: { productId: string; data: StockAdjustmentRequest }) =>
      adjustStock(productId, data),
    invalidate: (variables) => [productKeys.all, productKeys.detail(variables.productId)],
    errorMessage: '재고 조정에 실패했습니다.',
  });
}
