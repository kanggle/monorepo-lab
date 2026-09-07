/**
 * `widgets/demo-credentials/DemoLoginCredentials` — 데모 배포의 `/login` 이
 * **어느 계정으로 들어가는지 말한다** (`TASK-PC-FE-275`).
 *
 * 🔴 세 칸 중 진짜 방어선은 **`not-demo` 칸**이다. 로컬 개발과 CI 에서 이 블록이 뜨면
 *    데모 비밀번호가 모든 개발자 화면과 CI 스냅샷에 남는데, **그 사고는 아무 테스트도
 *    깨뜨리지 않는다**(있어야 할 것이 있는지만 보는 단언은 여분의 렌더를 못 잡는다).
 *    그래서 그 칸은 «문자열이 없다» 가 아니라 **컴포넌트가 `null` 을 반환한다**를 단언한다.
 *
 * 🔴 `unavailable` 칸도 장식이 아니다. 술어를 `=== 'running'` 으로 쓰고 싶은 유혹이
 *    있는데, 백엔드가 **꺼져 있을 때야말로** 방문자가 이 화면에 머문다(켜지기를 기다리는
 *    자리다). 이 칸이 그 술어 오작성을 막는 유일한 계측기다.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const { state } = vi.hoisted(() => ({ state: { value: 'not-demo' as string } }));

vi.mock('@/shared/config/demo-backend', () => ({
  resolveDemoBackendState: async () => state.value,
}));

import {
  DemoLoginCredentials,
  DEMO_LOGIN_EMAIL,
  DEMO_LOGIN_PASSWORD,
} from '@/widgets/demo-credentials/DemoLoginCredentials';

beforeEach(() => {
  state.value = 'not-demo';
});

/** 서버 컴포넌트다 — 호출해서 나온 엘리먼트를 렌더한다. */
async function renderBlock() {
  const el = await DemoLoginCredentials();
  if (el === null) return null;
  render(el);
  return el;
}

describe('DemoLoginCredentials', () => {
  it('🔴 `not-demo`(로컬·CI) → 아무것도 렌더하지 않는다', async () => {
    state.value = 'not-demo';
    expect(await renderBlock()).toBeNull();
    expect(screen.queryByTestId('demo-login-credentials')).toBeNull();
  });

  it('`running` → 계정 블록을 렌더한다', async () => {
    state.value = 'running';
    await renderBlock();
    const el = screen.getByTestId('demo-login-credentials');
    expect(el).toBeTruthy();
    const text = el.textContent ?? '';
    expect(text).toContain(DEMO_LOGIN_EMAIL);
    expect(text).toContain(DEMO_LOGIN_PASSWORD);
  });

  it('🔴 `unavailable` → 그래도 렌더한다 (술어가 «이 배포가 데모인가» 이므로)', async () => {
    state.value = 'unavailable';
    await renderBlock();
    // 백엔드가 꺼져 있을 때야말로 방문자가 이 화면에 오래 머문다. `DemoBackendNotice` 가
    // *왜 지금 안 되는가* 를, 이 블록이 *켜지면 무엇으로 들어가는가* 를 말한다.
    expect(screen.getByTestId('demo-login-credentials')).toBeTruthy();
  });

  it('🔴 다음 한 걸음(`demo-corp` 테넌트 선택)을 함께 말한다', async () => {
    state.value = 'running';
    await renderBlock();
    const text = screen.getByTestId('demo-login-credentials').textContent ?? '';
    // 계정만 알고 테넌트를 안 고르면 방문자는 빈 화면과 403 을 보고 "고장났다" 로 읽는다
    // — 운영자 권한은 계정이 아니라 **테넌트 assume** 에서 파생되기 때문이다.
    expect(text).toContain('demo-corp');
  });
});
