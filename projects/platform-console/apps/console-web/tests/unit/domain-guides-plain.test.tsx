import type { ComponentType } from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { IamGuideScreen } from '@/features/iam-guide';
import { WmsGuideScreen } from '@/features/wms-guide';
import { ScmGuideScreen } from '@/features/scm-guide';
import { FinanceGuideScreen } from '@/features/finance-guide';
import { ErpGuideScreen } from '@/features/erp-guide';
import { EcommerceGuideScreen } from '@/features/ecommerce-guide';
import { DOMAIN_GUIDE_TAB_KEYS } from '@/shared/guide/DomainGuideTabs';

/**
 * TASK-PC-FE-323 — 도메인 가이드 6개는 처음 보는 사람이 읽는 화면이다(소유자 결정 «쉽게 ·
 * 간단 명료하게 · 출처는 생략»). 화면에 보이는 글자에 티켓 · ADR 번호, 파일 이름, 「출처」 ·
 * 「근거」 표시가 없어야 한다. 코드 주석과 데이터의 `sources` 필드는 화면에 안 그려지므로
 * 여기서 걸리지 않는다 — 이 시험은 렌더된 `textContent` 만 본다.
 */
const GUIDES: [string, ComponentType][] = [
  ['iam-guide', IamGuideScreen],
  ['wms-guide', WmsGuideScreen],
  ['scm-guide', ScmGuideScreen],
  ['finance-guide', FinanceGuideScreen],
  ['erp-guide', ErpGuideScreen],
  ['ecommerce-guide', EcommerceGuideScreen],
];

const LEAKS: [string, RegExp][] = [
  ['ticket id', /TASK-[A-Z]+-?\d*/],
  ['ADR id', /ADR-[A-Z]+-?\d*/],
  ['file name', /[\w-]+\.(md|sql|java|kt|tsx?|ya?ml|sh|py|json|hcl)\b/],
  ['file:line', /[\w-]+\.\w+:\d+/],
];

describe.each(GUIDES)('%s — plain language (TASK-PC-FE-323)', (prefix, Screen) => {
  it('no ticket / ADR ids or file names in any of the 8 tabs', () => {
    render(<Screen />);
    const found: string[] = [];
    for (const key of DOMAIN_GUIDE_TAB_KEYS) {
      const panel = screen.getByTestId(`${prefix}-tabs-panel-${prefix}-tab-${key}`);
      const text = panel.textContent ?? '';
      for (const [what, re] of LEAKS) {
        const m = text.match(re);
        if (m) found.push(`${key}: ${what} «${m[0]}»`);
      }
    }
    // the tab bodies above sit inside the guide; anything above the tabs (intro, reading path) too
    const head = (screen.getByTestId(prefix).textContent ?? '').split('도메인 전체 설명')[0];
    for (const [what, re] of LEAKS) {
      const m = head.match(re);
      if (m) found.push(`intro: ${what} «${m[0]}»`);
    }
    expect(found).toEqual([]);
  });

  it('shows no «출처» / «근거» citation', () => {
    render(<Screen />);
    const guide = screen.getByTestId(prefix);
    expect(within(guide).queryAllByText(/^출처$/)).toHaveLength(0);
    expect(within(guide).queryByRole('columnheader', { name: '근거' })).not.toBeInTheDocument();
    expect(within(guide).queryByRole('columnheader', { name: '출처' })).not.toBeInTheDocument();
  });
});
