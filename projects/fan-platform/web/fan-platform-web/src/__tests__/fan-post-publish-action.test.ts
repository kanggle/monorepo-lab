import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-FAN-FE-016 — what `publishFanPost` actually puts on the wire.
 *
 * Separate from the component test because this one needs the REAL action module, and the
 * component test mocks it wholesale (the real module reaches `server-only` through the
 * session helper, which jsdom cannot load).
 */

const { gatewayFetch, revalidatePath } = vi.hoisted(() => ({
  gatewayFetch: vi.fn(),
  revalidatePath: vi.fn(),
}));

vi.mock('@/shared/api/client', () => ({ gatewayFetch }));
vi.mock('@/shared/auth/session', () => ({
  getFanSession: async () => ({ accessToken: 'token-abc' }),
}));
vi.mock('next/cache', () => ({ revalidatePath }));

import { publishFanPost } from '@/features/post/api/actions';

function sentBody() {
  const [, opts] = gatewayFetch.mock.calls[0] as unknown as [string, { body: unknown }];
  return opts.body;
}

beforeEach(() => {
  vi.clearAllMocks();
  gatewayFetch.mockResolvedValue({ data: { postId: 'p-9' } });
});

describe('publishFanPost', () => {
  it('🔴 AC-2: postType=FAN_POST · visibility=PUBLIC 을 고정해 보낸다', async () => {
    const result = await publishFanPost('제목', '본문');

    expect(result).toEqual({ ok: true, postId: 'p-9' });
    expect(gatewayFetch.mock.calls[0][0]).toBe('/api/v1/community/posts');
    // 필드 하나씩이 아니라 **바디 전체**를 고정한다. 부분 단언은 누군가 visibility 를
    // 파라미터로 바꿔도 통과한다 — 그게 이 티켓이 막으려는 변경이다.
    //
    // 🔴 ADR-003 (ACCEPTED — B) · TASK-FAN-BE-047 AC-5 — 이 `toEqual` 은 스타일이 아니다.
    // ADR-003 은 팬이 게이팅된 글을 쓸 수 있게 두기로 했고, 그 결정이 **되돌릴 수 있다**는
    // 근거가 "그런 행이 실제로는 쌓이지 않는다" 였다. 쌓이지 않는 이유가 바로 이 줄이다 —
    // 작성 화면이 PUBLIC 을 하드코딩하고, 이 단언이 바디 전체를 고정하므로 누구도 조용히
    // visibility 선택을 붙일 수 없다. 이걸 `toMatchObject` 로 완화하면 가드가 사라지는 게
    // 아니라 **ADR-003 의 전제가 사라진다**(행이 쌓이기 시작하고, 그 행들은 write-once 라
    // 나중에 방향을 틀 때 마이그레이션 대상이 된다).
    // ⇒ 완화하려면 테스트가 아니라 ADR-003 을 먼저 열어라.
    expect(sentBody()).toEqual({
      postType: 'FAN_POST',
      visibility: 'PUBLIC',
      title: '제목',
      body: '본문',
    });
  });

  it('제목이 공백뿐이면 빈 문자열이 아니라 undefined 로 보낸다', async () => {
    await publishFanPost('   ', '본문');
    expect((sentBody() as { title?: string }).title).toBeUndefined();
  });

  it('앞뒤 공백은 잘라서 보낸다', async () => {
    await publishFanPost('  제목  ', '  본문  ');
    expect(sentBody()).toMatchObject({ title: '제목', body: '본문' });
  });

  it('본문이 공백뿐이면 요청 자체를 보내지 않는다', async () => {
    const result = await publishFanPost('제목', '   ');

    expect(result.ok).toBe(false);
    expect(gatewayFetch).not.toHaveBeenCalled();
  });

  it('본문이 상한을 넘으면 요청을 보내지 않는다 (DTO 는 10000자)', async () => {
    const result = await publishFanPost('', 'x'.repeat(10_001));

    expect(result.ok).toBe(false);
    expect(gatewayFetch).not.toHaveBeenCalled();
  });

  it('🔴 성공하면 /me/posts 를 revalidate 한다 — 그 화면이 유일한 조회 경로다', async () => {
    await publishFanPost('제목', '본문');

    // 피드는 팔로우 기반이라 revalidate 해도 자기 글이 나타나지 않는다. 되돌아갈 수
    // 있는 화면은 /me/posts 하나뿐이므로 그것을 갱신하지 않으면 방금 쓴 글이 목록에서
    // 빠진 채로 보인다.
    expect(revalidatePath).toHaveBeenCalledWith('/me/posts');
  });

  it('게이트웨이가 실패하면 던지지 않고 메시지를 돌려준다', async () => {
    gatewayFetch.mockRejectedValue(new Error('boom'));

    const result = await publishFanPost('제목', '본문');

    expect(result.ok).toBe(false);
    expect(revalidatePath).not.toHaveBeenCalled();
  });
});
