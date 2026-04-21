import type { ApiClient } from '../client';
import type {
  UserProfile,
  UpdateUserProfileRequest,
  UpdateUserProfileResponse,
  AddressListResponse,
  CreateAddressRequest,
  CreateAddressResponse,
  UpdateAddressRequest,
} from '@repo/types';

export function createUserApi(client: ApiClient) {
  return {
    getMe: () => client.get<UserProfile>('/api/users/me'),

    updateMe: (data: UpdateUserProfileRequest) =>
      client.patch<UpdateUserProfileResponse>('/api/users/me', data),

    getAddresses: () =>
      client.get<AddressListResponse>('/api/users/me/addresses'),

    createAddress: (data: CreateAddressRequest) =>
      client.post<CreateAddressResponse>('/api/users/me/addresses', data),

    updateAddress: (addressId: string, data: UpdateAddressRequest) =>
      client.patch<{ id: string }>(
        `/api/users/me/addresses/${addressId}`,
        data,
      ),

    deleteAddress: (addressId: string) =>
      client.delete<void>(`/api/users/me/addresses/${addressId}`),
  };
}
