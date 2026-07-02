'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  masterStatusTone,
  type CreateCostCenterInput,
  type UpdateCostCenterInput,
  type CostCenterListResponse,
  type ErpListQueryParams,
} from '../api/types';
import {
  useCostCenters,
  useCreateCostCenter,
  useUpdateCostCenter,
  useRetireCostCenter,
} from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';
import {
  useMasterWrite,
  type MasterWriteController,
  type MasterWriteDialogProps,
} from './MasterWriteDialog';
import { COST_CENTER_WRITE_CONFIG } from './master-write-configs';

/**
 * Cost-centers list (TASK-PC-FE-010 / § 2.4.8) — paginated; references a
 * department. E2 honesty: retired rows visually distinct but NEVER hidden.
 * WRITE (TASK-PC-FE-048): 비용센터 추가 + per-row 수정/폐기 when `writable`; the
 * 부서 FK is a dropdown sourced from `optionSources.departments`.
 */
export interface CostCenterListProps {
  initial?: CostCenterListResponse;
  writable?: boolean;
  optionSources?: MasterWriteDialogProps['optionSources'];
}

export function CostCenterList({
  initial,
  writable = false,
  optionSources,
}: CostCenterListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useCostCenters(query, initial);
  const create = useCreateCostCenter();
  const update = useUpdateCostCenter();
  const retire = useRetireCostCenter();
  const controller: MasterWriteController = {
    pending: create.isPending || update.isPending || retire.isPending,
    error: create.error ?? update.error ?? retire.error ?? null,
    create: (values, idem) =>
      create.mutateAsync({ input: values as unknown as CreateCostCenterInput, idempotencyKey: idem }),
    update: (id, values, idem) =>
      update.mutateAsync({ id, input: values as unknown as UpdateCostCenterInput, idempotencyKey: idem }),
    retire: (id, reason, idem) => retire.mutateAsync({ id, reason, idempotencyKey: idem }),
  };
  const { openCreate, openUpdate, openRetire, dialog } = useMasterWrite(
    controller,
    COST_CENTER_WRITE_CONFIG,
    'erp-costcenter',
    optionSources,
  );

  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-costcenters-heading">
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="erp-costcenters-heading"
          className="text-lg font-medium text-foreground"
        >
          비용센터 (cost-centers)
        </h2>
        {writable && (
          <Button
            variant="primary"
            onClick={openCreate}
            data-testid="erp-costcenter-create"
          >
            비용센터 추가
          </Button>
        )}
      </div>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-costcenters-empty"
        >
          표시할 비용센터가 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="erp-costcenters-table">
            <caption className="sr-only">비용센터 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">소속 부서</th>
                <th scope="col" className="p-2">유효기간</th>
                {writable && <th scope="col" className="p-2">작업</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((c, i) => {
                const retired = isRetired(c.effectivePeriod);
                return (
                  <tr
                    key={c.id}
                    data-testid={`erp-costcenter-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{c.code}</td>
                    <td className="p-2">{c.name}</td>
                    <td className="p-2">
                      <StatusBadge
                        tone={masterStatusTone(c.status)}
                        data-testid={`erp-costcenter-status-${i}`}
                      >
                        {labelForUnknownEnum(c.status, KNOWN_MASTER_STATUSES)}
                      </StatusBadge>
                    </td>
                    <td className="p-2">{c.departmentId ?? '—'}</td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={c.effectivePeriod} />
                    </td>
                    {writable && (
                      <td className="p-2">
                        <div className="flex gap-1">
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => openUpdate(c.id, `${c.code} · ${c.name}`)}
                            data-testid={`erp-costcenter-edit-${i}`}
                          >
                            수정
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => openRetire(c.id, `${c.code} · ${c.name}`)}
                            data-testid={`erp-costcenter-retire-${i}`}
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
            aria-label="비용센터 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({ ...s, page: Math.max(0, (s.page ?? 0) - 1) }))
              }
              data-testid="erp-costcenters-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-costcenters-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))}
              data-testid="erp-costcenters-next"
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
