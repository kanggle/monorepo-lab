# Task ID

TASK-MONO-837

# Title

스펙이 선언한 metric · span 이름 중 코드에 없는 것을 전수 분류한다 — 무엇을 구현하고 무엇을 스펙에서 고칠지 소유자가 고를 수 있게(분류만, 구현 · 스펙 수정은 하지 않는다)

# Status

ready

# Owner

monorepo

# Task Tags

- observability
- analysis

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 (행마다 판단 — 프레임워크 이름 대응 · 파생값 · 이름 드리프트 · 오탐 제거)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-836` · `TASK-BE-665` 인계 «남은 것» → 소유자 «2번 진행».

# Dependency Markers

- 선행: `TASK-MONO-833`(코드 → 스펙 방향: 등록된 이름은 전부 문서에 있다, 가드 `check-metric-name-registry.sh`) · `TASK-MONO-834`(알림 → 코드 방향, 가드 `check-alert-rule-metrics.sh`). 이 티켓은 남은 방향 **스펙 → 코드** 를 잰다. 셋째 방향엔 가드가 없다.
- 후속: 소유자 결정 뒤의 구현 · 스펙 수정 티켓(이 티켓의 산출물이 그 입력).

# Goal

`TASK-BE-662` · `664` · `665` 는 스펙이 «필수» 라고 적은 metric 이 코드에 없어서 알림이 no-data 였던 결함들이었다. 그 셋은 **알림이 묻던 것** 만 고쳤다. 스펙에는 아직 코드에 없는 이름이 많이 남아 있고, 그중 일부는 결함(대시보드 패널이 비어 있다)이고 일부는 스펙 쪽이 틀렸다(프레임워크가 다른 이름으로 이미 낸다 · 쿼리로 계산하는 파생값 · 폐기된 서비스). 어느 쪽인지 행마다 갈라 두지 않으면 «전부 구현» 도 «전부 삭제» 도 틀린다.

기안 시점 예비 측정(2026-11-25 UTC, 아래 방법): 스펙 표 행에서 뽑은 고유 이름 251개(metric 228 · span 23, 4 프로젝트) 중 코드에 meter 로 등록된 것 74.

# Scope

## In Scope

1. **모집단**: `projects/*/specs/**/*.md` 의 표 행 중, 둘러싼 제목(가장 안쪽 분류 제목 기준)이 metric/메트릭/observability/관측 → metric, span/trace/tracing/추적(단어 경계) → span. 로그 필드 표(log/logging/로그/MDC 제목)는 제외. 행의 **첫 칸** 의 백틱 토큰 중 소문자 점·밑줄 이름. (project, kind, name) 로 중복 제거.
2. **코드 대조**: 프로젝트 `apps/*/src/main` + `libs/*/src/main` 의 `.java`/`.kt`. «등록됨» = meter 등록 호출(`Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer.builder(` · `.counter(` · `.timer(` · `.gauge(` · `.summary(`)에 넘어간 리터럴 또는 상수 값. 비교는 `.`/`-`→`_` 정규화 + 스펙 이름에서 Prometheus 접미사(`_total` · `_count` · `_sum` · `_bucket` · `_max`, 그다음 `_seconds`) 제거. **meter 가 아닌 리터럴(토픽 · 이벤트 · 태그 값)과의 일치는 구현으로 치지 않는다**(`TASK-MONO-835` 의 토픽 이름 오탐).
3. **분류**(행마다 하나): A 구현됨 · A2 이름만 다름(같은 계측이 다른 이름으로 등록) · B 프레임워크가 다른 이름으로 낸다(확인된 것) · B? 프레임워크 추정(미확인 — 확인 방법 명시) · C 파생값(스펙이 스스로 «계산» 이라 적음) · D1 미구현 + 대시보드/알림이 묻는다 · D2 미구현 · E span · F 폐기 서비스 스펙.
4. **곁가지 측정**: D 이름이 `projects/*/infra/**`(Grafana 대시보드 · 알림)에 나오는지.
5. **산출물**: 이 티켓 증거 절에 범주 × 프로젝트 표 · 범주별 이름 목록(서비스별) · 범주별 **소유자 결정 선택지와 에이전트 추천**. 측정 스크립트 원문은 증거 절 끝에(재현용).

## Out of Scope

- 구현 · 스펙 수정 · 대시보드 수정 — 전부 소유자 결정 뒤 별 티켓.
- 셋째 방향 가드 신설 — 결정 뒤 판단(분류가 끝나기 전엔 무엇을 «위반» 으로 칠지 정할 수 없다).

# Acceptance Criteria

- [ ] AC-1: 모집단 수와 그 방법이 증거에 있다. 방법의 **오탐 두 종**(제목 패턴이 «Contract» 를 «trac» 로 잡음 · 토픽 리터럴 일치)을 어떻게 걸렀는지 적는다.
- [ ] AC-2: 모든 고유 이름이 정확히 한 범주에 있다(범주 합 = 모집단).
- [ ] AC-3: B 는 행마다 근거(어느 프레임워크 시리즈 · 왜 존재한다고 보는지 — 실측 또는 코드 근거), B? 는 확인 방법.
- [ ] AC-4: D1 은 이름마다 묻는 파일.
- [ ] AC-5: 범주별 소유자 결정 선택지 + 추천(모델 표기 포함).

# Related Specs

- `platform/observability.md` § Business Metrics · § Change Rule
- 각 프로젝트 `specs/services/*/observability.md` · `idempotency.md` · `external-integrations.md` · `sagas/*.md`

# Related Contracts

- 없음.

# Edge Cases

- 같은 이름이 여러 스펙 파일(계약 + 서비스)에 있다 → 한 행, 파일 목록.
- 이름이 프로젝트 간에 같다(`auth_login_total` — iam 구현, ecommerce 폐기 스펙) → 프로젝트별로 따로.
- 등록이 헬퍼를 거친다(`buildCounter("…")`) → 등록 호출 정규식에 안 걸린다. 리터럴만 일치하는 행은 손으로 연다.

# Failure Scenarios

- 분류기가 토픽 이름을 구현으로 셈 → D 가 줄어 보이고 그 결함이 다시 숨는다. AC-1 이 두 오탐 처리를 요구한다.
- «추정» 을 «확인» 으로 적음 → 스펙을 틀린 이름으로 고치게 된다. B / B? 분리.

# Test Requirements

- 범주 합 = 모집단(스크립트 출력) · 리터럴만 일치한 행 전부 손 확인.

# Definition of Done

- AC 전부 · 머지 · 4-dim close · 소유자에게 결정 선택지 전달.
