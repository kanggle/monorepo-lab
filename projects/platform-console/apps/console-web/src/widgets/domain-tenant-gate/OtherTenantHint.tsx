/**
 * «이 테넌트에는 없습니다» — 빈 목록 **옆에** 붙는 힌트 (TASK-MONO-719, 소유자 결정 ⓑ).
 *
 * 🔴🔴 **왜 이것이 `DomainTenantGate` 안에 없는가.** `TASK-MONO-718` ⓑ 는 게이트 안에서
 * «활성 테넌트가 이 제품의 `tenants` 에 있는가» 를 물었고, 2026-09-22 데모 창이 그것을
 * FAIL 로 판정했다 — 라이브 레지스트리가 `ecommerce -> [demo-corp, ecommerce]` 라
 * 그 술어는 **언제나 통과**한다. 레지스트리가 틀린 것이 아니다: `demo-corp` 는 ecommerce
 * 제품에 **자격이 있고**(`TASK-BE-576`), 행이 `tenant_id=ecommerce` 에 사는 것은 **별개
 * 명제**다. 게이트는 «자격» 을 묻고 그 답을 «데이터 소유» 의 답으로 썼다.
 *
 * ⇒ 719 AC-0 소유자 결정 **ⓑ**: 콘솔이 이미 **확실히 아는 것**으로만 말한다 —
 *   ① 이 목록이 지금 **0건**이고
 *   ② 운영자가 **전환할 수 있는 다른 테넌트가 있다**
 * 그 둘이 참일 때 «여기엔 없습니다, 저기 있을 수 있습니다» 를 **힌트로** 띄운다.
 *
 * 🔵 **단언이 아니다.** 진짜로 어느 테넌트에도 0건일 수 있고, 그때도 이 힌트는 뜬다.
 *    그래서 문구가 «데이터가 없습니다» 가 아니라 «이 테넌트에는 없습니다» 여야 한다 —
 *    틀릴 때 **운영자가 한 번 더 확인하는** 쪽으로 틀린다(해롭지 않은 방향).
 * 🔴 **화면을 막지 않는다.** 빈 목록은 그대로 그려지고 이것은 그 아래 한 줄이다.
 *    718 이 세운 «못 확인함은 통과» 규칙을 이 티켓은 바꾸지 않는다.
 * 🔴 **전환할 다른 테넌트가 없으면 아무것도 안 그린다** — 「다른 테넌트로 가 보라」가
 *    말이 안 되는 배포에서 그 문장은 소음이고, 그 소음은 진짜 신호를 덮는다.
 */
export function OtherTenantHint({
  activeTenant,
  otherTenants,
}: {
  /** 지금 assume 한 테넌트. 없으면(`null`) 그리지 않는다 — 그 경우는 292 게이트 소관이다. */
  activeTenant: string | null;
  /**
   * 전환할 수 있는 **나머지** 테넌트(활성 제외). 빈 배열이면 그리지 않는다.
   * 🔵 호출자가 빼서 넘긴다 — 이 컴포넌트가 «무엇이 선택 가능한가» 를 스스로 정하면
   *    `selectableTenants()` 의 정의가 두 곳이 된다(이 저장소가 여러 번 데인 축).
   */
  otherTenants: readonly string[];
}) {
  if (!activeTenant || otherTenants.length === 0) return null;
  return (
    <p
      role="status"
      data-testid="other-tenant-hint"
      className="mt-2 text-sm text-muted-foreground"
    >
      현재 테넌트 <code>{activeTenant}</code> 에는 없습니다. 이 화면의 데이터는{' '}
      {otherTenants.map((t, i) => (
        <span key={t}>
          {i > 0 ? ', ' : ''}
          <code>{t}</code>
        </span>
      ))}{' '}
      테넌트에 있을 수 있습니다 — 상단의 테넌트 스위처에서 전환해 보세요.
    </p>
  );
}
