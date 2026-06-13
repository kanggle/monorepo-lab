'use client';

import type { EmployeeOrgViewListResponse } from '../api/types';
import { AsOfPicker } from './AsOfPicker';
import { EmployeeOrgViewCard } from './EmployeeOrgViewCard';

/**
 * erp **통합 조회** route screen (`/erp/orgview` — TASK-PC-FE-076 drill-in
 * split; the read-model org-view slice of the former `ErpOpsScreen`,
 * TASK-PC-FE-049/069). The integrated read-model employee org-view card
 * (read-only, eventually-consistent SECONDARY projection of the
 * authoritative masterdata). The `<AsOfPicker>` controls the `?asOf=`
 * URL param that threads through the org-view query.
 */
export interface ErpOrgViewScreenProps {
  initialEmployeeOrgViews?: EmployeeOrgViewListResponse | null;
}

export function ErpOrgViewScreen({
  initialEmployeeOrgViews,
}: ErpOrgViewScreenProps) {
  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-2 text-2xl font-semibold">
        ERP 통합 조회
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        부서·직원·직급·비용센터를 결합한 조직도 read-model 조회 (읽기 전용,
        eventually-consistent). 권위 데이터는 「마스터」 화면에서 조회합니다.
      </p>

      <AsOfPicker />

      <EmployeeOrgViewCard initial={initialEmployeeOrgViews ?? undefined} />
    </section>
  );
}
