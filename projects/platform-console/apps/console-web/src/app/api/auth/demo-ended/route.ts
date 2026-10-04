import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv, publicOrigin } from '@/shared/config/env';
import { resolveDemoBackendState } from '@/shared/config/demo-backend';
import { clearFullSession, isAuthenticated } from '@/shared/lib/session';
import {
  sessionEndDestination,
  sampleReturnPath,
  withDemoSignedOutNotice,
  buildDemoCheckedReLoginFor,
} from '@/shared/lib/session-end';
import {
  DEMO_STOP_MARKER_COOKIE,
  LIVE_PARAM,
  demoStopMarkerCookieOpts,
  liveRouteDemoStopOutcome,
  parseDemoStopMarker,
  parseLiveIntent,
  serializeDemoStopMarker,
  withDemoCheckedReturn,
  withoutDemoChecked,
} from '@/shared/lib/live-session-demo-stop';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * `GET /api/auth/demo-ended` — 데모 종료로 끝난 세션을 지우고 샘플 셸로 보낸다
 * (`TASK-PC-FE-305`, `console-integration-contract.md` § 2.6.2).
 *
 * `/login?error=session_expired` 페이지가 데모 상태 신호 `unavailable` 을 보면 여기로
 * 보낸다 — 서버 컴포넌트는 쿠키를 못 지우므로 라우트 핸들러가 필요하다(§ 2.6.1 의
 * 갱신 홉과 같은 이유).
 *
 * 🔴 **신호를 다시 읽는다.** 쿠키를 지우는 GET 이므로 호출자(링크 하나로 누구든 부를 수
 *    있다)를 믿지 않는다 — 데모가 실제로 꺼져 있을 때만 지운다. 그때의 세션은 어차피
 *    갱신될 수 없다(IdP 가 같이 꺼져 있다).
 *
 * 🔴 지우는 범위는 `clearFullSession` 그대로다(access/refresh/id_token/operator/tenant/
 *    assumed). `console_last_tenant` 는 로그아웃과 마찬가지로 남긴다 — 자격이 아니라 선호다.
 *
 * 🔵 지운 뒤 `(console)` 가드는 이 방문자를 `isSampleVisitor()` 로 본다 — 그 술어는 바뀌지
 *    않았다(ADR-MONO-074 A1). 죽은 쿠키를 샘플로 재해석한 것이 아니라 쿠키가 없어진 것이다.
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 live 모드 (`TASK-PC-FE-306`, § 2.6.2 «Live session»)
 * -----------------------------------------------------------------------------
 * `(console)` 레이아웃이 **작동하는 세션**(액세스·운영자 쿠키 있음)으로 데모 꺼짐을 읽으면
 * `?live=check`(또는 연속이 깨졌을 때 `?live=clear`)로 여기 온다. 그 세션은 **서로 다른 두 번의
 * 연속 `unavailable`** 뒤에만 끝난다(소유자 결정 ①) — 규칙은 `liveRouteDemoStopOutcome` 하나.
 * 끝내지 않는 모든 결과는 표식을 적고 **요청한 화면**으로 돌려보낸다(`/login` 이 아니다 —
 * 세션은 작동한다). 🔴 live 모드는 `live` 파라미터 **그리고** 살아 있는 쿠키일 때만 —
 * 그 밖(305 의 `/login` 착지에서 온 홉)은 아래 305 핸들러 그대로다.
 */
export async function GET(req: Request): Promise<NextResponse> {
  const requestId = newRequestId();
  const origin = publicOrigin(getServerEnv());
  const { searchParams } = new URL(req.url);
  const target = sampleReturnPath(searchParams.get('redirect'));

  const to = (path: string): NextResponse => {
    const res = NextResponse.redirect(new URL(path, origin).toString());
    res.headers.set('Cache-Control', 'no-store');
    return res;
  };

  const intent = parseLiveIntent(searchParams.get(LIVE_PARAM));
  if (intent !== null && (await isAuthenticated())) {
    const jar = await cookies();
    const marker = parseDemoStopMarker(jar.get(DEMO_STOP_MARKER_COOKIE)?.value);
    const page = withoutDemoChecked(target);
    const startedAt = Date.now();
    // `clear` 는 신호를 읽지 않는다 — 레이아웃이 이미 «꺼짐 아님» 을 읽었고, 그 결과는 첫
    // 판독을 지우는 것뿐이다(위조돼도 로그아웃을 미룰 뿐).
    const state = intent === 'check' ? await resolveDemoBackendState() : null;
    const settledAt = Date.now();
    const outcome = liveRouteDemoStopOutcome({ intent, state, marker, startedAt, settledAt });
    if (outcome.end) {
      clearFullSession(jar);
      jar.delete(DEMO_STOP_MARKER_COOKIE);
      logger.info('demo_ended_live_session_cleared', { requestId, state });
      return to(withDemoSignedOutNotice(page));
    }
    jar.set(
      DEMO_STOP_MARKER_COOKIE,
      serializeDemoStopMarker({ firstUnavailableAt: outcome.firstUnavailableAt, checkedAt: settledAt }),
      demoStopMarkerCookieOpts,
    );
    logger.info('demo_ended_live_session_kept', {
      requestId,
      intent,
      state,
      pending: outcome.firstUnavailableAt !== 0,
    });
    return to(withDemoCheckedReturn(page));
  }

  const state = await resolveDemoBackendState();
  if (sessionEndDestination(state) === 'sample') {
    const jar = await cookies();
    clearFullSession(jar);
    if (jar.get(DEMO_STOP_MARKER_COOKIE)) jar.delete(DEMO_STOP_MARKER_COOKIE);
    logger.info('demo_ended_session_cleared', { requestId, state });
    return to(withDemoSignedOutNotice(target));
  }

  // 신호가 동의하지 않는다(그사이 켜졌거나, 다른 인스턴스의 캐시가 달랐다) → 오늘의
  // 강제 재로그인 그대로. 쿠키는 건드리지 않는다. `demo_checked=1` 이 루프를 끊는다.
  logger.info('demo_ended_signal_disagrees', { requestId, state });
  return to(buildDemoCheckedReLoginFor(target));
}
