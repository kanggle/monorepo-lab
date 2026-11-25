# Task ID

TASK-MONO-838

# Title

iam · ecommerce 의 `alerts.json` 패널 11개씩이 프로비저닝되지 않은 datasource uid `"prometheus"` 를 가리킨다(Grafana 404) — 고치고, 대시보드가 가리키는 uid 가 그 프로젝트가 프로비저닝한 uid 인지 가드가 본다

# Status

done

# Owner

monorepo

# Task Tags

- observability
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (치환 2파일 + Node 가드 1 + CI 잡)
> 기안 2026-11-25 UTC. 출처: `TASK-BE-666` 증거 «기록만 ②» → 소유자 «진행».

# Dependency Markers

- 선행: `TASK-BE-666`(done) — wms 의 같은 결함을 고치며 Grafana 11.2.0 에서 실측: `GET /api/datasources/uid/prometheus` → **404 «Data source not found»**, 프로비저닝된 uid `PBFA97CFB590B2093` → 200.

# Goal

2026-11-25 UTC 실측(`git ls-files` 전체에서 `"uid": "prometheus"` 를 가진 파일):

- `projects/iam-platform/infra/grafana/dashboards/alerts.json` — 11곳. iam 프로비저닝 uid = `PBFA97CFB590B2093`(`provisioning/datasources/datasources.yml:9`), 다른 대시보드 참조 63곳이 이 값.
- `projects/ecommerce-microservices-platform/infra/grafana/dashboards/alerts.json` — 11곳. ecommerce 프로비저닝 uid = `PBFA97CFB590B2093`(`provisioning/datasources/prometheus.yml:6`), 다른 참조 31곳이 이 값.
- → 두 프로젝트의 «Alert Overview» 대시보드는 패널 전부가 «Data source not found» 다. 대시보드를 가진 프로젝트는 iam · ecommerce · wms 셋(wms 는 666 에서 고침).
- 이 결함을 잡는 것이 저장소에 없다. 같은 파일이 세 프로젝트에 복사되어 셋 다 깨져 있었다.

# Scope

## In Scope

1. 두 `alerts.json` 의 `"uid": "prometheus"` 11곳씩을 각 프로젝트의 프로비저닝 uid 로.
2. **가드** `scripts/check-grafana-datasource-uid.mjs`: 프로젝트마다 (a) `infra/grafana/provisioning/datasources/*.y{a,}ml` 의 datasource `uid:` 집합, (b) `infra/grafana/dashboards/**/*.json` 의 모든 `datasource` 객체의 `uid` — (b) ⊆ (a). 제외: Grafana 내장(`-- Grafana --` · `-- Mixed --` · `-- Dashboard --`)과 템플릿 변수(`$…` · `${…}`). 대시보드 자신의 최상위 `uid` 는 datasource 가 아니므로 세지 않는다(JSON 으로 파싱해서 `datasource` 키 아래만 본다). 모집단은 `git ls-files`. 대시보드가 있는데 프로비저닝 uid 가 0개면 실패(fail-closed), 대시보드 파일이 저장소 전체에 0개여도 실패.
3. `--self-test`: 맞는 uid 통과 · 없는 uid bite · 템플릿 변수 통과 · 내장 datasource 통과 · 대시보드 최상위 uid 무시 · 프로비저닝 파일 없음 bite · 다른 프로젝트의 uid 는 통과 안 됨(프로젝트별 집합) · 빈 모집단 bite.
4. CI 잡(paths: `projects/*/infra/grafana/**` · 스크립트 자신), `node --check` → `--self-test` → 실제 트리.
5. 가드 수 산문(`CLAUDE.md` · `platform/git-workflow-policy.md`) — 새 파일이 `git ls-files` 를 읽으므로 분자 · 분모 둘 다 +1.

## Out of Scope

- 대시보드가 묻는 **지표 이름** 이 존재하는가(iam 대시보드에 스펙 · 코드 어디에도 없는 이름이 있다 — `TASK-MONO-837` · `TASK-BE-666` 기록). 그 가드는 iam 정리 뒤.
- 대시보드 내용 변경.

# Acceptance Criteria

- [x] AC-1: 고치기 **전** 실제 트리에서 가드 rc=1, 정확히 22곳(iam 11 · ecommerce 11)을 파일 · uid 와 함께 보고. 고친 뒤 rc=0.
- [x] AC-2: `--self-test` 전부 PASS.
- [x] AC-3: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) — `check-ls-files-guard-count.sh` 포함.
- [x] AC-4: PR CI 에서 새 잡이 **실행되어** success · 실패 0.

# Related Specs

- `TEMPLATE.md` § Local Network Convention(Grafana 호스트)
- 각 프로젝트 `infra/grafana/provisioning/datasources/*.yml`

# Related Contracts

- 없음.

# Edge Cases

- datasource 가 문자열로 적힌 옛 형식(`"datasource": "Prometheus"`)은 uid 가 아니라 이름이다 — 이름이 프로비저닝 `name:` 과 같으면 통과, 아니면 실패.
- 패널 안 `targets[].datasource` 와 패널 `datasource` 둘 다 본다(Grafana 는 둘 다 쓴다).

# Failure Scenarios

- 가드가 대시보드 최상위 `uid`(`"alert-overview"`)까지 datasource 로 세어 첫날 빨강 → self-test 가 잡는다.
- 프로젝트 경계 없이 전 저장소 uid 합집합과 비교 → 한 프로젝트에만 있는 uid 를 다른 프로젝트가 써도 통과. self-test 가 잡는다.

# Test Requirements

- 고치기 전 bite · self-test · 가드 전수 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **변경**: iam · ecommerce `alerts.json` 의 `"uid": "prometheus"` 11곳씩 → `PBFA97CFB590B2093`(두 프로젝트의 프로비저닝 uid). 새 가드 `scripts/check-grafana-datasource-uid.mjs` + CI 잡 `grafana-datasource-uid`(paths: `projects/*/infra/grafana/**` · 스크립트). 가드 수 산문 `CLAUDE.md` · `platform/git-workflow-policy.md` 30/71 → **31/72**.
- **AC-1** — 고치기 **전** 실제 트리(두 파일 치환 전, 가드만 추가): rc=1, **정확히 22줄** — `projects/ecommerce-microservices-platform/…/alerts.json` 11 · `projects/iam-platform/…/alerts.json` 11, 각 줄에 JSON 경로 · 값 · 그 프로젝트의 프로비저닝 uid 목록(예: `$.panels[0].datasource.uid = "prometheus" — … (provisioned uids: PBFA97CFB590B2093, loki)`). 고친 뒤 rc=0: «226 datasource reference(s) in 20 dashboard(s) across 3 project(s)». 🔴 첫 실행은 `spawnSync … ENOBUFS` 로 죽었다 — `git ls-files -- projects` 출력이 `execFileSync` 기본 버퍼(1 MiB)를 넘었다. pathspec 을 `projects/*/infra/grafana/**` 로 좁히고 `maxBuffer` 를 올렸다.
- **AC-2** — `--self-test` **13/13**: 맞는 uid 통과 · 없는 uid bite(패널 + target 2) · 템플릿 변수 `${DS_PROMETHEUS}` 통과 · `-- Grafana --` 통과 · 대시보드 최상위 `uid` 무시 · Loki `derivedFields` 의 중첩 `name:`/`datasourceUid:` 는 datasource 아님(그 값을 쓰면 bite) · 옛 문자열 형식 = 프로비저닝 이름이면 통과 / 아니면 bite · 다른 프로젝트의 uid 는 통과 안 됨 · 프로비저닝 없음 bite · 대시보드 0 bite · 깨진 JSON bite · 추적 안 된 대시보드는 모집단 밖.
- **AC-3** — 스테이지 뒤 **전 가드**(`scripts/` 에 파일이 늘었으므로 관련 가드만이 아니라 전부): `check-*.sh` 43 중 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `check-*.mjs` **7/7**(새 가드 포함). `check-ls-files-guard-count.sh` «2 homes agree with the measured 31/72».
- **AC-4** — PR CI(#4695): 실패 0 · SUCCESS 42 · SKIPPED 34 — 새 잡 `Grafana datasource uid (dashboards vs the project's provisioning)` 가 **실행되어** SUCCESS(9s).

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
