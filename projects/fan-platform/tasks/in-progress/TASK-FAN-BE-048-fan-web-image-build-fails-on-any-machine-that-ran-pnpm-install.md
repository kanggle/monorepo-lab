# Task ID

TASK-FAN-BE-048

# Title

`DEMO_BUILD=1 demo-up.sh fan` 이 빌드에서 죽는다 — `.dockerignore` 가 여덟 프로젝트 중 **팬만** 없다

# Status

in-progress

# Owner

fan-platform

# Task Tags

- infra
- docker
- demo

---

# Goal

팬 도메인은 **로컬에서 한 번이라도 `pnpm install` 을 한 사람에게 100% 기동 불가**다.
그리고 **CI 는 이것을 원리적으로 볼 수 없다.**

실측 (2026-08-16, `DEMO_BUILD=1 bash infra/demo/demo-up.sh fan`):

```
target fan-platform-web: failed to solve:
  process "/bin/sh -c pnpm --filter fan-platform-web build" did not complete successfully: exit code: 1
  Error: Cannot find module '/app/web/fan-platform-web/node_modules/next/dist/bin/next'
```

**`pnpm install` 자체는 성공했다** — `Progress: resolved 576, reused 369, downloaded 207,
added 576, done` · `Done in 3m 37.2s`. 죽는 것은 **그 다음 레이어**다:

```
#75 [fan-platform-web builder 7/9] COPY web/fan-platform-web/ web/fan-platform-web/
#75 DONE 25.0s        ← 소스 디렉터리 COPY 에 25초
#76 [fan-platform-web builder 8/9] RUN pnpm --filter fan-platform-web build
#76 3.119 Error: Cannot find module '…/node_modules/next/dist/bin/next'
```

호스트의 `projects/fan-platform/web/fan-platform-web/node_modules` 가 **빌드 컨텍스트에 실려
들어와**, 컨테이너 안에서 pnpm 이 만든 심링크 트리 **위에 덮어쓴다** ⇒ `next` 바이너리 소실.
(25초라는 COPY 시간 자체가 그 증거다 — 소스만이면 그렇게 걸리지 않는다.)

---

# 🔴 모집단을 세면 낙오가 정확히 하나다

저장소의 `.dockerignore` 전수:

| 파일 | 그것이 지키는 빌드 컨텍스트 | `node_modules` 제외 |
|---|---|---|
| `/.dockerignore` | 저장소 루트 | ✅ `**/node_modules` |
| `projects/iam-platform/.dockerignore` | iam 루트 | ✅ |
| `projects/ecommerce-microservices-platform/.dockerignore` | ecommerce 루트 | ✅ |
| `projects/platform-console/apps/console-web/.dockerignore` | **그 앱 디렉터리** = console-web 의 컨텍스트 | ✅ |
| **없음** | **`projects/fan-platform/`** = fan-platform-web 의 컨텍스트(pnpm 워크스페이스 루트) | ❌ |

console 은 컨텍스트가 `./apps/console-web` 이고 **거기에** `.dockerignore` 가 있어 지켜진다.
fan 은 컨텍스트가 프로젝트 루트인데(lockfile·workspace 파일이 거기 있어 그렇게 잡은 것이고,
그 판단 자체는 Dockerfile 주석에 근거까지 적혀 있다) **그 자리에 파일이 없다.**

⇒ **형제 파리티 낙오 1건.** 세 프로젝트가 같은 함정을 알고 파일을 두었고, 팬만 빠졌다.

---

# 🔴 왜 CI 가 못 잡나 — 깨끗한 체크아웃에서 영구 초록

러너는 새 체크아웃이라 `node_modules` 가 **없다** ⇒ `COPY` 가 덮어쓸 것이 없고 빌드가
통과한다. 결함은 **개발자 머신에만 산다** — 그리고 데모를 띄우는 곳이 정확히 거기다.

`env_fresh_volume_ci_is_permanently_green_on_migration_order` 와 같은 축의 결함이다:
**판정 환경이 결함을 만들 수 없는 환경**이라 초록이 아무것도 뜻하지 않는다.

🔵 워크스루 §6 한계 대장에도 §7 트러블슈팅에도 **이 증상 행이 없다.**

---

# Scope

## In Scope

- `projects/fan-platform/.dockerignore` 신설. 형제(`ecommerce`)를 기준선으로 삼되,
  **팬의 컨텍스트에 맞게** 정한다(팬은 gradle 앱 + pnpm 웹이 한 프로젝트에 있다).
  최소한: `**/node_modules` · `**/.next` · `**/.turbo`.
  🔴 **`**/build` 를 넣을 때는 `!apps/*/build/libs` 예외를 반드시 함께** — 백엔드
  Dockerfile 들이 그 경로의 jar 를 COPY 한다(루트/iam 의 `.dockerignore` 가 이미 그 형태다).
  이 한 줄을 빠뜨리면 **백엔드 5개가 대신 죽는다.**
- 워크스루 §6 에 해소 행 + §7 에 증상 행(*"팬 기동이 `Cannot find module … next` 로 죽는다"*).

## Out of Scope

- **Dockerfile 의 빌드 컨텍스트 변경** — 컨텍스트가 워크스페이스 루트인 데에는 기록된
  이유가 있다(lockfile/workspace 파일 위치). 고칠 자리는 `.dockerignore` 다.
- 다른 프로젝트의 `.dockerignore` — 위 표대로 이미 있다.
- `fan-platform-web` 의 Next/pnpm 버전·설정.

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act)** — 착수 시 **호스트에 `projects/fan-platform/**/node_modules`
      가 있는지 먼저 확인**한다. 없으면 이 결함은 재현되지 않는다 ⇒ **재현 조건을 만든 뒤**
      착수한다(`pnpm install` 1회). 🔴 조건 없이 "고쳤다" 를 선언하면 **아무것도 재지 않은
      실험**이 된다 — `TASK-MONO-534` 가 술어를 네 번 만에 맞춘 그 자리다.
- [ ] **AC-1 (대조군 → 판정)** — 같은 호스트·같은 명령으로 두 칸:
      | 칸 | `.dockerignore` | 기대 |
      |---|---|---|
      | 대조군 | 없음 | `Cannot find module … next` 로 **실패** |
      | 판정 | 있음 | **성공**, `fan-platform-web` 이미지 생성 |
- [ ] **AC-2 (백엔드 회귀 없음)** — 같은 실행에서 백엔드 5개 이미지
      (gateway·community·artist·membership·notification)가 **여전히 구워진다.**
      🔴 `**/build` 제외가 `apps/*/build/libs` 를 삼키면 여기서 잡힌다. 이 칸을 빼지 말 것.
- [ ] **AC-3 (라이브)** — `demo-up.sh fan` 이 **9/9**(web 포함) 로 뜨고
      `http://web.fan-platform.local` 이 응답한다. 판정은 컨테이너 상태가 아니라 **HTTP 응답**.
- [ ] **AC-4 (문서)** — §6 해소 행 + §7 증상 행. `check-walkthrough-ledger-drift` OK.

---

# Related Specs

- `projects/fan-platform/web/fan-platform-web/Dockerfile` (컨텍스트 근거가 헤더에 있다)
- `projects/ecommerce-microservices-platform/.dockerignore` (형제 기준선)
- `/.dockerignore` · `projects/iam-platform/.dockerignore` (`!apps/*/build/libs` 예외 형태)
- `docs/guides/interview-demo-walkthrough.md` § 3 · § 6 · § 7

# Related Skills

N/A — 빌드 설정 1파일.

# Related Contracts

None.

# Target Service

`fan-platform-web` (빌드만; 런타임 코드 무변경).

# Architecture

N/A — 빌드 컨텍스트 위생. ADR 불필요.

---

# Implementation Notes

- 검증은 **반드시 이 호스트에서** 한다. CI 는 이 결함을 만들 수 없다(위 § 참조) ⇒
  *"CI 초록"* 은 AC-1 의 증거가 **아니다.**
- 이 호스트의 도커 컨트롤 플레인은 이미지 다수를 굽는 동안 **Hyper-V 소켓 버퍼 고갈**로
  끊긴다(2026-08-16 검증 중 3회 발생). 끊기면 컨테이너가 `Created` 로 남으므로
  `docker start` 로 하나씩 이어 올리면 된다 — 빌드 실패로 오독하지 말 것.

---

# Edge Cases

- 팬 웹은 `public/` 디렉터리가 없어서 Dockerfile 이 `mkdir -p` 로 만든다 —
  `.dockerignore` 가 그 경로에 영향을 주지 않는지 확인.
- `pnpm-lock.yaml` · `pnpm-workspace.yaml` · 각 `package.json` 은 **제외되면 안 된다**
  (install 레이어가 그것들을 COPY 한다).
- 이미 `fan-platform/web/fan-platform-web/.next` 가 호스트에 있으면 그것도 함께 실린다 ⇒
  제외 목록에 포함.

---

# Failure Scenarios

- `**/build` 를 예외 없이 제외 → 백엔드 5개가 `COPY build/libs/*.jar` 에서 죽는다.
  **결함을 옮기기만 한 것**이 된다(AC-2 가 이것을 잡는다).
- 호스트에 `node_modules` 가 없는 상태에서 검증 → 대조군 칸이 **양쪽 다 성공**하고,
  그 통과는 무효다(틀린 입력도 통과하는 판정).
- `.dockerignore` 를 `web/fan-platform-web/` 에 두는 실수 → 컨텍스트가 프로젝트 루트이므로
  **읽히지 않는다.** 도커는 **컨텍스트 루트의** `.dockerignore` 만 본다.

---

# Test Requirements

- AC-1 두 칸(같은 호스트, 같은 명령, `.dockerignore` 유무만 차등).
- AC-2 백엔드 5개 이미지 존재 확인.
- AC-3 `web.fan-platform.local` HTTP 응답 1회.

---

# Definition of Done

- [ ] `.dockerignore` 신설, AC-0~AC-4 닫힘.
- [ ] 워크스루 §6/§7 갱신, 가드 GREEN.
- [ ] `projects/fan-platform/tasks/INDEX.md` done entry(close chore 시).

---

# Provenance

2026-08-16 라이브 검증에서 발굴(사용자 요청: scm·fan·finance·erp 도 나머지 넷처럼 검증).
팬은 **백엔드 5개 이미지는 정상으로 구워졌고 web 만 실패**했으므로, web 을 뺀 8/8 로 띄워
§3 의 API 주장(디렉터리 3건 · 멤버십 `MEMBERS_ONLY` · PUBLIC 200 / MEMBERS_ONLY 200 /
PREMIUM **403**)은 모두 확인했다. **웹 UI 자체는 이 티켓이 닫히기 전까지 미검증이다.**

분석=Opus 5(1M) / 구현 권장=**Sonnet** (파일 1개 + 대조군 검증).
