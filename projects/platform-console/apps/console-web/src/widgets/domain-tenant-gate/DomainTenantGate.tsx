import type { ReactNode } from 'react';
import Link from 'next/link';
import { getAssumedToken, isSampleVisitor } from '@/shared/lib/session';

/**
 * Whether a domain section must ask the operator to pick a tenant first
 * (TASK-PC-FE-292).
 *
 * The domain sections (ecommerce · wms · scm · erp · finance · ledger) call their
 * gateways with {@link getDomainFacingToken}, which falls back to the base IAM
 * token when no tenant is assumed. For this console client that base token
 * carries the operational slug `iam`, not a customer tenant, so every gateway
 * rejects it — and the pages turn that 401 into «세션이 만료되었습니다», which is
 * false. A section therefore renders only once a tenant is ASSUMED.
 *
 * 🔵 A sample visitor passes: every call site answers them from the sample
 *    router (ADR-MONO-074), no token involved.
 * 🔴 Asked per SECTION layout, not in the `(console)` layout: the shell layout is
 *    not re-rendered on a client navigation between sections, so a gate there
 *    would never be asked on the way in.
 */
export async function tenantSelectionRequired(): Promise<boolean> {
  if (await isSampleVisitor()) return false;
  return (await getAssumedToken()) === null;
}

/** The domain-section layout gate — renders the section, or «테넌트를 선택하세요». */
export async function DomainTenantGate({
  section,
  children,
}: {
  /** Display name of the section, as the sidebar shows it. */
  section: string;
  children: ReactNode;
}) {
  if (!(await tenantSelectionRequired())) return <>{children}</>;

  return (
    <section aria-labelledby="domain-no-tenant-heading">
      <h1 id="domain-no-tenant-heading" className="mb-6 text-2xl font-semibold">
        {section}
      </h1>
      <div
        role="status"
        data-testid="domain-no-tenant"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        <p className="mb-2 font-medium text-foreground">테넌트를 먼저 선택하세요.</p>
        <p>
          {section} 화면은 선택한 테넌트의 권한으로 열립니다. 상단의 테넌트
          스위처에서 테넌트를 선택하면 이 화면이 열립니다.
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
