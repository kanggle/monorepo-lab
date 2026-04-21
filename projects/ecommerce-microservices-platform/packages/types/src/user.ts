// User domain types based on specs/contracts/http/user-api.md

export interface UserProfile {
  userId: string;
  email: string;
  name: string;
  nickname: string | null;
  phone: string | null;
  profileImageUrl: string | null;
  status: 'ACTIVE';
  createdAt: string;
  updatedAt: string;
}

export interface UpdateUserProfileRequest {
  nickname?: string;
  phone?: string;
  profileImageUrl?: string;
}

export interface UpdateUserProfileResponse {
  userId: string;
  email: string;
  name: string;
  nickname: string | null;
  phone: string | null;
  profileImageUrl: string | null;
  status: 'ACTIVE';
  updatedAt: string;
}

// Address types based on specs/contracts/http/user-api.md

export interface Address {
  id: string;
  label: string;
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2: string | null;
  isDefault: boolean;
}

export interface AddressListResponse {
  addresses: Address[];
}

export interface CreateAddressRequest {
  label: string;
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string | null;
  isDefault: boolean;
}

export interface CreateAddressResponse {
  id: string;
}

export interface UpdateAddressRequest {
  label?: string;
  recipientName?: string;
  phone?: string;
  zipCode?: string;
  address1?: string;
  address2?: string | null;
  isDefault?: boolean;
}

// Admin user types based on specs/contracts/http/user-api.md

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';

export interface AdminUserSummary {
  userId: string;
  email: string;
  name: string;
  nickname: string | null;
  status: UserStatus;
  createdAt: string;
}

export interface AdminUserDetail {
  userId: string;
  email: string;
  name: string;
  nickname: string | null;
  phone: string | null;
  profileImageUrl: string | null;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserListParams {
  status?: UserStatus;
  email?: string;
  page?: number;
  size?: number;
}
