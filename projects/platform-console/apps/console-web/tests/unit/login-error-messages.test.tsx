/**
 * `/login` 의 에러 문구 — **진짜 페이지를 태워서** 잰다 (`TASK-PC-FE-279`).
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 이 파일의 옛 판은 자기가 지키려던 맵을 **복제**해 두고 그 복제본을 검사했다
 * -----------------------------------------------------------------------------
 * 머리에 이렇게 적혀 있었다: *"We replicate the lookup logic here … **If the map or
 * constant changes in the source, update this file accordingly.**"*
 *
 * 그 지시는 지켜지지 않았다. `TASK-PC-FE-279` AC-0 이 기계로 대조한 결과:
 *
 * | 축 | 결과 |
 * |---|---|
 * | 값 불일치 | **1** — `not_provisioned`(소스는 `ADR-MONO-044` 로 *"아직 소속된 조직이 없습니다…"*, 복제본은 그 이전 화석 *"운영자 권한이 없는 계정입니다…"*) |
 * | 소스에만 있는 키 | **1** — `session_expired` |
 * | 복제본에만 있는 키 | 0 |
 * | `GENERIC_ERROR` | 일치 |
 *
 * 🔴🔴 **`session_expired` 공백은 `TASK-PC-FE-278` 이 같은 날 만들었다** — 소스에 키를
 * 넣고 이 복제본을 안 고쳤다. 그 세션은 이 파일의 머리 주석을 **읽고 인용까지 했다.**
 * ⇒ 「사람이 두 곳을 맞춰 고친다」는 규율은 **그것을 방금 읽은 사람에게도 실패한다.**
 * 그리고 실패해도 이 스위트는 **초록이었다** — 자기 복제본을 검사하니까.
 *
 * -----------------------------------------------------------------------------
 * 🔵 그래서 무엇이 달라졌나 — **핀은 남기고, 비교 대상을 바꿨다**
 * -----------------------------------------------------------------------------
 * 아래 {@link EXPECTED} 도 문자열을 적어 둔다. 그것과 옛 복제본의 차이는 **무엇과
 * 대조되는가**다:
 *
 * - 옛 판: 복제본을 **복제본의 resolve 로직**에 먹였다 ⇒ 소스가 계산에 **한 번도 안 들어갔다.**
 * - 지금: 핀을 **진짜 페이지가 렌더한 DOM** 과 대조한다 ⇒ 소스가 바뀌면 **빨개진다.**
 *
 * 🔵 대가는 알고 받는다 — 문구를 정당하게 고치면 이 핀도 같이 고쳐야 한다.
 * 그것이 의도다: 문장이 계약이고(형제 `DemoBackendNotice.test.tsx` 가 같은 이유로
 * 리터럴을 고정한다), **실패가 조용하지 않은 쪽**을 골랐다.
 *
 * 🔵 소스는 한 줄도 안 고쳤다 — 페이지가 이미 `role="alert"` 를 달고 있어서
 * 접근성 역할로 잡으면 된다(`data-testid` 를 새로 심을 이유가 없다).
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { SESSION_EXPIRED } from '@/shared/lib/re-login';

const redirectMock = vi.fn();
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    redirectMock(path);
    throw new Error(`REDIRECT:${path}`);
  },
}));

// 🔵 이 스위트는 **익명 방문자**만 다룬다 — 인증된 방문자의 단락 회로는
//    `relogin-loop.test.tsx`(TASK-PC-FE-278) 가 대조군까지 갖춰 잰다.
vi.mock('@/shared/lib/session', () => ({ isAuthenticated: async () => false }));

// 🔴 props 를 **흘려보내야** 한다. `{children, href}` 만 받는 목은 `data-testid` 를
//    조용히 버리고, 그러면 대조군이 «렌더가 죽었다» 와 구별되지 않는다(실제로 밟았다).
vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    prefetch: _prefetch,
    ...rest
  }: {
    children: React.ReactNode;
    href: string;
    prefetch?: boolean;
  } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

// 🔴 **동기** 컴포넌트로 목한다. `async () => null` 은 React 가 async Client Component
//    으로 읽어 suspend 하고 렌더 결과가 통째로 빈다(`278` 이 실제로 밟았다).
vi.mock('@/widgets/demo-notice/DemoBackendNotice', () => ({
  DemoBackendNotice: () => null,
}));
vi.mock('@/widgets/demo-credentials/DemoLoginCredentials', () => ({
  DemoLoginCredentials: () => null,
}));

/**
 * 코드 → 방문자가 보게 되는 문장. **이것이 계약이다.**
 * 🔴 여기를 고칠 때는 소스(`(auth)/login/page.tsx`)를 같이 고쳐야 하는데,
 *    안 고치면 **이 스위트가 빨개진다** — 옛 판이 못 하던 그것이다.
 */
const EXPECTED: Record<string, string> = {
  provider_error: 'IAM 로그인 중 오류가 발생했습니다. 다시 시도해주세요.',
  invalid_state: '로그인 세션이 만료되었습니다. 다시 로그인해주세요.',
  state_mismatch: '보안 검증에 실패했습니다. 다시 로그인해주세요.',
  token_exchange_failed:
    '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.',
  not_provisioned:
    '아직 소속된 조직이 없습니다. 다시 로그인하면 조직 만들기로 안내됩니다.',
  operator_exchange_unavailable:
    '인증 서버 일시 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
  [SESSION_EXPIRED]: '세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요.',
};

/** 알 수 없는 코드가 받는 문장. */
const GENERIC = '로그인 중 오류가 발생했습니다. 다시 시도해주세요.';

async function renderLogin(sp: { error?: string; redirect?: string } = {}) {
  const { default: LoginPage } = await import('@/app/(auth)/login/page');
  const el = await LoginPage({ searchParams: Promise.resolve(sp) });
  render(<div data-testid="host">{el}</div>);
}

/** 렌더된 경고의 텍스트(없으면 null). 페이지가 이미 다는 `role="alert"` 로 잡는다. */
function alertText(): string | null {
  return screen.queryByRole('alert')?.textContent?.trim() ?? null;
}

beforeEach(() => {
  vi.resetModules();
  redirectMock.mockClear();
});

describe('/login 에러 문구 — 소스를 태운다 (TASK-PC-FE-279)', () => {
  for (const [code, message] of Object.entries(EXPECTED)) {
    it(`🔴 \`?error=${code}\` → 핀과 «렌더된 소스»가 일치한다`, async () => {
      await renderLogin({ error: code });
      expect(alertText()).toBe(message);
    });
  }

  it('🔴🔴 알 수 없는 코드도 조용하지 않다 — fallback 이 실제로 그려진다', async () => {
    await renderLogin({ error: 'a-code-nobody-has-written-yet' });
    expect(alertText()).toBe(GENERIC);
  });

  it('🔵 대조군 — `error` 가 없으면 경고가 아예 없다 (페이지는 렌더된다)', async () => {
    await renderLogin();
    expect(alertText()).toBeNull();
    expect(screen.getByTestId('iam-login')).toBeInTheDocument();
  });

  it('🔵 대조군 — 빈 `error` 도 경고를 만들지 않는다', async () => {
    await renderLogin({ error: '' });
    expect(alertText()).toBeNull();
    expect(screen.getByTestId('iam-login')).toBeInTheDocument();
  });
});

describe('/login 에러 문구 — 의미 (리터럴이 아니라 «무엇을 약속하는가»)', () => {
  // 🔵 옛 판에서 이 축을 그대로 가져왔다. 리터럴 핀이 있어도 이 칸들은 남긴다 —
  //    핀은 «바뀌었나» 를 잡고, 이 칸들은 «틀린 방향으로 바뀌었나» 를 잡는다.
  it('🔴 `not_provisioned` 은 권한 요청이 아니라 **조직 만들기**로 안내한다 (ADR-MONO-044)', async () => {
    await renderLogin({ error: 'not_provisioned' });
    const t = alertText()!;
    expect(t).toContain('조직');
    // 🔴 이 칸이 바로 이 티켓이 드러낸 화석을 문다 — 옛 복제본의 문구가 되살아나면 빨개진다.
    expect(t).not.toContain('관리자');
    expect(t).not.toContain('권한');
  });

  it('🔴 `state_mismatch` 는 재시도가 아니라 **재로그인**을 말한다', async () => {
    await renderLogin({ error: 'state_mismatch' });
    expect(alertText()).toContain('다시 로그인');
  });

  it('🔴 `session_expired` 는 «로그아웃됐다» 는 사실을 말한다 (반짝임만 남기지 않는다)', async () => {
    await renderLogin({ error: SESSION_EXPIRED });
    expect(alertText()).toContain('세션이 만료');
  });
});

describe('/login 에러 문구 — 도달 가능한 코드가 전부 매핑돼 있다', () => {
  // 🔴 AC-0 이 «reachable 6개, 미매핑 0» 을 쟀다. 게이트 없는 숫자는 반드시 낡으므로
  //    그 측정을 여기서 가둔다: `callback/route.ts` 가 새 사유를 추가하면 빨개진다.
  it('🔴 `callback/route.ts` 의 모든 `loginRedirect` 사유가 전용 문구를 갖는다', () => {
    const src = readFileSync(
      join(process.cwd(), 'src/app/api/auth/callback/route.ts'),
      'utf8',
    );
    // 🔴 술어를 인자의 «모양» 에 걸지 마라. 첫 판은
    //    `loginRedirect\([^)]*?'…'` 였는데 첫 인자가 `publicOrigin(env)` 라
    //    그 안의 `)` 에 걸려 **0건**을 냈다(비공허성 칸이 그것을 잡았다).
    //    지금 술어: 선언(`function loginRedirect(`)을 뺀 **호출** 각각에 대해,
    //    그 뒤 120자 안의 첫 소문자 문자열 리터럴 = 사유. 인자 모양에 무관하다.
    const reasons = [...src.matchAll(/(?<!function )loginRedirect\(/g)]
      .map((m) => src.slice(m.index, m.index + 120).match(/'([a-z_]+)'/)?.[1])
      .filter((r): r is string => Boolean(r));

    // 🔵 비공허성 — 술어가 아무것도 못 찾았으면 위 단언은 공허하게 통과한다.
    expect(reasons.length).toBeGreaterThan(0);

    const unmapped = [...new Set(reasons)].filter((r) => !(r in EXPECTED));
    expect(
      unmapped,
      [
        '`/login` 으로 보내는 사유인데 전용 문구가 없다 — generic fallback 으로 떨어진다.',
        `  미매핑: ${unmapped.join(', ')}`,
        '  고칠 곳: (auth)/login/page.tsx 의 ERROR_MESSAGES **와** 이 파일의 EXPECTED.',
      ].join('\n'),
    ).toEqual([]);
  });

  it('🔵 `session_expired` 는 콘솔 화면들이 붙이는 마커라 callback 밖에서 온다', () => {
    // 🔵 위 칸의 모집단(callback)이 이 코드를 **안 담는 것이 정상**이라는 것을 적어 둔다.
    //    담겨 있기를 기대하면 그 칸이 잘못된 이유로 빨개진다.
    expect(EXPECTED[SESSION_EXPIRED]).toBeTruthy();
  });

  // 🔴🔴 이 칸이 없으면 이 스위트는 **자기가 태어난 이유가 된 드리프트를 못 잡는다.**
  //
  // 위 핀 칸들은 `EXPECTED` 에 **있는** 키만 렌더해 본다. 그래서 소스가 키를 «새로
  // 얻고» 핀이 그것을 모르면 아무 칸도 안 돈다 — `session_expired` 가 정확히 그
  // 경로로 새어 들었다(`TASK-PC-FE-278` 이 소스에만 넣었고, 옛 복제본도 이 스위트도
  // 침묵했다). callback 커버리지 칸도 못 잡는다: 그 코드는 callback 이 아니라
  // `(console)` 아래 53개 화면이 붙이기 때문이다.
  //
  // ⇒ 소스의 **키 집합**을 직접 읽어 `EXPECTED` 와 등호로 맞춘다.
  it('🔴🔴 소스 맵의 키 집합이 핀과 «양방향»으로 일치한다 (새 키가 조용히 늘지 않는다)', () => {
    const src = readFileSync(
      join(process.cwd(), 'src/app/(auth)/login/page.tsx'),
      'utf8',
    );
    const block = src.slice(
      src.indexOf('const ERROR_MESSAGES'),
      src.indexOf('};', src.indexOf('const ERROR_MESSAGES')),
    );
    expect(block.length, 'ERROR_MESSAGES 블록을 못 찾았다 — 술어가 낡았다').toBeGreaterThan(0);

    const sourceKeys = block
      .split('\n')
      .filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l)) // 주석 줄 제외
      .flatMap((l) => {
        const m = l.match(/^\s*(?:\[([A-Za-z_][A-Za-z0-9_]*)\]|([a-z_]+))\s*:/);
        if (!m) return [];
        // 계산된 키(`[SESSION_EXPIRED]`)는 상수의 **값**으로 정규화한다.
        if (m[1] === 'SESSION_EXPIRED') return [SESSION_EXPIRED];
        return [m[1] ?? m[2]];
      });

    // 🔵 비공허성 — 술어가 아무것도 못 읽었으면 아래 등호는 «둘 다 비어서» 통과할 수 있다.
    expect(sourceKeys.length).toBeGreaterThan(0);

    expect(
      [...sourceKeys].sort(),
      [
        '소스 맵과 이 파일의 EXPECTED 가 키 집합에서 갈렸다.',
        '  소스에만 있는 키 = 방문자는 문구를 보는데 이 스위트는 그것을 한 번도 안 렌더한다.',
        '  핀에만 있는 키   = 더 이상 없는 코드를 재고 있다.',
        '  둘 다 이 티켓(TASK-PC-FE-279)이 고친 그 드리프트다.',
      ].join('\n'),
    ).toEqual(Object.keys(EXPECTED).sort());
  });
});
