import Link from 'next/link';

/**
 * 둘러보기 상단의 **상시 배너** — "이건 샘플이고, 진짜 콘솔은 로그인이 필요하다".
 *
 * 🔴 페이지가 아니라 `(demo)/layout.tsx` 가 렌더한다. 화면마다 붙이면 **한 화면만
 *    빠뜨리는 것**이 가능해지고, 빠뜨린 화면은 그냥 "운영 콘솔" 로 읽힌다. 배너를
 *    레이아웃에 두면 그 화면이 존재하려면 배너를 통과해야 한다.
 *
 * 🔴 문구가 «데이터가 없습니다» 나 «백엔드가 꺼져 있습니다» 라고 말하지 않는다 —
 *    그 축은 이 배너가 안 잰다(그것은 `DemoBackendNotice` 의 몫이고 인증된 셸에만 있다).
 *    여기서 참인 문장은 하나뿐이다: **이 화면의 값은 합성이다.**
 *
 * 🔵 로그인 진입점이 배너 안에 있다. 요구사항의 *"실시간 콘솔로 전환하는 분명한 진입점"* —
 *    상시 배너에 두면 어느 화면에서든 한 번의 클릭이다.
 */
export function SampleDataBanner() {
  return (
    <div
      role="status"
      data-testid="demo-sample-banner"
      className="border-b border-amber-300 bg-amber-100 px-4 py-2 text-sm text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/60 dark:text-amber-100"
    >
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-3 gap-y-1 text-center">
        <span>
          <strong className="font-semibold">샘플(합성) 데이터</strong>로 만든
          둘러보기입니다. 실제 운영 데이터는 한 건도 포함되어 있지 않으며, 실시간
          콘솔은 로그인이 필요합니다.
        </span>
        <Link
          href="/login"
          data-testid="demo-login-link"
          className="rounded font-semibold underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          운영자 로그인으로 이동
        </Link>
      </div>
    </div>
  );
}
