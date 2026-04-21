import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type {
  ProductDetail,
  ProductStatus,
  CreateProductRequest,
  UpdateProductRequest,
} from '@repo/types';
import { useSubmitAction } from '@/shared/hooks/use-async-action';
import { useCreateProduct } from './use-create-product';
import { useUpdateProduct } from './use-update-product';
import type { VariantInput } from '../components/VariantEditor';

export function useProductForm(product?: ProductDetail) {
  const isEdit = !!product;
  const router = useRouter();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();

  const [name, setName] = useState(product?.name ?? '');
  const [description, setDescription] = useState(product?.description ?? '');
  const [price, setPrice] = useState(product?.price ?? 0);
  const [categoryId, setCategoryId] = useState(product?.categoryId ?? '');
  const [status, setStatus] = useState<ProductStatus>(product?.status ?? 'ON_SALE');
  const [variants, setVariants] = useState<VariantInput[]>(
    product?.variants.map((v, i) => ({
      _key: i, optionName: v.optionName, stock: v.stock, additionalPrice: v.additionalPrice,
    })) ?? [{ _key: 0, optionName: '', stock: 0, additionalPrice: 0 }],
  );
  const { error, isSubmitting, runSubmit } = useSubmitAction();

  const isValid = name.trim().length > 0 && price > 0 && categoryId.trim().length > 0;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting) return;

    await runSubmit(async () => {
      if (isEdit) {
        const data: UpdateProductRequest = { name: name.trim(), description: description.trim(), price, status };
        await updateProduct.mutateAsync({ productId: product.id, data });
        router.push(`/products/${product.id}`);
      } else {
        const data: CreateProductRequest = {
          name: name.trim(), description: description.trim(), price, categoryId: categoryId.trim(),
          variants: variants.filter((v) => v.optionName.trim().length > 0)
            .map((v) => ({ optionName: v.optionName.trim(), stock: v.stock, additionalPrice: v.additionalPrice })),
        };
        const created = await createProduct.mutateAsync(data);
        router.push(`/products/${created.id}`);
      }
    }, '저장에 실패했습니다.');
  }

  return {
    name, setName,
    description, setDescription,
    price, setPrice,
    categoryId, setCategoryId,
    status, setStatus,
    variants, setVariants,
    error,
    isSubmitting,
    isEdit,
    isValid,
    handleSubmit,
  };
}
