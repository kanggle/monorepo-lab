'use client';

import { Button } from '@/shared/ui/Button';
import type { AuditPage, AuditRow } from '../api/types';
import { AuditRowCells, auditRowKey } from './AuditRowCells';

/**
 * Audit list table + pagination (TASK-PC-FE-149 — presentational, extracted
 * from `AuditScreen`). Controlled: the parent (`useAuditScreen`) owns all
 * state and passes derived rows + pagination + page-change handlers. Each
 * row's cells are rendered by `AuditRowCells` discriminated by `source`.
 * Render output / DOM / every `data-testid` is identical to the inlined
 * version.
 */

export interface AuditTableProps {
  rows: AuditRow[];
  data?: AuditPage;
  totalPages: number;
  prevDisabled: boolean;
  nextDisabled: boolean;
  onPrev: () => void;
  onNext: () => void;
}

export function AuditTable({
  rows,
  data,
  totalPages,
  prevDisabled,
  nextDisabled,
  onPrev,
  onNext,
}: AuditTableProps) {
  return (
    <>
      <table
        className="data-table"
        data-testid="audit-table"
      >
        <caption className="sr-only">감사 로그 통합 조회 결과</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              소스
            </th>
            <th scope="col" className="p-2">
              액션 / 이벤트
            </th>
            <th scope="col" className="p-2">
              행위자 / 계정
            </th>
            <th scope="col" className="p-2">
              대상 / 위치
            </th>
            <th scope="col" className="p-2">
              결과
            </th>
            <th scope="col" className="p-2">
              발생 시각 (UTC)
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => {
            const key = auditRowKey(row, i);
            return (
              <tr
                key={key}
                data-testid={`audit-row-${i}`}
                data-source={(row as { source: string }).source}
                className="border-b border-border"
              >
                <AuditRowCells row={row} />
              </tr>
            );
          })}
        </tbody>
      </table>

      <nav
        className="mt-4 flex items-center justify-between"
        aria-label="페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={prevDisabled}
          onClick={onPrev}
          data-testid="audit-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="audit-pageinfo"
        >
          {data
            ? `${data.page + 1} / ${totalPages} 페이지 · 총 ${data.totalElements}건`
            : '—'}
        </span>
        <Button
          variant="secondary"
          disabled={nextDisabled}
          onClick={onNext}
          data-testid="audit-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
