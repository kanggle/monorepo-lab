import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ApiErrorResponse } from '@repo/types';
import { AcceptSellerInvitationForm } from '@/features/seller-invitation/ui/AcceptSellerInvitationForm';

const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
  usePathname: () => '/seller-invitations/accept',
}));

vi.mock('@/features/seller-invitation/api/seller-invitation-api', () => ({
  acceptSellerInvitation: vi.fn(),
}));

import { acceptSellerInvitation } from '@/features/seller-invitation/api/seller-invitation-api';
const mockAccept = vi.mocked(acceptSellerInvitation);

const EMAIL_VERIFICATION_URL = 'http://iam.local/email-verification';

function renderForm(initialToken?: string) {
  return render(
    <AcceptSellerInvitationForm
      initialToken={initialToken}
      emailVerificationUrl={EMAIL_VERIFICATION_URL}
    />,
  );
}

function apiError(code: string, message = 'error'): ApiErrorResponse {
  return { code, message, timestamp: new Date().toISOString() };
}

describe('AcceptSellerInvitationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('?token= 값으로 입력칸이 미리 채워진다 (AC-1)', () => {
    renderForm('tok-prefill');
    expect(screen.getByLabelText('초대 코드')).toHaveValue('tok-prefill');
  });

  it('토큰이 비어 있으면 수락 버튼이 비활성화된다', () => {
    renderForm();
    expect(screen.getByRole('button', { name: '초대 수락' })).toBeDisabled();
  });

  it('유효한 토큰으로 수락하면 성공 화면을 보여주고 주소에서 token 을 지운다 (AC-1, Edge)', async () => {
    mockAccept.mockResolvedValueOnce({
      sellerId: 'seller-1',
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: '2026-10-08T00:00:00Z',
    });

    const user = userEvent.setup();
    renderForm('tok-1');
    await user.click(screen.getByRole('button', { name: '초대 수락' }));

    await waitFor(() => {
      expect(screen.getByText('셀러 구성원 가입 완료')).toBeInTheDocument();
    });
    expect(screen.getByText('seller-1', { exact: false })).toBeInTheDocument();
    expect(mockReplace).toHaveBeenCalledWith('/seller-invitations/accept');
  });

  it('앞뒤 공백을 다듬어 전송한다 (Edge)', async () => {
    mockAccept.mockResolvedValueOnce({
      sellerId: 'seller-1', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2026-10-08T00:00:00Z',
    });
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText('초대 코드'), '  tok-with-space  ');
    await user.click(screen.getByRole('button', { name: '초대 수락' }));

    await waitFor(() => {
      expect(mockAccept).toHaveBeenCalledWith({ token: 'tok-with-space' });
    });
  });

  describe('AC-2 — SELLER_INVITATION_EMAIL_NOT_VERIFIED', () => {
    it('이메일 인증 필요 문구 + 인증 화면 링크를 보여주고, 수락 버튼이 계속 살아 있다', async () => {
      mockAccept.mockRejectedValueOnce(apiError('SELLER_INVITATION_EMAIL_NOT_VERIFIED'));

      const user = userEvent.setup();
      renderForm('tok-1');
      await user.click(screen.getByRole('button', { name: '초대 수락' }));

      await waitFor(() => {
        expect(screen.getByTestId('email-not-verified')).toBeInTheDocument();
      });
      expect(screen.getByText('이메일 인증이 필요합니다. 계정의 이메일을 인증한 뒤 다시 수락해 주세요.')).toBeInTheDocument();
      expect(screen.getByText('이메일 인증 화면으로 이동').closest('a')).toHaveAttribute(
        'href',
        EMAIL_VERIFICATION_URL,
      );
      // 초대가 소모되지 않았다 — 버튼이 비활성화되어 있으면 안 된다.
      expect(screen.getByRole('button', { name: '초대 수락' })).not.toBeDisabled();
    });

    it('인증 후 같은 화면에서 다시 수락할 수 있다', async () => {
      mockAccept.mockRejectedValueOnce(apiError('SELLER_INVITATION_EMAIL_NOT_VERIFIED'));
      mockAccept.mockResolvedValueOnce({
        sellerId: 'seller-1', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2026-10-08T00:00:00Z',
      });

      const user = userEvent.setup();
      renderForm('tok-1');
      await user.click(screen.getByRole('button', { name: '초대 수락' }));
      await waitFor(() => expect(screen.getByTestId('email-not-verified')).toBeInTheDocument());

      await user.click(screen.getByRole('button', { name: '초대 수락' }));

      await waitFor(() => {
        expect(screen.getByText('셀러 구성원 가입 완료')).toBeInTheDocument();
      });
      expect(mockAccept).toHaveBeenCalledTimes(2);
    });
  });

  describe('AC-3 — 나머지 오류 코드별 문구 (표)', () => {
    const cases: Array<[string, string]> = [
      ['SELLER_INVITATION_EMAIL_MISMATCH', '이 초대는 다른 이메일 주소로 발급되었습니다. 초대받은 이메일 계정으로 로그인해 주세요.'],
      ['SELLER_INVITATION_NOT_FOUND', '유효하지 않은 초대 코드입니다. 코드를 다시 확인해 주세요.'],
      ['SELLER_INVITATION_ALREADY_USED', '이미 다른 계정이 사용한 초대입니다.'],
      ['SELLER_NOT_ACTIVE', '초대한 셀러가 더 이상 활성 상태가 아닙니다.'],
      ['SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE', '이 계정으로는 셀러 구성원이 될 수 없습니다.'],
      ['SELLER_INVITATION_EXPIRED', '초대가 만료되었습니다. 초대한 운영자에게 새 초대를 요청해 주세요.'],
      ['SERVICE_UNAVAILABLE', '잠시 후 다시 시도해 주세요.'],
      ['UNAUTHORIZED', '로그인이 필요합니다. 다시 로그인한 뒤 시도해 주세요.'],
    ];

    it.each(cases)('%s → 고유한 문구를 보여준다', async (code, expectedMessage) => {
      mockAccept.mockRejectedValueOnce(apiError(code));

      const user = userEvent.setup();
      renderForm('tok-1');
      await user.click(screen.getByRole('button', { name: '초대 수락' }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent(expectedMessage);
      });
    });

    it('모든 코드의 문구가 서로 다르다 (표 유일성)', () => {
      const messages = cases.map(([, message]) => message);
      expect(new Set(messages).size).toBe(messages.length);
    });

    it('503 은 초대/토큰 문제로 말하지 않는다', async () => {
      mockAccept.mockRejectedValueOnce(apiError('SERVICE_UNAVAILABLE'));

      const user = userEvent.setup();
      renderForm('tok-1');
      await user.click(screen.getByRole('button', { name: '초대 수락' }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요.');
      });
      expect(screen.getByRole('alert')).not.toHaveTextContent(/초대|코드|토큰/);
    });

    it('알 수 없는 에러 시 기본 메시지를 표시한다', async () => {
      mockAccept.mockRejectedValueOnce(new Error('boom'));

      const user = userEvent.setup();
      renderForm('tok-1');
      await user.click(screen.getByRole('button', { name: '초대 수락' }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent('초대 수락에 실패했습니다.');
      });
    });
  });
});
