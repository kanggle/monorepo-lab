'use client';
import { useState, useTransition } from 'react';
import { Button } from '@/shared/ui/Button';
import { cancelMembership } from '@/features/membership/api/actions';
import type { MembershipListItem } from '@/entities/membership';

const TIER_LABEL: Record<MembershipListItem['tier'], string> = {
  MEMBERS_ONLY: '멤버스 전용',
  PREMIUM: '프리미엄',
};

function fmt(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR');
}

/**
 * Current active membership summary + cancel. Receives only plain data (no
 * access token); the cancel write goes through the `'use server'` action.
 */
export function MembershipStatusCard({ membership }: { membership: MembershipListItem }) {
  const [isPending, startTransition] = useTransition();
  const [confirming, setConfirming] = useState(false);

  const onCancel = () => {
    startTransition(() => {
      void cancelMembership(membership.membershipId);
    });
  };

  return (
    <div className="rounded-2xl border border-brand-200 bg-brand-50/40 p-6 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-brand-600">
            현재 멤버십
          </p>
          <p className="mt-1 text-xl font-bold text-ink-900">
            {TIER_LABEL[membership.tier]}
            <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
              이용 중
            </span>
          </p>
          <p className="mt-2 text-sm text-ink-600">
            {fmt(membership.validFrom)} ~ {fmt(membership.validTo)} · {membership.planMonths}개월
          </p>
        </div>
        {confirming ? (
          <div className="flex flex-col items-end gap-2">
            <p className="text-xs text-ink-600">정말 해지하시겠어요?</p>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={isPending}
                onClick={() => setConfirming(false)}
              >
                유지
              </Button>
              <Button
                variant="primary"
                size="sm"
                disabled={isPending}
                onClick={onCancel}
              >
                {isPending ? '해지 중...' : '해지 확정'}
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="ghost" size="sm" onClick={() => setConfirming(true)}>
            멤버십 해지
          </Button>
        )}
      </div>
    </div>
  );
}
