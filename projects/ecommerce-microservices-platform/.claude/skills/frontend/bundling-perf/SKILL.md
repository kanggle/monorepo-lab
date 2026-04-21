---
name: bundling-perf
description: Bundle analysis, code splitting, image/font, Core Web Vitals
category: frontend
---

# Skill: Bundling & Frontend Performance

Patterns for keeping Next.js bundles small and Core Web Vitals green.

Prerequisite: read `specs/platform/observability.md` and `cross-cutting/performance-tuning/SKILL.md` before using this skill.

---

## Core Web Vitals Targets

| Metric | Good | Needs Improvement | Source |
|---|---|---|---|
| LCP (Largest Contentful Paint) | < 2.5 s | < 4 s | Lighthouse / web-vitals |
| INP (Interaction to Next Paint) | < 200 ms | < 500 ms | web-vitals |
| CLS (Cumulative Layout Shift) | < 0.1 | < 0.25 | web-vitals |
| TTFB (Time to First Byte) | < 800 ms | < 1.8 s | server logs |

Measured at p75 of real users via `web-vitals` library reporting to the observability backend.

---

## Bundle Analysis

```bash
# next.config.js
const withBundleAnalyzer = require('@next/bundle-analyzer')({
  enabled: process.env.ANALYZE === 'true',
});
module.exports = withBundleAnalyzer({ /* ... */ });
```

```bash
ANALYZE=true pnpm build --filter=web-store
```

CI fails if first-load JS exceeds the per-route budget (default: 180 KB gzipped for landing routes, 250 KB for app routes).

---

## Code Splitting

| Technique | When |
|---|---|
| Route-level (automatic in App Router) | Always |
| `next/dynamic` for heavy components | Charts, editors, modals not in initial view |
| `next/dynamic` with `ssr: false` | Browser-only libs (e.g., charting) |
| Conditional import | Feature flags, A/B branches |

```tsx
const HeavyChart = dynamic(() => import('@/features/dashboard/components/HeavyChart'), {
  ssr: false,
  loading: () => <ChartSkeleton />,
});
```

Avoid `next/dynamic` for components < 20 KB — the loader overhead outweighs the saving.

---

## Image Optimization

- Always use `next/image` for raster images
- Set `priority` only on LCP image (one per page)
- Use `sizes` attribute for responsive images
- Prefer AVIF/WebP via `next.config.js` `images.formats`

```tsx
<Image
  src="/hero.jpg"
  alt="Hero"
  width={1200}
  height={600}
  priority
  sizes="(max-width: 768px) 100vw, 1200px"
/>
```

---

## Font Optimization

```tsx
// app/layout.tsx
import { Inter } from 'next/font/local';

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-inter',
});
```

- Use `next/font` (zero layout shift)
- `display: swap` to avoid invisible text
- Self-host critical fonts; avoid Google Fonts CDN
- Preload only the weights actually used

---

## Third-Party Scripts

Use `next/script` with appropriate strategy:

| Strategy | Use For |
|---|---|
| `beforeInteractive` | Polyfills (rare) |
| `afterInteractive` (default) | Analytics |
| `lazyOnload` | Chat widgets, social embeds |
| `worker` | Heavy analytics in Web Worker (experimental) |

Defer or remove anything not on the critical path.

---

## Server Components & Streaming

- Default to Server Components; opt into `'use client'` only when needed (event handlers, state, browser APIs)
- Use `<Suspense>` boundaries to stream slow data without blocking the shell
- Move data fetching to the deepest server component that needs it

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `import * from 'lodash'` | Use `lodash-es` with named imports or `lodash/<fn>` |
| Importing entire icon library | Use per-icon import (`lucide-react/icons/<name>`) |
| Client component at the page root | Push `'use client'` boundary as deep as possible |
| Unsized images | Always set width/height or `fill` + container |
| Layout shift from late-loading widgets | Reserve space with skeleton |
| Including `moment.js` | Switch to `date-fns` or `dayjs` |

---

## Verification Checklist

- [ ] First-load JS within budget (per-route)
- [ ] LCP image has `priority`
- [ ] All images use `next/image` with explicit dimensions
- [ ] Fonts via `next/font` with `display: swap`
- [ ] web-vitals reporting wired to observability backend
- [ ] Bundle analyzer report attached to PR for any > 10 KB regression
