'use client';

import { useRef, useCallback, useState } from 'react';
import { useProductImages } from '../hooks/use-product-images';
import { ImageItem } from './ImageItem';
import { formStyles } from '@/shared/lib/form-styles';
import type { UploadingImage } from '../types/product-image';

interface Props {
  productId?: string;
  thumbnailUrl?: string;
}

const MAX_IMAGE_COUNT = 10;
const ACCEPT = 'image/jpeg,image/png,image/webp';

const styles = {
  title: {
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#374151',
    marginBottom: '8px',
  } as const,
  noProductMessage: {
    padding: '20px',
    textAlign: 'center',
    color: '#6b7280',
    fontSize: '0.875rem',
    backgroundColor: '#f9fafb',
    borderRadius: '8px',
    border: '1px solid #e5e7eb',
  } as const,
  dropZone: {
    border: '2px dashed #d1d5db',
    borderRadius: '8px',
    minHeight: 280,
    padding: 8,
    transition: 'border-color 0.2s, background-color 0.2s',
    backgroundColor: '#fafafa',
  } as const,
  dropZoneActive: {
    border: '2px dashed #1A1A2E',
    borderRadius: '8px',
    minHeight: 280,
    padding: 8,
    backgroundColor: '#f0f0ff',
  } as const,
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, 1fr)',
    gap: 8,
  } as const,
  addButton: {
    border: '2px dashed #d1d5db',
    borderRadius: '8px',
    aspectRatio: '1',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    backgroundColor: '#fafafa',
    transition: 'border-color 0.2s',
    color: '#9ca3af',
    fontSize: '0.75rem',
    gap: 4,
  } as const,
  addIcon: {
    fontSize: '1.5rem',
    color: '#9ca3af',
  } as const,
  emptyZone: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 260,
    cursor: 'pointer',
    gap: 4,
  } as const,
  emptyIcon: {
    fontSize: '2rem',
    color: '#9ca3af',
  } as const,
  emptyText: {
    fontSize: '0.8125rem',
    color: '#374151',
  } as const,
  subText: {
    fontSize: '0.75rem',
    color: '#9ca3af',
  } as const,
  fallbackImg: {
    width: '100%',
    aspectRatio: '1',
    objectFit: 'cover',
    borderRadius: 6,
    opacity: 0.4,
  } as const,
  uploadingCard: {
    position: 'relative',
    borderRadius: '6px',
    overflow: 'hidden',
    backgroundColor: '#f9fafb',
    border: '1px solid #e5e7eb',
  } as const,
  uploadingImgWrapper: {
    width: '100%',
    aspectRatio: '1',
    overflow: 'hidden',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  } as const,
  uploadingImg: {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
    opacity: 0.5,
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
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.3)',
    borderRadius: 2,
    marginTop: 8,
    overflow: 'hidden',
  } as const,
  progressFill: {
    height: '100%',
    backgroundColor: '#fff',
    borderRadius: 2,
    transition: 'width 0.2s',
  } as const,
  hiddenInput: { display: 'none' } as const,
};

function UploadingCard({ uploading, onRemove }: { uploading: UploadingImage; onRemove: () => void }) {
  return (
    <div style={styles.uploadingCard}>
      <div style={styles.uploadingImgWrapper as React.CSSProperties}>
        <img src={uploading.previewUrl} alt="" style={styles.uploadingImg as React.CSSProperties} />
        {uploading.status === 'error' ? (
          <div style={{ ...styles.uploadingOverlay, backgroundColor: 'rgba(220,38,38,0.5)' } as React.CSSProperties}>
            <span style={{ fontSize: '0.7rem', marginBottom: 4 }}>{uploading.error}</span>
            <button type="button" onClick={onRemove} style={{ padding: '2px 8px', fontSize: '0.7rem', border: '1px solid rgba(255,255,255,0.5)', borderRadius: 4, backgroundColor: 'transparent', cursor: 'pointer', color: '#fff' }}>
              닫기
            </button>
          </div>
        ) : (
          <div style={styles.uploadingOverlay as React.CSSProperties}>
            <span>{uploading.status === 'registering' ? '등록 중...' : `${uploading.progress}%`}</span>
            <div style={styles.progressBar}>
              <div style={{ ...styles.progressFill, width: `${uploading.progress}%` }} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function ProductImageSection({ productId, thumbnailUrl }: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const {
    images,
    uploadingImages,
    isLoading,
    isUploading,
    error,
    clearError,
    uploadImages,
    removeUploadingImage,
    setPrimary,
    removeImage,
    updateSortOrder,
  } = useProductImages(productId);

  const handleFiles = useCallback((fileList: FileList | null) => {
    if (!fileList || isUploading) return;
    const files = Array.from(fileList);
    if (files.length > 0) uploadImages(files);
  }, [isUploading, uploadImages]);

  const openFilePicker = useCallback(() => {
    if (!isUploading && fileInputRef.current) fileInputRef.current.click();
  }, [isUploading]);

  if (!productId) {
    return (
      <div>
        <p style={styles.title}>상품 이미지</p>
        <p style={styles.noProductMessage}>상품을 먼저 저장한 후 이미지를 추가할 수 있습니다.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div>
        <p style={styles.title}>상품 이미지</p>
        <p style={{ color: '#9ca3af', fontSize: '0.875rem' }}>이미지 로딩 중...</p>
      </div>
    );
  }

  const sorted = [...images].sort((a, b) => a.sortOrder - b.sortOrder);
  const activeUploads = uploadingImages.filter((u) => u.status !== 'done');
  const currentCount = images.length + activeUploads.length;
  const isFull = currentCount >= MAX_IMAGE_COUNT;
  const hasContent = sorted.length > 0 || activeUploads.length > 0;

  const dropZoneStyle = isDragging ? styles.dropZoneActive : styles.dropZone;

  const fileInput = (
    <input
      ref={fileInputRef}
      type="file"
      accept={ACCEPT}
      multiple
      style={styles.hiddenInput}
      onChange={(e) => { handleFiles(e.target.files); if (fileInputRef.current) fileInputRef.current.value = ''; }}
    />
  );

  function handleMoveUp(index: number) {
    if (index <= 0) return;
    const cur = sorted[index], prev = sorted[index - 1];
    if (cur && prev) { updateSortOrder(cur.imageId, prev.sortOrder); updateSortOrder(prev.imageId, cur.sortOrder); }
  }
  function handleMoveDown(index: number) {
    if (index >= sorted.length - 1) return;
    const cur = sorted[index], next = sorted[index + 1];
    if (cur && next) { updateSortOrder(cur.imageId, next.sortOrder); updateSortOrder(next.imageId, cur.sortOrder); }
  }

  return (
    <div>
      <p style={styles.title}>상품 이미지 ({currentCount}/{MAX_IMAGE_COUNT})</p>

      {error && (
        <div style={{ ...formStyles.errorAlert, marginBottom: 8, whiteSpace: 'pre-line', fontSize: '0.75rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <span>{error}</span>
            <button type="button" onClick={clearError} style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#dc2626', fontSize: '0.875rem', padding: '0 0 0 8px' }} aria-label="에러 닫기">x</button>
          </div>
        </div>
      )}

      <div
        style={dropZoneStyle}
        onDragOver={(e) => { e.preventDefault(); e.stopPropagation(); if (!isUploading) setIsDragging(true); }}
        onDragLeave={(e) => { e.preventDefault(); e.stopPropagation(); setIsDragging(false); }}
        onDrop={(e) => { e.preventDefault(); e.stopPropagation(); setIsDragging(false); if (!isUploading) handleFiles(e.dataTransfer.files); }}
      >
        {hasContent ? (
          <div style={styles.grid}>
            {sorted.map((image, index) => (
              <ImageItem
                key={image.imageId}
                image={image}
                onSetPrimary={setPrimary}
                onDelete={removeImage}
                onMoveUp={() => handleMoveUp(index)}
                onMoveDown={() => handleMoveDown(index)}
                isFirst={index === 0}
                isLast={index === sorted.length - 1}
              />
            ))}

            {activeUploads.map((u) => (
              <UploadingCard key={u.id} uploading={u} onRemove={() => removeUploadingImage(u.id)} />
            ))}

            {!isFull && (
              <div style={styles.addButton as React.CSSProperties} onClick={openFilePicker} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openFilePicker(); } }}>
                <span style={styles.addIcon}>+</span>
                <span>추가</span>
              </div>
            )}
          </div>
        ) : thumbnailUrl ? (
          <div onClick={openFilePicker} style={{ cursor: 'pointer' }}>
            <img src={thumbnailUrl} alt="현재 이미지" style={styles.fallbackImg as React.CSSProperties} />
            <div style={{ textAlign: 'center', padding: '8px 0' }}>
              <p style={styles.emptyText}>클릭하여 이미지 추가</p>
              <p style={styles.subText}>JPEG, PNG, WebP / 최대 5MB</p>
            </div>
          </div>
        ) : (
          <div style={styles.emptyZone as React.CSSProperties} onClick={openFilePicker} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openFilePicker(); } }}>
            <span style={styles.emptyIcon}>+</span>
            <p style={styles.emptyText}>이미지를 드래그하거나 클릭하여 선택</p>
            <p style={styles.subText}>JPEG, PNG, WebP / 최대 5MB</p>
          </div>
        )}
      </div>
      {fileInput}
    </div>
  );
}
