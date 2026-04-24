import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DataTable } from '@/shared/ui/DataTable';
import type { ColumnDef } from '@/shared/ui/DataTable';

interface TestItem extends Record<string, unknown> {
  id: string;
  name: string;
}

const columns: ColumnDef<TestItem>[] = [
  { key: 'name', header: '이름' },
];

const data: TestItem[] = [
  { id: '1', name: 'A' },
];

describe('DataTable 접근성', () => {
  it('이전 페이지 버튼에 aria-label이 있다', () => {
    render(
      <DataTable
        columns={columns}
        data={data}
        isLoading={false}
        pagination={{ page: 1, totalPages: 3, onPageChange: vi.fn() }}
      />,
    );

    expect(screen.getByLabelText('이전 페이지')).toBeInTheDocument();
  });

  it('다음 페이지 버튼에 aria-label이 있다', () => {
    render(
      <DataTable
        columns={columns}
        data={data}
        isLoading={false}
        pagination={{ page: 0, totalPages: 3, onPageChange: vi.fn() }}
      />,
    );

    expect(screen.getByLabelText('다음 페이지')).toBeInTheDocument();
  });

  it('각 페이지 번호 버튼에 aria-label이 있다', () => {
    render(
      <DataTable
        columns={columns}
        data={data}
        isLoading={false}
        pagination={{ page: 0, totalPages: 3, onPageChange: vi.fn() }}
      />,
    );

    expect(screen.getByLabelText('1 페이지')).toBeInTheDocument();
    expect(screen.getByLabelText('2 페이지')).toBeInTheDocument();
    expect(screen.getByLabelText('3 페이지')).toBeInTheDocument();
  });

  it('현재 페이지 버튼에 aria-current="page"가 설정된다', () => {
    render(
      <DataTable
        columns={columns}
        data={data}
        isLoading={false}
        pagination={{ page: 1, totalPages: 3, onPageChange: vi.fn() }}
      />,
    );

    expect(screen.getByLabelText('2 페이지')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByLabelText('1 페이지')).not.toHaveAttribute('aria-current');
  });
});
