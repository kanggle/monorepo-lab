import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddressList } from '@/features/user/ui/AddressList';
import type { Address, ApiErrorResponse } from '@repo/types';

vi.mock('@/features/user/api/address-api', () => ({
  deleteAddress: vi.fn(),
  updateAddress: vi.fn(),
}));

import { deleteAddress, updateAddress } from '@/features/user/api/address-api';
const mockDeleteAddress = vi.mocked(deleteAddress);
const mockUpdateAddress = vi.mocked(updateAddress);

const MOCK_ADDRESSES: Address[] = [
  {
    id: 'addr-1',
    label: '집',
    recipientName: '홍길동',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '서울시 강남구 테헤란로 1',
    address2: '101호',
    isDefault: true,
  },
  {
    id: 'addr-2',
    label: '회사',
    recipientName: '홍길동',
    phone: '010-9876-5432',
    zipCode: '54321',
    address1: '서울시 서초구 서초대로 2',
    address2: null,
    isDefault: false,
  },
];

const mockOnAddClick = vi.fn();
const mockOnEditClick = vi.fn();
const mockOnChanged = vi.fn();
const mockOnSetDefault = vi.fn();
const mockOnDeleted = vi.fn();

function renderAddressList(addresses = MOCK_ADDRESSES) {
  return render(
    <AddressList
      addresses={addresses}
      onAddClick={mockOnAddClick}
      onEditClick={mockOnEditClick}
      onChanged={mockOnChanged}
      onSetDefault={mockOnSetDefault}
      onDeleted={mockOnDeleted}
    />,
  );
}

describe('AddressList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('배송지 목록을 렌더링한다', () => {
    renderAddressList();

    expect(screen.getByText('집')).toBeInTheDocument();
    expect(screen.getByText('회사')).toBeInTheDocument();
    expect(screen.getByText('홍길동 / 010-****-5678')).toBeInTheDocument();
    expect(
      screen.getByText('(12345) 서울시 강남구 테헤란로 1 101호'),
    ).toBeInTheDocument();
  });

  it('기본 배송지에 "기본" 배지를 표시한다', () => {
    renderAddressList();

    expect(screen.getByText('기본 배송지')).toBeInTheDocument();
  });

  it('기본 배송지가 아닌 항목에 "기본으로 설정" 버튼을 표시한다', () => {
    renderAddressList();

    expect(screen.getByText('기본으로 설정')).toBeInTheDocument();
  });

  it('배송지 개수를 표시한다', () => {
    renderAddressList();

    expect(screen.getByText('2개 / 최대 10개')).toBeInTheDocument();
  });

  it('배송지 추가 버튼 클릭 시 onAddClick을 호출한다', async () => {
    const user = userEvent.setup();
    renderAddressList();

    await user.click(screen.getByText('배송지 추가'));

    expect(mockOnAddClick).toHaveBeenCalledTimes(1);
  });

  it('수정 버튼 클릭 시 onEditClick을 호출한다', async () => {
    const user = userEvent.setup();
    renderAddressList();

    const editButtons = screen.getAllByText('수정');
    await user.click(editButtons[0]);

    expect(mockOnEditClick).toHaveBeenCalledWith(MOCK_ADDRESSES[0]);
  });

  it('삭제 버튼 클릭 시 확인 다이얼로그를 표시한다', async () => {
    const user = userEvent.setup();
    renderAddressList();

    const deleteButtons = screen.getAllByText('삭제');
    await user.click(deleteButtons[0]);

    expect(
      screen.getByText('이 배송지를 삭제하시겠습니까?'),
    ).toBeInTheDocument();
  });

  it('삭제 확인 후 삭제 API를 호출하고 onChanged를 호출한다', async () => {
    mockDeleteAddress.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    renderAddressList();

    // 두 번째 배송지(회사)의 삭제 버튼 클릭
    await user.click(screen.getByRole('button', { name: '회사 삭제' }));

    // 확인 다이얼로그가 표시된 후, 확인 다이얼로그 내 삭제 확인 버튼 클릭
    await user.click(screen.getByRole('button', { name: '삭제 확인' }));

    await waitFor(() => {
      expect(mockDeleteAddress).toHaveBeenCalledWith('addr-2');
    });

    await waitFor(() => {
      expect(mockOnDeleted).toHaveBeenCalledWith('addr-2');
    });
  });

  it('삭제 취소 시 확인 다이얼로그를 숨긴다', async () => {
    const user = userEvent.setup();
    renderAddressList();

    const deleteButtons = screen.getAllByText('삭제');
    await user.click(deleteButtons[0]);

    expect(
      screen.getByText('이 배송지를 삭제하시겠습니까?'),
    ).toBeInTheDocument();

    await user.click(screen.getByText('취소'));

    expect(
      screen.queryByText('이 배송지를 삭제하시겠습니까?'),
    ).not.toBeInTheDocument();
  });

  it('삭제 API 에러 시 에러 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'DEFAULT_ADDRESS_CANNOT_BE_DELETED',
      message: 'Cannot delete default address',
      timestamp: new Date().toISOString(),
    };
    mockDeleteAddress.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    renderAddressList();

    // 첫 번째 배송지(기본 배송지)의 삭제 버튼 클릭 → 확인 다이얼로그 표시
    await user.click(screen.getByRole('button', { name: '집 삭제' }));

    // 확인 다이얼로그 내 삭제 확인 버튼 클릭
    await user.click(screen.getByRole('button', { name: '삭제 확인' }));

    await waitFor(() => {
      expect(
        screen.getByText('기본 배송지는 삭제할 수 없습니다.'),
      ).toBeInTheDocument();
    });
  });

  it('기본 배송지 설정 클릭 시 updateAddress를 호출한다', async () => {
    mockUpdateAddress.mockResolvedValueOnce({ id: 'addr-2' });

    const user = userEvent.setup();
    renderAddressList();

    await user.click(screen.getByText('기본으로 설정'));

    await waitFor(() => {
      expect(mockUpdateAddress).toHaveBeenCalledWith('addr-2', {
        isDefault: true,
      });
    });

    await waitFor(() => {
      expect(mockOnSetDefault).toHaveBeenCalledWith('addr-2');
    });
  });

  it('배송지가 10개일 때 추가 버튼이 비활성화된다', () => {
    const tenAddresses: Address[] = Array.from({ length: 10 }, (_, i) => ({
      id: `addr-${i}`,
      label: `배송지${i}`,
      recipientName: '홍길동',
      phone: '010-0000-0000',
      zipCode: '00000',
      address1: `주소 ${i}`,
      address2: null,
      isDefault: i === 0,
    }));

    renderAddressList(tenAddresses);

    expect(screen.getByText('배송지 추가')).toBeDisabled();
    expect(
      screen.getByText('배송지는 최대 10개까지 등록 가능합니다.'),
    ).toBeInTheDocument();
  });
});
