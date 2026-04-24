'use client';

import { useState, useCallback, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  requestUploadUrl,
  uploadToPresignedUrl,
  registerImage,
  updateImage as updateImageApi,
  deleteImage as deleteImageApi,
  getImages,
} from '../api/product-image-api';
import { productKeys } from './query-keys';
import type { ProductImage, UploadingImage } from '../types/product-image';
import { getErrorMessage } from '@repo/types/guards';

const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const MAX_IMAGE_COUNT = 10;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export interface ImageValidationError {
  file: File;
  message: string;
}

function imageKeys(productId: string) {
  return [...productKeys.all, productId, 'images'] as const;
}

export function useProductImages(productId: string | undefined) {
  const queryClient = useQueryClient();
  const [uploadingImages, setUploadingImages] = useState<UploadingImage[]>([]);
  const [error, setError] = useState<string>('');

  const {
    data,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: productId ? imageKeys(productId) : ['noop'],
    queryFn: () => (productId ? getImages(productId) : Promise.resolve({ images: [] })),
    enabled: !!productId,
  });

  const images: ProductImage[] = data?.images ?? [];

  const clearError = useCallback(() => setError(''), []);

  const validateFiles = useCallback(
    (files: File[]): { valid: File[]; errors: ImageValidationError[] } => {
      const errors: ImageValidationError[] = [];
      const valid: File[] = [];

      const currentCount = images.length + uploadingImages.filter((u) => u.status !== 'error').length;

      for (const file of files) {
        if (!ALLOWED_TYPES.includes(file.type)) {
          errors.push({ file, message: '지원하지 않는 파일 형식입니다. (JPEG, PNG, WebP만 가능)' });
          continue;
        }
        if (file.size > MAX_FILE_SIZE) {
          errors.push({ file, message: '파일 크기가 5MB를 초과합니다.' });
          continue;
        }
        if (currentCount + valid.length >= MAX_IMAGE_COUNT) {
          errors.push({ file, message: '이미지는 최대 10장까지 등록할 수 있습니다.' });
          continue;
        }
        valid.push(file);
      }

      return { valid, errors };
    },
    [images.length, uploadingImages],
  );

  const uploadImage = useCallback(
    async (file: File) => {
      if (!productId) return;

      const uploadId = `upload-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      const previewUrl = URL.createObjectURL(file);

      const uploading: UploadingImage = {
        id: uploadId,
        file,
        progress: 0,
        status: 'uploading',
        previewUrl,
      };

      setUploadingImages((prev) => [...prev, uploading]);

      try {
        // Step 1: Get presigned URL
        const { uploadUrl, objectKey } = await requestUploadUrl(
          productId,
          file.type,
          file.size,
        );

        // Step 2: Upload to presigned URL
        await uploadToPresignedUrl(uploadUrl, file, (progress) => {
          setUploadingImages((prev) =>
            prev.map((u) => (u.id === uploadId ? { ...u, progress } : u)),
          );
        });

        // Step 3: Register image
        setUploadingImages((prev) =>
          prev.map((u) => (u.id === uploadId ? { ...u, status: 'registering' as const, progress: 100 } : u)),
        );

        const sortOrder = images.length + uploadingImages.filter((u) => u.status === 'done').length;
        const isPrimary = images.length === 0 && uploadingImages.filter((u) => u.status === 'done').length === 0;

        await registerImage(productId, {
          objectKey,
          sortOrder,
          isPrimary,
        });

        // Done
        setUploadingImages((prev) =>
          prev.map((u) => (u.id === uploadId ? { ...u, status: 'done' as const } : u)),
        );

        // Remove from uploading list after a short delay and refresh
        setTimeout(() => {
          setUploadingImages((prev) => prev.filter((u) => u.id !== uploadId));
          URL.revokeObjectURL(previewUrl);
        }, 500);

        // Refresh images list
        await queryClient.invalidateQueries({ queryKey: imageKeys(productId) });
        // Also invalidate product detail to update thumbnailUrl
        await queryClient.invalidateQueries({ queryKey: productKeys.detail(productId) });
      } catch (err) {
        const message = getErrorMessage(err, '이미지 업로드에 실패했습니다.');
        setUploadingImages((prev) =>
          prev.map((u) =>
            u.id === uploadId ? { ...u, status: 'error' as const, error: message } : u,
          ),
        );
      }
    },
    [productId, images.length, uploadingImages, queryClient],
  );

  const uploadImages = useCallback(
    async (files: File[]) => {
      clearError();
      const { valid, errors } = validateFiles(files);

      if (errors.length > 0) {
        setError(errors.map((e) => `${e.file.name}: ${e.message}`).join('\n'));
      }

      // Upload valid files in parallel
      await Promise.allSettled(valid.map((file) => uploadImage(file)));
    },
    [clearError, validateFiles, uploadImage],
  );

  const removeUploadingImage = useCallback((uploadId: string) => {
    setUploadingImages((prev) => {
      const item = prev.find((u) => u.id === uploadId);
      if (item) {
        URL.revokeObjectURL(item.previewUrl);
      }
      return prev.filter((u) => u.id !== uploadId);
    });
  }, []);

  const setPrimary = useCallback(
    async (imageId: string) => {
      if (!productId) return;
      try {
        await updateImageApi(productId, imageId, { isPrimary: true });
        await queryClient.invalidateQueries({ queryKey: imageKeys(productId) });
        await queryClient.invalidateQueries({ queryKey: productKeys.detail(productId) });
      } catch (err) {
        setError(getErrorMessage(err, '대표 이미지 변경에 실패했습니다.'));
      }
    },
    [productId, queryClient],
  );

  const removeImage = useCallback(
    async (imageId: string) => {
      if (!productId) return;
      try {
        await deleteImageApi(productId, imageId);
        await queryClient.invalidateQueries({ queryKey: imageKeys(productId) });
        await queryClient.invalidateQueries({ queryKey: productKeys.detail(productId) });
      } catch (err) {
        setError(getErrorMessage(err, '이미지 삭제에 실패했습니다.'));
      }
    },
    [productId, queryClient],
  );

  const updateSortOrder = useCallback(
    async (imageId: string, sortOrder: number) => {
      if (!productId) return;
      try {
        await updateImageApi(productId, imageId, { sortOrder });
        await queryClient.invalidateQueries({ queryKey: imageKeys(productId) });
      } catch (err) {
        setError(getErrorMessage(err, '순서 변경에 실패했습니다.'));
      }
    },
    [productId, queryClient],
  );

  const refreshImages = useCallback(() => {
    if (productId) {
      return refetch();
    }
  }, [productId, refetch]);

  const isUploading = uploadingImages.some(
    (u) => u.status === 'uploading' || u.status === 'registering',
  );

  // Cleanup preview URLs on unmount
  useEffect(() => {
    return () => {
      uploadingImages.forEach((u) => URL.revokeObjectURL(u.previewUrl));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return {
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
    refreshImages,
  };
}
