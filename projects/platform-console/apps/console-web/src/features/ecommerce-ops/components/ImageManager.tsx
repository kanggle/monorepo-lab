'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useImages,
  useUpdateImage,
  useDeleteImage,
} from '../hooks/use-ecommerce-images';
import { IMAGE_MAX_PER_PRODUCT, type ImageItem } from '../api/image-types';
import { ConfirmDialog } from './ConfirmDialog';
import { ImageUploadField } from './ImageUploadField';

/**
 * Product-image management section (TASK-PC-FE-082 — § 2.4.10 #10/#13/#14),
 * embedded in the product detail alongside the variant editor + stock adjust.
 * Lists the product's images (thumbnail · primary badge · sortOrder), hosts the
 * presigned upload control ({@link ImageUploadField}, #11/#12), and offers
 * set-primary (#13) + delete (#14, confirm-gated). The producer recomputes the
 * product `thumbnailUrl` from the primary image, so every mutation invalidates
 * the product detail query too (handled in the hooks).
 *
 * Resilience (§ 2.5): a failed image list degrades ONLY this section (an inline
 * notice) — the surrounding product detail (header / variants / stock) stays
 * intact.
 */
export interface ImageManagerProps {
  productId: string;
}

export function ImageManager({ productId }: ImageManagerProps) {
  const imagesQ = useImages(productId);
  const update = useUpdateImage();
  const del = useDeleteImage();

  const [toDelete, setToDelete] = useState<ImageItem | null>(null);
  const [delError, setDelError] = useState<string | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);

  const images = imagesQ.data?.images ?? [];
  const atLimit = images.length >= IMAGE_MAX_PER_PRODUCT;

  function errMsg(e: unknown, fallback: string): string {
    const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
    return messageForCode(code, fallback);
  }

  function setPrimary(img: ImageItem) {
    if (img.isPrimary) return;
    setRowError(null);
    update.mutate(
      { productId, imageId: img.imageId, body: { isPrimary: true } },
      {
        onError: (e) =>
          setRowError(errMsg(e, '대표 이미지를 변경하지 못했습니다.')),
      },
    );
  }

  function confirmDelete() {
    if (!toDelete) return;
    setDelError(null);
    del.mutate(
      { productId, imageId: toDelete.imageId },
      {
        onSuccess: () => setToDelete(null),
        onError: (e) => setDelError(errMsg(e, '이미지를 삭제하지 못했습니다.')),
      },
    );
  }

  return (
    <section className="mb-8" data-testid="image-manager">
      <h3 className="mb-2 text-base font-medium text-foreground">이미지</h3>

      {imagesQ.isLoading ? (
        <p className="text-sm text-muted-foreground" data-testid="image-loading">
          이미지를 불러오는 중…
        </p>
      ) : imagesQ.isError ? (
        <p
          role="status"
          className="rounded-md border border-border bg-muted px-3 py-4 text-sm text-muted-foreground"
          data-testid="image-degraded"
        >
          이미지 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.
        </p>
      ) : images.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="image-empty"
        >
          등록된 이미지가 없습니다. 아래에서 이미지를 추가하세요.
        </p>
      ) : (
        <ul
          className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4"
          data-testid="image-list"
        >
          {images.map((img, i) => (
            <li
              key={img.imageId}
              className="rounded-md border border-border p-2"
              data-testid={`image-row-${i}`}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={img.url}
                alt={`상품 이미지 ${i + 1}`}
                className="mb-2 aspect-square w-full rounded object-cover"
                data-testid={`image-thumb-${i}`}
              />
              <div className="flex items-center justify-between gap-2 text-xs">
                <span className="text-muted-foreground">
                  순서 {img.sortOrder}
                </span>
                {img.isPrimary ? (
                  <span
                    className="rounded bg-primary/10 px-1.5 py-0.5 font-medium text-primary"
                    data-testid={`image-primary-badge-${i}`}
                  >
                    대표
                  </span>
                ) : (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setPrimary(img)}
                    disabled={update.isPending}
                    data-testid={`image-set-primary-${i}`}
                  >
                    대표로
                  </Button>
                )}
              </div>
              <div className="mt-1 flex justify-end">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    setDelError(null);
                    setToDelete(img);
                  }}
                  data-testid={`image-delete-${i}`}
                >
                  삭제
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {rowError && (
        <p
          role="alert"
          className="mb-2 text-xs text-destructive"
          data-testid="image-row-error"
        >
          {rowError}
        </p>
      )}

      {!imagesQ.isError && (
        <ImageUploadField
          productId={productId}
          nextSortOrder={images.length}
          disabled={atLimit}
          onUploaded={() => imagesQ.refetch()}
        />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="이미지를 삭제할까요?"
        description="선택한 이미지를 삭제합니다. 이 작업은 되돌릴 수 없습니다."
        confirmLabel="삭제"
        tone="destructive"
        pending={del.isPending}
        errorMessage={delError}
        onConfirm={confirmDelete}
        onCancel={() => {
          setToDelete(null);
          setDelError(null);
        }}
      />
    </section>
  );
}
