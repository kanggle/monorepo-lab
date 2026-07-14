'use client';

import {
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  masterStatusTone,
  type Department,
} from '../api/types';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { useDepartment } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';
import { RetiredReferenceBadge } from './RetiredReferenceBadge';

/**
 * Department detail (TASK-PC-FE-010 / § 2.4.8).
 *
 * E1 reference integrity: if the resolved `parentId` (via
 * `useDepartment` for the parent) is `RETIRED`, the
 * `<RetiredReferenceBadge>` surfaces next to the parent label. The
 * test pins this honest surfacing.
 *
 * STRICTLY READ-ONLY.
 */
export interface DepartmentDetailProps {
  id: string;
  initial?: Department;
}

export function DepartmentDetail({ id, initial }: DepartmentDetailProps) {
  const q = useDepartment(id);
  const d = q.data ?? initial ?? null;
  // E1: resolve the parent (if any) so we can surface a retired
  // cross-reference honestly.
  const parentQ = useDepartment(d?.parentId ?? null);
  const parent = parentQ.data ?? null;
  const parentRetired = parent?.status === 'RETIRED';

  if (!d) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="erp-department-detail-loading"
      >
        부서 정보를 불러오는 중…
      </p>
    );
  }

  return (
    <section
      aria-labelledby="erp-department-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="erp-department-detail"
    >
      <h2
        id="erp-department-detail-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        부서 상세
      </h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">코드</dt>
          <dd className="text-foreground" data-testid="erp-department-code">
            {d.code}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">이름</dt>
          <dd className="text-foreground">{d.name}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <StatusBadge
              tone={masterStatusTone(d.status)}
              data-testid="erp-department-status"
            >
              {labelForUnknownEnum(d.status, KNOWN_MASTER_STATUSES)}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">유효기간</dt>
          <dd className="text-foreground">
            <EffectivePeriodBadge period={d.effectivePeriod} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상위 부서</dt>
          <dd className="text-foreground">
            {d.parentId ? (
              <>
                <span data-testid="erp-department-parentid">{d.parentId}</span>
                {parent && (
                  <span className="ml-1 text-muted-foreground">
                    ({parent.name})
                  </span>
                )}
                {parentRetired && (
                  <RetiredReferenceBadge reason="retired department" />
                )}
              </>
            ) : (
              '— (root)'
            )}
          </dd>
        </div>
      </dl>
    </section>
  );
}
