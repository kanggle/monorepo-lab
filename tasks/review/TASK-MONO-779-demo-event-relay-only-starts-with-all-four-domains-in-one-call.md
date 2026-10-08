# Task ID

TASK-MONO-779

# Title

데모 이벤트 릴레이가 **한 번의 기동 호출에 iam·ecommerce·wms·scm 넷이 다 있을 때만** 뜬다 — 도메인을 나중에 더하면(`/domain/start` · 묶음 추가) 릴레이 없이 조용히 돈다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — `demo-up.sh` 릴레이 블록과 `demo-down.sh` 대칭부, 가드 한 칸. 판단은 «넷이 모일 때마다 띄운다» 하나다.

---

# Dependency Markers

- 출처: 24차 데모 창(2026-10-08 UTC, `ami-01f1b4b56e4f9e51a`) — `TASK-MONO-765` AC-3 측정 중 발견.
- 선행 기록: `TASK-MONO-760`(done) § 정적 분석 표 «🔴 릴레이 — 기동 | 창 의존» 이 코드에서 **추론**만 했던 것을 이 창이 **관측**했다.
- 관련: `TASK-MONO-511`(릴레이 도입) · `ADR-MONO-062` B · `TASK-MONO-553` A(릴레이 격리).

# Goal

`infra/demo/demo-up.sh:363-381` 은 **한 번의 호출**에 `RELAY_DOMAINS`(`projects.sh:106` — iam ecommerce wms scm)가 모두 있어야 릴레이(`docker-compose.relay.yml`)를 띄운다. 묶음 기동(`/bundle/start`)은 방문자가 고른 묶음만 올리므로 기본 부팅에 scm 이 빠지는 일이 흔하고, 그 뒤 `/domain/start scm` 이나 묶음 추가로 넷이 **모여도** 릴레이는 뜨지 않는다. 그동안 크로스프로젝트 이벤트(iam→ecommerce 계정 · ecommerce→wms 풀필먼트 · wms→ecommerce · wms→scm · scm→wms)는 **한 건도 건너가지 않고 에러도 없다**.

24차 창 관측(2026-10-08 UTC):

| 시각 | 사실 |
|---|---|
| 15:20 부팅 | 선택 = iam · ecommerce · wms → 데모 로그 «⚠ 이벤트 릴레이 생략 — 이번 기동에 없는 도메인: scm» |
| 16:11 · 17:5x | `/domain/start erp` · `/domain/start scm` — 넷이 모였지만 릴레이 없음(`docker ps -a` 에 `demo-event-relay` 0) |
| 18:1x | 시드 주문 3건의 `ecommerce.fulfillment.requested.v1` = ecommerce-kafka **3** · wms-kafka **0** |
| 18:14 | 소유자가 SSM 으로 `docker compose -p relay -f infra/demo/docker-compose.relay.yml up -d` → 1분 안에 wms-kafka 3 · 소비 lag 0 · 출고 주문 3건 생성 |
| 18:32 재기동 | 묶음 `console · store · store-fulfillment · console-wms · console-scm` 로 켜자 부팅 로그에 릴레이가 같이 떴다(대조군) |

🔴 이 상태는 **다른 결함처럼 보인다** — 765 AC-3 를 «DLT 로 안 갔다 = 통과» 로 읽을 뻔했다(요청이 wms 에 **도착하지 않아서** 안 간 것).

# Scope

## In Scope

- 도메인을 올리는 모든 경로(`demo-up.sh <set>` · `domain_start` → `demo-boot.sh <domain>` · 묶음 추가)가 끝날 때 **지금 떠 있는 도메인 집합**을 보고 넷이 모였으면 릴레이를 올린다(이미 떠 있으면 no-op — `up -d` 는 멱등).
- `demo-down.sh:72-82` 의 대칭: 넷 중 하나를 내려 릴레이를 내렸다가 다시 올리면 릴레이도 다시 뜬다.
- 정적 가드 한 칸(데모 래퍼 스모크): «릴레이 기동 판정이 이번 호출의 `SET` 이 아니라 **떠 있는 도메인**을 본다» 를 문장 단위로 고정.

## Out of Scope

- 릴레이 healthcheck 가 `iam-kafka` 만 보는 것(`docker-compose.relay.yml:51`) — 별도 판단.
- 릴레이가 넷 미만에서도 부분적으로 도는 구조(external 네트워크 참조라 불가 — `demo-up.sh` 주석).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정(file:line, 구현 뒤 기준):

  | 무엇 | 파일:줄 |
  |---|---|
  | 릴레이를 띄우는 곳(판정 + `up -d`) | `infra/demo/demo-up.sh:372-396`(판정은 `372-379`, `GUARD-Z44` 앵커 구간) |
  | 판정이 보는 술어(단일 출처) | `infra/demo/projects.sh:288-306` `domain_running()` |
  | 릴레이를 내리는 곳 | `infra/demo/demo-down.sh:67-82`(부분 종료라도 네 도메인 중 하나면 먼저 내림) |
  | 부분 종료 잔존 가드가 같은 술어를 쓰는 곳 | `infra/demo/demo-down.sh:57` `domain_running "$r"` |
  | `domain_start` 가 부르는 경로 | `infra/demo/aws/terraform/lambda/handler.py:959-974 domain_start()` → SSM `bash demo-boot.sh <name>` → `infra/demo/demo-boot.sh:260 exec demo-up.sh "$@"` → 위 판정 |

  (착수 시의 **고침 전** 판정은 `demo-up.sh:363-366`(이 티켓 발견 시점, `[[ " ${SET[*]} " == *" $d "* ]]`)였다 — Goal 절의 24차 창 관측이 그 상태를 가리킨다.)

- [x] **AC-1** — In Scope 1·2 구현: `projects.sh` 에 `domain_running()` 단일 출처를 두고, `demo-up.sh` 의 릴레이 판정이 `SET` 대신 그것을 본다(`demo-down.sh` 의 기존 `is_running()` 중복도 같은 함수로 정리). 셸 단위 시험(`verify-demo-wrapper.sh` (z44), `demo-up.sh` 의 `GUARD-Z44` 구간을 **그대로 추출**해 실행) 으로 넷이 모이는 순서 세 가지를 검증: 한 번에(SET={iam,ecommerce,wms,scm}) · 나중에 scm(SET={scm,iam}, 실제로는 넷 다 up) · 나중에 iam(SET={iam}, 실제로는 넷 다 up) — 셋 다 `relay_missing` 이 빈 상태로 나왔다(SET 과 무관).
- [x] **AC-2** — 같은 (z44) 안에서 scm 만 실제로 안 떠 있는 시나리오(SET 은 넷 전부지만 running={iam,ecommerce,wms})를 돌려 `relay_missing=[scm]` 을 확인했다 — 생략 경고(`demo-up.sh:389` `⚠ 이벤트 릴레이 생략`)는 그대로 살아 있고 이름을 정확히 댄다.
- [x] **AC-3** — bite 1회: (z44) 가 추출한 판정 구간을 `sed` 로 옛 식(`[[ " ${SET[*]} " == *" $d "* ]]`)으로 되돌려 "나중에 scm" 시나리오를 다시 돌리면 `relay_missing=[ecommerce wms]`(비어있지 않음) — 가드가 **물었다**. 되돌리기 전(현재 코드)에는 같은 시나리오가 비어 있었다. (로컬에서 동일 추출·실행 로직으로 직접 재현 — 아래 구현 기록의 "bite 재현" 참조. docker 데몬이 없는 로컬 환경이라 전체 `verify-demo-wrapper.sh`(docker compose render 를 쓰는 앞선 칸들 포함)를 처음부터 끝까지는 못 돌렸다 — CI 의 `demo-wrapper-smoke` 잡이 전체 실행을 검증한다.)
- [ ] **AC-4** — ⚪ 재굽기 창: 기본 묶음으로 부팅 → `/domain/start scm` → `demo-event-relay` healthy · 시드 풀필먼트가 wms 로 건너감. 라이브 AWS 데모 호스트 + AMI 재굽기가 필요해 이 구현 세션에서는 측정하지 못했다 — 다음 데모 창에서 소유자가 관측해야 한다.

# Related Specs

- `infra/demo/demo-up.sh` · `infra/demo/demo-down.sh` · `infra/demo/projects.sh` § RELAY · `infra/demo/docker-compose.relay.yml` · `infra/demo/relay/mm2.properties`

# Related Contracts

- 없음(데모 배선).

# Edge Cases

- 릴레이가 이미 떠 있을 때 다시 `up -d` — 컨테이너 재생성 없이 no-op 이어야 한다(MM2 오프셋 유지).
- 넷 중 하나가 unhealthy 로 떠 있는 동안 — 지금 규칙과 같게(`failed+=relay` 후 계속).

# Failure Scenarios

1. 판정을 이번 호출의 `SET` 으로 남긴다 — 이 티켓의 결함 그대로.
2. 릴레이를 매번 재생성한다 — MM2 가 처음부터 다시 복제해 소비자 dedupe 에 짐을 지운다.
3. 내릴 때만 고치고 올릴 때를 안 고친다 — 내림·올림 한 바퀴 뒤 다시 침묵.

# Implementation Record (2026-10-08 UTC)

## 변경

- `infra/demo/projects.sh` — `domain_running()` 신설(단일 출처): `docker ps -aq --filter
  label=com.docker.compose.project=<slug>` 로 "그 compose 프로젝트에 컨테이너가 하나라도
  있는가"를 묻는다. `-a`(전 상태)를 쓰는 이유는 주석에 적었다 — healthcheck 가 아니라
  "아직 안 내려갔는가"를 재는 것이 세 호출자 모두의 의도이기 때문이다(TASK-MONO-551 A 가
  같은 함정을 다른 자리에서 겪었다).
- `infra/demo/demo-up.sh` — 릴레이 기동 판정을 `[[ " ${SET[*]} " == *" $d "* ]]`(이번 호출의
  요청 집합) 에서 `domain_running "$d"`(지금 떠 있는 상태) 로 교체. 가드 추출용 앵커
  `# GUARD-Z44-BEGIN`/`-END` 를 그 구간에 둘렀다.
- `infra/demo/demo-down.sh` — 자체 정의였던 `is_running()` 을 제거하고 같은 질문을
  `projects.sh` 의 `domain_running()` 으로 옮겼다(부분 종료 잔존 가드 + 릴레이 선종료
  판정, 두 자리 모두). 동작은 바뀌지 않는다 — 정의가 한 곳으로 모였을 뿐이다.
- `infra/demo/verify-demo-wrapper.sh` — 정적 가드 (z44) 신설. `demo-up.sh` 의
  `GUARD-Z44` 구간을 **그 파일에서 그대로 추출**해(손으로 재작성하지 않음)
  `domain_running` 을 스텁으로 치환한 서브셸에서 실행 — docker 데몬 없이 돈다(LIVE
  게이트 밖, 정적 구간). AC-1(세 순서 모두 missing 없음) · AC-2(scm 만 안 뜬 경우
  missing=[scm]) · AC-3(bite — 옛 식으로 되돌리면 missing=[ecommerce wms]) 를 한 칸에서 잰다.

## 검증

- `bash -n`: `demo-up.sh` · `demo-down.sh` · `projects.sh` · `demo-boot.sh` ·
  `verify-demo-wrapper.sh` 전부 rc=0.
- (z44) 로직을 `demo-up.sh` 의 실제 `GUARD-Z44` 구간(파일에서 그대로 추출)으로 로컬
  재현 — 결과:
  - 한 번에 / 나중에 scm / 나중에 iam → 셋 다 `relay_missing=""`.
  - scm 만 실제로 안 뜬 경우(SET 은 넷 전부) → `relay_missing="scm"`.
  - bite(옛 `SET` 식으로 되돌린 스니펫, "나중에 scm" 시나리오) → `relay_missing="ecommerce wms"`
    (비어있지 않음 — 가드가 물었다).
- `verify-demo-wrapper.sh` 를 **처음부터 끝까지**(`--live` 포함) 로컬에서는 못 돌렸다 —
  이 머신에 docker 데몬이 없다(`docker ps` → `open //./pipe/docker_engine: ...`).
  정적 구간 (a)~(g) 는 데몬 없이도 돌아 통과를 직접 확인했고(`docker compose config` 는
  데몬을 안 쓴다), (h)(레지스트리 조회)부터는 네트워크 의존이라 타임아웃— (z44) 자체는
  그 앞에서 독립적으로(위 "로컬 재현") 검증했다. CI 의 `demo-wrapper-smoke` 잡이
  `--live` 전체 실행을 검증한다.
- 레포 가드 3종(모두 `git add` 뒤 측정, rc 는 아래 보고 참조): `check-index-queue-drift.sh` ·
  `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh`.
- `scripts/` 아래 파일 추가/삭제 없음 — 전체 가드 스윕 규칙은 적용 대상 아님.

## 못 한 것

- AC-4(라이브 재굽기 창) — AWS 데모 호스트 + AMI 재굽기가 필요해 이 세션에서 측정 불가.
  다음 데모 창에서 소유자가 관측.
- `verify-demo-wrapper.sh --live` 전체 실행 — 로컬에 docker 데몬 없음. (z44) 자체는
  데몬 없이 돌므로 영향 없음(위 참조).
