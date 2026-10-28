/**
 * 운영자 «도메인 접근» · 그룹 «세트» 배정 sample fixtures (TASK-PC-FE-383). Both GETs live on the
 * already-`ready` `operators` / `groups` surfaces, so the ledger never showed the gap — these cells
 * are what pins them. Every assertion goes THROUGH `sampleResponse` and parses with the SAME zod
 * schema the real dialog / panel uses.
 *
 * 🔴 Schema-only is not enough (the TASK-PC-FE-373 `alertId` ↔ `id` class): the console schema is
 *    tolerant (`source: z.string()`), so the wire keys, the allowed source kinds and the 404 shapes
 *    are pinned below against `admin-api.md` LITERALLY (copied, not derived from the fixture).
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { DomainAccessResponseSchema } from '@/features/operators/api/domain-access-types';
import { GroupDomainSetListSchema } from '@/features/operator-groups/api/domain-sets-types';
import { DomainPermissionSetListSchema } from '@/features/domain-permission-sets/api/types';
import { IAM_GROUPS, IAM_OPERATORS } from '@/shared/sample/fixtures/iam';

function get(surface: string, path: string, method = 'GET'): Response {
  return sampleResponse({ core: 'iam', surface, method, path });
}

const accessPath = (id: string) => `/api/admin/operators/${encodeURIComponent(id)}/domain-access`;
const groupSetsPath = (id: string) => `/api/admin/groups/${encodeURIComponent(id)}/domain-permission-sets`;

// --- the producer contract, copied literally (admin-api.md) ----------------------------------
/** § GET …/operators/{operatorId}/domain-access — Response 200 top-level keys. */
const WIRE_ACCESS_KEYS = ['operatorId', 'roles', 'sets', 'tenantId'];
/** … `sets[]`. */
const WIRE_ACCESS_SET_KEYS = ['name', 'setId', 'sources', 'systemKey'];
/** … `sets[].sources[]` — DIRECT / MIGRATION / ORIGIN carry no group keys; GROUP adds both. */
const WIRE_SOURCE_KEYS = ['grantedAt', 'grantedBy', 'source', 'tenantId'];
const WIRE_GROUP_SOURCE_KEYS = [...WIRE_SOURCE_KEYS, 'groupId', 'groupName'].sort();
/** `sets[].sources[].source` — «DIRECT · MIGRATION · ORIGIN · GROUP». */
const CONTRACT_SOURCES = ['DIRECT', 'MIGRATION', 'ORIGIN', 'GROUP'];
/** § GET …/groups/{groupId}/domain-permission-sets — `items[]` keys. */
const WIRE_GROUP_ITEM_KEYS = ['grantedAt', 'grantedBy', 'name', 'setId', 'systemKey'];

const sortedKeys = (o: object) => Object.keys(o).sort();

async function setCatalogue() {
  const res = get('domain_permission_sets', '/api/admin/domain-permission-sets');
  return DomainPermissionSetListSchema.parse(await res.json()).items;
}

async function allAccess() {
  const out = [];
  for (const o of IAM_OPERATORS) {
    const res = get('operators', accessPath(o.operatorId));
    expect(res.status, o.operatorId).toBe(200);
    const raw = await res.json();
    out.push({ raw, parsed: DomainAccessResponseSchema.parse(raw) });
  }
  return out;
}

describe('operators — GET /api/admin/operators/{operatorId}/domain-access', () => {
  it('every sample operator answers 200, parses with DomainAccessResponseSchema, and carries exactly the producer keys', async () => {
    for (const { raw, parsed } of await allAccess()) {
      expect(sortedKeys(raw)).toEqual(WIRE_ACCESS_KEYS);
      expect(parsed.tenantId).toBe('sample');
      for (const s of (raw as { sets: object[] }).sets) expect(sortedKeys(s)).toEqual(WIRE_ACCESS_SET_KEYS);
    }
  });

  it('source rows: keys per kind follow the contract (GROUP adds groupId + groupName), grantedBy null only for MIGRATION', async () => {
    for (const { parsed } of await allAccess()) {
      for (const set of parsed.sets) {
        expect(set.sources.length, set.setId).toBeGreaterThan(0);
        for (const src of set.sources) {
          expect(CONTRACT_SOURCES, src.source).toContain(src.source);
          expect(src.tenantId).toBe('sample'); // no '*' operator in the sample world
          const keys = sortedKeys(src).filter((k) => (src as Record<string, unknown>)[k] !== undefined);
          if (src.source === 'GROUP') {
            expect(keys).toEqual(WIRE_GROUP_SOURCE_KEYS);
          } else {
            expect(keys).toEqual(WIRE_SOURCE_KEYS);
          }
          // «시스템 경로(MIGRATION · ORIGIN)는 null» — a person-granted row names its granter.
          expect(src.grantedBy === null, `${set.setId} ${src.source}`).toBe(src.source === 'MIGRATION');
        }
      }
    }
  });

  it('the sources shown include every kind the screen labels (직접 · 이전 호환 · 그룹) and a set held via two sources', async () => {
    const all = (await allAccess()).flatMap((a) => a.parsed.sets);
    const kinds = new Set(all.flatMap((s) => s.sources.map((x) => x.source)));
    expect([...kinds].sort()).toEqual(['DIRECT', 'GROUP', 'MIGRATION']);
    // contract: the PK includes the source → the same set can be DIRECT and GROUP at once.
    expect(all.some((s) => s.sources.some((x) => x.source === 'DIRECT') && s.sources.some((x) => x.source === 'GROUP'))).toBe(true);
  });

  it('AC-2 — every setId is in the TASK-PC-FE-382 set list, and name/systemKey agree with it', async () => {
    const catalogue = new Map((await setCatalogue()).map((s) => [s.setId, s]));
    for (const { parsed } of await allAccess()) {
      for (const set of parsed.sets) {
        const known = catalogue.get(set.setId);
        expect(known, `unknown set ${set.setId}`).toBeDefined();
        expect(set.name).toBe(known!.name);
        expect(set.systemKey).toBe(known!.systemKey);
      }
    }
  });

  it('`roles` = the sum of the sets\' roles, catalogue order, no duplicates', async () => {
    const catalogue = new Map((await setCatalogue()).map((s) => [s.setId, s]));
    const roleOrder = (
      (await get('domain_permission_sets', '/api/admin/domain-roles').json()) as { items: { role: string }[] }
    ).items.map((r) => r.role);
    for (const { parsed } of await allAccess()) {
      const expected = new Set(parsed.sets.flatMap((s) => catalogue.get(s.setId)!.roles.map((r) => r.role)));
      const got = parsed.roles.map((r) => r.role);
      expect(new Set(got)).toEqual(expected);
      expect(got.length).toBe(new Set(got).size);
      const idx = got.map((r) => roleOrder.indexOf(r));
      expect(idx).toEqual([...idx].sort((a, b) => a - b));
    }
  });

  it('AC-2 — a GROUP-sourced row exists only for a member of that group (and every member holds the template)', async () => {
    const membersOf = async (groupId: string) =>
      ((await get('groups', `/api/admin/groups/${groupId}/members`).json()) as { items: { operatorId: string }[] }).items.map(
        (m) => m.operatorId,
      );
    const access = await allAccess();
    for (const g of IAM_GROUPS) {
      const members = await membersOf(g.groupId);
      const templates = GroupDomainSetListSchema.parse(await get('groups', groupSetsPath(g.groupId)).json()).items;
      for (const a of access) {
        const groupSets = a.parsed.sets
          .filter((s) => s.sources.some((x) => x.source === 'GROUP' && x.groupId === g.groupId))
          .map((s) => s.setId)
          .sort();
        if (members.includes(a.parsed.operatorId)) {
          expect(groupSets, `${a.parsed.operatorId} ∈ ${g.groupId}`).toEqual(templates.map((t) => t.setId).sort());
        } else {
          expect(groupSets, `${a.parsed.operatorId} ∉ ${g.groupId}`).toEqual([]);
        }
        // the echoed groupName is the group's own name
        for (const s of a.parsed.sets)
          for (const x of s.sources)
            if (x.source === 'GROUP' && x.groupId === g.groupId) expect(x.groupName).toBe(g.name);
      }
    }
  });

  it('the 세트 목록\'s grantCount / groupCount equal the assignment rows this fixture answers (one world, two screens)', async () => {
    const access = await allAccess();
    const rowsOf = (setId: string) =>
      access.flatMap((a) => a.parsed.sets.filter((s) => s.setId === setId).flatMap((s) => s.sources)).length;
    const groupsOf = async (setId: string) => {
      let n = 0;
      for (const g of IAM_GROUPS) {
        const items = GroupDomainSetListSchema.parse(await get('groups', groupSetsPath(g.groupId)).json()).items;
        if (items.some((i) => i.setId === setId)) n += 1;
      }
      return n;
    };
    for (const s of await setCatalogue()) {
      expect(rowsOf(s.setId), `grantCount ${s.setId}`).toBe(s.grantCount);
      expect(await groupsOf(s.setId), `groupCount ${s.setId}`).toBe(s.groupCount);
    }
  });

  it('MIGRATION rows are on `legacy-compat` only (§ 출처: «이전 백필 — legacy-compat 만»)', async () => {
    const mig = (await allAccess()).flatMap((a) =>
      a.parsed.sets.filter((s) => s.sources.some((x) => x.source === 'MIGRATION')),
    );
    expect(mig.length).toBeGreaterThan(0);
    for (const s of mig) expect(s.systemKey).toBe('legacy-compat');
  });

  it('unknown operatorId → 404 OPERATOR_NOT_FOUND (admin-api.md: «404 OPERATOR_NOT_FOUND — operatorId 미존재 …»), flat envelope', async () => {
    const res = get('operators', accessPath('op-sample-9999'));
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('OPERATOR_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');
  });

  it('writes (POST grant / DELETE revoke) keep the existing 403 SAMPLE_READ_ONLY — no new rule', async () => {
    for (const [method, path] of [
      ['POST', '/api/admin/operators/op-sample-0001/domain-permission-sets'],
      ['DELETE', '/api/admin/operators/op-sample-0001/domain-permission-sets/0192f3a0-0000-7000-8000-00000000d001'],
    ] as const) {
      const res = get('operators', path, method);
      expect(res.status, `${method} ${path}`).toBe(403);
      expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
    }
  });

  it('a nearby path with no fixture stays 503 (never a silent 200)', () => {
    expect(get('operators', '/api/admin/operators/op-sample-0001/domain-access/x').status).toBe(503);
    // `/me/domain-access` is NOT called on the sample path (layout skips it for a visitor,
    // the inbox route is sample-gated first) — so it is deliberately unanswered.
    expect(get('operators', '/api/admin/me/domain-access').status).toBe(503);
  });
});

describe('groups — GET /api/admin/groups/{groupId}/domain-permission-sets', () => {
  it('every sample group answers 200 {items}, parses with GroupDomainSetListSchema, producer keys only', async () => {
    for (const g of IAM_GROUPS) {
      const res = get('groups', groupSetsPath(g.groupId));
      expect(res.status, g.groupId).toBe(200);
      const raw = (await res.json()) as { items: object[] };
      expect(Object.keys(raw)).toEqual(['items']);
      const parsed = GroupDomainSetListSchema.parse(raw);
      expect(parsed.items.length, g.groupId).toBeGreaterThan(0);
      for (const item of raw.items) expect(sortedKeys(item)).toEqual(WIRE_GROUP_ITEM_KEYS);
    }
  });

  it('AC-2 — every setId is in the TASK-PC-FE-382 set list, name/systemKey agree, grantedBy is a sample operator', async () => {
    const catalogue = new Map((await setCatalogue()).map((s) => [s.setId, s]));
    const operatorIds = IAM_OPERATORS.map((o) => o.operatorId) as string[];
    for (const g of IAM_GROUPS) {
      const { items } = GroupDomainSetListSchema.parse(await get('groups', groupSetsPath(g.groupId)).json());
      for (const i of items) {
        const known = catalogue.get(i.setId);
        expect(known, `unknown set ${i.setId}`).toBeDefined();
        expect(i.name).toBe(known!.name);
        expect(i.systemKey).toBe(known!.systemKey);
        expect(operatorIds).toContain(i.grantedBy);
      }
    }
  });

  it('unknown groupId → 404 GROUP_NOT_FOUND (admin-api.md: «Errors: 401, 403, 404 GROUP_NOT_FOUND» — not an empty list)', async () => {
    const res = get('groups', groupSetsPath('group-sample-9999'));
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('GROUP_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');
  });

  it('writes (POST / DELETE) keep the existing 403 SAMPLE_READ_ONLY', async () => {
    for (const [method, path] of [
      ['POST', groupSetsPath('group-sample-0001')],
      ['DELETE', `${groupSetsPath('group-sample-0001')}/0192f3a0-0000-7000-8000-00000000d001`],
    ] as const) {
      const res = get('groups', path, method);
      expect(res.status, `${method} ${path}`).toBe(403);
      expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
    }
  });

  it('the existing group paths are unchanged (detail / members / grants still answer)', () => {
    expect(get('groups', '/api/admin/groups/group-sample-0001').status).toBe(200);
    expect(get('groups', '/api/admin/groups/group-sample-0001/members').status).toBe(200);
    expect(get('groups', '/api/admin/groups/group-sample-0001/grants').status).toBe(200);
  });
});
