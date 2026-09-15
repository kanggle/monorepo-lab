# Task ID

TASK-PC-FE-283

# Title

IAM 화면이 샘플로 선다 — 계정·감사·운영자·그룹·조직·파트너십·권한·구독·테넌트 (`ADR-MONO-074` 실행 2/8)

# Status

ready

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 가 깐 샘플 모드 위에서 **IAM 도메인의 GET 을 전부 `ready`** 로 만든다. 익명 방문자가 아래 화면을 열면
«준비 중» 이 아니라 실제 화면이 합성 값으로 선다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 도메인 티켓 여섯(283~288)은 샘플 라우터 등록부와 원장 파일을 **공유**한다 ⇒
병렬 worktree 금지, **직렬 머지**.

화면: `/account` · `/accounts` · `/audit` · `/iam` · `/iam/guide`(정적) · `/operator-groups` · `/operators` · `/org-hierarchy` ·
`/partnerships` · `/permission-sets` · `/permissions` · `/subscriptions` · `/tenants` · `/tenants/[tenantId]`

코어: `callAdminGateway` (`shared/api/iam-gateway.ts`). ADR 인벤토리: GET **18** · 쓰기 **38**(코드 읽기 수).

---

# Scope

## In Scope

- 위 화면이 부르는 IAM GET 픽스처 전부 + 원장 `pending → ready`
- 목록 ↔ 상세 id 일관, 화면에 노출된 필터·페이지 동작

## Out of Scope

- 샘플 모드 기반(판정·코어 분기·셸·가드) — `TASK-PC-FE-282`
- 쓰기 동작 — 전부 `SAMPLE_READ_ONLY`(R1ⓐ, 282 가 이미 처리)

---

# Acceptance Criteria

- [ ] **AC-0** `TASK-PC-FE-282` AC-0 표와 원장에서 IAM `pending` GET 목록을 뽑아 이 파일에 적는다(18 과 다르면 그 수가 범위).
- [ ] **AC-1** 그 GET 전부 `ready`. 픽스처마다 **실제 파서**(zod 스키마/parse 함수)를 통과하는 테스트.
- [ ] **AC-2** «(샘플)» 표기(R2ⓐ) — 282 AC-7 의 규칙 테스트가 이 픽스처도 순회하고 초록.
- [ ] **AC-3** 목록에 나오는 id 로 상세(`/tenants/[tenantId]` 등)가 **찾아진다**. 없는 id 는 실제와 같은 404 모양.
- [ ] **AC-4** 화면에 노출된 필터·검색·페이지는 **픽스처 위에서 적용**된다(무시하면 «안 좁혀짐» 이 고장으로 보인다). 페이지 메타의 총계는 실제 행 수와 같다.
- [ ] **AC-5** 대표 쓰기 1개(예: 계정 잠금)가 «샘플 화면에서는 실행되지 않습니다» 를 보인다.
- [ ] **AC-6** `e2e-smoke` 에 익명 `/accounts` 렌더 1칸(배너 + 표 + «(샘플)» 문자열).
- [ ] **AC-7** 🔴 감사 로그·운영자 이메일 등 **사람을 식별하는 값**은 명백한 합성(`*.example` 도메인, 실재하지 않는 이름)이다.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `projects/platform-console/tasks/ready/TASK-PC-FE-282-anonymous-visitors-enter-the-real-console-and-the-gateways-answer-with-samples.md`
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4 (IAM admin) — 픽스처는 이 응답 모양을 따른다

# Edge Cases

- `/tenants` 는 실제로는 플랫폼 스코프가 있어야 열린다 — 샘플에서는 282 AC-12(샘플 운영자 = 전 화면 열림)를 따른다.
- 빈 목록 화면 하나는 empty-state 가 보이도록 픽스처에 0행 케이스를 둘지 결정해 적는다.

# Failure Scenarios

- 픽스처가 스키마를 어김 → 섹션 degrade 로 보여 «고장» 과 구별 안 됨 ⇒ AC-1 파서 테스트.
- 원장 파일 동시 수정 → 직렬 머지 규칙 위반, 충돌.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [ ] 원장의 IAM `pending` 0
- [ ] 로그인 운영자 경로 테스트 무수정 초록

분석=Opus 5 / 구현 권장=Sonnet 5.
