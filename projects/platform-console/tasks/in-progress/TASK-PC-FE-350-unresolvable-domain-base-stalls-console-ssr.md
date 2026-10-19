# Task ID

TASK-PC-FE-350

# Title

풀리지 않는 도메인 주소 하나가 콘솔 SSR 의 자기 자신 호출까지 막는가 — `getaddrinfo` 의 threadpool 점유 가설 측정 · production 영향 확인

# Status

in-progress (2026-10-19 UTC — AC-0 측정 완료 · 소유자 결정 대기)

# Owner

frontend

# Task Tags

- code
- test
- reliability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (Node 런타임 · DNS · threadpool 측정과 수선 설계 — 단순 설정 변경이 아니다)
> 기안 2026-10-19 UTC — `TASK-PC-FE-341` 판정(조작 실험)에서 뗀 제품 쪽 위험. **기전 미측정.**

# Dependency Markers

- 출처: `TASK-PC-FE-341` § 판정 (조작 실험). e2e 쪽 수선(설정 누락)은 341 에서 끝났다. 이 티켓은 **같은 일이 운영에서도 날 수 있는가** 를 다룬다.
- 선행 없음.

# Goal

341 이 재 둔 것: e2e 스택에서 E-Commerce 주소가 풀리지 않는 기본값 `http://ecommerce.local/...` 로 남아 있었다. 그러자 ① E-Commerce 조합 다리가 매번 4 s 다리 타임아웃까지 기다렸고, ② 런마다 한 번 개요 렌더의 **자기 자신 호출**(`getOperatorOverviewState` → `selfOrigin()` 의 조합 route)이 ~10 s 뒤 `transport:TypeError/UND_ERR_CONNECT_TIMEOUT` 로 죽어 «통합 개요를 일시적으로 불러올 수 없습니다» 로 저하됐다. 그 주소 하나만 non-routable 로 바꾸자 ①② 둘 다 0 이 됐다(3 런 대 3 런).

**그 사이 기전은 재지 않았다.** 가장 그럴듯한 설명은 이렇다. Node `dns.lookup`(=`getaddrinfo`)은 libuv threadpool(기본 4)에서 돌고, 시작하면 취소할 수 없다. 그래서 4 s 에 포기한 E-Commerce 다리의 조회가 칸을 계속 차지한다. 조회가 몰리면 `localhost` 조회조차 줄을 서고, undici 의 10 s connect timeout(조회 포함)에 걸린다. 이게 맞다면 **이 결함은 e2e 전용이 아니다.** 운영에서도 느리게 풀리는 도메인 주소 하나(DNS 장애 · 오설정)가 콘솔의 다른 모든 SSR(자기 자신 호출 · 다른 도메인 호출)을 함께 멈출 수 있다. 4 s 다리 타임아웃이 그것을 막지 못한다.

# Scope

## In Scope

- **AC-0 측정 (결정 아님)**: 
  - ① 기전 확인 — 로컬/단위 수준 재현: 느린 조회(예: 응답하지 않는 DNS 이름 · 가짜 `lookup`)로 threadpool 을 채우고 `localhost` 로의 `fetch` 가 지연 · `UND_ERR_CONNECT_TIMEOUT` 이 되는지. 대조군 = 같은 부하에서 `UV_THREADPOOL_SIZE` 를 키우거나 IP 리터럴을 쓰면 사라지는지.
  - ② production 영향 — Vercel `kanggle-console` · 데모 호스트(`infra/demo/demo.env`)에서 콘솔 서버가 쓰는 도메인 주소가 전부 설정돼 있는지, 그리고 그중 풀리지 않거나 느리게 풀리는 것이 있는지. 저장소에는 Vercel 환경 값이 없다 — 소유자 확인 또는 배포 로그로 잰다.
  - ③ 자기 자신 호출이 왜 HTTP 인지 — 같은 프로세스 안의 조합을 함수로 부를 수 있는지(`TASK-PC-FE-302` 의 설계 이유 확인).
- AC-0 결과로 소유자에게 수선 선택지를 추천과 함께 묻는다. 후보:
  - (가) 다리 조회를 취소 가능하게(예: `lookup` 에 타임아웃) 또는 IP/사전 해석
  - (나) 자기 자신 HTTP 호출을 함수 호출로 대체
  - (다) `UV_THREADPOOL_SIZE` 상향(완화일 뿐)
  - (라) 설정 누락을 기동 시 거절(필수 env 검증)

## Out of Scope

- e2e 설정 수정(`TASK-PC-FE-341` — 완료).
- 다리 타임아웃 4 s 값 변경.

# Acceptance Criteria

- [x] **AC-0** (2026-10-19 UTC — 아래 § AC-0 측정. ① 로컬 재현됨(Windows · Node 20.19.5/24.14.0, Linux/musl 미측정) · ② 표 · ③ 판독) — 위 ①②③ 을 각각 «재 봤다 / 못 쟀다(이유)» 로 적는다. ① 이 재현되지 않으면 341 의 판정 문구 «기전은 가설» 을 그대로 두고, 이 티켓은 ② 결과만으로 닫을지 소유자에게 묻는다.
- [ ] **AC-1** — 소유자 결정(선택지 + 추천)을 적는다.
- [ ] **AC-2** — 결정한 수선 + 빨강 먼저 시험(① 의 재현이 시험이 된다) + 대조군.
- [ ] **AC-3** — 배포 상태(Vercel · 데모)를 따로 적는다.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (조합 · SSR)
- `docs/adr/ADR-MONO-081-*` (조합이 콘솔 서버 안으로 들어온 결정)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 (조합 다리 · 저하)

# Edge Cases

- 데모 호스트는 `*.local` 을 Traefik/hosts 로 푼다 — 거기서는 이 결함이 안 보일 수 있다(«안 보임» ≠ «없음»).
- Vercel 서버리스는 요청마다 프로세스가 다를 수 있다 — threadpool 점유가 요청 사이에 이어지는지 자체가 측정 대상이다.

# Failure Scenarios

1. 기전을 재지 않고 (다) 만 넣는다 — 숫자만 키워 증상을 미룬다.
2. 341 의 조작 실험 결과를 «threadpool 이 원인» 으로 옮겨 적는다 — 실험이 확정한 것은 «그 주소가 원인» 까지다.
3. e2e 에서 재현이 안 된다고(이제 주소가 막혀 있으니) 결함이 없다고 닫는다.

# AC-0 측정 (2026-10-19 UTC)

> 측정만 했다. 코드 · 설정 · Vercel · AWS 는 바꾸지 않았다. 재현 스크립트는 저장소 밖 세션 scratchpad(`…/scratchpad/pcfe350/` — `probe.js` · `driver.js` · `probe-abort.js` · `summarize.js`, 결과 `run-node20.tsv` · `run-node24.tsv` · `run-abort-node20.txt`)에만 있다. **임시라 사라질 수 있다** — 아래 설계만으로 다시 짤 수 있게 적는다.

## ① 기전 재현 — ✅ **재 봤다 (Windows 호스트 한정)**

**환경**: Windows 11 호스트, Node **20.19.5**(이미지와 같은 major — npx 캐시에 이미 있던 바이너리를 오프라인으로 실행) + 호스트 Node **24.14.0**(교차 확인). 🔴 Docker 데몬이 꺼져 있어(`docker version` → `npipe … docker_engine` 없음) **`node:20-alpine`(musl) 안에서는 못 쟀다.** WSL Ubuntu 에는 node 가 없다.

**설계**: 매 반복이 새 프로세스(`UV_THREADPOOL_SIZE` 는 기동 전에만 먹는다). `127.0.0.1` 에만 바인드한 로컬 HTTP 서버(= `HOSTNAME=0.0.0.0` 콘솔처럼 IPv4 전용)를 띄우고, 부하를 건 뒤 100 ms 에 `fetch('http://<target>:<port>/')` 의 시간 · `err.cause.code` 를 잰다. 50 ms 간격 타이머로 **이벤트 루프 지연(max lag)** 도 같이 잰다 — «루프가 멈췄다» 와 «threadpool 대기» 를 가르는 대조다.

부하 두 종류를 썼다:
- **결정적 부하** `crypto.pbkdf2` (threadpool 작업, 길이를 보정해 ~5 s · ~12 s). 호스트 DNS 에 기대지 않으려고 썼다.
- **실제 느린 조회** `dns.lookup('nx-pcfe350-test.local')`. 🔴 `ecommerce.local` 은 이 호스트에서 **hosts 파일로 풀린다**(18 ms → `127.0.0.1`) — 그래서 못 쓴다. 대신 hosts 에 없는 `.local` 이름을 썼다: 단독 조회 **2785 ms 뒤 `ENOTFOUND`**(Windows 의 `.local` mDNS/LLMNR 대기). 대조: `nx-pcfe350.invalid` 90 ms, `localhost` 11 ms(`::1`, `127.0.0.1`).

### 표 1 — pool 부하 + `localhost` fetch (Node 20.19.5, 반복별)

| 조건 | pool | 부하 | 대상 | 결과 (반복별) | max loop lag |
|---|---|---|---|---|---|
| (a) 부하 없음 | 4 | — | `localhost` | OK 70 · 111 · 131 ms | 41–86 ms |
| pbkdf2 포화 ~12 s | 4 | 4 작업 (11.7–12.8 · 10.6–11.5 · 10.4–11.0 s) | `localhost` | **`TypeError/UND_ERR_CONNECT_TIMEOUT` 10670 ms** · OK 10508 · OK 10358 ms | 32–71 ms |
| pbkdf2 포화 ~5 s | 4 | 4 작업 (4.1–5.5 s) | `localhost` | OK 5403 · 4451 · 4031 ms | 23–38 ms |
| pbkdf2 비포화 | 4 | **3** 작업 ~12 s | `localhost` | OK 98 · 81 · 68 ms | 16–57 ms |
| (b) pool 16 | 16 | 4 작업 ~12 s | `localhost` | OK 154 · 152 · 144 ms | 45–88 ms |
| (b′) pool 16 을 16 작업으로 포화 | 16 | 16 작업 (23.7–28.7 s) | `localhost` | **`UND_ERR_CONNECT_TIMEOUT` 11011 · 10993 ms** | 142–148 ms |
| (c) IP 리터럴 | 4 | 4 작업 ~12 s | `127.0.0.1` | OK 123 · 125 · 133 ms | 39–60 ms |

- fetch 시간이 **«칸이 비는 시각」을 따라간다**(5 s 부하 → 4.0–5.4 s, 12 s 부하 → 10.4–10.7 s). 칸이 10 s 안에 비면 늦게 성공하고, 안 비면 10 s 에 `UND_ERR_CONNECT_TIMEOUT` 이다. 포화 ~12 s 의 성공 2 회는 보정 오차로 작업이 10.4–11.5 s 에 끝나 경계에 걸린 것이다(작업 종료 시각과 fetch 성공 시각이 0.1 s 안에서 맞는다).
- ⇒ **undici 의 10 s connect timeout 은 이름 조회 대기를 포함한다** — 소켓 연결이 아니라 `getaddrinfo` 가 칸을 기다리는 동안 시계가 간다.
- 이벤트 루프 lag 은 모든 조건에서 ≤148 ms 다 ⇒ 막힌 것은 **루프가 아니라 threadpool 대기열**이다.
- 대조군 (a)(b)(c) 가 갈랐다: 부하 없음 · 칸 여유 · 조회 없음(IP) 셋 다 빠르다. 그리고 (b′) — **pool 을 키워도 그만큼 채우면 그대로 재현된다.**

### 표 2 — 실제 느린 조회로 채우기 (Node 20.19.5)

동시 K 개의 `dns.lookup('nx-pcfe350-test.local')` 를 12 s 동안 끝나는 대로 다시 낸다.

| 조건 | pool | K | 대상 | fetch (반복별) | 그 조회들 |
|---|---|---|---|---|---|
| K=4 | 4 | 4 | `localhost` | OK 5095 · 4707 · 4582 ms | p50 ~4.6 s, 전부 `ENOTFOUND` |
| **K=16** (대기열 깊이) | 4 | 16 | `localhost` | **`UND_ERR_CONNECT_TIMEOUT` 10745 · 10661 · 10701 ms** | p50 ~16.2 s(대기 포함) · max ~18.6 s |
| K=16 + pool 32 | 32 | 16 | `localhost` | OK 907 · 930 ms | p50 ~0.89 s |
| K=16 + IP 리터럴 | 4 | 16 | `127.0.0.1` | OK 171 · 117 ms | p50 ~16.2 s |
| K=4 + pool 16 | 16 | 4 | `localhost` | OK 109 · 109 ms | p50 ~2.34 s |
| K=4 + IP 리터럴 | 4 | 4 | `127.0.0.1` | OK 106 · 105 ms | p50 ~4.6 s |

- 조회가 칸 수보다 많이 몰리면(K=16 > pool 4) **`localhost` 조회가 그 뒤에 줄을 서고 ~10.7 s 에 `UND_ERR_CONNECT_TIMEOUT`** — 341 의 로그 모양(~10 s · `transport:TypeError/UND_ERR_CONNECT_TIMEOUT`)과 같다. 3/3.

### 표 3 — 🔴 다리가 4 s 에 **포기해도** 칸은 안 풀린다 (조합 다리 흉내, Node 20.19.5)

`probe-abort.js`: K 개의 `fetch('http://nx-pcfe350-test.local/x')` 를 각자 `AbortController` 4 s(= `LEG_TIMEOUT_MS`)로 끊는다. **그 K 개가 모두 reject 된 다음에** 자기 서버를 부른다.

| 조건 | 다리 결과 | 다리 끝 | 그 **뒤** 자기 자신 fetch |
|---|---|---|---|
| K=16 · pool 4 · `localhost` (×3) | TIMEOUT 14 · `ENOTFOUND` 2 | 4.07–4.12 s | **`UND_ERR_CONNECT_TIMEOUT` 10186 · 10243 · 10224 ms** |
| K=16 · pool 4 · `127.0.0.1` (×2) | 같음 | 4.06 s | OK 54 · 82 ms |
| K=16 · pool 32 · `localhost` (×2) | `ENOTFOUND` 16 (즉시 `DOWNSTREAM_ERROR` 모양) | 2.37–2.44 s | OK 54 · 53 ms |
| K=2 · pool 4 · `localhost` (×2) | `ENOTFOUND` 2 | 2.38–2.40 s | OK 34 · 60 ms |

- ⇒ `AbortController` 는 fetch 를 reject 시킬 뿐, **이미 돌거나 대기열에 있는 `getaddrinfo` 를 취소하지 않는다.** 다리가 «4 s 에 끝났다» 고 로그를 남긴 뒤에도 그 조회들이 칸을 계속 잡고, 다음 요청의 `localhost` 조회가 10 s 를 다 쓴다. 이것이 «4 s 다리 타임아웃이 막지 못한다» 의 실측이다.
- 🔵 부수 관찰: pool 이 넉넉하면 같은 이름이 2.4 s 에 `ENOTFOUND` 로 끝나 다리가 `DOWNSTREAM_ERROR` 가 된다. 341 의 e2e 에서 E-Commerce 다리가 **매번 `TIMEOUT` ~4000 ms** 였다는 것은 그 컨테이너에서 조회(대기 포함)가 매번 ≥4 s 였다는 뜻이다 — 조회가 빨리 실패했다면 WMS/SCM/ERP 처럼 즉시 `DOWNSTREAM_ERROR` 였을 것이다. 🔴 이것은 341 로그에서 **추론**한 것이지 컨테이너에서 잰 값이 아니다.

### Node 24.14.0 교차 확인 (`run-node24.tsv`)

(a) OK 252 · 118 · 142 ms · pbkdf2 포화 OK 8737 · 8154 · 10522 ms(작업이 8.2–11.5 s 에 끝남 — 칸 비는 시각을 따라감) · (b) pool 16 OK 127 · 132 · 90 ms · (c) IP OK 71 · 57 · 63 ms · **DNS K=16 → `UND_ERR_CONNECT_TIMEOUT` 10761 · 10794 · 10747 ms** · K=16 + pool 32 OK 856 · 896 ms · K=16 + IP OK 79 · 68 ms. 같은 모양이다.

### ① 이 확정한 것 / 확정하지 않은 것

- ✅ **기전은 성립한다**(Node 20 · 24, Windows): 취소되지 않는 조회가 threadpool 을 채우면, 타임아웃 없는 `localhost` fetch 가 조회 대기만으로 10 s `UND_ERR_CONNECT_TIMEOUT` 이 된다. IP 리터럴은 조회를 안 해서 영향이 없다. pool 상향은 여유가 남는 동안만 막는다.
- ⚪ **못 쟀다 — 컨테이너(`node:20-alpine` · musl)에서 `ecommerce.local` 조회 한 번이 얼마나 걸리는가.** Docker 데몬이 꺼져 있었다. musl 리졸버의 타임아웃 · 재시도 · search 도메인, Docker 내장 DNS(127.0.0.11) 전달 경로가 Windows 와 다르다. 그래서 «341 의 e2e 사건이 **이** 기전이었다» 는 여전히 판정이 아니라 **가장 그럴듯한 설명**이다(Failure Scenario 2) — 다만 이제 기전 자체는 가설이 아니다. 다음 측정: 컨테이너 안에서 `getent hosts ecommerce.local` 시간, 그리고 표 3 스크립트를 `docker run --rm node:20-alpine` 으로.
- ⚪ 못 쟀다 — Vercel 함수 안에서 threadpool 점유가 **요청 사이로 이어지는가**(Edge Case 2). 같은 인스턴스가 동시 요청을 받는 fluid compute 면 이어질 수 있다. 저장소에서 잴 수단이 없다.

## ② production 영향

### 콘솔 서버가 읽는 백엔드 주소 — `env.ts` 스키마를 줄 단위로 읽어 짝을 맞췄다

`.local` 기본값 **14개**(도메인 13 + 자기 오리진 1) · 기본값 없는 필수 2개(`OIDC_ISSUER_URL` · `OIDC_REDIRECT_URI`) · 선택 1개(`CONSOLE_PUBLIC_ORIGIN`). `env.ts` 밖에서 `process.env` 로 읽는 주소는 `OTEL_EXPORTER_OTLP_ENDPOINT`(unset 이면 SDK 미기동) · `self-origin.ts` 의 두 줄뿐이다.

| 변수 | 코드 기본값 | 합성 다리? | e2e (`docker-compose.e2e.yml`) | base (`docker-compose.yml`) |
|---|---|---|---|---|
| `IAM_ADMIN_API_BASE` | `http://iam.local` | ✅ 개요 · 상태 | `admin-service:8085` | `${…:-http://iam.local}` |
| `CONSOLE_REGISTRY_URL` | `http://iam.local/api/admin/console/registry` | 레이아웃 | `admin-service:8085/…` | `${…:-iam.local…}` |
| `CONSOLE_TOKEN_EXCHANGE_URL` | `http://iam.local/api/admin/auth/token-exchange` | 로그인 · 갱신 | `admin-service:8085/…` | `${…:-iam.local…}` |
| `CONSOLE_ONBOARDING_URL` | `http://iam.local/api/admin/onboarding/organizations` | — | `admin-service:8085/…` | `${…:-iam.local…}` |
| `WMS_ADMIN_BASE_URL` | `http://wms.local/api/v1/admin` | ✅ | `127.0.0.1:9/…` | `${…:-wms.local…}` |
| `WMS_OUTBOUND_BASE_URL` | `http://wms.local/api/v1/outbound` | — | 🔴 **unset → `wms.local`** | `${…:-wms.local…}` |
| `SCM_GATEWAY_BASE_URL` | `http://scm.local` | ✅ | `127.0.0.1:9` | `${…:-scm.local}` |
| `FINANCE_BASE_URL` | `http://finance.local` | ✅ | `finance-account-service:8080` | `${…:-finance.local}` |
| `LEDGER_BASE_URL` | `http://finance.local` | — | 🔴 **unset → `finance.local`** | `${…:-finance.local}` |
| `ERP_BASE_URL` | `http://erp.local` | ✅ (+ 알림 인박스 `erp`) | `127.0.0.1:9` | `${…:-erp.local}` |
| `ECOMMERCE_ADMIN_BASE_URL` | `http://ecommerce.local/api/admin` | ✅ | `127.0.0.1:9/…` (341 수선) | `${…:-ecommerce.local…}` |
| `ECOMMERCE_PUBLIC_BASE_URL` | `http://ecommerce.local/api` | — | `127.0.0.1:9/…` (341 수선) | `${…:-ecommerce.local…}` |
| `FAN_GATEWAY_BASE_URL` | `http://fan-platform.local` | — | 🔴 **unset → `fan-platform.local`** | `${…:-fan-platform.local}` |
| `NEXT_PUBLIC_APP_URL` (자기 오리진 폴백) | `http://console.local` | 자기 자신 fetch | `http://localhost:3000` | 미지정 (`CONSOLE_PUBLIC_ORIGIN` 이 먼저) |
| `CONSOLE_PUBLIC_ORIGIN` | 없음(선택) | 자기 자신 fetch | unset → `localhost:3000` | `${…:-http://console.local}` |

- e2e 에 남은 `.local` 셋(`WMS_OUTBOUND` · `LEDGER` · `FAN`)은 합성 다리가 아니고, e2e spec 중 그 화면으로 `goto` 하는 것은 0건이다(`tests/e2e` grep). 그래서 지금 e2e 에서는 조회가 안 생긴다. 🔴 그러나 341 의 가드(`tests/unit/e2e-compose-composition-bases.test.ts`)는 **합성 다리만** 모집단으로 삼으므로, 누가 그 화면을 e2e 로 열면 같은 일이 조용히 돌아온다.
- base compose(로컬 `pnpm console:up`): 주석에 따르면 `*.local` 은 Traefik 컨테이너의 네트워크 alias 로 Docker 내장 DNS 가 답한다(guard (i)). ⚪ 이 측정에서 풀리는 속도는 재지 않았다.

### 환경별 판정

| 환경 | 콘솔이 거기서 도는가 | 도메인 주소 | 이 결함에 노출되는가 |
|---|---|---|---|
| **데모 호스트** (`infra/demo/demo.env`) | ❌ **안 돈다.** `infra/demo/projects.sh` 의 `NOT_DEMO_COMPOSE` 에 `projects/platform-console/docker-compose.yml` 이 사유와 함께 있다(«console-web 은 Vercel 에서 돈다 · BFF 은퇴 — `TASK-MONO-757`»). `demo.env` 의 콘솔 블록도 같은 이유로 지워졌다(그 파일 L202–219) | 해당 없음 | **없음** (돌지 않으므로) |
| **Vercel `kanggle-console`** | ✅ | 🔵 **설계상 도메인 13개를 설정하지 않는다.** `VERCEL.md` 체크리스트의 env 는 `DEMO_API_BASE` · `CONSOLE_PUBLIC_ORIGIN` · `OIDC_ISSUER_URL` · `OIDC_REDIRECT_URI` · `OIDC_CLIENT_ID` 다섯뿐이고, 도메인 주소는 코드 기본값 `*.local` 을 `resolveBackendUrl()` 이 런타임에 `.<데모 IP>.sslip.io` 로 바꾼다(`shared/config/demo-backend.ts`). **데모가 `running` 이 아니거나 `/status` 가 실패하면 설정값 그대로 — 즉 `http://*.local` 을 그대로 부른다** | ⚪ **소유자 확인 필요.** 데모가 꺼진 시간에는 모든 도메인 다리 · 화면이 `*.local` 을 조회한다. `.local` 은 mDNS 예약 TLD 라 공용 DNS 에는 없다 — 문제는 **빨리 실패하느냐(NXDOMAIN) 느리게 실패하느냐**인데, Vercel(AWS Lambda) 리졸버에서의 시간은 저장소에서 잴 수 없다. 데모가 켜진 동안에도 조회 대상은 공용 와일드카드 DNS(sslip.io)라 그 DNS 가 느리면 같은 축이다. 그리고 Vercel 의 `selfOrigin()` 은 `https://console.hubwang.com` 이라 **자기 자신 fetch 도 공용 DNS 조회 + 엣지 왕복**이다 — 같은 프로세스의 `.local` 조회 뒤에 줄을 설 수 있다 |
| e2e | ✅ (CI) | 위 표 | 341 수선으로 합성 다리는 막힘. 비합성 셋은 위 🔴 |

### 🙋 소유자가 Vercel `kanggle-console`(Production · Preview)에서 볼 것

1. **설정돼 있어야 하는 것**: `DEMO_API_BASE` · `CONSOLE_PUBLIC_ORIGIN` · `OIDC_ISSUER_URL` · `OIDC_REDIRECT_URI` · `OIDC_CLIENT_ID` — 있는지, 값이 `VERCEL.md` 와 같은지.
2. **설정돼 있지 않아야(또는 있으면 그 값을 알려 줘야) 하는 것**: `IAM_ADMIN_API_BASE` · `CONSOLE_REGISTRY_URL` · `CONSOLE_TOKEN_EXCHANGE_URL` · `CONSOLE_ONBOARDING_URL` · `WMS_ADMIN_BASE_URL` · `WMS_OUTBOUND_BASE_URL` · `SCM_GATEWAY_BASE_URL` · `FINANCE_BASE_URL` · `LEDGER_BASE_URL` · `ERP_BASE_URL` · `ECOMMERCE_ADMIN_BASE_URL` · `ECOMMERCE_PUBLIC_BASE_URL` · `FAN_GATEWAY_BASE_URL` · `NEXT_PUBLIC_APP_URL`. 하나라도 `.local` 이 아닌 값으로 설정돼 있으면 그 도메인은 데모 파생을 안 타므로 따로 적어야 한다.
3. `UV_THREADPOOL_SIZE` 가 설정돼 있는지.
4. (가장 값이 큰 측정) **데모가 꺼진 시간대의 런타임 로그**에서 `console_composition_leg` 의 `reason` — `DOWNSTREAM_ERROR`(조회가 빨리 실패) 인지 `TIMEOUT` ~4000 ms(조회가 느림) 인지, 그리고 `operator_overview_bff_unavailable` / `domain_health_bff_unavailable` 의 `cause` 가 나오는지. 이것이 «Vercel 에서 `.local` 이 느리게 실패하는가» 를 재는 유일한 길이다.

## ③ 왜 자기 자신을 HTTP 로 부르는가 — ✅ **판독했다**

- 그 모양은 **설계 결정이 아니라 물려받은 것**이다. `TASK-PC-FE-011` 때 `/api/console/dashboards/operator-overview` 라우트는 console-bff 로 가는 **프록시**였고(AC-19, `CONSOLE_BFF_URL`), SSR 페이지와 브라우저 `<RetryButton>` 이 같은 라우트를 불렀다. `TASK-PC-FE-302`(ADR-MONO-081)가 라우트 안을 프록시 → **같은 프로세스 안의 합성**으로 바꿨지만, Scope 에 «화면 컴포넌트(선 모양이 같으므로 변경 0이어야 한다)» 라고 적어 페이지 쪽 호출은 손대지 않았다. ADR-MONO-081 · 302 · 303 · 계약 어디에도 «SSR 은 HTTP 를 거쳐야 한다» 는 요구는 없다(grep `self-fetch|in-process|selfOrigin|자기 자신` — specs 0건).
- 라우트가 하는 일을 줄 단위로 읽었다(`operator-overview/route.ts` · `domain-health/route.ts`): `sampleGate()` → `getActiveTenant()` · 토큰 읽기(`getAccessToken` · `getOperatorToken` · `getDomainFacingToken`) → `operatorOverviewLegs()` / `domainHealthLegs()` → `compose(…, fetchLeg = fetch(resolveBackendUrl(url)))`. 전부 `next/headers` 의 `cookies()` **읽기**이고 쿠키 쓰기 · 토큰 교환 · 캐시가 없다(`session.ts` L222–331, 응답은 `cache: 'no-store'`). 샘플 응답도 같은 프로세스의 합성 값이다(`shared/api/sample-gate.ts`). Server Component 안에서도 `cookies()` 읽기는 된다.
- ⇒ **함수로 부를 수 있다.** HTTP 왕복이 주는 것은 «라우트와 같은 코드를 한 번만 쓴다» 뿐이고, 그것도 공용 함수로 얻을 수 있다. 대신 이 왕복이 만든 비용은 기록돼 있다: 쿠키를 손으로 복사해야 했고(`TASK-PC-FE-030` · `-037` — 빠뜨리면 `NO_ACTIVE_TENANT`), 타임아웃 없는 fetch 하나 · `localhost`(Vercel 에선 공용 호스트명) 조회 하나 · undici 10 s connect timeout 이 추가됐으며, Vercel 에서는 함수 호출이 하나 더 생긴다.
- 🔴 바꿀 때 지켜야 할 불변식: `tests/unit/sample-fetch-allowlist.test.ts` 가 «`sampleGate(` 가 첫 토큰 읽기 · 첫 `fetch(` 보다 앞» 을 파일 목록으로 고정한다 — 공용 함수로 옮기면 그 목록과 시험이 함께 움직여야 한다. 그리고 브라우저 `<RetryButton>` 은 계속 라우트가 필요하다(라우트는 남는다).
- `domain-health` 도 같다(`domain-health-state.ts` → `fetchDomainHealth(cookie, selfOrigin())` → 같은 모양의 라우트). `selfOrigin()` 을 쓰는 SSR 호출은 이 둘뿐이다(`shared/api/client.ts` 는 주석에서만 언급).

## 추천 (소유자 결정 대기 — AC-1)

잰 것에만 기대어 적는다.

| 선택지 | 기대는 근거 | 막는 것 | 못 막는 것 |
|---|---|---|---|
| **(나) 자기 자신 HTTP 호출 → 함수 호출** | 표 1 (c) · 표 2 · 표 3 의 IP 대조군: **조회를 안 하면 대기열에 안 선다.** ③: 기술적으로 막는 것이 없다 | 개요 · 상태의 «통합 개요를 일시적으로 불러올 수 없습니다» 전체 저하(341 의 사건 모양) — 조회 하나 · 10 s timeout 하나가 통째로 사라진다 | 다른 도메인 다리 · 레이아웃(registry · token-exchange)의 조회도 같은 대기열에 선다 — 그쪽은 다리 타임아웃(4–5 s)으로 **카드 저하**까지는 간다 |
| **(가) 조회를 시간 제한 · 사전 해석** | 표 3: `AbortController` 는 조회를 못 끊는다 ⇒ 느린 조회가 칸을 잡는 시간 자체를 줄여야 한다 | 원인 쪽(느린 이름 하나가 칸을 오래 잡는 것). 모든 다리 · 화면에 효과 | 🔴 `getaddrinfo` 는 시작하면 취소가 안 되므로 «타임아웃» 은 기다림만 끊고 칸은 그대로다 — 실효가 있으려면 threadpool 을 안 쓰는 해석(예: `dns.resolve*` 계열 = c-ares, 또는 undici `connect.lookup` 교체)이어야 한다. 그 경로가 musl · Vercel 에서 어떻게 도는지는 **안 쟀다** |
| **(라) 설정 누락을 기동 시 거절** | ② 의 e2e 표: 원래 결함은 «한 줄 빠짐» 이었다 | 컨테이너 배포(e2e · base)에서 빠진 키 | 🔴 **Vercel 에는 그대로 적용할 수 없다** — Vercel 은 도메인 13개를 *일부러* 비워 `.local` 기본값을 런타임에 파생한다(②). «필수» 로 만들면 그 설계를 깬다. 그리고 «설정됨 ≠ 빨리 풀림»(DNS 장애)은 못 막는다 |
| (다) `UV_THREADPOOL_SIZE` 상향 | 표 1 (b) · 표 2 pool 32 는 빠르다 | 여유가 남는 동안의 증상 | 표 1 (b′): pool 16 을 16 개로 채우면 **그대로 10 s timeout**. 완화일 뿐(Failure Scenario 1) |

**추천: (나) 를 먼저, (가) 를 조사 항목으로.** (나) 는 잰 대조군(IP 리터럴 = 조회 없음)이 직접 뒷받침하고, 바뀌는 면이 두 SSR 진입점뿐이며, 341 이 본 사건 모양 전체를 없앤다. (가) 는 원인 쪽이지만 «취소 가능한 해석» 이 musl/Vercel 에서 어떻게 도는지 재지 않았으므로 측정 티켓으로 둔다. (라) 는 컨테이너 배포에 한해 의미가 있고 Vercel 설계와 충돌한다 — 하려면 «Vercel 에서는 `DEMO_API_BASE` 가 있을 때만 `.local` 허용» 같은 조건부여야 하고 그것은 별도 결정이다. (다) 는 단독으로 넣지 않는다.

**아직 모르는 것**: ⓐ 컨테이너(musl)에서 `ecommerce.local` 한 번의 조회 시간 → 341 사건을 이 기전으로 **귀속**할 근거(Docker 필요) · ⓑ Vercel 에서 `.local` 이 빨리 실패하는가(소유자 로그 4번) · ⓒ Vercel 인스턴스 안에서 점유가 요청 사이로 이어지는가.
