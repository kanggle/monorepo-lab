/** Web Push browser helpers (TASK-FE-083). */

/** True only where the browser supports Service Worker + Push + Notification APIs. */
export function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

/**
 * Convert a base64url-encoded VAPID public key to the Uint8Array that
 * {@code pushManager.subscribe({ applicationServerKey })} requires.
 */
export function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  // Back the view with a concrete ArrayBuffer (not ArrayBufferLike) so the result is
  // a valid `applicationServerKey` BufferSource under TS 5.7+ typed-array generics.
  const output = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

/** Register (or return the existing registration for) the push service worker. */
export function registerServiceWorker(): Promise<ServiceWorkerRegistration> {
  return navigator.serviceWorker.register('/sw.js');
}
