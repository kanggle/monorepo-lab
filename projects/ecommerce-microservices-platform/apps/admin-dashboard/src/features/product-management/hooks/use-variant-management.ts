import { useState } from 'react';
import { useAsyncAction } from '@/shared/hooks/use-async-action';
import { useAddVariant, useUpdateVariant, useDeleteVariant } from './use-variant-mutations';

interface EditState {
  variantId: string;
  optionName: string;
  additionalPrice: number;
}

interface AddState {
  optionName: string;
  stock: number;
  additionalPrice: number;
}

export function useVariantManagement(productId: string, onChanged: () => void) {
  const [editing, setEditing] = useState<EditState | null>(null);
  const [adding, setAdding] = useState<AddState | null>(null);
  const { error, execute } = useAsyncAction();

  const addMutation = useAddVariant(productId);
  const updateMutation = useUpdateVariant(productId);
  const deleteMutation = useDeleteVariant(productId);

  const isMutating = addMutation.isPending || updateMutation.isPending || deleteMutation.isPending;

  async function handleUpdate() {
    if (!editing || !editing.optionName.trim()) return;
    await execute(async () => {
      await updateMutation.mutateAsync({
        variantId: editing.variantId,
        data: { optionName: editing.optionName.trim(), additionalPrice: editing.additionalPrice },
      });
      setEditing(null);
      onChanged();
    }, '옵션 수정에 실패했습니다.');
  }

  async function handleDelete(variantId: string) {
    await execute(async () => {
      await deleteMutation.mutateAsync(variantId);
      onChanged();
    }, '옵션 삭제에 실패했습니다.');
  }

  async function handleAdd() {
    if (!adding || !adding.optionName.trim()) return;
    await execute(async () => {
      await addMutation.mutateAsync({
        optionName: adding.optionName.trim(),
        stock: adding.stock,
        additionalPrice: adding.additionalPrice,
      });
      setAdding(null);
      onChanged();
    }, '옵션 추가에 실패했습니다.');
  }

  return {
    editing,
    setEditing,
    adding,
    setAdding,
    error,
    isMutating,
    handleUpdate,
    handleDelete,
    handleAdd,
  };
}
