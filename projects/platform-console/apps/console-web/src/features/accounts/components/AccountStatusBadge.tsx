import { StatusBadge, type StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Account `status` → shared semantic {@link StatusTone} (TASK-PC-FE-242).
 *
 * `status` is a tolerant free string (the producer may add lifecycle states);
 * an unrecognised value renders `neutral` rather than crashing or being hidden.
 */
export function accountStatusTone(status: string): StatusTone {
  if (status === 'ACTIVE') return 'success';
  if (status === 'LOCKED') return 'danger';
  return 'neutral';
}

/**
 * Account status badge — the domain's `status → tone` map plus the shared chip.
 *
 * Before TASK-PC-FE-242 this file re-implemented the pill markup and carried its
 * own colour ladder, so an ACTIVE account rendered grey here while every other
 * domain renders an active entity green. It now owns only the mapping, which is
 * the one thing a domain is supposed to own.
 */
export function AccountStatusBadge({ status }: { status: string }) {
  return (
    <StatusBadge tone={accountStatusTone(status)} data-testid="account-status">
      {status}
    </StatusBadge>
  );
}
