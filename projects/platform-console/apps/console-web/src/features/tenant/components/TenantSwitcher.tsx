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
  const [selected, setSelected] = useState(activeTenant ?? tenants[0] ?? '');

  if (tenants.length === 0) return null;

  if (tenants.length === 1) {
    return (
      <span
        className="text-sm text-primary-foreground/80"
        data-testid="tenant-single"
      >
        테넌트: <strong>{tenants[0]}</strong>
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
        className="text-sm text-primary-foreground/80"
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
