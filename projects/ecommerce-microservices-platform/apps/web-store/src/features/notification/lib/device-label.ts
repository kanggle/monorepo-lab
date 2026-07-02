/**
 * Parse a stored User-Agent string into a short, human-readable device label
 * such as "Windows · Chrome" for the push device list (TASK-FE-085).
 *
 * This is a best-effort presentation helper — User-Agent strings are messy and
 * spoofable, so it never throws: an unknown/absent UA falls back to a generic
 * label. Order matters (e.g. Edge/Opera before Chrome, since they include
 * "Chrome" in their UA).
 */
const UNKNOWN_DEVICE = '알 수 없는 기기';

function detectOs(ua: string): string | null {
  if (/windows nt/i.test(ua)) return 'Windows';
  if (/iphone|ipad|ipod/i.test(ua)) return 'iOS';
  if (/android/i.test(ua)) return 'Android';
  if (/mac os x|macintosh/i.test(ua)) return 'macOS';
  if (/linux/i.test(ua)) return 'Linux';
  return null;
}

function detectBrowser(ua: string): string | null {
  if (/edg\//i.test(ua)) return 'Edge';
  if (/opr\/|opera/i.test(ua)) return 'Opera';
  if (/samsungbrowser/i.test(ua)) return 'Samsung Internet';
  if (/firefox\//i.test(ua)) return 'Firefox';
  if (/chrome\/|crios\//i.test(ua)) return 'Chrome';
  // Safari must be checked after Chrome (Chrome's UA also contains "Safari").
  if (/safari\//i.test(ua)) return 'Safari';
  return null;
}

export function deviceLabelFromUserAgent(userAgent: string | null | undefined): string {
  if (!userAgent || !userAgent.trim()) return UNKNOWN_DEVICE;

  const os = detectOs(userAgent);
  const browser = detectBrowser(userAgent);

  if (os && browser) return `${os} · ${browser}`;
  if (browser) return browser;
  if (os) return os;
  return UNKNOWN_DEVICE;
}
