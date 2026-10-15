'use client';

import type { TenantOrgNodeOption } from '../api/types';

export interface TenantCreateScreenProps {
  orgNodeOptions?: TenantOrgNodeOption[] | null;
  navigate?: (href: string) => void;
}

/** TASK-MONO-773 S6 — PHASE 1 scaffold only (so the red tests compile). Pre-S6 the form lives on `/tenants`. */
export function TenantCreateScreen(_props: TenantCreateScreenProps) {
  return null;
}
