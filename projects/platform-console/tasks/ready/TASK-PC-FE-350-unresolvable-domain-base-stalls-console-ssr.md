# Task ID

TASK-PC-FE-350

# Title

풀리지 않는 도메인 주소 하나가 콘솔 SSR 의 자기 자신 호출까지 막는가 — `getaddrinfo` 의 threadpool 점유 가설 측정 · production 영향 확인

# Status

ready

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

- [ ] **AC-0** — 위 ①②③ 을 각각 «재 봤다 / 못 쟀다(이유)» 로 적는다. ① 이 재현되지 않으면 341 의 판정 문구 «기전은 가설» 을 그대로 두고, 이 티켓은 ② 결과만으로 닫을지 소유자에게 묻는다.
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
