/**
 * `POST /api/demo/heartbeat` — 데모 EC2 를 깨어 있게 유지하는 중계.
 *
 * 🔴🔴 이 스위트의 **가장 중요한 칸은 익명 칸**이다. 공개 카탈로그는 저장본만으로 도므로
 *    공개 열람자는 백엔드를 안 쓴다 — 그런데 하트비트가 나가면 아무도 안 쓰는 인스턴스가
 *    켜진 채로 월 예산을 태우고, 정작 백엔드가 필요한 사람의 `/start` 가 429 로 거절된다.
 *    그래서 판정은 «204 를 돌려줬다» 가 아니라 **«fetch 가 한 번도 안 불렸다»** 이다.
 *    (전자만 보면 세션 검사를 통째로 지워도 초록이다 — 어차피 204 니까.)
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const mockSession = vi.hoisted(() => vi.fn());

vi.mock('@/shared/auth/session', () => ({
  getWebStoreSession: mockSession,
}));

import { POST } from '@/app/api/demo/heartbeat/route';

const ANONYMOUS = { accessToken: null, accountId: null, tenantId: null, roles: [] };
const SIGNED_IN = {
  accessToken: 'tok',
  accountId: '11111111-1111-1111-1111-111111111111',
  tenantId: null,
  roles: ['CONSUMER'],
};

const ORIGINAL_ENV = { ...process.env };

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  fetchMock = vi.fn().mockResolvedValue({ ok: true } as Response);
  vi.stubGlobal('fetch', fetchMock);
  process.env.DEMO_API_BASE = 'https://control.example';
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('POST /api/demo/heartbeat', () => {
  it('익명이면 아무 데도 보내지 않는다 — 공개 열람이 EC2 를 켜 두면 안 된다', async () => {
    mockSession.mockResolvedValue(ANONYMOUS);

    const res = await POST();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(res.status).toBe(204);
  });

  it('세션 토큰은 있는데 accountId 가 없으면(비-CONSUMER 등) 보내지 않는다', async () => {
    mockSession.mockResolvedValue({ ...ANONYMOUS, accessToken: 'tok' });

    await POST();

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('DEMO_API_BASE 가 없으면(로컬·CI·비데모) 로그인 상태여도 보내지 않는다', async () => {
    delete process.env.DEMO_API_BASE;
    mockSession.mockResolvedValue(SIGNED_IN);

    const res = await POST();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(res.status).toBe(204);
  });

  it('로그인 + DEMO_API_BASE 가 있을 때만 <base>/heartbeat 로 POST 한다', async () => {
    mockSession.mockResolvedValue(SIGNED_IN);

    const res = await POST();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://control.example/heartbeat');
    expect(init.method).toBe('POST');
    expect(res.status).toBe(204);
  });

  it('베이스의 끝 슬래시를 정규화한다 (//heartbeat 로 나가면 404 다)', async () => {
    process.env.DEMO_API_BASE = 'https://control.example/';
    mockSession.mockResolvedValue(SIGNED_IN);

    await POST();

    expect(fetchMock.mock.calls[0][0]).toBe('https://control.example/heartbeat');
  });

  it('컨트롤 플레인이 죽어 있어도 204 다 — 비콘 실패를 화면 오류로 번역하지 않는다', async () => {
    mockSession.mockResolvedValue(SIGNED_IN);
    fetchMock.mockRejectedValueOnce(new Error('ECONNREFUSED'));

    const res = await POST();

    expect(res.status).toBe(204);
  });
});
