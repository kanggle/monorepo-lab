# Task ID

TASK-PC-FE-038

# Title

`console-web` — retheme the platform console to a **Vercel-style** aesthetic: Geist typography + a near-black/white minimalist palette + **dark/light mode (system default + toggle)** + accent-element polish (Vercel-style sticky top bar, buttons, surfaces). Implemented via CSS-variable design tokens so the retheme propagates through the existing semantic Tailwind classes (`bg-background` / `text-foreground` / `border-border` / …) the components already use; the dormant `dark:` variants already present in feature components activate once `darkMode: 'class'` + a `.dark` root class exist.

# Status

done

# Owner

frontend-engineer (design-token retheme + theme infra + shell polish; no API/contract/domain change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **no dependency on**: any backend/contract/ADR change. Pure presentation layer (console-web). Tenant/auth/federation behaviour unchanged.
- **respects**: the MONO-166 console-web PR CI gate (unit + `tsc --noEmit` + lint must stay green) and the frontend-app performance budget (architecture.md § Performance Budget — keep added deps minimal: `geist` font + `next-themes`).
- **leverages**: feature components already carry `dark:` variants (e.g. `WmsOpsScreen` amber badges, `DomainHealthCard` status accents) that are currently dormant (no `.dark` infra) — this task activates them.

# Goal

The console reads as a Vercel-style product: Geist font, high-contrast minimalist neutrals, subtle borders, generous whitespace, a sticky blurred top bar, and a working system-aware dark/light toggle — with **zero change to behaviour, routing, data-testids, or API**.

# Scope

## In Scope

- **Design tokens → CSS variables** (`globals.css`): `:root` (light Vercel palette) + `.dark` (dark Vercel palette) for `background/foreground/border/muted(+foreground)/primary(+foreground)/destructive(+foreground)` plus new `accent(+foreground)`/`ring`. Vercel/Geist neutrals (light bg `#fff` / fg `~#171717` / border `#ebebeb`; dark bg `#0a0a0a` / fg `#ededed` / border `#272727`).
- **`tailwind.config.ts`**: `darkMode: 'class'`; colors → `hsl(var(--token))`; add `accent`/`ring`.
- **Geist font** (`app/layout.tsx`): `geist/font` (Sans + Mono) applied to `<html>` (+ `suppressHydrationWarning` for the theme class).
- **Theme infra**: `next-themes` `ThemeProvider` (attribute=`class`, `defaultTheme="system"`, `enableSystem`) wrapping the root; a `ThemeToggle` client component (light/dark/system) in the top bar.
- **Shell polish** (`(console)/layout.tsx`): top bar from inverted `bg-primary` → Vercel `sticky top-0 bg-background/80 backdrop-blur border-b border-border`; nav links `text-muted-foreground hover:text-foreground` + active state; toggle added to the right cluster. **(Critical: the current `bg-primary` bar would render WHITE in dark mode — must move to `bg-background`.)**
- **Primitive polish** (`shared/ui/Button.tsx`, optional `Card.tsx`): subtle Vercel hover/active; both already token-based so the palette flows automatically.
- **Login page** (`(auth)/login`): align to the new tokens/font (it sits outside the `(console)` layout).

## Out of Scope

- No feature-screen rewrites — the token retheme + dark activation handle their colours; this task does not redesign per-domain ops tables/forms beyond what the shared primitives + tokens change.
- No status-colour palette change (amber/green/red semantic badges keep their meaning; their existing `dark:` variants simply activate).
- No API / proxy / contract / auth / tenant logic change. No new routes.

# Acceptance Criteria

- [x] **AC-1** Light + dark themes both via CSS-var tokens; theme defaults to `system` and a top-bar toggle (`data-testid="theme-toggle"`) switches light↔dark + persists (next-themes, attribute=class, pre-hydration script for no FOUC + `suppressHydrationWarning` on `<html>`).
- [x] **AC-2** Geist Sans/Mono applied app-wide (`font-sans` ← `--font-geist-sans`); top bar is the Vercel sticky/blurred/bordered bar (`sticky top-0 bg-background/80 backdrop-blur border-b`), no inverted `bg-primary` bar; nav data-testids (`nav-dashboards` … `nav-erp`) + routes unchanged.
- [x] **AC-3** `pnpm test` (780/780) + `tsc --noEmit` (exit 0) + `next lint` (clean) + `next build` (success; /login 109 kB < 180 kB budget) all green. No data-testid / route / API change.

# Related Specs

- `console-web` `architecture.md` § Tech Stack / § Performance Budget / § Server vs Client Components (ThemeProvider is a client boundary; font + tokens are layout-level).

# Edge Cases

- **FOUC / hydration**: `next-themes` injects a pre-paint script + `suppressHydrationWarning` on `<html>` prevents the class-mismatch warning.
- **`bg-primary` top bar inversion**: explicitly moved to `bg-background` so dark mode is correct (primary flips to near-white in dark, which as a bar bg would be wrong).
- **Dormant `dark:` variants**: once `.dark` is set, the pre-existing `dark:bg-amber-950/40` etc. activate — verify they read sensibly (they were authored for exactly this).

# Failure Scenarios

- If `next-themes` is undesirable (extra dep), a minimal inline-script + cookie/localStorage toggle is the fallback — but next-themes is the de-facto SSR-safe standard (shadcn/Vercel use it) and tiny; chosen for FOUC-correctness.

# Test Requirements

- `pnpm test` green (unit tests assert routing/logic, not chrome classes — verified: no `toHaveClass`/`bg-primary` assertions — so the retheme does not break them).
- `tsc --noEmit` + `next lint` + `next build` green (MONO-166 gate parity).
- Manual/visual: light + dark render; toggle works (AC-1).

# Definition of Done

- [x] Tokens (CSS vars light+dark) + `darkMode:'class'` + Geist + next-themes ThemeProvider + ThemeToggle + Vercel top bar + Button polish + login alignment.
- [x] `pnpm test` + `tsc --noEmit` + `next lint` + `next build` green.
- [x] No behaviour/routing/data-testid/API change; diff confined to console-web presentation layer + task lifecycle.
- [x] Task md + `INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1055 squash `8d38c3df`, 3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 요청 "콘솔 UI를 Vercel 스타일로". 결정(2026-06-03): 다크+라이트(system+토글) + 토큰+폰트+앤셜폴리시 깊이. **메타: 컴포넌트가 이미 시맨틱 토큰(`bg-background` 등) + 잠든 `dark:` 변형을 갖고 있어, CSS-변수 토큰화 + `darkMode:'class'` 만으로 전 화면 Vercel 팔레트 + 다크모드가 전파 — 컴포넌트별 재작성 불요(앤셜/프리미티브 폴리시만). 단 inverted `bg-primary` topbar 는 다크모드에서 흰 바가 되므로 `bg-background+border-b` 로 필수 전환.**
