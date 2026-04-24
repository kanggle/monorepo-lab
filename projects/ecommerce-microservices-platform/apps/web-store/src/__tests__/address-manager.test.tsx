import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddressManager } from '@/features/user/ui/AddressManager';
import type { Address } from '@repo/types';

const mockRefetch = vi.fn();
const mockInvalidate = vi.fn();

const mockUseAddresses = vi.fn();

vi.mock('@/entities/user', () => ({
  useAddresses: () => mockUseAddresses(),
}));

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div role="alert">
      {message}
      <button onClick={onRetry}>다시 시도</button>
    </div>
  ),
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

vi.mock('@/shared/ui/Skeleton', () => ({
  Skeleton: () => <div data-testid="skeleton" />,
}));

vi.mock('@/features/user/ui/AddressList', () => ({
  AddressList: ({ addresses, onAddClick }: { addresses: Address[]; onAddClick: () => void }) => (
    <div data-testid="address-list">
      <span>{addresses.length}개 배송지</span>
      <button onClick={onAddClick}>배송지 추가</button>
    </div>
  ),
}));

vi.mock('@/features/user/ui/AddressForm', () => ({
  AddressForm: ({ onSaved, onCancel }: { onSaved: () => void; onCancel: () => void }) => (
    <div data-testid="address-form">
      <button onClick={onSaved}>저장</button>
      <button onClick={onCancel}>취소</button>
    </div>
  ),
}));

describe('AddressManager', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 스켈레톤을 표시한다', () => {
    mockUseAddresses.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    render(<AddressManager />);

    expect(screen.getByText('배송지 관리')).toBeInTheDocument();
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('에러 시 에러 메시지를 표시한다', () => {
    mockUseAddresses.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    render(<AddressManager />);

    expect(screen.getByRole('alert')).toHaveTextContent('배송지 목록을 불러오는데 실패했습니다.');
  });

  it('에러 시 다시 시도 버튼 클릭으로 refetch를 호출한다', async () => {
    mockUseAddresses.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    const user = userEvent.setup();
    render(<AddressManager />);

    await user.click(screen.getByText('다시 시도'));
    expect(mockRefetch).toHaveBeenCalled();
  });

  it('배송지가 없으면 빈 상태와 추가 버튼을 표시한다', () => {
    mockUseAddresses.mockReturnValue({
      data: { addresses: [] },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    render(<AddressManager />);

    expect(screen.getByTestId('empty-state')).toHaveTextContent('등록된 배송지가 없습니다.');
    expect(screen.getByText('첫 배송지 추가하기')).toBeInTheDocument();
  });

  it('배송지가 있으면 AddressList를 표시한다', () => {
    mockUseAddresses.mockReturnValue({
      data: {
        addresses: [
          { id: 'addr-1', label: '집', recipientName: '홍길동', phone: '010-1234-5678', zipCode: '12345', address1: '서울시', address2: null, isDefault: true },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    render(<AddressManager />);

    expect(screen.getByTestId('address-list')).toBeInTheDocument();
    expect(screen.getByText('1개 배송지')).toBeInTheDocument();
  });

  it('추가 버튼을 클릭하면 AddressForm을 표시한다', async () => {
    mockUseAddresses.mockReturnValue({
      data: {
        addresses: [
          { id: 'addr-1', label: '집', recipientName: '홍길동', phone: '010-1234-5678', zipCode: '12345', address1: '서울시', address2: null, isDefault: true },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    const user = userEvent.setup();
    render(<AddressManager />);

    await user.click(screen.getByText('배송지 추가'));

    expect(screen.getByTestId('address-form')).toBeInTheDocument();
  });

  it('폼에서 취소를 클릭하면 목록으로 돌아간다', async () => {
    mockUseAddresses.mockReturnValue({
      data: {
        addresses: [
          { id: 'addr-1', label: '집', recipientName: '홍길동', phone: '010-1234-5678', zipCode: '12345', address1: '서울시', address2: null, isDefault: true },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    const user = userEvent.setup();
    render(<AddressManager />);

    await user.click(screen.getByText('배송지 추가'));
    expect(screen.getByTestId('address-form')).toBeInTheDocument();

    await user.click(screen.getByText('취소'));
    expect(screen.getByTestId('address-list')).toBeInTheDocument();
  });

  it('폼에서 저장 완료 시 목록으로 돌아가고 invalidate를 호출한다', async () => {
    mockUseAddresses.mockReturnValue({
      data: {
        addresses: [
          { id: 'addr-1', label: '집', recipientName: '홍길동', phone: '010-1234-5678', zipCode: '12345', address1: '서울시', address2: null, isDefault: true },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: mockRefetch,
      invalidate: mockInvalidate,
    });

    const user = userEvent.setup();
    render(<AddressManager />);

    await user.click(screen.getByText('배송지 추가'));
    await user.click(screen.getByText('저장'));

    expect(screen.getByTestId('address-list')).toBeInTheDocument();
    expect(mockInvalidate).toHaveBeenCalled();
  });
});
