'use client';

import { useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import type { AcceptSellerInvitationResponse } from '@repo/types';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import { acceptSellerInvitation } from '../api/seller-invitation-api';
import { formatDateTime } from '@/shared/lib';

interface AcceptSellerInvitationFormProps {
  /** Pre-filled from `?token=` (TASK-FE-107 AC-1). */
  initialToken?: string;
  /**
   * `${OIDC_ISSUER_URL}/email-verification` — built server-side by the page
   * (`shared/auth/auth-callbacks.ts:34`) and handed down as a prop, so this
   * client component never needs to read the (server-only) issuer env itself.
   */
  emailVerificationUrl: string;
}

const EMAIL_NOT_VERIFIED_CODE = 'SELLER_INVITATION_EMAIL_NOT_VERIFIED';

/**
 * Seller-member invitation accept screen (TASK-FE-107). Calls
 * `POST /api/seller-invitations/accept` (contract: product-api.md :376-415).
 *
 * `SELLER_INVITATION_EMAIL_NOT_VERIFIED` is handled separately from the
 * generic error table (AC-2): the invitation is NOT consumed by a refused
 * attempt, so the form stays usable and offers the IAM verification-mail
 * page — the person can come straight back and accept again.
 */
export function AcceptSellerInvitationForm({
  initialToken = '',
  emailVerificationUrl,
}: AcceptSellerInvitationFormProps) {
  const router = useRouter();
  const pathname = usePathname();

  const [token, setToken] = useState(initialToken);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [emailNotVerified, setEmailNotVerified] = useState(false);
  const [result, setResult] = useState<AcceptSellerInvitationResponse | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Edge case — trim whitespace/newlines from copy-paste before sending.
    const trimmed = token.trim();
    if (!trimmed || isSubmitting) return;

    setError('');
    setEmailNotVerified(false);
    setIsSubmitting(true);

    try {
      const response = await acceptSellerInvitation({ token: trimmed });
      setResult(response);
      // Edge case — don't leave the token sitting in the address bar/history
      // after a successful accept.
      router.replace(pathname);
    } catch (err) {
      if (isApiError(err) && err.code === EMAIL_NOT_VERIFIED_CODE) {
        setEmailNotVerified(true);
      } else if (isApiError(err)) {
        setError(ERROR_MESSAGES[err.code] ?? err.message ?? '초대 수락에 실패했습니다.');
      } else {
        setError('초대 수락에 실패했습니다.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (result) {
    return (
      <div className="card" style={{ padding: 'var(--space-6)' }}>
        <h1 className="page-title">셀러 구성원 가입 완료</h1>
        <p role="status" style={{ marginTop: 'var(--space-4)' }}>
          셀러 <strong>{result.sellerId}</strong> 의 구성원이 되었습니다.
        </p>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
          가입일: {formatDateTime(result.joinedAt)}
        </p>
        <Link
          href="/"
          className="btn btn-primary btn-lg"
          style={{ marginTop: 'var(--space-6)', display: 'inline-block' }}
        >
          스토어로 이동
        </Link>
      </div>
    );
  }

  return (
    <div className="card" style={{ padding: 'var(--space-6)' }}>
      <h1 className="page-title">셀러 초대 수락</h1>
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)' }}>
        운영자에게 받은 초대 코드를 입력하면 해당 셀러의 구성원이 됩니다.
      </p>

      {emailNotVerified && (
        <div role="alert" className="alert-error" data-testid="email-not-verified">
          <p>이메일 인증이 필요합니다. 계정의 이메일을 인증한 뒤 다시 수락해 주세요.</p>
          <p style={{ margin: 0 }}>
            <a href={emailVerificationUrl}>이메일 인증 화면으로 이동</a>
          </p>
        </div>
      )}
      {!emailNotVerified && error && (
        <div role="alert" className="alert-error">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label htmlFor="invitationToken" className="label">
            초대 코드
          </label>
          <input
            id="invitationToken"
            type="text"
            className="input"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="초대 코드를 붙여넣어 주세요"
          />
        </div>

        <button
          type="submit"
          disabled={isSubmitting || token.trim().length === 0}
          className="btn btn-primary btn-lg"
          style={{ width: '100%', marginTop: 'var(--space-4)' }}
        >
          {isSubmitting ? '수락 처리 중...' : '초대 수락'}
        </button>
      </form>
    </div>
  );
}
