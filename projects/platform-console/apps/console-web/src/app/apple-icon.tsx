import { ImageResponse } from 'next/og';

/**
 * TASK-PC-FE-297 — Apple touch icon (Next.js metadata file convention). Same
 * design as `icon.svg` (black tile + white console glyph), rasterised at build
 * time because iOS does not accept an SVG touch icon. No external font/image
 * fetch — pure shapes, so the build stays offline-safe.
 */
export const size = { width: 180, height: 180 };
export const contentType = 'image/png';

export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#000',
        }}
      >
        <svg width="140" height="140" viewBox="0 0 64 64">
          <rect x="12" y="15" width="40" height="34" rx="5" fill="none" stroke="#fff" strokeWidth="4" />
          <path d="M12 24h40" stroke="#fff" strokeWidth="4" />
          <path
            d="m20 32 6 5-6 5M30 42h12"
            fill="none"
            stroke="#fff"
            strokeWidth="4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
    ),
    size,
  );
}
