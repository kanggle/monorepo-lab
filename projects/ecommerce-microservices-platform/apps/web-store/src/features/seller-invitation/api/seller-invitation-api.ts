import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';
import type {
  AcceptSellerInvitationRequest,
  AcceptSellerInvitationResponse,
} from '@repo/types';

const productApi = createProductApi(apiClient);

/**
 * Accept a seller-member invitation (TASK-FE-107). On failure the error is
 * propagated to the caller unchanged — the UI decides the copy per error code
 * (contract: product-api.md § POST /api/seller-invitations/accept, :376-415).
 */
export async function acceptSellerInvitation(
  data: AcceptSellerInvitationRequest,
): Promise<AcceptSellerInvitationResponse> {
  return productApi.acceptSellerInvitation(data);
}
