'use client';

import { useEffect, useId, useState } from 'react';
import { useTenantSwitch } from '../hooks/use-tenant-switch';
import type { CompanyGroup } from '../lib/tenant-options';

interface Props {
  tenants: string[];
  activeTenant: string | null;
  /**
   * Org-node (company) grouping for the AWS-IdC-style account picker
   * (TASK-PC-FE-237 / ADR-047). Empty ⇒ the flat switcher (byte-identical to
   * pre-grouping behaviour — the no-org-node / degraded case). A company
   * renders as a display-only `<optgroup>` (NOT selectable — the token carries
   * exactly one `tenant_id`; only leaf tenants are options). An ungrouped
   * tenant (D7 lazy-migration `org_node_id = null`) is a legal permanent state
   * and is ALWAYS still rendered.
   */
  companies?: CompanyGroup[];
}

/**
 * Tenant switcher (multi-tenant trait — scopes the session `tenant_id`).
 *
 * Graceful degradation (task Edge Case): with 0 tenants the control is hidden
 * entirely; with exactly 1 tenant it renders a static read-only label (no-op,
 * not an error). The actual cross-tenant rejection is enforced server-side /
 * by IAM — this is only the UX gate (architecture.md § Boundary Rules).
 */
export function TenantSwitcher({
  tenants,
  activeTenant,
  companies = [],
}: Props) {
  const selectId = useId();
  const switchTenant = useTenantSwitch();
  // No active tenant ⇒ render an UNSELECTED placeholder, never a silent
  // default to `tenants[0]` (TASK-PC-FE-036). Defaulting to tenants[0] made the
  // switcher show a tenant as "selected" while the server had no active-tenant
  // cookie, so the tenant-scoped overviews gated with "select a tenant" —
  // a confusing UI/server mismatch. The placeholder keeps the switcher honest;
  // the operator picks a customer, which sets the cookie via /api/tenant.
  const [selected, setSelected] = useState(activeTenant ?? '');

  // TASK-PC-FE-066 — the switcher lives in the persistent `(console)` layout, so
  // it stays MOUNTED across navigations and `router.refresh()`. When the active
  // tenant is changed from ELSEWHERE (e.g. clicking a tenant inside the catalog
  // grid → `/api/tenant` + `router.push` to that product's ops), the server
  // layout re-renders with a new `activeTenant` prop, but the local `selected`
  // state — seeded once at mount — would stay stale, leaving the top switcher on
  // the previous tenant. Sync it to the prop so an external switch reflects here.
  // The direct `onChange` path is unaffected (it setSelected()s immediately; the
  // post-switch refresh re-feeds the same value → idempotent).
  useEffect(() => {
    setSelected(activeTenant ?? '');
  }, [activeTenant]);

  if (tenants.length === 0) return null;

  if (tenants.length === 1) {
    return (
      <span
        className="text-sm text-muted-foreground"
        data-testid="tenant-single"
      >
        테넌트: <strong className="text-foreground">{tenants[0]}</strong>
      </span>
    );
  }

  const onChange = (value: string) => {
    setSelected(value);
    switchTenant.mutate(value);
  };

  // Grouped rendering (companies present): display-only `<optgroup>` blocks
  // followed by the ungrouped leaves under a plain `그룹 없음` label. An
  // ungrouped tenant is any selectable tenant not claimed by a company — it
  // must NEVER disappear (D7). With no companies we fall through to the flat
  // list below (byte-identical to the pre-grouping switcher).
  const hasCompanies = companies.length > 0;
  const groupedTenants = new Set(
    companies.flatMap((c) => c.tenants),
  );
  const ungrouped = tenants.filter((t) => !groupedTenants.has(t));

  return (
    <div className="flex items-center gap-2">
      <label
        htmlFor={selectId}
        className="text-sm text-muted-foreground"
      >
        테넌트
      </label>
      <select
        id={selectId}
        value={selected}
        disabled={switchTenant.isPending}
        onChange={(e) => onChange(e.target.value)}
        data-testid="tenant-select"
        className="rounded-md border border-border bg-background px-2 py-1 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        {selected === '' && (
          <option value="" disabled>
            테넌트 선택…
          </option>
        )}
        {hasCompanies ? (
          <>
            {companies.map((c) => (
              <optgroup key={c.orgNodeId} label={c.name}>
                {c.tenants.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </optgroup>
            ))}
            {ungrouped.length > 0 && (
              <optgroup label="그룹 없음">
                {ungrouped.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </optgroup>
            )}
          </>
        ) : (
          tenants.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))
        )}
      </select>
      {switchTenant.isError && (
        <span role="alert" className="text-xs text-destructive">
          전환 실패
        </span>
      )}
    </div>
  );
}
