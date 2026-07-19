import { apiClient } from '@/shared/config/api';
import { createUserApi } from '@repo/api-client';
import type {
  AddressListResponse,
  Address,
  CreateAddressRequest,
  CreateAddressResponse,
  UpdateAddressRequest,
} from '@repo/types';

const userApi = createUserApi(apiClient);

// 내부 mock 상태: 네트워크 실패 시 폴백 용도. 외부로 export 하지 않는다.
const mockAddressState = {
  useMock: false,
  addresses: [
    {
      id: 'addr-1',
      label: '집',
      recipientName: '홍길동',
      phone: '010-1234-5678',
      zipCode: '06234',
      address1: '서울특별시 강남구 테헤란로 427',
      address2: '위워크 타워 10층',
      isDefault: true,
    },
    {
      id: 'addr-2',
      label: '회사',
      recipientName: '홍길동',
      phone: '010-1234-5678',
      zipCode: '03925',
      address1: '서울특별시 마포구 월드컵북로 21',
      address2: null,
      isDefault: false,
    },
  ] as Address[],
  idCounter: 3,
};

function pushMockAddress(data: CreateAddressRequest): CreateAddressResponse {
  const id = `addr-${mockAddressState.idCounter++}`;
  const newAddr = { id, ...data, address2: data.address2 ?? null };
  if (newAddr.isDefault) {
    mockAddressState.addresses = mockAddressState.addresses.map((a) => ({ ...a, isDefault: false }));
  }
  mockAddressState.addresses.push(newAddr);
  return { id };
}

function applyMockUpdate(addressId: string, data: UpdateAddressRequest): { id: string } {
  if (data.isDefault) {
    mockAddressState.addresses = mockAddressState.addresses.map((a) => ({ ...a, isDefault: a.id === addressId }));
  } else {
    mockAddressState.addresses = mockAddressState.addresses.map((a) =>
      a.id === addressId ? { ...a, ...data, address2: data.address2 !== undefined ? (data.address2 ?? null) : a.address2 } : a,
    );
  }
  return { id: addressId };
}

/**
 * Run the real API call; on any failure flip to the in-memory mock and serve
 * the mock result instead (network-outage fallback). Once flipped, subsequent
 * calls skip the network entirely. `mockFn` owns the mock-state mutation.
 */
async function withMockFallback<T>(
  mockFn: () => T,
  realFn: () => Promise<T>,
): Promise<T> {
  if (mockAddressState.useMock) return mockFn();
  try {
    return await realFn();
  } catch {
    mockAddressState.useMock = true;
    return mockFn();
  }
}

export async function getMyAddresses(): Promise<AddressListResponse> {
  return withMockFallback(
    () => ({ addresses: [...mockAddressState.addresses] }),
    async () => {
      const data = await userApi.getAddresses();
      data.addresses = data.addresses.map((addr) => {
        const a = addr as unknown as Record<string, unknown>;
        return {
          ...addr,
          isDefault: a.isDefault ?? a.is_default ?? false,
          recipientName: a.recipientName ?? a.recipient_name ?? '',
          zipCode: a.zipCode ?? a.zip_code ?? '',
        } as Address;
      });
      return data;
    },
  );
}

export async function createAddress(
  data: CreateAddressRequest,
): Promise<CreateAddressResponse> {
  return withMockFallback(
    () => pushMockAddress(data),
    () => userApi.createAddress(data),
  );
}

export async function updateAddress(
  addressId: string,
  data: UpdateAddressRequest,
): Promise<{ id: string }> {
  return withMockFallback(
    () => applyMockUpdate(addressId, data),
    () => userApi.updateAddress(addressId, data),
  );
}

export async function deleteAddress(addressId: string): Promise<void> {
  return withMockFallback<void>(
    () => {
      mockAddressState.addresses = mockAddressState.addresses.filter((a) => a.id !== addressId);
    },
    () => userApi.deleteAddress(addressId),
  );
}
