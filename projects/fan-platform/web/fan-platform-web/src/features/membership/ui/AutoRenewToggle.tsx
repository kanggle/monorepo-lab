'use client';
import { useState, useTransition } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  enrollBillingKey,
  cancelBillingKeyEnrollment,
  type BillingKeyEnrollment,
} from '@/features/membership/api/actions';
import { requestIssueBillingKey } from '@/features/membership/lib/portone-billing-key';
import type { MembershipTier } from '@/entities/membership';

const TIER_LABEL: Record<MembershipTier, string> = {
  MEMBERS_ONLY: '멤버스 전용',
  PREMIUM: '프리미엄',
};

/**
 * Auto-renewal ("자동 갱신") registration surface for a single tier (ADR-002).
 *
 * <p>The card issuance runs client-side (`requestIssueBillingKey`); the resulting
 * `billingKey` is handed straight to the `enrollBillingKey` `'use server'` action
 * and never held in component state (ADR-002 §D5 — treated as sensitive as an
 * access token). Cancel calls the DELETE action.
 *
 * <p>The backend exposes NO GET list endpoint for enrollments (pinned
 * membership-api.md contract), so the page cannot know the current enrollment
 * ahead of time. This component is therefore optimistic: it starts from whatever
 * `enrollment` the page passes (normally `null`) and flips local state on a
 * successful enroll/cancel — the same optimistic pattern `SubscribePanel` uses
 * after a successful call.
 */
export function AutoRenewToggle({
  tier,
  enrollment,
  buyerEmail,
  buyerName,
}: {
  tier: MembershipTier;
  enrollment?: BillingKeyEnrollment | null;
  buyerEmail?: string | null;
  buyerName?: string | null;
}) {
  const [enrolled, setEnrolled] = useState<boolean>(enrollment?.active ?? false);
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  const onEnroll = () => {
    setError(null);
    startTransition(async () => {
      const issued = await requestIssueBillingKey({ email: buyerEmail, fullName: buyerName });
      if (!issued.ok) {
        setError(issued.message);
        return;
      }
      // The billingKey is consumed by this single call and never retained.
      const result = await enrollBillingKey(tier, issued.billingKey);
      if (!result.ok) {
        setError(result.message);
        return;
      }
      setEnrolled(true);
    });
  };

  const onCancel = () => {
    setError(null);
    startTransition(async () => {
      await cancelBillingKeyEnrollment(tier);
      setEnrolled(false);
    });
  };

  return (
    <div className="rounded-xl border border-ink-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-brand-600">자동 갱신</p>
      <p className="mt-1 text-sm font-medium text-ink-900">
        {TIER_LABEL[tier]} 멤버십 자동 갱신
      </p>
      <p className="mt-1 text-sm text-ink-600">
        카드를 한 번 등록하면 만료일에 자동으로 결제·갱신됩니다. 등록은 결제가 아닌 카드 등록입니다.
      </p>
      <div className="mt-4">
        {enrolled ? (
          <div className="flex flex-wrap items-center gap-3">
            <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700">
              자동 갱신 등록됨
            </p>
            <Button
              variant="secondary"
              size="md"
              onClick={onCancel}
              disabled={isPending}
              data-testid="auto-renew-cancel"
            >
              {isPending ? '처리 중...' : '해지'}
            </Button>
          </div>
        ) : (
          <Button
            variant="primary"
            size="md"
            onClick={onEnroll}
            disabled={isPending}
            data-testid="auto-renew-enroll"
          >
            {isPending ? '등록 처리 중...' : '자동 갱신 등록'}
          </Button>
        )}
      </div>
      {error ? (
        <p role="alert" className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      ) : null}
    </div>
  );
}
