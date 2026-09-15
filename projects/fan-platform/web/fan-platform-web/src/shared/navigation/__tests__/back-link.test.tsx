/**
 * «← 뒤로» 의 판정 (TASK-FAN-FE-023 AC-3).
 *
 * 🔵 ⓐ(이력 있음 → back) 와 ⓑ(이력 없음 → `/`) 는 서로의 **대조군**이다 — 한쪽만 있으면
 *    «항상 back» 이나 «항상 `/`» 구현이 초록으로 통과한다.
 * 🔴 `next/link` 는 평범한 `<a>` 로 갈아 끼운다. 여기서 재는 것은 우리 `onClick` 의 판정이지
 *    Next 라우터가 아니다(라우터 동작은 라이브 AC-4 가 잰다).
 */
import type { AnchorHTMLAttributes, ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

const nav = vi.hoisted(() => ({ pathname: '/' }));

vi.mock('next/link', () => ({
  default: ({ href, children, ...rest }: { href: string; children: ReactNode } & AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
vi.mock('next/navigation', () => ({ usePathname: () => nav.pathname }));

import { BackLink } from '@/shared/ui/BackLink';
import { InAppHistoryTracker } from '@/shared/navigation/InAppHistoryTracker';
import { recordPathname, hasInAppHistory, __resetInAppHistory } from '@/shared/navigation/inAppHistory';

let backSpy: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  __resetInAppHistory();
  // jsdom 의 이력 길이는 1 에서 시작한다 — 칸마다 «탭에 이전 항목이 있다» 로 맞춘다.
  window.history.pushState({}, '', '/before');
  backSpy = vi.spyOn(window.history, 'back').mockImplementation(() => {});
});

afterEach(() => {
  backSpy.mockRestore();
});

/** 클릭하고, 모든 핸들러가 지난 뒤의 `defaultPrevented` 를 돌려준다(jsdom 의 실제 이동은 막는다). */
function click(el: HTMLElement, init: MouseEventInit = {}): boolean {
  let prevented = false;
  const onWindow = (e: Event) => {
    prevented = e.defaultPrevented;
    e.preventDefault();
  };
  window.addEventListener('click', onWindow);
  fireEvent.click(el, init);
  window.removeEventListener('click', onWindow);
  return prevented;
}

describe('inAppHistory — 앱 안 이력 판정', () => {
  it('직접 진입(경로 하나) → 이력 없음', () => {
    recordPathname('/posts/a');
    expect(hasInAppHistory()).toBe(false);
  });

  it('앱 안에서 한 번 이동 → 이력 있음', () => {
    recordPathname('/artists/x');
    recordPathname('/posts/a');
    expect(hasInAppHistory()).toBe(true);
  });

  it('🔴🔴 직접 진입 → 다른 화면 → 브라우저 뒤로로 복귀 → 이력 없음 (사이트 밖으로 나가면 안 된다)', () => {
    recordPathname('/posts/a');
    recordPathname('/artists/x');
    recordPathname('/posts/a');
    expect(hasInAppHistory()).toBe(false);
  });

  it('같은 경로가 다시 기록돼도(쿼리만 바뀜·재렌더) 이동으로 세지 않는다', () => {
    recordPathname('/');
    recordPathname('/');
    expect(hasInAppHistory()).toBe(false);
  });

  it('🔴 스택이 이력을 말해도 탭 이력 길이가 1 이면 이력 없음 — 과대 집계를 한 번 더 막는다', () => {
    recordPathname('/');
    recordPathname('/posts/a');
    const lengthSpy = vi.spyOn(window.history, 'length', 'get').mockReturnValue(1);
    expect(hasInAppHistory()).toBe(false);
    lengthSpy.mockRestore();
  });
});

describe('InAppHistoryTracker — 경로가 바뀌면 기록한다 (배선)', () => {
  it('렌더된 경로가 바뀌면 이력이 생긴다', () => {
    nav.pathname = '/artists/x';
    const { rerender } = render(<InAppHistoryTracker />);
    expect(hasInAppHistory()).toBe(false);
    nav.pathname = '/posts/a';
    rerender(<InAppHistoryTracker />);
    expect(hasInAppHistory()).toBe(true);
  });
});

describe('BackLink — 클릭 판정', () => {
  it('접근 가능한 이름은 «뒤로», 폴백 주소는 `/`', () => {
    render(<BackLink />);
    const link = screen.getByRole('link', { name: '뒤로' });
    expect(link.getAttribute('href')).toBe('/');
  });

  it('ⓐ 앱 안 이력 있음 → `history.back()` 한 번 · 기본 이동은 막는다', () => {
    recordPathname('/artists/x');
    recordPathname('/posts/a');
    render(<BackLink />);
    const prevented = click(screen.getByTestId('back-link'));
    expect(backSpy).toHaveBeenCalledTimes(1);
    expect(prevented).toBe(true);
  });

  it('ⓑ 🔴 이력 없음(공유 링크 직접 진입) → back 을 안 부르고 `/` 로 가게 둔다', () => {
    recordPathname('/posts/a');
    render(<BackLink />);
    const prevented = click(screen.getByTestId('back-link'));
    expect(backSpy).not.toHaveBeenCalled();
    expect(prevented).toBe(false);
  });

  it.each([
    ['⌘', { metaKey: true }],
    ['Ctrl', { ctrlKey: true }],
    ['Shift', { shiftKey: true }],
    ['가운데 버튼', { button: 1 }],
  ])('ⓒ %s 클릭은 이력이 있어도 가로채지 않는다', (_label, init) => {
    recordPathname('/artists/x');
    recordPathname('/posts/a');
    render(<BackLink />);
    const prevented = click(screen.getByTestId('back-link'), init);
    expect(backSpy).not.toHaveBeenCalled();
    expect(prevented).toBe(false);
  });
});
