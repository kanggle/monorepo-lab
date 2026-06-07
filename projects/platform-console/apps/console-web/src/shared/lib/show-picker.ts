import type { MouseEvent } from 'react';

/**
 * `onClick` handler for native `<input type="date" | "datetime-local" | ...>`
 * fields so that clicking ANYWHERE in the field — not just the small calendar
 * glyph — opens the browser's date/time picker.
 *
 * `HTMLInputElement.showPicker()` must run inside a user gesture (a click is
 * one) and is unsupported on older browsers / jsdom; it also throws if the
 * picker is already open or the call is otherwise disallowed. Hence the
 * feature-detect + try/catch: when unavailable we silently fall back to the
 * browser's default (glyph-only) behavior, so the input stays fully usable.
 */
export function showPickerOnClick(e: MouseEvent<HTMLInputElement>): void {
  const el = e.currentTarget;
  if (typeof el.showPicker === 'function') {
    try {
      el.showPicker();
    } catch {
      /* not allowed / already shown / unsupported — keep default behavior */
    }
  }
}
