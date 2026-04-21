import { apiClient } from '@/shared/config/api';
import type {
  UploadUrlResponse,
  RegisterImageRequest,
  UpdateImageRequest,
  ProductImage,
  ProductImagesResponse,
} from '../types/product-image';

export async function requestUploadUrl(
  productId: string,
  contentType: string,
  contentLength: number,
): Promise<UploadUrlResponse> {
  return apiClient.post<UploadUrlResponse>(
    `/api/admin/products/${productId}/images/upload-url`,
    { contentType, contentLength },
  );
}

export async function uploadToPresignedUrl(
  uploadUrl: string,
  file: File,
  onProgress?: (progress: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl);
    xhr.setRequestHeader('Content-Type', file.type);

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`업로드 실패 (${xhr.status})`));
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('네트워크 오류로 업로드에 실패했습니다.'));
    });

    xhr.addEventListener('abort', () => {
      reject(new Error('업로드가 취소되었습니다.'));
    });

    xhr.send(file);
  });
}

export async function registerImage(
  productId: string,
  data: RegisterImageRequest,
): Promise<ProductImage> {
  return apiClient.post<ProductImage>(
    `/api/admin/products/${productId}/images`,
    data,
  );
}

export async function updateImage(
  productId: string,
  imageId: string,
  data: UpdateImageRequest,
): Promise<ProductImage> {
  return apiClient.patch<ProductImage>(
    `/api/admin/products/${productId}/images/${imageId}`,
    data,
  );
}

export async function deleteImage(
  productId: string,
  imageId: string,
): Promise<void> {
  return apiClient.delete<void>(
    `/api/admin/products/${productId}/images/${imageId}`,
  );
}

export async function getImages(
  productId: string,
): Promise<ProductImagesResponse> {
  return apiClient.get<ProductImagesResponse>(
    `/api/admin/products/${productId}/images`,
  );
}
