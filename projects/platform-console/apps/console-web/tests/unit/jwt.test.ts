import { describe, it, expect } from 'vitest';
import {
  readJwtClaim,
  homeTenantFromAccessToken,
  decodeJwtPayload,
} from '@/shared/lib/jwt';

/** Build a JWT-shaped string (header.payload.signature) with the given payload. */
function jwt(payload: Record<string, unknown>): string {
  const b64 = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `eyJhbGciOiJSUzI1NiJ9.${b64}.sig`;
}

describe('jwt — homeTenantFromAccessToken (TASK-PC-FE-036 active-tenant default)', () => {
  it('returns the real-customer tenant_id claim', () => {
    expect(homeTenantFromAccessToken(jwt({ tenant_id: 'acme-corp' }))).toBe(
      'acme-corp',
    );
  });

  it('returns null for the platform-scope sentinel "*" (operator must select)', () => {
    expect(homeTenantFromAccessToken(jwt({ tenant_id: '*' }))).toBeNull();
  });

  it('returns null when the tenant_id claim is absent', () => {
    expect(homeTenantFromAccessToken(jwt({ sub: 'op-1' }))).toBeNull();
  });

  it('returns null for an empty tenant_id', () => {
    expect(homeTenantFromAccessToken(jwt({ tenant_id: '' }))).toBeNull();
  });

  it('returns null for a malformed / non-JWT token (never throws)', () => {
    expect(homeTenantFromAccessToken('not-a-jwt')).toBeNull();
    expect(homeTenantFromAccessToken('')).toBeNull();
    expect(homeTenantFromAccessToken('a.b.c')).toBeNull();
  });

  it('readJwtClaim reads arbitrary claims and is null-safe', () => {
    const t = jwt({ tenant_id: 'globex-corp', entitled_domains: ['scm', 'erp'] });
    expect(readJwtClaim(t, 'tenant_id')).toBe('globex-corp');
    expect(readJwtClaim(t, 'entitled_domains')).toEqual(['scm', 'erp']);
    expect(readJwtClaim(t, 'missing')).toBeNull();
    expect(readJwtClaim('garbage', 'tenant_id')).toBeNull();
  });
});

describe('jwt — decodeJwtPayload (TASK-PC-FE-041 account identity)', () => {
  it('returns the full payload object for a well-formed token', () => {
    const t = jwt({ email: 'ops@example.com', sub: 'op-1', entitled_domains: ['wms'] });
    expect(decodeJwtPayload(t)).toEqual({
      email: 'ops@example.com',
      sub: 'op-1',
      entitled_domains: ['wms'],
    });
  });

  it('is null-safe: null / undefined / empty / non-JWT input → null', () => {
    expect(decodeJwtPayload(null)).toBeNull();
    expect(decodeJwtPayload(undefined)).toBeNull();
    expect(decodeJwtPayload('')).toBeNull();
    expect(decodeJwtPayload('garbage')).toBeNull();
    expect(decodeJwtPayload('only.one')).toBeNull(); // 2-part but payload not JSON
  });

  it('returns null when the payload segment is not a JSON object', () => {
    // payload = base64url("\"a-string\"") → parses to a string, not an object.
    const b64 = Buffer.from(JSON.stringify('a-string')).toString('base64url');
    expect(decodeJwtPayload(`eyJhbGciOiJSUzI1NiJ9.${b64}.sig`)).toBeNull();
  });

  it('readJwtClaim is built on decodeJwtPayload (behaviour preserved)', () => {
    const t = jwt({ tenant_id: 'acme-corp' });
    expect(readJwtClaim(t, 'tenant_id')).toBe(decodeJwtPayload(t)?.tenant_id);
    expect(readJwtClaim(t, 'tenant_id')).toBe('acme-corp');
  });
});
