# Task ID

TASK-PC-FE-044

# Title

Fix **운영자 관리 / 감사·보안 목록이 테넌트 전환에 반영되지 않는 stale-cache 버그**. `useTenantSwitch` 가 전환 성공 시 invalidate 하는 React Query 키 집합에 `['operators']` 와 `['audit']` 를 추가한다(현재 `['catalog']` + `['session']` 만 invalidate — tenant-scoped 인 두 목록 쿼리가 누락). 이로써 테넌트를 바꾸면 mounted 된 operators/audit 쿼리가 새 활성-테넌트 쿠키로 재요청되어 목록이 갱신된다.

# Status

done

# Owner

frontend-engineer (console-web only — project-internal)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **follows / fixes**: TASK-MONO-175 (operators 목록을 활성 테넌트로 스코핑) + TASK-PC-FE-043 (audit 목록을 활성 테넌트로 스코핑) — 두 task 가 producer/consumer 의 **서버측** 스코핑은 완성했으나, **클라이언트 React Query 캐시**가 테넌트 차원을 반영하지 않아 전환 후 목록이 갱신되지 않는 잔여 결함.
- **mechanism**: TASK-PC-FE-040 (`useTenantSwitch` 가 전환 성공 시 `router.refresh()` + tenant-scoped 클라이언트 쿼리 invalidate). 이 task 는 PC-FE-040 이 확립한 invalidation 메커니즘을 operators/audit 까지 확장한다(설계 의도 = "client-side tenant-scoped queries on the page" 를 invalidate; 두 키가 단순 누락됨).
- **root cause**: `features/tenant/hooks/use-tenant-switch.ts` 의 `onSuccess` 가 `['catalog']` + `['session']` 만 invalidate. operators 목록 키(`['operators', status, page, size]`)는 테넌트 슬롯이 없고, audit 목록 키는 테넌트 슬롯이 있으나 `AuditScreen` 이 초기 쿼리에 활성 테넌트를 채우지 않아 항상 `null` → 두 캐시 모두 테넌트 간 충돌. seeded 쿼리(`initialData` + `staleTime: 30s` + `refetchOnMount: false`)라 `router.refresh()` 가 서버에서 새 테넌트 데이터를 `initial` 로 넘겨도 React Query 는 동일 키의 캐시를 유지하고 `initialData` 를 무시 → 직전 테넌트 목록이 그대로 표시.

# Goal

multi-assignment 운영자(예: `multi-operator`, home=acme-corp, assigned acme+globex)가 테넌트를 acme ↔ globex 로 전환하면, **운영자 관리**와 **감사·보안** 목록이 즉시 새 테넌트 범위로 다시 그려진다(데모: 운영자 acme=2 / globex=1). 전환 직후 직전 테넌트의 목록이 남아 있지 않는다.

# Scope

## In Scope

- `features/tenant/hooks/use-tenant-switch.ts` — `onSuccess` 의 invalidation 목록에 `qc.invalidateQueries({ queryKey: ['operators'] })` + `qc.invalidateQueries({ queryKey: ['audit'] })` 추가. JSDoc 의 invalidation 설명을 두 키 포함하도록 갱신.
- 단위 테스트: `tests/unit/TenantSwitcher.test.tsx` — 전환 성공 시 `QueryClient.invalidateQueries` 가 `['operators']` + `['audit']`(+ 기존 `['catalog']` + `['session']`)로 호출됨을 단언.

## Out of Scope

- operators/audit 쿼리 키에 테넌트를 인라인으로 넣는 더 큰 리팩터 — 본 task 는 PC-FE-040 이 확립한 invalidate-on-switch 메커니즘을 완성하는 최소 수정. (키-차원 리팩터는 별도 필요 시 follow-up.)
- 서버측 스코핑(MONO-175 / PC-FE-043) — 이미 완료, 변경 없음.
- 카탈로그/세션 invalidation — 기존 동작 유지.

# Acceptance Criteria

- [x] **AC-1** 테넌트 전환 성공 시 `useTenantSwitch` 가 `['operators']` 와 `['audit']` 를 invalidate 한다(단위 테스트).
- [x] **AC-2** 기존 invalidation(`['catalog']`, `['session']`) + `router.refresh()` 동작은 보존(회귀 없음; 기존 테스트 GREEN).
- [x] **AC-3** 데모 라이브: `multi-operator` 로그인 → acme(운영자 2명) ↔ globex(운영자 1명) 전환 시 목록이 즉시 재스코핑. 감사·보안도 테넌트별 이벤트로 갱신. [live — console-web 재빌드+재기동, 번들 invalidation 키 반영 확인]
- [x] **AC-4** console `pnpm test` GREEN(791/791), `tsc --noEmit` clean / `lint` clean / `build` GREEN.

# Related Specs

- `console-integration-contract.md` § 2.7 (active-tenant switch) / § 2.4.2 (audit) / § 2.4.3 (operators). ADR-MONO-020 (active-tenant scoping).

# Edge Cases

- 전환 실패(403/422/503) → `onSuccess` 미실행 → invalidation 없음(직전 테넌트 목록 유지, 올바름).
- operators/audit 쿼리가 현재 페이지에 mount 되어 있지 않으면 invalidate 는 다음 mount 시 fresh fetch 를 유발(active observer 없으면 즉시 refetch 없음 — 해당 페이지로 이동 시 서버 컴포넌트가 새 `initial` 제공 + 쿼리 stale 표시).

# Failure Scenarios

- invalidation 키 오타 → 목록이 갱신되지 않음(단위 테스트가 정확한 키를 단언하여 방지).

# Test Requirements

- `tests/unit/TenantSwitcher.test.tsx`: 전환 성공 시 `invalidateQueries` 가 operators + audit 키로 호출됨을 spy 로 단언.
- `pnpm test` + `tsc --noEmit` + `lint` + `build`.
- Local: console-web 재빌드+재기동; `multi-operator` 로 acme↔globex 전환 → operators 2↔1, audit 테넌트별 갱신 확인.

# Definition of Done

- [x] `use-tenant-switch.ts` 수정 + JSDoc 갱신.
- [x] 단위 테스트 추가; `pnpm test`(791/791)/`tsc`/`lint`/`build` GREEN.
- [x] Local console-web 재빌드+재기동; acme↔globex 전환 시 목록 재스코핑 확인.
- [x] Task md + `projects/platform-console/tasks/INDEX.md` 갱신.
- [x] Reviewed + merged (impl PR #1071 squash `fd6626a9`, 3-dim verified; 전 CI GREEN, transient 없음).

---

분석=Opus 4.8 / 구현=Opus(직접). MONO-175 + PC-FE-043 의 잔여 클라이언트-캐시 결함 수정. 메타: 서버측 테넌트 스코핑을 추가할 때 **클라이언트 React Query 캐시의 테넌트 차원**(invalidate-on-switch 목록 또는 쿼리 키)도 함께 갱신해야 함 — seeded 쿼리는 `router.refresh` 의 새 `initial` 을 동일 키에서 무시하므로 invalidation 이 필수. 라이브에서 "두 테넌트 목록이 동일"로 표면화(2026-06-04).
