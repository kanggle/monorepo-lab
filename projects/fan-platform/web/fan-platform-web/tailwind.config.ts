import type { Config } from 'tailwindcss';

/**
 * fan-platform design tokens.
 *
 * Identity: K-pop / fandom — purple-pink accent palette. Dark mode is enabled
 * via `class` strategy so the layout can opt-in via `<html class="dark">`.
 * Tokens are intentionally minimal — components compose with Tailwind utility
 * classes; no design-system primitive layer in v1.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#faf5ff',
          100: '#f3e8ff',
          200: '#e9d5ff',
          300: '#d8b4fe',
          400: '#c084fc',
          500: '#a855f7',
          600: '#9333ea',
          700: '#7e22ce',
          800: '#6b21a8',
          900: '#581c87',
        },
        accent: {
          50: '#fdf2f8',
          100: '#fce7f3',
          300: '#f9a8d4',
          500: '#ec4899',
          700: '#be185d',
        },
        ink: {
          50: '#fafafa',
          100: '#f4f4f5',
          200: '#e4e4e7',
          400: '#a1a1aa',
          600: '#52525b',
          800: '#27272a',
          900: '#18181b',
        },
      },
      fontFamily: {
        sans: ['var(--font-noto-sans-kr)', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

export default config;
