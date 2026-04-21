import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VariantEditor, type VariantInput } from '@/features/product-management/components/VariantEditor';

describe('VariantEditor', () => {
  const defaultVariants: VariantInput[] = [
    { _key: 0, optionName: '기본', stock: 10, additionalPrice: 0 },
  ];

  it('옵션 목록을 렌더링한다', () => {
    render(<VariantEditor variants={defaultVariants} onChange={vi.fn()} initialKeyCount={1} />);

    expect(screen.getByDisplayValue('기본')).toBeInTheDocument();
  });

  it('옵션 추가 버튼을 클릭하면 onChange가 호출된다', async () => {
    const onChange = vi.fn();
    render(<VariantEditor variants={defaultVariants} onChange={onChange} initialKeyCount={1} />);

    await userEvent.click(screen.getByText('+ 옵션 추가'));
    expect(onChange).toHaveBeenCalledTimes(1);
    const newVariants = onChange.mock.calls[0][0] as VariantInput[];
    expect(newVariants).toHaveLength(2);
  });

  it('옵션이 2개 이상이면 삭제 버튼을 표시한다', () => {
    const twoVariants: VariantInput[] = [
      { _key: 0, optionName: 'A', stock: 1, additionalPrice: 0 },
      { _key: 1, optionName: 'B', stock: 2, additionalPrice: 100 },
    ];
    render(<VariantEditor variants={twoVariants} onChange={vi.fn()} initialKeyCount={2} />);

    const deleteButtons = screen.getAllByText('삭제');
    expect(deleteButtons).toHaveLength(2);
  });

  it('옵션이 1개이면 삭제 버튼을 표시하지 않는다', () => {
    render(<VariantEditor variants={defaultVariants} onChange={vi.fn()} initialKeyCount={1} />);

    expect(screen.queryByText('삭제')).not.toBeInTheDocument();
  });
});
