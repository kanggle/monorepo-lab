import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderToStaticMarkup } from 'react-dom/server';

/**
 * TASK-PC-FE-301 — the single render + judgement site for "no tenant is
 * currently active". `NoTenantNoticeBody` (sync, sourced by every gated
 * screen) carries the ONLY copy of the two paragraphs; `NoTenantNotice`
 * (async) is the thin wrapper that resolves {@link noTenantNoticeKind} for a
 * caller that does not already have it.
 */

const noTenantNoticeKindMock = vi.fn();
vi.mock('@/shared/lib/active-tenant-default', () => ({
  noTenantNoticeKind: () => noTenantNoticeKindMock(),
}));

vi.mock('next/link', () => ({
  default: ({
    href,
    children,
    ...rest
  }: {
    href: string;
    children: React.ReactNode;
  }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

import { NoTenantNotice, NoTenantNoticeBody } from '@/widgets/no-tenant-notice';

beforeEach(() => {
  noTenantNoticeKindMock.mockReset();
});

describe('NoTenantNoticeBody — the shared copy (owner decision ⓒ, 2026-09-26 UTC)', () => {
  it("kind='zero' → the no-reachable-tenant notice, no switcher link, no description", () => {
    render(
      <NoTenantNoticeBody
        kind="zero"
        testId="some-screen-no-tenant"
        description={<>screen-specific description (must NOT render in this branch)</>}
      />,
    );
    const el = screen.getByTestId('some-screen-no-tenant');
    expect(el).toHaveAttribute('data-tenant-notice', 'zero');
    expect(el).toHaveTextContent('이 계정에는 접근 가능한 테넌트가 없습니다.');
    expect(el).toHaveTextContent('권한이 필요합니다');
    expect(el).not.toHaveTextContent('테넌트를 먼저 선택하세요');
    expect(el).not.toHaveTextContent('screen-specific description');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it("kind='select' → the ORIGINAL «테넌트를 먼저 선택하세요» + the caller's description + a switcher link", () => {
    render(
      <NoTenantNoticeBody
        kind="select"
        testId="some-screen-no-tenant"
        description={<>screen-specific description</>}
      />,
    );
    const el = screen.getByTestId('some-screen-no-tenant');
    expect(el).toHaveAttribute('data-tenant-notice', 'select');
    expect(el).toHaveTextContent('테넌트를 먼저 선택하세요.');
    expect(el).toHaveTextContent('screen-specific description');
    expect(el).not.toHaveTextContent('접근 가능한 테넌트가 없습니다');
    expect(screen.getByRole('link', { name: '카탈로그로 이동' })).toHaveAttribute(
      'href',
      '/console',
    );
  });

  it('linkHref/linkLabel are overridable (the tenant-detail route points back to its own list)', () => {
    render(
      <NoTenantNoticeBody
        kind="select"
        testId="tenant-detail-no-tenant"
        description={<>d</>}
        linkHref="/tenants"
        linkLabel="목록으로"
      />,
    );
    expect(screen.getByRole('link', { name: '목록으로' })).toHaveAttribute(
      'href',
      '/tenants',
    );
  });
});

describe('NoTenantNotice — async wrapper resolves the judgement then delegates', () => {
  it("resolves 'zero' from noTenantNoticeKind and renders the zero body", async () => {
    noTenantNoticeKindMock.mockResolvedValue('zero');
    const out = await NoTenantNotice({ testId: 'x-no-tenant', description: <>d</> });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('x-no-tenant');
    expect(html).toContain('접근 가능한 테넌트가 없습니다');
  });

  it("resolves 'select' from noTenantNoticeKind and renders the select body", async () => {
    noTenantNoticeKindMock.mockResolvedValue('select');
    const out = await NoTenantNotice({ testId: 'x-no-tenant', description: <>d</> });
    const html = renderToStaticMarkup(out);
    expect(html).toContain('x-no-tenant');
    expect(html).toContain('테넌트를 먼저 선택하세요');
  });
});
