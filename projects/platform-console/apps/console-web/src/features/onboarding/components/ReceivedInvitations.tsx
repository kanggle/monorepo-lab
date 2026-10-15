import type { ReceivedInvitationsState } from '../api/types';

export interface ReceivedInvitationsProps {
  state: ReceivedInvitationsState;
  emailVerificationUrl: string | null;
}

/** TASK-MONO-773 S6 — PHASE 1 scaffold only (so the red tests compile). Pre-S6 there is no such panel. */
export function ReceivedInvitations(_props: ReceivedInvitationsProps) {
  return null;
}
