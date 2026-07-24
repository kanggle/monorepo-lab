'use client';
import { useState, useTransition } from 'react';
import { Button } from '@/shared/ui/Button';
import { subscribe } from '@/features/membership/api/actions';
import { requestPortOnePayment } from '@/features/membership/lib/portone-checkout';
import type { MembershipTier } from '@/entities/membership';

interface TierMeta {
  tier: MembershipTier;
  name: string;
  price: string;
  perks: string[];
}

const TIERS: TierMeta[] = [
  {
    tier: 'MEMBERS_ONLY',
    name: '멤버스 전용',
    price: '월 7,900원 (가상)',
    perks: ['멤버 전용 포스트 열람', '아티스트 라이브 알림', '디지털 기념품'],
  },
  {
    tier: 'PREMIUM',
    name: '프리미엄',
    price: '월 17,900원 (가상)',
    perks: ['프리미엄 포스트 / 라이브', 'V-card 디지털 굿즈', '오프라인 이벤트 우선 신청'],
  },
];

const PLAN_OPTIONS = [1, 3, 12];

/**
 * Tier subscribe cards. Receives only plain data; the subscribe write goes
 * through the `'use server'` action, which returns a discriminated result so a
 * PG decline renders inline (no error boundary).
 *
 * @param heldActiveTiers tiers the caller already actively holds (disabled here).
 * @param highlightTier   tier to visually emphasize (from the gate deep-link).
 */
export function SubscribePanel({
  heldActiveTiers,
  highlightTier,
}: {
  heldActiveTiers: MembershipTier[];
  highlightTier?: MembershipTier;
}) {
  const [planMonths, setPlanMonths] = useState(1);
  const [isPending, startTransition] = useTransition();
  const [pendingTier, setPendingTier] = useState<MembershipTier | null>(null);
  const [decline, setDecline] = useState<{ tier: MembershipTier; message: string } | null>(null);

  const held = new Set(heldActiveTiers);
  // PREMIUM ⊇ MEMBERS_ONLY (membership-service AccessPolicy.tierGrants): holding
  // PREMIUM already grants all MEMBERS_ONLY content, so offering the MEMBERS_ONLY
  // subscribe would sell nothing new. Suppress it. The reverse (MEMBERS_ONLY held
  // → PREMIUM offered) stays open as the upgrade path.
  const hasPremium = held.has('PREMIUM');

  const onSubscribe = (tier: MembershipTier) => {
    setDecline(null);
    setPendingTier(tier);
    startTransition(async () => {
      const meta = TIERS.find((t) => t.tier === tier);
      // Open the PortOne payment window; the returned paymentId is verified
      // server-side by the subscribe action's backend (ADR-001).
      const checkout = await requestPortOnePayment(
        `${meta?.name ?? '멤버십'} 멤버십 (${planMonths}개월)`,
        planMonths,
      );
      if (!checkout.ok) {
        setDecline({ tier, message: checkout.message });
        setPendingTier(null);
        return;
      }
      const result = await subscribe(tier, planMonths, checkout.paymentId);
      if (!result.ok) {
        setDecline({
          tier,
          message:
            result.code === 'PAYMENT_DECLINED'
              ? '결제가 거절되었습니다. 다른 결제 수단을 사용해 주세요.'
              : result.message,
        });
      }
      setPendingTier(null);
    });
  };

  return (
    <div className="flex flex-col gap-5">
      <fieldset className="flex flex-wrap items-end gap-4 rounded-xl border border-ink-200 bg-white p-4">
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium text-ink-700">결제 기간</span>
          <select
            value={planMonths}
            onChange={(e) => setPlanMonths(Number(e.target.value))}
            className="rounded-md border border-ink-300 px-3 py-1.5 text-sm"
          >
            {PLAN_OPTIONS.map((m) => (
              <option key={m} value={m}>
                {m}개월
              </option>
            ))}
          </select>
        </label>
        <p className="flex-1 self-center text-sm text-ink-500">
          카드 결제는 PortOne 테스트 결제창에서 진행됩니다.
        </p>
      </fieldset>

      <ul className="grid gap-4 md:grid-cols-2">
        {TIERS.map((meta) => {
          const isHeld = held.has(meta.tier);
          // MEMBERS_ONLY is already covered by a held PREMIUM — offer nothing.
          const includedInPremium = meta.tier === 'MEMBERS_ONLY' && hasPremium && !isHeld;
          const isHighlighted = highlightTier === meta.tier;
          const busy = isPending && pendingTier === meta.tier;
          return (
            <li
              key={meta.tier}
              className={[
                'rounded-xl border bg-white p-6 shadow-sm',
                isHighlighted ? 'border-brand-500 ring-2 ring-brand-200' : 'border-ink-200',
              ].join(' ')}
            >
              <p className="text-xs font-semibold uppercase tracking-wide text-brand-600">
                {meta.name}
              </p>
              <p className="mt-2 text-2xl font-bold text-ink-900">{meta.price}</p>
              <ul className="mt-4 flex flex-col gap-2 text-sm text-ink-700">
                {meta.perks.map((p) => (
                  <li key={p}>· {p}</li>
                ))}
              </ul>
              <div className="mt-5">
                {isHeld ? (
                  <p className="rounded-md bg-emerald-50 px-3 py-2 text-center text-sm font-medium text-emerald-700">
                    이용 중인 멤버십
                  </p>
                ) : includedInPremium ? (
                  <p className="rounded-md bg-ink-50 px-3 py-2 text-center text-sm font-medium text-ink-500">
                    프리미엄에 포함됨
                  </p>
                ) : (
                  <Button
                    variant="primary"
                    size="md"
                    className="w-full"
                    disabled={isPending}
                    onClick={() => onSubscribe(meta.tier)}
                  >
                    {busy ? '결제 처리 중...' : '카드로 결제'}
                  </Button>
                )}
                {decline && decline.tier === meta.tier ? (
                  <p
                    role="alert"
                    className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700"
                  >
                    {decline.message}
                  </p>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
