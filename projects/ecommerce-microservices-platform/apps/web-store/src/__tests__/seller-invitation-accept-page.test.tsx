import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';

// AcceptSellerInvitationForm 자체 동작은 accept-seller-invitation-form.test.tsx 에서
// 덮는다. 여기서는 페이지가 받은 props(토큰·이메일 인증 링크)를 그대로 넘기는지만 본다.
vi.mock('@/features/seller-invitation', () => ({
  AcceptSellerInvitationForm: ({
    initialToken,
    emailVerificationUrl,
  }: {
    initialToken?: string;
    emailVerificationUrl: string;
  }) => (
    <div
      data-testid="accept-form"
      data-token={initialToken ?? ''}
      data-email-verification-url={emailVerificationUrl}
    />
  ),
}));

import AcceptSellerInvitationPage from '@/app/(store)/seller-invitations/accept/page';

describe('AcceptSellerInvitationPage', () => {
  it('?token= 을 트리밍해 폼에 넘긴다 (AC-1)', async () => {
    render(
      await AcceptSellerInvitationPage({
        searchParams: Promise.resolve({ token: '  tok-123  ' }),
      }),
    );

    expect(screen.getByTestId('accept-form').dataset.token).toBe('tok-123');
  });

  it('token 이 없으면 빈 값을 넘긴다', async () => {
    render(await AcceptSellerInvitationPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByTestId('accept-form').dataset.token).toBe('');
  });

  it('OIDC_ISSUER_URL 기반 이메일 인증 링크를 폼에 넘긴다 (AC-0/AC-2)', async () => {
    render(await AcceptSellerInvitationPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByTestId('accept-form').dataset.emailVerificationUrl).toBe(
      'http://iam.local/email-verification',
    );
  });
});
