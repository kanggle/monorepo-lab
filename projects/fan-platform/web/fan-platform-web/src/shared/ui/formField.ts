/**
 * 입력칸·선택칸의 공용 클래스 — **OS 다크 모드에서도 흰 바탕 + 검은 글자** (TASK-FAN-FE-026).
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 색을 전부 적는가
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 `globals.css` 가 `:root { color-scheme: light dark }` 이고, 다크 모드면 `body` 가
 *    `text-ink-50`(흰 글자)이 된다. Tailwind preflight 는 `input`/`select` 에 `color: inherit` 를
 *    주므로 글자색을 안 적은 칸은 흰 글자를 물려받고, 바탕을 안 적은 칸은 브라우저가 다크
 *    네이티브 바탕을 칠한다. 둘 중 하나만 적으면 흰 바탕에 흰 글자 / 어두운 바탕에 어두운
 *    글자가 된다 — 그래서 **바탕·글자·color-scheme 셋 다** 고정한다.
 * 🔴 `dark:` 변형은 쓰지 않는다 — `tailwind.config.ts` 가 `darkMode: 'class'` 인데 `<html>` 에
 *    `dark` 클래스를 다는 곳이 없어 `dark:` 는 한 번도 발화하지 않는다.
 * 🔵 `[color-scheme:light]` — 선택칸이 펼치는 목록, 검색칸의 지우기(×) 같은 **네이티브 부품**도
 *    밝게 그리게 한다. 바탕색만으로는 그 부품까지 닿지 않는다.
 * 🔵 `ink-300`/`ink-500`/`ink-700` 은 토큰에 없다(`ink` 는 50·100·200·400·600·800·900) — 적으면
 *    조용히 무효가 된다.
 */
export const FORM_FIELD_CLASS =
  'rounded-md border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 [color-scheme:light] focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-200';
