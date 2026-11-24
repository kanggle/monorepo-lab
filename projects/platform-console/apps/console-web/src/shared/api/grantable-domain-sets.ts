import { z } from 'zod';
import { apiClient } from '@/shared/api/client';

/**
 * Grant-picker options for ADR-MONO-083 「업무 권한 세트」 — the EXISTING
 * slice-a list route `/api/domain-permission-sets`, consumed by BOTH
 * `features/operators` (per-operator grant) and `features/operator-groups`
 * (per-group grant). Promoted here from two verbatim feature-local copies
 * (TASK-PC-FE-387) because architecture.md § Forbidden Dependencies forbids
 * feature-to-feature TS imports; `shared/api/` is the promotion home.
 */

/** Trimmed from the `/api/domain-permission-sets` (slice a) response. */
export const GrantableSetOptionSchema = z
  .object({
    setId: z.string(),
    name: z.string(),
    systemKey: z.string().nullable(),
    readOnly: z.boolean(),
    domains: z.array(z.string()),
  })
  .passthrough();
export type GrantableSetOption = z.infer<typeof GrantableSetOptionSchema>;

export const GrantableSetOptionListSchema = z.object({
  items: z.array(GrantableSetOptionSchema),
});

// `legacy-compat` is excluded client-side — the producer rejects it with
// `400 VALIDATION_ERROR` (MIGRATION-source-only backfill marker).
export async function queryGrantableSets(): Promise<GrantableSetOption[]> {
  const raw = await apiClient.get<unknown>('/api/domain-permission-sets');
  const parsed = GrantableSetOptionListSchema.parse(raw);
  return parsed.items.filter((s) => s.systemKey !== 'legacy-compat');
}
