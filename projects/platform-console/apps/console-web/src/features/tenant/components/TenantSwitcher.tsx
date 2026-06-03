'use client';

import { useId, useState } from 'react';
import { useTenantSwitch } from '../hooks/use-tenant-switch';

interface Props {
  tenants: string[];
  activeTenant: string | null;
}

/**
 * Tenant switcher (multi-tenant trait — scopes the session `tenant_id`).
 *
 * Graceful degradation (task Edge Case): with 0 tenants the control is hidden
 * entirely; with exactly 1 tenant it renders a static read-only label (no-op,
 * not an error). The actual cross-tenant rejection is enforced server-side /
 * by GAP — this is only the UX gate (architecture.md § Boundary Rules).
 */
export function TenantSwitcher({ tenants, activeTenant }: Props) {
  const selectId = useId();
  const switchTenant = useTenantSwitch();
  // No active tenant ⇒ render an UNSELECTED placeholder, never a silent
  // default to `tenants[0]` (TASK-PC-FE-036). Defaulting to tenants[0] made the
  // switcher show a tenant as "selected" while the server had no active-tenant
  // cookie, so the tenant-scoped overviews gated with "select a tenant" —
  // a confusing UI/server mismatch. The placeholder keeps the switcher honest;
  // the operator picks a customer, which sets the cookie via /api/tenant.
  const [selected, setSelected] = useState(activeTenant ?? '');

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
        {tenants.map((t) => (
          <option key={t} value={t}>
            {t}
          </option>
        ))}
      </select>
      {switchTenant.isError && (
        <span role="alert" className="text-xs text-destructive">
          전환 실패
        </span>
      )}
    </div>
  );
}
