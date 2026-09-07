import type { ReactNode } from 'react';
import { Header } from '@/widgets/header/Header';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';

/**
 * Main shell — 공개 페이지와 회원 페이지가 **둘 다** 여기 붙는다.
 *
 * 🔴 예전 주석은 *"gated by middleware so all child pages can assume the visitor is
 * authenticated"* 라고 적혀 있었다. 그 문장은 두 번 틀렸다: 처음에는 미들웨어가 배포
 * 빌드에서 아예 안 돌아서 틀렸고(`TASK-FAN-FE-018`), 지금은 **설계상** 틀리다 — `/`,
 * `/artists`, `/posts/:id`, `/membership` 은 이제 익명으로 열린다
 * (`shared/auth/public-paths.ts` 가 그 목록의 유일한 정본이다).
 *
 * ⇒ 이 셸의 자식은 «인증됐다» 를 **가정할 수 없다.** 세션이 필요한 조각은 각자
 *   `isAuthenticated()` 로 묻고, 익명일 때는 게이트웨이를 부르지 않는다. 미들웨어는
 *   여전히 fail-closed 지만, 그것이 지키는 것은 이제 **허용 목록 밖의 경로**다.
 *
 * `DemoBackendNotice` renders nothing outside the demo deployment, so local dev and CI
 * are unaffected (`TASK-MONO-586` AC-3).
 *
 * 🔵 데모 heartbeat 은 여기 없다 — `Header` 의 인증 분기 안에 있다. 이 자리로 올리면
 *    익명 방문이 데모 EC2 를 살려 두게 된다(`app/api/demo/heartbeat/route.ts` 헤더).
 */
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <DemoBackendNotice />
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>
    </>
  );
}
