import { getActiveTenant, getIdToken, getAccessToken } from '@/shared/lib/session';
import { decodeJwtPayload } from '@/shared/lib/jwt';
import { AccountSelfService } from '@/features/operators';
import { getCatalog } from '@/features/catalog';

export const dynamic = 'force-dynamic';

/**
 * 계정 설정 — TASK-PC-FE-041 (read-only identity) + TASK-PC-FE-045 (self-
 * service). Reached from the top-bar account menu (⋮ → 계정 설정). It surfaces
 * the signed-in operator's own identity, decoded **verification-free** from the
 * IAM OIDC `id_token` / access-token cookies (display only, NEVER an
 * authorization input — § 2.1; the `id_token` is not a credential), plus the
 * current active tenant.
 *
 * TASK-PC-FE-045: the operator's editable SELF-service (내 비밀번호 변경 +
 * 내 기본 finance 계정 = `me/password` + `me/profile`, § 2.4.3) now lives HERE
 * (was on `/operators`), so 계정 설정 = 내 것 and 운영자 관리 = 남 관리 (AWS/GCP
 * console split). The forms are a client island ({@link AccountSelfService});
 * the initial default-finance-account value is read from the operator-scoped
 * registry (`getCatalog()`), identical to the prior `/operators` derivation.
 * API / proxy / endpoints are unchanged. Credentials + profile identity are
 * owned by IAM (the IdP).
 */
function strClaim(claims: Record<string, unknown> | null, key: string): string | null {
  const v = claims?.[key];
  return typeof v === 'string' && v.trim() !== '' ? v : null;
}

function listClaim(claims: Record<string, unknown> | null, key: string): string[] {
  const v = claims?.[key];
  if (!Array.isArray(v)) return [];
  return v.filter((x): x is string => typeof x === 'string' && x.trim() !== '');
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1 px-4 py-3 sm:flex-row sm:items-center sm:gap-4">
      <dt className="w-40 shrink-0 text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words text-sm text-foreground">{children}</dd>
    </div>
  );
}

export default async function AccountPage() {
  const [idToken, accessToken, activeTenant] = await Promise.all([
    getIdToken(),
    getAccessToken(),
    getActiveTenant(),
  ]);
  const idClaims = decodeJwtPayload(idToken);
  const accessClaims = decodeJwtPayload(accessToken);

  // TASK-PC-FE-045: initial default-finance-account for the self profile form,
  // read from the operator-scoped registry (moved verbatim from the prior
  // /operators derivation). Registry unavailable ⇒ empty input + active set
  // flow (the producer is authoritative; this is only the seed value).
  let initialDefaultAccountId: string | null = null;
  try {
    const catalog = await getCatalog();
    initialDefaultAccountId =
      catalog.products.find((p) => p.productKey === 'finance')?.operatorContext
        ?.defaultAccountId ?? null;
  } catch {
    initialDefaultAccountId = null;
  }

  const accountId =
    strClaim(idClaims, 'preferred_username') ??
    strClaim(idClaims, 'email') ??
    strClaim(idClaims, 'sub') ??
    strClaim(accessClaims, 'sub') ??
    '—';
  const email = strClaim(idClaims, 'email');
  const accountType = strClaim(accessClaims, 'account_type');
  const homeTenantRaw = strClaim(accessClaims, 'tenant_id');
  const homeTenant =
    homeTenantRaw === '*' ? '플랫폼 범위 (*)' : (homeTenantRaw ?? '—');
  const entitledDomains = listClaim(accessClaims, 'entitled_domains');

  return (
    <section aria-labelledby="account-heading" className="max-w-2xl">
      <h1 id="account-heading" className="mb-2 text-2xl font-semibold text-foreground">
        계정 설정
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        로그인한 운영자 계정 정보입니다 (읽기 전용). 자격 증명과 프로필은
        GAP(IdP)에서 관리됩니다.
      </p>

      <dl
        data-testid="account-info"
        className="divide-y divide-border rounded-lg border border-border"
      >
        <Row label="아이디">
          <span data-testid="account-id" className="font-medium">
            {accountId}
          </span>
        </Row>
        {email && <Row label="이메일">{email}</Row>}
        {accountType && <Row label="계정 유형">{accountType}</Row>}
        <Row label="홈 테넌트">{homeTenant}</Row>
        <Row label="현재 활성 테넌트">
          {activeTenant ?? (
            <span className="text-muted-foreground">미선택</span>
          )}
        </Row>
        <Row label="권한 도메인">
          {entitledDomains.length > 0 ? (
            <span className="flex flex-wrap gap-1.5">
              {entitledDomains.map((d) => (
                <span
                  key={d}
                  className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                >
                  {d}
                </span>
              ))}
            </span>
          ) : (
            <span className="text-muted-foreground">—</span>
          )}
        </Row>
      </dl>

      <div className="mt-8">
        <h2 className="mb-3 text-lg font-semibold text-foreground">
          셀프서비스
        </h2>
        <p className="mb-4 text-sm text-muted-foreground">
          본인 계정의 비밀번호와 기본 finance 계정을 직접 변경합니다. (다른
          운영자 관리는 운영자 관리 메뉴에서 수행합니다.)
        </p>
        <AccountSelfService initialDefaultAccountId={initialDefaultAccountId} />
      </div>
    </section>
  );
}
