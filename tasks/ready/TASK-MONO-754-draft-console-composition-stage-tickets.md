# Task ID

TASK-MONO-754

# Title

⏳ 콘솔 합성을 콘솔 서버로 — `ADR-MONO-081` 갈래대로 단계 티켓을 기안한다

# Status

ready

# Owner

monorepo

# Task Tags

- architecture
- frontend
- planning

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (합성·실패 격리·자격 선택 이전) · 삭제·정리 단계는 Sonnet
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** AC-0 은 verify-then-act 게이트다.

---

# Dependency Markers

- **선행 (prerequisite)**: `ADR-MONO-081` ACCEPTED(정확형 `ADR-MONO-081 ACCEPTED — <A|B|C|D>`).
- **관련(같은 표면)**: `TASK-MONO-751`(콘솔 팬 화면, `ADR-MONO-079` D4-A) — 그 티켓의 «BFF 라우트» 는 console-web 의 같은 출처 라우트를 뜻한다(`ADR-MONO-081` D3). 751 의 Related Contracts 줄 «console-bff ↔ fan gateway» 를 고치는 일이 단계 1 의 AC 다. 두 티켓 사이에 코드 순서 의존은 없다 — 751 이 console-bff 를 **새로 쓰지만 않으면** 된다.

# Goal

`ADR-MONO-081` 이 고른 갈래(D5)로 콘솔의 교차 도메인 합성 세 가지(운영 개요 · 도메인 상태 · 알림 인박스)를 옮기기 위한 **단계 티켓을 기안**한다. 이 티켓 자신은 코드를 쓰지 않는다 —
합성 이전·e2e 재작성·서비스 삭제는 각자 다른 실패 모양을 가지므로 한 PR 에 담으면 대조군이 무엇을 재는지 흐려진다.

# Scope

## In Scope

- 갈래별 단계 분해와 각 단계 티켓 파일(`projects/platform-console/tasks/ready/` 또는 루트 `tasks/ready/` — CI·compose·데모 AMI·`libs/` 를 건드리는 단계는 루트)
- 단계 순서: 계약·스펙이 먼저, 삭제가 마지막(`ADR-MONO-081` § 단계 표 · R3)

## Out of Scope

- 구현 코드
- 도메인 화면 6종의 호출 경로(이미 console-web 직접)
- 자격 모델 변경(`ADR-MONO-017` D4 · `ADR-MONO-020`)

# Acceptance Criteria

- [ ] **AC-0 (게이트)** — `docs/adr/ADR-MONO-081-*.md` Status 가 `ACCEPTED` 이고 갈래 letter 가 적혀 있다. 아니면 착수하지 않는다. 갈래가 **D** 면 단계 티켓 없이 이 티켓을 닫는다(사유 기록).
- [ ] **AC-1** — 단계 티켓이 `ready/` 에 있고, 각 티켓이 Goal/Scope/AC/Related Specs/Related Contracts/Edge Cases/Failure Scenarios 를 갖는다.
- [ ] **AC-2** — 🔴 첫 단계는 **계약·스펙 갱신**이다(구현 전): `console-integration-contract.md` § 2.4.9 의 생산자 · `notification-inbox-contract.md` 의 집계기 자리 · `PROJECT.md` Service Map 과 `service_types`.
- [ ] **AC-3** — 🔴 대조군 둘이 **한 단계의 AC 로** 들어가 있다: (1) 레그 하나를 죽이면 그 카드만 열화하고 나머지는 실제 값 · (2) 레그 하나가 401 이면 응답 401(열화 카드 200 이 아니다). (`ADR-MONO-081` § Verification)
- [ ] **AC-4** — 페더레이션 e2e 세 스펙(`operator-overview-composition` · `domain-health-composition` · `observability-trace-tree`) 재작성이 **삭제 단계보다 앞선** 단계에 있다(`ADR-MONO-081` D4).
- [ ] **AC-5** — 라이더(R1 지표 → 구조화 로그 · R2 회로 차단기 없음 · R3 삭제는 이전 머지·nightly 초록 뒤)가 소유자 공급 여부와 함께 단계 티켓 AC 로 옮겨져 있다.
- [ ] **AC-6** — 삭제 단계 AC 에 `ADR-MONO-081` D4 의 지우는 목록 전부와, `bff_*` 지표 소비자 grep(0 이어야 한다)이 있다.
- [ ] **AC-7** — `TASK-MONO-751` 의 Related Contracts 줄 정정이 단계 1 의 AC 로 들어가 있다(D3).

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` D1·D2·D4·D5·D7
- `docs/adr/ADR-MONO-043-notification-architecture-unification.md` D2·D5
- `projects/platform-console/PROJECT.md`
- `projects/platform-console/specs/services/console-web/architecture.md` §§ 2.4.9 · 303–309
- `projects/platform-console/specs/services/console-bff/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9
- `platform/contracts/notification-inbox-contract.md`
- `platform/contracts/jwt-standard-claims.md`(console-bff 대상 언급)

# Edge Cases

- 활성 테넌트 없는 운영자 — 어떤 레그도 부르지 않고 400 `NO_ACTIVE_TENANT`(호출 수 0 단언).
- 샘플 방문자(`ADR-MONO-074`) — 여전히 레그를 부르지 않는다. 샘플 원장의 `core: 'console-bff'` 이름을 바꾸면 원장 가드가 문다(`ADR-MONO-081` D1).
- finance 기본 계정 id 가 없는 운영자 — 지금처럼 finance 카드만 `MISSING_PREREQUISITE`.

# Failure Scenarios

1. 이전과 삭제를 한 PR 로 합쳐, 삭제 뒤 nightly 가 빨개졌을 때 원인이 «합성 이전» 인지 «정리 누락» 인지 가를 수 없다.
2. console-bff 를 지운 뒤 페더레이션 e2e 를 고쳐, 그 사이 nightly 가 빨간 채 다른 머지가 쌓인다.
3. 레그 401 을 열화 카드로 삼켜, 만료 세션이 «일부 도메인 장애» 로 보인다.
