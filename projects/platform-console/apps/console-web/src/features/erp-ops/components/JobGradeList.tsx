'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  type CreateJobGradeInput,
  type UpdateJobGradeInput,
  type JobGradeListResponse,
  type ErpListQueryParams,
} from '../api/types';
import {
  useJobGrades,
  useCreateJobGrade,
  useUpdateJobGrade,
  useRetireJobGrade,
} from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';
import { useMasterWrite, type MasterWriteController } from './MasterWriteDialog';
import { JOB_GRADE_WRITE_CONFIG } from './master-write-configs';

/**
 * Job-grades list (TASK-PC-FE-010 / § 2.4.8) — paginated, producer-ordered by
 * `displayOrder` asc (no client re-sort). E2 honesty: retired rows visually
 * distinct but NEVER hidden. WRITE (TASK-PC-FE-048): 직급 추가 + per-row
 * 수정/폐기 when `writable`.
 */
export interface JobGradeListProps {
  initial?: JobGradeListResponse;
  writable?: boolean;
}

export function JobGradeList({ initial, writable = false }: JobGradeListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useJobGrades(query, initial);
  const create = useCreateJobGrade();
  const update = useUpdateJobGrade();
  const retire = useRetireJobGrade();
  const controller: MasterWriteController = {
    pending: create.isPending || update.isPending || retire.isPending,
    error: create.error ?? update.error ?? retire.error ?? null,
    create: (values, idem) =>
      create.mutateAsync({ input: values as unknown as CreateJobGradeInput, idempotencyKey: idem }),
    update: (id, values, idem) =>
      update.mutateAsync({ id, input: values as unknown as UpdateJobGradeInput, idempotencyKey: idem }),
    retire: (id, reason, idem) => retire.mutateAsync({ id, reason, idempotencyKey: idem }),
  };
  const { openCreate, openUpdate, openRetire, dialog } = useMasterWrite(
    controller,
    JOB_GRADE_WRITE_CONFIG,
    'erp-jobgrade',
  );

  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-jobgrades-heading">
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="erp-jobgrades-heading"
          className="text-lg font-medium text-foreground"
        >
          직급 (job-grades)
        </h2>
        {writable && (
          <Button
            variant="primary"
            onClick={openCreate}
            data-testid="erp-jobgrade-create"
          >
            직급 추가
          </Button>
        )}
      </div>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-jobgrades-empty"
        >
          표시할 직급이 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="erp-jobgrades-table">
            <caption className="sr-only">직급 목록 (displayOrder 오름차순)</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">정렬 (displayOrder)</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">유효기간</th>
                {writable && <th scope="col" className="p-2">작업</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((g, i) => {
                const retired = isRetired(g.effectivePeriod);
                return (
                  <tr
                    key={g.id}
                    data-testid={`erp-jobgrade-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{g.code}</td>
                    <td className="p-2">{g.name}</td>
                    <td className="p-2">{g.displayOrder ?? '—'}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-jobgrade-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(g.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={g.effectivePeriod} />
                    </td>
                    {writable && (
                      <td className="p-2">
                        <div className="flex gap-1">
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => openUpdate(g.id, `${g.code} · ${g.name}`)}
                            data-testid={`erp-jobgrade-edit-${i}`}
                          >
                            수정
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => openRetire(g.id, `${g.code} · ${g.name}`)}
                            data-testid={`erp-jobgrade-retire-${i}`}
                            className="text-destructive"
                          >
                            폐기
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="직급 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({ ...s, page: Math.max(0, (s.page ?? 0) - 1) }))
              }
              data-testid="erp-jobgrades-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-jobgrades-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))}
              data-testid="erp-jobgrades-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
      {writable && dialog}
    </section>
  );
}
