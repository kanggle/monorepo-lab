import { NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Runtime store configuration for client components (TASK-BE-572 AC-4).
 *
 * <p>Why a route handler and not `NEXT_PUBLIC_*`: Next inlines `NEXT_PUBLIC_*` at BUILD time, so
 * a value baked into the image is fixed forever — the demo AMI is built once and then booted with
 * whatever profile the host chooses, which is exactly the case the inlined value cannot serve.
 * (`NEXT_PUBLIC_TOSS_CLIENT_KEY` right next to this is inlined that way, and that is precisely why
 * the demo could not turn the real PG off by env alone.) Reading `process.env` inside a
 * `force-dynamic` route evaluates it per request, in the running container.
 *
 * `demoPayment` must agree with payment-service's `demo-pg` profile: the mock PG approves anything
 * but the real Toss adapter would reject a made-up paymentKey. `infra/demo/demo.env` sets both from
 * one place and a demo wrapper guard asserts they are set together, so the two cannot drift apart.
 */
export function GET() {
  return NextResponse.json({
    demoPayment: process.env.DEMO_PAYMENT_MOCK === '1',
  });
}
