'use client';

import { useRef, useState, useCallback } from 'react';
import type { ProductImage } from '../types/product-image';

interface Props {
  onFilesSelected: (files: File[]) => void;
  disabled?: boolean;
  currentCount: number;
  maxCount: number;
  existingImages?: ProductImage[];
  fallbackThumbnailUrl?: string;
}

const ACCEPT = 'image/jpeg,image/png,image/webp';

const styles = {
  wrapper: {
    position: 'relative',
    borderRadius: '8px',
    overflow: 'hidden',
  } as const,
  bgGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(60px, 1fr))',
    gap: 4,
    padding: 4,
  } as const,
  bgImage: {
    width: '100%',
    aspectRatio: '1',
    objectFit: 'cover',
    borderRadius: 4,
    opacity: 0.45,
    display: 'block',
  } as const,
  overlay: {
    position: 'absolute',
    inset: 0,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.5)',
  } as const,
  dropZone: {
    border: '2px dashed #d1d5db',
    borderRadius: '8px',
    aspectRatio: '1',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center',
    cursor: 'pointer',
    transition: 'border-color 0.2s, background-color 0.2s',
    backgroundColor: '#fafafa',
  } as const,
  dropZoneActive: {
    border: '2px dashed #1A1A2E',
    borderRadius: '8px',
    aspectRatio: '1',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center',
    cursor: 'pointer',
    backgroundColor: '#f0f0ff',
  } as const,
  dropZoneDisabled: {
    border: '2px dashed #e5e7eb',
    borderRadius: '8px',
    aspectRatio: '1',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center',
    cursor: 'not-allowed',
    backgroundColor: '#f9fafb',
    opacity: 0.6,
  } as const,
  dropZoneWithBg: {
    border: '2px dashed #d1d5db',
    borderRadius: '8px',
    aspectRatio: '1',
    textAlign: 'center',
    cursor: 'pointer',
    position: 'relative',
    overflow: 'hidden',
  } as const,
  dropZoneWithBgActive: {
    border: '2px dashed #1A1A2E',
    borderRadius: '8px',
    aspectRatio: '1',
    textAlign: 'center',
    cursor: 'pointer',
    position: 'relative',
    overflow: 'hidden',
  } as const,
  icon: {
    fontSize: '1.5rem',
    marginBottom: '4px',
    color: '#6b7280',
  } as const,
  mainText: {
    fontSize: '0.8125rem',
    color: '#374151',
    marginBottom: '2px',
  } as const,
  subText: {
    fontSize: '0.75rem',
    color: '#9ca3af',
  } as const,
  selectBtn: {
    display: 'inline-block',
    marginTop: '8px',
    padding: '6px 16px',
    fontSize: '0.75rem',
    border: '1px solid #d1d5db',
    borderRadius: '6px',
    backgroundColor: '#fff',
    cursor: 'pointer',
    color: '#374151',
    fontWeight: 500,
  } as const,
  hiddenInput: {
    display: 'none',
  } as const,
};

export function ImageUploader({
  onFilesSelected,
  disabled = false,
  currentCount,
  maxCount,
  existingImages = [],
  fallbackThumbnailUrl,
}: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);

  const handleFiles = useCallback(
    (fileList: FileList | null) => {
      if (!fileList || disabled) return;
      const files = Array.from(fileList);
      if (files.length > 0) {
        onFilesSelected(files);
      }
    },
    [disabled, onFilesSelected],
  );

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!disabled) {
        setIsDragging(true);
      }
    },
    [disabled],
  );

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);
      if (!disabled) {
        handleFiles(e.dataTransfer.files);
      }
    },
    [disabled, handleFiles],
  );

  const handleClick = useCallback(() => {
    if (!disabled && fileInputRef.current) {
      fileInputRef.current.click();
    }
  }, [disabled]);

  const isFull = currentCount >= maxCount;
  const isDisabled = disabled || isFull;
  const hasImages = existingImages.length > 0;
  const hasFallback = !hasImages && !!fallbackThumbnailUrl;

  const overlayContent = (
    <>
      <div style={styles.icon}>+</div>
      <p style={styles.mainText}>
        {isFull
          ? '이미지를 더 이상 추가할 수 없습니다'
          : '이미지를 드래그하거나 클릭하여 선택'}
      </p>
      <p style={styles.subText}>
        JPEG, PNG, WebP / 최대 5MB / {currentCount}/{maxCount}장
      </p>
      {!isDisabled && (
        <button type="button" style={styles.selectBtn} tabIndex={-1}>
          파일 선택
        </button>
      )}
    </>
  );

  const commonProps = {
    onDragOver: handleDragOver,
    onDragLeave: handleDragLeave,
    onDrop: handleDrop,
    onClick: handleClick,
    role: 'button' as const,
    tabIndex: 0,
    onKeyDown: (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleClick();
      }
    },
    'aria-label': '이미지 업로드 영역',
    'data-testid': 'image-uploader',
  };

  const fileInput = (
    <input
      ref={fileInputRef}
      type="file"
      accept={ACCEPT}
      multiple
      style={styles.hiddenInput}
      onChange={(e) => {
        handleFiles(e.target.files);
        if (fileInputRef.current) {
          fileInputRef.current.value = '';
        }
      }}
      data-testid="file-input"
    />
  );

  if ((hasImages || hasFallback) && !isDisabled) {
    const zoneStyle = isDragging ? styles.dropZoneWithBgActive : styles.dropZoneWithBg;
    return (
      <div style={zoneStyle} {...commonProps}>
        {hasImages ? (
          <div style={styles.bgGrid}>
            {existingImages.map((img) => (
              <img key={img.imageId} src={img.url} alt="" style={styles.bgImage} />
            ))}
          </div>
        ) : (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 8 }}>
            <img
              src={fallbackThumbnailUrl}
              alt=""
              style={{ ...styles.bgImage, width: 120, height: 120, borderRadius: 8 }}
            />
          </div>
        )}
        <div style={styles.overlay}>
          {overlayContent}
        </div>
        {fileInput}
      </div>
    );
  }

  const dropZoneStyle = isDisabled
    ? styles.dropZoneDisabled
    : isDragging
      ? styles.dropZoneActive
      : styles.dropZone;

  return (
    <div style={dropZoneStyle} {...commonProps}>
      {overlayContent}
      {fileInput}
    </div>
  );
}
