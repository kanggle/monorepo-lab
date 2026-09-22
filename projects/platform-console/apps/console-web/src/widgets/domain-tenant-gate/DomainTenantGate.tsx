import type { ReactNode } from 'react';
import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import {
  getActiveTenant,
  getAssumedToken,
  isSampleVisitor,
} from '@/shared/lib/session';

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

/**
 * Whether the ASSUMED tenant is one this section's product actually serves
 * (TASK-MONO-718, owner decision ⓑ). Returns the tenants to switch to, or
 * `null` when there is nothing to say.
 *
 * 🔴🔴 **CORRECTED 2026-09-22 (TASK-MONO-719) — this does NOT close the failure
 * it was written for.** The paragraph that stood here said it did, on this
 * evidence (same instant, same URLs, on the demo):
 *
 *     tenant=ecommerce  → orders 5 · products 24 · users 1 · sellers 2
 *     tenant=demo-corp  → 0 · 0 · 0 · 0
 *
 * The 15th demo window then judged the shipped gate and it never rendered: the
 * LIVE registry answers `ecommerce -> [demo-corp, ecommerce]`, so
 * `product.tenants.includes('demo-corp')` is TRUE and this function always
 * returns `null`. The operator still saw the plain «표시할 주문이 없습니다».
 *
 * 🔵 The registry is not wrong. `demo-corp` IS entitled to the ecommerce
 * product — it carries the operator ROLES (TASK-BE-576). That the ROWS live
 * under `tenant_id=ecommerce` is a SEPARATE proposition. **This function asks
 * entitlement; the failure lives in data ownership**, and the two genuinely
 * diverge here. The unit cells missed it because their fixture said
 * `tenants: ['ecommerce']` — a shape the live system does not produce.
 *
 * ⇒ The measured case moved to `OtherTenantHint` (TASK-MONO-719, owner
 *   decision ⓑ): beside an EMPTY list, when other tenants are selectable, say
 *   so as a hint. **Do not re-add that claim here** — this function cannot see
 *   whether a list came back empty; it runs in the layout, before the children.
 *
 * 🔵 **What this function DOES still close, honestly**: a tenant that the
 * product genuinely does not serve. That is reachable, because
 * `selectableTenants()` is the union ACROSS products — a tenant registered
 * only under scm is selectable and can walk into /ecommerce. It was simply
 * never the case the demo measured.
 *
 * 🔵 **Judged by relation, never by a slug list** — the same discipline
 * `active-tenant-default.ts` states for `selectableTenants()`: a hard-coded
 * «ecommerce lives in `ecommerce`» would break silently on the next rename.
 *
 * 🔴 **Every uncertainty falls THROUGH to the section.** A degraded registry, a
 * product that is absent, or a product with no tenants cannot prove a mismatch,
 * and a gate that blocks on «I could not check» would turn a registry blip into
 * a dead section. Only a positive answer — the product is present, it lists
 * tenants, and the active one is not among them — renders the notice.
 */
async function tenantMismatch(
  productKey: string,
): Promise<{ active: string; expected: string[] } | null> {
  const active = await getActiveTenant();
  if (!active) return null; // the «select a tenant» gate above owns this case
  try {
    const catalog = await getCatalog();
    if (catalog.degraded) return null;
    const product = catalog.products.find((p) => p.productKey === productKey);
    if (!product || product.tenants.length === 0) return null;
    if (product.tenants.includes(active)) return null;
    return { active, expected: product.tenants };
  } catch {
    // Cannot prove a mismatch from a failed registry — let the section render.
    return null;
  }
}

/** The domain-section layout gate — renders the section, or «테넌트를 선택하세요». */
export async function DomainTenantGate({
  section,
  productKey,
  children,
}: {
  /** Display name of the section, as the sidebar shows it. */
  section: string;
  /**
   * Registry `productKey` of this section. OPTIONAL and opt-in: pass it and the
   * gate also checks that the assumed tenant is one the product serves
   * (TASK-MONO-718). Omit it and the gate behaves exactly as before.
   *
   * 🔵 Left off the other sections deliberately — TASK-MONO-718 § 제외 says the
   *    measurement points at ecommerce alone (wms · scm · erp · finance all
   *    rendered their data under `demo-corp` in the same window), and widening
   *    it to «a shared problem» would be a claim nothing measured.
   */
  productKey?: string;
  children: ReactNode;
}) {
  if (!(await tenantSelectionRequired())) {
    const mismatch = productKey ? await tenantMismatch(productKey) : null;
    if (!mismatch) return <>{children}</>;
    return (
      <section aria-labelledby="domain-tenant-mismatch-heading">
        <h1
          id="domain-tenant-mismatch-heading"
          className="mb-6 text-2xl font-semibold"
        >
          {section}
        </h1>
        <div
          role="status"
          data-testid="domain-tenant-mismatch"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            이 화면의 데이터는 다른 테넌트에 있습니다.
          </p>
          <p>
            현재 활성 테넌트는 <code>{mismatch.active}</code> 인데, {section}{' '}
            화면은{' '}
            {mismatch.expected.map((t, i) => (
              <span key={t}>
                {i > 0 ? ', ' : ''}
                <code>{t}</code>
              </span>
            ))}{' '}
            테넌트의 데이터를 읽습니다. 상단의 테넌트 스위처에서 그 테넌트로
            전환하면 이 화면이 채워집니다.
          </p>
          <p className="mt-2">
            🔵 전환하지 않아도 화면은 열리지만 <b>목록이 비어 보입니다</b> —
            데이터가 없는 것이 아니라 지금 테넌트가 그 데이터를 소유하지 않기
            때문입니다.
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
