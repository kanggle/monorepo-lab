'use client';

import { useState } from 'react';
import { ConfirmDialog } from '@/shared/ui';
import type { ProductImage } from '../types/product-image';

interface Props {
  image: ProductImage;
  onSetPrimary: (imageId: string) => void;
  onDelete: (imageId: string) => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
  isFirst: boolean;
  isLast: boolean;
}

const styles = {
  card: {
    position: 'relative',
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    overflow: 'hidden',
    backgroundColor: '#fff',
  } as const,
  primaryCard: {
    position: 'relative',
    border: '2px solid #1A1A2E',
    borderRadius: '8px',
    overflow: 'hidden',
    backgroundColor: '#fff',
  } as const,
  imageWrapper: {
    width: '100%',
    aspectRatio: '1',
    overflow: 'hidden',
    backgroundColor: '#f9fafb',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  } as const,
  image: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  } as const,
  primaryBadge: {
    position: 'absolute',
    top: '8px',
    left: '8px',
    backgroundColor: '#1A1A2E',
    color: '#fff',
    fontSize: '0.7rem',
    padding: '2px 8px',
    borderRadius: '4px',
    fontWeight: 600,
  } as const,
  actions: {
    padding: '8px',
    display: 'flex',
    gap: '4px',
    justifyContent: 'center',
    flexWrap: 'wrap',
  } as const,
  btn: {
    padding: '4px 8px',
    fontSize: '0.75rem',
    border: '1px solid #d1d5db',
    borderRadius: '4px',
    backgroundColor: '#fff',
    cursor: 'pointer',
    color: '#374151',
  } as const,
  deleteBtn: {
    padding: '4px 8px',
    fontSize: '0.75rem',
    border: '1px solid #fecaca',
    borderRadius: '4px',
    backgroundColor: '#fef2f2',
    cursor: 'pointer',
    color: '#dc2626',
  } as const,
  orderBtns: {
    display: 'flex',
    gap: '2px',
  } as const,
};

export function ImageItem({
  image,
  onSetPrimary,
  onDelete,
  onMoveUp,
  onMoveDown,
  isFirst,
  isLast,
}: Props) {
  const [confirmOpen, setConfirmOpen] = useState(false);

  return (
    <>
      <div style={image.isPrimary ? styles.primaryCard : styles.card} data-testid={`image-item-${image.imageId}`}>
        {image.isPrimary && (
          <span style={styles.primaryBadge}>대표</span>
        )}
        <div style={styles.imageWrapper}>
          <img
            src={image.url}
            alt={`상품 이미지 ${image.sortOrder + 1}`}
            style={styles.image}
          />
        </div>
        <div style={styles.actions}>
          {!image.isPrimary && (
            <button
              type="button"
              style={styles.btn}
              onClick={() => onSetPrimary(image.imageId)}
              aria-label="대표 이미지로 설정"
            >
              대표 설정
            </button>
          )}
          <div style={styles.orderBtns}>
            <button
              type="button"
              style={styles.btn}
              onClick={onMoveUp}
              disabled={isFirst}
              aria-label="위로 이동"
            >
              &uarr;
            </button>
            <button
              type="button"
              style={styles.btn}
              onClick={onMoveDown}
              disabled={isLast}
              aria-label="아래로 이동"
            >
              &darr;
            </button>
          </div>
          <button
            type="button"
            style={styles.deleteBtn}
            onClick={() => setConfirmOpen(true)}
            aria-label="이미지 삭제"
          >
            삭제
          </button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="이미지 삭제"
        message="이 이미지를 삭제하시겠습니까?"
        confirmLabel="삭제"
        cancelLabel="취소"
        onConfirm={() => {
          setConfirmOpen(false);
          onDelete(image.imageId);
        }}
        onCancel={() => setConfirmOpen(false)}
      />
    </>
  );
}
