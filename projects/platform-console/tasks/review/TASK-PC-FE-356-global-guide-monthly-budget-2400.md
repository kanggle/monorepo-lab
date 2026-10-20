# Task ID

TASK-PC-FE-356

# Title

콘솔 전역 가이드의 «한 달에 최대 1800분» 을 2400분으로 — 데모 월 예산이 2400 으로 올랐다(소유자 결정 2026-10-20 UTC)

# Status

review (2026-10-20 UTC — 구현 · 시험 끝 · AC-3 = Vercel 배포 기록)

# Owner

frontend

# Task Tags

- copy

---

> **분석 모델:** Opus 5.5 / **구현:** Opus 5.5(문구 한 곳)

# Dependency Markers

- 출처: 32차 데모 창 소유자 결정 «2400 으로 올리고 전부 다 해줘» — 라이브 Lambda `MONTHLY_BUDGET_MINUTES=2400`(2026-10-20 18:02Z) · `infra/demo/aws/terraform/variables.tf` 기본값 2400(#4437). `TASK-MONO-672` § 32nd window — harvest 가 이 문구를 «소유자 승인 뒤» 로 남겼고, 소유자가 승인했다(«콘솔 가이드의 ‹한 달에 최대 1800분› 문구를 2400분 으로 바꿔줘»).

# Goal

`features/global-guide/data.ts` `SHUTDOWN_RULES` 의 월 상한 카드가 실제 상한(2400분)을 말한다.

# Scope

## In Scope

- 카드 제목 «한 달에 최대 1800분» → «한 달에 최대 2400분», 본문 «… 1800분을 넘으면 …» → «… 2400분을 넘으면 …»(승인 문안).
- 같은 카드의 출처 줄 번호를 현재 위치로(`variables.tf:205-209` · `README.md:242`) · 옆 «한 번에 최대 180분» 카드의 출처도 낡아 있어(`variables.tf:139-142` 는 부팅 보호 주석) `150-154` 로.

## Out of Scope

- 예산 값 자체(#4437 에서 고정).

# Acceptance Criteria

- [x] **AC-1** — 문구 두 줄 + 출처 줄 번호. 소유자 문안 승인(2026-10-20 UTC).
- [x] **AC-2 (시험)** — `npx vitest run guide` rc=0(14 파일 · 178). e2e · 단위 시험에 그 문장을 고정한 곳 없음(grep `1800` — 나머지는 토큰 TTL 1800 s 로 무관).
- [ ] **AC-3** — 배포 상태(Vercel Production) 기록.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- 없음(정적 가이드 데이터).

# Edge Cases

- 다음 예산 변경 때 같은 카드를 또 고쳐야 한다 — 카드의 `sources` 가 그 자리를 가리킨다.

# Failure Scenarios

1. 값만 바꾸고 출처 줄을 안 바꾼다 — 가이드의 «근거» 링크가 엉뚱한 주석을 가리킨다(180분 카드가 이미 그랬다).
