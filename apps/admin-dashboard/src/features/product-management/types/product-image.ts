export interface ProductImage {
  imageId: string;
  url: string;
  objectKey: string;
  sortOrder: number;
  isPrimary: boolean;
  uploadedAt?: string;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  objectKey: string;
  expiresAt: string;
}

export interface RegisterImageRequest {
  objectKey: string;
  sortOrder: number;
  isPrimary: boolean;
}

export interface UpdateImageRequest {
  sortOrder?: number;
  isPrimary?: boolean;
}

export interface ProductImagesResponse {
  images: ProductImage[];
}

export interface UploadingImage {
  id: string;
  file: File;
  progress: number;
  status: 'uploading' | 'registering' | 'done' | 'error';
  error?: string;
  previewUrl: string;
}
