import { getFanSession } from '@/shared/auth/session';
import { getFeed } from '@/features/feed/api/getFeed';
import { FeedList } from './FeedList';

/**
 * `/` 의 **회원 전용 절반** — 내가 팔로우한 아티스트의 피드.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 «개인화 피드» 가 공개 피드 위에 얹히는 모양인가
 * ─────────────────────────────────────────────────────────────────────────
 * `/` 는 공개 경로가 됐지만, 그렇다고 개인화 피드를 없애는 것은 회원 기능의 **후퇴**다.
 * 두 피드는 성질이 다르다:
 *
 *   · 공개 피드 — 저장본. 로그인·백엔드 없이 뜬다. 누구에게나 같다.
 *   · 개인화 피드 — 게이트웨이. **누구를 팔로우했는가**에 따라 다르고, 그래서 인가가 필요하다.
 *
 * 🔴 익명 방문자에게는 이 컴포넌트가 **만들어지지 않는다**(페이지가 세션을 먼저 본다).
 *    조건을 이 안에 두지 않은 이유: 그러면 익명 요청도 이 모듈에 들어와 `getFeed` 앞까지
 *    간 뒤 분기하게 되고, 그 분기는 언젠가 사라진다. 호출 자체가 없는 편이 강하다.
 *
 * 🔴 실패하면 **아무것도 안 그린다.** 공개 피드가 이미 화면을 채우고 있으므로 여기서
 *    "피드를 불러올 수 없습니다" 를 띄우면, 백엔드가 꺼진 데모에서 회원만 고장 난 화면을
 *    본다. 데모 백엔드가 꺼진 사실 자체는 `DemoBackendNotice` 가 이미 말한다.
 */
export async function FollowingFeedSection({ size }: { size: number }) {
  const session = await getFanSession();
  if (!session.accessToken) return null;

  try {
    const feed = await getFeed(session.accessToken, 0, size);
    if (feed.content.length === 0) return null;
    return (
      <section data-testid="following-feed" className="mb-10">
        <header className="mb-4">
          <h2 className="text-lg font-semibold text-ink-900">팔로우 피드</h2>
          <p className="text-sm text-ink-600">팔로우한 아티스트의 최신 포스트입니다.</p>
        </header>
        <FeedList items={feed.content} />
      </section>
    );
  } catch {
    return null;
  }
}
