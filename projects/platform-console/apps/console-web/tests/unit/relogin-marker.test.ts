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
import { RE_LOGIN_PATH } from '@/shared/lib/re-login';

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
