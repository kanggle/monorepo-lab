# Task ID

TASK-MONO-391

# Title

주간 `docker-cleanup.sh` 가 **매주 성공적으로 돌면서도** 디스크를 못 지킨다 — 빌드캐시를 **묶지 않고**(`-a` 없는 `builder prune`), 컨테이너 JSON 로그는 **보지도 않는다**

# Status

done

# Owner

monorepo

# Task Tags

- infra
- chore
- disk

---

# 이건 "가드가 없다" 가 아니라 **"가드가 초록인 채로 못 문다"** 이다

디스크 가드 2개는 **존재하고, 배선돼 있고, 실제로 돈다**:

| 가드 | 상태 (2026-07-13 실측) |
|---|---|
| Windows 예약작업 `DockerWeeklyCleanup` (수 3am → `scripts/docker-cleanup.sh`) | `State=Ready` · **`LastRunTime=2026-07-08 03:00` · `LastTaskResult=0`** · `NextRunTime=2026-07-15` |
| Claude Code `PostToolUse` 훅 (`scripts/hook-prune-after-build.sh` — 빌드 후 dangling 이미지 prune) | `.claude/settings.local.json` 에 배선됨, 스크립트 존재 |

**그런데 5일 뒤인 2026-07-13, C: 여유가 2.86GB 까지 떨어졌다.** 정리해보니 회수 가능분이 **9.8GB** 였다(빌드캐시 5.0 + 미참조 이미지 1.9 + JSON 로그 2.9). 즉 **매주 성공(exit 0)하는 가드가, 정작 그 주에 자란 것들을 그대로 두고 초록으로 끝났다.**

`LastTaskResult=0` 은 *"스크립트가 죽지 않았다"* 는 뜻이지 *"디스크를 지켰다"* 는 뜻이 아니다.

---

# 결함 1 — `builder prune` 이 캐시를 **묶지 않는다** (`scripts/docker-cleanup.sh:40`)

```bash
docker builder prune -f          # ← dangling 캐시만 지운다
```

`-a` 없는 `builder prune` 은 **dangling 빌드캐시만** 회수한다. 정리 직전 실측:

```
TYPE          TOTAL   ACTIVE   SIZE      RECLAIMABLE
Build Cache   101     0        5.012GB   387.4MB      ← ACTIVE=0 인데 RECLAIMABLE 은 387MB뿐
```

**`ACTIVE=0` 인데 `RECLAIMABLE=387.4MB`** — 이 두 숫자의 간극이 결함의 전부다. 아무 빌드도 안 돌고 있으니 5.012GB **전부가 unused** 인데, 그중 dangling 은 387MB 뿐이라 주간 스크립트는 매주 그 387MB 만 지우고 **나머지 4.6GB 는 손도 안 댄 채 초록으로 끝났다.** `docker builder prune -a -f` 로 돌리자 **`Total: 5.012GB`** 가 한 번에 나왔다.

> 주의 — `docker system df` 의 `RECLAIMABLE` 열을 "회수 가능량" 으로 읽으면 안 된다. **빌드캐시 행에서 그 열은 dangling 부분만 센다.** 실제로 회수 가능했던 건 그 열이 말한 387MB 가 아니라 **5.012GB** 였다.

## 🔴 고칠 방향 — **크기 상한은 반증됐다. 나이로 잘라야 한다.**

첫 시도는 *"지우지 말고 묶어라"* 였다 — 도커 29.4.2 가 `--keep-storage` 를 폐기하고 제공하는 **`--max-used-space`** 로 상한을 걸면 캐시 전멸(=매주 콜드빌드) 없이 증식만 묶을 수 있다고 봤다. **실측이 그 가설을 죽였다.**

| 시도 | 캐시 | 상한 | 축출 |
|---|---|---|---|
| `builder prune -f --max-used-space 300MB` | 601.5MB | 300MB | 98.9MB (→ **502.6MB 잔존**) |
| `builder prune -f -a --max-used-space 300MB` | 502.6MB | 300MB | **0B** |
| `builder prune -f --reserved-space 0 --max-used-space 300MB` | 502.6MB | 300MB | **0B** |

**세 번 다 상한 아래로 못 내려갔다.** 원인은 **buildkit GC 가 최근 사용된 레코드를 나이로 보호**하기 때문이다 — 잔존 레코드는 전부 `Last used: 6분 전` 이었다(`docker buildx du --verbose`). ⇒ **이 호스트에서 크기 상한은 캐시를 묶지 못한다.** (오늘 `-a` 가 5.012GB 를 한 번에 지운 건 그것들이 **오래된** 레코드였기 때문이다 — 크기가 아니라 나이가 기준이었다.)

**무는 건 나이 필터다. 양방향으로 증명했다:**

```
docker builder prune -f -a --filter until=1h   # 캐시는 6분 전 것 → 0B      ← 음성 대조(나이를 존중한다)
docker builder prune -f -a --filter until=1m   # 같은 레코드      → 502.6MB ← 양성 대조(실제로 문다)
```

⇒ **채택:**

```bash
docker builder prune -f -a --filter until=168h   # 1주 넘게 안 쓴 캐시만 제거
```

**주간 실행 + 1주 필터 = 오래된 캐시는 매주 사라지고, 뜨거운 한 주치는 살아남는다.** 콜드빌드도 없고 증식도 없다. 나이로 자른 뒤에도 여전히 크면(≥5GB) 그건 *"한 주치 빌드가 실제로 크다"* 는 뜻이므로 **지우지 말고 경고만** 한다.

> **이 반증 자체가 산출물이다.** `--max-used-space` 는 이름이 정확히 *"하고 싶은 일"* 처럼 생겼고 도움말도 *"Maximum amount of disk space allowed to keep for cache"* 라고 말한다. **그런데 이 호스트에서 그 플래그는 0B 를 축출한다.** 다음 사람이 같은 함정에 다시 들어가지 않도록 스크립트 헤더에 세 시도의 수치를 그대로 박아 둔다.

---

# 결함 2 — 컨테이너 JSON 로그를 **아예 보지 않는다** (그리고 `docker system df` 도 안 센다)

`docker-cleanup.sh` 는 컨테이너 / dangling 이미지 / 빌드캐시 3종만 다룬다. **`/var/lib/docker/containers/*/*-json.log` 는 스크립트에도, `docker system df` 에도 나오지 않는다.**

이 영역이 **두 번** 터졌다:

| 날짜 | 규모 | 진범 |
|---|---|---|
| 2026-07-10 | **17.1GB** | `victoriatraces` 미기동 → OTLP export 실패 스택트레이스 무한 누적 (scm-demand-planning 10.8GB + scm-inventory-visibility 5.6GB) |
| 2026-07-13 | **2.9GB** | `scm-inventory-visibility-service-1` (exited) — Kafka 컨슈머 retry WARN 무한 누적 |

두 번 다 `docker system df` 는 **이 영역을 0으로 보고했다.** 사람이 VM 안에서 `du` 를 쳐야만 보였다.

## 왜 `TASK-MONO-338`(로그 로테이션) 이 이걸 못 막나 — **막을 수 없는 게 맞다**

338 은 완전하다(tracked compose 3파일 **19/19 · 6/6 · 4/4** 전수 커버, 2026-07-13 재검증). 문제는 **도커의 구조**다:

- **`HostConfig.LogConfig` 는 컨테이너 생성 시점에 고정된다.** compose 에 `logging:` 을 추가해도 **이미 존재하는 컨테이너에는 소급되지 않는다.**
- 2026-07-13 에 2.9GB 를 뿜은 컨테이너는 **2026-07-02 생성**, 338 은 **2026-07-11 머지** — **가드보다 9일 앞선 컨테이너**다. 재생성(`up -d --force-recreate`) 전까지 이 컨테이너는 **영원히 무제한 로그를 쓴다**.

⇒ **로테이션은 "앞으로 만들 컨테이너" 의 안전망이고, 장수하는 옛 컨테이너에는 감시가 따로 필요하다.** 그 감시가 지금 **어디에도 없다** — 그래서 두 번 다 "C: 가 꽉 찼다" 로만 발견됐다.

---

# Scope

## In Scope

- **`scripts/docker-cleanup.sh:40`** — `docker builder prune -f` → `docker builder prune -f -a --filter "until=${CACHE_MAX_AGE}"` (기본 `168h`). **헤더 주석에 `--max-used-space` 3회 반증 수치를 그대로 박는다**(다음 사람이 같은 함정에 다시 들어간다).
- **`scripts/docker-cleanup.sh` 에 로그 감시 단계 추가** — `docker system df` 가 못 보는 영역을 **소리내어** 보고한다:
  1. `/var/lib/docker/containers` 총량 (VM 안에서 `du -sm`).
  2. **`LogConfig.Options` 에 `max-size` 가 없는 컨테이너 목록** = 무제한으로 자랄 수 있는 것들(= 로테이션 도입 전에 생성된 장수 컨테이너). 이게 **감시의 핵심 술어**다 — 크기가 아니라 **상한의 부재**를 본다(크기는 터진 뒤에야 커진다).
  3. 임계(예: 로그 총량 > 1GB **또는** 개별 로그 > 500MB)를 넘으면 **비-0 종료가 아니라 경고 + 조치 안내**(`--force-recreate` 로 로테이션을 붙여라). 예약작업을 RED 로 만들면 아무도 안 본다.
- 선택 플래그 **`--logs`** — 명시적으로 줬을 때만 상한 없는 컨테이너의 JSON 로그를 `truncate -s 0`. **기본 동작 아님**(관측성 손실이므로 사람이 의도해야 한다).
- 스크립트 헤더 주석에 **"`RECLAIMABLE` 열은 빌드캐시 행에서 dangling 만 센다"** 와 **"LogConfig 는 생성 시점 고정 → 재생성해야 로테이션이 붙는다"** 를 명기(두 번 다 이걸 몰라 놓쳤다).

## Out of Scope

- **untracked 로컬 데모 오버레이 4종**(`docker-compose.federation-e2e.{ecommerce,ecommerce-extra,erp-fullstack,ledger}.yml`) — `.gitignore` 가 이름으로 명시한 **의도적 미커밋** 파일이며 `TASK-MONO-338` § Out of Scope 가 이미 결정했다. **git 으로 승격하지 말 것**(반복 반증된 가설). 2026-07-13 에 **로컬에서** 동일 `x-logging` 앵커를 붙여 31개 서비스에 로테이션을 적용했다(merged config 47/47 `max-size`, 누락 0). 커밋 대상 아님.
- **vhdx `compact`** — C: 실제 회수는 여전히 관리자 수동(`compact-rd-vhdx-*.ps1`). 이 task 는 **VM 내부 증식 억제**만 다룬다.
- 예약작업 자체의 주기/등록 변경(수 3am 유지).

---

# Acceptance Criteria

> **실측 = 2026-07-14, 이 호스트(docker 29.5.3).** 어제(07-13)의 수치는 **인계된 가설로 취급하고 전부 재측정**했다 — 캐시가 그 사이 비워져 **모집단 자체가 달랐다**(502.6MB → 오늘 새로 만든 146.4MB). 아래 숫자는 전부 오늘 것이다.

- [x] **AC-1 — 빌드캐시가 나이로 잘린다 (양방향).** ① **양성**: 필터보다 **오래된** 캐시가 실제로 제거된다. ② **음성**: 필터보다 **최근** 캐시는 **남는다**(0B 축출). **두 방향을 다 보여야 한다** — 양성만 보면 "그냥 다 지우는 것" 과 구별되지 않고, 음성만 보면 "아무것도 안 하는 것" 과 구별되지 않는다. **`--max-used-space` 로 구현하지 말 것 — 3회 반증됐다**(§ 고칠 방향).
  **✅ 같은 레코드에 나이 필터만 바꿔 양방향 확인**: `until=1h`(1분 된 캐시) → **0B 축출**(음성) / `until=1m`(4분 된 같은 레코드) → **146.4MB 전량 축출**(양성).
  **✅ 결함도 함께 재현**: 현행 명령 `builder prune -f`(=`-a` 없음)를 그 146.4MB 에 돌리니 **12.29kB 만 회수**하고 146.4MB 를 남긴 채 exit 0 — 그리고 그 직후 `docker system df` 는 `RECLAIMABLE 0B` 라고 보고했다(`ACTIVE=0` 인데도).
- [x] **AC-2 — 상한 없는 컨테이너를 이름으로 지목한다.** 스크립트 출력이 `LogConfig.Options` 에 `max-size` 가 없는 컨테이너를 **목록으로** 찍는다. **크기가 아니라 상한의 부재로 판정한다**(AC-4 가 이 차이를 강제).
  **✅ 실행 시 55개 지목**(노이즈 방지로 상위 10 + 잔여 개수). 정리 후 재보고에서 21개.
- [x] **AC-3 — 로그 총량을 보고한다.** `/var/lib/docker/containers` 총량이 출력에 나온다(`docker system df` 는 이 값을 0 으로 보고하므로, 이 줄이 **유일한 가시성**이다).
  **✅ 486MB → 390MB 보고**(같은 실행에서 `docker system df` 는 이 영역을 여전히 한 줄도 안 보여준다).
- [x] **AC-4 — mutation: 감시가 실제로 문다.** 상한 없는 컨테이너를 하나 만들고 스크립트 실행 → **그 컨테이너가 목록에 뜬다**. 로그가 **작아도(≈0B) 떠야 한다** — 크기 기준이면 안 뜬다.
  **✅ `mono391-probe`(`alpine sleep`, `max-size` 없음, 로그 ≈0B)가 목록 최상단에 떴다.** 적용 여부를 먼저 확인했다(`docker inspect` → `max-size=[]`). **로그가 비어 있는데도 떴다는 것이 요점** — 크기 술어였다면 안 떴을 것이고, 17GB 로 터진 뒤에야 떴을 것이다.
- [x] **AC-5 — 기본 동작은 비파괴.** `--logs` 없이 실행하면 **어떤 로그도 truncate 되지 않는다**. 관측성은 명시 요청 시에만 포기한다.
  **✅ 살아남은 컨테이너의 로그 바이트 불변**: `fulfillment-demo-outbound` 189MB → **189MB**, `fulfillment-demo-inventory` 153MB → **153MB**. (총량이 486→390MB 로 준 것은 truncate 가 아니라 **prune 된 컨테이너가 자기 로그를 데려간 것** — `scm-gateway` 82MB.)
  **✅ `--logs` 경로의 truncate 기구는 격리 probe 로 별도 확인**(821,961B → **0B**). 살아있는 컨테이너의 관측성을 버리지 않기 위해 스크립트의 `--logs` 분기 자체는 실행하지 않고, 동일한 `wsl … sh -s "$1"` 명령형을 probe 에 적용해 확인했다.
- [x] **AC-6 — 경고 상황에서도 비-0 로 죽지 않는다** (RED 예약작업은 무시된다).
  **✅ 실전 전체 실행 → 종료코드 0.** **✅ 경고 분기를 강제 발화**(`WARN_TOTAL_MB=1 WARN_SINGLE_MB=1`)시켜 총량 경고 + 개별 `[!]` 플래그가 실제로 찍히게 한 뒤에도 **종료코드 0**. ← 평시엔 임계 미달로 이 분기가 **한 번도 안 돌기 때문에** 임계를 env 로 열어 물려봤다(안 돌아본 분기는 안 되는 분기다).
  **⚠️ 미결(머지 후) — `Start-ScheduledTask DockerWeeklyCleanup` → `LastTaskResult=0`.** 예약작업은 **메인 체크아웃**(`monorepo-lab\scripts\docker-cleanup.sh`)을 실행하므로 **이 PR 이 main 에 머지되고 그 체크아웃을 `git pull` 해야 비로소 이 수정이 가드에 도달한다.** *"커밋이 main 에 있다" ≠ "그 커밋이 실행된다"* — 워크트리에서 초록인 것은 예약작업이 고쳐졌다는 증거가 아니다.
- [x] **AC-7 — dry-run 보존.** `--dry-run` 이 여전히 아무것도 안 지우고 회수 가능량만 보고한다(+ 새 로그 섹션 포함).
  **✅ dry-run 실행 → 삭제 0건**, 로그 섹션 정상 출력.

---

# Edge Cases

- **🔴 `container prune -f` 가 매주 수요일 데모의 멈춘 컨테이너를 지운다 (기존 동작 — 이 task 가 발견했다)**. 2026-07-13 실측: 멈춘 컨테이너 **35개 중 22개가 `federation-hardening-e2e` 데모**다. 문제는 **복구 경로가 두 갈래로 갈라져 있다**는 것이다:
  - **저장소에 추적된 경로** = `scripts/fed-e2e-up.sh` — bare `compose up -d`. **컨테이너가 없어도 성립한다**(재생성하므로).
  - **운영 노트가 가르치는 경로** = 일괄 **`docker start`**(티어 순서). 이건 **컨테이너가 존재해야** 성립한다. `TASK-MONO-338` § Edge Cases 도 이 경로를 전제로 "티어 순서 재기동" 을 적었다.
  - ⚠️ 그 절차를 담은 `compact-rd-vhdx.ps1` 은 **저장소에 없다**(2026-06-10 에 untracked 오작동 위험 때문에 삭제, 운영자 노트에만 존치). ⇒ **이 갈라짐은 저장소가 아니라 사람의 기억 속에 있어서, CI 가 절대 못 잡는다.**

  ⇒ 수요일 3am 이 지나면 후자는 **조용히 사라진다**(볼륨은 남으니 데이터 손실은 없다). **구현 시 결정할 것**: (a) `docker container prune -f --filter "until=24h"` 로 갓 멈춘 컨테이너를 보호할지, (b) 현행 유지하되 **복구 경로가 `docker start` 가 아니라 `compose up -d` 임을 스크립트 출력에 명시**할지. **둘 중 하나는 해야 한다 — 지금은 두 경로가 서로 다른 것을 가르치고 있다.**

  **🔴 결정 (2026-07-14) — (a) 는 술어가 반증돼 채택 불가. (b) 를 구현했다.**
  **`container prune --filter until=` 은 *정지* 시각이 아니라 *생성* 시각으로 매칭한다.** 라벨로 격리한 probe 로 판별했다(라벨 필터가 범위를 실제로 좁히는지 먼저 확인 — 없는 라벨로 prune → 무관 컨테이너 35개 무사, 0B): probe 는 **생성 3분38초 전 / 정지 0초 전**이었는데 `--filter until=2m` 에 **제거됐다**. ⇒ **13일 전 생성된 데모 컨테이너는 `until=24h` 로도 그대로 지워지고, 정작 보호되는 건 갓 생성된 Testcontainers 고아다 — 보호가 정확히 뒤집혀 있다.** (`docker ps` 는 `until` 필터를 **아예 받지 않아**(`invalid filter 'until'`) 미리보기가 불가능하다. 문서가 아니라 실측만이 답을 준다.)
  ⇒ **필터를 걸지 않는다.** 대신 스크립트가 **지우기 전에 몇 개를 지우는지, 그리고 복구는 `compose up -d` 임을 소리내어 말한다.** **이건 양보가 아니다** — 함정 2 가 말하듯 **재생성만이 로테이션을 붙인다.** `docker start` 로 되살린 옛 컨테이너는 상한 없는 로그를 영원히 계속 쓴다. ⇒ **prune → `compose up -d` 경로가 결함 2 의 해법과 같은 방향**이고, `docker start` 경로는 결함 2 를 **영구화**한다.
  ⚠️ 실측 시점의 데모는 **이미 반쯤 죽어 있었다**(서비스 17 running / 인프라 22 `Exited(255)`). `TASK-MONO-338` § Edge Cases 가 *"일괄 `docker start` 는 `depends_on` 을 무시해 Exit(255) 떼죽음을 부른다"* 고 적어 둔 지문과 **모양이 같다** — 다만 그 기동을 직접 목격하진 않았으므로 **정황 일치로만 기록**한다(인과 주장 아님).
- **도커가 죽어 있을 때** — 2026-07-13 실제로 `failed to connect to the backend: timed out dialing Hyper-V socket` 상태였다. `set -euo pipefail` 이라 첫 `docker system df` 에서 비-0 종료 → 예약작업이 **RED** 가 된다. 이때 RED 는 **정당**하다(청소를 못 했으니). 다만 **메시지가 "도커 미기동" 임을 분명히** 해야 한다 — 안 그러면 다음 사람이 스크립트 버그로 오진한다.
- **`wsl -d rancher-desktop` 호출** — 로그 총량/LogConfig 조회는 VM 안을 봐야 한다. Git Bash 에서 `wsl ... sh -c '...$f...'` 는 **루프 변수가 외부 bash 에 먹혀 빈 문자열로 뭉개진다**(단따옴표도 무효, 2026-07-10 실증). **반드시 stdin heredoc** (`sh -s <<'EOSH'`) + `MSYS_NO_PATHCONV=1`. **LogConfig 조회는 `docker inspect` 로 호스트에서 가능하므로 wsl 을 안 써도 된다 — 되도록 그 경로를 택하라.**
- **로그 총량 임계는 컨테이너 수에 비례한다** — 데모 풀스택은 44~55 컨테이너다. 컨테이너당이 아니라 **총량 + 개별 최대** 두 축으로 본다(총 1GB / 개별 500MB 제안).
- **`--max-used-space` 는 buildx 전용 플래그** — 구형 도커에선 미지원. 스크립트가 플래그 지원 여부를 확인하고 미지원이면 `-a` 로 폴백(그리고 그 사실을 출력).

# Failure Scenarios

- **F1 — `-a` 만 붙이고 필터를 뺌** → 매주 빌드캐시 **전멸** → 수요일마다 첫 빌드가 콜드. 디스크는 지키지만 **캐시의 존재 이유를 없앤다.** AC-1 의 **음성 대조**(최근 캐시는 남는다)가 정확히 이걸 잡는다.
- **F1' — `--max-used-space` 로 "묶었다" 고 믿음** → 플래그는 받아들여지고 **exit 0** 이며 **아무것도 축출하지 않는다**(3회 실측 0B). **가장 위험한 실패 모드다 — 초록이고, 조용하고, 캐시는 계속 자란다.** 지금 스크립트가 매주 성공하며 실패해 온 것과 **똑같은 모양**이다.
- **F2 — 로그 감시를 "크기 임계" 로만 구현** → 상한 없는 컨테이너가 **아직 작을 때는 안 보이고**, 17GB 로 터진 뒤에야 보인다. **그건 감시가 아니라 사후 통보다.** AC-2/AC-4 가 "상한의 부재" 로 판정할 것을 강제하는 이유.
- **F3 — 경고를 비-0 종료로 구현** → `DockerWeeklyCleanup` 이 매주 RED → 사람이 무시 → **가드가 꺼진 것과 같아진다**(이 저장소가 이미 한 번 겪은 실패: 첫날 RED 인 가드는 꺼진다). 경고는 **출력**이지 종료코드가 아니다.
- **F4 — 기본 동작에 truncate 를 넣음** → 주간 작업이 조용히 로그를 날려 **사고 조사 근거가 사라진다**. 로그 폭증은 **증상**이고, 진짜 원인(미기동 의존 서비스, kafka 재시도)은 그 로그 안에 있다. `--logs` 를 명시 플래그로 두는 이유.
- **F5 (구현 중 발견) — `container prune` 에 `--filter until=` 을 걸어 "갓 멈춘 것을 지킨다" 고 믿음** → **`until` 은 생성 시각 기준**이라 오래 살아온 데모는 그대로 지워지고 갓 만들어진 고아만 살아남는다. **플래그는 받아들여지고 exit 0 이고 보호가 뒤집혀 있다 — F1' 과 정확히 같은 모양**(이번엔 `docker ps` 로 미리보기조차 안 돼서 실측 없이는 보이지 않는다).

# Test Requirements

- AC-1: 양방향. 짧은 필터(`until=1m`)로 **축출됨**을 보이고, 긴 필터(`until=1h`)로 최근 캐시가 **남음**을 보인다. 둘 다 `docker system df` 의 Build Cache 행으로 확인.
- AC-4: 상한 없는 probe 컨테이너 → 목록 출현(로그 ≈0B 인데도).
- AC-5: `--logs` 없이 실행 → 로그 크기 불변 실측.
- AC-6: `Start-ScheduledTask DockerWeeklyCleanup` → `LastTaskResult=0`.
- 셸 스크립트이므로 CI 단위테스트 대상 아님 — **호스트 실측이 유일한 권위**(이 파일은 이 호스트에서만 돈다).

# Definition of Done

- [x] AC-1~7 (**AC-6 의 예약작업 실행 1건만 머지 후로 미룸** — 예약작업이 메인 체크아웃을 실행하므로 구조적으로 지금 검증 불가. 위 AC-6 참조.)
- [x] `scripts/docker-cleanup.sh` 헤더에 **세** 함정 명기 — `RECLAIMABLE` 의 의미 · `LogConfig` 생성시점 고정 · **`container prune --filter until=` 은 생성시각 기준**(구현 중 발견, F5)
- [ ] `tasks/INDEX.md` done entry (close chore)

---

# Dependency Markers

- **선행 (done)**: `TASK-MONO-204`(이 스크립트를 커밋한 task) · **`TASK-MONO-338`**(로그 로테이션 — 이 task 는 **338 이 구조적으로 못 덮는 영역**, 즉 *로테이션 도입 이전에 생성된 장수 컨테이너*를 감시한다. 338 을 고치는 게 아니다) · `TASK-MONO-339`(기동 완전성 — 로그 폭증의 **원인** 축).
- **연관**: 없음(순수 로컬 호스트 유지보수).

# Related Specs

- `scripts/docker-cleanup.sh` — 수정 대상
- `scripts/hook-prune-after-build.sh` — 빌드 후 dangling prune 훅(정상 동작, 변경 없음)
- `tasks/done/TASK-MONO-338-e2e-compose-log-rotation.md` § Out of Scope — untracked 오버레이 결정(뒤집지 말 것)

# Related Contracts

- 없음 (인프라 유지보수 — API/이벤트 계약 무관)

---

# Provenance

발굴 2026-07-13, *"도커 용량 정리"* 중. C: 여유가 **2.86GB** 까지 떨어져 있었고 도커 데몬은 이미 죽어 있었다(Hyper-V 소켓 타임아웃). 정리로 **9.8GB** 를 회수했다(빌드캐시 5.0 + 미참조 이미지 1.9 + JSON 로그 2.9 → C: 2.86 → 19.5GB).

**이 티켓의 요점은 "정리를 안 했다" 가 아니다.** 정리 자동화는 **두 개나 있었고, 배선돼 있었고, 5일 전 수요일에 성공(`LastTaskResult=0`)했다.** 그런데도 9.8GB 가 쌓였다. 가드가 초록이었던 이유는 **가드가 자기가 못 보는 것에 대해 침묵했기 때문**이다 — `builder prune` 은 unused 캐시의 8%(387MB/5.0GB)만 보고 나머지를 안 봤고, JSON 로그는 `docker system df` 조차 0 으로 보고했다.

**이 저장소가 반복해 배우는 것의 또 다른 얼굴** — 가드는 *무는가* 뿐 아니라 *물 기회를 얻는가* 로 평가해야 한다. 여기선 가드가 **매주 실행됐다**(기회는 얻었다). 실패한 건 **범위**다: 초록으로 끝나면서 자기 사각지대를 **한 마디도 보고하지 않았다.** 그래서 AC-3(총량을 소리내어 보고)이 AC-1(실제 회수)만큼 중요하다 — **못 지우더라도 보이기라도 해야 한다.**

분석=Opus 4.8 / 구현 권장=**Sonnet**(셸 스크립트 한 파일 + 호스트 실측. 판단은 이 티켓에서 끝났고, 남은 건 기계적 구현이다).
