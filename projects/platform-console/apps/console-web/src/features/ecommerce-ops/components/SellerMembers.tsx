'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  useSellerMembers,
  useInviteSellerMember,
} from '../hooks/use-ecommerce-sellers';
import {
  InviteSellerMemberBodySchema,
  invitationStateLabel,
  type InviteSellerMemberResponse,
} from '../api/seller-types';

/**
 * Seller members section of the seller detail screen (TASK-MONO-752 —
 * ADR-MONO-079 D5): the people linked to this seller, its invitations, and an
 * invite-by-email form.
 *
 * A person is linked only when they accept the invitation while logged in to
 * the store (IAM checks the invited email is that account's email). The console
 * does not deliver the invitation — there is no mail path — so the token from
 * the invite response is shown ONCE here for the operator to hand over; the
 * list never carries it. Inviting is allowed only for an ACTIVE seller (the
 * producer answers 409 SELLER_NOT_ACTIVE otherwise).
 */

export interface SellerMembersProps {
  sellerId: string;
  /** The seller's status — the invite form is offered only when ACTIVE. */
  sellerStatus: string;
}

const MEMBER_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '활성',
  REVOKED: '회수됨',
};

export function SellerMembers({ sellerId, sellerStatus }: SellerMembersProps) {
  const emailFid = useId();
  const membersQ = useSellerMembers(sellerId);
  const invite = useInviteSellerMember(sellerId);

  const [email, setEmail] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [issued, setIssued] = useState<InviteSellerMemberResponse | null>(null);

  const canInvite = sellerStatus === 'ACTIVE';

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const parsed = InviteSellerMemberBodySchema.safeParse({ email });
    if (!parsed.success) {
      setFormError(parsed.error.issues[0]?.message ?? '이메일을 확인해 주세요.');
      return;
    }
    invite.mutate(parsed.data.email, {
      onSuccess: (res) => {
        setIssued(res);
        setEmail('');
      },
      onError: (err: unknown) => {
        const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
        setFormError(
          code === 'SELLER_NOT_ACTIVE'
            ? '활성 상태의 셀러만 구성원을 초대할 수 있습니다.'
            : messageForCode(code, '초대를 만들지 못했습니다.'),
        );
      },
    });
  }

  const data = membersQ.data;

  return (
    <section
      aria-labelledby="seller-members-heading"
      className="mt-8"
      data-testid="seller-members"
    >
      <h2 id="seller-members-heading" className="mb-3 text-lg font-semibold">
        구성원
      </h2>

      {membersQ.isError && (
        <p
          role="status"
          className="text-sm text-muted-foreground"
          data-testid="seller-members-degraded"
        >
          구성원 정보를 일시적으로 불러올 수 없습니다.
        </p>
      )}

      {data && (
        <>
          {data.members.length === 0 ? (
            <p
              className="text-sm text-muted-foreground"
              data-testid="seller-members-empty"
            >
              아직 구성원이 없습니다.
            </p>
          ) : (
            <ul className="mb-4 space-y-1 text-sm" data-testid="seller-members-list">
              {data.members.map((m) => (
                <li
                  key={m.accountId}
                  className="flex flex-wrap gap-3"
                  data-testid="seller-member-row"
                >
                  <span className="font-mono text-xs">{m.accountId}</span>
                  <span>{m.role}</span>
                  <span data-testid="seller-member-status">
                    {MEMBER_STATUS_LABEL[m.status] ?? m.status}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {formatDateTime(m.joinedAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}

          {data.invitations.length > 0 && (
            <>
              <h3 className="mb-2 mt-4 text-sm font-medium">초대</h3>
              <ul
                className="mb-4 space-y-1 text-sm"
                data-testid="seller-invitations-list"
              >
                {data.invitations.map((inv) => (
                  <li
                    key={inv.invitationId}
                    className="flex flex-wrap gap-3"
                    data-testid="seller-invitation-row"
                  >
                    <span>{inv.email}</span>
                    <span data-testid="seller-invitation-state">
                      {invitationStateLabel(inv)}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      만료 {formatDateTime(inv.expiresAt)}
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}

      {canInvite ? (
        <form
          onSubmit={onSubmit}
          className="mt-4 flex max-w-xl flex-wrap items-end gap-3"
          data-testid="seller-invite-form"
        >
          <div className="min-w-0 flex-1">
            <label htmlFor={emailFid} className="block text-sm font-medium">
              초대할 이메일
            </label>
            <input
              id={emailFid}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              maxLength={320}
              placeholder="person@example.com"
              className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              data-testid="seller-invite-email"
            />
          </div>
          <Button
            type="submit"
            size="sm"
            disabled={invite.isPending || email.trim() === ''}
            data-testid="seller-invite-submit"
          >
            초대
          </Button>
        </form>
      ) : (
        <p
          className="mt-4 text-sm text-muted-foreground"
          data-testid="seller-invite-unavailable"
        >
          활성 상태의 셀러만 구성원을 초대할 수 있습니다.
        </p>
      )}

      {formError && (
        <p
          role="alert"
          className="mt-2 text-sm text-destructive"
          data-testid="seller-invite-error"
        >
          {formError}
        </p>
      )}

      {issued && (
        <div
          role="status"
          className="mt-3 rounded-md border border-border bg-muted p-3 text-sm"
          data-testid="seller-invite-issued"
        >
          <p>
            {issued.email} 님에게 전달할 초대 코드입니다. 이 화면을 벗어나면
            다시 볼 수 없습니다. 받은 사람이 스토어에 그 이메일 계정으로
            로그인한 뒤 수락해야 구성원이 됩니다.
          </p>
          <p
            className="mt-2 break-all font-mono text-xs"
            data-testid="seller-invite-token"
          >
            {issued.token}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            만료 {formatDateTime(issued.expiresAt)}
          </p>
        </div>
      )}
    </section>
  );
}
