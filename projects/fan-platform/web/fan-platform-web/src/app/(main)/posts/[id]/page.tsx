import { notFound } from 'next/navigation';
import { isAuthenticated } from '@/shared/auth/session';
import { memberPostDetail } from '@/features/post';
import {
  readFanPublicData,
  findPost,
  PublicPostDetail,
  ProvenanceBanner,
} from '@/features/public-browse';

/**
 * 포스트 상세 — 출처가 **둘**이고, 순서가 정해져 있다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 *   ① 로그인 상태면 게이트웨이 판을 **먼저** 시도한다.
 *      회원은 예전과 똑같은 것을 봐야 한다 — 멤버 전용 본문, 반응 바, 그리고 자격이
 *      없을 때의 `MEMBERSHIP_REQUIRED` 안내까지. 그 판정은 community-service 가 하고
 *      이 화면은 그리기만 한다(인가는 하나도 안 바뀐다).
 *   ② 익명이거나 ①이 판정을 못 했으면 **공개 저장본**을 그린다.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * 🔴🔴 순서를 뒤집으면 안 된다. 저장본을 먼저 그리면 회원이 자기가 볼 권리가 있는 멤버
 *    전용 본문을 «잠김» 으로 보게 된다 — 회원 기능의 후퇴다. 반대로 익명 경로에서는 ①이
 *    **아예 실행되지 않으므로**(세션이 없다) 이 순서가 익명 방문에 요청을 만들지 않는다.
 *
 * 🔴 «백엔드 먼저, 실패하면 저장본» 이 아니다. 그 구조는 ADR-MONO-070 이 금지했고, 여기서
 *    갈리는 축은 **실패가 아니라 신원**이다: 익명에게 ①은 존재하지 않는 경로이지 실패한
 *    경로가 아니다. 로그인한 방문자에게만 두 칸이 순서를 갖는다.
 *
 * 🔴🔴 잠긴 글의 본문은 ②에서 **구조적으로 없다** — 봉투가 `body: null` 을 보장하고
 *    (`validateDatasetData` 가 아니면 봉투를 거부한다) `PublicPostDetail` 의 잠김 분기에는
 *    본문을 참조하는 식이 없다. 「조심해서 안 그린다」가 아니라 그릴 것이 없다.
 */
export default async function PostDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  if (await isAuthenticated()) {
    const member = await memberPostDetail(id);
    if (member) return member;
  }

  const result = await readFanPublicData();
  const post = findPost(result.data, id);
  if (!post) notFound();

  return (
    <section>
      <PublicPostDetail post={post} />
      <ProvenanceBanner
        source={result.envelope.source}
        generatedAt={result.envelope.generatedAt}
        degraded={result.degraded}
      />
    </section>
  );
}
