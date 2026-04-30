'use client';

import type { ReactNode } from 'react';
import type { OperatorRole } from '@/shared/api/admin-api';
import { hasAnyRole } from '@/shared/lib/roles';

interface RoleGuardProps {
  roles: OperatorRole[];
  allow: OperatorRole[];
  children: ReactNode;
  fallback?: ReactNode;
}

/**
 * Client-side visibility guard. The backend is the authoritative enforcer —
 * this only hides UI that the current operator is not allowed to invoke.
 */
export function RoleGuard({ roles, allow, children, fallback = null }: RoleGuardProps) {
  const permitted = hasAnyRole(roles, allow);
  return <>{permitted ? children : fallback}</>;
}

export { hasAnyRole } from '@/shared/lib/roles';
