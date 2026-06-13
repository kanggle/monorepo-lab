# Task ID

TASK-FAN-BE-018

# Title

community-service membership-gate 스펙 drift 정정 — FAN-BE-010 이후 production 기본이 fail-closed `HttpMembershipChecker` 인데 community-service 서비스 스펙(architecture / overview / dependencies)은 여전히 "v1 = membership-service 없음 / PREMIUM 항상 통과 / v2 에서 교체" 로 기술. 같은 프로젝트 `membership-service/overview.md` + `membership-api.md` 는 swap 을 이미 인지 → 스펙 내부 모순. doc-only 보정.

# Status

review

# Owner

backend (Opus 4.8 analysis). 프로젝트 내부(`projects/fan-platform/`). 서비스 스펙 doc-only, production code 0.

# Task Tags

- docs

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행/원인**: TASK-FAN-BE-010 (`community HttpMembershipChecker adapter swap`, #1205, 2026-06-09 머지) — prod 기본 `MembershipChecker` 를 `AlwaysAllowMembershipChecker` → `HttpMembershipChecker`(fail-closed) 로 교체. 코드·membership-service 스펙·`membership-api.md:270` 은 반영됐으나 community-service 서비스 스펙은 미반영.
- **관련**: TASK-FAN-INT-002 (live-trio e2e escape-hatch 정렬) — e2e **테스트 코드**만 현행화했고 community-service **서비스 스펙**은 범위 밖이었음. 본 task 가 그 잔여 spec drift 를 닫음. e2e 하위-케이스의 로그-문자열 단언은 INT-002 영역이므로 건드리지 않음.

# Goal

community-service 서비스 스펙이 membership 게이트의 **현재 production 동작**(FAN-BE-010 이후 `HttpMembershipChecker`, fail-closed)을 정확히 반영하도록 정정한다. `AlwaysAllowMembershipChecker` 는 membership-service 부재 스택(live-trio e2e, `community.membership-service.enabled=false` escape-hatch)용 `@ConditionalOnMissingBean` fallback 임을 명시한다.

# Scope

**In (doc-only, 4 files / 7 spots):**

- `specs/services/community-service/architecture.md`
  - L100 file tree — `HttpMembershipChecker`(prod default) + AlwaysAllow(fallback) 둘 다 표기
  - L117 forbidden-deps note — "v2 will use HTTP clients" → v1 이 이미 HTTP client 사용
  - L168 MEMBERS_ONLY visibility row — prod default = HttpMembershipChecker
  - L169 PREMIUM visibility row — "v1 has no membership-service" 제거, 게이트 적용 명시
- `specs/services/community-service/overview.md`
  - L63 "Out of scope (v1)" — membership 통합은 v1 에 포함됨(FAN-BE-010)으로 이동
- `specs/services/community-service/dependencies.md`
  - L11 membership-service 행 — v2/NO(v1) → YES(v1, FAN-BE-010), HttpMembershipChecker
- `specs/integration/v1-e2e-scenarios.md`
  - L218 premise PREMIUM bullet — production=HttpMembershipChecker + e2e 가 escape-hatch 로 opt-out 함을 명시(INT-002 정합)

**Out:**

- production code / 테스트 (변경 없음 — 코드는 이미 정확)
- e2e-scenarios.md 하위-케이스 로그-문자열 단언(L230-234) — INT-002 영역, 현 동작 정확
- `membership-api.md:270`(이미 정확), `membership-service/*`(이미 정확)

# Acceptance Criteria

- [ ] 7 spot 전부 정정; community-service 스펙 어디에도 "v1 = membership 없음 / always-pass(production) / v2 에서 교체" 잔존 없음.
- [ ] `HttpMembershipChecker` = production default, `AlwaysAllowMembershipChecker` = `@ConditionalOnMissingBean` fallback(e2e escape-hatch) 로 일관 기술.
- [ ] e2e-scenarios premise 가 production 게이트 vs e2e opt-out 을 구분(INT-002 와 정합, 모순 없음).
- [ ] production code / `*.java` / 테스트 0 변경. docs-only fast-lane CI.
- [ ] 3-dim merge 검증 후 close chore.

# Related Specs

- `projects/fan-platform/specs/services/community-service/{architecture,overview,dependencies}.md`
- `projects/fan-platform/specs/services/membership-service/overview.md` (이미 FAN-BE-010 인지 — 정정 대상 아님, 정합 기준)
- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md`

# Related Contracts

- `projects/fan-platform/specs/contracts/http/membership-api.md` (L270 이미 정확 — 변경 없음)

# Edge Cases

- AlwaysAllowMembershipChecker 는 삭제/폐기된 게 아니라 fallback 으로 **유지** — 스펙도 제거가 아닌 역할 재기술.
- e2e premise 정정은 INT-002 가 설정한 escape-hatch 프레이밍과 **일치**시키는 추가 설명(모순 아님).

# Failure Scenarios

- production 동작을 "always-pass" 로 오기술 → 운영자/리뷰어가 PREMIUM 게이트가 미적용이라 오인. 본 정정으로 해소.
- AlwaysAllow 를 "production v1" 으로 오기술 → membership-service 부재 e2e escape-hatch 와 production 을 혼동. premise clarifier 로 해소.
