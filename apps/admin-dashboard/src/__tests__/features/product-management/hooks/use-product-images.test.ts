import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useProductImages } from '@/features/product-management/hooks/use-product-images';

const mockGetImages = vi.fn().mockResolvedValue({
  images: [
    {
      imageId: 'img-1',
      url: 'https://example.com/1.jpg',
      objectKey: 'products/1.jpg',
      sortOrder: 0,
      isPrimary: true,
      uploadedAt: '2026-01-01T00:00:00Z',
    },
  ],
});

const mockRequestUploadUrl = vi.fn().mockResolvedValue({
  uploadUrl: 'https://s3.example.com/presigned',
  objectKey: 'products/new.jpg',
  expiresAt: '2026-01-01T00:05:00Z',
});

const mockUploadToPresignedUrl = vi.fn().mockResolvedValue(undefined);
const mockRegisterImage = vi.fn().mockResolvedValue({
  imageId: 'img-2',
  url: 'https://example.com/2.jpg',
  objectKey: 'products/new.jpg',
  sortOrder: 1,
  isPrimary: false,
  uploadedAt: '2026-01-01T00:00:00Z',
});

const mockUpdateImage = vi.fn().mockResolvedValue({
  imageId: 'img-1',
  url: 'https://example.com/1.jpg',
  objectKey: 'products/1.jpg',
  sortOrder: 0,
  isPrimary: true,
});

const mockDeleteImage = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/product-management/api/product-image-api', () => ({
  getImages: (...args: unknown[]) => mockGetImages(...args),
  requestUploadUrl: (...args: unknown[]) => mockRequestUploadUrl(...args),
  uploadToPresignedUrl: (...args: unknown[]) => mockUploadToPresignedUrl(...args),
  registerImage: (...args: unknown[]) => mockRegisterImage(...args),
  updateImage: (...args: unknown[]) => mockUpdateImage(...args),
  deleteImage: (...args: unknown[]) => mockDeleteImage(...args),
}));

// Mock URL.createObjectURL / revokeObjectURL
global.URL.createObjectURL = vi.fn(() => 'blob:test');
global.URL.revokeObjectURL = vi.fn();

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useProductImages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('productId가 있으면 이미지를 로드한다', async () => {
    const { result } = renderHook(() => useProductImages('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.images).toHaveLength(1);
    });

    expect(mockGetImages).toHaveBeenCalledWith('prod-1');
  });

  it('productId가 없으면 이미지를 로드하지 않는다', () => {
    const { result } = renderHook(() => useProductImages(undefined), {
      wrapper: createWrapper(),
    });

    expect(result.current.images).toHaveLength(0);
    expect(mockGetImages).not.toHaveBeenCalled();
  });

  it('파일 타입 검증: 허용되지 않은 타입이면 에러를 설정한다', async () => {
    const { result } = renderHook(() => useProductImages('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    const invalidFile = new File(['test'], 'test.gif', { type: 'image/gif' });

    await act(async () => {
      await result.current.uploadImages([invalidFile]);
    });

    expect(result.current.error).toContain('지원하지 않는 파일 형식');
  });

  it('파일 크기 검증: 5MB 초과 시 에러를 설정한다', async () => {
    const { result } = renderHook(() => useProductImages('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    const largeFile = new File([new ArrayBuffer(6 * 1024 * 1024)], 'large.jpg', {
      type: 'image/jpeg',
    });

    await act(async () => {
      await result.current.uploadImages([largeFile]);
    });

    expect(result.current.error).toContain('5MB를 초과');
  });

  it('setPrimary가 updateImage를 호출한다', async () => {
    const { result } = renderHook(() => useProductImages('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    await act(async () => {
      await result.current.setPrimary('img-2');
    });

    expect(mockUpdateImage).toHaveBeenCalledWith('prod-1', 'img-2', { isPrimary: true });
  });

  it('removeImage가 deleteImage를 호출한다', async () => {
    const { result } = renderHook(() => useProductImages('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    await act(async () => {
      await result.current.removeImage('img-1');
    });

    expect(mockDeleteImage).toHaveBeenCalledWith('prod-1', 'img-1');
  });

  it('clearError가 에러를 초기화한다', async () => {
    const { result } = renderHook(() => useProductImages('prod-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => !result.current.isLoading);

    const invalidFile = new File(['test'], 'test.gif', { type: 'image/gif' });

    await act(async () => {
      await result.current.uploadImages([invalidFile]);
    });

    expect(result.current.error).toBeTruthy();

    act(() => {
      result.current.clearError();
    });

    expect(result.current.error).toBe('');
  });
});
