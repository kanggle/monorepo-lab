/**
 * "조직을 만들면 일어나는 일" static explainer panel for
 * {@link CreateOrganizationForm} (TASK-PC-FE-182 — extracted TASK-PC-FE-212
 * presentational split). Purely static — no props, no state, no interactivity.
 * Markup / testid (`onboarding-what-happens`) is byte-verbatim from the former
 * god-file.
 *
 * TASK-MONO-773 S6 — `mode="additional"` (a company operator's second company, ADR-MONO-082 D6 b) adds one
 * line: the existing companies' permissions are untouched (ADR-044 D2 — the new rows point at the new tenant
 * only). The default (`"first"`) renders byte-identically to before.
 */
export function OnboardingWhatHappens({ mode = 'first' }: { mode?: 'first' | 'additional' } = {}) {
  return (
    <section
      data-testid="onboarding-what-happens"
      aria-label="조직을 만들면 일어나는 일"
      className="rounded-md border border-border bg-muted/40 px-4 py-3 text-sm text-muted-foreground"
    >
      <p className="font-medium text-foreground">조직을 만들면</p>
      <ul className="mt-2 list-disc space-y-1 pl-5">
        <li>
          새 조직(테넌트)이 생기고, 당신이 그 조직의{' '}
          <strong className="text-foreground">
            관리자(TENANT_ADMIN · 구독 관리자)
          </strong>
          가 됩니다.
        </li>
        <li>
          이 권한은{' '}
          <strong className="text-foreground">방금 만든 조직에만</strong>{' '}
          적용됩니다 — 다른 조직에는 접근할 수 없습니다.
        </li>
        {mode === 'additional' && (
          <li>
            새 회사가 하나 <strong className="text-foreground">더해질 뿐</strong>, 지금 소속된 회사의
            권한은 그대로입니다. 상단 스위처에서 회사를 오갈 수 있습니다.
          </li>
        )}
        <li>
          조직은{' '}
          <strong className="text-foreground">도메인 구독 0</strong>{' '}
          으로 시작합니다. 콘솔 입장 후{' '}
          <strong className="text-foreground">구독</strong> 화면에서 WMS ·
          이커머스 등 필요한 도메인을 직접 켜야 운영 화면이 열립니다.
        </li>
      </ul>
      <p className="mt-2">
        다음 단계: 조직 생성 → 도메인 구독 켜기 → 운영자 초대 · 도메인 운영.
      </p>
    </section>
  );
}
