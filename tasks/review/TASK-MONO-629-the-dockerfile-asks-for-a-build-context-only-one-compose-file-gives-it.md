# Task ID

TASK-MONO-629

# Title

🔴🔴 **Dockerfile 이 빌드 컨텍스트를 요구하는데 그것을 주는 compose 는 넷 중 하나뿐이다** — main nightly 가 30시간째 빨갛다

# Status

review

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

- [x] **AC-0 (착수 시 실측 — 상속 금지)**
      1. 🔴 **지금도 빨간지 먼저 잰다.** 그 사이 누가 고쳤을 수 있다. `gh run list` 로
         마지막 런의 그 잡 상태를 보고, 초록이면 **왜 초록인지**(누가 고쳤는지)를 적고
         가드 축만 진행한다 — 「어긋남이 없다」와 「어긋남을 잰다」는 다른 명제다.
      2. 모집단을 **다시 센다.** `COPY --from=<X>` 중 **같은 파일의 스테이지가 아닌 것**이
         몇 개이고, 그것을 빌드하는 compose 서비스가 몇 개인가. 🔴 위 표(3×4)를 상속하지 마라.
      3. § 후보 a~d 중 하나를 고르고 **왜 나머지가 아닌지** 적는다.

- [ ] **AC-1 — `main` 이 초록으로 돌아온다.** 🔴 판정은 「고쳤다」가 아니라
      **머지 뒤 그 잡이 실제로 통과한 런 하나**다. 그 런 id 를 적는다.

- [x] **AC-2 — 가드가 존재하고, 어느 쪽에서 어긋나도 문다.**
      두 도착 경로를 **둘 다** 시험한다: ⑴ Dockerfile 이 컨텍스트 요구를 늘리는데 compose 가
      안 따라오는 경우 ⑵ 기존 Dockerfile 을 빌드하는 compose 서비스가 **새로 생기는** 경우.

- [x] **AC-3 — bite 는 주입으로 증명한다.** 🔴 **주입이 실제로 됐는지 먼저 단언**하라
      (원본에 있었나 · 사본에 없나 · 다른 축은 그대로인가).
      🔴 **커버리지를 「지금 저장소에 그런 파일이 있다」에 기대지 마라** — 모집단이 0 이 되는
      날 그 칸은 조용히 공허해진다. **주입 픽스처로** 판정 경로를 돌려라.

- [x] **AC-4 — 조용한 0 을 금지한다.** 「그 Dockerfile 을 빌드하는 compose 서비스」를 **하나도**
      못 찾으면 그것은 통과가 아니라 **판정 불가**다. 🔴 이 가드의 가장 그럴듯한 결함이
      그것이다 — 해석기가 못 찾으면 볼 대상이 없어 초록이 된다.

- [x] **AC-5 — 러너.** 어디서 도는지 적고 **그 자리에서 실제로 도는 것을 확인**한다.
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
- [x] 가드가 존재하고 **두 도착 경로 모두**에서 물며, bite 가 **주입**으로 증명돼 있다
- [x] 「대상 0건 = 판정 불가」가 집행돼 있다
- [x] 러너가 어디인지 적혀 있고 그 자리에서 도는 것이 확인됐다

---

분석=Opus 5 / 구현 권장=Opus (한 줄 수정은 사소하지만 가드의 급소가 «compose 의 `build:` 를
해석해 대상을 찾는 술어» 이고, 그 술어가 조용히 0건을 내면 가드 전체가 공허해진다 —
기계적 치환이 아니다).

---

# 구현 (2026-09-06 UTC)

## AC-0 ① 지금도 빨간가 — **그렇다. 그리고 한 곳이 아니다.**

| 워크플로 | 상태 | 창 |
|---|---|---|
| `nightly-e2e.yml` → `Platform Console E2E full-stack` | **실패** (20여 런 연속) | 마지막 초록 `59fd9e1da` 09-05T06:19Z → 첫 빨강 `697f80139` 11:57Z |
| `federation-hardening-e2e.yml` → `Federation Hardening E2E full-stack` | **실패** | 09-04T21:17Z 까지 매일 초록 → **09-05T20:59Z**(697f80139 다음 스케줄)부터 실패 |

🔴🔴 **두 번째는 이 티켓을 쓸 때 몰랐다.** 로그를 열어 같은 원인임을 확인했다(추론 아님):

```
#67 [console-web internal] load metadata for docker.io/library/demo-backend-resolver:latest
#67 ERROR: pull access denied … insufficient_scope: authorization failed
  73 | >>> COPY --from=demo-backend-resolver . /infra/demo/backend-resolver
실패 스텝 = Start docker compose Phase 2 (producers + console-bff + console-web)
```

## AC-0 ② 모집단 재계수 — **표를 상속하지 않았고, 그게 값을 했다**

티켓은 「Dockerfile 3 × compose 4, 낙오 **한 칸**」이라고 적었다. 실제로 세니:

```
Dockerfile 49개 스캔 · 외부 컨텍스트 요구 3건
compose 27개 스캔  ·  그 셋을 빌드하는 서비스 5칸  ·  선언 누락 **2칸**
```

🔴 **낙오는 둘이었다.** 티켓의 계수는 `projects/*/docker-compose*.yml` 만 봤고,
두 번째 낙오는 **`tests/` 아래**에 있었다:

| compose · 서비스 | |
|---|---|
| `projects/platform-console/docker-compose.e2e.yml` · `console-web` | ✖ |
| `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` · `console-web` | ✖ |

🔵 그 compose 의 주석은 *"platform-console 의 `docker-compose.e2e.yml` 에서 그대로
재사용"* 이라고 적고 있었다 — **재사용의 원본 자신이 낡아 있었으므로 사본도 낡았다.**

## AC-0 ③ 갈래 — **b, 단 «새 가드» 가 아니라 「(z26) 이 왜 못 봤나」부터 읽고 골랐다**

🔴 착수 전에는 몰랐던 사실: **이 축을 보는 가드가 이미 있다고 적혀 있었다.**
`platform-console/docker-compose.yml` 의 주석이 *"가드 (z26)이 이 축을 정적으로 문다"*
라고 말한다. 그런데 못 잡았다. 열어 보니 **두 곳에서 좁다**:

```bash
# infra/demo/verify-demo-wrapper.sh (z26)
for z26_yml in "$ROOT/$z26_proj"/docker-compose*.yml; do   # ← ⑵ 그 프로젝트 디렉터리뿐
  z26_ctx="$(… )"
  [ -n "$z26_ctx" ] && break                               # ← ⑴ 첫 매치에서 멈춘다
done
```

⇒ **묻는 질문이 「어떤 compose 가 주는가」인데 요구는 「모든 compose 가 줘야 한다」다.**
`docker-compose.yml` 이 주고 있었으므로 `docker-compose.e2e.yml` 은 **한 번도 안 봤고**,
`tests/` 아래 것은 **시야에 애초에 없었다.** 두 낙오가 동시에 살아 있던 이유가 그것이다.

**그래서 고른 것**: 저장소 전체를 모집단으로 잡고 **매치되는 서비스를 전부** 보는
판정자를 새로 만들되, `(z26)` 은 **자기 축을 계속 보게 두고 주석을 정정**했다.
🔵 둘은 **다른 것을 잰다** — (z26) 의 도착 경로는 `package.json` 의 `link:`/`file:` 탈출
이고, 새 판정자의 도착 경로는 Dockerfile 과 compose 다. 한쪽만 두면 다른 쪽이 샌다.

**왜 나머지가 아닌가**: (a) YAML anchor/`extends` — compose 들이 **서로 다른 깊이**에
있어 상대경로가 다르다(`../../` vs `../../../`), 병합 규칙도 판본 의존이라 이 결함을
푸는 대신 새 결함을 들여온다. (c) e2e compose 통합 — 별개 ADR 사안. (d) 산문 — 게이트가 없다.

---

## AC-1 — `main` 초록 복구

두 칸에 선언을 넣었다. 🔵 경로는 **각 compose 파일 기준**이라 값이 다르다:

| 파일 | 값 |
|---|---|
| `projects/platform-console/docker-compose.e2e.yml` | `../../infra/demo/backend-resolver` |
| `tests/federation-hardening-e2e/docker/…federation-e2e.yml` | `../../../infra/demo/backend-resolver` |

둘 다 실제로 해석되는지 확인했다(`realpath -m` → `infra/demo/backend-resolver`, 존재함).

🔴 **AC-1 은 아직 안 닫혔다.** 판정은 「고쳤다」가 아니라 **머지 뒤 그 잡이 통과한 런 하나**
이고, 그것은 머지 후에만 생긴다. 그때까지 이 티켓은 `review/` 에 남는다.

## AC-2 · AC-3 — 가드와 bite

`scripts/check-build-context-declarations.sh` (신설):

- **요구** = Dockerfile 의 `COPY --from=<X>` 중 **스테이지도 이미지 참조도 아닌 것**.
  🔴 셋을 구별 못 하면 스테이지를 **거짓 고발**한다(판별 순서: 스테이지 → 이미지꼴 → 나머지).
- **공급** = 그 Dockerfile 을 빌드하는 **모든** compose 서비스의 `build.additional_contexts`.
  🔴 문자열 동일성이 아니라 **키 존재**를 묻는다 — 값은 compose 파일 기준이라 정당하게 다르다.
- 열거는 `find`(`git ls-files` 아님) — (z26) 이 실측한 두 이유 때문이다: 스테이지 전 파일이
  안 보이고, root 로 돌면 git 이 dubious ownership 으로 죽어 **0줄**을 낸다.

**bite ①(실물)** — 고치기 전 트리(`origin/main`)를 그대로 꺼내 돌렸다.
🔴 주입 단언 먼저: 두 파일의 `additional_contexts` 출현 **0건**.

```
✖ 누락 — …/console-web/Dockerfile 이 '--from=demo-backend-resolver' 를 요구하는데
         projects/platform-console/docker-compose.e2e.yml 의 서비스 'console-web' 가 안 줍니다
         tests/federation-hardening-e2e/docker/…federation-e2e.yml 의 서비스 'console-web' 가 안 줍니다
rc=1
```

**bite ②(주입 픽스처, `--self-test`)** — 다섯 칸, 전부 **읽기 전에 주입을 단언**한다:

| 칸 | 무엇 | rc |
|---|---|---|
| ① | 정렬된 세계 (스테이지 `builder` + 이미지 `alpine:3.20` 을 **고발하면 안 된다**) | 0 |
| ② | 도착경로 ⑴ — Dockerfile 이 요구를 늘리고 compose 가 안 따라옴 | 1 |
| ③ | 도착경로 ⑵ — **새 compose** 가 안 줌 (형제는 여전히 주는 중) | 1 |
| ④ | 요구가 있는데 **빌드하는 compose 가 0건** | 2 |
| ⑤ | 축약형 `build: <path>` (컨텍스트를 줄 수 없음) | 1 |

🔴 **칸 ③ 이 이 티켓의 핵심이다** — `(z26)` 은 정확히 그 상태에서 **초록**이었다.
🔵 커버리지를 「지금 저장소에 그런 파일이 있다」에 안 기댄다. 이 패턴이 언젠가 0건이 되면
population 기반 시험은 **조용히 공허**해지고, 요구 건수에 하한을 걸면 이번엔 **성공이
고장으로** 읽힌다. 그래서 판정 경로는 **주입한 세계**에서 돈다.

🔴🔴 **자가검사가 내 가드의 결함 둘을 잡았다** (첫 판은 다섯 칸이 **전부 rc=2**):

1. **EXIT trap 이 미할당 변수를 읽었다** — `set -u` 아래서 `unbound variable` 로 죽는데,
   그 죽음은 **판정 직후**라 rc 를 덮어썼다. 사유가 판정과 무관한데 rc 는 판정처럼 보인다.
2. **내가 건 열거 하한이 픽스처를 죽였다** — `Dockerfile ≥ 3` 이 픽스처(2개)에서 발화했다.
   🔴 하한을 **내리지 않고** 픽스처에 요구 없는 앱을 하나 더 주입해서 풀었다(그 앱은
   음성 대조군도 겸한다 — 어느 칸에서도 이름이 찍히면 안 된다).

## AC-4 — 조용한 0 금지

요구가 있는데 그것을 빌드하는 compose 서비스를 **하나도 못 찾으면 `rc=2`(판정 불가)** 다.
🔴 이 가드의 가장 그럴듯한 결함이 «해석기가 대상을 못 찾아 볼 것이 없어 초록» 이고,
칸 ④ 가 그 경로를 실제로 돌린다. 열거 하한도 **요구 건수가 아니라 스캔한 파일 수**에 건다 —
요구는 정당하게 0 이 될 수 있지만 스캔이 0 이면 계측기가 고장난 것이다.

## AC-5 — 러너

`ci.yml` 신설 잡 **`build-contexts`**. 스텝 셋: `bash -n` · `--self-test` · 실판정.

paths-filter(순수 양성):

```yaml
build-contexts:
  - '**/Dockerfile'
  - '**/docker-compose*.yml'
  - 'scripts/check-build-context-declarations.sh'
```

🔴 **`projects/*/docker-compose.yml` 로 좁히지 않았다.** 두 번째 낙오가 `tests/` 아래였고,
좁은 필터는 그 도착 경로를 **못 깨운다** — `(z26)` 이 못 본 이유와 같은 모양이다.
🔴 잡 술어에 `workflows` 플래그를 함께 걸었다(`TASK-MONO-520` 의 교훈: 자기 스텝만 고치는
PR 은 잡을 **skip** 하고, skip 은 초록으로 보고된다).

🔵 `code-changed` 와 AND 하지 않았다 — 도착 경로가 전부 Dockerfile/compose 라 AND 하면
**결함을 만드는 바로 그 diff 에서** 가드가 꺼진다.

## 같은 PR 에서 함께 고친 것

- `(z26)` 의 헤더 주석 — *"이 축을 정적으로 문다"* 는 **자기 범위 안에서만** 참이라는 것과,
  저장소 전체 축은 새 판정자가 본다는 것을 적었다. 🔴 두 벌이 아니라 **다른 것을 잰다**는
  구별을 함께 적었다(도착 경로가 `package.json` vs Dockerfile·compose).

## 🔴 남은 것

- **AC-1 의 런 하나.** 머지 뒤 `nightly-e2e.yml` 의 그 잡과 `federation-hardening-e2e.yml`
  이 실제로 통과하는 것을 보고 런 id 를 적는다. 그전까지 `review/`.

## CORRECTION (2026-09-06 UTC, 머지 직후 — **AC-1 의 절반이 닫혔다**)

impl PR **#3662** 머지(squash `ca167232d`) 직후 `main` 이 띄운 nightly 런에서 실측했다.

**✅ 닫힌 절반 — `nightly-e2e.yml` 런 `34036399536`**

```
Platform Console E2E full-stack (Playwright + docker compose)   → success
```

🔵 **판정을 「빌드가 됐다」가 아니라 「잡이 통과했다」로 잡았다.** 그 전에 중간 상태도
봤다: 예전에는 **수 초 만에** 죽던 스텝 12(`Start remaining containers …`)가 이번에는
수 분간 **실제로 빌드했고**, 그 뒤 13~16 스텝을 전부 지나 Playwright 까지 끝났다.
🔴 그 구별이 중요하다 — 「스텝 12 통과」만 봤으면 뒤에서 죽는 경우를 못 봤을 것이다.

같은 런의 나머지: 실패 **0건**(성공 10 · 건너뜀 3 · 진행 중 1 — 무관한 잡).

**⚪ 아직 못 잰 절반 — `federation-hardening-e2e.yml`**

이 워크플로는 **스케줄(하루 1회, ~21:00 UTC)** 로만 돈다. 이번 창에서는 아직 안 돌았다.
🔴 **「같은 원인이니 같이 고쳐졌을 것」으로 적지 않는다** — 그것은 유추이고, 이 티켓이
바로 그 부류의 오독에서 태어났다. 확인할 것:

```
gh run list --repo kanggle/monorepo-lab --workflow federation-hardening-e2e.yml --limit 1
# 기대: 2026-09-06T21:xxZ 스케줄 런의
#       'Federation Hardening E2E full-stack (Playwright + docker compose)' = success
#       (직전 실패 런 = 33991705892, 09-05T20:59Z)
```

🔵 **일부러 `workflow_dispatch` 로 앞당기지 않았다.** 그 잡은 풀스택 + Playwright 라
러너 분을 크게 먹고, 어차피 몇 시간 뒤 **무료로 도는** 스케줄이 같은 답을 준다.

⇒ **그래서 이 티켓은 `review/` 에 남는다.** AC-1 은 「`main` 이 초록으로 돌아온다」이고,
망가진 워크플로가 **둘**이었다는 것이 AC-0 의 발견이므로 절반만으로 닫지 않는다.
🔴 `done/` 은 frozen 이라 거기 적은 잔여는 다시 안 읽힌다.

**🔵 그리고 가드는 이미 살아 있다** — `ci.yml` 의 `build-contexts` 잡이 impl PR 에서
실제로 돌았고(런 `34035915551`), 러너에서도 같은 수를 냈다: Dockerfile 49 스캔 ·
요구 3건 · compose 27 스캔 · 서비스 5칸 · 자가검사 `0/1/1/2/1`.
