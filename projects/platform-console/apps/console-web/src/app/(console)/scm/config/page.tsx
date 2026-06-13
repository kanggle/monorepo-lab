import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { SeedConfigScreen } from '@/features/scm-config';

export const dynamic = 'force-dynamic';

/**
 * scm replenishment **seed/config** operator screen route (TASK-PC-FE-080 — the
 * operator config arm of the ADR-MONO-027 wms→scm replenishment loop; the third
 * SCM drill-in tab 설정, alongside 운영 (FE-008) + 보충 (FE-077)). A sub-route of
 * the existing `/scm` section, registry-driven (`productKey=scm`).
 *
 * Server component. scm demand-planning is reached server-side (from the
 * SKU-code-driven proxy routes the client calls) with the HttpOnly
 * **domain-facing IAM OIDC access token** (NOT the IAM exchanged operator token
 * — § 2.4.6.2 reuses the § 2.4.6 / § 2.4.6.1 per-domain credential rule; scm has
 * no operator-token exchange). GET inspect + PUT upsert ride the SAME credential.
 *
 * Eligibility (§ 2.4.6.2 reusing § 2.4.6 tenant-model divergence): scm resolves
 * the tenant from the JWT `tenant_id ∈ {scm,*}` claim producer-side — the console
 * sends no tenant. The page resolves the operator's scm eligibility from the
 * data-driven registry; a non-eligible operator is BLOCKED with an actionable
 * state and NO scm call is ever made (the SKU-driven forms are never rendered).
 * There is NO list route, so the page itself makes NO seed call at load — the
 * forms fetch only once the operator looks up a SKU. Resilience (§ 2.5): 401 →
 * whole-session re-login; registry degrade → section-only degrade.
 */
export default async function ScmConfigPage() {
  let eligible = false;
  let registryDegraded = false;
  try {
    const catalog = await getCatalog();
    const scm = catalog.products.find((p) => p.productKey === 'scm');
    eligible = Boolean(scm && scm.available && scm.tenants.length > 0);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    registryDegraded = true;
  }

  if (registryDegraded) {
    return (
      <section aria-labelledby="cfg-heading">
        <h1 id="cfg-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 설정
        </h1>
        <div
          role="status"
          data-testid="cfg-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 설정 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  if (!eligible) {
    return (
      <section aria-labelledby="cfg-heading">
        <h1 id="cfg-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 설정
        </h1>
        <div
          role="status"
          data-testid="cfg-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            scm 보충 설정 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 scm 테넌트 스코프가 부여되어 있지
            않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
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

  return <SeedConfigScreen />;
}
