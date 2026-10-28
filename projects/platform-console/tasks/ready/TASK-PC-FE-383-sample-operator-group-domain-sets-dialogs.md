# Task ID

TASK-PC-FE-383

# Title

익명 샘플의 운영자 «도메인 접근» 대화상자 · 그룹 상세 «세트» 칸이 «준비 중» — `ready` 표면(`operators` · `groups`) 안에서 픽스처가 빠진 하위 GET 둘

# Status

ready

# Owner

frontend

# Task Tags

- code
- console
- sample

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (`TASK-PC-FE-382` 와 같은 모양 — iam 픽스처 하위 경로 둘 + 스키마 시험)
> Drafted 2026-10-28 UTC. 출처: `TASK-PC-FE-382` 구현 보고의 범위 밖 항목 → 메인 세션 코드 확인 → 소유자 결정 «둘 다 채운다».

# Dependency Markers

- 선행: `TASK-PC-FE-382`(done) — `domain_permission_sets` 표면 픽스처(프리셋 `setId` · 샘플 테넌트 세트 `0192f3a0-…d001`/`d002`)를 이미 넣었다. 이 티켓의 배정 행은 **그 id 를 참조**해야 한다.
- 관련: `ADR-MONO-074`(A4 합성 · A9 샘플 없는 GET 은 섹션 degrade · R1 쓰기), `ADR-MONO-083`(업무 권한 세트 배정).

# Goal

로그인하지 않은 방문자가 `/operators` 에서 운영자 행의 «도메인 접근» 을 열거나 `/operator-groups` 의 그룹 상세를 열 때, «준비 중» 대신 그 운영자 · 그룹의 업무 권한 세트 배정(합성 샘플)을 본다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 실측 — 2026-10-28 UTC; 🔴 착수 시 file:line 재확인):**
  - 운영자 대화상자: `features/operators/components/OperatorRow.tsx:140`(`action-domain-access-<operatorId>`) → `OperatorDomainAccessDialog` → `features/operators/api/operators-domain-access-api.ts:39` `GET /api/admin/operators/{id}/domain-access`(쓰기 `:66` POST · `:86` DELETE).
  - 그룹 칸: `features/operator-groups/components/GroupDetail.tsx` → `features/operator-groups/api/group-domain-sets-api.ts:31` `GET /api/admin/groups/{id}/domain-permission-sets`(쓰기 `:46` POST · `:63` DELETE).
  - 샘플 핸들러: `shared/sample/fixtures/iam.ts` `operatorsFixture`(`:525` — `grantable-roles` `:542` · `lookup` `:548` · `{id}/assignments` `:552` · 목록 `:563` 만 처리, 그 밖은 `undefined`) · `groupsFixture`(`:838` — `members` `:841` · `grants` `:850` · 상세 `:859` · 목록 `:867` 만). ⇒ 두 GET 은 `undefined` → 503 `SAMPLE_NOT_READY` → 대화상자 · 칸이 degrade.
  - 🔵 원장(`coverage.ts`)은 **표면 단위**라 `operators` · `groups` 가 `ready` 인 채로 하위 경로 빈칸을 못 본다(`sample-coverage-ledger.test.ts` 가 초록인 이유).
  - 샘플 id: 운영자 `op-sample-0001` …(`IAM_OPERATORS`, `:404`~) · 그룹 `group-sample-0001` …(`IAM_GROUPS`, `:790`~).
  - 생산자 계약(모양의 정본): `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/operators/{operatorId}/domain-access`(`:4154`) · § `GET /api/admin/groups/{groupId}/domain-permission-sets`(`:4277`) · 출처 규칙 § 출처(`source_key`) 와 회수 규칙.
- **구현:** 두 GET 의 응답을 생산자 계약 모양 그대로 합성 — 운영자마다 배정 세트(출처 `DIRECT` · `MIGRATION` · `GROUP:<id>` 를 계약이 허용하는 만큼 섞어 화면의 출처 표기가 보이게), 그룹마다 배정 세트. 참조하는 `setId` 는 `TASK-PC-FE-382` 의 세트 목록에 **있는 것만**. 그룹 출처 행은 그 그룹의 멤버 운영자에게만. 없는 운영자 · 그룹 id 는 생산자 계약이 적은 응답 그대로(404 인지 · 빈 목록인지 **계약 원문을 확인**). 쓰기는 ADR-074 R1 기존 처리 그대로.
- `/api/admin/me/domain-access`(`:4341`)를 콘솔이 샘플 경로에서 부르는지 AC-0 에서 확인 — 부르는데 빈칸이면 이 티켓에 포함, 안 부르면 기록만.

## Out of Scope

- 원장 단위를 «하위 경로» 로 바꾸는 가드(소유자가 고르지 않은 갈래 — 범위가 커질 수 있음).
- 백엔드 · 계약 변경, 로그인 화면 동작.

# Acceptance Criteria

- [ ] **AC-0** 위 사실 재확인 + 두 화면이 부르는 GET 전수 표(어긋나면 정정 기록).
- [ ] **AC-1 (수정 전 빨강)** 두 GET 의 샘플 응답을 콘솔 zod 스키마(`features/operators/api/domain-access-types.ts` · `features/operator-groups/api/domain-sets-types.ts`)로 파싱하는 시험 — 수정 전 코드에서 503/`undefined` 로 빨강임을 확인해 구현 보고에 적는다.
- [ ] **AC-2** 배정 행의 모든 `setId` 가 `TASK-PC-FE-382` 의 세트 목록 픽스처에 있다는 단언 · 그룹 출처 행의 운영자는 그 그룹 멤버라는 단언. 픽스처 필드명 ↔ 생산자 계약 필드명 대조표를 구현 보고에 둔다. 없는 id 응답이 **계약 원문과 같다**(원문 인용).
- [ ] **AC-3** 쿠키 없는 렌더(단위 또는 로컬 `next dev` + Playwright)에서 운영자 «도메인 접근» 대화상자와 그룹 상세 세트 칸에 «준비 중» 이 없고 배정 행이 보인다. 대조군: 아직 `pending` 인 화면(`/fan`)에서 같은 술어가 «준비 중» 을 잡는다.
- [ ] **AC-4** console-web `lint` · `tsc` · vitest(`--maxWorkers=2 --minWorkers=1`) 전체 초록 · 샘플 격리 · 라벨 가드 초록.
- [ ] **AC-5 (라이브, 배포 뒤)** Vercel Production 배포 뒤 로그아웃 상태로 `console.hubwang.com/operators` 의 «도메인 접근» 과 그룹 상세를 열어 «준비 중» 이 없음을 확인(대조군 `/fan` 함께).

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` — 위 절들. **읽기만, 변경 없음.**

# Edge Cases

- `'*'` 운영자(플랫폼 운영자 샘플이 있다면)의 배정은 `tenantId='*'` 행 — 계약의 `'*'` 규칙을 따른다.
- 같은 세트가 `DIRECT` 와 `GROUP:<id>` 두 출처로 동시에 있을 수 있다(계약상 PK 가 출처를 포함) — 화면이 둘을 따로 그리면 둘 다 넣어 보여 준다.
- 샘플 그룹 멤버 목록(`members`)과 그룹 출처 행이 어긋나면 화면이 «멤버가 아닌데 그룹 출처» 를 그린다 → AC-2 단언.

# Failure Scenarios

1. 세트 목록에 없는 `setId` 를 참조 — 대화상자에 «알 수 없는 세트».
2. 없는 id 응답을 지어낸다 — `TASK-PC-FE-382` 의 진입 정책 404 사고(티켓 엣지 케이스가 계약과 반대였다)와 같은 클래스. 계약 원문을 인용해 고정한다.
3. 출처 표기를 하나만 넣는다 — 화면의 출처 구분이 샘플에서 한 번도 안 보인다.
