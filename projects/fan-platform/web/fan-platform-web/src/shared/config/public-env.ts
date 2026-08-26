/**
 * 브라우저가 봐도 되는 env — **오직 `NEXT_PUBLIC_*` 만.**
 *
 * -----------------------------------------------------------------------------
 * 🔴 왜 `env.ts` 에서 떼어냈나 (TASK-MONO-586 / ADR-MONO-067 D1)
 * -----------------------------------------------------------------------------
 * `env.ts` 는 *"비공개 값은 서버에서만 접근하므로 클라이언트 번들 유출이 빌드 타임에
 * 거부된다"* 고 스스로 적어 뒀다. **그 문장은 틀렸다.** 빌드가 막는 것은 비공개 env 의
 * **값**이지 **모듈**이 아니다 — 클라이언트 컴포넌트가 그 모듈에서 무엇이든 하나만
 * 임포트하면 `env` 객체 리터럴이 통째로 청크에 실리고, 비공개 항목의 **폴백 리터럴까지
 * 함께** 간다.
 *
 * 2026-08-26 산출물로 확인했다(청크 `992-*.js`):
 *
 *     nextAuthUrl: null != (o = c.env.NEXTAUTH_URL) ? o : "http://localhost:3002"
 *
 * 값은 비어 있지만 **주소 문자열은 브라우저에 있다.** `TASK-MONO-565` 가 같은 것을
 * `iam.local` 에 대해 먼저 발견했고, 그때는 원인이 *"서버·클라 설정이 한 env 모듈"* 이라고
 * 적혔다. 이 파일이 그 한 모듈을 **둘로** 가른다.
 *
 * 🔴 **여기에 비공개 값을 추가하지 마라.** 이 모듈은 클라이언트 컴포넌트가 임포트하는
 * 유일한 설정 모듈이고, 그것이 이 파일의 존재 이유 전부다. 새 값이 `NEXT_PUBLIC_` 로
 * 시작하지 않으면 여기 올 자격이 없다.
 *
 * 🔵 프로브 대조군으로 검증되는 성질이다: `NEXT_PUBLIC_*` 를 주입해 빌드하면 그 값이
 * 청크에 나타나야 하고, 비공개 폴백 리터럴은 **나타나지 않아야** 한다.
 */
export const publicEnv = {
  /**
   * PortOne V2 public keys (browser — build-time inlined via `NEXT_PUBLIC_*`).
   * Semi-public: they initialise the payment window client-side. The API secret
   * that VERIFIES the payment is server-side only (membership-service), never here.
   * Empty when unset → the checkout helper reports "결제 모듈 미설정" instead of crashing.
   *
   * 🔵 로컬 `.env.local` 이 이 값을 주고 있어서 로컬 빌드에는 실값이 박힌다. Vercel 에는
   * 그 파일이 없으므로 **대시보드 env 에 넣지 않으면 빈 문자열**이다 — 크래시는 아니고
   * 위 문구가 뜬다.
   */
  portoneStoreId: process.env.NEXT_PUBLIC_PORTONE_STORE_ID ?? '',
  portoneChannelKey: process.env.NEXT_PUBLIC_PORTONE_CHANNEL_KEY ?? '',
} as const;

export type PublicEnv = typeof publicEnv;
