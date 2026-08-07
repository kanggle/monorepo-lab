# Task ID

TASK-MONO-516

# Title

Federation Hardening E2E nightly 가 **2026-07-31 이후 매일** 빨갛다 — `scm-inbound-expected-loop` 스펙에서 wms 가 ASN 을 만들지 않는다 (간헐 아님, **결정론적**)

# Status

ready

# Task Tags

- bug
- ci
- integration

---

# 배경 — `TASK-ERP-BE-041` 작업 중 main 확인하다 발견

`main` 의 **Federation Hardening E2E (Phase 8 cross-product, nightly)** 워크플로 이력:

| 결과 | commit | 날짜 |
|---|---|---|
| ❌ | `078d230c6` | 2026-08-07 |
| ❌ | `eae894bf7` | 2026-08-05 |
| ❌ | `6d65c43ad` | 2026-08-04 |
| ❌ | `672426d33` | 2026-08-03 |
| ❌ | `672426d33` | 2026-08-02 |
| ❌ | `672426d33` | 2026-08-01 |
| ❌ | `e00236923` | 2026-07-31 |
| ✅ | `7e48b1152` | 2026-07-30 ← **마지막 초록** |

🔴 **7일 연속, 서로 다른 커밋에서, 같은 실패.** 이것은 flake 가 아니라 **결정론적 회귀**다.
그리고 **아무도 티켓을 갖고 있지 않다**(`TASK-MONO-328` 이 파일명만 스쳐 언급하지만 그 티켓은
CI `if:` 리팩터로 무관).

## 실패 지문

```
[chromium] › specs/scm-inbound-expected-loop.spec.ts:91:7 › scm inbound-expected …
Error: wms inbound-service creates an Asn(CREATED, SCM_PROCUREMENT)
       — poNumber=SCM-PO-FED-8E451
1 failed, 20 passed
```

`Build`·`compose up`·서비스 기동은 모두 통과했고 **스펙 하나만** 죽는다. 즉 인프라 실패가
아니라 **동작 실패**다.

## 🔴 대조군이 없는 관찰 — 원인으로 쓰지 말 것

같은 로그에 `SQL Error: 1406, SQLState: 22001`("Data too long for column")이 보인다. **그러나
그것은 `auth-service` 가 낸 것**이고 inbound 경로가 아니다. 착수 시 이것을 원인으로 채택하지
마라 — 나는 그 연결을 세우려다 서비스 이름을 확인하고 **철회했다.** 초록이던 `7e48b1152`
실행에도 같은 1406 이 있는지부터 보라(있으면 잡음이다).

## 🔵 `TASK-SCM-BE-058` 과 **증상 계열이 같다** — 그러나 공통 원인은 미확인

`TASK-SCM-BE-058`(scm e2e `InboundExpectedLoopE2ETest` — "기다리던 inbound-expected 이벤트가
안 왔다")과 이 티켓(federation — "wms 가 ASN 을 안 만든다")은 **같은 scm → wms inbound-expected
경로**다. 한쪽이 다른 쪽의 결과일 가능성이 높다.

🔴 **그러나 그것은 아직 추론이다.** 결정적 차이가 하나 있다:

| | `SCM-BE-058` | 이 티켓 |
|---|---|---|
| 성격 | **간헐적**(같은 날 초록 2회) | **결정론적**(7일 연속) |

간헐과 결정론이 같은 원인일 수는 있지만(자원 압박이 간헐을 만들고 스키마/계약 결함이
결정론을 만드는 식으로 **둘일 수도** 있다), **같다고 가정하고 하나만 고치면 나머지가 남는다.**
AC-2 가 이것을 가르라고 요구한다.

# Goal

Federation Hardening nightly 가 `main` 에서 초록이다. 그리고 **7일간 빨갰던 이유**가 기록된다.

# Scope

## In Scope

- `tests/federation/specs/scm-inbound-expected-loop.spec.ts` 와 그것이 검증하는 경로
- wms `inbound-service` 의 ASN 생성 (scm `inbound-expected` 소비)
- 필요 시 `federation-hardening-e2e.yml`

## Out of Scope

- scm 단독 e2e 의 간헐 실패 → **`TASK-SCM-BE-058`**(공통 원인으로 밝혀지면 그때 병합)
- GitHub Actions 장애로 인한 `Set up job` 실패

# Acceptance Criteria

- [ ] **AC-0 (모집단 재확인)** — 착수 시 이력을 **다시** 센다. 위 표를 그대로 믿지 말 것.
      그리고 실패를 **실패 단계로 갈라서** 센다(`Set up job` = 인프라, 스펙 실행 = 이 결함)
- [ ] **AC-1 (마지막 초록 → 첫 빨강 사이의 diff)** — `7e48b1152`(초록) → `e00236923`(첫 빨강)
      사이에 무엇이 들어갔는지 본다. 🔴 **7일 연속 결정론적 실패는 원인 커밋이 있다** —
      간헐 가설로 시간을 쓰지 마라
- [ ] **AC-2 (SCM-BE-058 과의 관계 확정)** — 공통 원인인가 별개인가를 **수치로** 가른다.
      같다면 한 티켓으로 병합하고, 다르다면 그 사실을 양쪽 티켓에 적는다.
      🔴 "비슷해 보인다" 로 병합하지 마라
- [ ] **AC-3 (1406 배제/채택)** — 초록 실행 로그에 같은 `SQL Error: 1406` 이 있는지 확인해
      잡음인지 신호인지 판정하고 적는다
- [ ] **AC-4 (수정 + 연속 초록)** — 수정 후 nightly **연속 2회** 초록. 결정론적 결함이므로
      1회로도 강한 증거지만, 2회가 인프라 잡음과 갈라 준다

# Related Specs

- `projects/wms-platform/specs/services/inbound-service/architecture.md`
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- ADR-MONO-050 (scm → wms inbound-expected 컨슈머)

# Edge Cases

- federation 스택은 여러 프로젝트를 한 compose 로 띄운다 — 한 서비스의 스키마/계약 변경이
  **다른 프로젝트의 nightly** 만 깨뜨릴 수 있다(그래서 PR-time CI 는 초록이었다)
- 🔴 `nightly-e2e.yml` 과 `federation-hardening-e2e.yml` 은 **다른 워크플로**다. 하나가 초록이라고
  다른 하나를 추정하지 마라 — 이 세션에서 둘 다 빨갰고 실패 스펙이 서로 달랐다

# Failure Scenarios

- **간헐로 오해하고 재실행** → 7일 연속 실패는 재실행으로 안 사라진다. AC-1 이 diff 를 요구한다
- **`SCM-BE-058` 과 묶어서 하나만 고침** → 나머지가 남는다. AC-2 가 막는다
- **PR-time CI 가 초록이라 안심** → 이 경로는 **nightly 에서만** 실행된다
  (CLAUDE.md § "Post-merge nightly check")

# Definition of Done

- [ ] AC-0~AC-4 충족
- [ ] Federation Hardening E2E nightly GREEN
- [ ] Ready for review
