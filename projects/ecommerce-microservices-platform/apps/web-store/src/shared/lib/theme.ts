/**
 * Light/dark theming primitives for the storefront.
 *
 * The active theme is expressed as `data-theme="light" | "dark"` on <html>,
 * which the design tokens in globals.css key off (`:root` = light,
 * `:root[data-theme='dark']` = dark). The choice is persisted in localStorage
 * and applied pre-hydration by {@link ThemeScript} so there is no flash of the
 * wrong theme; {@link ThemeToggle} flips it at runtime.
 */
export type Theme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'webstore-theme';

/** The theme currently painted, read from the <html data-theme> attribute. */
export function readAppliedTheme(): Theme {
  if (typeof document === 'undefined') return 'light';
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

/** Apply a theme to the document and persist the explicit choice. */
export function applyTheme(theme: Theme): void {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme;
  }
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Private-mode / storage-disabled: the in-memory attribute still applies.
  }
}
