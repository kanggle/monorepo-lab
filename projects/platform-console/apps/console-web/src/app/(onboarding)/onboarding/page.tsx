import { CreateOrganizationForm } from '@/features/onboarding';

export const dynamic = 'force-dynamic';

/**
 * Self-service "create organization" page (ADR-MONO-044 §3.4 step 3 /
 * TASK-PC-FE-182). Reached when a logged-in visitor owns no workspace yet
 * (the callback routes the operator-exchange `not_provisioned` case here
 * instead of to re-login). Creating an organization makes them its first
 * TENANT_ADMIN + TENANT_BILLING_ADMIN and drops them straight into the
 * console — no platform operator in the loop.
 */
export default function OnboardingPage() {
  return (
    <div className="rounded-lg border border-border bg-background p-8">
      <h1 className="text-xl font-semibold text-foreground">조직 만들기</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        로그인은 완료됐지만 아직 소속된 워크스페이스가 없습니다. 새 조직을
        만들면 <strong className="text-foreground">그 조직의 관리자</strong>가
        되어 바로 운영 콘솔을 사용할 수 있습니다. (플랫폼 관리자의 승인이 필요
        없습니다.)
      </p>
      <div className="mt-6">
        <CreateOrganizationForm />
      </div>
    </div>
  );
}
