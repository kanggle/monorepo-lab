# Task ID

TASK-MONO-629

# Title

🔴🔴 **Dockerfile 이 빌드 컨텍스트를 요구하는데 그것을 주는 compose 는 넷 중 하나뿐이다** — main nightly 가 30시간째 빨갛다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- guard
- docker
- regression

---

# Goal

**`main` 을 초록으로 되돌리고**, 같은 어긋남이 다시 새지 않도록 **그것을 재는 술어를 만든다.**

🔴 두 번째가 본체다. 첫 번째는 한 줄이다.

# 🔴 발견 경로 (2026-09-06, `TASK-MONO-628` 착수 뒤 큐 스캔 중)

`nightly-e2e.yml` 의 **`Platform Console E2E full-stack`** 잡이 **20여 런 연속** 같은 스텝에서
죽고 있다. 플레이크가 아니다 — 창이 **커밋 하나**로 좁혀진다.

| | |
|---|---|
| 마지막 초록 | `59fd9e1da` · 2026-09-05T06:19Z |
| 첫 빨강 | **`697f80139`**(`TASK-MONO-585`) · 2026-09-05T11:57Z |
| 그 사이 커밋 | **1개** |
| 실패 스텝 | `Start remaining containers (finance + console-bff + console-web)` |

**로그 원문**(인접성 추론이 아니라 읽은 것):

```
> [console-web internal] load metadata for docker.io/library/demo-backend-resolver:latest:
target console-web: failed to solve: pull access denied, repository does not exist
  or may require authorization: server message: insufficient_scope: authorization failed
Dockerfile:73  >>>  COPY --from=demo-backend-resolver . /infra/demo/backend-resolver
```

## 기전

`697f80139` 이 `console-web/Dockerfile` 에 `COPY --from=demo-backend-resolver` 를 넣었다.
그 이름은 **이미지도 스테이지도 아니고 추가 빌드 컨텍스트**이며, compose 가
`build.additional_contexts` 로 줘야 한다. 그 커밋은 그것을
`projects/platform-console/docker-compose.yml` 에는 넣었고,
**nightly 잡이 실제로 쓰는 `projects/platform-console/docker-compose.e2e.yml` 에는 안 넣었다.**

🔴 컨텍스트가 없으면 BuildKit 은 그 이름을 **이미지로 해석한다** ⇒
`docker.io/library/demo-backend-resolver:latest` 를 pull 하려다 거부당한다.
**증상이 「인증 실패」로 나타나서 원인이 배선처럼 안 보인다** — 그것이 이 결함의 성질이다.

## 형제 전수 — 낙오는 **정확히 그 한 칸**이다

| 서비스 · compose | `build.additional_contexts` |
|---|---|
| `web-store` — `ecommerce-microservices-platform/docker-compose.yml` | ✔ |
| `fan-platform-web` — `fan-platform/docker-compose.yml` | ✔ |
| `console-web` — `platform-console/docker-compose.yml` | ✔ |
| `console-web` — **`platform-console/docker-compose.e2e.yml`** | ✖ ← 여기 |

🔵 그리고 `ci.yml` 은 **초록이었다.** 콘솔 풀스택 e2e 는 nightly 에만 있다 —
`CLAUDE.md` 의 «Post-merge nightly check» 가 이름 붙인 그 실패 모드이고,
`585` 는 그 확인을 하지 않았다.

# 🔴 왜 이것이 «다음에 또» 인가

- 이 계약은 **파일 두 벌에 걸쳐 있다**: Dockerfile 이 컨텍스트를 **요구**하고, compose 가 그것을
  **공급**한다. 둘을 잇는 술어가 하나도 없다.
- 모집단이 이미 **Dockerfile 3 × compose 4** 이고, 프로젝트가 늘 때마다 곱해진다.
  손으로 유지되는 목록이고, 이 저장소는 그 부류에 두 번 데였다
  (`MONO-339` README 서비스 목록 · `MONO-344` compose 파일 목록).
- 어긋남이 **compose 를 추가하는 쪽**에서도 생긴다: 새 e2e compose 가 기존 Dockerfile 을
  빌드하면 그 순간 낙오한다. **정적 가드가 아니면 nightly 첫 실행까지 안 보인다.**

# Scope

**In**

- `projects/platform-console/docker-compose.e2e.yml` 의 `console-web` 에 빌드 컨텍스트 선언
- 「Dockerfile 이 요구하는 외부 빌드 컨텍스트를, 그 Dockerfile 을 빌드하는 **모든** compose
  서비스가 선언하는가」를 재는 가드 + 러너 배선
- 그 가드의 bite(주입 픽스처)와 자가검사

**Out**

- 🔴 `TASK-MONO-585` 자체의 재검토. 그 티켓의 결정(런타임 백엔드 해석)은 옳고 `done/` 이다.
  이 티켓은 **그것이 새어 나간 한 칸**과 **그 부류를 재는 술어**만 본다.
- `console-web` e2e 스펙 자체. 스택이 뜨면 그 축은 원래대로 돌아간다.

# 🔵 후보 (착수 전 추정 — **AC-0 이 고른다**)

| | 안 | 🔴 대가 |
|---|---|---|
| a | compose 의 `additional_contexts` 를 **YAML anchor / `extends`** 로 한 벌로 만든다 | 🔵 두 벌이 애초에 안 생긴다. 🔴 compose 파일이 **다른 디렉터리 기준**이면 상대경로가 달라지고, `extends` 는 `additional_contexts` 를 병합하는 규칙이 판본마다 다르다 |
| b | **가드**: Dockerfile 의 `COPY --from=<X>`(스테이지 아님) 를 뽑아, 그 Dockerfile 을 빌드하는 모든 compose 서비스가 `<X>` 를 선언하는지 대조 | 🔵 **양쪽 도착 경로를 다 막는다**(Dockerfile 이 요구를 늘리는 쪽 · compose 가 새로 생기는 쪽). 🔴 「그 Dockerfile 을 빌드하는 compose 서비스」를 찾는 술어가 이 가드의 급소다 — `build.context`+`build.dockerfile` 을 실제로 해석해야 하고, 못 찾으면 **조용한 0** 이 된다 |
| c | e2e compose 를 없애고 데모 compose + 오버레이로 통합 | 🔴 범위가 이 티켓을 훨씬 넘는다(별개 ADR 사안) |
| d | 산문으로 적는다 | 🔴 산문에는 게이트가 없다 |

🔴 **b 를 추천하지만 착수 전 추정이다.** AC-0 이 compose 두 벌의 실제 모양과
`build:` 해석 규칙을 읽고 고른다.

# Acceptance Criteria

- [ ] **AC-0 (착수 시 실측 — 상속 금지)**
      1. 🔴 **지금도 빨간지 먼저 잰다.** 그 사이 누가 고쳤을 수 있다. `gh run list` 로
         마지막 런의 그 잡 상태를 보고, 초록이면 **왜 초록인지**(누가 고쳤는지)를 적고
         가드 축만 진행한다 — 「어긋남이 없다」와 「어긋남을 잰다」는 다른 명제다.
      2. 모집단을 **다시 센다.** `COPY --from=<X>` 중 **같은 파일의 스테이지가 아닌 것**이
         몇 개이고, 그것을 빌드하는 compose 서비스가 몇 개인가. 🔴 위 표(3×4)를 상속하지 마라.
      3. § 후보 a~d 중 하나를 고르고 **왜 나머지가 아닌지** 적는다.

- [ ] **AC-1 — `main` 이 초록으로 돌아온다.** 🔴 판정은 「고쳤다」가 아니라
      **머지 뒤 그 잡이 실제로 통과한 런 하나**다. 그 런 id 를 적는다.

- [ ] **AC-2 — 가드가 존재하고, 어느 쪽에서 어긋나도 문다.**
      두 도착 경로를 **둘 다** 시험한다: ⑴ Dockerfile 이 컨텍스트 요구를 늘리는데 compose 가
      안 따라오는 경우 ⑵ 기존 Dockerfile 을 빌드하는 compose 서비스가 **새로 생기는** 경우.

- [ ] **AC-3 — bite 는 주입으로 증명한다.** 🔴 **주입이 실제로 됐는지 먼저 단언**하라
      (원본에 있었나 · 사본에 없나 · 다른 축은 그대로인가).
      🔴 **커버리지를 「지금 저장소에 그런 파일이 있다」에 기대지 마라** — 모집단이 0 이 되는
      날 그 칸은 조용히 공허해진다. **주입 픽스처로** 판정 경로를 돌려라.

- [ ] **AC-4 — 조용한 0 을 금지한다.** 「그 Dockerfile 을 빌드하는 compose 서비스」를 **하나도**
      못 찾으면 그것은 통과가 아니라 **판정 불가**다. 🔴 이 가드의 가장 그럴듯한 결함이
      그것이다 — 해석기가 못 찾으면 볼 대상이 없어 초록이 된다.

- [ ] **AC-5 — 러너.** 어디서 도는지 적고 **그 자리에서 실제로 도는 것을 확인**한다.
      🔴 도착 경로 양쪽(`**/Dockerfile` · `**/docker-compose*.yml`)이 전부 그 잡을 깨워야 한다.
      러너 없는 가드는 썩는다.

# Related Specs

- `CLAUDE.md` § Git / branch / worktree discipline — «Post-merge nightly check»
- [`docs/adr/ADR-MONO-068`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md) — `@demo/backend-resolver` 공유 패키지(`B2`)

# Related Contracts

없음.

# Related Tasks

- `TASK-MONO-585` — 발견 경로. `console-web` 을 런타임 해석으로 옮기며 이 요구를 만들었다
- `TASK-MONO-614` — `@demo/backend-resolver` 를 공유 패키지로 올린 티켓
- `TASK-MONO-628` — **형제 축**. 「같은 계약을 나눠 갖는 두 파일이 서로 다른 속도로 배포된다」
  를 잰다. 이 티켓은 **같은 커밋 안에서도 두 벌이 어긋난다**를 잰다
- `TASK-MONO-516` · nightly 전용 스펙이 초록으로 머지된 뒤 main 을 빨갛게 만든 선례

# Edge Cases

① 🔴 **`COPY --from=<X>` 의 `<X>` 는 세 가지일 수 있다** — 같은 파일의 스테이지(`FROM … AS X`) ·
   이미지 참조 · 추가 빌드 컨텍스트. 가드가 셋을 구별 못 하면 **스테이지를 거짓 고발**한다.
   판별은 그 Dockerfile 안의 `AS <X>` 존재 여부가 먼저다.

② 🔴 **compose 의 상대경로는 그 compose 파일 기준**이다. 두 compose 가 다른 디렉터리에 있으면
   같은 컨텍스트라도 문자열이 다르다 — 가드는 **문자열 동일성이 아니라 키 존재**를 물어야 한다.

③ 🔴 **모집단이 0 이 될 수 있다.** 이 패턴을 아무도 안 쓰게 되는 날, 하한을 박아 두면 가드가
   «자기 분기를 못 돌아» 빨개진다. 커버리지는 주입 픽스처가 갖고, 실모집단 0 은 정상이다.

# Failure Scenarios

① **한 줄만 고치고 끝낸다** → main 은 초록이 되지만 다섯 번째 칸에서 똑같이 샌다.
   이 티켓의 명제는 「그 한 칸」이 아니라 **「그 부류를 재는 것이 없다」** 이다.

② **가드를 만들고 러너에 안 붙인다** → 초록인 채 한 번도 안 돈다.

③ **compose 해석기가 대상을 못 찾는데 초록** → AC-4 가 겨누는 그 모양. 0건은 통과가 아니다.

④ **nightly 를 안 보고 닫는다** → 이 티켓 자신이 그 실패에서 태어났다. AC-1 은 문서가 아니라
   **통과한 런 하나**를 요구한다.

# Definition of Done

- [ ] `main` 의 nightly `Platform Console E2E full-stack` 이 **실제로 통과한 런**이 있다(런 id 기재)
- [ ] 가드가 존재하고 **두 도착 경로 모두**에서 물며, bite 가 **주입**으로 증명돼 있다
- [ ] 「대상 0건 = 판정 불가」가 집행돼 있다
- [ ] 러너가 어디인지 적혀 있고 그 자리에서 도는 것이 확인됐다

---

분석=Opus 5 / 구현 권장=Opus (한 줄 수정은 사소하지만 가드의 급소가 «compose 의 `build:` 를
해석해 대상을 찾는 술어» 이고, 그 술어가 조용히 0건을 내면 가드 전체가 공허해진다 —
기계적 치환이 아니다).
