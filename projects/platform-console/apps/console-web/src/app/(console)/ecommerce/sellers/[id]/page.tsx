import Link from 'next/link';
import { notFound } from 'next/navigation';
import {
  getSellerDetailSectionState,
  SellerDetail,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../products/_eligibility';

export const dynamic = 'force-dynamic';

/**
 * Decode the dynamic `[id]` route segment to the raw seller_id value.
 *
 * Next.js delivers the segment percent-encoded for seller ids that carry
 * non-ASCII / space characters (e.g. `셀러 1`, registered via the in-console
 * seller form). `getSeller()` re-encodes the value for the upstream path, so
 * passing the still-encoded segment double-encodes it (`%EC..` → `%25EC..`);
 * Spring StrictHttpFirewall then rejects the `%25`-bearing path with `400`,
 * which the section maps to a spurious "일시적으로 불러올 수 없습니다" degrade
 * (TASK-PC-FE-133). Decoding here yields the raw id so `getSeller()` encodes
 * exactly once. ASCII ids are unaffected (decode is a no-op); a malformed
 * encoding falls back to the raw segment rather than throwing.
 */
function decodeSellerId(segment: string): string {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

/**
 * ecommerce seller DETAIL route (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10 7th area).
 * Server component: eligibility pre-flight → seeded detail read → degrade
 * waterfall (registryDegraded → notEligible → forbidden → notFound → degraded
 * → happy). Missing/cross-tenant seller → notFound().
 */
export default async function SellerDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: rawId } = await params;
  const id = decodeSellerId(rawId);
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">셀러 상세</h1>
  );

  const note = (testid: string, msg: string) => (
    <section>
      {heading}
      <div
        role="status"
        data-testid={testid}
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        {msg}
      </div>
      <Link
        href="/ecommerce/sellers"
        className="mt-4 inline-block text-sm underline"
      >
        목록으로
      </Link>
    </section>
  );

  if (registryDegraded) {
    return note(
      'seller-degraded',
      'ecommerce 셀러 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  const state = await getSellerDetailSectionState(eligible, id);

  if (state.notEligible) {
    return note(
      'seller-not-eligible',
      'ecommerce 셀러 운영 화면에 대한 접근 권한이 없습니다. 운영자 관리자에게 문의하세요.',
    );
  }
  if (state.forbidden) {
    return note(
      'seller-forbidden',
      '이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)',
    );
  }
  if (state.notFound) {
    notFound();
  }
  if (state.degraded || !state.detail) {
    return note(
      'seller-degraded',
      'ecommerce 셀러 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  return <SellerDetail seller={state.detail} />;
}
