import { createProduct } from '../api/product-api';
import { productKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';

export function useCreateProduct() {
  return useInvalidatingMutation({
    mutationFn: createProduct,
    invalidate: [productKeys.all],
    errorMessage: '상품 생성에 실패했습니다.',
  });
}
