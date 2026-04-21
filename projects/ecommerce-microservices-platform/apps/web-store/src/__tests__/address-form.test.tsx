import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddressForm } from '@/features/user/ui/AddressForm';
import type { Address, ApiErrorResponse } from '@repo/types';

// AddressSearch는 daum postcode API를 사용하여 테스트 환경에서 동작하지 않으므로
// onSelect 콜백을 트리거할 수 있는 스텁으로 대체한다.
vi.mock('@/shared/ui/AddressSearch', () => ({
  AddressSearch: ({ onSelect }: { onSelect: (d: { zipCode: string; address1: string }) => void }) => (
    <button
      type="button"
      data-testid="address-search-stub"
      onClick={() => onSelect({ zipCode: '12345', address1: '서울시 강남구' })}
    >
      주소 검색
    </button>
  ),
}));

vi.mock('@/features/user/api/address-api', () => ({
  createAddress: vi.fn(),
  updateAddress: vi.fn(),
}));

import { createAddress, updateAddress } from '@/features/user/api/address-api';
const mockCreateAddress = vi.mocked(createAddress);
const mockUpdateAddress = vi.mocked(updateAddress);

const MOCK_ADDRESS: Address = {
  id: 'addr-1',
  label: '집',
  recipientName: '홍길동',
  phone: '010-1234-5678',
  zipCode: '12345',
  address1: '서울시 강남구 테헤란로 1',
  address2: '101호',
  isDefault: true,
};

const mockOnSaved = vi.fn();
const mockOnCancel = vi.fn();

function renderAddForm() {
  return render(<AddressForm onSaved={mockOnSaved} onCancel={mockOnCancel} />);
}

function renderEditForm(address = MOCK_ADDRESS) {
  return render(
    <AddressForm
      address={address}
      onSaved={mockOnSaved}
      onCancel={mockOnCancel}
    />,
  );
}

describe('AddressForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('추가 모드', () => {
    it('빈 필드로 폼을 렌더링한다', () => {
      renderAddForm();

      expect(screen.getByText('새 배송지 추가')).toBeInTheDocument();
      expect(screen.getByLabelText('배송지명')).toHaveValue('');
      expect(screen.getByLabelText('수령인')).toHaveValue('');
      expect(screen.getByLabelText('연락처')).toHaveValue('');
      expect(screen.getByPlaceholderText('우편번호')).toHaveValue('');
      expect(screen.getByPlaceholderText('주소 검색을 눌러주세요')).toHaveValue('');
      expect(screen.getByPlaceholderText('상세주소 (선택)')).toHaveValue('');
    });

    it('기본 배송지 체크박스가 표시된다', () => {
      renderAddForm();

      expect(screen.getByLabelText('기본 배송지로 설정')).toBeInTheDocument();
    });

    it('필수 필드가 비어있으면 에러를 표시한다', async () => {
      const user = userEvent.setup();
      renderAddForm();

      await user.click(screen.getByRole('button', { name: '추가' }));

      expect(screen.getByText('배송지명을 입력해주세요.')).toBeInTheDocument();
      expect(screen.getByText('수령인을 입력해주세요.')).toBeInTheDocument();
      expect(screen.getByText('연락처를 입력해주세요.')).toBeInTheDocument();
      expect(screen.getByText('주소를 입력해주세요.')).toBeInTheDocument();
      expect(mockCreateAddress).not.toHaveBeenCalled();
    });

    it('유효하지 않은 연락처를 입력하면 에러를 표시한다', async () => {
      const user = userEvent.setup();
      renderAddForm();

      await user.type(screen.getByLabelText('배송지명'), '집');
      await user.type(screen.getByLabelText('수령인'), '홍길동');
      await user.type(screen.getByLabelText('연락처'), 'abc');
      // 주소 검색 스텁으로 zipCode/address1 세팅
      await user.click(screen.getByTestId('address-search-stub'));
      await user.click(screen.getByRole('button', { name: '추가' }));

      // 인라인 검증과 필드 에러 두 곳에서 동일 메시지가 렌더링될 수 있다
      const phoneErrors = screen.getAllByText('올바른 휴대폰 번호를 입력해주세요. (예: 010-1234-5678)');
      expect(phoneErrors.length).toBeGreaterThanOrEqual(1);
    });

    it('유효한 입력 시 createAddress를 호출하고 onSaved를 호출한다', async () => {
      mockCreateAddress.mockResolvedValueOnce({ id: 'addr-new' });

      const user = userEvent.setup();
      renderAddForm();

      await user.type(screen.getByLabelText('배송지명'), '집');
      await user.type(screen.getByLabelText('수령인'), '홍길동');
      await user.type(screen.getByLabelText('연락처'), '010-1234-5678');
      // 주소 검색 스텁으로 zipCode='12345', address1='서울시 강남구' 세팅
      await user.click(screen.getByTestId('address-search-stub'));
      await user.type(screen.getByPlaceholderText('상세주소 (선택)'), '101호');
      await user.click(screen.getByRole('button', { name: '추가' }));

      await waitFor(() => {
        expect(mockCreateAddress).toHaveBeenCalledWith({
          label: '집',
          recipientName: '홍길동',
          phone: '010-1234-5678',
          zipCode: '12345',
          address1: '서울시 강남구',
          address2: '101호',
          isDefault: false,
        });
      });

      await waitFor(() => {
        expect(mockOnSaved).toHaveBeenCalledTimes(1);
      });
    });

    it('ADDRESS_LIMIT_EXCEEDED 에러 시 적절한 메시지를 표시한다', async () => {
      const apiError: ApiErrorResponse = {
        code: 'ADDRESS_LIMIT_EXCEEDED',
        message: 'Max addresses reached',
        timestamp: new Date().toISOString(),
      };
      mockCreateAddress.mockRejectedValueOnce(apiError);

      const user = userEvent.setup();
      renderAddForm();

      await user.type(screen.getByLabelText('배송지명'), '집');
      await user.type(screen.getByLabelText('수령인'), '홍길동');
      await user.type(screen.getByLabelText('연락처'), '010-1234-5678');
      await user.click(screen.getByTestId('address-search-stub'));
      await user.click(screen.getByRole('button', { name: '추가' }));

      await waitFor(() => {
        expect(
          screen.getByText('배송지는 최대 10개까지 등록 가능합니다.'),
        ).toBeInTheDocument();
      });
    });

    it('제출 중 중복 클릭을 방지한다', async () => {
      let resolveCreate: (value: unknown) => void;
      mockCreateAddress.mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveCreate = resolve;
          }),
      );

      const user = userEvent.setup();
      renderAddForm();

      await user.type(screen.getByLabelText('배송지명'), '집');
      await user.type(screen.getByLabelText('수령인'), '홍길동');
      await user.type(screen.getByLabelText('연락처'), '010-1234-5678');
      await user.click(screen.getByTestId('address-search-stub'));
      await user.click(screen.getByRole('button', { name: '추가' }));

      expect(
        screen.getByRole('button', { name: '저장 중...' }),
      ).toBeDisabled();
      expect(mockCreateAddress).toHaveBeenCalledTimes(1);

      resolveCreate!({ id: 'addr-new' });

      await waitFor(() => {
        expect(mockOnSaved).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('수정 모드', () => {
    it('기존 값으로 폼을 렌더링한다', () => {
      renderEditForm();

      expect(screen.getByText('배송지 수정')).toBeInTheDocument();
      expect(screen.getByLabelText('배송지명')).toHaveValue('집');
      expect(screen.getByLabelText('수령인')).toHaveValue('홍길동');
      expect(screen.getByLabelText('연락처')).toHaveValue('010-1234-5678');
      expect(screen.getByPlaceholderText('우편번호')).toHaveValue('12345');
      expect(screen.getByPlaceholderText('주소 검색을 눌러주세요')).toHaveValue(
        '서울시 강남구 테헤란로 1',
      );
      expect(screen.getByPlaceholderText('상세주소 (선택)')).toHaveValue('101호');
    });

    it('수정 모드에서 기본 배송지 체크박스가 표시되지 않는다', () => {
      renderEditForm();

      expect(
        screen.queryByLabelText('기본 배송지로 설정'),
      ).not.toBeInTheDocument();
    });

    it('수정 완료 시 updateAddress를 호출하고 onSaved를 호출한다', async () => {
      mockUpdateAddress.mockResolvedValueOnce({ id: 'addr-1' });

      const user = userEvent.setup();
      renderEditForm();

      await user.clear(screen.getByLabelText('배송지명'));
      await user.type(screen.getByLabelText('배송지명'), '본가');
      await user.click(screen.getByRole('button', { name: '수정 완료' }));

      await waitFor(() => {
        expect(mockUpdateAddress).toHaveBeenCalledWith('addr-1', {
          label: '본가',
          recipientName: '홍길동',
          phone: '010-1234-5678',
          zipCode: '12345',
          address1: '서울시 강남구 테헤란로 1',
          address2: '101호',
          isDefault: true,
        });
      });

      await waitFor(() => {
        expect(mockOnSaved).toHaveBeenCalledTimes(1);
      });
    });

    it('ADDRESS_NOT_FOUND 에러 시 적절한 메시지를 표시한다', async () => {
      const apiError: ApiErrorResponse = {
        code: 'ADDRESS_NOT_FOUND',
        message: 'Address not found',
        timestamp: new Date().toISOString(),
      };
      mockUpdateAddress.mockRejectedValueOnce(apiError);

      const user = userEvent.setup();
      renderEditForm();

      await user.clear(screen.getByLabelText('배송지명'));
      await user.type(screen.getByLabelText('배송지명'), '본가');
      await user.click(screen.getByRole('button', { name: '수정 완료' }));

      await waitFor(() => {
        expect(
          screen.getByText('이미 삭제된 배송지입니다.'),
        ).toBeInTheDocument();
      });
    });
  });

  it('취소 버튼 클릭 시 onCancel을 호출한다', async () => {
    const user = userEvent.setup();
    renderAddForm();

    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(mockOnCancel).toHaveBeenCalledTimes(1);
  });

  it('알 수 없는 에러 시 기본 메시지를 표시한다', async () => {
    mockCreateAddress.mockRejectedValueOnce(new Error('unknown'));

    const user = userEvent.setup();
    renderAddForm();

    await user.type(screen.getByLabelText('배송지명'), '집');
    await user.type(screen.getByLabelText('수령인'), '홍길동');
    await user.type(screen.getByLabelText('연락처'), '010-1234-5678');
    await user.click(screen.getByTestId('address-search-stub'));
    await user.click(screen.getByRole('button', { name: '추가' }));

    await waitFor(() => {
      expect(
        screen.getByText('배송지 저장에 실패했습니다.'),
      ).toBeInTheDocument();
    });
  });
});
