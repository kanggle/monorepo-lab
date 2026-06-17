'use client';

import { useEffect } from 'react';

/**
 * Root error boundary (TASK-PC-FE-114).
 *
 * `(console)/error.tsx` catches render errors in the console segment's CHILDREN
 * only — by App Router design it does NOT catch errors thrown by its sibling
 * `(console)/layout.tsx` (the boundary is nested inside that layout), nor
 * root-layout errors, nor client-side `ChunkLoadError` / RSC-fetch failures.
 * Those all bubble to the root, and with no root boundary Next.js shows its
 * bare default — "Application error: a client-side exception has occurred" —
 * which is exactly what an operator saw on the first screen after logging in
 * against a freshly-redeployed (cold) stack: a slow/cold `console-web` failed
 * to serve a JS chunk / RSC payload mid-navigation, the resulting client-side
 * exception had no boundary, and the shell blank-crashed.
 *
 * This component is the missing root boundary. `global-error.tsx` replaces the
 * whole document on an otherwise-unhandled error, so it must render its own
 * `<html>`/`<body>`. A one-shot auto-reload recovers from transient chunk-load
 * failures (the common cold-deploy case) without operator action; anything
 * else surfaces a friendly, manually-recoverable panel instead of the raw
 * Next.js string.
 */

function isChunkLoadError(error: Error): boolean {
  // Next.js / webpack surface a stale-chunk failure as `ChunkLoadError`
  // (error.name) or a "Loading chunk … failed" / "Failed to fetch dynamically
  // imported module" message. After a redeploy the client's cached chunk
  // hashes no longer exist on the server — a single reload fetches the new ones.
  const name = error?.name ?? '';
  const message = error?.message ?? '';
  return (
    name === 'ChunkLoadError' ||
    /loading chunk [\w-]+ failed/i.test(message) ||
    /failed to fetch dynamically imported module/i.test(message) ||
    /importing a module script failed/i.test(message)
  );
}

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // eslint-disable-next-line no-console
    console.error(
      JSON.stringify({
        level: 'error',
        msg: 'global_render_error',
        name: error?.name,
        digest: error?.digest,
        chunk: isChunkLoadError(error),
      }),
    );

    // Transient cold-deploy chunk failure → reload once to fetch fresh chunks.
    // Guard with sessionStorage so a genuinely-broken build cannot reload-loop.
    if (isChunkLoadError(error) && typeof window !== 'undefined') {
      const KEY = 'pc_chunk_reload_once';
      if (!window.sessionStorage.getItem(KEY)) {
        window.sessionStorage.setItem(KEY, '1');
        window.location.reload();
      }
    }
  }, [error]);

  return (
    <html lang="ko">
      <body
        style={{
          margin: 0,
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontFamily:
            'ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
          background: '#0a0a0a',
          color: '#fafafa',
        }}
      >
        <div
          role="alert"
          style={{
            maxWidth: 28 * 16,
            padding: 32,
            textAlign: 'center',
            border: '1px solid #262626',
            borderRadius: 12,
            background: '#111111',
          }}
        >
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>
            문제가 발생했습니다
          </h2>
          <p style={{ marginTop: 8, fontSize: 14, color: '#a3a3a3' }}>
            화면을 표시하는 중 오류가 발생했습니다. 다시 시도해주세요. 방금
            배포가 있었다면 새로고침으로 해결되는 경우가 많습니다.
          </p>
          <div
            style={{
              marginTop: 24,
              display: 'flex',
              gap: 8,
              justifyContent: 'center',
            }}
          >
            <button
              type="button"
              onClick={() => reset()}
              style={{
                padding: '8px 16px',
                fontSize: 14,
                fontWeight: 500,
                borderRadius: 6,
                border: 'none',
                cursor: 'pointer',
                background: '#fafafa',
                color: '#0a0a0a',
              }}
            >
              다시 시도
            </button>
            <button
              type="button"
              onClick={() => {
                if (typeof window !== 'undefined') window.location.reload();
              }}
              style={{
                padding: '8px 16px',
                fontSize: 14,
                fontWeight: 500,
                borderRadius: 6,
                border: '1px solid #404040',
                cursor: 'pointer',
                background: 'transparent',
                color: '#fafafa',
              }}
            >
              새로고침
            </button>
          </div>
        </div>
      </body>
    </html>
  );
}
