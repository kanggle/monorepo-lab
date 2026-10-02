# Task ID

TASK-MONO-736

# Status

done

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

- [x] **AC-0** — 채널 결정 + 그 채널의 유효성 술어(무엇이 보이면 «읽혔다» 인가). → ② 요약 로그 줄 (소유자, 2026-09-29) · 술어는 § 구현 기록.
- [x] **AC-1** — 구현. 🔴 판정은 `outcome="match"` 가 보이는 것이다 — `mismatch_shadowed` 부재만 보고 닫지 마라(697 의 반복된 함정).
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

---

# Implementation Record (2026-09-29 UTC · 분석=Opus 5.5 · 구현=Opus 5.5)

## AC-0 — 채널 = ② 집계 로그 한 줄 (소유자 결정, 2026-09-29)

- **왜 ② 인가(판단 근거로 제시한 것)**: 검증 코드는 공용 `libs/java-security` 의 `AllowedAudiencesValidator` **한 곳**이라 6 게이트웨이(+ console-bff)가 한 변경으로 같이 움직인다. ① 은 원인이 셋(ecommerce 401 · wms/iam 연결 실패 — 그 둘은 게이트웨이 외 서비스도 거의 전부 down)이라 도메인별 작업이 되고, 창 없이 검증이 어렵다. 게이트웨이 로그는 여섯 모두 root `INFO` 이고 데모에서 덮는 설정이 없음을 확인했다(`application.yml` · ecommerce `logback-spring.xml` · `infra/demo` 전역 grep 0건).
- **형식(wire)**: `JWT audience summary: gateway=<g> mode=<SHADOW|ENFORCE> match=<n> mismatch=<m>` — 기동 이후 누적, `validate` 안에서 최대 60초에 한 번(첫 검사 즉시), 스레드·스케줄러 없음(이 클래스는 프레임워크 중립이라 멈출 생명주기가 없다).
- **유효성 술어(«읽혔다»)**: 게이트웨이마다 마지막 요약 줄이 **있고** `match > 0`. 🔴 줄이 없으면 «검사 0회» — «불일치 0» 이 아니다(697 의 반복된 함정을 형식 자체가 막는다: 분자와 분모가 **한 줄**에 같이 나온다).
- 🔴 재시작하면 0 부터 다시 센다 — 창에서는 트래픽을 돈 뒤 읽고 컨테이너 Up 시간을 같이 적는다.

## AC-1 — 구현 ✅

- `libs/java-security/.../AllowedAudiencesValidator.java` — 누적 `AtomicLong` 두 개 + `maybeSummarize()`(CAS 로 경계의 동시 검사에서도 한 줄) + `summaryLine()`(wire 형식 단일 출처) + 상수 `SUMMARY_LOG_PREFIX` · `SUMMARY_INTERVAL_SECONDS`. 공개 생성자 시그니처 **불변**(`System::nanoTime` · `log::info` 로 위임) — 6 게이트웨이·console-bff 코드 무변경. 테스트용 패키지 전용 생성자(시계·sink 주입). 기존 메트릭·WARN 줄 무변경.
- 테스트 `AllowedAudiencesValidatorTest$SummaryLine` 8칸: wire 형식 · 검사 0회 → 줄 0 · 첫 검사 즉시 · 간격 안 억제 + 간격 뒤 **누적** · 요약 수 = 메트릭 수 · ENFORCE 도 보고 · 64 스레드 동시 → 줄 1 · 공개 생성자 동작.
- **bite 2건**(주입 줄 수 확인 → RED → 원복 → 주입 0 → GREEN): ① 첫 줄을 한 간격 뒤로 미룸 → 4칸 RED(첫 검사 즉시 · 누적 · ENFORCE · 동시) ② 줄마다 누적값을 0 으로 리셋 → 2칸 RED(누적 · 메트릭 일치).
- 스펙 동기화(같은 사실의 모든 집): erp · finance · fan · scm `gateway-service/architecture.md`, wms `gateway-service/overview.md`, ecommerce `specs/integration/iam-integration.md` 에 요약 줄 한 문장씩. `TASK-MONO-697` 에 § 정정 ④(읽는 법의 현재판) 추가.

## 검증 (로컬 — 개별 실행, rc 직접 확인)

| 게이트 | 결과 |
|---|---|
| `:libs:java-security:check` (테스트 + `assertClasspathNeutrality`) | rc=0 · 128 tests, 0 fail · 중립성 OK(26 artefacts) |
| `:libs:java-gateway:test` | 83 / 0 fail |
| 6 게이트웨이 `:test` (wms 60 · ecommerce 148 · fan 48 · scm 56 · finance 33 · erp 33) + `console-bff:test` 87 | 전부 0 fail · rc=0 |

⚪ Testcontainers `integrationTest` 는 로컬 Docker 꺼짐으로 안 돌렸다 — CI 통합 잡이 권위. 이 변경은 로그 한 줄 추가라 IT 기대값을 바꿀 자리가 없다.

## AC-2 — ⏳ 창 대기 (재굽기 뒤)

검증기는 앱 소스라 **재굽기 전까지 데모에 없다**(`infra/demo/aws/README.md` 배포 층). 18차 AMI(이 PR 머지 이후 커밋)로 연 창에서: 콘솔 5 도메인 · web-store · fan 로그인 트래픽 → 6 게이트웨이 각각 `docker logs <gw> 2>&1 | grep "JWT audience summary" | tail -1` 이 있고 `match > 0`. 이어서 `TASK-MONO-697` AC-0 을 § 정정 ④ 대로 잰다.

---

## CORRECTION (2026-10-02 UTC) — AC-2 라이브 🟢: 6 게이트웨이 전부 match > 0 · mismatch 0

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

트래픽: 콘솔 5 도메인 화면(09:43–10:00) · 스토어·팬 로그인(09:37–10:07). 10:06:30Z 에 각 게이트웨이 컨테이너 로그의 마지막 요약 줄(SSM 읽기):

| 게이트웨이 | 요약 줄 | `not on allowlist` WARN 줄 |
|---|---|---|
| ecommerce | `mode=SHADOW match=181 mismatch=0` | 0 |
| fan | `match=69 mismatch=0` | 0 |
| scm | `match=60 mismatch=0` | 0 |
| erp | `match=29 mismatch=0` | 0 |
| wms | `match=26 mismatch=0` | 0 |
| finance | `match=1 mismatch=0` | 0 |

- 분모가 전부 0 보다 크다 ⇒ «불일치 0» 이 공허하지 않다. iam 게이트웨이는 대상 밖(요약 줄 0 — 이 검사를 하지 않는다).
- 「그 뒤 697 AC-0 을 이 채널로 잰다」 — 이 표를 `TASK-MONO-697` 에 한 창의 표본으로 덧붙였다(697 AC-0 은 client 별 ⚪ 칸 판정까지 요구하므로 이 표본만으로 닫히지 않는다 — 그 판정은 697 의 몫).
⇒ AC-2 닫힘 → `done/`.
