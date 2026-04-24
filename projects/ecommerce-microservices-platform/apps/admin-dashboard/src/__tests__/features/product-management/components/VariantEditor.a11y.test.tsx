import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  VariantEditor,
  type VariantInput,
} from '@/features/product-management/components/VariantEditor';

describe('VariantEditor 접근성', () => {
  const variants: VariantInput[] = [
    { _key: 0, optionName: '기본', stock: 10, additionalPrice: 500 },
    { _key: 1, optionName: '대형', stock: 5, additionalPrice: 1000 },
  ];

  it('모든 label이 htmlFor로 input과 연결되어 있다', () => {
    render(
      <VariantEditor
        variants={variants}
        onChange={vi.fn()}
        initialKeyCount={2}
      />,
    );

    const labels = screen.getAllByText('옵션명');
    labels.forEach((label) => {
      const htmlFor = label.getAttribute('for');
      expect(htmlFor).toBeTruthy();
      const input = document.getElementById(htmlFor!);
      expect(input).toBeInTheDocument();
      expect(input?.tagName).toBe('INPUT');
    });

    const stockLabels = screen.getAllByText('재고');
    stockLabels.forEach((label) => {
      const htmlFor = label.getAttribute('for');
      expect(htmlFor).toBeTruthy();
      expect(document.getElementById(htmlFor!)).toBeInTheDocument();
    });

    const priceLabels = screen.getAllByText('추가 가격');
    priceLabels.forEach((label) => {
      const htmlFor = label.getAttribute('for');
      expect(htmlFor).toBeTruthy();
      expect(document.getElementById(htmlFor!)).toBeInTheDocument();
    });
  });

  it('각 input의 id가 고유하다', () => {
    render(
      <VariantEditor
        variants={variants}
        onChange={vi.fn()}
        initialKeyCount={2}
      />,
    );

    const allInputs = screen.getAllByRole('textbox').concat(screen.getAllByRole('spinbutton'));
    const ids = allInputs.map((input) => input.id).filter(Boolean);
    const uniqueIds = new Set(ids);
    expect(uniqueIds.size).toBe(ids.length);
  });

  it('삭제 버튼에 aria-label이 존재한다', () => {
    render(
      <VariantEditor
        variants={variants}
        onChange={vi.fn()}
        initialKeyCount={2}
      />,
    );

    const deleteButtons = screen.getAllByRole('button', { name: /삭제/ });
    deleteButtons.forEach((btn) => {
      expect(btn).toHaveAttribute('aria-label');
    });
  });
});
