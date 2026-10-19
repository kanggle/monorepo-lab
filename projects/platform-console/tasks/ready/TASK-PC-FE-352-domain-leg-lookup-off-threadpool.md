# Task ID

TASK-PC-FE-352

# Title

도메인 다리 · 레지스트리 · 토큰 교환의 이름 조회가 libuv threadpool 을 막지 않게 — `TASK-PC-FE-350` 선택지 (가) 측정 · 결정

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- reliability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (Node DNS · undici `connect.lookup` · musl/Vercel 측정)
> 기안 2026-10-19 UTC — `TASK-PC-FE-350` 의 남은 위험. 급하지 않다: 350 (나) 로 «개요 전체 저하» 경로는 사라졌고, 남은 영향은 카드 하나의 저하다.

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

- [ ] **AC-0** — ①②③ 을 «쟀다 / 못 쟀다(이유)» 로 적는다. 대조군은 lookup 교체 없음 · `127.0.0.1` 리터럴 · pool 크기 상향.
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
