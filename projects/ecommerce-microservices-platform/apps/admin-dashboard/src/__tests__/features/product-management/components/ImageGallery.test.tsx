import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImageGallery } from '@/features/product-management/components/ImageGallery';
import type { ProductImage } from '@/features/product-management/types/product-image';

vi.mock('@/shared/lib/overlay-styles', () => ({
  overlayStyle: {},
  dialogStyle: {},
}));

const mockImages: ProductImage[] = [
  {
    imageId: 'img-1',
    url: 'https://example.com/1.jpg',
    objectKey: 'products/1.jpg',
    sortOrder: 0,
    isPrimary: true,
    uploadedAt: '2026-01-01T00:00:00Z',
  },
  {
    imageId: 'img-2',
    url: 'https://example.com/2.jpg',
    objectKey: 'products/2.jpg',
    sortOrder: 1,
    isPrimary: false,
    uploadedAt: '2026-01-01T00:00:00Z',
  },
];

describe('ImageGallery', () => {
  const defaultProps = {
    images: mockImages,
    uploadingImages: [],
    onSetPrimary: vi.fn(),
    onDelete: vi.fn(),
    onUpdateSortOrder: vi.fn(),
    onRemoveUploading: vi.fn(),
  };

  beforeEach(() => {
    defaultProps.onSetPrimary.mockClear();
    defaultProps.onDelete.mockClear();
    defaultProps.onUpdateSortOrder.mockClear();
  });

  it('이미지 그리드를 렌더링한다', () => {
    render(<ImageGallery {...defaultProps} />);

    expect(screen.getByTestId('image-gallery')).toBeInTheDocument();
    expect(screen.getByTestId('image-item-img-1')).toBeInTheDocument();
    expect(screen.getByTestId('image-item-img-2')).toBeInTheDocument();
  });

  it('대표 이미지에 뱃지를 표시한다', () => {
    render(<ImageGallery {...defaultProps} />);
    expect(screen.getByText('대표')).toBeInTheDocument();
  });

  it('비대표 이미지에 대표 설정 버튼을 표시한다', () => {
    render(<ImageGallery {...defaultProps} />);
    const setPrimaryButtons = screen.getAllByText('대표 설정');
    expect(setPrimaryButtons).toHaveLength(1); // Only non-primary image has it
  });

  it('대표 설정 버튼 클릭 시 onSetPrimary를 호출한다', async () => {
    render(<ImageGallery {...defaultProps} />);

    await userEvent.click(screen.getByText('대표 설정'));
    expect(defaultProps.onSetPrimary).toHaveBeenCalledWith('img-2');
  });

  it('삭제 버튼 클릭 시 확인 다이얼로그를 표시한다', async () => {
    render(<ImageGallery {...defaultProps} />);

    const deleteButtons = screen.getAllByLabelText('이미지 삭제');
    await userEvent.click(deleteButtons[0]!);

    expect(screen.getByText('이 이미지를 삭제하시겠습니까?')).toBeInTheDocument();
  });

  it('삭제 확인 시 onDelete를 호출한다', async () => {
    render(<ImageGallery {...defaultProps} />);

    const deleteButtons = screen.getAllByLabelText('이미지 삭제');
    await userEvent.click(deleteButtons[0]!);

    // The confirm dialog has a "삭제" button inside it
    const dialog = screen.getByRole('dialog');
    const confirmBtn = dialog.querySelector('button:last-child')!;
    await userEvent.click(confirmBtn);

    expect(defaultProps.onDelete).toHaveBeenCalledWith('img-1');
  });

  it('이미지가 없으면 빈 메시지를 표시한다', () => {
    render(<ImageGallery {...defaultProps} images={[]} />);
    expect(screen.getByText('등록된 이미지가 없습니다.')).toBeInTheDocument();
  });

  it('업로드 중인 이미지를 표시한다', () => {
    const uploadingImages = [
      {
        id: 'upload-1',
        file: new File(['test'], 'test.jpg', { type: 'image/jpeg' }),
        progress: 50,
        status: 'uploading' as const,
        previewUrl: 'blob:test',
      },
    ];

    render(<ImageGallery {...defaultProps} uploadingImages={uploadingImages} />);
    expect(screen.getByText('50%')).toBeInTheDocument();
  });
});
