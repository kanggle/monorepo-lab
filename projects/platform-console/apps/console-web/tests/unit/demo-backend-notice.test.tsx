/**
 * `widgets/demo-notice/DemoBackendNotice` — 데모가 꺼져 있을 때 **그렇다고 말한다**
 * (TASK-MONO-585 AC-3 / ADR-MONO-067 § Consequences).
 *
 * 🔴 세 칸 중 진짜 방어선은 **`not-demo` 칸**이다. 로컬 개발과 CI 에서 "데모 서버가 꺼져
 *    있습니다" 를 띄우면 그것은 **거짓말**이고, 그 거짓말은 아무 테스트도 안 깨뜨린 채
 *    모든 개발자 화면 맨 위에 붙는다.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const { state } = vi.hoisted(() => ({ state: { value: 'not-demo' as string } }));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveDemoBackendState: async () => state.value,
}));

import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';

beforeEach(() => {
  state.value = 'not-demo';
});

/** 서버 컴포넌트다 — 호출해서 나온 엘리먼트를 렌더한다. */
async function renderNotice() {
  const el = await DemoBackendNotice();
  if (el === null) return null;
  render(el);
  return el;
}

describe('DemoBackendNotice', () => {
  it('🔴 `not-demo`(로컬·CI) → 아무것도 렌더하지 않는다', async () => {
    state.value = 'not-demo';
    expect(await renderNotice()).toBeNull();
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
  });

  it('`running` → 아무것도 렌더하지 않는다', async () => {
    state.value = 'running';
    expect(await renderNotice()).toBeNull();
  });

  it('`unavailable` → 배너를 렌더한다', async () => {
    state.value = 'unavailable';
    await renderNotice();
    const el = screen.getByTestId('demo-backend-notice');
    expect(el).toBeTruthy();
    expect(el.getAttribute('role')).toBe('status');
  });

  it('🔴 문구가 «여섯 도메인» 을 주장하지 않는다 — 그 축은 이 위젯이 안 잰다', async () => {
    state.value = 'unavailable';
    await renderNotice();
    const text = screen.getByTestId('demo-backend-notice').textContent ?? '';
    // 재는 것은 **인스턴스**의 상태 한 칸이다. 도메인별 상태는 `/dashboards/health` 가
    // 말한다. 두 축을 한 문장으로 합치면 둘 중 하나는 반드시 거짓이 된다(AC-3).
    expect(text).not.toMatch(/iam|wms|scm|finance|erp|ecommerce/i);
    expect(text).toContain('데모 서버');
  });
});
