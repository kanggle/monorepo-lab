# Task ID

TASK-MONO-756

# Title

`ADR-MONO-081` 단계 4 — 페더레이션 e2e 세 스펙을 **console-web 합성 기준**으로 다시 쓴다

# Status

ready

# Owner

monorepo

# Task Tags

- e2e
- ci
- platform-console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 단, `observability-trace-tree` 는 트레이스 뿌리가 바뀌므로 무엇을 단언할지부터 정한다.

---

# Dependency Markers

- **선행**: `TASK-PC-FE-302` · `TASK-PC-FE-303` 머지.
- **후속**: `TASK-MONO-757`(삭제) — 🔴 이 티켓이 **먼저** 머지돼야 한다(`ADR-MONO-081` D4: 지우고 나서 고치면 그 사이 nightly 가 빨갛다).

# Goal

`tests/federation-hardening-e2e` 의 console-bff 전제 스펙 셋을 console-web 합성 기준으로 바꿔, console-bff 를 지워도 같은 성질(교차 도메인 합성 · 도메인 상태 · 트레이스 연결)을 계속 재게 한다.

# Scope

## In Scope

- `specs/operator-overview-composition.spec.ts` · `specs/domain-health-composition.spec.ts` · `specs/observability-trace-tree.spec.ts`(console-bff 언급 40곳)
- 같은 하네스의 console-bff 를 언급하는 나머지 두 스펙(`tenant-switch-rescope` · `entitlement-trust-crossdomain`)의 해당 줄
- `docker/docker-compose.federation-e2e.yml` 은 **건드리지 않는다**(console-bff 서비스 제거는 757) — 스펙이 더는 그것을 부르지 않게만 한다

## Out of Scope

- console-bff 서비스·잡 삭제(757)

# Acceptance Criteria

- [ ] **AC-1** — 세 스펙이 console-bff 엔드포인트를 직접 부르지 않는다(`grep -n "console-bff\|:8080/api/console" tests/federation-hardening-e2e/specs` 의 남은 줄은 주석뿐이고 그 이유가 적혀 있다).
- [ ] **AC-2** — 합성 스펙이 `ADR-MONO-081` § Verification 의 대조군 1(레그 하나 죽음 → 그 카드만 열화)을 실제 스택에서 잰다.
- [ ] **AC-3** — 🔴 트레이스 스펙: 뿌리가 console-web 서버 span 이 되고, 그 아래 도메인 게이트웨이 span 들이 **같은 trace id** 로 이어지는지를 단언한다. console-web 이 trace 헤더를 전파하지 않으면 그것이 이 티켓의 발견이다 — 전파를 고치거나(작으면) 별도 티켓으로 떼고 그 사실을 적는다. 단언을 지워 초록을 만들지 않는다.
- [ ] **AC-4** — 이 PR 의 `federation-hardening-e2e.yml` 런과, console-bff 가 아직 떠 있는 상태에서 스펙이 그것을 안 부른다는 사실(접근 로그 0 또는 서비스 미기동 상태로 한 번 더 런)을 적는다.

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md` D4
- `tests/federation-hardening-e2e/README.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9

# Edge Cases

- 하네스가 단일 compose 프로젝트라 console-web 이 도메인 서비스에 닿는 주소가 데모와 다르다(`infra/demo/README.md` «fed-e2e 값은 그대로 옮길 수 없다»).

# Failure Scenarios

1. 트레이스 단언을 «span 이 있다» 로 약화해, 연결이 끊겨도 초록이다.
2. 스펙만 바꾸고 console-bff 가 여전히 응답하는 상태에서만 돌려, 스펙이 실제로 console-bff 없이 서는지 모른다(AC-4).
