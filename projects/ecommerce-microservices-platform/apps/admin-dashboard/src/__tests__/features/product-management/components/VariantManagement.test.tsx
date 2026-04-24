import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VariantManagement } from '@/features/product-management/components/VariantManagement';
import type { ProductVariant } from '@repo/types';

const mockHandleUpdate = vi.fn();
const mockHandleDelete = vi.fn();
const mockHandleAdd = vi.fn();
const mockSetEditing = vi.fn();
const mockSetAdding = vi.fn();

vi.mock('@/features/product-management/hooks/use-variant-management', () => ({
  useVariantManagement: () => ({
    editing: mockEditing,
    setEditing: mockSetEditing,
    adding: mockAdding,
    setAdding: mockSetAdding,
    error: mockError,
    isMutating: false,
    handleUpdate: mockHandleUpdate,
    handleDelete: mockHandleDelete,
    handleAdd: mockHandleAdd,
  }),
}));

let mockEditing: { variantId: string; optionName: string; additionalPrice: number } | null = null;
let mockAdding: { optionName: string; stock: number; additionalPrice: number } | null = null;
let mockError = '';

const variants: ProductVariant[] = [
  { id: 'v-1', optionName: '빨강', stock: 10, additionalPrice: 1000 },
  { id: 'v-2', optionName: '파랑', stock: 5, additionalPrice: 500 },
];

describe('VariantManagement', () => {
  const onChanged = vi.fn();

  beforeEach(() => {
    mockEditing = null;
    mockAdding = null;
    mockError = '';
    vi.clearAllMocks();
  });

  it('모든 옵션을 테이블에 표시한다', () => {
    render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

    expect(screen.getByText('빨강')).toBeInTheDocument();
    expect(screen.getByText('파랑')).toBeInTheDocument();
    expect(screen.getByText('+1,000원')).toBeInTheDocument();
    expect(screen.getByText('+500원')).toBeInTheDocument();
  });

  it('수정 버튼 클릭 시 setEditing을 호출한다', async () => {
    const user = userEvent.setup();
    render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

    const editButtons = screen.getAllByRole('button', { name: '수정' });
    await user.click(editButtons[0]);

    expect(mockSetEditing).toHaveBeenCalledWith({
      variantId: 'v-1',
      optionName: '빨강',
      additionalPrice: 1000,
    });
  });

  it('삭제 버튼 클릭 시 handleDelete를 호출한다', async () => {
    const user = userEvent.setup();
    render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

    const deleteButtons = screen.getAllByRole('button', { name: '삭제' });
    await user.click(deleteButtons[0]);

    expect(mockHandleDelete).toHaveBeenCalledWith('v-1');
  });

  it('옵션이 1개일 때 삭제 버튼이 표시되지 않는다', () => {
    const singleVariant: ProductVariant[] = [
      { id: 'v-1', optionName: '기본', stock: 10, additionalPrice: 0 },
    ];

    render(<VariantManagement productId="prod-1" variants={singleVariant} onChanged={onChanged} />);

    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument();
  });

  it('추가 가격이 0이면 "-"를 표시한다', () => {
    const zeroPrice: ProductVariant[] = [
      { id: 'v-1', optionName: '기본', stock: 10, additionalPrice: 0 },
    ];

    render(<VariantManagement productId="prod-1" variants={zeroPrice} onChanged={onChanged} />);

    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('+ 옵션 추가 버튼 클릭 시 setAdding을 호출한다', async () => {
    const user = userEvent.setup();
    render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

    await user.click(screen.getByRole('button', { name: '+ 옵션 추가' }));

    expect(mockSetAdding).toHaveBeenCalledWith({
      optionName: '',
      stock: 0,
      additionalPrice: 0,
    });
  });

  it('에러가 있으면 에러 메시지를 표시한다', () => {
    mockError = '옵션 삭제에 실패했습니다.';

    render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

    expect(screen.getByText('옵션 삭제에 실패했습니다.')).toBeInTheDocument();
  });

  describe('편집 모드', () => {
    it('편집 중인 옵션에 입력 필드와 저장/취소 버튼을 표시한다', () => {
      mockEditing = { variantId: 'v-1', optionName: '빨강', additionalPrice: 1000 };

      render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

      expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument();
    });

    it('저장 버튼 클릭 시 handleUpdate를 호출한다', async () => {
      const user = userEvent.setup();
      mockEditing = { variantId: 'v-1', optionName: '빨강', additionalPrice: 1000 };

      render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

      await user.click(screen.getByRole('button', { name: '저장' }));

      expect(mockHandleUpdate).toHaveBeenCalled();
    });

    it('취소 버튼 클릭 시 setEditing(null)을 호출한다', async () => {
      const user = userEvent.setup();
      mockEditing = { variantId: 'v-1', optionName: '빨강', additionalPrice: 1000 };

      render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

      await user.click(screen.getByRole('button', { name: '취소' }));

      expect(mockSetEditing).toHaveBeenCalledWith(null);
    });
  });

  describe('추가 모드', () => {
    it('추가 모드에서 입력 필드와 추가/취소 버튼을 표시한다', () => {
      mockAdding = { optionName: '', stock: 0, additionalPrice: 0 };

      render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

      expect(screen.getByRole('button', { name: '추가' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument();
    });

    it('추가 버튼 클릭 시 handleAdd를 호출한다', async () => {
      const user = userEvent.setup();
      mockAdding = { optionName: '초록', stock: 5, additionalPrice: 200 };

      render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

      await user.click(screen.getByRole('button', { name: '추가' }));

      expect(mockHandleAdd).toHaveBeenCalled();
    });

    it('추가 모드에서는 + 옵션 추가 버튼이 표시되지 않는다', () => {
      mockAdding = { optionName: '', stock: 0, additionalPrice: 0 };

      render(<VariantManagement productId="prod-1" variants={variants} onChanged={onChanged} />);

      expect(screen.queryByRole('button', { name: '+ 옵션 추가' })).not.toBeInTheDocument();
    });
  });
});
