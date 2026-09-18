import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ServiceCatalog } from '@/features/catalog';
import type { CatalogState } from '@/shared/api/registry-types';

/**
 * 🔴 TASK-MONO-711 ③ — 「저하가 **마커가 아니라 부재**로 렌더되는 자리」.
 *
 * `/console` 의 도메인 상태 레그가 죽으면 예전에는 `healthByDomain` 이 빈 객체가
 * 되고 타일의 상태 점이 그냥 안 그려졌다. 문구도 없고 `data-testid` 마커도 없다
 * ⇒ 저하 술어(문구 ∪ 마커)의 **두 날개 어느 쪽에도 안 걸린다**. 2026-09-18 촬영이
 * 레그 하나가 죽은 이 화면을 «저하 아님»(본문 436자, 마커 0개)으로 집계한 이유다.
 *
 * 이 파일이 세 가지를 못박는다:
 *   ① 실패하면 마커가 **그려진다**.
 *   ② 테넌트 미선택(정상 상태)은 «말은 하되 저하로 세지 않는다».
 *   ③ ①의 마커가 촬영 스크립트가 **실제로 읽는** 접미사다 — 두 파일이 갈라지면 빨강.
 *
 * ③ 이 없으면 ① 은 공허하다: 아무도 안 보는 `data-testid` 를 그려도 초록이기 때문이다.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn(), push: vi.fn(), replace: vi.fn() }),
}));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const EMPTY_CATALOG: CatalogState = { products: [], degraded: false };

function renderCatalog(healthState?: 'ok' | 'no-tenant' | 'unavailable') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ServiceCatalog catalog={EMPTY_CATALOG} healthState={healthState} />
    </QueryClientProvider>,
  );
}

const UNAVAILABLE_TESTID = 'catalog-health-unavailable';
const NO_TENANT_TESTID = 'catalog-health-no-tenant';

describe('ServiceCatalog — 도메인 상태 레그의 부재를 말한다 (TASK-MONO-711 ③)', () => {
  it('레그가 죽으면 마커를 그린다 (부재가 아니라 표현)', () => {
    renderCatalog('unavailable');
    expect(screen.getByTestId(UNAVAILABLE_TESTID)).toBeInTheDocument();
    // 셸은 여전히 쓸 수 있다 — 저하를 드러내는 것이 화면을 비우는 것은 아니다.
    expect(screen.getByRole('heading', { name: '서비스' })).toBeInTheDocument();
  });

  it('🔴 «일시적» 이라고 말하지 않는다 — 방문자 배포에서 상시 참인 거짓말이다', () => {
    renderCatalog('unavailable');
    expect(screen.getByTestId(UNAVAILABLE_TESTID).textContent).not.toMatch(
      /일시적/,
    );
  });

  it('테넌트 미선택은 «말은 하되» 저하 마커를 쓰지 않는다', () => {
    renderCatalog('no-tenant');
    expect(screen.getByTestId(NO_TENANT_TESTID)).toBeInTheDocument();
    expect(screen.queryByTestId(UNAVAILABLE_TESTID)).not.toBeInTheDocument();
  });

  it('🔵 대조군 — 정상이면 두 안내가 **모두** 없다', () => {
    renderCatalog('ok');
    expect(screen.queryByTestId(UNAVAILABLE_TESTID)).not.toBeInTheDocument();
    expect(screen.queryByTestId(NO_TENANT_TESTID)).not.toBeInTheDocument();
  });

  it('🔵 대조군 — prop 을 아예 안 주면(옛 호출부) 정상으로 읽는다', () => {
    renderCatalog(undefined);
    expect(screen.queryByTestId(UNAVAILABLE_TESTID)).not.toBeInTheDocument();
    expect(screen.queryByTestId(NO_TENANT_TESTID)).not.toBeInTheDocument();
  });
});

describe('마커가 촬영 술어와 갈라지지 않는다 (TASK-MONO-711 ③ 비공허성)', () => {
  // __dirname = tests/unit → tests → console-web → apps → platform-console
  //           → projects → <repo root>   (6단계)
  const REPO_ROOT = path.resolve(
    __dirname,
    '..',
    '..',
    '..',
    '..',
    '..',
    '..',
  );
  const CAPTURE = path.resolve(REPO_ROOT, 'scripts', 'capture-portfolio.mjs');

  function degradedSuffixes(): string[] {
    const src = readFileSync(CAPTURE, 'utf8');
    const m = src.match(/const\s+DEGRADED_SUFFIXES\s*=\s*\[([^\]]*)\]/);
    // 🔴 여기서 못 찾으면 «접미사 0개» 로 조용히 통과해서는 안 된다 — 빈 목록은
    //    모든 단언을 공허하게 만든다(부재 판정에 대리지표 금지).
    if (!m) throw new Error(`DEGRADED_SUFFIXES 를 ${CAPTURE} 에서 못 찾았습니다`);
    const list = [...m[1].matchAll(/'([^']+)'/g)].map((x) => x[1]);
    if (list.length === 0) throw new Error('DEGRADED_SUFFIXES 가 비어 있습니다');
    return list;
  }

  it('실패 마커는 촬영 스크립트가 읽는 접미사 중 하나로 끝난다', () => {
    const sufs = degradedSuffixes();
    expect(sufs.some((s) => UNAVAILABLE_TESTID.endsWith(s))).toBe(true);
  });

  it('🔴 테넌트 미선택 마커는 그 접미사 중 어느 것으로도 끝나지 않는다', () => {
    // 반대 방향 — 정상 상태가 저하로 집계되면 촬영이 멀쩡한 화면을 후보에서 뺀다.
    const sufs = degradedSuffixes();
    expect(sufs.some((s) => NO_TENANT_TESTID.endsWith(s))).toBe(false);
  });

  it('두 마커가 실제 컴포넌트에 **그 철자로** 존재한다', () => {
    // 상수만 맞춰 두고 컴포넌트를 고치면 위 두 칸이 공허해진다.
    const src = readFileSync(
      path.resolve(
        __dirname,
        '..',
        '..',
        // __dirname 이 tests/unit 이므로 두 단계면 앱 루트다.
        'src',
        'features',
        'catalog',
        'components',
        'ServiceCatalog.tsx',
      ),
      'utf8',
    );
    expect(src).toContain(`data-testid="${UNAVAILABLE_TESTID}"`);
    expect(src).toContain(`data-testid="${NO_TENANT_TESTID}"`);
  });
});
