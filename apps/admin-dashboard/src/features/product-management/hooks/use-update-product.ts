import { updateProduct } from '../api/product-api';
import { productKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { UpdateProductRequest } from '@repo/types';

export function useUpdateProduct() {
  return useInvalidatingMutation({
    mutationFn: ({ productId, data }: { productId: string; data: UpdateProductRequest }) =>
      updateProduct(productId, data),
    invalidate: (variables) => [productKeys.all, productKeys.detail(variables.productId)],
    errorMessage: '상품 수정에 실패했습니다.',
  });
}
