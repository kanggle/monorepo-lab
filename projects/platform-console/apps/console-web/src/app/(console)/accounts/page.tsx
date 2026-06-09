import Link from 'next/link';
import { getAccountsListState } from '@/features/accounts';
import { AccountsScreen } from '@/features/accounts';

export const dynamic = 'force-dynamic';

/**
 * IAM accounts operator surface route (TASK-PC-FE-002 — ADR-MONO-013
 * Phase 2 slice 1). The catalog `iam.baseRoute` resolves here.
 *
 * Server component: the initial accounts page is fetched server-side via the
 * IAM admin-service client with the HttpOnly operator token + active tenant
 * (`getAccountsListState()`). Resilience is handled there:
 *   - 401 → `redirect('/login')` (auth failure; handled inside
 *     `getAccountsListState`).
 *   - 403 PERMISSION_DENIED → a distinct 권한 없음 notice (TASK-MONO-202;
 *     `account.read` absent — NOT a re-login).
 *   - no active tenant → a "select a tenant" gate (never an empty
 *     `X-Tenant-Id`).
 *   - 503 / timeout → a degraded notice; the console shell stays intact
 *     (the `(console)` layout still renders around this).
 */
export default async function AccountsPage() {
  const state = await getAccountsListState({ page: 0, size: 20 });

  if (state.noTenant) {
    return (
      <section aria-labelledby="accounts-heading">
        <h1 id="accounts-heading" className="mb-6 text-2xl font-semibold">
          계정 운영
        </h1>
        <div
          role="status"
          data-testid="accounts-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            계정 운영 작업은 테넌트 범위로 수행됩니다. 상단의 테넌트
            스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
          </p>
          <Link
            href="/console"
            className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            카탈로그로 이동
          </Link>
        </div>
      </section>
    );
  }

  if (state.forbidden) {
    return (
      <section aria-labelledby="accounts-heading">
        <h1 id="accounts-heading" className="mb-6 text-2xl font-semibold">
          계정 운영
        </h1>
        <div
          role="status"
          data-testid="accounts-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            조회 권한이 없습니다.
          </p>
          <p>
            계정 목록을 조회하려면 <code>account.read</code> 권한이 필요합니다.
            권한이 필요하면 관리자에게 문의하세요.
          </p>
        </div>
      </section>
    );
  }

  if (state.degraded || !state.page) {
    return (
      <section aria-labelledby="accounts-heading">
        <h1 id="accounts-heading" className="mb-6 text-2xl font-semibold">
          계정 운영
        </h1>
        <div
          role="status"
          data-testid="accounts-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          계정 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <AccountsScreen initial={state.page} />;
}
