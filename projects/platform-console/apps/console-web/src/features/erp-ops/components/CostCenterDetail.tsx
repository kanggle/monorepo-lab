'use client';

import {
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  type CostCenter,
} from '../api/types';
import { useCostCenter, useDepartment } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';
import { RetiredReferenceBadge } from './RetiredReferenceBadge';

/**
 * Cost-center detail (TASK-PC-FE-010 / § 2.4.8).
 *
 * E1 reference integrity: the `departmentId` is resolved via
 * `useDepartment(...)`; a retired department reference surfaces a
 * `<RetiredReferenceBadge>`. NEVER silently sanitized.
 */
export interface CostCenterDetailProps {
  id: string;
  initial?: CostCenter;
}

export function CostCenterDetail({ id, initial }: CostCenterDetailProps) {
  const q = useCostCenter(id);
  const c = q.data ?? initial ?? null;
  const departmentQ = useDepartment(c?.departmentId ?? null);
  const departmentRetired = departmentQ.data?.status === 'RETIRED';

  if (!c) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="erp-costcenter-detail-loading"
      >
        비용센터 정보를 불러오는 중…
      </p>
    );
  }
  return (
    <section
      aria-labelledby="erp-costcenter-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="erp-costcenter-detail"
    >
      <h2
        id="erp-costcenter-detail-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        비용센터 상세
      </h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">코드</dt>
          <dd className="text-foreground">{c.code}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">이름</dt>
          <dd className="text-foreground">{c.name}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <span
              data-testid="erp-costcenter-status"
              className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
            >
              {labelForUnknownEnum(c.status, KNOWN_MASTER_STATUSES)}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">유효기간</dt>
          <dd className="text-foreground">
            <EffectivePeriodBadge period={c.effectivePeriod} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">소속 부서</dt>
          <dd className="text-foreground">
            {c.departmentId ? (
              <>
                <span data-testid="erp-costcenter-department-ref">
                  {c.departmentId}
                </span>
                {departmentQ.data && (
                  <span className="ml-1 text-muted-foreground">
                    ({departmentQ.data.name})
                  </span>
                )}
                {departmentRetired && (
                  <RetiredReferenceBadge reason="retired department" />
                )}
              </>
            ) : (
              '—'
            )}
          </dd>
        </div>
      </dl>
    </section>
  );
}
