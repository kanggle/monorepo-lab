import { test, expect } from '@playwright/test';
import { shouldSkipGap, loginAsSeededConsumer } from './helpers/auth';

/**
 * TASK-INT-024 (ADR-MONO-021 § 3.3 step 3, D4 step 3) — assert the
 * `account_type=CONSUMER` claim survives the FULL GAP OIDC round-trip into the
 * web-store NextAuth session.
 *
 * This is the e2e layer on top of TASK-BE-329 (which emits the claim on the GAP
 * access + id token, proven by the auth-service `FormLoginIntegrationTest` real-
 * MySQL IT) and TASK-BE-330 (which sets the type explicitly at provisioning).
 * Here we verify the claim makes it all the way through:
 *
 *   GAP id_token (account_type=CONSUMER)
 *     → NextAuth `profile()` (auth.ts: profile.account_type → user.accountType)
 *     → `jwt()` callback (token.accountType)
 *     → `session()` callback (session.accountType)
 *     → exposed on GET /api/auth/session
 *
 * Before BE-329 the GAP pipeline emitted NO account_type claim and web-store's
 * signIn callback ACCEPTED its absence (the guard only rejected an explicit
 * non-CONSUMER). Now the claim is present and CONSUMER — this spec is the
 * regression gate for that contract.
 *
 * Requires a real GAP container built from BE-329+ (emits account_type) +
 * the consumer seed (`e2e/fixtures/gap-consumer-seed.sql`, account_type=CONSUMER).
 * Gated on SKIP_GAP_E2E so the default CI run (=1) skips it — no regression to
 * the nightly frontend-e2e job. See `docker-compose.gap-e2e.yml`.
 */
test.describe('account_type claim end-to-end (GAP → web-store session)', () => {
  test.skip(shouldSkipGap(), 'requires a running GAP container (SKIP_GAP_E2E=1)');

  test('seeded consumer login surfaces account_type=CONSUMER on the web-store session', async ({
    page,
  }) => {
    // 1. Log in as the seeded CONSUMER through the real GAP OIDC flow.
    await loginAsSeededConsumer(page);

    // 2. Authenticated: the header profile menu (avatar) is present.
    await expect(page.getByRole('button', { name: '프로필 메뉴' })).toBeVisible({
      timeout: 15_000,
    });

    // 3. THE assertion: the NextAuth session — populated from the GAP id_token's
    //    account_type claim — exposes account_type=CONSUMER. (If the claim were
    //    absent, session.accountType would be null; if OPERATOR, the session
    //    callback would have anonymized the session and the login would have
    //    bounced to /login?error=account_type_mismatch before step 2.)
    const session = await page.request
      .get('/api/auth/session')
      .then((r) => r.json() as Promise<{ accountType?: string | null; accountId?: string | null }>);

    expect(session.accountType).toBe('CONSUMER');
    expect(session.accountId).toBeTruthy();
  });
});
