import type { IamOverviewState } from '../api/overview-state';
import { OperatorsCard, AccountsCard } from './IamOverviewSummaryCards';
import { AuditCard } from './IamOverviewAuditCard';
import { NoTenantNoticeBody } from '@/widgets/no-tenant-notice';

/**
 * IAM operator **overview snapshot** presentation (TASK-PC-FE-180). Server
 * component — STRICTLY READ-ONLY, no `'use client'`. Renders the
 * `getIamOverviewState` fan-out: three count/summary cards (each a quick-launch
 * `Link` to its IAM screen), the operator ACTIVE/SUSPENDED split, and the recent
 * audit·security mini-list. A non-`ok` cell renders a compact "점검 필요" /
 * "권한 없음" placeholder instead of a number (never blanks); a `noActiveTenant`
 * state renders a single page-level tenant gate (mirror of the sibling IAM
 * screens). The static role/permission guide now lives at `/iam/guide`.
 *
 * TASK-PC-FE-212: the count/summary cards ({@link OperatorsCard}/
 * {@link AccountsCard}), the audit·security mini-list ({@link AuditCard}), and
 * the shared presentational primitives / label maps live in sibling files
 * (`IamOverviewSummaryCards` / `IamOverviewAuditCard` / `IamOverviewPrimitives`
 * / `overview-labels`). This container keeps the tenant gate + section layout
 * orchestration (behavior-preserving split).
 */
export function IamOverviewScreen({ state }: { state: IamOverviewState }) {
  if (state.noActiveTenant) {
    // TASK-PC-FE-301: `NoTenantNoticeBody` (not the async `NoTenantNotice`
    // wrapper) — this component is sync/presentational and unit-tested with
    // the client-side RTL renderer, so `state.tenantNoticeKind` is resolved
    // server-side by `getIamOverviewState()` and threaded down as a plain
    // prop. `?? 'select'` is defensive only (every real caller sets it
    // whenever `noActiveTenant` is true).
    return (
      <NoTenantNoticeBody
        kind={state.tenantNoticeKind ?? 'select'}
        testId="iam-overview-no-tenant"
        description={
          <>
            IAM 개요는 선택한 테넌트 범위의 운영자·계정·감사 현황을 보여줍니다.
            상단 테넌트 스위처에서 테넌트를 선택하면 현황이 표시됩니다.
          </>
        }
      />
    );
  }

  return (
    <section data-testid="iam-overview" aria-label="IAM 운영 개요">
      <div className="mb-8">
        <div className="mb-3 flex flex-wrap gap-3">
          <OperatorsCard summary={state.operators} />
          <AccountsCard summary={state.accounts} />
        </div>
        <AuditCard
          status={state.audit.status}
          total={state.audit.total}
          recent={state.audit.recent}
        />
      </div>
    </section>
  );
}
