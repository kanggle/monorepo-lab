import { addVariant, updateVariant, deleteVariant } from '../api/product-api';
import { productKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';

function variantInvalidateKeys(productId: string) {
  return [productKeys.detail(productId), productKeys.all];
}

export function useAddVariant(productId: string) {
  return useInvalidatingMutation({
    mutationFn: (data: { optionName: string; stock: number; additionalPrice: number }) =>
      addVariant(productId, data),
    invalidate: variantInvalidateKeys(productId),
  });
}

export function useUpdateVariant(productId: string) {
  return useInvalidatingMutation({
    mutationFn: ({ variantId, data }: { variantId: string; data: { optionName: string; additionalPrice: number } }) =>
      updateVariant(productId, variantId, data),
    invalidate: variantInvalidateKeys(productId),
  });
}

export function useDeleteVariant(productId: string) {
  return useInvalidatingMutation({
    mutationFn: (variantId: string) => deleteVariant(productId, variantId),
    invalidate: variantInvalidateKeys(productId),
  });
}
