import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable } from '@/shared/ui/DataTable';
import type { ColumnDef } from '@/shared/ui/DataTable';

interface TestItem extends Record<string, unknown> {
  id: string;
  name: string;
  price: number;
}

const columns: ColumnDef<TestItem>[] = [
  { key: 'name', header: '이름' },
  {
    key: 'price',
    header: '가격',
    render: (item: TestItem) => `${item.price.toLocaleString()}원`,
  },
];

const testData: TestItem[] = [
  { id: '1', name: '상품 A', price: 10000 },
  { id: '2', name: '상품 B', price: 20000 },
];

describe('DataTable', () => {
  it('로딩 중일 때 스피너를 표시한다', () => {
    render(<DataTable columns={columns} data={[]} isLoading={true} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('데이터가 없을 때 빈 상태 메시지를 표시한다', () => {
    render(
      <DataTable
        columns={columns}
        data={[]}
        isLoading={false}
        emptyMessage="상품이 없습니다."
      />,
    );
    expect(screen.getByText('상품이 없습니다.')).toBeInTheDocument();
  });

  it('데이터를 테이블에 표시한다', () => {
    render(<DataTable columns={columns} data={testData} isLoading={false} />);

    expect(screen.getByText('이름')).toBeInTheDocument();
    expect(screen.getByText('가격')).toBeInTheDocument();
    expect(screen.getByText('상품 A')).toBeInTheDocument();
    expect(screen.getByText('10,000원')).toBeInTheDocument();
    expect(screen.getByText('상품 B')).toBeInTheDocument();
    expect(screen.getByText('20,000원')).toBeInTheDocument();
  });

  it('행 클릭 시 onRowClick을 호출한다', async () => {
    const onRowClick = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        onRowClick={onRowClick}
      />,
    );

    await userEvent.click(screen.getByText('상품 A'));
    expect(onRowClick).toHaveBeenCalledWith(testData[0]);
  });

  it('페이지네이션을 표시한다', () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 0, totalPages: 3, onPageChange }}
      />,
    );

    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('페이지네이션 버튼 클릭 시 onPageChange를 호출한다', async () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 0, totalPages: 3, onPageChange }}
      />,
    );

    await userEvent.click(screen.getByText('다음'));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('rowKey를 지정하면 해당 함수의 반환값이 key로 사용된다', () => {
    const rowKey = (item: TestItem) => `custom-${item.id}`;
    const { container } = render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        rowKey={rowKey}
      />,
    );

    const rows = container.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
  });

  it('rowKey가 없으면 데이터의 id 필드를 key로 사용한다', () => {
    const { container } = render(
      <DataTable columns={columns} data={testData} isLoading={false} />,
    );

    const rows = container.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
  });

  it('id가 없는 데이터도 올바르게 렌더링된다', () => {
    const dataWithoutId = [
      { name: '상품 X', price: 5000 } as unknown as TestItem,
      { name: '상품 Y', price: 7000 } as unknown as TestItem,
    ];
    render(<DataTable columns={columns} data={dataWithoutId} isLoading={false} />);

    expect(screen.getByText('상품 X')).toBeInTheDocument();
    expect(screen.getByText('상품 Y')).toBeInTheDocument();
  });

  it('첫 페이지에서 이전 버튼이 비활성화된다', () => {
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 0, totalPages: 3, onPageChange: vi.fn() }}
      />,
    );

    expect(screen.getByText('이전')).toBeDisabled();
  });

  it('10페이지 이상일 때 말줄임(...)이 표시된다', () => {
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 0, totalPages: 20, onPageChange: vi.fn() }}
      />,
    );

    const ellipsis = screen.getByText('...');
    expect(ellipsis).toBeInTheDocument();
    expect(ellipsis.tagName).toBe('SPAN');
  });

  it('10페이지 이상일 때 첫/마지막/현재 페이지 ±2가 표시된다', () => {
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 10, totalPages: 20, onPageChange: vi.fn() }}
      />,
    );

    // 첫 페이지
    expect(screen.getByText('1')).toBeInTheDocument();
    // 마지막 페이지
    expect(screen.getByText('20')).toBeInTheDocument();
    // 현재 페이지 ±2 (9, 10, 11, 12, 13)
    expect(screen.getByText('9')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
    expect(screen.getByText('11')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('13')).toBeInTheDocument();
  });

  it('10페이지 미만일 때 말줄임 없이 전체 페이지가 표시된다', () => {
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 0, totalPages: 5, onPageChange: vi.fn() }}
      />,
    );

    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.queryByText('...')).not.toBeInTheDocument();
  });

  it('말줄임(...)은 클릭할 수 없다', () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={testData}
        isLoading={false}
        pagination={{ page: 5, totalPages: 20, onPageChange }}
      />,
    );

    const ellipses = screen.getAllByText('...');
    ellipses.forEach((el) => {
      expect(el.tagName).toBe('SPAN');
      expect(el.closest('button')).toBeNull();
    });
  });
});
