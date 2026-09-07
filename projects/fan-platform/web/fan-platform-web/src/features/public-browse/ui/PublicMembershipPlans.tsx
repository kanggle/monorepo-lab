import Link from 'next/link';
import type { PublicMembershipPlan } from '@demo/public-data';

/**
 * 공개 멤버십 안내 — 요금제·혜택·가격.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴🔴 이 데이터의 출처는 **백엔드가 아니다**
 * ─────────────────────────────────────────────────────────────────────────
 * `GET /api/v1/memberships` 는 *"내 구독"* 을 돌려주는 **개인 데이터** 엔드포인트다
 * (DTO 에 accountId·결제 상태가 있다). 그것을 공개 화면의 출처로 삼는 것이
 * `@demo/public-data` 가 막으려는 바로 그 실패이므로, 요금제 **안내**는 저장소가 소유한
 * `authored` 콘텐츠다. 실제 과금·가입은 로그인 뒤 백엔드가 한다.
 *
 * 🔴 `note` 는 **반드시 그린다.** 그 필드가 존재하는 이유가 "표시 가격은 안내용이고 실제
 *    가입은 로그인 후" 를 화면이 말하게 하는 것이다(`PublicMembershipPlan.note` 주석).
 *    안 그리면 이 카드는 «지금 이 가격에 가입된다» 를 주장하는 화면이 된다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export function PublicMembershipPlans({
  plans,
  authenticated,
}: {
  plans: PublicMembershipPlan[];
  /**
   * 로그인 여부. 🔵 CTA 문구만 가른다 — 로그인해도 이 카드는 그대로 보인다(요금제 안내는
   * 회원에게도 공개 정보이고, 회원 패널은 이 위에 따로 붙는다).
   */
  authenticated: boolean;
}) {
  return (
    <section data-testid="public-membership-plans" className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold text-ink-900">요금제</h2>

      {plans.length === 0 ? (
        <p className="text-sm text-ink-600">
          공개된 요금제 안내가 저장본에 없습니다.
        </p>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {plans.map((plan) => (
            <li
              key={plan.tier}
              data-testid="public-membership-plan"
              className="flex flex-col gap-3 rounded-xl border border-ink-200 bg-white p-6 shadow-sm"
            >
              <div>
                <p className="text-base font-semibold text-ink-900">{plan.name}</p>
                <p className="mt-1 text-2xl font-bold text-brand-600">
                  {plan.priceKrw.toLocaleString('ko-KR')}원
                  <span className="ml-1 text-sm font-normal text-ink-500">
                    / {plan.period === 'MONTHLY' ? '월' : plan.period}
                  </span>
                </p>
              </div>
              <ul className="flex flex-col gap-1 text-sm text-ink-700">
                {plan.benefits.map((benefit) => (
                  <li key={benefit}>· {benefit}</li>
                ))}
              </ul>
              {/* 🔴 위 § 참조 — 이 문장을 지우지 마라. */}
              <p className="mt-auto text-xs text-ink-500">{plan.note}</p>
            </li>
          ))}
        </ul>
      )}

      {authenticated ? null : (
        <div
          data-testid="membership-login-cta"
          className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-ink-200 bg-ink-50/40 p-6 text-center"
        >
          <p className="text-sm text-ink-700">
            가입하려면 로그인이 필요합니다. 결제와 구독 관리는 로그인 후 진행됩니다.
          </p>
          <Link
            href="/login?from=%2Fmembership"
            className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            로그인하고 가입하기
          </Link>
        </div>
      )}
    </section>
  );
}
