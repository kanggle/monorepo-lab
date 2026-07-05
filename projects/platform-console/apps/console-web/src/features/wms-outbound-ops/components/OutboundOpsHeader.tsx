'use client';

/**
 * Heading band of the wms outbound screen (TASK-PC-FE-198 split) — the section
 * `<h1>` (id `wms-outbound-heading`, referenced by the container's
 * `aria-labelledby`) + the one-line workflow subtitle. Pure presentation, no
 * props: the `OutboundOpsScreen` container owns the section wrapper + all
 * orchestration. Markup + the heading id preserved verbatim.
 */
export function OutboundOpsHeader() {
  return (
    <>
      <h1
        id="wms-outbound-heading"
        className="mb-2 text-2xl font-semibold"
      >
        WMS 출고
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        출고 주문 목록 · 주문 상세(라인 + saga) · 피킹 → 패킹 → 출고 확정.
      </p>
    </>
  );
}
