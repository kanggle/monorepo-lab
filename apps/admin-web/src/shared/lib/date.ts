import { format, parseISO } from 'date-fns';

/**
 * Format an ISO 8601 timestamp for UI display. Returns '-' for null/undefined
 * and falls back to the raw string if parsing fails (defensive: the backend is
 * expected to always return ISO strings).
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  try {
    return format(parseISO(iso), 'yyyy-MM-dd HH:mm:ss');
  } catch {
    return iso;
  }
}
