# Task ID

TASK-MONO-534

# Title

도커가 한 번 재시작되면 **데모의 IdP 가 돌아오지 않는다** — iam 앱 5개에 `restart:` 가 없다

# Status

in-progress

# Owner

monorepo

# Task Tags

- infra
- demo
- resilience

---

# 🔬 실측 (2026-08-15, 로컬 데모 가동 중 Rancher Desktop 재시작)

호스트의 도커 컨트롤 플레인이 고갈돼 VM 을 재시작했다. 볼륨은 보존되므로 컨테이너가
스스로 복귀할 것으로 보았고, **대부분은 실제로 복귀했다** — 그러나 갈렸다:

| 스택 | 복귀 |
|---|---|
| ecommerce | **33 / 33** ✅ |
| platform-console | **2 / 2** ✅ |
| traefik | **1 / 1** ✅ |
| iam **인프라** (mysql · redis · kafka · grafana · prometheus · loki · promtail · alertmanager · kafka-ui) | **9 / 9** ✅ |
| **iam 앱** (auth · gateway · admin · account · security) | **0 / 5** ❌ |

**크래시가 아니다.** `docker inspect iam-auth-service-1` → `ExitCode=255 · OOMKilled=false ·
restart=no`, `FinishedAt` 이 VM 을 내린 그 시각이다. 로그 마지막 줄들도 종료 직전의
Redis health 경고일 뿐 기동 실패가 아니다. **재시작 정책이 없어서 안 돌아온 것이다.**

## 왜 갈렸나 — 앱은 오버레이에만 산다

`projects.sh` 가 적어 둔 두 패턴 중 **패턴 1**(iam · wms)은 `base`(인프라) +
`docker-compose.e2e.yml`(앱) 을 함께 줘야 앱이 뜬다. 그런데:

- `base` 의 인프라 서비스는 `restart: unless-stopped` 를 갖고 있고, 오버레이가 같은 키를
  다시 선언해도 그 값은 **병합으로 살아남는다** ⇒ 인프라 9개가 복귀했다.
- **앱 서비스는 오버레이에만 정의된다** ⇒ 상속받을 `restart:` 가 없다.

영향받는 것은 **iam 앱 5개뿐**이다:

| 프로젝트 | 앱 서비스의 **실효** `restart` (병합 결과) |
|---|---|
| `iam` (auth · account · security · admin · gateway) | **없음** ⇒ 이 티켓의 대상 |
| `wms` (gateway · master · inbound · inventory · outbound · notification · admin) | **`unless-stopped`** — `x-wms-app-common` **앵커**가 갖고 있다 ⇒ 대상 아님 |

`kafka-init` 은 양쪽 다 `restart: "no"` 를 **명시**한다(일회성 작업이므로 옳다). 즉 이 파일들은
이 필드를 모르는 것이 아니고, **iam 의 앱만 아무 값도 갖지 않는다.**

## 🔴 이 표는 한 번 틀렸다 — 그 오류 자체가 AC-0 의 근거다

최초 작성 시 **"iam 5 + wms 7 = 12개"** 로 적었다. 서비스 블록을 파싱해 `restart:` 줄을 센
것이었고, **`<<: *wms-app-common` 앵커로 상속되는 값을 보지 못했다.** wms 앱 7개는 처음부터
정상이었다(라이브로도 확인된다 — 메모리가 부족한 동안 wms 앱들이 `RestartCount=7` 로
**실제로 재시작되고 있었다**. 재시작 정책이 없었다면 한 번 죽고 끝이다).

⇒ **선언 파일을 세면 틀린다.** 판정은 `docker compose config` 의 **병합 결과**여야 한다.
AC-0 이 그것을 요구하는 이유는 이 티켓 본인이 그 함정에 먼저 빠졌기 때문이다.

## 왜 이게 데모에서 나쁜가

안 돌아온 것이 하필 **이 모노레포의 OIDC IdP** 다. `projects.sh` 헤더가 이미 이름 붙여 둔
실패 모드가 정확히 이것이다 — ***"96 컨테이너가 전부 healthy 로 떠도 로그인이 불가능"***
(`MONO-358`). 게이트웨이도 프런트도 스토어도 전부 초록인데 **로그인만 안 된다.**

그리고 이 상황은 데모에서 드물지 않다: 노트북 절전/재부팅, Docker Desktop·Rancher 재시작,
호스트 업데이트 — 전부 같은 결과를 낸다. 면접 중이나 면접 직전에 밟기 딱 좋은 자리다.

🔵 워크스루 §6 한계 대장에 이 항목은 **없다**(재시작을 언급하는 두 행은 창고 스택 볼륨
비밀번호 건과 admin-service 시드 건으로, 둘 다 다른 사안이다).

# Goal

데모 스택이 **도커/호스트 재시작을 넘겨서 살아남게** 한다. 판정은 선언이 아니라
**복귀했는가**로 한다.

# Scope

**In scope**

- **데모 전용 오버레이**(`infra/demo/iam-traefik.override.yml`)에 **iam 앱 5개**의
  `restart: unless-stopped` 를 얹는다.
  🔵 그 오버레이는 **이미 이 5개를 전부 재선언하고 있다**(`gateway`·`auth`·`account`·
  `admin`·`security`) — 새 파일도, 새 서비스 블록도 필요 없다.
  🔵 **모범 답안이 형제 프로젝트에 이미 있다**: `x-wms-app-common` 앵커가 `restart:
  unless-stopped` 를 담고 앱 7개가 그것을 머지한다. iam 도 같은 모양으로 갈지, 오버레이에
  서비스별로 얹을지만 정하면 된다(오버레이 쪽이 CI 파일을 안 건드린다는 점에서 유리하다).
- 재시작 정책이 **효과적으로** 붙었는지 병합 결과에서 확인(`docker compose config`).
- 실제 복귀 실측(AC-2).

**Out of scope**

- 🔴 **`docker-compose.e2e.yml` 자체를 고치는 것.** 그 파일은 **CI 하네스**이고, CI 에서는
  재시작이 없는 것이 **옳다** — 앱이 죽으면 런이 실패해야 하지 조용히 되살아나면 안 된다.
  거기에 `unless-stopped` 를 넣으면 **CI 가 크래시를 재시작으로 가리게 된다.** 이것이 이
  티켓에서 가장 위험한 오수정이다.
- **ecommerce · console** — 라이브 복귀를 실측했다(33/33 · 2/2). 건드리지 않는다.
  🔵 **`scm`·`fan`·`finance`·`erp` 는 "범위 밖"이 아니라 "아직 안 쟀다"** — 이번 기동에
  떠 있지도 않았다. AC-0 이 재는 대상이고, 거기서 빠진 것이 나오면 그때 범위에 들어온다.
  *"패턴 2 니까 괜찮다"* 는 추론이지 측정이 아니다.
- **일회성 init 컨테이너** — `kafka-init` 류에 `unless-stopped` 를 붙이면 무한 재실행이 된다.

# Acceptance Criteria

**AC-0 — 모집단을 다시 센다 (verify-then-act).**
`docker compose -p <slug> <-f …> config` 로 **8개 도메인 전부**의 서비스별 **실효** restart
정책을 뽑아 표로 만든다. 위 표는 iam·wms 두 도메인만 그렇게 잰 것이고, **나머지 6개는
아직 안 쟀다**(패턴 2 라서 괜찮을 것이라는 것은 추론이지 측정이 아니다 — ecommerce·console
33+2 는 라이브 복귀로 확인됐지만 scm·fan·finance·erp 는 이번에 떠 있지도 않았다).
🔴 **선언 파일을 세면 틀린다** — 두 경로로 틀린다: ① base 의 값이 오버레이 병합으로
살아남고(iam 인프라 9개가 그래서 복귀했다) ② **YAML 앵커로 상속된다**(wms 앱 7개).
이 티켓이 ②에 먼저 걸려 대상을 12개로 과다 집계했다.

**AC-1 — 데모 오버레이에 얹고, 효과를 병합 결과에서 확인한다.**
`config` 출력에서 대상 앱 서비스가 `restart: unless-stopped` 를 갖는 것을 확인한다.
**`git diff` 로 `docker-compose.e2e.yml` 이 안 바뀌었음도 함께 보인다**(Out of scope 준수).

**AC-2 — 문다: 실제로 복귀해야 한다.**
🔴 **판정은 "선언이 있다" 가 아니라 "돌아왔다" 이다.** 대상 앱 컨테이너를 `docker kill`
하거나(또는 도커를 재시작하고) **스스로 다시 Up 이 되는 것**을 실측한다. 수정 전에는 같은
조작으로 **안 돌아오는 것**도 함께 기록한다 — 대조군 없이는 이 AC 가 아무것도 증명하지 않는다.

**AC-3 — 복귀 ≠ 즉시 사용 가능. 그 사실을 문서에 남긴다.**
🔴 실측: 이번 재시작에서 **`iam-kafka` 가 healthy 가 되기까지 약 8분** 걸렸고(중간에
`unhealthy` 구간을 길게 지났다), 앱들은 그것을 기다린다. 재시작 정책은 *"놔두면 돌아온다"*
를 만들 뿐 *"즉시 쓸 수 있다"* 를 만들지 않는다. 워크스루 §7(문제 해결)에 **재시작 직후
로그인이 안 되면 몇 분 기다려라**는 행을 넣는다 — 안 그러면 다음 사람이 이 티켓이 고친
것을 결함으로 다시 신고한다.

**AC-4 — 한계 대장 갱신.**
워크스루 §6 에 이 항목을 **해소로** 기록한다(또는 부분 해소 시 남은 부분을 명시).

# Related Specs

- `infra/demo/projects.sh` — 패턴 1/2 구분과 `MONO-358` 실패 모드 서술의 출처
- `projects/iam-platform/docker-compose.e2e.yml` · `projects/wms-platform/docker-compose.e2e.yml` — **읽기 전용**(Out of scope)
- `infra/demo/iam-traefik.override.yml` — **수정 대상**(iam 앱 5개를 이미 전부 재선언한다)
- `projects/wms-platform/docker-compose.e2e.yml` § `x-wms-app-common` — **모범 답안**(형제 프로젝트가 이미 올바르다). 수정 대상 아님
- `infra/demo/demo-down.sh` — 명시적 정지와의 상호작용(Edge Cases)
- `docs/guides/interview-demo-walkthrough.md` § 6 · § 7

# Related Contracts

없음 (인프라 전용, API·이벤트 계약 무변경).

# Edge Cases

- **`unless-stopped` 는 명시적으로 stop 된 컨테이너를 되살리지 않는다** — `demo-down.sh`
  가 의도적으로 내린 스택이 도커 재시작에 되살아나지는 않는지 확인한다(`unless-stopped`
  의 정의상 안 되살아나야 하지만, **정의를 믿지 말고 한 번 재현한다**).
- **init 컨테이너** — `kafka-init` 처럼 종료가 정상인 것에 붙이면 무한 재실행.
- **패턴 2 와의 대칭성** — 이미 `unless-stopped` 인 서비스에 다시 선언해도 무해하지만,
  **바뀐 것이 무엇인지 흐려진다.** 필요한 자리만 건드린다.
- **AWS 데모 호스트** — 부팅 시 `demo-up.sh` 가 도는 경로라 이 결함의 영향이 다르다.
  같은 수정이 그쪽에서 무해한지만 확인하고, 그 축의 판단은 `TASK-MONO-399` 에 남긴다.

# Failure Scenarios

- 🔴 **가장 위험: `docker-compose.e2e.yml` 을 고치는 것.** CI 가 크래시를 재시작으로 가리게
  된다 — 결함을 고치려다 **결함 탐지기를 끄는** 전형적인 형태다. Out of scope 가 이것을 막는다.
- **선언만 넣고 복귀를 재지 않는 것.** 이 저장소가 반복해서 물린 축이다 — `MONO-399` 의
  전제가 정확히 *"선언은 아무것도 강제하지 않는다"* 이고, 그 티켓은 그래서 런타임
  `docker inspect` 로 확인하라고 못박는다. AC-2 가 같은 규율이다.
- **대조군 없이 초록을 만드는 것.** 수정 후 컨테이너가 살아 있는 것은 **수정 전에도 참일 수
  있다**(그 순간 아무도 안 죽였으므로). 죽여 보지 않은 초록은 증거가 아니다.
- **init 컨테이너까지 일괄 적용.** 무한 재실행 + 로그 오염.

# Notes

- 분석 = Opus 5 (1M) / **구현 권장 = Sonnet** — 수정 자체는 오버레이 몇 줄이고, 어려운
  부분(어디를 고치면 안 되는가, 무엇을 판정으로 삼는가)은 이 본문이 이미 못박았다.
- 발단: `TASK-MONO-533`(낡은 이미지) 과 같은 라이브 세션에서 나왔다. 둘 다 *"데모를 실제로
  띄워 보기 전에는 아무도 묻지 않았던 것"* 이라는 공통점이 있다.
