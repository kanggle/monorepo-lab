'use client';

import { useCallback } from 'react';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { getErrorMessage } from '@repo/types/guards';
import type { WishlistItem } from '@repo/types';
import { removeFromWishlist } from '../api/wishlist-api';
import { wishlistKeys } from '../model/query-keys';
import { WishlistItemInfo } from './WishlistItemInfo';
import { WishlistItemActions } from './WishlistItemActions';

interface WishlistItemCardProps {
  item: WishlistItem;
}

export function WishlistItemCard({ item }: WishlistItemCardProps) {
  const queryClient = useQueryClient();
  const isDeleted = item.productStatus === 'DELETED' || item.productName === null;

  const removeMutation = useMutation({
    mutationFn: () => removeFromWishlist(item.wishlistItemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: wishlistKeys.all });
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, '위시리스트 삭제에 실패했습니다.'));
    },
  });

  const handleRemove = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!removeMutation.isPending) {
        removeMutation.mutate();
      }
    },
    [removeMutation],
  );

  const formattedDate = new Date(item.addedAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  const content = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--space-4)',
        padding: 'var(--space-4)',
        border: '1px solid var(--color-border-light)',
        borderRadius: 'var(--radius-md)',
        background: 'var(--color-surface)',
        opacity: isDeleted ? 0.6 : 1,
        transition: 'box-shadow var(--transition-fast)',
      }}
    >
      <WishlistItemInfo item={item} isDeleted={isDeleted} formattedDate={formattedDate} />
      <WishlistItemActions isPending={removeMutation.isPending} onRemove={handleRemove} />
    </div>
  );

  if (isDeleted) {
    return content;
  }

  return (
    <Link
      href={`/products/${item.productId}`}
      style={{ textDecoration: 'none', color: 'inherit' }}
    >
      {content}
    </Link>
  );
}
