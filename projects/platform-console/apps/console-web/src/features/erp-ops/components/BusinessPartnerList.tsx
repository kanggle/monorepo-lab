'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  KNOWN_PARTNER_TYPES,
  labelForUnknownEnum,
  type CreateBusinessPartnerInput,
  type UpdateBusinessPartnerInput,
  type BusinessPartnerListResponse,
  type ErpListQueryParams,
} from '../api/types';
import {
  useBusinessPartners,
  useCreateBusinessPartner,
  useUpdateBusinessPartner,
  useRetireBusinessPartner,
} from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';
import { useMasterWrite, type MasterWriteController } from './MasterWriteDialog';
import { BUSINESS_PARTNER_WRITE_CONFIG } from './master-write-configs';

/**
 * Business-partners list (TASK-PC-FE-010 / § 2.4.8) — paginated;
 * `paymentTerms` is detail-only (confidential). E2 honesty: retired rows
 * visually distinct but NEVER hidden. WRITE (TASK-PC-FE-048): 거래처 추가 +
 * per-row 수정/폐기 when `writable`; `partnerType` is a static select.
 * (v1: `paymentTerms` not editable from this flat dialog — a follow-up.)
 */
export interface BusinessPartnerListProps {
  initial?: BusinessPartnerListResponse;
  writable?: boolean;
}

export function BusinessPartnerList({
  initial,
  writable = false,
}: BusinessPartnerListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useBusinessPartners(query, initial);
  const create = useCreateBusinessPartner();
  const update = useUpdateBusinessPartner();
  const retire = useRetireBusinessPartner();
  const controller: MasterWriteController = {
    pending: create.isPending || update.isPending || retire.isPending,
    error: create.error ?? update.error ?? retire.error ?? null,
    create: (values, idem) =>
      create.mutateAsync({ input: values as unknown as CreateBusinessPartnerInput, idempotencyKey: idem }),
    update: (id, values, idem) =>
      update.mutateAsync({ id, input: values as unknown as UpdateBusinessPartnerInput, idempotencyKey: idem }),
    retire: (id, reason, idem) => retire.mutateAsync({ id, reason, idempotencyKey: idem }),
  };
  const { openCreate, openUpdate, openRetire, dialog } = useMasterWrite(
    controller,
    BUSINESS_PARTNER_WRITE_CONFIG,
    'erp-businesspartner',
  );

  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-businesspartners-heading">
      <div className="mb-3 flex items-center justify-between">
        <h2
          id="erp-businesspartners-heading"
          className="text-lg font-medium text-foreground"
        >
          거래처 (business-partners)
        </h2>
        {writable && (
          <Button
            variant="primary"
            onClick={openCreate}
            data-testid="erp-businesspartner-create"
          >
            거래처 추가
          </Button>
        )}
      </div>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-businesspartners-empty"
        >
          표시할 거래처가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="erp-businesspartners-table"
          >
            <caption className="sr-only">거래처 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">유형</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">유효기간</th>
                {writable && <th scope="col" className="p-2">작업</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((p, i) => {
                const retired = isRetired(p.effectivePeriod);
                return (
                  <tr
                    key={p.id}
                    data-testid={`erp-businesspartner-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{p.code}</td>
                    <td className="p-2">{p.name}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-businesspartner-type-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(p.partnerType, KNOWN_PARTNER_TYPES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-businesspartner-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(p.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={p.effectivePeriod} />
                    </td>
                    {writable && (
                      <td className="p-2">
                        <div className="flex gap-1">
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => openUpdate(p.id, `${p.code} · ${p.name}`)}
                            data-testid={`erp-businesspartner-edit-${i}`}
                          >
                            수정
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => openRetire(p.id, `${p.code} · ${p.name}`)}
                            data-testid={`erp-businesspartner-retire-${i}`}
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
            aria-label="거래처 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({ ...s, page: Math.max(0, (s.page ?? 0) - 1) }))
              }
              data-testid="erp-businesspartners-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-businesspartners-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))}
              data-testid="erp-businesspartners-next"
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
