import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ReceivedInvitations } from '@/features/onboarding';

/**
 * TASK-MONO-773 S6 — «받은 초대» on the non-operator empty state (console-integration-contract § 2.6.4 «Empty state»
 * item 1; IAM `onboarding-api.md` § received-invitations).
 *
 *   - each item: company (`tenantDisplayName` ?? `tenantId`), roles, a «첫 관리자» badge when `firstAdmin`, expiry,
 *     and «초대 메일의 링크로 수락하세요 — …».
 *   - 🔴 READ-ONLY (owner decision 2026-10-15 UTC, 773 S1 Q6): no link, no accept button — acceptance stays the
 *     TASK-MONO-772 email-link flow.
 *   - `EMAIL_NOT_VERIFIED` → verification guidance + link · `NOT_ELIGIBLE` → the not-eligible copy ·
 *     IAM `503` → «받은 초대를 불러오지 못했습니다» (never «초대 없음»).
 */

const URL_VERIFY = 'http://iam.local/email-verification';

const ITEM = {
  invitationId: 'inv-1',
  tenantId: 'acme-corp',
  tenantDisplayName: 'Acme Corp',
  roles: ['SUPPORT_LOCK', 'AUDITOR'],
  firstAdmin: false,
  expiresAt: '2026-10-22T10:00:00Z',
  invitedAt: '2026-10-15T10:00:00Z',
};

describe('ReceivedInvitations', () => {
  it('🔴 VERIFIED: one row per invitation — company, roles, expiry, the «메일 링크로 수락» instruction', () => {
    render(
      <ReceivedInvitations
        state={{ kind: 'loaded', accountState: 'VERIFIED', items: [ITEM] }}
        emailVerificationUrl={URL_VERIFY}
      />,
    );
    const row = screen.getByTestId('received-invitation-inv-1');
    expect(row).toHaveTextContent('Acme Corp');
    expect(row).toHaveTextContent('SUPPORT_LOCK');
    expect(row).toHaveTextContent('AUDITOR');
    expect(row).toHaveTextContent('2026-10-22');
    expect(screen.getByTestId('received-invitations')).toHaveTextContent('초대 메일의 링크로 수락하세요');
    expect(within(row).queryByText('첫 관리자')).toBeNull();
  });

  it('🔴 a first-admin invitation carries the «첫 관리자» badge; no display name → the tenant id', () => {
    render(
      <ReceivedInvitations
        state={{
          kind: 'loaded',
          accountState: 'VERIFIED',
          items: [{ ...ITEM, invitationId: 'inv-2', tenantDisplayName: null, firstAdmin: true }],
        }}
        emailVerificationUrl={URL_VERIFY}
      />,
    );
    const row = screen.getByTestId('received-invitation-inv-2');
    expect(row).toHaveTextContent('acme-corp');
    expect(within(row).getByText('첫 관리자')).toBeInTheDocument();
  });

  it('🔴🔴 read-only — no link and no button anywhere in the panel', () => {
    render(
      <ReceivedInvitations
        state={{ kind: 'loaded', accountState: 'VERIFIED', items: [ITEM, { ...ITEM, invitationId: 'inv-3', firstAdmin: true }] }}
        emailVerificationUrl={URL_VERIFY}
      />,
    );
    const panel = screen.getByTestId('received-invitations');
    expect(panel.querySelectorAll('a')).toHaveLength(0);
    expect(panel.querySelectorAll('button')).toHaveLength(0);
  });

  it('VERIFIED with no items → «받은 초대가 없습니다»', () => {
    render(
      <ReceivedInvitations
        state={{ kind: 'loaded', accountState: 'VERIFIED', items: [] }}
        emailVerificationUrl={URL_VERIFY}
      />,
    );
    expect(screen.getByTestId('received-invitations')).toHaveTextContent('받은 초대가 없습니다');
  });

  it('🔴 EMAIL_NOT_VERIFIED → «이메일을 인증하면 받은 초대와 회사 만들기가 열립니다» + verification link', () => {
    render(
      <ReceivedInvitations
        state={{ kind: 'loaded', accountState: 'EMAIL_NOT_VERIFIED', items: [] }}
        emailVerificationUrl={URL_VERIFY}
      />,
    );
    const panel = screen.getByTestId('received-invitations');
    expect(panel).toHaveTextContent('이메일을 인증하면 받은 초대와 회사 만들기가 열립니다');
    expect(screen.getByTestId('received-invitations-verify-link')).toHaveAttribute('href', URL_VERIFY);
  });

  it('🔴 NOT_ELIGIBLE → the not-eligible copy', () => {
    render(
      <ReceivedInvitations
        state={{ kind: 'loaded', accountState: 'NOT_ELIGIBLE', items: [] }}
        emailVerificationUrl={URL_VERIFY}
      />,
    );
    expect(screen.getByTestId('received-invitations')).toHaveTextContent('개인 계정으로 로그인해 만드세요');
  });

  it('🔴 unavailable → «받은 초대를 불러오지 못했습니다», never «없습니다»', () => {
    render(<ReceivedInvitations state={{ kind: 'unavailable' }} emailVerificationUrl={URL_VERIFY} />);
    const panel = screen.getByTestId('received-invitations');
    expect(panel).toHaveTextContent('받은 초대를 불러오지 못했습니다');
    expect(panel).not.toHaveTextContent('받은 초대가 없습니다');
  });
});
