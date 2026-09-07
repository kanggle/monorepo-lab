/**
 * 🔴🔴 둘러보기의 **모든** 버튼은 비활성이고, **왜** 안 눌리는지를 그 자리에서 말한다.
 *
 * 술어가 「어떤 버튼 하나가 비활성이다」가 아니라 **「모든 버튼이 비활성이다」**인 것이
 * 이 파일의 요점이다. 존재 한정사(`find` / `[0]`)로 쓰면, 새 화면이 활성 버튼을 하나
 * 추가해도 앞선 비활성 버튼이 그것을 가려 준다 — 이 저장소가 이미 이름 붙여 둔 실패다.
 *
 * 🔵 그리고 **비어 있지 않음**을 함께 잰다. 버튼이 0개면 「모든 버튼이 비활성」은 공허하게
 *    참이고, 그 상태는 «쓰기 작업을 아예 안 보여 주는 화면» — 요구사항이 반대로 요구한
 *    것이다. 하한은 전 도메인 합계에 **1**로 둔다(도메인별 하한을 박으면 픽스처가 표를
 *    하나 옮길 때마다 가드가 거짓 빨강을 낸다).
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';

vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    ...rest
  }: { children: ReactNode; href: string } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

import DemoHomePage from '@/app/(demo)/demo/page';
import DemoDomainPage from '@/app/(demo)/demo/[domain]/page';
import {
  __resetConsoleSampleCache,
  DEMO_DISABLED_REASON,
} from '@/features/demo-tour';

const DOMAINS = ['ecommerce', 'wms', 'scm', 'erp', 'finance', 'iam'] as const;

beforeEach(() => {
  __resetConsoleSampleCache();
});

async function renderDomain(key: string) {
  const el = await DemoDomainPage({ params: Promise.resolve({ domain: key }) });
  return render(el);
}

describe('둘러보기의 쓰기 작업 컨트롤', () => {
  it('🔴 전 도메인 화면의 **모든** <button> 이 disabled 이고 사유 툴팁을 갖는다', async () => {
    let total = 0;

    for (const key of DOMAINS) {
      const { container, unmount } = await renderDomain(key);
      const buttons = Array.from(container.querySelectorAll('button'));
      for (const b of buttons) {
        expect(
          b.hasAttribute('disabled'),
          `[${key}] "${b.textContent}" 버튼이 활성 상태입니다`,
        ).toBe(true);
        expect(b.getAttribute('aria-disabled')).toBe('true');
        expect(b.getAttribute('title')).toBe(DEMO_DISABLED_REASON);
        // 스크린리더 사용자에게도 사유가 닿아야 한다(툴팁은 마우스에만 있다).
        expect(b.getAttribute('aria-label')).toContain(DEMO_DISABLED_REASON);
      }
      total += buttons.length;
      unmount();
    }

    // 비공허성 — 쓰기 작업을 하나도 안 보여 주면 이 테스트는 아무것도 증명하지 않는다.
    expect(total).toBeGreaterThan(0);
  });

  it('🔴 클릭해도 아무 일이 없다(핸들러 자체가 없다 — 네트워크 호출 0)', async () => {
    const fetchSpy = vi.fn(() => {
      throw new Error('둘러보기 버튼이 네트워크를 호출했습니다.');
    });
    vi.stubGlobal('fetch', fetchSpy);

    const { container } = await renderDomain('erp');
    const approve = screen.getByTestId('demo-action-approval-승인');
    expect(approve).toBeDisabled();
    // 🔵 disabled 버튼은 클릭 이벤트를 안 받는다 — `user-event` 는 그 사실을 그대로 재현한다.
    await userEvent.click(approve, { pointerEventsCheck: 0 });
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(container.querySelector('form[action]')).toBeNull();

    vi.unstubAllGlobals();
  });

  it('사유 문구가 요구된 정확한 문자열이다', () => {
    expect(DEMO_DISABLED_REASON).toBe('실시간 기능 시작 후 로그인');
  });

  it('개요 화면(읽기 전용 집계)에는 쓰기 버튼을 지어내지 않는다', async () => {
    const { container } = render(await DemoHomePage());
    const buttons = Array.from(container.querySelectorAll('button'));
    // 있어도 되지만, 있다면 전부 비활성이어야 한다(위와 같은 전칭 술어).
    for (const b of buttons) expect(b.hasAttribute('disabled')).toBe(true);
    expect(screen.queryByTestId('demo-actions-domain-health')).toBeNull();
  });
});
