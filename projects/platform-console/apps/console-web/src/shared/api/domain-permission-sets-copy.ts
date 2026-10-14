/**
 * ADR-MONO-083 D7 revocation-latency copy — shared verbatim across every
 * 업무 권한 세트 mutation surface (TASK-PC-FE-338).
 *
 * WHY THIS LIVES IN `shared/` — slice a (`features/domain-permission-sets`,
 * rows 1-5: the set CRUD screen) owned this string first; slice b adds TWO
 * more consumers of the exact same copy (`features/operators`' per-operator
 * «업무 권한» revoke/grant confirms — rows 6-8, and `features/operator-groups`'
 * group 업무 권한 세트 add/remove confirms — rows 9-11). `architecture.md`
 * § Forbidden Dependencies bars a `features/A → features/B` import, and the
 * value is genuinely shared (not route-level composition, not a borrowed
 * producer wire type) — the exact remedy the architecture doc prescribes:
 * "값이 진짜 공유 → `shared/` 로 승격하고 양쪽 다 거기서 import" (precedent:
 * `shared/api/rbac-catalog.ts`, `shared/lib/money.ts`).
 *
 * 🔴 Pre-P3 wording (TASK-BE-632 not deployed): the bound is **30 minutes**,
 * never «즉시». Once P3 ships, every consumer of this constant updates
 * together (the whole point of the promotion — one wording, not three
 * copies that can drift apart at the P3 cut-over).
 */
export const DOMAIN_SET_LATENCY_COPY =
  '다음 테넌트 재교환부터 적용됩니다. 이미 발급된 도메인 토큰은 만료(최대 30분)까지 그대로 쓰입니다.';
