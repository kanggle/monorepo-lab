import type { DelegationFactListResponse } from '../api/types';
import { DelegationScreen } from './DelegationScreen';
import { DelegationFactCard } from './DelegationFactCard';

/**
 * erp **위임** route screen (`/erp/delegation` — TASK-PC-FE-076 drill-in
 * split). Combines the two former 위임 sub-sections:
 *   - `<DelegationScreen>` (위임 관리 — write surface, client-driven,
 *     TASK-PC-FE-054; needs no server seed).
 *   - `<DelegationFactCard>` (위임 현황 — read-model delegation facts,
 *     read-only, TASK-PC-FE-055).
 */
export interface ErpDelegationScreenProps {
  initialDelegationFacts?: DelegationFactListResponse | null;
}

export function ErpDelegationScreen({
  initialDelegationFacts,
}: ErpDelegationScreenProps) {
  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-2 text-2xl font-semibold">
        ERP 위임
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        결재 권한 위임을 부여/회수(관리)하고, 현재 적용 중인 위임 현황을 조회
        합니다. 권한이 없는 작업은 실행 시 안내됩니다.
      </p>

      {/* 위임(관리) — write surface (client-driven). */}
      <DelegationScreen />

      {/* 위임 현황 — read-model delegation facts (read-only). */}
      <DelegationFactCard initial={initialDelegationFacts ?? undefined} />
    </section>
  );
}
