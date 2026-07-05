import Link from 'next/link';
import { redirect } from 'next/navigation';
import {
  getPartnershipsListState,
  PartnershipsScreen,
} from '@/features/partnerships';
import { getActiveTenant } from '@/shared/lib/session';

export const dynamic = 'force-dynamic';

/**
 * 파트너십 (TASK-PC-FE-187 / ADR-MONO-045 §3.4 step 3).
 *
 * The cross-org partner-delegation operator surface: a `TENANT_ADMIN`
 * (`partnership.manage`) invites / accepts / suspends / reactivates /
 * terminates partnerships and (partner side) assigns own operators as
 * participants. This surface manages the RELATIONSHIP state only — the derived
 * cross-org domain permissions are capped at assume-tenant issuance
 * (`delegated_scope ∩ participant ∩ host-holds`); this UI never extends admin
 * authority across the org boundary (the backend enforces that).
 *
 * Server component: the initial list is fetched server-side via the IAM
 * admin-service client with the HttpOnly operator token + active tenant
 * (`getPartnershipsListState()`). Resilience is handled there (mirror of
 * `operators`):
 *   - 401 → `redirect('/login')` (clean re-login, no partial authed state).
 *   - no active tenant → a "select a tenant" gate (never an empty
 *     `X-Tenant-Id`).
 *   - 403 PERMISSION_DENIED (lacks partnership.manage) / PARTNERSHIP_SCOPE_DENIED
 *     → an inline "not permitted" state (no crash, no re-login loop).
 *   - 503 / timeout → a degraded notice; the console shell stays intact.
 */
export default async function PartnershipsPage() {
  const state = await getPartnershipsListState({ page: 0, size: 20 });

  if (state.noTenant) {
    return (
      <section aria-labelledby="partnerships-heading">
        <h1
          id="partnerships-heading"
          className="mb-6 text-2xl font-semibold"
        >
          파트너십
        </h1>
        <div
          role="status"
          data-testid="partnerships-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            파트너십은 테넌트 범위로 관리됩니다. 상단의 테넌트 스위처에서
            테넌트를 선택한 뒤 다시 시도하세요.
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

  if (state.permissionError) {
    return (
      <section aria-labelledby="partnerships-heading">
        <h1
          id="partnerships-heading"
          className="mb-6 text-2xl font-semibold"
        >
          파트너십
        </h1>
        <div
          role="status"
          data-testid="partnerships-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          {state.permissionError.code === 'PARTNERSHIP_SCOPE_DENIED'
            ? '이 파트너십 범위에 대한 관리 권한이 없습니다.'
            : '파트너십 관리는 partnership.manage 권한이 필요합니다 (조직 관리자 TENANT_ADMIN).'}
        </div>
      </section>
    );
  }

  if (state.degraded || !state.page) {
    return (
      <section aria-labelledby="partnerships-heading">
        <h1
          id="partnerships-heading"
          className="mb-6 text-2xl font-semibold"
        >
          파트너십
        </h1>
        <div
          role="status"
          data-testid="partnerships-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          파트너십 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  // Past the noTenant gate an active tenant is selected; read it for the client
  // component (drives the invite-form copy). A 401 here → clean re-login.
  const activeTenant = await getActiveTenant();
  if (!activeTenant) redirect('/login');

  return (
    <PartnershipsScreen initial={state.page} activeTenant={activeTenant} />
  );
}
