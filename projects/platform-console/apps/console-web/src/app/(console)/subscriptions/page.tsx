import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getCatalog } from '@/features/catalog';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError } from '@/shared/api/errors';
import {
  deriveDomainSubscriptions,
  SubscriptionsScreen,
} from '@/features/subscriptions';

export const dynamic = 'force-dynamic';

/**
 * 도메인 구독 (TASK-PC-FE-183 / ADR-MONO-023 · ADR-MONO-044 §3.4 follow-up).
 *
 * The tenant-owner entitlement surface: a `TENANT_BILLING_ADMIN`
 * (`subscription.manage`) self-enables the business domains for their active
 * tenant. This is the piece that makes self-service onboarding (PC-FE-182)
 * meaningful — a freshly-onboarded tenant is born with ZERO subscriptions
 * (ADR-044 D6); without this screen the owner lands in a console where no
 * domain can be turned on (the AWS/GCP "owner turns on services" parity).
 *
 * Server component: derive current state from the catalog (there is no
 * list/GET subscriptions endpoint — a domain is ACTIVE ⟺ in the registry for
 * the active tenant; SUSPENDED/CANCELLED drop out, ADR-023). Tenant-gated
 * (subscriptions are tenant-scoped); 401 on the catalog read → clean re-login.
 */
export default async function SubscriptionsPage() {
  const activeTenant = await getActiveTenant();

  if (!activeTenant) {
    return (
      <section aria-labelledby="subscriptions-heading">
        <h1 id="subscriptions-heading" className="mb-6 text-2xl font-semibold">
          도메인 구독
        </h1>
        <div
          role="status"
          data-testid="subscriptions-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            도메인 구독은 테넌트 범위로 관리됩니다. 상단의 테넌트 스위처에서
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

  let degraded = false;
  let rows;
  try {
    const catalog = await getCatalog();
    degraded = catalog.degraded;
    rows = deriveDomainSubscriptions(catalog.products, activeTenant);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) redirect('/login');
    throw err;
  }

  return (
    <section aria-labelledby="subscriptions-heading">
      <h1 id="subscriptions-heading" className="mb-2 text-2xl font-semibold">
        도메인 구독
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        조직 <span className="font-medium text-foreground">{activeTenant}</span>
        의 도메인 구독을 관리합니다. 구독한 도메인만 콘솔·엔타이틀먼트에서 사용할
        수 있습니다 (구독 관리 권한: TENANT_BILLING_ADMIN).
      </p>

      {degraded && (
        <div
          role="status"
          data-testid="subscriptions-degraded"
          className="mb-4 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          카탈로그를 일시적으로 불러올 수 없어 현재 구독 상태가 정확하지 않을 수
          있습니다. 새로고침 후에도 계속되면 잠시 뒤 다시 시도하세요.
        </div>
      )}

      <SubscriptionsScreen activeTenant={activeTenant} rows={rows} />
    </section>
  );
}
