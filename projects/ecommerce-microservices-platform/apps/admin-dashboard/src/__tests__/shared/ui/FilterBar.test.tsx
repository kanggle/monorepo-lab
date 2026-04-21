import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FilterBar } from '@/shared/ui';

describe('FilterBar', () => {
  it('검색 입력과 버튼을 표시한다', () => {
    const onSearchChange = vi.fn();
    render(
      <FilterBar
        searchPlaceholder="검색..."
        onSearchChange={onSearchChange}
      />,
    );

    expect(screen.getByPlaceholderText('검색...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '검색' })).toBeInTheDocument();
  });

  it('검색 폼 제출 시 onSearchChange를 호출한다', async () => {
    const user = userEvent.setup();
    const onSearchChange = vi.fn();
    render(
      <FilterBar
        searchPlaceholder="검색..."
        onSearchChange={onSearchChange}
      />,
    );

    await user.type(screen.getByPlaceholderText('검색...'), '테스트');
    await user.click(screen.getByRole('button', { name: '검색' }));

    expect(onSearchChange).toHaveBeenCalledWith('테스트');
  });

  it('상태 옵션을 표시하고 선택 시 onStatusChange를 호출한다', async () => {
    const user = userEvent.setup();
    const onStatusChange = vi.fn();
    const statusOptions = [
      { label: '판매중', value: 'ON_SALE' },
      { label: '품절', value: 'SOLD_OUT' },
    ];

    render(
      <FilterBar
        statusOptions={statusOptions}
        onStatusChange={onStatusChange}
      />,
    );

    const select = screen.getByRole('combobox');
    expect(select).toBeInTheDocument();

    await user.selectOptions(select, 'ON_SALE');
    expect(onStatusChange).toHaveBeenCalledWith('ON_SALE');
  });

  it('상태를 "전체"로 변경하면 undefined를 전달한다', async () => {
    const user = userEvent.setup();
    const onStatusChange = vi.fn();
    const statusOptions = [
      { label: '판매중', value: 'ON_SALE' },
    ];

    render(
      <FilterBar
        statusOptions={statusOptions}
        statusValue="ON_SALE"
        onStatusChange={onStatusChange}
      />,
    );

    await user.selectOptions(screen.getByRole('combobox'), '');
    expect(onStatusChange).toHaveBeenCalledWith(undefined);
  });

  it('searchValue prop이 변경되면 입력 필드가 동기화된다', () => {
    const onSearchChange = vi.fn();
    const { rerender } = render(
      <FilterBar
        searchPlaceholder="검색..."
        searchValue="초기값"
        onSearchChange={onSearchChange}
      />,
    );

    expect(screen.getByPlaceholderText('검색...')).toHaveValue('초기값');

    rerender(
      <FilterBar
        searchPlaceholder="검색..."
        searchValue="변경된값"
        onSearchChange={onSearchChange}
      />,
    );

    expect(screen.getByPlaceholderText('검색...')).toHaveValue('변경된값');
  });

  it('searchValue prop이 빈 문자열로 변경되면 입력 필드가 초기화된다', () => {
    const onSearchChange = vi.fn();
    const { rerender } = render(
      <FilterBar
        searchPlaceholder="검색..."
        searchValue="검색어"
        onSearchChange={onSearchChange}
      />,
    );

    expect(screen.getByPlaceholderText('검색...')).toHaveValue('검색어');

    rerender(
      <FilterBar
        searchPlaceholder="검색..."
        searchValue=""
        onSearchChange={onSearchChange}
      />,
    );

    expect(screen.getByPlaceholderText('검색...')).toHaveValue('');
  });

  it('onSearchChange가 없으면 검색 입력을 표시하지 않는다', () => {
    const statusOptions = [{ label: '판매중', value: 'ON_SALE' }];
    render(
      <FilterBar
        statusOptions={statusOptions}
        onStatusChange={vi.fn()}
      />,
    );

    expect(screen.queryByPlaceholderText('검색...')).not.toBeInTheDocument();
  });
});
