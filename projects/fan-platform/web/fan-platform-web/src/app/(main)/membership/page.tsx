import { isAuthenticated } from '@/shared/auth/session';
import { MembershipMemberPanel } from '@/features/membership';
import {
  readFanPublicData,
  PublicMembershipPlans,
  ProvenanceBanner,
} from '@/features/public-browse';
import type { MembershipTier } from '@/entities/membership';

function parseTier(raw: string | undefined): MembershipTier | undefined {
  return raw === 'MEMBERS_ONLY' || raw === 'PREMIUM' ? raw : undefined;
}

/**
 * 멤버십 — **공개 소개 + (로그인 시) 회원 패널**.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴🔴 이 페이지가 갈라진 이유 — 예전 판은 «공개 화면» 이 아니라 **개인 데이터 화면**이었다
 * ─────────────────────────────────────────────────────────────────────────
 * 예전 `/membership` 은 `getMemberships(accessToken)` 을 무조건 불렀다. 그 엔드포인트
 * (`GET /api/v1/memberships`)가 돌려주는 것은 요금제 목록이 아니라 **현재 사용자의 구독**
 * 이다 — DTO 에 accountId 와 결제 상태가 들어 있다. 그러니 이 경로를 그대로 공개로
 * 열었다면 공개 화면이 개인 데이터 엔드포인트를 때리게 됐을 것이다.
 *
 * ⇒ 축을 둘로 나눈다:
 *     · 공개 절반 — 요금제·혜택·가격·주의문구. 출처는 저장본의 `membershipPlans` 이고,
 *                   그것은 백엔드에서 뽑은 것이 **아니라** 저장소가 쓴 `authored` 안내다.
 *     · 회원 절반 — `MembershipMemberPanel`. 로그인했을 때만 **엘리먼트가 만들어진다.**
 *
 * 🔴 `/membership/history` 는 갈라지지 않는다 — 전부 인증 필요다. 그래서
 *    `shared/auth/public-paths.ts` 에서 `/membership` 은 **정확 일치**로 열려 있고,
 *    하위 경로는 안 열린다. 그 한 칸이 이 분리의 실제 집행 지점이다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export default async function MembershipPage({
  searchParams,
}: {
  searchParams: Promise<{ tier?: string }>;
}) {
  const { tier } = await searchParams;
  const highlightTier = parseTier(tier);

  const result = await readFanPublicData();
  const authed = await isAuthenticated();

  return (
    <section className="flex flex-col gap-8">
      <header>
        <h1 className="text-2xl font-bold text-ink-900">멤버십</h1>
        <p className="text-sm text-ink-600">
          멤버 전용·프리미엄 콘텐츠를 위한 구독입니다.
        </p>
      </header>

      {authed ? <MembershipMemberPanel highlightTier={highlightTier} /> : null}

      <PublicMembershipPlans plans={result.data.membershipPlans} authenticated={authed} />

      <ProvenanceBanner
        source={result.envelope.source}
        generatedAt={result.envelope.generatedAt}
        degraded={result.degraded}
      />
    </section>
  );
}
