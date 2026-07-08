/**
 * 운영자 그룹 스텁 라우트 (TASK-PC-FE-225 — IAM nav 정석 재편성).
 *
 * 정석 IAM taxonomy의 워크포스 평면 중 「운영자 그룹」(IAM User Group / Google
 * Group 대응) 메뉴 항목의 목적지. 이 태스크는 nav 배치 + 스텁 라우트만
 * 담당한다 — 실제 목록/상세/CRUD 기능은 ADR-MONO-046 실행 로드맵에서 구현된다.
 *
 * 정적 서버 컴포넌트: 데이터 로딩·권한 게이트 없음(스텁이므로 `(console)`
 * 레이아웃의 인증 가드만 적용된다).
 */
export default function OperatorGroupsPage() {
  return (
    <section aria-labelledby="operator-groups-heading">
      <h1 id="operator-groups-heading" className="mb-6 text-2xl font-semibold">
        운영자 그룹
      </h1>
      <div
        role="status"
        data-testid="operator-groups-stub"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        운영자 그룹 관리 화면은 준비 중입니다. 후속 태스크(ADR-MONO-046 실행
        로드맵)에서 구현됩니다.
      </div>
    </section>
  );
}
