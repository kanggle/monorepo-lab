import Link from 'next/link';
import type { FanSectionFlags } from '../api/fan-state';

/**
 * The page-level waterfall note for the `(console)/fan/**` routes (TASK-MONO-751) — one
 * place for the copy every fan page renders when it cannot show its data:
 * registryDegraded → notEligible → forbidden → degraded. Returns `null` when the page can
 * render its screen (`notFound` is the page's own `notFound()`).
 */
export function FanSectionNote({
  title,
  flags,
  registryDegraded,
  backHref,
}: {
  title: string;
  flags: Pick<FanSectionFlags, 'notEligible' | 'forbidden' | 'degraded'> | null;
  registryDegraded: boolean;
  backHref?: string;
}) {
  let testId: string | null = null;
  let body: React.ReactNode = null;
  if (registryDegraded) {
    testId = 'fan-degraded';
    body = '팬 디렉터리 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속 사용할 수 있습니다.';
  } else if (flags?.notEligible) {
    testId = 'fan-not-eligible';
    body = (
      <>
        <p className="mb-2 font-medium text-foreground">팬 디렉터리 화면에 대한 접근 권한이 없습니다.</p>
        <p>
          이 화면은 <b>플랫폼 운영자</b>가 <code>fan-platform</code> 테넌트로 전환했을 때만 열립니다
          (ADR-MONO-079 R3 — 고객사 운영자는 팬 테넌트를 다루지 않습니다).
        </p>
      </>
    );
  } else if (flags?.forbidden) {
    testId = 'fan-forbidden';
    body = '이 화면을 조회할 권한이 없습니다. 상단 스위처에서 fan-platform 테넌트를 선택했는지 확인하세요.';
  } else if (flags?.degraded) {
    testId = 'fan-degraded';
    body = '팬 디렉터리 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속 사용할 수 있습니다.';
  }
  if (testId === null) return null;

  return (
    <section>
      <h1 className="mb-6 text-2xl font-semibold">{title}</h1>
      <div
        role="status"
        data-testid={testId}
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        {body}
      </div>
      <Link href={backHref ?? '/dashboards/overview'} className="mt-4 inline-block text-sm underline">
        {backHref ? '목록으로' : '개요로 이동'}
      </Link>
    </section>
  );
}
