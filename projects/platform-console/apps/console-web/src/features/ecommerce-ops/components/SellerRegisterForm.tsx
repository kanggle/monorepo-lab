'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useRegisterSeller } from '../hooks/use-ecommerce-sellers';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Seller register form (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10.5).
 * Used by the `new` page.
 *
 *   - `sellerId` (≤64 chars, non-blank, unique-within-tenant — producer enforces
 *     409 CONFLICT on duplicate).
 *   - `displayName` (non-blank).
 *   - Confirm-gated submit; 409/400 surfaced inline. NO `Idempotency-Key`.
 *   - On success → /ecommerce/sellers.
 *
 * v1: register only. No edit/delete (producer defines none — ADR-030 v1).
 */

export function SellerRegisterForm() {
  const router = useRouter();
  const sellerIdFid = useId();
  const displayNameFid = useId();

  const [sellerId, setSellerId] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const register = useRegisterSeller();
  const pending = register.isPending;

  const sellerIdTrimmed = sellerId.trim();
  const displayNameTrimmed = displayName.trim();

  const formValid =
    sellerIdTrimmed !== '' &&
    sellerIdTrimmed.length <= 64 &&
    displayNameTrimmed !== '';

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formValid) return;
    setError(null);
    setConfirmOpen(true);
  }

  function confirmSubmit() {
    register.mutate(
      { sellerId: sellerIdTrimmed, displayName: displayNameTrimmed },
      {
        onSuccess: () => {
          setConfirmOpen(false);
          router.push('/ecommerce/sellers');
          router.refresh();
        },
        onError: (err: unknown) => {
          const code =
            err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
          setError(
            messageForCode(
              code,
              code === 'CONFLICT' || code === 'HTTP_409'
                ? `셀러 ID "${sellerIdTrimmed}"는 이미 사용 중입니다.`
                : '셀러를 등록하지 못했습니다.',
            ),
          );
        },
      },
    );
  }

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
  const labelCls = 'block text-sm font-medium text-foreground';

  return (
    <form
      onSubmit={onSubmit}
      className="max-w-xl space-y-5"
      data-testid="seller-register-form"
    >
      <div>
        <label htmlFor={sellerIdFid} className={labelCls}>
          셀러 ID <span className="text-destructive">*</span>
        </label>
        <input
          id={sellerIdFid}
          value={sellerId}
          onChange={(e) => setSellerId(e.target.value)}
          maxLength={64}
          placeholder="예: acme-corp"
          className={inputCls}
          data-testid="seller-register-seller-id"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          최대 64자. 테넌트 내에서 고유해야 합니다.
        </p>
      </div>

      <div>
        <label htmlFor={displayNameFid} className={labelCls}>
          셀러 이름 <span className="text-destructive">*</span>
        </label>
        <input
          id={displayNameFid}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="예: Acme Corporation"
          className={inputCls}
          data-testid="seller-register-display-name"
        />
      </div>

      {error && !confirmOpen && (
        <p
          role="alert"
          className="text-sm text-destructive"
          data-testid="seller-register-error"
        >
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <Button
          type="submit"
          disabled={!formValid || pending}
          data-testid="seller-register-submit"
        >
          셀러 등록
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
          data-testid="seller-register-cancel"
        >
          취소
        </Button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="셀러를 등록할까요?"
        description={`셀러 ID "${sellerIdTrimmed}", 이름 "${displayNameTrimmed}"로 등록합니다.`}
        confirmLabel="등록"
        pending={pending}
        errorMessage={error}
        onConfirm={confirmSubmit}
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
        }}
      />
    </form>
  );
}
