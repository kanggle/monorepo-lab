import type { MetadataRoute } from 'next';

/**
 * TASK-PC-FE-297 — web app manifest (Next.js metadata file convention →
 * `/manifest.webmanifest`). Icons are the two metadata-convention icon files
 * next to this one (`icon.svg`, `apple-icon.tsx`). Black background to match
 * the icon tile.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Platform Console',
    short_name: 'Console',
    description:
      'Unified operations console for the enterprise suite (iam · wms · scm · erp · finance)',
    start_url: '/dashboards/overview',
    display: 'standalone',
    background_color: '#000000',
    theme_color: '#000000',
    icons: [
      { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml' },
      { src: '/apple-icon', sizes: '180x180', type: 'image/png' },
    ],
  };
}
