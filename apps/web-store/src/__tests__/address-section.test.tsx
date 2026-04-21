import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddressSection } from '@/features/checkout/ui/AddressSection';
import type { Address, ShippingAddress } from '@repo/types';

vi.mock('@/shared/ui/Skeleton', () => ({
  Skeleton: () => <div data-testid="skeleton" />,
}));

vi.mock('@/shared/ui/AddressSearch', () => ({
  AddressSearch: ({ onSelect }: { onSelect: (data: { zipCode: string; address1: string }) => void }) => (
    <button data-testid="address-search" onClick={() => onSelect({ zipCode: '12345', address1: '서울시 강남구' })}>
      주소 검색
    </button>
  ),
}));

const emptyAddress: ShippingAddress = {
  recipient: '',
  phone: '',
  zipCode: '',
  address1: '',
  address2: null,
};

const savedAddresses: Address[] = [
  {
    id: 'addr-1',
    label: '집',
    recipientName: '홍길동',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '서울시 강남구',
    address2: '101호',
    isDefault: true,
  },
  {
    id: 'addr-2',
    label: '회사',
    recipientName: '홍길동',
    phone: '010-9876-5432',
    zipCode: '54321',
    address1: '서울시 서초구',
    address2: null,
    isDefault: false,
  },
];

const defaultProps = {
  addressLoading: false,
  savedAddresses: [],
  selectedAddressId: '',
  address: emptyAddress,
  phoneValid: true,
  isNewAddress: false,
  onAddressSelect: vi.fn(),
  onAddressSearchSelect: vi.fn(),
  onFieldChange: vi.fn(),
};

describe('AddressSection', () => {
  it('배송지 정보 제목을 표시한다', () => {
    render(<AddressSection {...defaultProps} />);

    expect(screen.getByText('배송지 정보')).toBeInTheDocument();
  });

  it('로딩 중일 때 스켈레톤을 표시한다', () => {
    render(<AddressSection {...defaultProps} addressLoading={true} />);

    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('저장된 배송지가 있으면 라디오 버튼 목록을 표시한다', () => {
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={savedAddresses}
        selectedAddressId="addr-1"
      />,
    );

    expect(screen.getByText('집')).toBeInTheDocument();
    expect(screen.getByText('회사')).toBeInTheDocument();
    expect(screen.getByText('새 배송지 직접 입력')).toBeInTheDocument();
  });

  it('기본 배송지에 기본 뱃지를 표시한다', () => {
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={savedAddresses}
        selectedAddressId="addr-1"
      />,
    );

    expect(screen.getByText('기본')).toBeInTheDocument();
  });

  it('배송지 선택 시 onAddressSelect를 호출한다', async () => {
    const onAddressSelect = vi.fn();
    const user = userEvent.setup();
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={savedAddresses}
        selectedAddressId="addr-1"
        onAddressSelect={onAddressSelect}
      />,
    );

    const radios = screen.getAllByRole('radio');
    await user.click(radios[1]);

    expect(onAddressSelect).toHaveBeenCalledWith('addr-2');
  });

  it('새 배송지 선택 시 onAddressSelect를 new로 호출한다', async () => {
    const onAddressSelect = vi.fn();
    const user = userEvent.setup();
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={savedAddresses}
        selectedAddressId="addr-1"
        onAddressSelect={onAddressSelect}
      />,
    );

    const radios = screen.getAllByRole('radio');
    await user.click(radios[2]); // 'new' radio

    expect(onAddressSelect).toHaveBeenCalledWith('new');
  });

  it('저장된 배송지가 없으면 배송지 입력 폼을 표시한다', () => {
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={[]}
        isNewAddress={false}
      />,
    );

    expect(screen.getByLabelText('수령인')).toBeInTheDocument();
    expect(screen.getByLabelText('전화번호')).toBeInTheDocument();
  });

  it('새 배송지 모드일 때 배송지 입력 폼을 표시한다', () => {
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={savedAddresses}
        selectedAddressId="new"
        isNewAddress={true}
      />,
    );

    expect(screen.getByLabelText('수령인')).toBeInTheDocument();
  });

  it('전화번호가 유효하지 않으면 에러 메시지를 표시한다', () => {
    const invalidAddress: ShippingAddress = {
      ...emptyAddress,
      phone: '123',
    };
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={[]}
        address={invalidAddress}
        phoneValid={false}
      />,
    );

    expect(screen.getByText(/올바른 휴대폰 번호를 입력해주세요/)).toBeInTheDocument();
  });

  it('수령인 입력 시 onFieldChange를 호출한다', async () => {
    const onFieldChange = vi.fn();
    const user = userEvent.setup();
    render(
      <AddressSection
        {...defaultProps}
        savedAddresses={[]}
        onFieldChange={onFieldChange}
      />,
    );

    await user.type(screen.getByLabelText('수령인'), '김');

    expect(onFieldChange).toHaveBeenCalledWith('recipient', '김');
  });
});
