'use client';

import { useRef, useState } from 'react';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { firstAdminValidationError, type Tenant } from '../api/types';

/**
 * «다시 초대» for a `PENDING` tenant (TASK-MONO-773 S6 — console-integration-contract § 2.6.4). The address field
 * starts at the row's current first-admin address: left unchanged it is a `:resend` of that invitation (only the
 * reason is needed); changed — or with no invitation left (`NONE`) — it is a NEW first-admin invitation, which
 * also needs the display name (the create's `firstAdmin` rules). The caller decides the route from the values
 * (`useReinviteFirstAdmin`); this dialog only gates the submit.
 *
 * Built on the shared {@link ConfirmDialog} primitive (no `window.confirm`), like `TenantConfirmDialog`.
 */
export interface TenantReinviteDialogProps {
  tenant: Tenant;
  pending: boolean;
  error: string | null;
  onConfirm: (values: { email: string; displayName: string; reason: string }) => void;
  onCancel: () => void;
}

export function TenantReinviteDialog({
  tenant,
  pending,
  error,
  onConfirm,
  onCancel,
}: TenantReinviteDialogProps) {
  const current = tenant.firstAdmin ?? null;
  const currentEmail = current?.email ?? null;
  const liveInvitation = current?.invitationId ?? null;
  const [email, setEmail] = useState(currentEmail ?? '');
  const [displayName, setDisplayName] = useState('');
  const [reason, setReason] = useState('');
  const emailRef = useRef<HTMLInputElement>(null);

  const sameAddress =
    liveInvitation !== null &&
    currentEmail !== null &&
    currentEmail.trim().toLowerCase() === email.trim().toLowerCase();
  const draftOk = sameAddress || firstAdminValidationError(email, displayName) === null;
  const canConfirm = draftOk && reason.trim().length > 0 && !pending;

  return (
    <ConfirmDialog
      open
      title="첫 관리자를 다시 초대할까요?"
      description={
        sameAddress
          ? `${tenant.displayName} (${tenant.tenantId}) — 같은 주소로 초대 메일을 다시 보냅니다. 이전 메일의 링크는 더 이상 열리지 않습니다.`
          : `${tenant.displayName} (${tenant.tenantId}) — 이 주소로 첫 관리자 초대를 새로 보냅니다.`
      }
      confirmLabel={sameAddress ? '다시 보내기' : '초대 보내기'}
      pending={pending}
      confirmDisabled={!canConfirm}
      errorMessage={error}
      dialogTestId="tenant-reinvite-dialog"
      overlayTestId="tenant-reinvite-overlay"
      cancelTestId="tenant-reinvite-cancel"
      confirmTestId="tenant-reinvite-submit"
      errorTestId="tenant-reinvite-error"
      initialFocusRef={emailRef}
      onConfirm={() =>
        canConfirm && onConfirm({ email: email.trim(), displayName: displayName.trim(), reason: reason.trim() })
      }
      onCancel={onCancel}
    >
      <label htmlFor="tenant-reinvite-email" className="mt-4 block text-sm font-medium text-foreground">
        첫 관리자 이메일 <span className="text-destructive">*</span>
      </label>
      <input
        id="tenant-reinvite-email"
        ref={emailRef}
        type="text"
        inputMode="email"
        autoComplete="off"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        data-testid="tenant-reinvite-email"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />
      {!sameAddress && (
        <>
          <label
            htmlFor="tenant-reinvite-display-name"
            className="mt-3 block text-sm font-medium text-foreground"
          >
            첫 관리자 이름 <span className="text-destructive">*</span>
          </label>
          <input
            id="tenant-reinvite-display-name"
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            data-testid="tenant-reinvite-display-name"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </>
      )}
      <label htmlFor="tenant-reinvite-reason" className="mt-3 block text-sm font-medium text-foreground">
        감사 사유 <span className="text-destructive">*</span>
      </label>
      <textarea
        id="tenant-reinvite-reason"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        rows={3}
        data-testid="tenant-reinvite-reason"
        className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        placeholder="이 작업을 수행하는 이유를 입력하세요"
      />
    </ConfirmDialog>
  );
}
