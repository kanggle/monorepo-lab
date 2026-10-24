import type { IamOverviewState } from '../api/overview-state';
import { OperatorsCard, AccountsCard } from './IamOverviewSummaryCards';
import { AuditCard } from './IamOverviewAuditCard';
import { NoTenantNoticeBody } from '@/widgets/no-tenant-notice';
import { PerformanceStrip } from '@/shared/ui/PerformanceStrip';
import type { MetricsPeriod } from '@/shared/lib/kst-period';

/** `GET /api/admin/metrics` keys (admin-api.md) → screen names. */
export const IAM_PERFORMANCE_LABELS: Readonly<Record<string, string>> = {
  loginSuccess: '로그인 성공',
  loginFailure: '로그인 실패',
  accountLocked: '계정 잠금',
  newAccount: '신규 계정',
};

/**
 * What the four numbers cover (TASK-MONO-811). Consumer-pool accounts
 * (ADR-MONO-078) record their logins and locks under the pool, not under the
 * site, so those two drop out here; a consumer who joins this site is a new
 * account of this site. Wording mirrored in console-integration-contract
 * § 2.4.3.1.
 */
export const IAM_PERFORMANCE_SCOPE_NOTE =
  '로그인 성공 · 실패와 계정 잠금에는 소비자 풀 계정이 빠집니다(그 기록은 이 사이트가 아니라 풀에 남습니다). 신규 계정에는 이 사이트로 가입한 소비자가 포함됩니다.';

function periodHrefs(): Readonly<Record<MetricsPeriod, string>> {
  return { week: '/iam?period=week', month: '/iam?period=month' };
}

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
 *
 * TASK-MONO-811 — the «실적» strip (ADR-MONO-084 P6) is the first block:
 * 로그인 성공 · 로그인 실패 · 계정 잠금 · 신규 계정, with a one-line scope note
 * under it. Its cards are not links (no list filters by those event times).
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
      {/* «실적» strip (ADR-MONO-084 P6, TASK-MONO-811) — first block. */}
      <PerformanceStrip
        cell={state.performance}
        selected={state.period}
        periodHrefs={periodHrefs()}
        labels={IAM_PERFORMANCE_LABELS}
        chartKey="loginSuccess"
        testid="iam-performance"
      />
      <p
        className="-mt-3 mb-6 text-xs text-muted-foreground"
        data-testid="iam-performance-scope"
      >
        {IAM_PERFORMANCE_SCOPE_NOTE}
      </p>

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
