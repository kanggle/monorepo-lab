import { NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Runtime payment configuration for client components (TASK-FAN-FE-015).
 *
 * Why a route handler and not `NEXT_PUBLIC_*`: Next inlines `NEXT_PUBLIC_*` at BUILD time, so a
 * value baked into the image is fixed forever — and this image is built once and then booted with
 * whatever profile the host chooses, which is exactly the case an inlined value cannot serve.
 * `NEXT_PUBLIC_PORTONE_STORE_ID` / `_CHANNEL_KEY` right beside this are inlined that way, which is
 * why the demo could not turn the PortOne window off by env alone. Reading `process.env` inside a
 * `force-dynamic` route evaluates it per request, in the running container.
 *
 * `demoPayment` must agree with membership-service's gateway selection, and note that fan's
 * polarity is the REVERSE of ecommerce's: there the real Toss adapter is the default and `demo-pg`
 * opts into the mock, whereas here `MockPaymentGatewayAdapter` is `@Profile("!portone")` — the mock
 * is what you get unless `portone` is switched ON. So the invariant this flag must satisfy is
 * "demoPayment on ⟺ the `portone` profile off", and the demo wrapper's guard (x2) asserts exactly
 * that against the rendered compose. Copying ecommerce's predicate verbatim would have asserted the
 * opposite and passed on a broken configuration.
 *
 * Note this route sits behind the session middleware like every other non-`/api/auth` path. That is
 * deliberate rather than an oversight: the only caller is a checkout that already requires a signed-in
 * fan, and the flag carries nothing secret, so gating it costs nothing and keeps one fewer public
 * surface. An unauthenticated request gets the login redirect, whose body is not the expected JSON —
 * `isDemoPayment` treats that as "not a demo", which is the safe direction.
 */
export function GET() {
  return NextResponse.json({
    demoPayment: process.env.DEMO_PAYMENT_MOCK === '1',
  });
}
