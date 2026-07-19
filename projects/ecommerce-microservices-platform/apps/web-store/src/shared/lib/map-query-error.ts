import { isApiError } from '@repo/types/guards';

/**
 * Map a TanStack Query error into a user-facing message for the common
 * two-outcome case: a specific "not found" code gets one message, everything
 * else (including non-API errors) gets a fallback. No error → empty string.
 *
 * `notFoundMessage` may be `''` when a not-found result should read as "no
 * message" (e.g. optional payment info that legitimately may not exist yet).
 * Error shapes with more than two outcomes (e.g. a distinct ACCESS_DENIED
 * branch) should stay inline rather than force-fit this helper.
 */
export function mapQueryError(
  error: unknown,
  opts: { notFoundCode: string; notFoundMessage: string; fallbackMessage: string },
): string {
  if (!error) return '';
  return isApiError(error) && error.code === opts.notFoundCode
    ? opts.notFoundMessage
    : opts.fallbackMessage;
}
