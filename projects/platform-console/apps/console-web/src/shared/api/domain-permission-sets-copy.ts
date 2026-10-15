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
 * 🔴 Post-P3 wording (TASK-BE-632 live since the 27th demo window — TASK-PC-FE-338
 * c-2 PR 1, owner decision 2026-10-15): the bound is **«최대 약 6분»**, never
 * «즉시». The assume token's TTL is 300 s, and the resource servers accept a
 * token until `exp` + 60 s (Spring `JwtTimestampValidator` default skew —
 * BE-632 AC-6 measured `exp + 62/63 s`; `auth-api.md` § 회수 지연). Before P3
 * this read «최대 30분». Every consumer of this constant changes together.
 */
export const DOMAIN_SET_LATENCY_COPY =
  '다음 테넌트 재교환부터 적용됩니다. 이미 발급된 도메인 토큰은 만료(최대 약 6분)까지 그대로 쓰입니다.';

/**
 * What a domain permission set DOES once P3 is live (contract § 2.4.3.4
 * «Status honesty») — the persistent effect note on the three set surfaces
 * (the 업무 권한 세트 screen, the operator «업무 권한» dialog, the group
 * «업무 권한 세트» panel). It replaced the pre-P3 «적용 예정» note.
 */
export const DOMAIN_SET_EFFECT_COPY =
  '운영자가 테넌트를 고르면, 배정된 세트의 역할 중 그 테넌트가 구독한 도메인의 역할만 도메인 토큰에 실립니다.';
