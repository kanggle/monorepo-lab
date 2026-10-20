# Task ID

TASK-PC-FE-353

# Title

다섯 화면이 «테넌트 스위처에서 플랫폼 스코프(*)를 선택하라» 고 안내하고 한 화면은 활성 테넌트 `*` 를 전제하지만 스위처에 `*` 가 없다 — 문구를 고칠지 `'*'` 운영자에게 `*` 를 줄지 (결정 먼저)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- copy

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(문구만일 때) · Opus(`*` 를 스위처에 넣을 때 — 세션 · assume · 화면별 의미가 바뀐다)
> 기안 2026-10-20 UTC — 30차 데모 창(`TASK-MONO-672` § Order «Still open» 3 · 4)의 발견. **결정 티켓이 먼저다** — AC-0 에서 재고 소유자가 고르기 전에는 구현하지 않는다.

# Dependency Markers

- 출처: `TASK-MONO-672` «Still open» 4 의 🔴 Finding · 3 의 🔵(30차, 소유자 브라우저 `star-owner@demo.com`).
- 선행 없음.

# Goal

소유자 전용 `'*'` SUPER_ADMIN(`TASK-MONO-798`)으로 콘솔에 로그인하면, 테넌트를 고르지 않은 상태에서 `/tenants` 가 «테넌트 관리는 SUPER_ADMIN 전용이며 활성 테넌트가 선택되어 있어야 합니다. 상단의 테넌트 스위처에서 플랫폼 스코프(*)를 선택한 뒤 다시 시도하세요.» 라고 말한다. 그런데 **스위처에 `*` 가 없다**(소유자 실측 «* 없어»). 스위처 목록은 `features/tenant/lib/tenant-options.ts` `selectableTenants()` = 레지스트리 제품들의 `tenants` 합집합이고, 그 안에 `*` 는 들어가지 않는다. 소유자는 `demo-corp` 를 골라서 `/tenants` 목록을 봤다 — 화면의 실제 게이트는 «아무 활성 테넌트» 다(`tenants/page.tsx` `state.noTenant`).

같은 «플랫폼 스코프(*)» 전제는 모두 여섯 곳에 있다(안내 다섯 · 전제 하나):

| 파일 | 문구 |
|---|---|
| `app/(console)/tenants/page.tsx:43` | «… 스위처에서 플랫폼 스코프(*)를 선택한 뒤 …» |
| `app/(console)/tenants/[tenantId]/page.tsx:43` | 같음 |
| `app/(console)/org-hierarchy/page.tsx:35` | 같음 |
| `app/(console)/operator-groups/page.tsx:48` | «… 테넌트(SUPER_ADMIN 은 플랫폼 스코프 *)를 …» |
| `app/(console)/domain-permission-sets/page.tsx:66` | 같음 |
| `app/(console)/security-settings/page.tsx:53` | «플랫폼 스코프(*)에는 진입 정책이 없습니다 …» — 활성 테넌트가 `*` 일 수 있다는 전제 |

안내를 따를 수 있게 만든다 — 문구를 사실에 맞추거나, `'*'` 운영자가 `*` 를 고를 수 있게 하거나.

# Scope

## In Scope

- **AC-0 측정 (코드 · 계약 · 이력)**:
  - ① `*` 가 스위처에 들어간 적이 있는가 — `git log -S` 로 `selectableTenants` · 레지스트리 계약(`console-registry-api.md` § Tenant selection rule)의 이력. 예전엔 들어갔다면 언제 · 왜 빠졌나.
  - ② 화면별로 «활성 테넌트 = 실제 테넌트(예 `demo-corp`)» 와 «활성 테넌트 = `*`» 가 **무엇을 다르게 보여 주는가**. 특히 운영자 그룹 · 업무 권한 세트는 활성 테넌트가 **목록의 범위**를 정한다 — `demo-corp` 를 고르면 `demo-corp` 그룹만 보이고, 플랫폼(`*`) 그룹은 볼 길이 없을 수 있다(그렇다면 문구만 고치는 것은 기능 공백을 감춘다).
  - ③ `audience='*'` assume 은 이미 된다(30차: 200, `tenant_id='*'`, 역할 0). 그 토큰을 콘솔 세션에 넣으면 도메인 화면 · 개요 · 알림이 어떻게 되는가(코드 판독 — `getDomainFacingToken` · 개요 카드 · 샘플 게이트).
- 결과로 소유자에게 묻는다(추천과 함께):
  - (가) 문구만 — 화면마다 실제 게이트에 맞는 문장(소유자 문안 승인).
  - (나) `'*'` 운영자(그 grant 를 가진 운영자)에게 스위처에 «플랫폼(*)» 항목을 넣는다 + 화면별 동작 정리.
  - (다) 둘 다 — 지금 (가), (나) 는 별도 기안.

## Out of Scope

- 레지스트리 계약의 테넌트 범위 규칙 자체(IAM 소유) — (나) 가 그것을 바꿔야 하면 IAM 계약 먼저, 별도 티켓.
- `TASK-MONO-672` «Still open» 3 의 API 판정(닫혔다).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①②③ 을 file:line 으로 적는다. 소유자 결정(선택지 · 추천).
- [ ] **AC-1** — 결정한 수선. 문구가 바뀌면 사용자 문안을 먼저 보여 주고 승인받는다.
- [ ] **AC-2** — 시험: 고친 화면마다 «테넌트 없음» 안내가 따를 수 있는 동작을 말한다(문구 시험 또는 스위처 항목 시험) · 기존 비회귀(일반 운영자 스위처 목록 불변).
- [ ] **AC-3** — 배포 상태(Vercel) · 라이브(`star-owner@demo.com` 으로 그 안내를 따라 화면에 들어간다 — 다음 데모 창).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.7 (assume-tenant · 활성 테넌트)
- `projects/iam-platform/specs/contracts/http/console-registry-api.md` § Tenant selection rule (있으면 — AC-0 에서 확인)

# Edge Cases

- 일반 운영자(단일 테넌트)는 스위처가 숨거나 라벨 하나다 — (나) 가 그들에게 `*` 를 보이면 안 된다.
- `*` 를 고른 세션에서 도메인 화면을 열면 도메인 서버가 `tenant_id='*'` 를 어떻게 받는지(ecommerce 는 GET 만 와일드카드 허용 — `TASK-MONO-795`).

# Failure Scenarios

1. 문구만 «아무 테넌트나 고르라» 로 바꾸고 닫는다 — 그룹 · 세트 화면에서 플랫폼(`*`) 범위를 볼 길이 없다는 기능 공백이 그대로 묻힌다(AC-0 ② 가 막는다).
2. 스위처에 `*` 를 모든 운영자에게 넣는다 — 서버가 거절하는 선택지를 보여 준다(`*` grant 없는 운영자).
3. 여섯 곳 중 일부만 고친다 — 같은 문장이 화면마다 다른 사실을 말하게 된다.
