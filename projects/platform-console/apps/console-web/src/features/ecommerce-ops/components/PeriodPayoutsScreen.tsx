'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { DetailHeader } from './DetailHeader';
import {
  usePeriodPayouts,
  useExecutePayouts,
} from '../hooks/use-ecommerce-settlements';
import {
  SETTLEMENT_DEFAULT_PAGE_SIZE,
  type PayoutsResponse,
  type PayoutsListParams,
} from '../api/settlement-types';
import { PeriodPayoutsTable } from './PeriodPayoutsTable';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Settlement period detail (TASK-PC-FE-221) — the per-period payouts list. There
 * is no single GET period-by-id read; the period is identified by `periodId` and
 * the payouts are its content. Owns pagination + query; the server-rendered
 * page-0 result seeds the table. Header follows the ecommerce-ops DetailHeader
 * ghost-back convention.
 *
 * Phase B: a confirm-gated "지급 실행(시뮬레이션)" action (SIMULATED — no real
 * disbursement). The detail has no period-status read, so an OPEN period surfaces
 * the producer's `409 PERIOD_NOT_CLOSED` INLINE in the confirm dialog (never a
 * fake 500/degrade); on success the payouts refetch to show PAID/FAILED.
 *
 * Resilience (§ 2.5): 403 → inline forbidden; 503/timeout → degraded.
 */
export interface PeriodPayoutsScreenProps {
  periodId: string;
  initialPayouts: PayoutsResponse;
}

export function PeriodPayoutsScreen({
  periodId,
  initialPayouts,
}: PeriodPayoutsScreenProps) {
  const [query, setQuery] = useState<PayoutsListParams>({
    page: 0,
    size: initialPayouts.size || SETTLEMENT_DEFAULT_PAGE_SIZE,
  });
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [execError, setExecError] = useState<string | null>(null);

  const seeded = (query.page ?? 0) === 0;
  const listQ = usePeriodPayouts(
    periodId,
    query,
    seeded ? initialPayouts : undefined,
  );
  const data = seeded ? listQ.data ?? initialPayouts : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  const executeM = useExecutePayouts();

  const rows = data?.items ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  function runExecute() {
    setExecError(null);
    executeM.mutate(periodId, {
      onSuccess: () => setConfirmOpen(false),
      onError: (err: unknown) => {
        const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
        setExecError(messageForCode(code, '지급을 실행하지 못했습니다.'));
      },
    });
  }

  return (
    <section aria-labelledby="settlements-period-heading">
      <DetailHeader
        headingId="settlements-period-heading"
        title={`정산 기간 지급 내역 · ${periodId}`}
        backHref="/ecommerce/settlements"
        backTestId="settlements-period-back"
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setExecError(null);
              setConfirmOpen(true);
            }}
            data-testid="settlements-payouts-execute"
          >
            지급 실행 (시뮬레이션)
          </Button>
        }
      />

      {forbidden ? (
        <div
          role="status"
          data-testid="settlements-payouts-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="settlements-payouts-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          지급 내역을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속
          사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-payouts-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-payouts-empty"
        >
          이 기간에 지급 내역이 없습니다. (OPEN 기간은 마감 후 지급이 생성됩니다.)
        </p>
      ) : (
        <PeriodPayoutsTable
          rows={rows}
          pagination={{
            prevDisabled: (query.page ?? 0) <= 0,
            nextDisabled: (data?.page ?? 0) + 1 >= totalPages,
            pageInfo: `${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`,
            onPrev: () =>
              setQuery((q) => ({
                ...q,
                page: Math.max(0, (q.page ?? 0) - 1),
              })),
            onNext: () => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
          }}
        />
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="지급을 실행할까요? (시뮬레이션)"
        description={`기간 "${periodId}"의 PENDING 지급을 실행합니다. 실제 송금은 발생하지 않는 시뮬레이션이며, 이미 PAID 인 지급은 변경되지 않습니다. 기간이 마감(CLOSED)되지 않았다면 실행할 수 없습니다.`}
        confirmLabel="지급 실행"
        pending={executeM.isPending}
        errorMessage={execError}
        onConfirm={runExecute}
        onCancel={() => {
          setConfirmOpen(false);
          setExecError(null);
        }}
      />
    </section>
  );
}
