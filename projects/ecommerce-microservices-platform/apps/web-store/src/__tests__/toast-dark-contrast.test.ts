/**
 * Toast 글자 대비 — 라이트·다크 두 테마 모두 WCAG AA(4.5:1) 이상 (TASK-FE-101).
 *
 * 🔴 결함: Toast 가 배경을 밝은 연녹으로 **고정**하고 글자색만 테마 토큰 `--color-text` 를 썼다. 다크 테마에서
 *    그 토큰이 거의 흰색으로 바뀌어 «장바구니에 추가되었습니다.» 가 밝은 배경 위 밝은 글자(대비 약 1.1:1)가 됐다.
 * 🔵 jsdom 은 CSS 변수를 풀지 못하므로 렌더 결과로는 이 결함을 잴 수 없다. 그래서 두 겹으로 잰다:
 *    ① `globals.css` 에서 **토큰의 실제 값**을 읽어 두 테마의 대비를 계산한다.
 *    ② Toast 가 색을 고정값으로 두지 않고 그 토큰을 쓰는지 소스로 문다.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const css = readFileSync(fileURLToPath(new URL('../app/globals.css', import.meta.url)), 'utf8');
const toastSource = readFileSync(fileURLToPath(new URL('../shared/ui/Toast.tsx', import.meta.url)), 'utf8');

/** 선택자 블록 하나에서 `--token: #rrggbb` 선언만 모은다. */
function tokensOf(selector: string): Record<string, string> {
  const start = css.indexOf(`${selector} {`);
  if (start < 0) throw new Error(`globals.css 에 '${selector} {' 블록이 없다`);
  const end = css.indexOf('}', start);
  const out: Record<string, string> = {};
  for (const m of css.slice(start, end).matchAll(/(--[a-z0-9-]+)\s*:\s*(#[0-9a-f]{6})\b/gi)) {
    out[m[1]] = m[2].toLowerCase();
  }
  return out;
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const AA = 4.5;
const DARK = ":root[data-theme='dark']";
const light = tokensOf(':root');
// 다크 블록은 바뀌는 토큰만 적는다 — 나머지는 :root 값을 물려받는다(브라우저와 같은 규칙).
const dark = { ...light, ...tokensOf(DARK) };
const SURFACES = ['--color-success-surface', '--color-error-surface'] as const;

describe('Toast 글자 대비 (TASK-FE-101)', () => {
  it('🔴 대조군 — 결함이던 조합(다크 글자 on 고정 연녹 배경)은 이 기준에 걸린다', () => {
    // 이 칸이 없으면 «대비 계산이 늘 통과하는» 고장난 판정기도 아래 칸들을 통과한다.
    expect(contrast(dark['--color-text'], '#f0fdf4')).toBeLessThan(2);
    expect(contrast(dark['--color-text'], '#f0fdf4')).toBeLessThan(AA);
  });

  for (const [theme, tokens] of [
    ['light', light],
    ['dark', dark],
  ] as const) {
    for (const surface of SURFACES) {
      it(`${theme}: --color-text 와 ${surface} 의 대비가 ${AA}:1 이상`, () => {
        const fg = tokens['--color-text'];
        const bg = tokens[surface];
        expect(fg, '--color-text 토큰이 없다').toMatch(/^#[0-9a-f]{6}$/);
        expect(bg, `${surface} 토큰이 없다`).toMatch(/^#[0-9a-f]{6}$/);
        expect(contrast(fg, bg)).toBeGreaterThanOrEqual(AA);
      });
    }
  }

  it('다크 테마가 토스트 표면을 직접 정의한다 (라이트 값을 그대로 물려받지 않는다)', () => {
    const darkOnly = tokensOf(DARK);
    for (const surface of SURFACES) {
      expect(darkOnly[surface], `다크 블록에 ${surface} 가 없다`).toBeDefined();
      expect(darkOnly[surface]).not.toBe(light[surface]);
    }
  });

  it('Toast 는 색을 고정 hex 로 두지 않고 테마 토큰을 쓴다', () => {
    expect(toastSource).not.toMatch(/#[0-9a-f]{3,8}\b/i);
    for (const t of [
      '--color-success-surface',
      '--color-success-border',
      '--color-error-surface',
      '--color-error-border',
      '--color-text',
    ]) {
      expect(toastSource).toContain(`var(${t})`);
    }
  });
});
