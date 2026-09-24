import type { ReactNode } from 'react';
import type { NavIconName } from './console-nav-config';

/**
 * TASK-PC-FE-297 — sidebar item icons.
 *
 * Inline SVG, hand-written, NO icon-library dependency (`package.json` has
 * none — checked 2026-09-24 UTC — and adding one for ~40 glyphs is bundle
 * weight + review load the ticket explicitly rules out). 24×24 stroke glyphs
 * in the same style as the sidebar's existing chevrons (`stroke="currentColor"`,
 * width 2, round caps), so each icon takes the item's text colour: white on the
 * dark theme, near-black on the light theme. A literal `#fff` would vanish on
 * the light theme's white sidebar — `currentColor` is what keeps «white icon»
 * readable in both.
 *
 * Decorative only (`aria-hidden`): the item's text label stays the accessible
 * name, so the accessibility tree is unchanged by the icons.
 */
const PATHS: Record<NavIconName, ReactNode> = {
  dashboard: (
    <>
      <path d="M3.3 19a10 10 0 1 1 17.4 0" />
      <path d="m12 14 4-5" />
      <circle cx="12" cy="14" r="1.5" />
    </>
  ),
  catalog: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </>
  ),
  guide: (
    <>
      <path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z" />
      <path d="M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z" />
    </>
  ),
  overview: (
    <>
      <path d="M3 3v18h18" />
      <path d="m7 15 4-4 3 3 5-6" />
    </>
  ),
  shield: <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />,
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8" r="4" />
      <path d="M2 21a7 7 0 0 1 14 0" />
      <path d="M16 3.5a4 4 0 0 1 0 9" />
      <path d="M22 21a7 7 0 0 0-4-6.3" />
    </>
  ),
  hierarchy: (
    <>
      <rect x="9" y="2" width="6" height="5" rx="1" />
      <rect x="2" y="17" width="6" height="5" rx="1" />
      <rect x="16" y="17" width="6" height="5" rx="1" />
      <path d="M12 7v5M5 17v-5h14v5" />
    </>
  ),
  building: (
    <>
      <rect x="4" y="2" width="16" height="20" rx="1" />
      <path d="M9 22v-4h6v4M8 6h.01M12 6h.01M16 6h.01M8 10h.01M12 10h.01M16 10h.01M8 14h.01M12 14h.01M16 14h.01" />
    </>
  ),
  key: (
    <>
      <circle cx="7.5" cy="15.5" r="5.5" />
      <path d="m11.5 11.5 9-9M16 7l3 3M18.5 4.5l2 2" />
    </>
  ),
  lock: (
    <>
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </>
  ),
  audit: (
    <>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
      <circle cx="11.5" cy="14.5" r="2.5" />
      <path d="m13.3 16.3 2.2 2.2" />
    </>
  ),
  identity: (
    <>
      <rect x="2" y="5" width="20" height="14" rx="2" />
      <circle cx="8" cy="12" r="2" />
      <path d="M5 16a3 3 0 0 1 6 0M14 10h5M14 14h4" />
    </>
  ),
  subscription: (
    <>
      <path d="m12 2 9 5-9 5-9-5z" />
      <path d="m3 12 9 5 9-5" />
      <path d="m3 17 9 5 9-5" />
    </>
  ),
  partnership: (
    <>
      <path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7" />
      <path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" />
    </>
  ),
  warehouse: (
    <>
      <path d="M22 8.4V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.4L12 3z" />
      <path d="M6 22V12h12v10M6 16h12" />
    </>
  ),
  truck: (
    <>
      <path d="M14 18V6H2v12h2" />
      <path d="M14 9h4l4 4v5h-2" />
      <circle cx="7" cy="18" r="2" />
      <circle cx="17" cy="18" r="2" />
      <path d="M9 18h6" />
    </>
  ),
  wallet: (
    <>
      <path d="M20 7V5a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2" />
      <path d="M3 6v12a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-3" />
    </>
  ),
  ledger: (
    <>
      <path d="M4 19.5V4.5A2.5 2.5 0 0 1 6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5z" />
      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M9 7h7M9 11h5" />
    </>
  ),
  cart: (
    <>
      <circle cx="9" cy="20" r="1.5" />
      <circle cx="18" cy="20" r="1.5" />
      <path d="M2 3h3l2.7 12.4a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 2-1.6L22 7H6" />
    </>
  ),
  inbound: (
    <>
      <path d="M12 3v12M7 10l5 5 5-5" />
      <path d="M4 21h16" />
    </>
  ),
  outbound: (
    <>
      <path d="M12 15V3M7 8l5-5 5 5" />
      <path d="M4 21h16" />
    </>
  ),
  package: (
    <>
      <path d="m21 8-9-5-9 5v8l9 5 9-5z" />
      <path d="m3 8 9 5 9-5M12 13v8" />
    </>
  ),
  database: (
    <>
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
      <path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
    </>
  ),
  settings: (
    <>
      <path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3" />
      <path d="M1 14h6M9 8h6M17 16h6" />
    </>
  ),
  clipboard: (
    <>
      <rect x="5" y="4" width="14" height="18" rx="2" />
      <path d="M9 2h6v4H9zM9 12h6M9 16h4" />
    </>
  ),
  refresh: (
    <>
      <path d="M21 12a9 9 0 0 1-15.5 6.2L3 16" />
      <path d="M3 12a9 9 0 0 1 15.5-6.2L21 8" />
      <path d="M21 3v5h-5M3 21v-5h5" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m21 21-4.3-4.3" />
    </>
  ),
  approval: (
    <>
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <path d="m8 12 3 3 5-6" />
    </>
  ),
  delegation: (
    <>
      <path d="M16 3h5v5M21 3l-7 7" />
      <path d="M8 21H3v-5M3 21l7-7" />
    </>
  ),
  tag: (
    <>
      <path d="M12.6 2.6A2 2 0 0 0 11.2 2H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.7 8.7a2.4 2.4 0 0 0 3.4 0l6.6-6.6a2.4 2.4 0 0 0 0-3.4z" />
      <circle cx="7.5" cy="7.5" r="1.5" />
    </>
  ),
  receipt: (
    <>
      <path d="M4 2v20l3-2 2.5 2 2.5-2 2.5 2 2.5-2 3 2V2l-3 2-2.5-2L12 4 9.5 2 7 4z" />
      <path d="M8 9h8M8 13h6" />
    </>
  ),
  percent: (
    <>
      <path d="M19 5 5 19" />
      <circle cx="6.5" cy="6.5" r="2.5" />
      <circle cx="17.5" cy="17.5" r="2.5" />
    </>
  ),
  store: (
    <>
      <path d="M3 9 5 3h14l2 6" />
      <path d="M3 9a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0" />
      <path d="M5 11v10h14V11M10 21v-5h4v5" />
    </>
  ),
  settlement: (
    <>
      <rect x="2" y="6" width="20" height="12" rx="2" />
      <circle cx="12" cy="12" r="2.5" />
      <path d="M6 12h.01M18 12h.01" />
    </>
  ),
  bell: (
    <>
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
    </>
  ),
};

export function NavIcon({ name }: { name: NavIconName }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      data-nav-icon={name}
      className="shrink-0"
    >
      {PATHS[name]}
    </svg>
  );
}
