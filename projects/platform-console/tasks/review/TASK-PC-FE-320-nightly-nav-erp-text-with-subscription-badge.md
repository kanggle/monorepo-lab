# Task ID

TASK-PC-FE-320

# Title

nightly 콘솔 e2e 빨강 수정 — `overview-consolidation.spec.ts:82` 가 `nav-erp` 텍스트를 정확히 «ERP» 로 단언해 `TASK-PC-FE-314` 의 «구독 필요» 배지에 걸린다

# Status

review

# Owner

platform-console

# Task Tags

- console-web
- e2e
- fix

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku · Sonnet — 단언 한 줄.

---

# Dependency Markers

- 출처(fix-of): `TASK-PC-FE-314`(review) — 그 티켓 AC-6 «머지 뒤 첫 nightly 콘솔 잡 확인» 에서 발견.

# Goal

`nightly-e2e.yml` «Platform Console E2E full-stack» 이 `8706986b8`(#4218) 까지 초록, `779195d40`(#4219 = `TASK-PC-FE-314`) 부터 매 실행 빨강이었다(2026-10-07 UTC, 실행 8 회 연속). 실패는 처음부터 끝까지 **같은 1 건**:

```
tests/e2e/overview-consolidation.spec.ts:82
expect(getByTestId('nav-erp')).toHaveText('ERP')
Expected: "ERP"   Received: "ERP구독 필요"
```

`TASK-PC-FE-314` 는 활성 테넌트가 레지스트리에서 그 도메인에 자격이 없으면 드릴 토글 **안에** «구독 필요» 배지를 붙인다(숨기지 않는다 — 설계). 레지스트리 `tenants` 는 자격 표가 맞다(`TASK-MONO-719` 결론). 그러니 배지 판정이 아니라 **단언이 너무 좁다** — 이 시험이 재려는 것은 «ERP 가 드릴 토글이고 마스터 자식으로 간다» 이지 그 스택 테넌트의 구독 여부가 아니다.

🔴 놓친 경위: `TASK-PC-FE-314` 머지 전 e2e grep 은 «숨겨질 메뉴를 찾는 스펙» 만 셌고, 메뉴 **텍스트를 정확히 단언하는** 스펙은 술어에 없었다.

# Scope

## In Scope

- 단언을 `toHaveText(/^ERP(구독 필요)?$/)` 로 — 라벨은 정확히 고정하고 배지는 있든 없든 허용. `toContainText` 는 쓰지 않는다(다른 글자로 시작하는 라벨도 통과시킨다).

## Out of Scope

- 배지 판정 · 위치 변경, e2e 스택 테넌트의 ERP 자격 픽스처 변경.

# Acceptance Criteria

- [x] **AC-0** — 같은 모양의 단언(내비 testid 에 정확한 텍스트) 전수: 콘솔 e2e 디렉터리(`tests/e2e/**` · `e2e-smoke/**`) · 루트 `tests/federation-hardening-e2e/**` 에서 `toHaveText('ERP'|'WMS'|'SCM'|'재무'|'이커머스')` 및 `nav-…` + `toHaveText` — **이 한 줄뿐**.
- [x] **AC-1** — 단언 수정(위 Scope).
- [ ] **AC-2** — 머지 뒤 첫 nightly «Platform Console E2E» 초록 확인(⚪ 머지 뒤).

# Related Specs

- `projects/platform-console/tasks/review/TASK-PC-FE-314-sidebar-by-role-and-subscription.md`
- `tasks/done/TASK-MONO-719-the-tenant-mismatch-gate-asks-entitlement-and-calls-it-data-ownership.md`

# Related Contracts

- 없음(시험만).

# Edge Cases

- e2e 스택 테넌트가 나중에 ERP 자격을 얻으면 배지가 사라진다 — 정규식이 두 경우를 모두 받는다.

# Failure Scenarios

1. `toContainText('ERP')` 로 느슨하게 고친다 — 라벨이 «ERP 관리» 같은 다른 글자로 바뀌어도 통과한다.
2. 배지를 버튼 밖으로 옮겨 시험을 맞춘다 — 시험 때문에 화면을 바꾸는 것이고, 배지가 어느 메뉴의 것인지 흐려진다.
