import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchBar } from '@/features/search/ui/SearchBar';

const mockPush = vi.fn();
// `URLSearchParams.get` returns `string | null` — preserve the union here
// so individual tests can override with a string via `mockReturnValue(...)`.
const mockGet = vi.fn<() => string | null>(() => null);

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => ({ get: mockGet }),
}));

describe('SearchBar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('검색 입력 필드를 표시한다', () => {
    render(<SearchBar />);

    expect(screen.getByPlaceholderText('상품을 검색하세요')).toBeInTheDocument();
  });

  it('검색 버튼을 표시한다', () => {
    render(<SearchBar />);

    expect(screen.getByText('검색')).toBeInTheDocument();
  });

  it('검색어 입력 후 폼 제출 시 올바른 URL로 이동한다', async () => {
    const user = userEvent.setup();
    render(<SearchBar />);

    const input = screen.getByPlaceholderText('상품을 검색하세요');
    await user.type(input, '노트북');
    await user.click(screen.getByText('검색'));

    expect(mockPush).toHaveBeenCalledWith('/products?q=%EB%85%B8%ED%8A%B8%EB%B6%81');
  });

  it('빈 검색어로 제출하면 전체 상품 목록(/products)으로 이동한다', async () => {
    const user = userEvent.setup();
    render(<SearchBar />);

    await user.click(screen.getByText('검색'));

    expect(mockPush).toHaveBeenCalledWith('/products');
  });

  it('공백만 있는 검색어로 제출하면 전체 상품 목록(/products)으로 이동한다', async () => {
    const user = userEvent.setup();
    render(<SearchBar />);

    const input = screen.getByPlaceholderText('상품을 검색하세요');
    await user.type(input, '   ');
    await user.click(screen.getByText('검색'));

    expect(mockPush).toHaveBeenCalledWith('/products');
  });

  it('URL에 기존 검색어가 있으면 입력 필드에 표시한다', () => {
    mockGet.mockReturnValue('기존검색어');
    render(<SearchBar />);

    const input = screen.getByPlaceholderText('상품을 검색하세요') as HTMLInputElement;
    expect(input.value).toBe('기존검색어');
  });
});
