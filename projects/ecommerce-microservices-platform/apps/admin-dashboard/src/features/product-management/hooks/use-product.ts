import { useQuery } from '@tanstack/react-query';
import { getProduct } from '../api/product-api';
import { productKeys } from './query-keys';

export function useProduct(productId: string) {
  return useQuery({
    queryKey: productKeys.detail(productId),
    queryFn: () => getProduct(productId),
    enabled: !!productId,
  });
}
