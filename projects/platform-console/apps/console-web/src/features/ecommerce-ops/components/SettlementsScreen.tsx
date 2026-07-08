import { AccrualsSection } from './AccrualsSection';
import { SellerBalanceLookup } from './SellerBalanceLookup';
import { CommissionRateLookup } from './CommissionRateLookup';
import { SettlementPeriodsSection } from './SettlementPeriodsSection';
import type {
  AccrualsResponse,
  PeriodsResponse,
} from '../api/settlement-types';

/**
 * ecommerce settlement operations screen (TASK-PC-FE-221 Phase A — the 8th
 * ecommerce-ops facet). READ-ONLY orchestrator: it composes four self-contained
 * client sections and owns no state itself (a server component — lighter First
 * Load; the interactive sections carry the client boundary), mirroring the
 * EcommerceOverview server-compatible composition precedent.
 *
 * Sections:
 *   (a) 정산 라인 — accruals with sellerId/orderId filter + pagination (seeded);
 *   (b) 셀러 잔액 조회 — sellerId-driven balance lookup;
 *   (c) 수수료율 조회 — sellerId-driven commission-rate lookup;
 *   (d) 정산 기간 — periods list (seeded) → per-period payouts drill.
 *
 * The seed (page-0 accruals + periods) comes from the page's
 * `getSettlementsSectionState`; the lookups query on demand. Mutations (rate PUT,
 * period open/close, payout execute) are Phase B — this surface is view-only.
 */
export interface SettlementsScreenProps {
  accruals: AccrualsResponse;
  periods: PeriodsResponse;
}

export function SettlementsScreen({ accruals, periods }: SettlementsScreenProps) {
  return (
    <section aria-labelledby="ecommerce-settlements-heading">
      <h1
        id="ecommerce-settlements-heading"
        className="mb-2 text-2xl font-semibold"
      >
        E-Commerce 정산
      </h1>
      <p className="mb-8 text-sm text-muted-foreground">
        셀러별 라인 단위 수수료 적립 · 잔액 · 수수료율 · 정산 기간/지급 조회.
        정산 라인은 이벤트 스트림에서만 적립되는 append-only 원장입니다(수동 적립
        없음). 수수료율 설정 · 기간 개시/마감 · 지급 실행 등 변경 작업은 콘솔의
        현재 범위 밖입니다.
      </p>

      <AccrualsSection initialAccruals={accruals} />
      <SellerBalanceLookup />
      <CommissionRateLookup />
      <SettlementPeriodsSection initialPeriods={periods} />
    </section>
  );
}
