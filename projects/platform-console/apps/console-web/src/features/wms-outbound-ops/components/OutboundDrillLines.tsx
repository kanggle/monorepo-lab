'use client';

import { masterRefLabel } from '@/shared/lib/master-ref-label';
import type { OutboundOrderLine } from '../api/types';

/**
 * Order-drill line table (TASK-PC-FE-198 split) — the `<table>` of the drilled
 * order's lines (line no, sku, lot, ordered qty). Pure presentation; the
 * {@link OutboundOrderDrill} container passes the loaded `detail.lines`.
 */
export interface OutboundDrillLinesProps {
  lines: OutboundOrderLine[];
}

export function OutboundDrillLines({ lines }: OutboundDrillLinesProps) {
  return (
    <table
      className="mb-4 data-table"
      data-testid="outbound-drill-lines"
    >
      <caption className="sr-only">주문 라인</caption>
      <thead>
        <tr className="border-b border-border text-left">
          <th scope="col" className="p-2">
            라인
          </th>
          <th scope="col" className="p-2">
            SKU
          </th>
          <th scope="col" className="p-2">
            로트
          </th>
          <th scope="col" className="p-2">
            주문 수량
          </th>
        </tr>
      </thead>
      <tbody>
        {lines.map((l, i) => (
          <tr
            key={l.orderLineId}
            data-testid={`outbound-line-${i}`}
            className="border-b border-border"
          >
            <td className="p-2">{l.lineNo ?? i + 1}</td>
            {/* 🔴 TASK-MONO-659 — outbound 는 인입 시점에 `skuCode` 로 조회해서 UUID 를
                얻는다. 즉 코드를 쥐었다가 버리고 있었고, 이 칸이 그 결과였다. */}
            <td
              className="p-2"
              data-master-ref="orderLine.skuId"
              title={l.skuId ?? undefined}
            >
              {masterRefLabel(l.skuId, l.skuCode ? { code: l.skuCode } : null)}
            </td>
            <td className="p-2">{l.lotId ?? '—'}</td>
            <td className="p-2">{l.qtyOrdered}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
