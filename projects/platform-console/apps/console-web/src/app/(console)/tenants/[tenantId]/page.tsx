import Link from 'next/link';
import { notFound } from 'next/navigation';
import { getTenantDetailState, TenantDetail } from '@/features/tenants';

export const dynamic = 'force-dynamic';

/**
 * IAM tenant DETAIL + inline-edit route (TASK-PC-FE-226) — combined
 * "상세/수정" per the task's single `[tenantId]/page.tsx` shape (no separate
 * `/edit` route). SUPER_ADMIN only, mirroring `/tenants`.
 *
 * Server component: eligibility waterfall mirrors the ecommerce seller-detail
 * precedent — noTenant → permissionError → notFound → degraded → happy.
 */
export default async function TenantDetailPage({
  params,
}: {
  params: Promise<{ tenantId: string }>;
}) {
  const { tenantId: rawTenantId } = await params;
  const tenantId = decodeURIComponent(rawTenantId);
  const state = await getTenantDetailState(tenantId);

  const heading = (
    <h1 id="tenant-detail-heading" className="mb-6 text-2xl font-semibold">
      테넌트 상세
    </h1>
  );

  if (state.noTenant) {
    return (
      <section aria-labelledby="tenant-detail-heading">
        {heading}
        <div
          role="status"
          data-testid="tenant-detail-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            테넌트 관리는 SUPER_ADMIN 전용이며 활성 테넌트가 선택되어 있어야
            합니다. 상단의 테넌트 스위처에서 플랫폼 스코프(*)를 선택한 뒤 다시
            시도하세요.
          </p>
          <Link
            href="/tenants"
            className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            목록으로
          </Link>
        </div>
      </section>
    );
  }

  if (state.permissionError) {
    return (
      <section aria-labelledby="tenant-detail-heading">
        {heading}
        <div
          role="status"
          data-testid="tenant-detail-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          테넌트 관리는 SUPER_ADMIN 전용입니다. 이 화면을 조회·변경할 권한이
          없습니다.
        </div>
      </section>
    );
  }

  if (state.notFound) {
    notFound();
  }

  if (state.degraded || !state.tenant) {
    return (
      <section aria-labelledby="tenant-detail-heading">
        {heading}
        <div
          role="status"
          data-testid="tenant-detail-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          테넌트 관리 서비스를 일시적으로 불러올 수 없습니다. 잠시 후 다시
          시도하세요.
        </div>
        <Link
          href="/tenants"
          className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          목록으로
        </Link>
      </section>
    );
  }

  return <TenantDetail tenant={state.tenant} />;
}
