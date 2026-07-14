'use client';

import {
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  masterStatusTone,
  type JobGrade,
} from '../api/types';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { useJobGrade } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Job-grade detail (TASK-PC-FE-010 / § 2.4.8) — leaf master (no
 * outgoing references in the v1 reference graph).
 */
export interface JobGradeDetailProps {
  id: string;
  initial?: JobGrade;
}

export function JobGradeDetail({ id, initial }: JobGradeDetailProps) {
  const q = useJobGrade(id);
  const g = q.data ?? initial ?? null;
  if (!g) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="erp-jobgrade-detail-loading"
      >
        직급 정보를 불러오는 중…
      </p>
    );
  }
  return (
    <section
      aria-labelledby="erp-jobgrade-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="erp-jobgrade-detail"
    >
      <h2
        id="erp-jobgrade-detail-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        직급 상세
      </h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">코드</dt>
          <dd className="text-foreground">{g.code}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">이름</dt>
          <dd className="text-foreground">{g.name}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">정렬 (displayOrder)</dt>
          <dd className="text-foreground">{g.displayOrder ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <StatusBadge
              tone={masterStatusTone(g.status)}
              data-testid="erp-jobgrade-status"
            >
              {labelForUnknownEnum(g.status, KNOWN_MASTER_STATUSES)}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">유효기간</dt>
          <dd className="text-foreground">
            <EffectivePeriodBadge period={g.effectivePeriod} />
          </dd>
        </div>
      </dl>
    </section>
  );
}
