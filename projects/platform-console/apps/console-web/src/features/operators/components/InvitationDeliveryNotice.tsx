'use client';

import { Button } from '@/shared/ui/Button';
import {
  isDeliveryFailed,
  type OperatorInvitation,
} from '../api/invitation-types';
import { deliveryNoticeCopy } from './invitation-copy';

/**
 * Result notice after an invite (201) or a resend (200) — TASK-MONO-772 S5,
 * console-integration-contract § 2.4.3: «An invite/resend response with
 * `FAILED_*` is shown inline as a failure of DELIVERY, not of the invitation».
 *
 * So a failed delivery is a WARNING (`role="status"`, amber) with «다시
 * 보내기» — never `role="alert"` / destructive styling, never an error state:
 * the invitation exists and is in the pending list below.
 *
 * 🔴 Shows the email and the delivery outcome only — never a token or a link
 *    (the producer never returns one; OD-4: delivery is by mail only).
 */

export interface InvitationDeliveryNoticeProps {
  invitation: OperatorInvitation;
  via: 'invite' | 'resend';
  onResend: () => void;
  onDismiss: () => void;
}

export function InvitationDeliveryNotice({
  invitation,
  via,
  onResend,
  onDismiss,
}: InvitationDeliveryNoticeProps) {
  const failed = isDeliveryFailed(invitation);
  return (
    <div
      role="status"
      data-testid="invitation-delivery-notice"
      data-delivery-status={invitation.delivery?.status ?? 'NONE'}
      className={
        failed
          ? 'mb-4 rounded-md border border-amber-500/50 bg-amber-500/10 px-3 py-2 text-sm text-foreground'
          : 'mb-4 rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground'
      }
    >
      <p>
        {failed ? '⚠ ' : ''}
        {deliveryNoticeCopy(invitation, via)}
      </p>
      <div className="mt-2 flex gap-2">
        {failed && (
          <Button
            type="button"
            size="sm"
            onClick={onResend}
            data-testid="invitation-delivery-notice-resend"
          >
            다시 보내기
          </Button>
        )}
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={onDismiss}
          data-testid="invitation-delivery-notice-dismiss"
        >
          닫기
        </Button>
      </div>
    </div>
  );
}
