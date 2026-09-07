import Link from 'next/link';
import { getFanSession } from '@/shared/auth/session';
import { getMemberships, currentActive } from '@/features/membership/api/getMemberships';
import { MembershipStatusCard } from './MembershipStatusCard';
import { AutoRenewToggle } from './AutoRenewToggle';
import { RenewPanel } from './RenewPanel';
import { SubscribePanel } from './SubscribePanel';
import type { MembershipTier } from '@/entities/membership';

/**
 * `/membership` 의 **회원 전용 절반** — 지금 내 구독 상태와 가입·해지·갱신 조작.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 페이지에서 이 덩어리를 떼어냈는가
 * ─────────────────────────────────────────────────────────────────────────
 * `/membership` 이 공개 경로가 되면서 한 화면에 **성질이 다른 두 절반**이 생겼다:
 *
 *   · 공개 절반 — 요금제 안내. 저장본에서 읽고, 로그인도 백엔드도 필요 없다.
 *   · 회원 절반 — **내** 구독. `GET /api/v1/memberships` 는 «요금제 목록» 이 아니라
 *                 «현재 사용자의 구독» 을 돌려주는 개인 데이터 엔드포인트다.
 *
 * 🔴🔴 그 둘이 한 함수 안에 섞여 있으면, 공개 경로에서 개인 데이터 호출이 «조건이 거짓이라
 *    안 불릴 뿐» 인 상태가 된다. 조건은 다음 리팩터에서 사라질 수 있다. 그래서 호출 자체를
 *    이 컴포넌트 안으로 옮기고, 페이지는 **로그인했을 때만 이 엘리먼트를 만든다** —
 *    익명 경로에는 이 모듈의 코드가 실행될 자리가 없다.
 *
 * 🔵 내용은 예전 `/membership` 페이지와 **같다.** 인가도 그대로다: 토큰은 서버에 남고,
 *    가입·해지는 `'use server'` 액션이며, 최종 판정은 membership-service 가 한다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export async function MembershipMemberPanel({
  highlightTier,
}: {
  highlightTier?: MembershipTier;
}) {
  // TASK-FAN-FE-015: 이 값은 배포의 **현재 상태**여야 한다 — 이미지가 구워질 때의 상태가
  // 아니라. 요청마다 도는 서버 컴포넌트에서 읽는 이유.
  const demoPayment = process.env.DEMO_PAYMENT_MOCK === '1';

  const session = await getFanSession();
  const memberships = await getMemberships(session.accessToken);
  const active = currentActive(memberships);
  const heldActiveTiers = memberships.filter((m) => m.active).map((m) => m.tier);
  // 막 만료된 멤버십(저장은 ACTIVE, 읽는 시점엔 비활성, 해지 아님)은 갱신 대상이다.
  // 목록은 최신 창 우선이라 첫 매치가 가장 최근이다. 활성이 하나도 없을 때만 노출한다.
  const expired = active
    ? null
    : (memberships.find((m) => m.status === 'ACTIVE' && !m.active) ?? null);

  return (
    <div data-testid="membership-member-panel" className="flex flex-col gap-8">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-ink-600">
          {demoPayment ? '결제는 데모용 모의 PG로 처리됩니다.' : ' '}
        </p>
        <Link
          href="/membership/history"
          className="shrink-0 text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          이력 보기
        </Link>
      </div>

      {active ? <MembershipStatusCard membership={active} /> : null}
      {active ? (
        <AutoRenewToggle
          tier={active.tier}
          buyerEmail={session.email}
          buyerName={session.displayName}
        />
      ) : null}
      {expired ? (
        <RenewPanel
          membership={expired}
          buyerEmail={session.email}
          buyerName={session.displayName}
        />
      ) : null}

      <SubscribePanel
        heldActiveTiers={heldActiveTiers}
        highlightTier={highlightTier}
        buyerEmail={session.email}
        buyerName={session.displayName}
      />
    </div>
  );
}
