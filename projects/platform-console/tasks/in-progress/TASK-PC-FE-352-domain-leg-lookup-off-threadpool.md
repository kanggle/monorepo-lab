# Task ID

TASK-PC-FE-352

# Title

도메인 다리 · 레지스트리 · 토큰 교환의 이름 조회가 libuv threadpool 을 막지 않게 — `TASK-PC-FE-350` 선택지 (가) 측정 · 결정

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test
- reliability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (Node DNS · undici `connect.lookup` · musl/Vercel 측정)
> 기안 2026-10-19 UTC — `TASK-PC-FE-350` 의 남은 위험. 급하지 않다: 350 (나) 로 «개요 전체 저하» 경로는 사라졌고, 남은 영향은 카드 하나의 저하다.
> 🔵 **착수 결정 (소유자, 2026-10-20 UTC — 선택창)**: (가) **`TASK-ERP-BE-045` 머지 뒤 AC-0 측정 착수**(보류 · 닫음 아님). 데모 불필요 — 로컬 + Docker `node:20-alpine`. 수선 범위는 AC-0 결과로 AC-1 에서 다시 묻는다.

# Dependency Markers

- 출처: `TASK-PC-FE-350` AC-0 ①(로컬 재현) · 추천 «(가) 는 후속 조사».
- 선행: 350 머지(#4417) — 끝났다.

# Goal

350 AC-0 ① 에서 확인했다: 다리가 4 s 에 포기해도 `getaddrinfo` 는 취소되지 않고 threadpool(기본 4)을 계속 차지한다. 그러면 같은 프로세스의 다른 이름 조회가 모두 줄을 선다. 350 (나) 는 개요의 **자기 자신 호출**만 없앴다. 도메인 다리 · 레이아웃의 레지스트리 · 토큰 교환 같은 다른 호출은 여전히 같은 줄에서 기다릴 수 있다.

threadpool 을 쓰지 않는 조회로 바꾸면 이 공유 정체가 없어지는지 잰다. 바꿀 수 있다면 어디에 넣을지 정한다.

# Scope

## In Scope

- **AC-0 측정**:
  - ① 350 의 재현 스크립트(세션 scratchpad `pcfe350/` — 임시라 사라졌으면 다시 만든다)에 undici `Agent({ connect: { lookup } })` 를 더해 대조한다. lookup 은 `dns.resolve4/6`(c-ares — threadpool 미사용)을 쓴다.
  - ② `node:20-alpine` 컨테이너에서 같은 측정을 한다. 350 은 Docker 가 꺼져 재지 못했다.
  - ③ Vercel(서버리스)에서 threadpool 점유가 요청 사이에 이어지는지 — 잴 수 있는 방법을 찾고, 못 재면 그 이유를 적는다.
- 결과로 소유자에게 묻는다. 후보:
  - 콘솔 서버 fetch 전체에 c-ares lookup 을 쓰는 dispatcher 를 건다.
  - 다리에만 건다.
  - 하지 않는다.

## Out of Scope

- 자기 자신 호출(350 — 끝났다) · 다리 타임아웃 값.

# Acceptance Criteria

- [x] **AC-0** (2026-10-20 UTC — § AC-0: ① 쟀다 · ② Docker 꺼짐 · ③ 배포 필요 + 운영 노출 경로 = 소유자 확인 ④) — ①②③ 을 «쟀다 / 못 쟀다(이유)» 로 적는다. 대조군은 lookup 교체 없음 · `127.0.0.1` 리터럴 · pool 크기 상향.
- [ ] **AC-1** — 소유자 결정(선택지 · 추천).
- [ ] **AC-2** — 결정한 수선 + 빨강 먼저 시험(threadpool 을 채운 상태에서 다리 하나가 다른 다리를 막지 않는다) + 대조군.
- [ ] **AC-3** — 배포 상태.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9

# Edge Cases

- `dns.resolve*` 는 `/etc/hosts` 를 읽지 않는다 — `localhost` · compose 서비스 이름 · Traefik 별칭이 hosts 에만 있다면 깨진다. 측정에 반드시 넣는다.
- musl 의 `getaddrinfo` 는 glibc 와 동작이 다르다 — Windows · glibc 측정을 musl 결론으로 옮기지 않는다.

# Failure Scenarios

1. `UV_THREADPOOL_SIZE` 만 키우고 닫는다 — 350 에서 pool 16 에 작업 16 개로 다시 재현됐다.
2. c-ares 로 바꾸다 hosts 기반 이름이 풀리지 않아 데모 · 로컬이 깨진다(Edge Case 1).

---

# AC-0 — 측정 (2026-10-20 UTC)

> 측정만 했다. 코드 · 설정 · Vercel · AWS 는 바꾸지 않았다. 스크립트는 세션 scratchpad `pcfe352/`(`probe.js` · `driver.sh`, 결과 `run-node24-r1.tsv`)에만 있다 — **임시라 사라질 수 있다.** 설계: 매 반복 새 프로세스 · `127.0.0.1` 전용 HTTP 서버 · t=0 에 느린 조회 K 개 · t=100 ms 에 피해자 `fetch` · 50 ms 타이머로 루프 지연. undici 7.16.0 을 scratchpad 별도 폴더에 설치해 `setGlobalDispatcher(new Agent({ connect: { lookup } }))` 로 **내장 `fetch`(Node 24.14.0 = undici 7.21.0)** 의 조회를 바꿨다 — 전역 dispatcher 심볼은 7.x 끼리 호환된다(실측: 아래 c-ares 갈래가 실제로 다르게 동작).

## ① 로컬 대조 — ✅ 재 봤다 (Windows · Node 24.14.0, 각 2 회)

느린 조회 = `dns.lookup('nx-pcfe352-test.local')`(단독 2718 ms `ENOTFOUND`). 죽은 DNS = `Resolver.setServers(['10.255.255.1'])`(라우팅 불가 — 매 질의 타임아웃).

| 피해자 조회 | 부하 | pool | 대상 | 피해자 fetch |
|---|---|---|---|---|
| 기본(`getaddrinfo`) | 없음 | 4 | `localhost` | OK 60 · 92 ms |
| 기본 | `lookup` ×16 | 4 | `localhost` | **`UND_ERR_CONNECT_TIMEOUT` 10600 · 10589 ms** (350 재현) |
| 기본 | `lookup` ×16 | 4 | `127.0.0.1` | OK 49 · 55 ms (대조: IP 리터럴) |
| 기본 | `lookup` ×16 | 32 | `localhost` | OK 2288 · **TIMEOUT 10597 ms** (대조: pool 상향 — 1/2 실패) |
| c-ares(`resolve4/6`) | `lookup` ×16 | 4 | `localtest.me` | OK 329 · 680 ms |
| c-ares | 죽은 DNS ×16 | 4 | `localtest.me` | OK 106 · 300 ms (부하 16 개 전부 `ETIMEOUT` ~9 s) |
| c-ares | 없음 | 4 | `erp.local`(hosts 전용) | **`ENOTFOUND` ×2** |
| c-ares | 없음 | 4 | `localhost` | **`ENOTFOUND` ×2** |
| 혼성(hosts 표 → `localhost` → c-ares) | 없음 | 4 | `erp.local` · `localhost` | OK 33–57 ms ×4 |
| 혼성 | `lookup` ×16 | 4 | `erp.local` | OK 55 · 46 ms |
| 혼성 | 죽은 DNS ×16 | 4 | `localtest.me` | OK 109 · 145 ms |

- ✅ **threadpool 밖 조회는 공유 정체를 없앤다** — 같은 부하에서 기본은 10.6 s 실패, c-ares · 혼성은 0.05–0.7 s 성공. 부하가 `getaddrinfo` 로 남아도(다른 코드 경로) 피해자는 빠져나온다.
- ✅ **pool 상향은 수선이 아니다** — 이번에는 pool 32 에서도 2 회 중 1 회가 10.6 s 실패(Failure Scenario 1 재확인).
- 🔴 **Edge Case 1 실측: 순수 c-ares 는 hosts 이름 · `localhost` 를 못 푼다**(`ENOTFOUND` 4/4). 넣는다면 혼성(hosts 표 먼저)이어야 한다.
- 🔴 **새로 본 위험 — c-ares 와 OS 해석기가 다른 설정을 읽는다.** 이 호스트에서 `dns.getServers()` = `['127.0.0.1']`(듣는 것 없음 → 모든 이름 `ECONNREFUSED`, `github.com` 포함), OS 해석기는 Wi-Fi 의 `172.20.10.1`. 그래서 위 표의 c-ares 갈래는 `CARES_SERVERS=1.1.1.1` 로 서버를 고정해서 쟀다. 리눅스에서는 둘 다 `/etc/resolv.conf` 를 읽지만, c-ares `resolve*` 는 `nsswitch` · `search` 도메인(짧은 이름 확장)을 쓰지 않는다 — ② 에서 재야 한다.
- 🔵 죽은 DNS 부하에서 루프 지연이 200–260 ms(다른 조건 ≤43 ms) — c-ares 재시도 처리 비용으로 보인다. 피해자에는 영향 없음.
- ⚪ Node 20(컨테이너 · Dockerfile 의 `node:20-alpine`)은 이 호스트에서 재지 않았다 — ② 에 포함.

## ② `node:20-alpine` — ⚪ 못 쟀다 (Docker 데몬 꺼짐, 2026-10-20 UTC `docker info` → `//./pipe/docker_engine` 없음)

다음 측정: 컨테이너 안에서 위 행렬 + `getent hosts` / c-ares 각각으로 compose 서비스 이름 · `extra_hosts` 별칭 · `search` 도메인이 필요한 짧은 이름 · `*.local` 기본값 조회 시간.

## ③ Vercel — ⚪ 못 쟀다 (이유)

- 이 호스트엔 Vercel CLI 가 없고, 함수 안 threadpool 점유가 요청 사이로 이어지는지 재려면 측정용 route 를 배포해야 한다(외부 배포 — 소유자 몫).
- 🔴 **대신 코드로 확인한 노출 경로**: 데모가 꺼지면 `resolveBackendUrl` 은 설정값을 **그대로** 돌려준다(`shared/config/demo-backend.ts:124`). Vercel 에서 도메인 주소 env 가 비어 있으면(소유자 확인 항목 ②) 그 설정값은 `*.local` 기본값 — **데모 off 동안 운영 콘솔의 모든 다리가 풀리지 않는 `*.local` 을 조회한다.** 그 조회가 Vercel(glibc)에서 빠르게 실패하면 정체가 없고, 느리면(≥ 다리 4 s) 이 기전이 운영에서 산다. 그것이 소유자 확인 항목 ④(`console_composition_leg` reason = `DOWNSTREAM_ERROR` 빠름 vs `TIMEOUT` ~4000 ms)다 — **AC-1 결정의 입력.**
- 수선 위치 후보(코드 확인): `src/instrumentation.ts` `register()`(nodejs 런타임 한정 — OTel 등록과 같은 자리). `undici` 는 지금 console-web 의 직접 의존이 아니다 — 넣으면 새 의존 1 개.

- [x] AC-0 닫힘(① 쟀다 · ② ③ 못 쟀다 + 이유).
