import type { OperatorRole } from '@/shared/api/admin-api';

export function hasAnyRole(roles: OperatorRole[], allow: OperatorRole[]): boolean {
  return roles.some((r) => allow.includes(r));
}
