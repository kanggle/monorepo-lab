import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { masterRefLabel } from '@/shared/lib/master-ref-label';
import {
  suggestionStatusTone,
  canApprove,
  canDismiss,
  type Suggestion,
} from '../api/types';

type ActionKind = 'approve' | 'dismiss';

/** A server-issued id (UUID) rather than a business code. */
const UUID_SHAPE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * 「공급사」 칸의 참조 대상 — `TASK-MONO-683`.
 *
 * 제안의 `supplierId` 는 계약상 **공급사 코드**다(ADR-MONO-050 D9 — wms 가 그 값을 코드로
 * 찾는다). 그래서 값 자체가 `code` 이고, 이름은 이 응답에 없다(`codeName` 이 코드만 그린다).
 * 🔴 UUID 모양이면 코드가 아니다 — 683 이전 시드가 넣던 값이고, 확정되면 wms 가 DLT 로 버린다.
 *    그 값을 «코드» 로 그리면 결함이 정상처럼 보이므로 `이름 확인 불가` 로 보이게 하고, 원문은
 *    `title` 에만 싣는다(창고 칸과 같은 규칙).
 */
function supplierCodeRef(supplierId: string | null | undefined) {
  if (!supplierId || UUID_SHAPE.test(supplierId)) return null;
  return { code: supplierId };
}

/**
 * The suggestion table + pagination for {@link ReplenishmentScreen} (TASK-PC-FE-190
 * split) — rendered only in the loaded/non-empty branch (the parent owns the
 * forbidden / rate-limited / degraded / empty branches). Each row shows the
 * `triggerAvailableQty` that explains WHY it was suggested; the approve / dismiss
 * buttons gate on `canApprove` / `canDismiss` (+ any pending action). Pure
 * presentation — markup + testids preserved verbatim (`repl-table`,
 * `repl-row-*`, `repl-approve-*`, `repl-dismiss-*`, `repl-prev/next/pageinfo`).
 *
 * Pagination note: prev-disabled keys off the REQUESTED `queryPage` while
 * pageinfo + next-disabled key off the RETURNED `dataPage` / `totalElements` —
 * exactly as the pre-split screen did.
 */
export function ReplenishmentTable({
  rows,
  queryPage,
  dataPage,
  totalPages,
  totalElements,
  onPrev,
  onNext,
  onAction,
  actionPending,
}: {
  rows: Suggestion[];
  queryPage: number;
  dataPage: number;
  totalPages: number;
  totalElements: number;
  onPrev: () => void;
  onNext: () => void;
  onAction: (kind: ActionKind, suggestion: Suggestion) => void;
  actionPending: boolean;
}) {
  return (
    <>
      <table className="mb-3 data-table" data-testid="repl-table">
        <caption className="sr-only">보충 추천 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              SKU
            </th>
            <th scope="col" className="p-2">
              창고
            </th>
            <th scope="col" className="p-2">
              공급사
            </th>
            <th scope="col" className="p-2">
              추천 수량
            </th>
            <th scope="col" className="p-2">
              트리거 가용재고
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              PO
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((s, i) => {
            const approveOk = canApprove(s.status);
            const dismissOk = canDismiss(s.status);
            return (
              <tr
                key={s.id}
                data-testid={`repl-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{s.skuCode ?? '—'}</td>
                {/* 🔴 「창고」 칸은 참조다 — `TASK-PC-FE-277`. 생산자가 `warehouseCode` 를
                    이미 싣고 있으므로 여기서 조회를 새로 하지 않는다. `data-master-ref` 는
                    276 의 회귀 가드가 읽는 **모집단 선언**이고, 원본 id 는 `title` 에만
                    싣는다(보이는 텍스트가 아니므로 UUID 가드가 안 문다). */}
                <td
                  className="p-2"
                  data-master-ref="suggestion.warehouseId"
                  title={s.warehouseId ?? undefined}
                >
                  {masterRefLabel(
                    s.warehouseId,
                    s.warehouseCode ? { code: s.warehouseCode } : null,
                  )}
                </td>
                <td
                  className="p-2"
                  data-master-ref="suggestion.supplierId"
                  title={s.supplierId ?? undefined}
                >
                  {masterRefLabel(s.supplierId, supplierCodeRef(s.supplierId))}
                </td>
                <td className="p-2">{s.suggestedQty ?? '—'}</td>
                <td className="p-2" data-testid={`repl-row-trigger-${i}`}>
                  {s.triggerAvailableQty ?? '—'}
                </td>
                <td className="p-2" data-testid={`repl-row-status-${i}`}>
                  <StatusBadge tone={suggestionStatusTone(s.status)}>
                    {s.status ?? '—'}
                  </StatusBadge>
                </td>
                <td className="p-2">{s.materializedPoId ?? '—'}</td>
                <td className="p-2">
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      onClick={() => onAction('approve', s)}
                      disabled={!approveOk || actionPending}
                      data-testid={`repl-approve-${i}`}
                    >
                      승인
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onAction('dismiss', s)}
                      disabled={!dismissOk || actionPending}
                      data-testid={`repl-dismiss-${i}`}
                    >
                      기각
                    </Button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <nav
        className="mb-8 flex items-center justify-between"
        aria-label="보충 추천 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={queryPage <= 0}
          onClick={onPrev}
          data-testid="repl-prev"
        >
          이전
        </Button>
        <span className="text-sm text-muted-foreground" data-testid="repl-pageinfo">
          {`${dataPage + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={dataPage + 1 >= totalPages}
          onClick={onNext}
          data-testid="repl-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
