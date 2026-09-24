# Task ID

TASK-MONO-729

# Title

론처 상태 배지·컨트롤 정합 + 방문자용 번들 "끄기" 버튼 (2026-09-24 UTC 전수조사)

# Status

in-progress

# Owner

monorepo

# Task Tags

- code
- deploy
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 정적 검증만으로 되돌릴 수 없는 상태-매트릭스 변경 + 가드 갱신이 함께 가야 한다. 라이브 창(실제 start/stop 클릭)은 비용이 들어 **에이전트가 돌리지 않는다** — 헤드리스 스텁-DOM/Playwright 로 대체 검증한다.

---

# Goal

`infra/demo/aws/site/index.html`(론처)의 카드별 상태 배지·시작/종료 버튼·전체 배지·의존성 고지·콘솔 표기를 소유자가 확정한 매트릭스대로 정리하고, 지금까지 없던 **방문자용 개별 번들 "끄기" 버튼**을 카드에 추가한다. `handler.py` 의 `/bundle/stop` 은 이미 있지만 페이지가 그것을 호출한 적이 없다 — 이 티켓이 그 배선을 잇는다.

# Scope

## In Scope

a) **카드별 상태/버튼 매트릭스** (BLABEL 재정의, `infra/demo/aws/site/index.html` 안 — 2026-09-24 UTC 실측: `BLABEL` 객체가 :582 부근, 상태값 `ready/booting/requested/selected/partial/stopping/waiting/unknown` 을 갖고 있음 확인):

| 상태 | 배지 | 버튼 | 비고 |
|---|---|---|---|
| 꺼짐 | ⚪ 꺼짐 | 「데모 서버 켜기」 | 클릭 가능 |
| 기동 중 | 🟡 기동 중… | 「기동 중…」(disabled) | 더블클릭 방지 |
| 사용 가능 | 🟢 사용 가능 | 「데모 서버 끄기」 | 클릭 시 그 번들의 `/bundle/stop` 호출 |
| 종료 중 | 🔵 종료 중… | 「종료 중…」(disabled) | 더블클릭 방지 |
| 초기 응답 전 | 🔵 상태 확인 중… | (disabled) | 첫 폴 전 placeholder — 기존 「… 확인 중」(index.html:244,262,280 등, class `b-unknown`)을 대체 |
| 진짜 오류 | 🔴 확인 실패 | (disabled 또는 재시도) | `/bundles` fetch 실패(index.html:763-770 `bundlesErr`)와 헬스 90초 이상 정체(`handler.py:45-53, 483-484` 기준)는 **서로 다른 원인**이므로 상세 줄(detail line)에서 원인을 구분해 보여준다 — 배지 텍스트 자체는 하나로 통일해도 된다. |

모든 전이 상태(기동 중/종료 중)에서 버튼은 disabled + 더블클릭 보호.

b) **전체(whole-demo) 집계 배지를 번들별 배지와 분리**한다 — 지금은 EC2 전체 배지(index.html:546-552 `B_EC2` 맵)가 `running` 이면 「✅ 실행 중」한 가지로만 보여, 번들 하나만 떠 있어도 "전부 사용 가능"처럼 읽힌다. 새 집계 값:

| 집계 상태 | 표기 |
|---|---|
| 모두 꺼짐 | ⚪ 모두 꺼짐 |
| 일부 기동 중 | 🟡 일부 기동 중 |
| 모두 사용 가능 | 🟢 모두 사용 가능 |
| 일부만 사용 가능(나머지는 꺼짐/기동 중) | 🟢 일부 서버 사용 가능 |
| 일부 종료 중 | 🔵 일부 종료 중 |
| 일부 오류 | 🔴 일부 오류 |

「전체 사용 가능」류 문구가 **일부만 켜진 상태에서 뜨는 경우가 0건**이 되는 것이 판정 기준.

c) **카드별 의존성 고지** — 각 번들 카드에 "필요한 공용 서비스: IAM(이미 실행 중이면 유지)" 류 한 줄을 추가한다. `projects.sh` 의 `DEPS` 표(2026-09-24 UTC 실측: `projects.sh:190-198`, 모든 도메인이 iam에 하드 의존)와 `handler.py:653-655`(정지 시 다른 도메인이 iam/traefik을 쓰고 있으면 잔류시키는 가드)가 **같은 원장**이다 — 페이지에 두 번째 사본을 하드코딩하지 말고, 이미 존재하는 diff 가드((z32) — `infra/demo/verify-demo-wrapper.sh` 안에서 `projects.sh` DEPS 대 `handler.py` 표를 대조하는 검사)가 참조하는 것과 같은 한 원장에서 파생한다.

d) **콘솔 표기 분리** — 론처의 도메인 목록(`index.html:830` 부근 `DOMAINS` 배열)에서 「운영자 콘솔」(index.html:232-236 카드 타이틀)을 「Platform Console」(운영 콘솔)로 표기하고, 시각적으로 비즈니스 도메인 5개(ecommerce/wms/scm/finance/erp)와 분리한다(예: 별도 구획 또는 구분선).

e) **방문자용 "끄기" 버튼** — 카드가 `ready`(사용 가능) 상태일 때 버튼이 그 번들의 `/bundle/stop` 을 호출한다(`handler.py:648-682` 기존 엔드포인트 재사용 — 새 API 불필요 전제, AC-0에서 재확인).

## Out of Scope

- 고급 영역(`<details class="adv">`, index.html:353-375)의 전체 스택/도메인별 시작·종료 버튼 — 무변경.
- `handler.py` 로직 변경은 **엄격히 필요한 경우에만**(예: 새 상태값이 응답에 없다면 최소 추가). 어떤 변경이든 배포하려면 `terraform apply` 가 필요하고 **에이전트는 이것을 실행할 수 없다** — AC에 "소유자가 apply 한다" 를 명시한다.
- 실제 라이브 EC2에 대고 start/stop 을 클릭하는 검증 — 비용 발생, 이 티켓은 **정적 + 스텁-DOM 검증**까지만 한다.

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act, 편집 전)** — `infra/demo/verify-demo-wrapper.sh` 안에서 이 배지·상태·버튼 텍스트·구조를 고정(pin)하는 가드를 **전부** 나열한다(최소 z14, z34 — BITE/BITE3 앵커 포함, z35, z37, z39, `t` 계열 각각의 역할을 한 줄로). 각 가드가 이번 변경으로 **어떻게 움직이는지**(기대 문자열 변경, 새 상태값 추가 등)를 적는다. 가드는 이 PR **안에서** 갱신한다 — 절대로 약화(assert 삭제·범위 축소)하지 않는다.
- [ ] **AC-1** — 위 매트릭스(a)대로 카드 상태·버튼·배지가 바뀐다. `bash infra/demo/verify-demo-wrapper.sh`(정적 모드)가 `rc=0`.
- [ ] **AC-2** — bite: 새로 추가한 동작(전이 상태 disable, "끄기" 버튼 존재, 전체 집계 분리 등) 중 하나를 되돌리면 그 동작을 지키는 가드가 실제로 빨개진다(적어도 한 개 이상 실측).
- [ ] **AC-3** — 헤드리스 스텁-DOM 또는 Playwright 로 `/bundles` 응답을 모킹해 각 상태(ready/booting/requested/selected/partial/stopping/waiting/unknown)에서 배지·버튼 텍스트·disabled 여부를 확인한다. **실제 start/stop 클릭은 라이브 API 에 대고 하지 않는다** — 비용 문제.
- [ ] **AC-4** — 전체 집계 배지가 "일부만 켜짐" 상태에서 "모두 사용 가능"류 문구를 내는 경우가 0건임을 스텁 시나리오로 확인한다.
- [ ] **AC-5** — 카드별 의존성 고지 문구가 `projects.sh` DEPS 표와 실측 대조해 일치하고, 하드코딩된 두 번째 사본이 아님을 diff 로 확인한다.
- [ ] **AC-6** — 콘솔 카드가 「Platform Console」로 표기되고 시각적으로 분리된다(스크린샷 또는 DOM 구조 확인).
- [ ] **AC-7** — `handler.py` 를 바꾼 경우, 그 변경이 **배포되려면 소유자가 `terraform apply` 를 실행해야 한다는 것**을 AC 결과에 명시한다(에이전트는 실행 불가).

# Related Specs

- `infra/demo/aws/site/index.html` (론처 페이지 본체)
- `infra/demo/aws/lambda/handler.py` (상태/시작/종료 API)
- `infra/demo/projects.sh` (DEPS 표, 도메인-공용서비스 의존성 원장)
- `infra/demo/demo-down.sh` (정지 시 잔류 가드, :50-65)
- `infra/demo/verify-demo-wrapper.sh` (정적 검증 — z14/z32/z34/z35/z37/z39 및 관련 가드)
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

# Related Contracts

- 론처 ↔ Lambda `/bundles`, `/bundle/stop`, `/status` 응답 스키마 — 이 티켓은 **소비만** 한다(새 필드가 꼭 필요하면 최소 추가, 계약 문서가 있다면 먼저 갱신).

# Edge Cases

- 다른 방문자가 지금 쓰고 있는 번들을 내가 끄는 경우 — 소유자 결정: **허용**(방지 메커니즘 없음). 카드에 "다른 방문자가 사용 중일 수 있습니다" 류 안내를 넣을지는 구현자 판단으로 남기고, 넣지 않기로 하면 그 이유를 기록한다.
- 공용 iam/traefik 이 사용 중인 상태에서 어떤 도메인을 끄는 경우 — 인스턴스 쪽 잔류 가드(`demo-down.sh`)가 이미 지킨다, 이 티켓은 UI 쪽에서 추가로 막지 않는다.
- 헬스가 90초 이상 정체된 상태에서 종료를 누르는 경우 — 배지가 이미 🔴 확인 실패이므로 버튼이 disabled 인지 확인한다.
- Lambda 엔드포인트가 옛 배포에 없는 경우(z34가 다루는 부재 경로) — 이 케이스에서 페이지가 깨지지 않는지 확인한다.

# Failure Scenarios

- UI의 "끄기" 버튼이 실수로 **전체 EC2** `/stop` 을 호출한다 — 반드시 그 카드의 번들 한정 `/bundle/stop` 이어야 한다.
- 전체 집계 배지를 두 번째 하드코딩 목록으로 계산한다 — (c)와 같은 함정, 반드시 한 원장에서 파생시킨다.
- 가드를 약화해서 rc=0 을 만든다 — AC-0의 「가드는 이 PR 안에서 갱신하되 약화 금지」를 위반하는 것이므로 실패로 간주.
- 라이브 API 에 대고 start/stop 을 실제로 클릭해 예산을 소모한다 — 이 티켓의 검증은 스텁/정적으로 충분해야 한다.

---

# Implementation Notes

- `handler.py` 변경은 최소화하고, 바꿨다면 **왜 정적/스텁 검증만으로 충분하지 않았는지**와 **배포에 필요한 소유자 조치(`terraform apply`)**를 AC 결과 섹션에 명시한다.
- 카드 상태 매트릭스는 기존 `BLABEL`/`B_EC2`/`B_DOM` 객체를 확장하는 방향으로 구현하고, 상태 이름 자체(`ready`/`booting`/... )는 API 응답이 이미 쓰는 값이므로 바꾸지 않는다 — 바뀌는 것은 그 값에 매핑되는 **표시 텍스트/버튼**뿐이다.
