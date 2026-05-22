'use client';

import Image from 'next/image';
import type { ProductImage, UploadingImage } from '../types/product-image';
import { ImageItem } from './ImageItem';

interface Props {
  images: ProductImage[];
  uploadingImages: UploadingImage[];
  onSetPrimary: (imageId: string) => void;
  onDelete: (imageId: string) => void;
  onUpdateSortOrder: (imageId: string, sortOrder: number) => void;
  onRemoveUploading: (uploadId: string) => void;
}

const styles = {
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
    gap: '12px',
  } as const,
  empty: {
    padding: '40px 20px',
    textAlign: 'center',
    color: '#9ca3af',
    fontSize: '0.875rem',
  } as const,
  uploadingCard: {
    position: 'relative',
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    overflow: 'hidden',
    backgroundColor: '#f9fafb',
  } as const,
  uploadingImageWrapper: {
    // `position: relative` required so the child `<Image fill>` fills this box.
    position: 'relative',
    width: '100%',
    aspectRatio: '1',
    overflow: 'hidden',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  } as const,
  uploadingOverlay: {
    position: 'absolute',
    inset: 0,
    backgroundColor: 'rgba(0,0,0,0.4)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#fff',
    fontSize: '0.75rem',
  } as const,
  progressBar: {
    width: '80%',
    height: '4px',
    backgroundColor: 'rgba(255,255,255,0.3)',
    borderRadius: '2px',
    marginTop: '8px',
    overflow: 'hidden',
  } as const,
  progressFill: {
    height: '100%',
    backgroundColor: '#fff',
    borderRadius: '2px',
    transition: 'width 0.2s',
  } as const,
  uploadingImg: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    opacity: 0.5,
  } as const,
  errorCard: {
    position: 'relative',
    border: '1px solid #fecaca',
    borderRadius: '8px',
    overflow: 'hidden',
    backgroundColor: '#fef2f2',
  } as const,
  errorOverlay: {
    position: 'absolute',
    inset: 0,
    backgroundColor: 'rgba(220,38,38,0.1)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '8px',
  } as const,
  errorText: {
    fontSize: '0.7rem',
    color: '#dc2626',
    textAlign: 'center',
    marginBottom: '4px',
  } as const,
  retryBtn: {
    padding: '2px 8px',
    fontSize: '0.7rem',
    border: '1px solid #fecaca',
    borderRadius: '4px',
    backgroundColor: '#fff',
    cursor: 'pointer',
    color: '#dc2626',
  } as const,
};

export function ImageGallery({
  images,
  uploadingImages,
  onSetPrimary,
  onDelete,
  onUpdateSortOrder,
  onRemoveUploading,
}: Props) {
  const sortedImages = [...images].sort((a, b) => a.sortOrder - b.sortOrder);
  const activeUploads = uploadingImages.filter((u) => u.status !== 'done');

  if (sortedImages.length === 0 && activeUploads.length === 0) {
    return <p style={styles.empty}>등록된 이미지가 없습니다.</p>;
  }

  function handleMoveUp(index: number) {
    if (index <= 0) return;
    const current = sortedImages[index];
    const prev = sortedImages[index - 1];
    if (current && prev) {
      onUpdateSortOrder(current.imageId, prev.sortOrder);
      onUpdateSortOrder(prev.imageId, current.sortOrder);
    }
  }

  function handleMoveDown(index: number) {
    if (index >= sortedImages.length - 1) return;
    const current = sortedImages[index];
    const next = sortedImages[index + 1];
    if (current && next) {
      onUpdateSortOrder(current.imageId, next.sortOrder);
      onUpdateSortOrder(next.imageId, current.sortOrder);
    }
  }

  return (
    <div style={styles.grid} data-testid="image-gallery">
      {sortedImages.map((image, index) => (
        <ImageItem
          key={image.imageId}
          image={image}
          onSetPrimary={onSetPrimary}
          onDelete={onDelete}
          onMoveUp={() => handleMoveUp(index)}
          onMoveDown={() => handleMoveDown(index)}
          isFirst={index === 0}
          isLast={index === sortedImages.length - 1}
        />
      ))}

      {activeUploads.map((uploading) => (
        <div
          key={uploading.id}
          style={uploading.status === 'error' ? styles.errorCard : styles.uploadingCard}
          data-testid={`uploading-${uploading.id}`}
        >
          <div style={styles.uploadingImageWrapper as React.CSSProperties}>
            <Image
              src={uploading.previewUrl}
              alt={uploading.file.name}
              fill
              // Local FileReader data: URL preview — Next.js optimizer is bypassed.
              unoptimized
              sizes="200px"
              style={styles.uploadingImg as React.CSSProperties}
            />
            {uploading.status === 'error' ? (
              <div style={styles.errorOverlay as React.CSSProperties}>
                <p style={styles.errorText as React.CSSProperties}>{uploading.error}</p>
                <button
                  type="button"
                  style={styles.retryBtn}
                  onClick={() => onRemoveUploading(uploading.id)}
                >
                  닫기
                </button>
              </div>
            ) : (
              <div style={styles.uploadingOverlay as React.CSSProperties}>
                <span>
                  {uploading.status === 'registering' ? '등록 중...' : `${uploading.progress}%`}
                </span>
                <div style={styles.progressBar}>
                  <div
                    style={{
                      ...styles.progressFill,
                      width: `${uploading.progress}%`,
                    }}
                  />
                </div>
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
