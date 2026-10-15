import { listReceivedInvitations } from './onboarding-client';
import type { ReceivedInvitationsState } from './types';

/**
 * TASK-MONO-773 S6 — the «받은 초대» read for the `/new-tenant` page (server-side; same pattern as
 * `features/tenants` `getTenantsListState`). Never throws: any failure — IAM `503`, timeout, `401`, a bad shape —
 * is `{ kind: 'unavailable' }`, which the panel renders as «could not load», never as «no invitations». A read
 * outage never blocks the creation form below it.
 */
export async function getReceivedInvitationsState(
  accessToken: string,
): Promise<ReceivedInvitationsState> {
  try {
    const answer = await listReceivedInvitations(accessToken);
    return { kind: 'loaded', accountState: answer.accountState, items: answer.items };
  } catch {
    return { kind: 'unavailable' };
  }
}
