import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProductImageSection } from '@/features/product-management/components/ProductImageSection';

vi.mock('@/shared/lib/overlay-styles', () => ({
  overlayStyle: {},
  dialogStyle: {},
}));

const mockGetImages = vi.fn().mockResolvedValue({ images: [] });

vi.mock('@/features/product-management/api/product-image-api', () => ({
  requestUploadUrl: vi.fn(),
  uploadToPresignedUrl: vi.fn(),
  registerImage: vi.fn(),
  updateImage: vi.fn(),
  deleteImage: vi.fn(),
  getImages: (...args: unknown[]) => mockGetImages(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('ProductImageSection', () => {
  beforeEach(() => {
    mockGetImages.mockClear();
  });

  it('productId가 없으면 안내 메시지를 표시한다', () => {
    render(<ProductImageSection />, { wrapper: createWrapper() });

    expect(
      screen.getByText('상품을 먼저 저장한 후 이미지를 추가할 수 있습니다.'),
    ).toBeInTheDocument();
  });

  it('productId가 있으면 업로드 영역을 표시한다', async () => {
    render(<ProductImageSection productId="prod-1" />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText(/이미지를 드래그하거나 클릭하여 선택/),
    ).toBeInTheDocument();
  });

  it('섹션 제목이 상품 이미지이다', () => {
    render(<ProductImageSection />, { wrapper: createWrapper() });
    expect(screen.getByText('상품 이미지')).toBeInTheDocument();
  });
});
