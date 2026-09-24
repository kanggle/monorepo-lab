# Task ID

TASK-MONO-729

# Title

론처 상태 배지·컨트롤 정합 + 방문자용 번들 "끄기" 버튼 (2026-09-24 UTC 전수조사)

# Status

review

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


---

# Implementation Record (2026-09-24 UTC)

## AC-0 — 이 표면을 핀하는 가드 전수와 각각의 이동 (편집 전 목록)

| 가드 | 역할(한 줄) | 이번 변경으로 어떻게 움직였나 |
|---|---|---|
| (t) | `demoHost()`(GUARD-T-ANCHOR) 가 `demo-boot.sh` 와 같은 도메인을 만드는가 | 무변경(해당 줄 안 건드림) — 통과 |
| (z11) | 론처의 로그인 계정이 시드 값과 같은가 | 무변경 — 통과 |
| (z14) | `[data-surface]` 링크 판정 + `#surfaces` 가시성(GUARD-Z14 구간 실행) | 구간 안 변경은 `render()` 의 running 문구 1줄뿐(「✅ 인스턴스 실행 중 — …」). 가드는 문구를 단언하지 않음 — 무변경·통과. 카드 순서 이동(콘솔을 뒤로)은 `data-surface` 선언을 안 바꿈 |
| (z15) | 부팅 판정이 HTTP 표면을 보는가(`<div id="smsg"></div>` 줄에 행 주입) | 그 줄 그대로 유지 — 무변경·통과 |
| (z32) | `projects.sh` ↔ `handler.py` 묶음 **키** 대조 + 카드 `data-bundle` 실재 | 무변경·통과. 🔴 값(하드 의존 iam)은 안 본다는 것을 확인 → 새 (z42) 가 메운다 |
| (z34) (+BITE/BITE3 앵커) | `/bundles` 404·5xx·차단 때 카드가 사유를 말하는가(GUARD-Z34 구간 실행) | 대역: 버튼 셀렉터 `data-bundle-start`→`data-bundle-act`, `data-deps` 칸, 집계 배지 `querySelector` 추가(그 셀렉터만 — 모르는 셀렉터는 여전히 죽음). 미측정 문구 상수 `… 확인 중`→`🔵 상태 확인 중…`. 스냅샷에 **버튼 모드** 필드 추가. `ready 면 잠김` 단언을 `ready 면 열림 + 모드=stop` 으로 **뒤집고**(목적인 «중복 켜기 금지» 는 모드로 유지), `waiting 모드=start` 단언 추가. BITE/BITE3 앵커 줄 무변경, bite 3칸 그대로 문다 |
| (z35) | 카드 구조(링크 1·제목 앵커·로그인 전/후·캐러셀 자리) + 캐러셀 실행 | 무변경·통과(콘솔 제목 `Platform Console` 도 `<h2><a …data-surface` 모양 유지) |
| (z37) | 캐러셀 참조 ↔ 캡처 파일 ↔ `capture-shots.mjs` | 무변경·통과(SHOTS 안 건드림 — 캡처 alt 의 「운영자 콘솔」은 그래서 남음) |
| (z39) | 묶음 버튼이 상태마다 옳게 눌리는가 + 배포 창 정규화 | **넓힘**: 기대 열이 «열림/잠김» → **«무엇이 열렸나(start/stop/locked)»**. 상태 8→11(stopping·partial·running+unknown 추가), 전이 6칸 추가(끄기 누름→종료 중 잠금·서버 반영→해제·거절→해제·켜기 누름→기동 중 잠금·상한 만료→서버 값·모드 없음→요청 0), 요청 경로·본문 대조. 기대값 **하나 뒤집힘**(소유자 매트릭스): ready 잠김→끄기. unknown 은 **열림 유지**(§ CORRECTION). bite 3→6(전이 표시 제거·끄기=/stop·**확인 실패에서 켜기 잠금** 추가) |
| (z16) | 정적 칸이 `--live` 게이트에 갇히지 않았나 | 새 칸 (z41)(z42) 는 게이트 위 정적 구간 — 전체 실행에서 통과 |
| (z25) | 파이프 뒤 `grep -q` 금지 | 새 코드는 파이프 뒤에 `grepq` 사용 — 통과 |
| **(z41) 신설** | 전체 집계 배지·의존성 고지가 카드 상태/응답에서 파생되는가 + 「확인 실패」두 원인의 사유 구별 | 집계 11칸 + **전 조합 512건 스윕**(«모두 사용 가능» ⇔ 전부 ready, 어긋남 0) · 의존성 응답 3벌 · bite 3칸 |
| **(z42) 신설** | 람다 `BUNDLE_REQUIRED_DOMAINS`(ast 로 식 평가) = `projects.sh` `resolve_bundles`(실행) | 묶음 9개 전부 일치 · bite 2칸(람다에서 iam 제거 · DEPS 에 의존 추가) |

약화 0건: 삭제된 단언 없음. 뒤집힌 단언 2개(z34 store·z39 S6)는 기대값 변경이고 각각 더 강한 형태(모드까지)로 남았다.

## 구현 (`infra/demo/aws/site/index.html`)

- (a) `BLABEL` 을 `[배지, 클래스, 집계 범주]` 한 표로. 꺼짐 ⚪(waiting·selected) · 기동 중 🟡(booting·requested) · 사용 가능 🟢 · 종료 중 🔵 · 확인 실패 🔴(unknown — 배지·사유는 🔴, 켜기는 **열림**: § CORRECTION) · 첫 응답 전 🔵 상태 확인 중…(`B_PENDING`). `partial` 은 매트릭스에 칸이 없어 「🟠 일부만 실행 중」 그대로 두고 켜기 가능, 집계에서는 기동 중으로 셈. 「확인 실패」 상세 줄: `/bundles` 실패 = 기존 `B_ERR` 문구, 헬스 정체 = `unknownNote()`(서버가 준 `health_age_seconds` 를 읽음).
- (a) 더블클릭 방지: `bundleTransit` — 누른 순간 이 화면만 「기동 중…/종료 중…」 을 들고, 서버가 끝난 쪽 값을 주면 버림. 거절·네트워크 실패면 즉시 버림. 상한 `B_TRANSIT_MS`=180초(🔴 실측 아님 — 묶음 하나 종료 소요는 잰 적 없음).
- (b) `#aggbadge` → `[data-agg-badge]` 한 줄, 값은 카드별 집계 범주에서 `aggregateOf()` 로만 파생. 매트릭스 6값 + 보탠 3값(`🔵 상태 확인 중…` · `🔴 확인 실패`(전부 오류) · `🔵 모두 종료 중` · `🟡 모두 기동 중` — 전부가 그 상태일 때 「일부」라고 하면 거짓이라). EC2 줄의 「✅ 실행 중」 → 「✅ 인스턴스 실행 중 — 무엇을 쓸 수 있는지는 카드 배지를 보세요」.
- (c) 카드마다 `필요한 공용 서비스` (`data-deps`) — `/bundles` 의 `domains` **교집합**(모든 묶음이 필요로 하는 도메인)에서 파생. 페이지에 `iam` 리터럴 없음. 원장 일치는 (z42).
- (d) 콘솔 카드 제목 `Platform Console` + 「운영 콘솔」 부제, 구획(「서비스 화면」/「운영 도구」, 점선 테두리)으로 분리하고 카드를 뒤로 옮김. 고급 영역 도메인 목록은 표기·구획만(비즈니스 5 / 공용·기타 / 운영 도구 = Platform Console (운영 콘솔)) — 버튼·동작 무변경.
- (e) 버튼 `data-bundle-act` 하나가 `data-mode`(renderCards 만 정함)로 켜기=`/bundle/start`·끄기=`/bundle/stop`(`B_ACT_PATH`). 끄기는 `ready` 에서만, 구세대 AMI 거절(`bundleBlocked`)과 무관하게 열림(demo-down 은 옛 동작). 「다른 방문자가 쓰고 있을 수 있다」는 **툴팁으로만** — 카드 사유 칸(note)은 비어 있어야 «문제 없음» 이라는 (z34) 약속을 지키기 위해.
- `handler.py` **무변경** ⇒ 이 PR 은 `terraform apply` 불필요(Vercel 머지 배포만). `_bundle_state` 독스트링의 «론처의 unknown 이 startable 인 것과 같은 이유» 는 CORRECTION 뒤 다시 참이다.

## 검증

- `bash infra/demo/verify-demo-wrapper.sh` (정적, 전체, 스테이지 후) — **rc=0**, ok 90줄, FAIL 0, 마지막 줄 「정적 검증 PASS」. (z41)(z42) 포함.
- 구간 단독 실행: (z34) rc=0 · (z39) rc=0 · (z40)(z41)(z42) rc=0 · (t)(z11)(z14)~(z15) rc=0 · (z25) rc=0.
- AC-2 bite 실측(주입 확인 후 판정 출력):
  - (z39) bite-4 전이 표시 제거 → `[T1_STOP_CLICK] store 버튼이 stop 입니다 (기대 locked)`
  - (z39) bite-5 끄기=/stop → `[T1_STOP_CLICK] 끄기가 /stop 로 나갔습니다 — 그 묶음 한정 /bundle/stop 이어야 합니다`
  - (z39) bite-6 (CORRECTION 후) unknown 을 startable 에서 뺌 → `[S8_UNKNOWN] store 버튼이 locked 입니다 (기대 start)` · `[S11_UNKNOWN_RUN] store 버튼이 locked 입니다 (기대 start)` (카드 3장 × 2칸 = 6줄)
  - (z41) bite-1 하나라도 ready=전부 → `[ONE_READY] 전체 배지가 «전부 사용 가능» 과 같은 문구입니다 … 🟢 모두 사용 가능`
  - (z41) bite-2 공용 사본 `["iam"]` → `[D2] 응답에서 공용 도메인이 늘었는데 fan 의 고지가 안 따라옵니다`
  - (z41) bite-3 헬스 사유 삭제 → `[헬스 정체] 카드가 사유를 한 마디도 말하지 않습니다`
  - (z42) 람다 iam 제거 → `< console-ecommerce|ecommerce` · DEPS[fan]+=wms → `> fan|fan iam wms`
- AC-3/4/6 — 로컬 스텁 API(`DEMO_API_BASE`=로컬 서버, 실 제어 API 호출 0건) + Playwright(chromium), 1280/400px 스크린샷 8장면 × 2. 8개 서버 상태 전부 관측: pending(첫 응답 전)·waiting·selected→⚪ 꺼짐/켜기 · booting·requested→🟡 기동 중…/잠김 · ready→🟢 사용 가능/끄기 · partial→🟠/켜기 · stopping→🔵 종료 중…/잠김 · unknown(헬스 245초 정체)→🔴 확인 실패/잠김+«마지막 발행 245초 전» · `/bundles` 404→🔴 확인 실패/잠김+배포 안 됨 사유. 집계: 모두 꺼짐 / **store 만 ready → 「🟢 일부 서버 사용 가능」** / 전부 ready → 「🟢 모두 사용 가능」. 클릭: store 「데모 서버 끄기」 두 번 클릭 → POST 정확히 1건 `/bundle/stop {"bundles":["store"]}`, `/stop` 0건, 즉시 「종료 중…」 잠금, 1.5초 재폴링 뒤(서버 여전히 ready)에도 잠금 유지.
- AC-5 — (z42) 가 묶음 9개 전부 `BUNDLE_REQUIRED_DOMAINS` = `resolve_bundles` 대조; (z41) 이 «응답이 바뀌면 고지가 따라 바뀐다»(원장에 없는 `z41-shared` 가 나타나고, 공용이 없으면 iam 이 사라짐)로 사본 아님을 실행 증명.
- AC-7 — `handler.py` 무변경, 소유자 `terraform apply` **불필요**.

## 측정하지 못한 것

- 실제 제어 API·EC2 에 대고 켜기/끄기 — 비용 때문에 의도적으로 안 함. 묶음 하나 종료 실소요(전이 상한 180초의 근거)는 미측정.
- 실제 Vercel 서빙본 — 머지 후 `check-launcher-fresh.sh` 로 확인할 일.

## CORRECTION (2026-09-24 UTC, PR #3998 리뷰)

- **첫 구현이 `unknown`(🔴 확인 실패)에서 켜기 버튼을 잠갔다 — 리뷰가 되돌렸다.** 소유자 매트릭스의 잠금 요구는 **전이 상태**(기동 중… / 종료 중…)의 더블클릭 방지였고, 「확인 실패」는 전이가 아니다. 기존 설계 판단(index.html 의 «모르는 것은 «못 한다» 가 아니다» · `handler.py` `_bundle_state` 독스트링)대로, 잠그면 헬스 발행이 끊긴 동안 방문자가 아무것도 못 한다 — 기능 회귀였다.
- 되돌린 것: `B_STARTABLE` 에 `unknown` 복귀 + 근거 주석 복원. 배지 「🔴 확인 실패」와 원인 상세 줄은 그대로, 버튼은 「데모 서버 켜기」(열림).
- origin/main 과의 동등성: 켜기 조건식은 main 과 **글자 그대로 같다** — `CONTROL_OK && info !== null && !bundleBlocked && B_STARTABLE.has(st)`(main:727 · 현재 renderCards), `B_STARTABLE` 도 main 과 같은 4원소. 그래서 `/bundles` 를 못 받은 경로(`info === null`)는 main 처럼 잠기고((z34) 7개 오류 시나리오가 잠김을 단언, rc=0), 문구만 「상태 확인 실패」다. 응답이 있는 unknown 만 열린다.
- 가드: (z39) S8 기대 locked→start, **S11(running + unknown = 헬스 정체의 실제 자리) 추가**, bite-6 을 반대로 — unknown 을 빼면 S8·S11 이 빨개진다(위 실측). `handler.py` 무변경 유지.
- 따라서 PR 본문·첫 보고의 «deviation 1(unknown 잠금)» 은 **철회**됐다.
