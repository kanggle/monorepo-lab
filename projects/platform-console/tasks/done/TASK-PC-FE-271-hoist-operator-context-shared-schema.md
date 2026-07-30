# Task ID

TASK-PC-FE-271

# Title

console-web hoist duplicated `OperatorContextSchema`/`OperatorContext` into one shared declaration

# Status

done

# Owner

frontend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`shared/api/iam-operators-types.ts` and `shared/api/registry-types.ts` each independently declare a zod schema `OperatorContextSchema` and type `OperatorContext` with the **byte-identical inner shape** (`{ defaultAccountId: z.string().optional() }`). This is genuine duplication, not a name collision — both modules' own doc comments independently assert the SAME cross-producer fact: `iam-operators-types.ts` says "the shape is byte-identical to the registry response item carrier ... (admin-api.md § carrier shape 대칭성)"; `registry-types.ts` cites the same TASK-BE-304/308 lineage and `console-registry-api.md § Per-operator profile attributes`. Two independently-maintained declarations of a documented, intentional cross-producer symmetry is exactly the kind of duplication `platform/refactoring-policy.md` prioritizes fixing — the same pattern TASK-PC-FE-260 applied to `tenantStatusTone`.

The two usage sites differ only in WHERE the `.optional()` wrapping is applied (`iam-operators-types.ts` wraps it at the field-embed site: `operatorContext: OperatorContextSchema.optional()`; `registry-types.ts` bakes `.optional()` into the schema declaration itself: `export const OperatorContextSchema = z.object({...}).optional()`). The resulting parsed shape at each embed site is identical either way (`OperatorContext | undefined`) — this task picks ONE canonical form (the base object, non-optional at declaration — matching `iam-operators-types.ts`'s existing pattern, which is also more conventional zod style) and adjusts `registry-types.ts`'s single embed site to add `.optional()` explicitly.

---

# Scope

## In Scope

1. **New file `shared/api/operator-context-types.ts`** — the canonical, single declaration:
   ```ts
   import { z } from 'zod';

   export const OperatorContextSchema = z.object({
     defaultAccountId: z.string().optional(),
   });
   export type OperatorContext = z.infer<typeof OperatorContextSchema>;
   ```
   Carry over the fuller doc comment from `iam-operators-types.ts` (the one describing the producer's `@JsonInclude.NON_NULL` omission behavior + "byte-identical to the registry response item carrier and the `me/profile`/`admin/{operatorId}/profile` request bodies" + the "strict on nested key set, forward-compat sibling key is fail-fast" note) since it is the more complete of the two.

2. **`shared/api/iam-operators-types.ts`** — remove its local `OperatorContextSchema`/`OperatorContext` declaration; import both from `./operator-context-types` instead. Its own re-export of them (used by `OperatorSummarySchema`'s `operatorContext: OperatorContextSchema.optional()` field) is unchanged in behavior — same `.optional()` wrapping at the same embed site.

3. **`shared/api/registry-types.ts`** — remove its local `OperatorContextSchema`/`OperatorContext` declaration; import `OperatorContextSchema` from `./operator-context-types` instead. Update its single embed site in `RegistryProductSchema` from `operatorContext: OperatorContextSchema` to `operatorContext: OperatorContextSchema.optional()` (the base schema is no longer pre-wrapped `.optional()` — this preserves the exact same resulting parsed shape, `OperatorContext | undefined`, on the `RegistryProduct` type).

4. **`features/operators/api/types.ts`** — this file re-exports `OperatorContextSchema`/`OperatorContext` FROM `shared/api/iam-operators-types` (TASK-PC-FE-259's public-surface-preservation barrel). No change needed here — `iam-operators-types.ts` still exports both names (now re-exported from the new shared module), so this barrel's own export statement is unaffected.

## Out of Scope

- Any other type in `iam-operators-types.ts` or `registry-types.ts` (`OperatorSummarySchema`, `OperatorPageSchema`, `RegistryProductSchema`, `RegistryResponseSchema`, `ProductKeySchema`, `OPERATOR_STATUSES`) — untouched, no duplication found there.
- The `me/profile` / `admin/{operatorId}/profile` request body schemas the doc comment mentions as also byte-identical (per `admin-api.md § carrier shape 대칭성`) — if such a third duplicate declaration exists in a mutation-request-body module, it is a candidate for a FOLLOW-UP task, not this one (this task only touches the two READ-response carriers already confirmed duplicated by direct file inspection).
- Any change to `console-integration-contract.md` or `admin-api.md` / `console-registry-api.md` — this is a pure internal-implementation hoist; no wire shape changes, no contract surface changes (`OperatorContext`'s parsed shape is byte-identical before/after at every embed site).

---

# Acceptance Criteria

- [ ] `shared/api/operator-context-types.ts` exists with the single canonical `OperatorContextSchema`/`OperatorContext` declaration.
- [ ] `shared/api/iam-operators-types.ts` no longer declares `OperatorContextSchema`/`OperatorContext` locally — imports from `./operator-context-types`; still re-exports both names (byte-unchanged public surface for `features/operators/api/types.ts`'s re-export barrel).
- [ ] `shared/api/registry-types.ts` no longer declares `OperatorContextSchema`/`OperatorContext` locally — imports `OperatorContextSchema` from `./operator-context-types`; `RegistryProductSchema.operatorContext` is `OperatorContextSchema.optional()` (was `OperatorContextSchema` with the optionality baked into the old local schema — net-identical resulting parsed type).
- [ ] `RegistryProduct['operatorContext']`'s inferred TypeScript type is unchanged (`{ defaultAccountId?: string } | undefined`) — verify via `tsc --noEmit` producing zero new errors at every existing consumer of `RegistryProduct`.
- [ ] `grep -c "z.object({" shared/api/iam-operators-types.ts shared/api/registry-types.ts` shows one fewer `OperatorContextSchema`-shaped object literal in each file (the declaration moved out).
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count, no test assertion values modified (existing coverage that constructs `operatorContext: {...}` / `operatorContext: undefined` fixtures for `OperatorSummary`/`RegistryProduct` must still pass against the same parsed shape).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Reduce Duplication** (Medium risk); duplication outranks naming per § Prioritization.
- TASK-PC-FE-260 (`tenantStatusTone` shared-vocabulary centralization) — the direct precedent for this exact "two modules independently declare the same documented-symmetric shape" pattern.
- TASK-PC-FE-259 (the `shared/` promotion discipline + `features/operators/api/types.ts` re-export-barrel preservation pattern) — followed here for `iam-operators-types.ts`'s own re-export of `OperatorContextSchema`/`OperatorContext`.
- `iam/specs/contracts/http/admin-api.md` § "carrier shape 대칭성" and `iam-platform/specs/contracts/http/console-registry-api.md` § "Per-operator profile attributes" — the two producer-contract sources whose independently-documented symmetry motivates this hoist (read for context only — this task does not modify either contract).

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API/event contract touched; pure internal type-declaration consolidation, zero wire-shape change.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Grep-verify `OperatorContextSchema`/`OperatorContext` usages BEFORE starting (baseline: `shared/api/iam-operators-types.ts`, `shared/api/registry-types.ts`, `features/operators/api/types.ts` — 3 files, confirmed via full-repo grep during investigation) and AFTER finishing (should be 4 files: the same 3 plus the new `shared/api/operator-context-types.ts`), per TASK-PC-FE-262/268/269/270's verification method.
- `registry-types.ts`'s embed-site change (`OperatorContextSchema` → `OperatorContextSchema.optional()`) is the one line requiring actual logic attention — do not just copy-paste the import without checking this site specifically, or `RegistryProduct.operatorContext` silently stops being optional in its type (a real, `tsc`-catchable regression if missed, since the field would become required).

---

# Edge Cases

- `registry-types.ts`'s CURRENT `OperatorContextSchema` is `.optional()` at the schema-declaration level (unusual zod style — most schemas in this codebase apply `.optional()` at the embed site, matching `iam-operators-types.ts`'s pattern). After the hoist, the canonical shared schema follows the more common embed-site-optional convention — this is a deliberate, documented style normalization, not an accidental behavior change (the Acceptance Criteria's `tsc --noEmit` check is the objective proof the resulting type is unchanged).
- The new shared module's doc comment should NOT simply concatenate both old comments — pick the more complete one (`iam-operators-types.ts`'s, per Scope item 1) and optionally fold in `registry-types.ts`'s reference to `console-registry-api.md § Per-operator profile attributes` as an additional citation, without duplicating the "byte-identical" claim redundantly.

---

# Failure Scenarios

- `tsc --noEmit` fails after the hoist → most likely the `registry-types.ts` embed-site `.optional()` was missed (see Implementation Notes) — check that specific line first before broader investigation.
- Any test constructing a `RegistryProduct` fixture WITHOUT an `operatorContext` field starts failing schema validation → confirms the `.optional()` regression above; fix the embed site, do not modify the test fixture.
- `features/operators/api/types.ts`'s re-export of `OperatorContextSchema`/`OperatorContext` breaks → `iam-operators-types.ts` must still export both names (via re-export from the new module, not by removing them) — this barrel's own export statement must not need any edit.

---

# Test Requirements

- No new tests required — existing coverage (any test constructing `OperatorSummary`/`RegistryProduct` fixtures with/without `operatorContext`, plus `tsc --noEmit` as the type-level regression check) is the verification mechanism and must pass unmodified.

---

# Definition of Done

- [ ] All 4 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertion values modified
- [ ] Ready for review
