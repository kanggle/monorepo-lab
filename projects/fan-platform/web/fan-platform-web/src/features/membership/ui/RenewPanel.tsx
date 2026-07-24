'use client';
import { useState, useTransition } from 'react';
import { Button } from '@/shared/ui/Button';
import { renewMembership } from '@/features/membership/api/actions';
import { requestPortOnePayment, TIER_MONTHLY_KRW } from '@/features/membership/lib/portone-checkout';
import type { MembershipListItem } from '@/entities/membership';

const TIER_LABEL: Record<MembershipListItem['tier'], string> = {
  MEMBERS_ONLY: '멤버스 전용',
  PREMIUM: '프리미엄',
};

function fmt(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR');
}

/**
 * Prompt to renew a just-expired membership (status ACTIVE, read-time inactive,
 * not canceled). Renewing re-activates the same tier with a fresh window from now
 * (the `'use server'` renew action; the access token stays on the server).
 */
export function RenewPanel({ membership }: { membership: MembershipListItem }) {
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  const onRenew = () => {
    setError(null);
    startTransition(async () => {
      // Same PortOne payment window as subscribe; the paymentId is verified
      // server-side by the renew action's backend (ADR-001).
      const checkout = await requestPortOnePayment(
        `${TIER_LABEL[membership.tier]} 멤버십 갱신 (${membership.planMonths}개월)`,
        TIER_MONTHLY_KRW[membership.tier] * membership.planMonths,
      );
      if (!checkout.ok) {
        setError(checkout.message);
        return;
      }
      const result = await renewMembership(
        membership.membershipId,
        membership.planMonths,
        checkout.paymentId,
      );
      if (!result.ok) setError(result.message);
    });
  };

  return (
    <div className="rounded-2xl border border-amber-200 bg-amber-50/50 p-6 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-amber-700">멤버십 만료</p>
      <p className="mt-1 text-lg font-bold text-ink-900">
        {TIER_LABEL[membership.tier]} 멤버십이 만료되었습니다
      </p>
      <p className="mt-1 text-sm text-ink-600">
        만료일 {fmt(membership.validTo)} · 갱신하면 같은 등급으로 다시 이용할 수 있어요.
      </p>
      <div className="mt-4">
        <Button
          variant="primary"
          onClick={onRenew}
          disabled={isPending}
          data-testid="renew-panel-button"
        >
          {isPending ? '갱신 중...' : '갱신하기'}
        </Button>
      </div>
      {error ? (
        <p className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
      ) : null}
    </div>
  );
}
