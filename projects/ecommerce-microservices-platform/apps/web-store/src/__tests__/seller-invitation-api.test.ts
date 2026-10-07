import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockAcceptSellerInvitation } = vi.hoisted(() => ({
  mockAcceptSellerInvitation: vi.fn(),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createProductApi: vi.fn(() => ({
    acceptSellerInvitation: mockAcceptSellerInvitation,
  })),
}));

import { acceptSellerInvitation } from '@/features/seller-invitation/api/seller-invitation-api';

describe('seller-invitation-api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('토큰을 전달해 초대를 수락한다', async () => {
    const response = { sellerId: 'seller-1', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2026-10-08T00:00:00Z' };
    mockAcceptSellerInvitation.mockResolvedValueOnce(response);

    const result = await acceptSellerInvitation({ token: 'tok-1' });

    expect(mockAcceptSellerInvitation).toHaveBeenCalledWith({ token: 'tok-1' });
    expect(result).toEqual(response);
  });

  it('API 에러를 그대로 전파한다', async () => {
    const error = { code: 'SELLER_INVITATION_NOT_FOUND', message: 'Not found' };
    mockAcceptSellerInvitation.mockRejectedValueOnce(error);

    await expect(acceptSellerInvitation({ token: 'bad' })).rejects.toEqual(error);
  });
});
