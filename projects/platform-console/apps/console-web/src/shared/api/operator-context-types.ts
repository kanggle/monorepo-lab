import { z } from 'zod';

/**
 * Per-operator profile attribute carrier (TASK-BE-308 / TASK-BE-304) —
 * TASK-PC-FE-271 hoist.
 *
 * Optional field on the IAM `admin-service` operators-list item AND the
 * registry response item — omitted by the producer when the operator's
 * {@code finance_default_account_id} is NULL (field-level
 * {@code @JsonInclude.NON_NULL}); present with
 * {@code { defaultAccountId: "<uuid>" }} when set. The shape is byte-
 * identical across BOTH producers and the {@code me/profile} +
 * {@code admin/{operatorId}/profile} request bodies
 * (`admin-api.md` § "carrier shape 대칭성"; `console-registry-api.md`
 * § "Per-operator profile attributes"). Strict on the nested key set — a
 * forward-compat new sibling key (e.g. `wmsDefaultWarehouseId`) is a
 * fail-fast signal, not a silent acceptance.
 *
 * `shared/api/iam-operators-types.ts` and `shared/api/registry-types.ts`
 * each independently declared this exact shape until TASK-PC-FE-271 hoisted
 * it here — both embed it as `.optional()` at their own field site (this
 * base schema is intentionally NOT pre-wrapped optional).
 */
export const OperatorContextSchema = z.object({
  defaultAccountId: z.string().optional(),
});
export type OperatorContext = z.infer<typeof OperatorContextSchema>;
