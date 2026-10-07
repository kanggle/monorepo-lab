# Task ID

TASK-PC-FE-312

# Title

조직 계층 노드 상세에서 **테넌트 추가 · 옮기기 · 빼기** + 잃는 도메인 확인 화면 · `/tenants` 생성 폼의 «소속 노드 (선택)» (`ADR-MONO-047` 개정 2026-10-07)

# Status

ready

# Owner

platform-console

# Task Tags

- console-web
- frontend

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 백엔드 판정(`TASK-BE-625`) 위의 화면 · 확인 창 · 폼 칸.

---

# Dependency Markers

- **선행**: `projects/iam-platform` `TASK-BE-625` 머지(API · 계약).
- 근거 ADR: `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § 개정 (2026-10-07 UTC) — 결정 «양쪽을 다 관리하는 사람만» · P1 · P2.

# Goal

지금 조직 계층 화면은 노드의 «소속 테넌트 (하위 노드 포함)» 를 **보여 주기만** 한다(`apps/console-web/src/features/org-hierarchy/components/OrgNodeDetail.tsx:234-260`). BE-625 의 쓰기를 화면에 연결한다:

- 노드 상세 «소속 테넌트» 에 **[테넌트 추가]**(내 관리 범위 안의 테넌트에서 고름) · 각 테넌트 옆 **[다른 노드로 옮기기]** · **[빼기]**.
- 확인 창(P2): 목적지 노드의 유효 상한 때문에 **잃게 되는 도메인**을 이름으로 보여 준다(«이동하면 이 도메인들이 꺼집니다: …»). 거절이 아니라 확인이다.
- `/tenants` 생성 폼에 «소속 노드 (선택)» — 비워 두면 지금처럼 무소속.
- 서버가 404(범위 밖)를 주면 «이 노드 또는 테넌트를 관리할 권한이 없습니다» 로 — 존재 여부를 화면이 추측해 말하지 않는다.

# Scope

## In Scope

- `features/org-hierarchy`: 추가 · 옮기기 · 빼기 UI + 사유 입력(기존 `OrgReasonDialog` 재사용) + 확인 창
- `app/api/…` 동일 출처 프록시 라우트(BE-625 엔드포인트) — 기존 org-node 프록시 모양 그대로
- `features/tenants`: 생성 폼 칸 + 프록시 본문 스키마(`app/api/tenants/_proxy.ts:22-30`)에 선택 `orgNodeId`
- 단위 시험 + bite

## Out of Scope

- 권한 판정(서버가 권위 — 화면은 버튼 노출만 조절)
- 노드 생성 권한 변경

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: `OrgNodeDetail.tsx` 소속 테넌트 절 · org-node 프록시 라우트 · 테넌트 생성 프록시 스키마 file:line · BE-625 가 «잃는 도메인» 을 응답으로 주는지(아니면 콘솔이 상한 ∩ 구독으로 계산).
- [ ] **AC-1** — 추가 · 옮기기 · 빼기가 렌더 DOM 에서 동작하고, 성공 뒤 소속 테넌트 목록이 갱신된다.
- [ ] **AC-2** — 상한이 걸린 노드로 옮길 때 확인 창이 잃는 도메인을 이름으로 보여 준다 · 잃는 것이 없으면 그 문단이 없다(대조군).
- [ ] **AC-3** — 서버 404 → 권한 문구(존재 추측 없음).
- [ ] **AC-4** — 생성 폼: 노드 고르면 본문에 `orgNodeId` · 비우면 본문에 칸 없음(회귀).
- [ ] **AC-5** — bite: 확인 창의 «잃는 도메인» 계산을 지우면 AC-2 칸만 빨강.
- [ ] **AC-6** — `tsc` · `lint` · `vitest` 전체 rc=0 · 콘솔 e2e 디렉터리 grep(`org-node`, `org-hierarchy`) 후 영향 확인 · 머지 뒤 첫 nightly 콘솔 잡 확인.

# Related Specs

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § 개정 (2026-10-07)
- `specs/services/console-web/architecture.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Org Hierarchy · § tenants (BE-625 가 추가)

# Edge Cases

- 내 관리 범위 안의 테넌트가 0 개 — [테넌트 추가] 를 숨기지 않고 «추가할 수 있는 테넌트가 없습니다» 로.
- 샘플 방문자(ADR-MONO-074) — 쓰기 버튼 없음(기존 샘플 규율).

# Failure Scenarios

1. 확인 창 없이 옮긴다 — 운영자가 모르는 사이 그 테넌트의 도메인 화면이 꺼진다.
2. 화면이 404 를 «없는 테넌트» 로 말한다 — 남의 테넌트 존재 여부를 추측해 흘린다.
