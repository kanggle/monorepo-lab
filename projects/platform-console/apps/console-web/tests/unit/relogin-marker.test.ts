/**
 * `TASK-PC-FE-278` — 401 재로그인 마커가 **모든 지점에 붙어 있는가.**
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 왜 상수가 아니라 이 테스트가 규율을 지키는가
 * -----------------------------------------------------------------------------
 * 401 지점은 53곳이고, 전부 `redirect('/login?error=session_expired')` 를 **리터럴로**
 * 쓴다. 공유 상수를 import 하게 만들 수도 있었지만 그것은 규율을 강제하지 못한다 —
 * **새 코드가 그냥 `redirect('/login')` 을 써도 상수는 아무 말도 안 한다.** 그리고 그때
 * 생기는 결함은 조용하다: 화면은 멀쩡히 뜨고, 방문자는 반짝임 뒤 카탈로그로 돌아오며,
 * 아무 테스트도 빨개지지 않는다(이 티켓 이전의 상태가 정확히 그랬다).
 *
 * ⇒ 강제는 여기서 한다. 소스를 읽어 **마커 없는 `redirect('/login')`** 을 찾는다.
 *
 * -----------------------------------------------------------------------------
 * 🔴 무엇을 안 보는지 — 명시한다 (덮지 않는 축을 숨기지 않는다)
 * -----------------------------------------------------------------------------
 * **주석 행은 검사하지 않는다.** 이 저장소의 JSDoc 은 60곳에서 옛 형태
 * `redirect('/login')` 을 인용하며 §2.5 resilience 분류를 서술한다. 그 인용들은 당시의
 * 서술이고 고쳐 쓰면 기록이 거짓이 된다 — 그래서 이 티켓은 **코드 53곳만** 바꿨고
 * 주석 60곳은 그대로 뒀다. 대가는 이 가드가 «주석에서 옛 형태를 복사해 온 새 코드»를
 * 못 막는다는 것이 아니라 — 막는다. 복사한 결과가 **코드 행**이 되는 순간 걸린다.
 * 못 막는 것은 「주석 자체가 낡아 보이는 것」뿐이고, 그것은 결함이 아니라 읽기 불편함이다.
 */

import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { RE_LOGIN_PATH, SESSION_EXPIRED } from '@/shared/lib/re-login';

const SRC = join(process.cwd(), 'src');
const BARE = "redirect('/login')";
const MARKED = `redirect('${RE_LOGIN_PATH}')`;

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (/\.tsx?$/.test(name)) out.push(p);
  }
  return out;
}

/** 주석 행(`*`, `//`, `/*` 로 시작)을 뺀 코드 행만. */
function codeLines(file: string): { line: string; n: number }[] {
  return readFileSync(file, 'utf8')
    .split('\n')
    .map((line, i) => ({ line, n: i + 1 }))
    .filter(({ line }) => !/^\s*(\*|\/\/|\/\*)/.test(line));
}

function scan(needle: string): string[] {
  const hits: string[] = [];
  for (const file of walk(SRC)) {
    for (const { line, n } of codeLines(file)) {
      if (line.includes(needle)) {
        hits.push(`${file.slice(SRC.length + 1).replace(/\\/g, '/')}:${n}`);
      }
    }
  }
  return hits;
}

describe('401 재로그인 마커 (TASK-PC-FE-278)', () => {
  it('🔴🔴 마커 없는 redirect(\'/login\') 이 코드에 하나도 없다', () => {
    // 🔴 `MARKED` 도 `BARE` 를 부분문자열로 갖지 않는다 — `')` 까지 포함해 비교하므로
    //    `redirect('/login?error=…')` 는 여기 안 걸린다. 술어가 자기 수정본을 세지 않는다.
    const bare = scan(BARE);

    expect(
      bare,
      [
        '마커 없는 401 재로그인이 남아 있다.',
        `  써야 하는 형태: redirect('${RE_LOGIN_PATH}')`,
        '  이유: /login 은 쿠키만 보고 인증을 판정하므로(isAuthenticated),',
        '        마커가 없으면 /console 로 되튕겨 재로그인이 일어나지 않는다.',
        '        증상은 「반짝임 + 무설명」이고 다른 어떤 테스트도 빨개지지 않는다.',
      ].join('\n'),
    ).toEqual([]);
  });

  it('🔵 비공허성 — 이 가드가 실제로 무언가를 보고 있다', () => {
    // 🔴 정확한 개수(53)를 박지 않는다. 섹션이 정당하게 늘거나 줄 때마다 빨개지는 핀은
    //    가드가 아니라 마찰이고, 결국 꺼진다. 여기서 재려는 것은 «모집단이 비지 않았나»다
    //    — 0 이면 위 칸의 초록이 «아무것도 안 봤다» 는 뜻이 되므로 그때 알아야 한다.
    const marked = scan(MARKED);
    expect(marked.length).toBeGreaterThan(0);
  });
});

/**
 * `TASK-MONO-690` — 위 두 칸의 술어(리터럴 `redirect('/login')` 문자열 대조)는
 * `(console)/layout.tsx` 의 카탈로그 401 분기를 놓쳤다: 그 분기는
 * `redirect(await buildLoginRedirect())` 였고 소스에 리터럴 `'/login'` 문자열이
 * 아예 없어 `scan(BARE)` 의 모집단 밖이었다(Goal 표 참조).
 *
 * 🔴 "이 한 자리만 리터럴로 더 잡는다"는 이 티켓의 Failure Scenario 1 이 이름 붙인
 * 바로 그 실패다 — 다음 모양 변형이 또 새로 모집단 밖이 된다. 그래서 술어를 **문자열
 * 모양이 아니라 의미**로 다시 세운다: *"401 을 잡는 `catch` 분기가 `redirect(` 를
 * 부르면, 그 호출은 반드시 마커(`RE_LOGIN_PATH` 식별자 또는 `SESSION_EXPIRED` 값
 * `'session_expired'`)를 실어야 한다"* — 리터럴이든 상수든 어떤 호출 모양이든
 * 잡는다.
 *
 * -----------------------------------------------------------------------------
 * 🔴 창(window) 설계 — 정확한 AST 대신 8행 창을 쓰는 이유와 그 대가
 * -----------------------------------------------------------------------------
 * 이 저장소는 의도적으로 AST 파서를 쓰지 않는다(위 BARE/MARKED 술어의 설계와 같은
 * 이유 — 소스를 읽는 단순한 텍스트 스캐너가 전체 규율이다). `err.status === 401`
 * 행에서 그 아래 **최대 8줄**(주석 제외) 안에서 `redirect(...)` 호출을 찾고, 그
 * 안에서 마커를 찾는다. 8줄은 이 저장소가 실제로 쓰는 두 모양
 * (한 줄 `if (...) redirect(...)` · 두 줄 `if (...) { redirect(...); }`)을
 * 넉넉히 덮는다 — 실측: 아래 비공허성 칸이 이 술어가 **редирект 없는** 401 분기
 * (예: `unauthorized: true` 플래그만 반환하는 분기)를 오탐하지 않는다는 것도
 * 같이 증명한다(그런 분기는 창 안에 `redirect(` 가 없어 애초에 population 밖).
 */
describe('🔴🔴 401 분기의 redirect 는 모양과 무관하게 마커를 실어야 한다 (TASK-MONO-690)', () => {
  const MAX_LOOKAHEAD_LINES = 8;

  function scan401BranchesMissingMarker(): string[] {
    const hits: string[] = [];
    for (const file of walk(SRC)) {
      const raw = readFileSync(file, 'utf8').split('\n');
      const isCommentLine = (line: string) => /^\s*(\*|\/\/|\/\*)/.test(line);
      for (let i = 0; i < raw.length; i++) {
        if (isCommentLine(raw[i])) continue;
        if (!/\.status === 401\b/.test(raw[i])) continue;

        const windowLines: string[] = [];
        for (
          let j = i, taken = 0;
          j < raw.length && taken < MAX_LOOKAHEAD_LINES;
          j++
        ) {
          if (isCommentLine(raw[j])) continue;
          windowLines.push(raw[j]);
          taken++;
        }
        const windowText = windowLines.join('\n');

        const redirectCall = windowText.match(/redirect\(([\s\S]*?)\);/);
        if (!redirectCall) continue; // this 401 branch never calls redirect() — outside this guard's population (e.g. returns an `unauthorized` flag instead)

        const arg = redirectCall[1];
        const carriesMarker =
          arg.includes(SESSION_EXPIRED) || arg.includes('RE_LOGIN_PATH');
        if (!carriesMarker) {
          hits.push(
            `${file.slice(SRC.length + 1).replace(/\\/g, '/')}:${i + 1}`,
          );
        }
      }
    }
    return hits;
  }

  it('🔴🔴 401 분기가 redirect() 를 부르면서 마커를 안 싣는 자리가 하나도 없다', () => {
    const hits = scan401BranchesMissingMarker();
    expect(
      hits,
      [
        '401 분기가 마커 없는 redirect() 를 부른다.',
        `  써야 하는 형태: redirect('${RE_LOGIN_PATH}') 또는 redirect(RE_LOGIN_PATH)`,
        '  이유: 이 분기가 도달했다는 것은 백엔드가 이미 이 세션의 토큰을 거절했다는',
        '        뜻이다 — 마커 없이 /login 으로 보내면 쿠키만 보는 판정에 되튕겨',
        '        (isAuthenticated 는 쿠키 전용) 재로그인이 일어나지 않는다(TASK-MONO-690).',
      ].join('\n'),
    ).toEqual([]);
  });

  it('🔵 비공허성 — 이 창(window) 술어가 실제로 redirect() 를 부르는 401 분기를 보고 있다', () => {
    // 🔴 정확한 개수를 박지 않는다(위 비공허성 칸과 같은 이유) — 0 이면 이 술어가
    //    「아무 401 분기도 못 찾았다」는 뜻이므로 그때 알아야 한다.
    let seen = 0;
    for (const file of walk(SRC)) {
      const raw = readFileSync(file, 'utf8').split('\n');
      const isCommentLine = (line: string) => /^\s*(\*|\/\/|\/\*)/.test(line);
      for (let i = 0; i < raw.length; i++) {
        if (isCommentLine(raw[i])) continue;
        if (!/\.status === 401\b/.test(raw[i])) continue;
        const windowLines: string[] = [];
        for (
          let j = i, taken = 0;
          j < raw.length && taken < MAX_LOOKAHEAD_LINES;
          j++
        ) {
          if (isCommentLine(raw[j])) continue;
          windowLines.push(raw[j]);
          taken++;
        }
        if (/redirect\([\s\S]*?\);/.test(windowLines.join('\n'))) seen++;
      }
    }
    expect(seen).toBeGreaterThan(0);
  });
});
