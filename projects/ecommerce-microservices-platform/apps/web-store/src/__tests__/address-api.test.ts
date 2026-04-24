import { describe, it, expect, vi, beforeEach } from 'vitest';

const {
  mockGetAddresses,
  mockCreateAddress,
  mockUpdateAddress,
  mockDeleteAddress,
} = vi.hoisted(() => ({
  mockGetAddresses: vi.fn(),
  mockCreateAddress: vi.fn(),
  mockUpdateAddress: vi.fn(),
  mockDeleteAddress: vi.fn(),
}));

vi.mock('@repo/api-client', () => ({
  createUserApi: () => ({
    getAddresses: mockGetAddresses,
    createAddress: mockCreateAddress,
    updateAddress: mockUpdateAddress,
    deleteAddress: mockDeleteAddress,
  }),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

import { getMyAddresses } from '@/entities/user/api/address-api';
import {
  createAddress,
  updateAddress,
  deleteAddress,
} from '@/features/user/api/address-api';

describe('address-api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getMyAddresses는 userApi.getAddresses를 호출한다', async () => {
    const mockResponse = {
      addresses: [
        {
          id: 'addr-1',
          label: '집',
          recipientName: '홍길동',
          phone: '010-1234-5678',
          zipCode: '12345',
          address1: '서울시',
          address2: null,
          isDefault: true,
        },
      ],
    };
    mockGetAddresses.mockResolvedValueOnce(mockResponse);

    const result = await getMyAddresses();

    expect(mockGetAddresses).toHaveBeenCalledTimes(1);
    expect(result).toEqual(mockResponse);
  });

  it('createAddress는 userApi.createAddress를 호출한다', async () => {
    const request = {
      label: '집',
      recipientName: '홍길동',
      phone: '010-1234-5678',
      zipCode: '12345',
      address1: '서울시',
      address2: null,
      isDefault: false,
    };
    mockCreateAddress.mockResolvedValueOnce({ id: 'addr-new' });

    const result = await createAddress(request);

    expect(mockCreateAddress).toHaveBeenCalledWith(request);
    expect(result).toEqual({ id: 'addr-new' });
  });

  it('updateAddress는 userApi.updateAddress를 호출한다', async () => {
    const data = { label: '본가' };
    mockUpdateAddress.mockResolvedValueOnce({ id: 'addr-1' });

    const result = await updateAddress('addr-1', data);

    expect(mockUpdateAddress).toHaveBeenCalledWith('addr-1', data);
    expect(result).toEqual({ id: 'addr-1' });
  });

  it('deleteAddress는 userApi.deleteAddress를 호출한다', async () => {
    mockDeleteAddress.mockResolvedValueOnce(undefined);

    await deleteAddress('addr-1');

    expect(mockDeleteAddress).toHaveBeenCalledWith('addr-1');
  });
});
