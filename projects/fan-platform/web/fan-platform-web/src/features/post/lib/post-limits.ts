/**
 * Fan post input bounds — mirrors the backend DTO (`PublishPostRequest`): body 1..10000,
 * title max 200 (TASK-FAN-FE-016).
 *
 * <p>These live outside `api/actions.ts` for a hard reason, not a stylistic one: a
 * {@code 'use server'} module may export ONLY async functions. Exporting a plain constant
 * from there compiles under `tsc`, passes `next lint`, and passes vitest — and then fails
 * `next build` with "Only async functions are allowed to be exported in a 'use server' file".
 * Measured: that is exactly how this file came to exist.
 */
export const FAN_POST_BODY_MAX = 10_000;
export const FAN_POST_TITLE_MAX = 200;
