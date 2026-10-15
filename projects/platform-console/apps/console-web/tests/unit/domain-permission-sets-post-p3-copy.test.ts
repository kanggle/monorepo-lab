/**
 * TASK-PC-FE-338 slice c-2 PR 1 — P3 (`TASK-BE-632`) is live since the 27th demo window: the
 * assume-tenant token's `roles` = assigned sets ∩ the tenant's subscribed domains, token TTL 300 s.
 *
 * Two pre-P3 statements must be gone from every copy, not only from the screens:
 *   1. «적용 예정» / «P3 … 배포 전» — a set now DOES narrow the token (contract § 2.4.3.4 «Status honesty»).
 *   2. the pre-P3 latency bound «최대 30분». The post-P3 bound is «최대 약 6분»: TTL 300 s + the
 *      resource servers' 60 s clock skew (BE-632 AC-6 measured `exp + 62/63 s`; owner decision
 *      2026-10-15 — `auth-api.md` § 회수 지연).
 *
 * The guide copies (`iam-guide/data.ts`, `permission-map.ts`) are read by people, not rendered
 * by the three screens' tests, so they get their own assertion here.
 */
import { describe, it, expect } from 'vitest';
import { DOMAIN_SET_LATENCY_COPY } from '@/shared/api/domain-permission-sets-copy';
import { CONSOLE_MENUS } from '@/features/iam-guide/data';
import { PERMISSION_MAP } from '@/shared/guide/permission-map';

describe('domain permission sets — post-P3 copy', () => {
  it('the shared latency copy says «최대 약 6분», not «30분», never «즉시»', () => {
    expect(DOMAIN_SET_LATENCY_COPY).toContain('다음 테넌트 재교환부터 적용됩니다');
    expect(DOMAIN_SET_LATENCY_COPY).toContain('최대 약 6분');
    expect(DOMAIN_SET_LATENCY_COPY).not.toMatch(/30분/);
    expect(DOMAIN_SET_LATENCY_COPY).not.toMatch(/즉시/);
  });

  it('the IAM guide «업무 권한 세트» menu no longer says «적용 예정» / «배포되기 전»', () => {
    const menu = CONSOLE_MENUS.find((m) => m.href === '/domain-permission-sets');
    expect(menu).toBeDefined();
    const text = JSON.stringify(menu);
    expect(text).not.toMatch(/적용 예정/);
    expect(text).not.toMatch(/배포되기 전/);
    expect(text).toMatch(/구독한 도메인/);
  });

  it('the permission-map row for /domain-permission-sets no longer says «적용 예정» / «배포 전»', () => {
    const row = PERMISSION_MAP.find((r) => r.href === '/domain-permission-sets');
    expect(row).toBeDefined();
    const text = JSON.stringify(row);
    expect(text).not.toMatch(/적용 예정/);
    expect(text).not.toMatch(/배포 전/);
  });
});
