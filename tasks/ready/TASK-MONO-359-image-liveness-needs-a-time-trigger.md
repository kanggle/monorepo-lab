# Task ID

TASK-MONO-359

# Title

이미지-실재 가드(h)가 **diff 트리거**에 달려 있어 자기가 막으려는 결함(레지스트리 외부 삭제)에는 **구조적으로 발화 불가** — time 트리거 부여

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- infra

---

# Goal

`TASK-MONO-353` 은 Docker Hub 에서 **삭제된** `bitnami/kafka:3.7` 이 scm/erp/fan 의 compose 를 전부 깨뜨린 사건을 고치면서, 재발 방지로 `infra/demo/verify-demo-wrapper.sh` 에 **가드 (h)** 를 넣었다 — *compose 가 참조하는 모든 레지스트리 이미지가 실제로 resolve 되는가*(`docker manifest inspect`). 가드 자체는 옳고, mutation 검증(죽은 이미지 복원 → FAIL)도 통과했다.

**문제는 가드가 아니라 가드의 트리거다.** 그 가드를 실행하는 유일한 CI 잡은 `ci.yml` 의 `demo-wrapper-smoke` 이고, 그 잡의 게이팅은:

```yaml
demo-wrapper: ${{ steps.filter.outputs.demo-wrapper == 'true'
                  && steps.filter.outputs.code-changed == 'true' }}   # ci.yml:164
demo-wrapper-smoke:
  if: needs.changes.outputs.demo-wrapper == 'true'                    # ci.yml:371
```

즉 **우리 diff 가 `infra/demo/**` · `infra/traefik/**` · `projects/*/docker-compose.yml` 중 하나를 건드릴 때만** 돈다(게다가 `code-changed` AND 까지 걸려 있다).

그런데 가드 (h) 의 자기 주석이 그 결함의 성질을 이렇게 못박는다:

> *"이 결함의 성질이 중요하다: **우리 커밋과 무관하게 외부에서 깨진다.** 따라서 **어떤 diff-기반 검사로도 잡히지 않는다.**"* — `infra/demo/verify-demo-wrapper.sh:189-190`

**diff 없이 도착하는 결함을 막는 가드를 diff 트리거에 달아 놓았다.** Docker Hub 가 내일 `apache/kafka:3.9.0` 이나 `redis:7-alpine` 을 지우면 우리 레포의 diff 는 0 → 필터 false → 잡 skip → CI 초록 → 데모 사망. **`bitnami/kafka` 때와 정확히 같은 침묵이 재현되고, 이번엔 가드가 옆에서 발화하지 못한 채 서 있다.**

가드 (h) 가 **지금 잡을 수 있는 것**은 *우리 diff 가 죽은 이미지를 새로 들여오는* 경우뿐이다(= MONO-353 의 mutation 테스트가 증명한 바로 그 시나리오). 그것도 가치 있지만, **실제로 데모를 죽인 시나리오는 여전히 무방비**다.

**diff-less 결함에 대한 가드는 path 트리거가 아니라 TIME 트리거를 요구한다.** 이 task 는 가드 (h) 에 시계를 달아 준다.

---

# Scope

## In Scope

- `nightly-e2e.yml` 에 신규 잡 **`demo-image-liveness`** 추가 — `verify-demo-wrapper.sh` 를 정적 모드(`--live` 없음)로 실행한다. `nightly-e2e.yml` 은 이미 `schedule`(cron `0 18 * * *`) + `push:[main]` + `workflow_dispatch` 를 갖고 있어 **시계·머지·수동** 세 경로가 한 번에 생긴다.
- 잡은 checkout + 스크립트 실행뿐. **아무것도 기동하지 않는다**(compose `config` 렌더 + 이미지당 `docker manifest inspect`).
- **`--require-coverage` 플래그 신설**(아래 § 착수 후 발견 참조) — 가드 (h) 가 **한 건도 확인하지 못한 실행**을 합격으로 처리하지 않는다. **nightly 만** 켠다.

## 착수 후 발견 — 시계를 다는 것만으로는 부족하다 (스코프 확장의 근거)

로컬에서 스크립트를 실제로 돌려 보고 알았다: `docker manifest inspect` 가 **레지스트리 익명 pull 레이트리밋**(`toomanyrequests`)에 걸리면, 가드 (h) 는 그 이미지를 **skip 으로 세고 그대로 통과**한다. 극단적으로 **전부 skip 되면 (h) 는 아무것도 확인하지 않고 exit 0** 한다.

그 관용 자체는 **옳다** — MONO-353 이 명시한 대로 *"flaky 한 가드는 결국 꺼진다"* 이고, 외부 레이트리밋이 **PR 머지를 막아서는 안 된다.**

그러나 그 관용을 **시계 위에 그대로 올리면**, 이 task 가 없애려던 바로 그 병리를 다시 만든다: **매일 밤 아무것도 보지 않고 초록을 켜는 등대.** 가드에 도달 경로를 준 것으로 끝내면, "가드가 돌았다"와 "가드가 무언가를 봤다"를 혼동하게 된다.

→ 그래서 **호출자가 커버리지를 요구**하게 한다. PR 잡은 관용을 유지하고(머지 차단 금지), **시계가 달린 잡만 `--require-coverage`** 로 "커버리지 0 = 무신호 = FAIL" 을 요구한다. 개별 이미지의 판정(확정 부재만 FAIL)은 **손대지 않는다** — 그 위에 얹은 *'이번 실행이 무언가를 보긴 했는가'* 단언이다.

## Out of Scope

- **`ci.yml` 의 `demo-wrapper-smoke` 제거·완화·플래그 추가 금지.** PR-time 에 *우리 diff* 로 들어오는 죽은 참조를 빠르게 잡는 역할은 그대로 유효하다. 이 task 는 **도달 범위를 넓히는 것**이지 대체가 아니다. PR 경로에는 `--require-coverage` 를 **켜지 않는다**(외부 레이트리밋이 머지를 막게 된다).
- **`ci.yml` 에 `schedule:` 추가 금지.** `dorny/paths-filter` 는 schedule 이벤트에서 비교 base 가 없어 필터 의미가 정의되지 않는다(전 잡 오-발화 위험). 시계는 이미 시계를 가진 워크플로에 붙인다.
- 가드 (h) 의 **개별 이미지 판정 로직 변경 금지** — 레이트리밋 skip vs 확정 부재 FAIL 구분, `build:` 서비스 제외는 MONO-353 이 근거를 갖고 설계한 것이다.
- **인증 pull(`docker/login-action`) 도입은 이번 범위 밖** — Docker Hub 토큰 시크릿은 사람이 만들어야 한다. 레이트리밋 skip 이 빈번해지면 그때 별도 후속으로(§ Failure Scenarios F1).
- 스크립트를 이미지 검사 전용 모드로 쪼개는 것(`--images-only` 등) — 정적 모드 전체가 이미 싸고, 나머지 드리프트 가드(a~e, g)도 시계를 얻는 것이 **순이득**이다.

---

# Acceptance Criteria

- [ ] **AC-1** `nightly-e2e.yml` 에 `demo-image-liveness` 잡이 존재하고, `verify-demo-wrapper.sh` 를 정적 모드로 실행한다. repo-guard(`github.repository == 'kanggle/monorepo-lab'`)를 갖는다(추출 포트폴리오 레포는 skip).
- [ ] **AC-2 (도달성 — 이 task 의 본질)** 가드 (h) 가 **레포 diff 없이도** 실행되는 경로가 존재한다. `demo-wrapper` 필터의 입력은 **전부 레포 경로**(`infra/demo/**`·`infra/traefik/**`·`projects/*/docker-compose.yml`)인데, 레지스트리에서 이미지가 사라지는 사건은 **그중 어느 것도 바꾸지 않는다** → 필터 false → 잡 skip. 즉 **어떤 paths-filter 조합으로도 도달 불가**이며, 이는 튜닝으로 고칠 수 없고 **트리거의 종류**를 바꿔야 한다. 실증은 이미 있다: MONO-353 의 compose 3개가 깨진 채로 **CI 는 계속 초록이었다.** 머지 후 push/cron 이 발화한 nightly 에서 `demo-image-liveness` 가 **실행됨**을 확인한다.
  - 주의: **이 PR 은 `infra/demo/verify-demo-wrapper.sh` 를 수정하므로 `demo-wrapper-smoke` 가 (정상적으로) 실행된다.** 그 실행은 스크립트 변경의 회귀 검증이지, 도달성 주장의 반례가 아니다 — 도달 불가는 *diff 가 0 인 사건*에 대한 것이다.
- [ ] **AC-3** `ci.yml` 의 `demo-wrapper-smoke` 는 무변경(PR-time 조기 검출 유지, 관용 유지).
- [ ] **AC-4 (무신호 ≠ 합격)** `--require-coverage` 로 실행하면 **확인 건수 0** 인 실행이 FAIL 한다. **mutation 으로 증명한다**: `docker manifest` 를 전량 레이트리밋으로 위조 → (a) 플래그 없는 실행은 여전히 PASS(관용 유지 확인), (b) `--require-coverage` 실행은 FAIL. 두 방향을 모두 확인해야 한다 — FAIL 만 보면 "언제나 FAIL 하는 가드"와 구별되지 않는다.
- [ ] **AC-5** 커버리지가 로그에 **수치로** 남는다(`N/M 확인, skip K`). skip 은 "이 이미지는 멀쩡하다"가 아니라 **"모른다"** 임이 메시지에 드러난다.
- [ ] **AC-6** 머지 후 nightly 1사이클에서 `demo-image-liveness` GREEN 이고, 그 로그의 커버리지가 **0/M 이 아님**을 확인(즉 실제로 무언가를 봤다).

---

# Related Specs

- `tasks/done/TASK-MONO-353-bitnami-kafka-dead-image.md` — 가드 (h) 를 도입한 task(그 가드의 트리거가 이 task 의 대상)
- `tasks/done/TASK-MONO-341-demo-wrapper-ci-smoke.md` — `demo-wrapper-smoke` 잡의 출처
- `tasks/done/TASK-MONO-345-docs-service-map-drift.md` — **가드는 자기가 단속하는 변경에 도달할 수 있어야 한다**는 동일 계열 교훈(거기서는 `code-changed` 와 AND 하면 docs-only diff 를 놓친다는 형태였다. 이 task 는 그 교훈의 한 겹 더 깊은 판본: 변경이 **diff 자체를 남기지 않는다** → 어떤 paths-filter 로도 도달 불가)
- `tasks/done/TASK-MONO-074/075` — paths-filter negation quirk(필터 정의는 손대지 않는다)
- Memory `project_untickected_backlog_sweep_2026_07_11` — "가드 없는 표면은 드리프트한다" + "가드는 mutation 을 주입해 실제로 무는지 확인"

# Related Skills

N/A — CI YAML.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — CI 트리거 배치. ADR 없음.

---

# Implementation Notes

- 정적 모드(`--live` 없음)는 가드 (a)~(e), (g), (h) 를 돌고 `exit 0` 한다(`verify-demo-wrapper.sh:245-248`). `--live`(실기동 공존 증명)는 PR-time `demo-wrapper-smoke` 에 남겨 둔다 — nightly 에서 컨테이너를 띄울 이유가 없다.
- **레이트리밋**: Docker Hub 익명 한도는 IP 당 100/6h 이고 GH 러너는 IP 를 공유한다. 스크립트는 이미 *확정적 부재*(manifest unknown / not found)에만 FAIL 하고 레이트리밋·네트워크 오류는 skip 으로 세어 출력한다. 이 설계를 바꾸지 말 것 — skip 을 FAIL 로 바꾸면 가드가 flaky 해지고, **flaky 한 가드는 결국 꺼진다.**
- nightly 는 PR 에서 돌지 않는다 → 이 잡의 런타임 검증은 **머지 후**다(push-to-main 이 `nightly-e2e.yml` 을 발화). PR 에서는 `bash -n` 문법 검사 + js-yaml 파싱만 가능. 선례: MONO-326 Phase 3b.

---

# Edge Cases

- **`ci.yml` 에 시계를 달고 싶은 유혹** — paths-filter 가 schedule 이벤트에서 base 없이 어떻게 동작하는지 정의되지 않았다. 전 잡 오-발화/오-skip 위험. Out of Scope 로 명시.
- **"nightly 가 이미 도니 괜찮다" 오해** — nightly 의 기존 잡은 전부 **Testcontainers 기반**이라 scm/erp/fan 의 `docker-compose.yml` 을 **한 번도 실행하지 않는다**. MONO-353 이 오래 방치된 근본 이유가 정확히 이것이다. 새 잡이 필요한 이유.
- **skip 건수 무시 금지** — skip 이 많으면 그만큼 커버리지가 빈 것이다. 조용한 truncation 은 "전부 확인했다"로 오독된다.

---

# Failure Scenarios

- **F1 — 레이트리밋으로 nightly 가 자주 노랗다** → 가드 신뢰도 하락 → 결국 꺼짐. 완화: 확정 부재에만 FAIL(현행 설계 유지). 재발 시 `docker/login-action` 으로 인증 pull 한도 상향을 별도 후속으로.
- **F2 — 새 잡이 nightly 전체를 느리게 만든다** → 실제로는 수 초(부팅 없음). 다른 잡과 병렬이며 `needs:` 없음.
- **F3 — 가드가 죽은 이미지를 잡았는데 아무도 안 본다** → nightly RED 는 main 배지를 붉힌다(ADR-MONO-011 § 6.1 의 알림 자동화는 여전히 미결 — 이 task 의 범위 밖이나, 그 미결이 이 가드의 실효를 제한한다는 사실은 기록해 둔다).

---

# Test Requirements

- PR: `bash -n infra/demo/verify-demo-wrapper.sh` + 워크플로 YAML 파싱(js-yaml) + `demo-wrapper-smoke`(이 PR 이 `infra/demo/**` 를 건드리므로 실행됨 → 스크립트 변경의 회귀 검증).
- 머지 후: nightly 1사이클에서 `demo-image-liveness` 실행 + GREEN + 커버리지 ≠ 0/M.

## ✅ 로컬 mutation 결과 (2026-07-12, 실기 검증 완료)

`docker manifest` 만 위조하는 PATH 스텁으로 4방향을 전부 확인했다(`docker compose` 는 진짜 통과 — 가드 (a)~(g)는 실제 compose 를 본다). **네 칸을 모두 채워야 의미가 있다**: FAIL 만 보면 "항상 FAIL 하는 가드"와 구별되지 않고, PASS 만 보면 무는지 알 수 없다.

| # | manifest 조회 | 플래그 | 커버리지 | exit | 확인하는 것 |
|---|---|---|---|---|---|
| A | 전량 rate-limit | 없음 | 0/16 | **0 PASS** | **PR 관용이 보존됐다** — 외부 레이트리밋이 머지를 막지 않는다 |
| B | 전량 rate-limit | `--require-coverage` | 0/16 | **1 FAIL** | **무신호 ≠ 합격** — 시계 잡이 "아무것도 안 보고 초록"을 거부한다 |
| C | 전량 성공 | `--require-coverage` | 16/16 | **0 PASS** | **항상-FAIL 가드가 아니다**(= nightly 가 영구 RED 가 되지 않는다) |
| D | 죽은 이미지 주입 | `--require-coverage` | — | **1 FAIL** | 가드 (h) **본연의 임무**(삭제 검출)가 새 명령에서도 살아 있고, **커버리지 실패(B)와 다른 메시지**로 구별된다 |

A 는 이 task 의 근거 그 자체다 — **가드가 16개 중 0개를 확인하고 exit 0 했다.** 그 상태를 시계 위에 그대로 올렸다면, 매일 밤 아무것도 안 보고 초록을 켰을 것이다.

(주입은 `git checkout` 으로 되돌렸고 워크트리 clean 확인. **Windows 체크아웃에서 `sed -i` 금지** — 매치가 없어도 CRLF 를 재작성해 파일이 modified 로 뜬다.)

---

# Definition of Done

- [ ] `demo-image-liveness` 잡 추가, `ci.yml` 무변경.
- [ ] 로컬 정적 실행 PASS + mutation FAIL 확인.
- [ ] 머지 후 nightly 1사이클 GREEN 관측.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-12 — 정식 큐가 전부 게이트 뒤에 있는 상태에서 "어떤 게이트가 이미 열렸는가"를 점검하다, 전날(2026-07-11) 머지된 MONO-353 의 가드 (h) 가 **자기 주석이 선언한 결함 성질과 모순되는 트리거**에 달려 있음을 발견. 가드는 존재하고 mutation 에도 물지만, **현실의 도착 경로(외부 삭제 = diff 0)에서는 발화 자체가 불가능**하다. 미티켓 백로그 스윕(2026-07-11)이 남긴 교훈 — *"가드 없는 표면은 드리프트한다"* + *"가드는 실제로 무는지 확인하라"* — 의 다음 판본: **가드가 물 수 있는지뿐 아니라, 물 기회를 얻는지도 확인해야 한다.**

분석=Opus 4.8 / 구현 권장=Sonnet (판단은 종료; CI YAML 추가 + shell 무변경).
