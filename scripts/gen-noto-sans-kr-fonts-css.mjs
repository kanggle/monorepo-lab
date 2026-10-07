#!/usr/bin/env node
// =============================================================================
// gen-noto-sans-kr-fonts-css.mjs — regenerate the self-hosted Noto Sans KR
// @font-face CSS (TASK-MONO-767 → TASK-MONO-767 follow-up, owner decision
// 2026-10-07: self-host Korean glyphs too, unicode-range-split).
// =============================================================================
// next/font/local's `src` array cannot express a per-entry `unicode-range`
// (checked against `next/dist/compiled/@next/font/dist/local/
// validate-local-font-function-call.d.ts` — the FontOptions.src item type is
// `{ path, weight?, style?, ext, format }`, no unicode-range field). So the
// self-hosted Korean coverage is a hand-authored-glue + generated @font-face
// CSS file instead of a `localFont({ src: [...] })` call.
//
// This script re-derives that generated CSS from the upstream
// @fontsource/noto-sans-kr package (OFL-1.1), which ships the exact same
// per-glyph-range split Google's own css2 endpoint serves (confirmed byte-
// identical unicode-range boundaries across the 4 weights used here).
//
// Usage:
//   1. Fetch the package once (no permanent devDependency needed):
//        npm pack @fontsource/noto-sans-kr@5.3.0
//        tar xzf fontsource-noto-sans-kr-5.3.0.tgz -C /tmp/fontsource-nskr
//   2. Generate:
//        node scripts/gen-noto-sans-kr-fonts-css.mjs \
//          /tmp/fontsource-nskr/package \
//          projects/<app>/.../fonts/noto-sans-kr.generated.css
//   3. Copy the referenced woff2 files (see the sibling .filelist.txt this
//      script writes next to the output) into the app's fonts/ and
//      fonts/korean/ directories, matching the paths this script writes into
//      the CSS (latin files keep the app's existing `noto-sans-kr-latin-
//      {weight}.woff2` names; Korean files keep fontsource's own
//      `noto-sans-kr-{index}-{weight}-normal.woff2` names under `korean/`).
//
// Only the `latin` and the unmarked/default (Korean + Hanja + CJK-symbol
// bulk — Google's own css2 response does not comment this one; fontsource
// numbers it) subsets are extracted. `latin-ext` / `cyrillic` / `vietnamese`
// are deliberately skipped — they were never requested by the pre-fix
// next/font `google`-loader config (`subsets: ['latin']`) either.
// =============================================================================
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const pkgDir = process.argv[2];
const outCss = process.argv[3];

if (!pkgDir || !outCss) {
  console.error('usage: node gen-noto-sans-kr-fonts-css.mjs <fontsource-package-dir> <out-css-path>');
  process.exit(1);
}

const weights = ['400', '500', '600', '700'];

function extractRules(cssText, weight) {
  const blocks = cssText.split(/\n\n+/).map((b) => b.trim()).filter(Boolean);
  const rules = [];
  for (const block of blocks) {
    const commentMatch = block.match(/\/\* noto-sans-kr-(\S+?)-normal \*\//);
    if (!commentMatch) continue;
    const idNoWeight = commentMatch[1].replace(new RegExp(`-${weight}$`), '');
    const isNumbered = /^\[\d+\]$/.test(idNoWeight);
    const isLatinOnly = idNoWeight === 'latin';
    if (!isNumbered && !isLatinOnly) continue;
    const srcMatch = block.match(/src: url\(\.\/files\/([^)]+\.woff2)\) format\('woff2'\)/);
    if (!srcMatch) continue;
    const woff2File = srcMatch[1];
    const unicodeRangeMatch = block.match(/unicode-range: ([^;]+);/);
    const unicodeRange = unicodeRangeMatch ? unicodeRangeMatch[1].trim() : null;
    rules.push({
      kind: isNumbered ? 'korean' : 'latin',
      index: isNumbered ? parseInt(idNoWeight.slice(1, -1), 10) : null,
      weight,
      woff2File,
      unicodeRange,
    });
  }
  return rules;
}

const allRules = [];
for (const w of weights) {
  const cssText = readFileSync(join(pkgDir, `${w}.css`), 'utf8');
  allRules.push(...extractRules(cssText, w));
}

const korean = allRules.filter((r) => r.kind === 'korean');
const latin = allRules.filter((r) => r.kind === 'latin');

console.error(`korean rules: ${korean.length} (expect 120 x ${weights.length} weights)`);
console.error(`latin rules: ${latin.length} (expect 1 x ${weights.length} weights)`);

function ruleCss(r) {
  const path = r.kind === 'korean'
    ? `./korean/${r.woff2File}`
    : `./${r.woff2File.replace('-normal.woff2', '.woff2')}`;
  return [
    `@font-face {`,
    `  font-family: 'Noto Sans KR';`,
    `  font-style: normal;`,
    `  font-weight: ${r.weight};`,
    `  font-display: swap;`,
    `  src: url('${path}') format('woff2');`,
    r.unicodeRange ? `  unicode-range: ${r.unicodeRange.split(',').map((s) => s.trim()).join(', ')};` : null,
    `}`,
  ].filter(Boolean).join('\n');
}

latin.sort((a, b) => Number(a.weight) - Number(b.weight));
korean.sort((a, b) => Number(a.weight) - Number(b.weight) || a.index - b.index);

const header = `/* GENERATED — do not hand-edit (TASK-MONO-767).
 * Regenerate with scripts/gen-noto-sans-kr-fonts-css.mjs — see that file's
 * header for how to fetch the upstream package. Source: Noto Sans KR via
 * @fontsource/noto-sans-kr@5.3.0 (OFL-1.1, see ../LICENSE-OFL.txt), weights
 * 400/500/600/700, subsets 'latin' + the unmarked/default subset (Google's
 * own label for this family's Korean+Hanja+CJK-symbol bulk — 120 unicode-
 * range-split files per weight, identical split across all 4 weights).
 * Deliberately excluded (not requested by the pre-fix config either):
 * latin-ext, cyrillic, vietnamese.
 */

`;

const css = header + latin.map(ruleCss).join('\n\n') + '\n\n' + korean.map(ruleCss).join('\n\n') + '\n';

writeFileSync(outCss, css, 'utf8');
console.error(`wrote ${outCss} (${css.length} bytes, ${latin.length + korean.length} @font-face rules)`);

const fileList = [...latin.map((r) => r.woff2File), ...korean.map((r) => r.woff2File)];
writeFileSync(outCss + '.filelist.txt', fileList.join('\n') + '\n', 'utf8');
