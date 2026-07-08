import { AccrualsSection } from './AccrualsSection';
import { SellerBalanceLookup } from './SellerBalanceLookup';
import { CommissionRateLookup } from './CommissionRateLookup';
import { SettlementPeriodsSection } from './SettlementPeriodsSection';
import type {
  AccrualsResponse,
  PeriodsResponse,
} from '../api/settlement-types';

/**
 * ecommerce settlement operations screen (TASK-PC-FE-221 — the 8th ecommerce-ops
 * facet). Orchestrator: it composes four self-contained client sections and owns
 * no state itself (a server component — lighter First Load; the interactive
 * sections carry the client boundary), mirroring the EcommerceOverview
 * server-compatible composition precedent.
 *
 * Sections:
 *   (a) 정산 라인 — accruals with sellerId/orderId filter + pagination (seeded,
 *       read-only append-only ledger — no manual accrual write);
 *   (b) 셀러 잔액 조회 — sellerId-driven balance lookup;
 *   (c) 수수료율 조회/설정 — sellerId-driven rate lookup + confirm-gated set (Phase B);
 *   (d) 정산 기간 — periods list (seeded) + open/close (Phase B) → per-period
 *       payouts drill (payout execute is confirm-gated on the detail page).
 *
 * The seed (page-0 accruals + periods) comes from the page's
 * `getSettlementsSectionState`; the lookups + mutations run on demand. All
 * mutations are confirm-gated; NO `Idempotency-Key`. Manual accrual write is
 * intentionally absent (event-stream only).
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
        셀러별 라인 단위 수수료 적립 · 잔액 · 수수료율 · 정산 기간/지급 조회와,
        수수료율 설정 · 기간 개시/마감 · 지급 실행(시뮬레이션) 운영. 정산 라인은
        이벤트 스트림에서만 적립되는 append-only 원장이라 수동 적립은 없습니다.
        모든 변경 작업은 확인(confirm) 게이트를 거칩니다.
      </p>

      <AccrualsSection initialAccruals={accruals} />
      <SellerBalanceLookup />
      <CommissionRateLookup />
      <SettlementPeriodsSection initialPeriods={periods} />
    </section>
  );
}
