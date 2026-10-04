'use client';

import { useSearchParams } from 'next/navigation';
import { ForcedReLoginCacheReset } from '@/widgets/forced-relogin-cache-reset/ForcedReLoginCacheReset';
import { SIGNED_OUT_DEMO_STOPPED, SIGNED_OUT_PARAM } from '@/shared/lib/session-end';

/** 안내 문구 — `DemoBackendNotice` 의 «데모 시작 페이지에서 서버를 켠 뒤(약 10분)» 와 같은 말을 쓴다. */
export const DEMO_SIGNED_OUT_COPY =
  '데모 서버가 종료되어 로그아웃되었습니다. 지금은 샘플 데이터로 둘러보는 중입니다 — 실제 데이터는 데모 시작 페이지에서 서버를 켠 뒤(약 10분) 다시 로그인하면 볼 수 있습니다.';

/**
 * TASK-PC-FE-305 (contract § 2.6.2) — 데모 종료로 세션이 끝나 샘플 셸에 착지한
 * 방문자에게 **왜 로그아웃됐는지** 말한다. `/api/auth/demo-ended` 가 붙인
 * `?signed_out=demo_stopped` 가 있을 때만 렌더한다.
 *
 * 🔴 PC-FE-299 AC-5 의 의무를 함께 옮겨 온다: 예전 착지(`/login?error=session_expired`)
 *    는 `ForcedReLoginCacheReset` 으로 TanStack Query 캐시를 비웠다. 이 착지가 그 자리를
 *    대신하므로 같은 위젯을 여기서 마운트한다 — 방금 끝난 세션의 조회 결과가 샘플 화면
 *    뒤에서 메모리에 남지 않게.
 *
 * 🔵 샘플 셸(`(console)/layout.tsx` 의 `sampleVisitor` 분기)에만 마운트된다. 로그인한
 *    운영자의 셸에는 이 안내가 뜰 일이 없다.
 */
export function DemoSignedOutNotice() {
  const params = useSearchParams();
  if (params?.get(SIGNED_OUT_PARAM) !== SIGNED_OUT_DEMO_STOPPED) return null;

  return (
    <>
      <ForcedReLoginCacheReset />
      <div
        role="status"
        data-testid="demo-signed-out-notice"
        className="border-b border-amber-300 bg-amber-50 px-4 py-2 text-center text-sm text-amber-900"
      >
        {DEMO_SIGNED_OUT_COPY}
      </div>
    </>
  );
}
