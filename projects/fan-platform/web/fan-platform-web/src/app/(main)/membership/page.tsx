/** Static membership info page — v2 will wire payments via membership-service. */
export default function MembershipPage() {
  const tiers = [
    {
      name: 'PUBLIC',
      price: '무료',
      perks: ['아티스트 디렉토리 열람', '공개 포스트 조회', '댓글 / 반응 작성'],
    },
    {
      name: 'MEMBERS_ONLY',
      price: '월 7,900원 (가상)',
      perks: ['멤버 전용 포스트 열람', '아티스트 라이브 알림', '디지털 기념품'],
    },
    {
      name: 'PREMIUM',
      price: '월 17,900원 (가상)',
      perks: ['프리미엄 포스트 / 라이브', 'V-card 디지털 굿즈', '오프라인 이벤트 우선 신청'],
    },
  ];
  return (
    <section>
      <header className="mb-8">
        <h1 className="text-2xl font-bold text-ink-900">멤버십</h1>
        <p className="text-sm text-ink-600">
          v1 은 안내 페이지입니다. 결제 흐름은 v2의 membership-service 도입과 함께 활성화됩니다.
        </p>
      </header>
      <ul className="grid gap-4 md:grid-cols-3">
        {tiers.map((tier) => (
          <li
            key={tier.name}
            className="rounded-xl border border-ink-200 bg-white p-6 shadow-sm"
          >
            <p className="text-xs font-semibold uppercase tracking-wide text-brand-600">
              {tier.name}
            </p>
            <p className="mt-2 text-2xl font-bold text-ink-900">{tier.price}</p>
            <ul className="mt-4 flex flex-col gap-2 text-sm text-ink-700">
              {tier.perks.map((p) => (
                <li key={p}>· {p}</li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}
