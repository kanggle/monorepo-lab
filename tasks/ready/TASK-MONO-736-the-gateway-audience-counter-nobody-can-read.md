# Task ID

TASK-MONO-736

# Status

ready

# Title

게이트웨이 audience 섀도 카운터를 **아무도 읽을 수 없다** — 데모 prometheus 가 게이트웨이를 스크레이프하지 못한다 (`TASK-MONO-697` 의 선행)

# Owner

monorepo (infra/observability · 6 게이트웨이)

# Task Tags

- observability
- gateway
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 스크레이프 설정 · 자격 · 판독이다. 다만 AC-0 이 «어느 채널로 볼 것인가» 를 먼저 정한다.

---

# Goal

`TASK-MONO-697`(audience 검사 SHADOW → ENFORCE)의 전환 조건은 **«실측 불일치 0»** 이다. 그 티켓의 2026-09-22 정정 ③ 이 적은 마지막 탐침
(«도메인 prometheus 가 게이트웨이를 스크레이프하니 거기서 읽어라»)을 16차 AMI 창(2026-09-26 UTC, 소유자 콘솔 · 스토어 · 팬 로그인 트래픽 뒤)에서 쟀다:

| prometheus | `gateway-service` 타깃 | `gateway_jwt_audience_total` · `{__name__=~"gateway_jwt.*"}` |
|---|---|---|
| `ecommerce-prometheus` | **down — `server returned HTTP status 401 Unauthorized`** | `result: []` |
| `wms-prometheus` | **down** (연결 실패) | `result: []` |
| `iam-prometheus` | **down** (연결 실패) | `result: []` |

그 밖: 7 게이트웨이 전부 `JWT audience not on allowlist` WARN **0줄** — 그러나 게이트웨이는 요청당 로그를 내지 않으므로(697 정정 ③) 분모가 없다.
⇒ 697 이 미리 적어 둔 분기 그대로: **«섀도가 관측 불가능하게 출하됐다»** — 불일치 0 이 아니라 **잴 수 없다**. 🔵 곁관측: wms · iam prometheus 는 게이트웨이뿐 아니라 거의 모든 서비스 타깃이 down 이다.

# Scope

## In Scope

- **AC-0** — 채널 결정(소유자): ① 도메인 prometheus 가 게이트웨이 `/actuator/prometheus` 를 **읽을 수 있게**(401 → 스크레이프 자격 또는 관리 포트 분리 · 연결 실패 → 타깃 주소) ② 게이트웨이가 audience 판정을 **집계 로그 한 줄**로 주기 발행 ③ 그 밖.
- 6 게이트웨이(wms · scm · erp · finance · fan · ecommerce)에서 `outcome="match"` 시계열이 **0 보다 큰 값으로 보이는 것**까지.

## Out of Scope

- ENFORCE 전환 자체(`TASK-MONO-697`).

# Acceptance Criteria

- [ ] **AC-0** — 채널 결정 + 그 채널의 유효성 술어(무엇이 보이면 «읽혔다» 인가).
- [ ] **AC-1** — 구현. 🔴 판정은 `outcome="match"` 가 보이는 것이다 — `mismatch_shadowed` 부재만 보고 닫지 마라(697 의 반복된 함정).
- [ ] **AC-2** — 창에서 6 게이트웨이 각각 `match > 0` 확인(트래픽: 콘솔 5 도메인 · web-store · fan 로그인). 그 뒤 697 AC-0 을 이 채널로 잰다.

# Related Specs

- `tasks/ready/TASK-MONO-697-…` § 2026-09-22 정정 ①②③ · `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5

# Related Contracts

- 없음(관측 표면).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 게이트웨이 액추에이터를 공개로 열어 401 을 푼다 | 🔴 금지 — 자격 또는 내부 포트로 |

# Failure Scenarios

1. **`result: []` 를 «불일치 0» 으로 읽는다** → 697 이 거짓 전제로 ENFORCE 된다.
2. **한 게이트웨이만 보이게 하고 닫는다** → 697 은 6 게이트웨이 전부를 전환한다.
