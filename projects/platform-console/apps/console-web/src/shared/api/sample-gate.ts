import { isSampleVisitor } from '@/shared/lib/session';
import { sampleResponse, type SampleRequest } from '@/shared/sample/router';

/**
 * The sample branch every backend call site asks FIRST (ADR-MONO-074 A2).
 *
 * Returns the sample `Response` for a sample visitor, or `null` for everyone
 * else — in which case the caller runs its existing token → `fetch` path,
 * byte-for-byte as before. The call sites:
 *
 *   - the six gateway cores (`callAdminGateway`, `fetchRegistry`,
 *     `callWmsGateway`, `callEcommerceGateway`, `callFlatEnvelopeGateway`
 *     — which `callScmGateway` shims onto);
 *   - the out-of-core proxies that reach console-bff directly
 *     (`api/console/dashboards/{operator-overview,domain-health}`,
 *     `api/console/notifications/{inbox,[sourceDomain]/[id]/read}`) and the
 *     tenant switch (`api/tenant`).
 *
 * `tests/unit/sample-fetch-allowlist.test.ts` pins that list: in each of those
 * files `sampleGate(` appears before the first token read and the first
 * `fetch(`.
 *
 * -----------------------------------------------------------------------------
 * 🔴 If the predicate itself throws, the answer is «not a sample visitor»
 * -----------------------------------------------------------------------------
 * That sends the caller down the existing path, which reads the SAME cookie
 * store next and surfaces the same failure — so production behaviour is
 * unchanged. The opposite choice (throw ⇒ sample) would be the dangerous
 * direction: an authenticated operator could be shown synthetic data as if it
 * were real (task Failure Scenario). The one place this branch is actually
 * reached is a unit test that mocks `@/shared/lib/session` with only the token
 * getters it needs; those tests keep modelling the authenticated path.
 */
export async function sampleGate(req: SampleRequest): Promise<Response | null> {
  let sample: boolean;
  try {
    sample = await isSampleVisitor();
  } catch {
    return null;
  }
  return sample ? sampleResponse(req) : null;
}
