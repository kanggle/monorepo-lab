import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  VariantEditor,
  type VariantInput,
} from '@/features/product-management/components/VariantEditor';

describe('VariantEditor', () => {
  const defaultVariants: VariantInput[] = [
    { _key: 0, optionName: '기본', stock: 10, additionalPrice: 0 },
  ];

  describe('초기 렌더링', () => {
    it('variants 데이터가 올바르게 렌더링된다', () => {
      render(
        <VariantEditor
          variants={defaultVariants}
          onChange={vi.fn()}
          initialKeyCount={1}
        />,
      );

      expect(screen.getByDisplayValue('기본')).toBeInTheDocument();
      expect(screen.getByDisplayValue('10')).toBeInTheDocument();
      expect(screen.getByDisplayValue('0')).toBeInTheDocument();
    });

    it('여러 옵션이 모두 렌더링된다', () => {
      const multipleVariants: VariantInput[] = [
        { _key: 0, optionName: 'A', stock: 5, additionalPrice: 100 },
        { _key: 1, optionName: 'B', stock: 3, additionalPrice: 200 },
      ];
      render(
        <VariantEditor
          variants={multipleVariants}
          onChange={vi.fn()}
          initialKeyCount={2}
        />,
      );

      expect(screen.getByDisplayValue('A')).toBeInTheDocument();
      expect(screen.getByDisplayValue('B')).toBeInTheDocument();
    });

    it('빈 variants 배열이면 옵션 행이 렌더링되지 않는다', () => {
      render(
        <VariantEditor
          variants={[]}
          onChange={vi.fn()}
          initialKeyCount={0}
        />,
      );

      expect(screen.getByText('옵션')).toBeInTheDocument();
      expect(screen.getByText('+ 옵션 추가')).toBeInTheDocument();
      expect(screen.queryByLabelText('옵션명')).not.toBeInTheDocument();
    });
  });

  describe('옵션 추가', () => {
    it('옵션 추가 버튼 클릭 시 새 옵션이 포함된 배열로 onChange가 호출된다', async () => {
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={defaultVariants}
          onChange={onChange}
          initialKeyCount={1}
        />,
      );

      await userEvent.click(screen.getByText('+ 옵션 추가'));

      expect(onChange).toHaveBeenCalledTimes(1);
      const newVariants = onChange.mock.calls[0][0] as VariantInput[];
      expect(newVariants).toHaveLength(2);
      expect(newVariants[0]).toEqual(defaultVariants[0]);
      expect(newVariants[1]).toEqual({
        _key: 1,
        optionName: '',
        stock: 0,
        additionalPrice: 0,
      });
    });

    it('빈 배열에서 옵션 추가 시 1개 옵션이 포함된 배열로 onChange가 호출된다', async () => {
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={[]}
          onChange={onChange}
          initialKeyCount={0}
        />,
      );

      await userEvent.click(screen.getByText('+ 옵션 추가'));

      expect(onChange).toHaveBeenCalledTimes(1);
      const newVariants = onChange.mock.calls[0][0] as VariantInput[];
      expect(newVariants).toHaveLength(1);
      expect(newVariants[0]).toEqual({
        _key: 0,
        optionName: '',
        stock: 0,
        additionalPrice: 0,
      });
    });
  });

  describe('옵션 제거', () => {
    it('삭제 버튼 클릭 시 해당 옵션이 제거된 배열로 onChange가 호출된다', async () => {
      const twoVariants: VariantInput[] = [
        { _key: 0, optionName: 'A', stock: 1, additionalPrice: 0 },
        { _key: 1, optionName: 'B', stock: 2, additionalPrice: 100 },
      ];
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={twoVariants}
          onChange={onChange}
          initialKeyCount={2}
        />,
      );

      const deleteButtons = screen.getAllByText('삭제');
      await userEvent.click(deleteButtons[0]);

      expect(onChange).toHaveBeenCalledTimes(1);
      const remaining = onChange.mock.calls[0][0] as VariantInput[];
      expect(remaining).toHaveLength(1);
      expect(remaining[0].optionName).toBe('B');
    });

    it('옵션이 1개이면 삭제 버튼이 표시되지 않는다', () => {
      render(
        <VariantEditor
          variants={defaultVariants}
          onChange={vi.fn()}
          initialKeyCount={1}
        />,
      );

      expect(screen.queryByText('삭제')).not.toBeInTheDocument();
    });

    it('옵션이 2개 이상이면 각 옵션마다 삭제 버튼이 표시된다', () => {
      const threeVariants: VariantInput[] = [
        { _key: 0, optionName: 'A', stock: 1, additionalPrice: 0 },
        { _key: 1, optionName: 'B', stock: 2, additionalPrice: 100 },
        { _key: 2, optionName: 'C', stock: 3, additionalPrice: 200 },
      ];
      render(
        <VariantEditor
          variants={threeVariants}
          onChange={vi.fn()}
          initialKeyCount={3}
        />,
      );

      expect(screen.getAllByText('삭제')).toHaveLength(3);
    });
  });

  describe('옵션 필드 업데이트', () => {
    it('옵션명 변경 시 onChange가 올바른 값으로 호출된다', () => {
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={defaultVariants}
          onChange={onChange}
          initialKeyCount={1}
        />,
      );

      const nameInput = screen.getByDisplayValue('기본');
      fireEvent.change(nameInput, { target: { value: '대' } });

      expect(onChange).toHaveBeenCalledTimes(1);
      const updatedVariants = onChange.mock.calls[0][0] as VariantInput[];
      expect(updatedVariants[0].optionName).toBe('대');
      expect(updatedVariants[0]._key).toBe(0);
    });

    it('재고 변경 시 onChange가 올바른 값으로 호출된다', () => {
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={defaultVariants}
          onChange={onChange}
          initialKeyCount={1}
        />,
      );

      const stockInput = screen.getByDisplayValue('10');
      fireEvent.change(stockInput, { target: { value: '50' } });

      expect(onChange).toHaveBeenCalledTimes(1);
      const updatedVariants = onChange.mock.calls[0][0] as VariantInput[];
      expect(updatedVariants[0].stock).toBe(50);
    });

    it('추가 가격 변경 시 onChange가 올바른 값으로 호출된다', () => {
      const variants: VariantInput[] = [
        { _key: 0, optionName: '기본', stock: 10, additionalPrice: 500 },
      ];
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={variants}
          onChange={onChange}
          initialKeyCount={1}
        />,
      );

      const priceInput = screen.getByDisplayValue('500');
      fireEvent.change(priceInput, { target: { value: '1000' } });

      expect(onChange).toHaveBeenCalledTimes(1);
      const updatedVariants = onChange.mock.calls[0][0] as VariantInput[];
      expect(updatedVariants[0].additionalPrice).toBe(1000);
    });

    it('여러 옵션 중 특정 옵션만 업데이트된다', () => {
      const twoVariants: VariantInput[] = [
        { _key: 0, optionName: 'A', stock: 1, additionalPrice: 0 },
        { _key: 1, optionName: 'B', stock: 2, additionalPrice: 100 },
      ];
      const onChange = vi.fn();
      render(
        <VariantEditor
          variants={twoVariants}
          onChange={onChange}
          initialKeyCount={2}
        />,
      );

      const nameInputB = screen.getByDisplayValue('B');
      fireEvent.change(nameInputB, { target: { value: 'C' } });

      expect(onChange).toHaveBeenCalledTimes(1);
      const updatedVariants = onChange.mock.calls[0][0] as VariantInput[];
      expect(updatedVariants[0].optionName).toBe('A');
      expect(updatedVariants[1].optionName).toBe('C');
    });
  });
});
