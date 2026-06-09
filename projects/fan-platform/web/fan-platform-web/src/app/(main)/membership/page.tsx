import { getFanSession } from '@/shared/auth/session';
import {
  getMemberships,
  currentActive,
  SubscribePanel,
  MembershipStatusCard,
} from '@/features/membership';
import type { MembershipTier } from '@/entities/membership';

function parseTier(raw: string | undefined): MembershipTier | undefined {
  return raw === 'MEMBERS_ONLY' || raw === 'PREMIUM' ? raw : undefined;
}

/**
 * Membership page — current status + tier subscribe (membership-service via the
 * gateway). Server Component: the access token stays on the server; the
 * subscribe/cancel writes go through `'use server'` actions.
 */
export default async function MembershipPage({
  searchParams,
}: {
  searchParams: Promise<{ tier?: string }>;
}) {
  const { tier } = await searchParams;
  const highlightTier = parseTier(tier);

  const session = await getFanSession();
  const memberships = await getMemberships(session.accessToken);
  const active = currentActive(memberships);
  const heldActiveTiers = memberships.filter((m) => m.active).map((m) => m.tier);

  return (
    <section className="flex flex-col gap-8">
      <header>
        <h1 className="text-2xl font-bold text-ink-900">멤버십</h1>
        <p className="text-sm text-ink-600">
          멤버 전용·프리미엄 콘텐츠를 위한 구독입니다. 결제는 데모용 모의 PG로 처리됩니다.
        </p>
      </header>

      {active ? <MembershipStatusCard membership={active} /> : null}

      <SubscribePanel heldActiveTiers={heldActiveTiers} highlightTier={highlightTier} />
    </section>
  );
}
