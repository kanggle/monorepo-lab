# TASK-BE-591 — 나이틀리 스택이 minio 를 더 이상 못 받는다 (Docker Hub 익명 접근이 끊겼다)

**Status:** in-progress

**Type:** TASK-BE (project-internal — `projects/ecommerce-microservices-platform/` 안의 이미지 참조만)

**Analysis model:** Opus 5 / **Recommended impl model:** Sonnet (레지스트리 4줄 교체 · 판정은 머지 후 나이틀리 런)

---

## Goal

`main` 이 **여섯 커밋 연속 빨강**이다. 잡은 `Nightly E2E` 워크플로의
**Frontend E2E full-stack (web-store, Playwright + docker compose)** 이고, 실패 지점은 테스트가
아니라 **스택 기동**이다:

```
Error response from daemon: pull access denied for minio/minio,
repository does not exist or may require 'docker login': denied: requested access to the resource is denied
##[error]Process completed with exit code 1.
```

테스트는 **한 개도 안 돌았다** — 컴포즈가 이미지를 못 받아 전 컨테이너가 `Interrupted` 됐고,
뒤이은 `Assert the required specs actually ran` 이 그래서 같이 터졌다(두 번째 실패는 **결과가
아니라 메아리**다).

목표: 그 이미지를 **받을 수 있는 곳**에서 받아 `main` 을 다시 초록으로 돌린다.

---

## 🔴 원인은 우리 저장소 밖이다 — 경계가 «내용» 이 아니라 «시각» 이다

세션 머지를 커밋별로 CI 확인하다가 드러났다. `full-stack e2e` 잡의 이력:

| 커밋 | 결과 | 런 시작(UTC) |
|---|---|---|
| `898aa0a0e` | 🟢 success | 2026-09-11T19:14:26Z |
| `1d1f09c11` | 🟢 success | 2026-09-11T19:14:27Z |
| **`5a000dad6`** | 🔴 **failure** | **2026-09-11T19:37:45Z** |
| `8ad16647d` · `3e03e89e5` · `ae09c6fb2` · `be4d4be10` · `648b9fb2d` | 🔴 failure | ~20:10 – 20:58 |

🔴 **첫 실패 커밋 `5a000dad6` 은 `.github/workflows/ci.yml` 한 곳만 건드린 `fix(ci)` 다** —
컴포즈도, 이미지도, ecommerce 코드도 안 건드렸다. 그리고:

```
git log d3e89a17c..origin/main --name-only -- <minio 를 참조하는 파일 전부>   →   출력 0줄
```

⇒ **이 세션의 어떤 커밋도 minio 참조를 건드리지 않았다.** 경계는 19:14 성공 → 19:37 실패,
즉 **시각**이다. 🔵 내용 경계였다면 그 커밋의 diff 에 원인이 있어야 하는데 없다.

## 실측 — 레지스트리에 직접 물었다 (2026-09-12 UTC)

익명 pull 토큰을 받아 manifest 를 요청했다. **대조군을 같이 뒀다**(같은 방식으로 물어서 200 이
나오는 이미지가 있어야, 401 이 «내 요청 방식» 이 아니라 «그 레포» 의 성질이다):

| 대상 | HTTP |
|---|---|
| `docker.io` `minio/minio:RELEASE.2024-10-13T13-34-11Z` | 🔴 **401** |
| `docker.io` `minio/minio:latest` | 🔴 **401** |
| `docker.io` `minio/minio:RELEASE.2025-04-22T22-12-26Z` | 🔴 **401** |
| `docker.io` `minio/mc:RELEASE.2024-10-08T09-37-26Z` | 🔴 **401** |
| **대조군** `docker.io` `library/postgres:16` | 🟢 **200** |
| Docker Hub 태그목록 API `/v2/repositories/minio/minio/tags` | 🔴 `{"message":"object not found"}` |
| **`quay.io`** `minio/minio:RELEASE.2024-10-13T13-34-11Z` | 🟢 **200** |
| **`quay.io`** `minio/mc:RELEASE.2024-10-08T09-37-26Z` | 🟢 **200** |

🔴 **막힌 것은 태그가 아니라 레포 전체다** — `latest` 도, 더 새 릴리스도 똑같이 401 이고
Hub 의 태그목록 API 는 레포 자체를 «없다» 고 답한다. ⇒ **태그를 올리는 것으로는 안 풀린다.**

🔵 `quay.io` 에는 **정확히 같은 두 태그**가 익명으로 열려 있고, 둘 다 OCI index 안에
`linux/amd64` 를 포함한다(러너가 amd64). `quay.io/minio/minio:RELEASE.2024-10-13T13-34-11Z`
digest = `sha256:9535594ad4122b7a78c6632788a989b96d9199b483d3bd71a5ceae73a922cdfa`.

---

## Scope

`projects/ecommerce-microservices-platform/` 안의 **minio 이미지 참조 4곳 전부**:

| 파일 | 줄 | 현재 |
|---|---|---|
| `docker-compose.yml` | 183 | `image: minio/minio:RELEASE.2024-10-13T13-34-11Z` |
| `docker-compose.yml` | 214 | `image: minio/mc:RELEASE.2024-10-08T09-37-26Z` |
| `k8s/base/storage-minio.yaml` | 59 | `image: minio/minio:RELEASE.2024-10-13T13-34-11Z` |
| `k8s/base/storage-minio.yaml` | 166 | `image: minio/mc:RELEASE.2024-10-08T09-37-26Z` |

🔴 **CI 를 빨갛게 만드는 것은 compose 2줄뿐이지만 네 곳을 다 고친다** — k8s 매니페스트는
CI 가 안 돌려서 «조용히» 같은 결함을 들고 있다. 한 사실이 두 자리에 있으면 한쪽만 고쳐진다.

**Out of scope**: 다른 이미지의 레지스트리 · Docker Hub 인증 도입(아래 ⓑ) · minio 버전 올리기
(버전 이동은 이 티켓의 문제가 아니고 별도 리스크다 — **레지스트리만** 옮긴다).

---

## Acceptance Criteria

### AC-0 — 착수 게이트 (전제부터 다시 재라)

- [x] 위 표의 401/200 을 **다시 재라**. Hub 가 접근을 되돌렸다면(레포 복구) 이 티켓은
      **phantom 이고 아무것도 고치지 말아야 한다** — 그때는 § 판정에 «되돌아왔다» 를 적고 닫아라.
      → 🟢 착수 직전 재측: `docker.io/minio/minio` **여전히 401**. phantom 아니다.
- [x] `main` tip 의 `full-stack e2e` 가 **아직 빨간지** 확인하라. 이미 초록이면 원인이 다른
      것이었다는 뜻이므로 **이 티켓의 진단부터 다시 세워라**.
      → 🟢 `648b9fb2d`(tip) 의 그 잡 = **failure**. 여섯 커밋 연속.

### AC-1 — 네 자리를 `quay.io` 로 옮긴다

- [x] 위 표의 **4줄 전부** `quay.io/` 접두사를 붙인다. **태그는 한 글자도 바꾸지 않는다.**
      → 🟢 네 줄 다. `git diff` = **+9/−4**(4줄 교체 + 사유 주석 5줄), 태그 문자열 무변경.
- [x] `git grep -n "image: minio/"` 가 **0건**이어야 한다(자리를 빠뜨렸는지는 grep 이 판정한다).
      → 🟢 **0건**. 남은 `minio/minio`·`minio/mc` 참조 5건은 전부 `quay.io/` 접두사가 붙은
      것(4건) + 사유 주석 안의 문자열(1건)이다.

### AC-2 — 판정은 머지 후 나이틀리 런이다 (그리고 그걸 «적어라», 넘기지 마라)

- [ ] 🔴 **이 잡은 PR 에서 안 돈다** — `nightly-e2e.yml` 은 `push` 로 `main` 에서만 돈다.
      ⇒ PR 초록은 이 결함에 대해 **아무것도 증명하지 않는다**. 머지 후 그 커밋의
      `Frontend E2E full-stack` 결론을 **직접 열어서** 확인하라.
- [ ] 실패가 **같은 자리(스택 기동)에서 사라졌는지**로 판정하라. 🔵 그 뒤에 다른 자리에서
      깨지면 그건 **이 티켓이 가려 두고 있던 다음 결함**이지 회귀가 아니다 — 따로 기안하라.

### AC-3 — 못 잰 것을 ⚪ 로 남긴다

- [x] 🔴 **Hub 판과 quay 판이 같은 바이트인지는 못 잰다** — Hub 가 이미 닫혀서 대조할 원본이
      없다. 호스트에 캐시된 사본도 없었다(`docker image inspect` → 없음). 이 ⚪ 를 적어라.
      → ⚪ **적었다**(아래 § 검증 「못 잰 것」). 🔵 이 칸의 동사는 「적어라」이고 「재라」가
      아니다 — 그래서 못 잰 채로 닫히는 것이 맞다.
- [x] 🔴 로컬 pull 로도 못 쟀다(이 호스트의 docker 데몬이 안 떠 있었다). 즉 **머지 전 증거는
      레지스트리 API 응답뿐**이다 — 그 사실을 숨기지 말고 적어라.
      → ⚪ **적었다**. `docker info` = `failed to connect … dockerDesktopLinuxEngine`.

---

## Related Specs / Contracts

- `projects/ecommerce-microservices-platform/docker-compose.yml` (§ minio · minio-init)
- `projects/ecommerce-microservices-platform/k8s/base/storage-minio.yaml`
- `.github/workflows/nightly-e2e.yml` — `frontend-e2e-fullstack` 잡 (읽기만; **안 건드린다**)
- 선례: `tasks/done/TASK-BE-406-*.md` — **같은 잡**이 인프라 사유로 빨개진 건

---

## Edge Cases

- **quay.io 도 언젠가 닫힌다.** 이 티켓은 그 축을 해결하지 않는다 — 옮길 뿐이다.
  🔵 근본 처방(사내 미러 / 이미지 pull-through 캐시)은 **비용이 드는 소유자 결정**이라
  여기 끼워 넣지 않는다.
- **`minio-init` 만 고치고 `minio` 를 빠뜨리는 것**(또는 반대). 둘은 다른 레포다
  (`minio/minio` vs `minio/mc`) — AC-1 의 grep 이 그걸 잡는다.
- **arm64 개발 호스트**: quay 의 index 에 `arm64` 도 있으므로 로컬 개발에도 영향 없다.

## Failure Scenarios

1. **가장 위험한 실패 = 머지하고 안 본다.** 이 잡은 PR 에서 안 도니까 «CI 초록」으로 닫으면
   고쳤는지 **모르는 채로** done 이 된다. AC-2 가 그걸 막는다.
2. quay 이미지가 Hub 판과 달라 부팅 동작이 미묘하게 다르다 → AC-2 가 스택 기동으로 잡는다.
   그래도 다르면 AC-3 의 ⚪ 가 그 조사의 출발점이다.
3. 태그를 «겸사겸사» 최신으로 올린다 → 레지스트리 문제와 버전 문제가 **한 커밋에 섞여**
   판정 불가가 된다. Out of scope 가 그것을 금지한다.

---

## 🟢 검증 (2026-09-12 UTC · 구현 PR)

> 🔴 이 절에는 체크박스를 두지 않는다 — 위 § Acceptance Criteria 가 유일한 체크 자리다.

| 축 | 결과 |
|---|---|
| AC-0 재측정 (Hub) | 🟢 여전히 401 — phantom 아님 |
| AC-0 재측정 (`main` tip) | 🟢 `648b9fb2d` 의 `full-stack e2e` = **failure** |
| AC-1 교체 | 🟢 4줄, 태그 무변경 |
| AC-1 grep | 🟢 `image: minio/` **0건** |
| quay 익명 manifest | 🟢 두 태그 **HTTP 200**, OCI index 에 **`linux/amd64` 포함**(러너 아키텍처) |
| **AC-2 판정** | 🟡 **아직 안 났다 — 머지 후에만 난다.** 아래 참조 |

## 🔴 AC-2 는 이 PR 로 안 닫힌다 — 이 티켓을 `in-progress` 에 둔 이유

`nightly-e2e.yml` 은 `on: push` · `branch: main` 이다. ⇒ **이 PR 의 CI 초록은 이 결함에 대해
아무것도 증명하지 않는다.** 판정은 머지 커밋의 `Frontend E2E full-stack` 결론이고, 그것을 보고
나서야 `review → done` 이 성립한다.

🔵 그래서 이 파일은 구현이 끝났는데도 **`review/` 가 아니라 `in-progress/`** 에 있다 —
`review/` 는 frozen 이고, 아직 잴 것이 남은 티켓을 frozen 자리에 두면 그 측정이 갈 곳을 잃는다.

## ⚪ 못 잰 것 (AC-3)

- ⚪ **Hub 판 ↔ quay 판의 바이트 동일성.** Hub 가 이미 닫혀 **대조할 원본이 없다**. 호스트에
  캐시된 사본도 없었다. 🔵 대신 잰 것: 태그 문자열이 같고, MinIO **자신의** 레지스트리이며,
  index 에 같은 아키텍처 집합(`amd64`/`arm64`/`ppc64le`)이 들어 있다. 🔴 **그건 동일성의
  증거가 아니라 «그럴듯함»의 증거다** — 구별해서 적는다.
- ⚪ **로컬 pull·기동.** 이 호스트의 docker 데몬이 꺼져 있었다
  (`failed to connect to the docker API at npipe:…dockerDesktopLinuxEngine`).
  ⇒ 머지 전 증거는 **레지스트리 API 응답뿐**이다.

## 🔵 왜 가드를 안 만드는가 (판단 기록)

«모든 compose 이미지가 익명으로 받아지는가» 가드는 **네트워크에 의존**한다 ⇒ 레지스트리가
느리거나 rate limit 이 걸리면 첫날부터 빨개지고, `TASK-MONO-360` 이 못박은 실패 모드
(*첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다*)에 정면으로 걸린다.
🔵 **이 축을 이미 보고 있는 것이 나이틀리 자신이다** — 그게 이번에 실제로 물었다.
