import { describe, it, expect } from 'vitest';
import { DOMAIN_FEATURES } from '@/shared/guide/domain-features';

/**
 * TASK-PC-FE-321 AC-5 — 문구 가드. 요약 문구(`oneLine` · 묶음 제목 · 항목 `text`)에
 * 구현 용어가 없는지 — 독자는 운영자·방문자다(Scope § 문구 규칙).
 */
const FORBIDDEN_WORDS = ['saga', 'elasticsearch', 'oidc', 'kafka', 'outbox', '-service', '/api/'];

function allText(): { where: string; text: string }[] {
  const out: { where: string; text: string }[] = [];
  for (const d of DOMAIN_FEATURES) {
    out.push({ where: `${d.key}.oneLine`, text: d.oneLine });
    for (const g of d.groups) {
      out.push({ where: `${d.key}/${g.title} (group title)`, text: g.title });
      for (const item of g.items) {
        out.push({ where: `${d.key}/${g.title}: "${item.text}"`, text: item.text });
      }
    }
  }
  return out;
}

describe('domain-features — wording guard (AC-5)', () => {
  it('has no implementation jargon in oneLine / group titles / item text', () => {
    const bad: string[] = [];
    for (const { where, text } of allText()) {
      const lower = text.toLowerCase();
      for (const word of FORBIDDEN_WORDS) {
        if (lower.includes(word)) bad.push(`${where} ~ forbidden word "${word}"`);
      }
    }
    expect(bad, bad.join('\n')).toEqual([]);
  });

  it('every item has non-empty text, and every domain has a non-empty oneLine', () => {
    for (const { where, text } of allText()) {
      expect(text.trim().length, where).toBeGreaterThan(0);
    }
  });
});
