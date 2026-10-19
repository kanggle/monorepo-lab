# Task ID

TASK-PC-FE-351

# Title

e2e 스택이 `*.local` 기본값을 가진 콘솔 서버 주소를 **전부** 막아 둔다 — 341 가드가 조합 다리만 보던 구멍(`WMS_OUTBOUND` · `LEDGER` · `FAN_GATEWAY`)

# Status

in-progress (2026-10-19 UTC 기안 · 착수)

# Owner

frontend

# Task Tags

- test
- e2e

---

> **분석 모델:** Opus 5.5 / **구현:** Opus 5.5 (설정 3 줄 + 가드 확장)
> 기안 2026-10-19 UTC — `TASK-PC-FE-350` AC-0 ② 에서 나온 후속.

# Dependency Markers

- 출처: `TASK-PC-FE-341`(e2e 의 E-Commerce 주소 누락 → 런당 1 회 10 s 자기 호출 실패) · `TASK-PC-FE-350` AC-0 ②(`.local` 기본값 14 개 전수).
- 선행 없음.

# Goal

341 은 E-Commerce 주소를 막고, 가드(`e2e-compose-composition-bases.test.ts`)를 **조합 다리가 읽는 주소**로 한정했다. 그런데 콘솔 서버에는 `*.local` 기본값을 가진 주소가 14 개 있고, e2e 에서 그중 3 개(`WMS_OUTBOUND_BASE_URL` · `LEDGER_BASE_URL` · `FAN_GATEWAY_BASE_URL`)가 비어 있다. 지금은 그 화면을 여는 e2e 가 없어 조용하다. 하지만 시험이 하나 생기는 날 341 과 같은 조회 정체가 돌아오고, 가드는 그것을 보지 못한다.

# Scope

## In Scope

- `docker-compose.e2e.yml` console-web 에 그 3 개를 다른 도메인처럼 non-routable(`127.0.0.1:9`)로 넣는다.
- 가드에 칸을 더한다: `env.ts` 의 zod 스키마에서 기본값이 `*.local` 인 키 **전부**(키 블록 단위로 짝지어 — 평면 정규식은 다음 키의 기본값과 짝을 잘못 짓는다)가 e2e 에 있어야 한다. 비공허 단언(짝 고정 · 개수 ≥ 14)을 둔다.

## Out of Scope

- 실제 백엔드로 연결해 그 화면을 e2e 로 시험하는 것.
- 기본 compose(`docker-compose.yml`)의 `${…:-*.local}` 기본값 — 거기서는 Traefik 별칭으로 풀린다(350 AC-0 ②).

# Acceptance Criteria

- [x] **AC-1** — 빨강 먼저: 새 칸이 main 에서 정확히 그 3 개 키를 짚어 실패한다. 비공허 단언은 통과한다. — ✅ 2026-10-19 UTC: `FAN_GATEWAY_BASE_URL` · `LEDGER_BASE_URL` · `WMS_OUTBOUND_BASE_URL` 3 개만 짚어 1 failed / 4 passed. 🔴 첫 시도는 «no tests» 였다(삽입 스크립트의 이스케이프가 시험 파일에 실제 CR · 백스페이스 문자로 들어가 변환 실패) — 그것은 판정이 아니었고, 제어 문자를 지운 뒤 다시 쟀다.
- [x] **AC-2** — 초록: 3 줄 추가 뒤 가드 통과 · 전체 vitest · tsc · lint rc=0. — ✅ vitest 409 files / 4898 · tsc rc=0 · next lint rc=0.
- [ ] **AC-3** — 브랜치 nightly 콘솔 잡 초록(설정 변경이므로).

# Related Specs

- `projects/platform-console/docker-compose.e2e.yml` (console-web 블록 주석)

# Related Contracts

- 없음(e2e 설정만).

# Edge Cases

- `e2e-smoke/sample-visitor-ledger.spec.ts` 는 `/ledger` 를 익명 샘플로 연다 — 샘플 경로라 백엔드를 부르지 않는다(영향 없음).
- 새 주소 키가 `env.ts` 에 생기면 가드가 그날부터 요구한다.

# Failure Scenarios

1. 가드가 평면 정규식으로 키와 기본값을 잘못 짝짓는다 — 실제로 350 AC-0 첫 시도에서 일어났다. 블록 단위 파싱 + 짝 고정 단언으로 막는다.
2. 막아 둔 주소 때문에 어떤 e2e 가 «정상 데이터» 를 기대하다 깨진다 — 지금 그 화면을 여는 e2e 는 없다(grep 확인).
