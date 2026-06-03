import Link from 'next/link';
import { getActiveTenant, getIdToken, getAccessToken } from '@/shared/lib/session';
import { decodeJwtPayload } from '@/shared/lib/jwt';

export const dynamic = 'force-dynamic';

/**
 * 계정 설정 — TASK-PC-FE-041. A **read-only** account page reached from the
 * top-bar account menu (⋮ → 계정 설정). It surfaces the signed-in operator's
 * own identity, decoded **verification-free** from the GAP OIDC `id_token` /
 * access-token cookies (display only, NEVER an authorization input — § 2.1;
 * the `id_token` is not a credential), plus the current active tenant.
 *
 * No mutation here: the operator's editable self-service profile (default
 * finance account, password) already lives in `/operators` (내 프로필 /
 * `me/profile` + `me/password`, § 2.4.3); this page links there. Credentials
 * and profile identity are owned by GAP (the IdP). No new API / route /
 * contract — claims-only, so it works regardless of tenant selection.
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

      <div className="mt-6 rounded-lg border border-border bg-muted px-4 py-4 text-sm text-muted-foreground">
        <p className="mb-2 font-medium text-foreground">프로필 편집</p>
        <p className="mb-3">
          기본 finance 계정·비밀번호 등 셀프서비스 프로필은 운영자 관리
          화면의 “내 프로필”에서 변경할 수 있습니다.
        </p>
        <Link
          href="/operators"
          data-testid="account-to-operators"
          className="inline-block text-sm font-medium text-foreground underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          운영자 관리로 이동
        </Link>
      </div>
    </section>
  );
}
