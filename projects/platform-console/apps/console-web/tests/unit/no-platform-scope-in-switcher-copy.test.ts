import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-353 — the tenant switcher has never offered `*`
 * (`selectableTenants()` is the union of registry tenants, and the registry
 * never lists `*` — AC-0 ①). Six places told the operator to pick «플랫폼
 * 스코프(*)» there, or assumed the active tenant could be `*`. Each screen's
 * real gate is «any active tenant» (AC-0 ②), so the copy now says that, and
 * says what the chosen tenant changes on that screen (owner-approved copy,
 * 2026-10-20 UTC).
 *
 * Read from source because these notices render only on the no-tenant path
 * of seven different pages; JSX line breaks collapse to one space, so the
 * text is compared whitespace-normalised.
 */
const SRC = path.resolve(__dirname, '..', '..', 'src');
const CONSOLE = path.join(SRC, 'app', '(console)');

function text(file: string): string {
  return readFileSync(file, 'utf8').replace(/\s+/g, ' ');
}

const NOTICES: ReadonlyArray<readonly [string, string]> = [
  [
    'tenants/page.tsx',
    '상단의 테넌트 스위처에서 테넌트를 하나 선택한 뒤 다시 시도하세요. 어느 테넌트를 골라도 보이는 목록은 같습니다.',
  ],
  [
    'tenants/[tenantId]/page.tsx',
    '상단의 테넌트 스위처에서 테넌트를 하나 선택한 뒤 다시 시도하세요. 어느 테넌트를 골라도 보이는 목록은 같습니다.',
  ],
  [
    'org-hierarchy/page.tsx',
    '테넌트 스위처에서 테넌트를 하나 선택한 뒤 다시 시도하세요. 어느 테넌트를 골라도 내 권한 범위의 조직 계층이 보입니다.',
  ],
  [
    'operator-groups/page.tsx',
    '테넌트 스위처에서 테넌트를 하나 선택한 뒤 다시 시도하세요. 어느 테넌트를 골라도 내 권한 범위의 그룹이 모두 보입니다.',
  ],
  [
    'domain-permission-sets/page.tsx',
    '테넌트 스위처에서 테넌트를 선택한 뒤 다시 시도하세요. 기본 제공 세트와 고른 테넌트의 세트가 보입니다.',
  ],
];

describe('TASK-PC-FE-353 — no screen sends the operator to a switcher entry that does not exist', () => {
  it.each(NOTICES)('%s says «pick any tenant» and what that tenant changes', (file, sentence) => {
    const src = text(path.join(CONSOLE, file));
    expect(src).toContain(sentence);
    expect(src).not.toMatch(/플랫폼 스코프\s*\(?\*/);
  });

  it('security-settings has no `*` branch and the guide no longer describes one', () => {
    const page = text(path.join(CONSOLE, 'security-settings', 'page.tsx'));
    expect(page).not.toContain("tenant === '*'");
    expect(page).not.toContain('security-settings-platform-scope');

    const guide = text(path.join(SRC, 'shared', 'guide', 'permission-map.ts'));
    expect(guide).toContain('활성 테넌트의 정책을 보여 주고 바꾼다');
    expect(guide).not.toContain('활성 테넌트가 플랫폼 스코프(*)이면');
  });
});
