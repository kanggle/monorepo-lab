import { THEME_STORAGE_KEY } from '@/shared/lib/theme';

/**
 * Pre-hydration theme bootstrap. Renders a tiny synchronous inline script in
 * <head> that sets `data-theme` on <html> before the first paint — from the
 * persisted choice, else the OS `prefers-color-scheme` — so dark mode never
 * flashes light on load. Kept dependency-free (no next-themes) to match the
 * storefront's plain-CSS token system.
 *
 * The <html> element carries `suppressHydrationWarning` (layout.tsx) because
 * this script mutates an attribute React also renders.
 */
export function ThemeScript() {
  const script = `(function(){try{var k=${JSON.stringify(THEME_STORAGE_KEY)};var s=localStorage.getItem(k);var t=(s==='light'||s==='dark')?s:(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');document.documentElement.setAttribute('data-theme',t);}catch(e){}})();`;
  return <script dangerouslySetInnerHTML={{ __html: script }} />;
}
