# TASK-BE-598 — quay.io 의 minio 도 닫혔다 (BE-591 의 우회로가 끊겼다)

**Status:** review

**Type:** TASK-BE (project-internal — `projects/ecommerce-microservices-platform/` 안의 이미지 참조)

**Analysis model:** Opus 5.5 / **Recommended impl model:** Opus 5.5 — 대체 이미지 선정이 판단이다(소유자 결정 가능성). 교체 자체는 몇 줄.

---

## Goal

`TASK-BE-591`(2026-09-12)이 Docker Hub 401 을 피해 minio 이미지를 `quay.io` 로 옮겼는데,
**2026-09-24 에 `quay.io/minio` 도 익명 pull 을 거절한다.** compose 가 참조하는 minio 이미지를
**받을 수 있는 출처**로 바꿔 캐시 없는 환경의 기동을 되살린다.

## 실측 (2026-09-24T13:10Z UTC) — BE-591 과 같은 방식(익명 pull 토큰 → manifest HEAD), 대조군 포함

| 대상 | HTTP |
|---|---|
| `quay.io/minio/minio:RELEASE.2024-10-13T13-34-11Z` | 🔴 **401** |
| `quay.io/minio/mc:RELEASE.2024-10-08T09-37-26Z` | 🔴 **401** |
| `quay.io/minio/minio:latest` | 🔴 **401** |
| **대조군** `quay.io/prometheus/node-exporter:latest` | 🟢 200 |
| **대조군** `docker.io/library/postgres:16` | 🟢 200 |

⇒ 요청 방식이 아니라 **그 레포**의 성질이다. 태그가 아니라 레포 전체(`latest` 포함)가 닫혔다 — 태그 올리기로는 안 풀린다.
🔴 처음엔 quay 의 **태그 조회 API**(`/api/v1/repository/.../tag/`)로 401 을 봤는데 그것은 pull 가능 여부의 증거가 아니다 —
BE-591 의 방식(manifest)으로 다시 재서 확정했다.

## 발견 경로와 영향

- `TASK-MONO-731`(#4003) CI 에서 **`Demo wrapper smoke (infra/demo)`** 가 칸 (h) *«참조 이미지가 레지스트리에 실재하는가»* 로 빨강
  (`FAIL: 레지스트리에서 사라진 이미지: quay.io/minio/mc… quay.io/minio/minio…`). 731 은 CSS 만 바꿨다 — 무관. 필수 체크가 아니라
  소유자 결정으로 병합했다. ⇒ **지금부터 `infra/demo` 를 건드리는 모든 PR 에서 이 잡이 빨갛다.**
- 🔴 **다음 AMI 재굽기가 막힐 수 있다** — packer 가 이미지를 pull 한다면 이 두 이미지에서 실패한다. `TASK-MONO-726` ·
  `TASK-BE-596`(wms) · `TASK-BE-597`(viewer 계정) · `TASK-MONO-730` 이 전부 그 재굽기를 기다린다. ⇒ **재굽기 전에 이 티켓이 먼저다.**
  (현 배포 AMI 는 이미 이미지를 담고 있어 **지금 데모 부팅은 영향 없음** — 추정, AC-0 에서 확인.)
- 나이틀리 `Frontend E2E full-stack (web-store)` 도 캐시가 없으면 BE-591 때와 같이 스택 기동에서 죽는다(확인 필요).

## Scope

참조 4곳(BE-591 이 옮긴 것 그대로):
- `docker-compose.yml:185` minio · `:217` mc
- `k8s/base/storage-minio.yaml:60` minio · `:168` mc

## Acceptance Criteria

- [x] **AC-0** — 착수 직전 재측(위 표와 같은 방식 + 대조군). 여전히 401 인지. 그리고 ① packer/AMI 굽기가 이미지를 pull 하는지
      (`infra/demo/aws/packer/**` 읽기) ② 최근 나이틀리 full-stack 런 결과 — 둘 다 결과를 기록.
      → 🟢 2026-09-24T14:05Z 재측: quay 3건 **여전히 401**, 대조군 2건 200 — phantom 아님. ① **pull 한다**
      (`demo-ami.pkr.hcl:381` `docker compose … pull --ignore-buildable`, 실패 시 `exit 1`) ⇒ 이 수정 없이 재굽기는 그 자리에서 죽는다.
      ② 나이틀리 web-store full-stack **3런 연속 failure**(`unauthorized` at pull). 상세 § 구현 기록.
- [x] **AC-1** — 대체 출처 후보를 **실측**으로 고른다(익명 manifest 200 + `linux/amd64` 포함 + 태그 고정 가능). 후보 예: 다른 공개 미러 ·
      공급자 빌드 이미지 · 소스 빌드 · S3 호환 대체물. 🔴 **MinIO 를 다른 제품으로 바꾸는 선택**이면 API 호환·초기화(mc 로 버킷 생성)
      경로가 달라지므로 **소유자 결정**을 받는다.
      → 🟢 후보 27건 실측. 선택 = `docker.io/bitnamilegacy/minio:2024.10.13-debian-12-r1` + `…/minio-client:2024.10.8-debian-12-r1`,
      **다이제스트 고정**. **제품 교체 아님** — 같은 MinIO 릴리스(바이너리에 `2024-10-13T13:34:11Z` / `2024-10-08T09:37:26Z` 박힘, 실측).
      발행자 Bitnami(Broadcom). 🔴 단 «더 이상 갱신 안 됨» 아카이브 — § 구현 기록 「선택의 대가」.
- [x] **AC-2** — 4곳 교체, 태그/다이제스트 고정. `bash infra/demo/verify-demo-wrapper.sh` 칸 (h) 초록, CI `Demo wrapper smoke` 초록.
      → 🟢 4곳 교체 + 이미지 차이 흡수(compose minio `entrypoint`·`user`, k8s minio `command`, k8s mc `MC_CONFIG_DIR`).
      로컬 `verify-demo-wrapper.sh` **rc=0**, 칸 (h) `커버리지 17/17 (skip 0건)`, 끝까지 「정적 검증 PASS」.
      🟡 CI `Demo wrapper smoke` 는 이 PR 의 체크로 판정한다(경로 필터 `projects/*/docker-compose.yml` 에 걸린다).
- [ ] **AC-3** — 스택 기동 실측: minio + mc 초기화 컨테이너가 버킷을 만들고, 이미지 업로드를 쓰는 서비스 경로 하나가 동작(로컬 또는 CI 로그).
      → ⚪ **이 PR 로는 안 닫힌다.** 로컬 Docker 데몬 없음(`failed to connect … dockerDesktopLinuxEngine`). 기동을 도는 잡은
      `nightly-e2e.yml` 의 `Frontend E2E full-stack (web-store …)` 하나이고 **`main` push 에서만** 돈다. 🔴 그 잡도 minio-init 의
      종료코드·버킷 존재·업로드 경로는 **단언하지 않는다** — § 구현 기록 「AC-3 이 요구하는 것과 CI 가 재는 것」.
- [x] **AC-4** — BE-591 의 교훈대로 «레지스트리 한 곳» 의존이 또 끊길 수 있음을 기록하고, (h) 가 이번처럼 그것을 잡는다는 사실을 적는다.
      → 🟢 적었다(§ 구현 기록 「AC-4」 + compose 주석).

## Related Specs / Contracts

- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-591-the-nightly-stack-cannot-pull-minio-anymore.md`
- 계약 변경 없음(스토리지 엔드포인트 동일 유지가 목표).

## Edge Cases

| 상황 | 기대 |
|---|---|
| 대체 이미지의 기본 자격·포트·경로가 다름 | compose 환경값을 그 이미지 문서에 맞추고 AC-3 으로 확인 |
| 대체물이 `mc` 를 안 줌 | 버킷 초기화를 그 도구로 다시 쓴다 — 초기화 컨테이너가 조용히 성공(rc=0)하고 버킷이 없는 경우를 대조군으로 |

## Failure Scenarios

1. 태그 API 의 401 을 «pull 불가» 로 읽고 멀쩡한 이미지를 바꾼다 → manifest 로 재라(이 티켓이 한 번 그 실수를 할 뻔했다).
2. 재굽기를 먼저 시작 → 굽기 중간(수십 분 뒤)에 pull 실패로 AMI 를 태운다.

---

# 구현 기록 (2026-09-24 UTC · 구현 PR)

> 🔴 이 절에는 체크박스를 두지 않는다 — 위 § Acceptance Criteria 가 유일한 체크 자리다.

## AC-0 — 재측 (2026-09-24T14:05Z UTC)

방식: 인증 없이 `GET /v2/<repo>/manifests/<ref>` → `WWW-Authenticate: Bearer` 의 realm/service 로 **익명 pull 토큰** →
같은 manifest 를 토큰으로 다시 요청(Accept = OCI index / Docker list / 단일 manifest). PowerShell `HttpClient`.

| 대상 | 토큰 | manifest |
|---|---|---|
| `quay.io/minio/minio:RELEASE.2024-10-13T13-34-11Z` | 200 | 🔴 **401** |
| `quay.io/minio/mc:RELEASE.2024-10-08T09-37-26Z` | 200 | 🔴 **401** |
| `quay.io/minio/minio:latest` | 200 | 🔴 **401** |
| **대조군** `quay.io/prometheus/node-exporter:latest` | — | 🟢 200 |
| **대조군** `docker.io/library/postgres:16` | 200 | 🟢 200 |

🔵 CI 의 칸 (h) 는 같은 401 을 `docker manifest inspect` 의 `no such manifest` 로 본다(로컬 재현: 옛 mc 참조 rc=1 `no such manifest`).
그래서 가드 문구가 «사라진 이미지» 라고 말한다 — **삭제가 아니라 비공개화**지만 결과(캐시 없는 곳에서 pull 불가)는 같다.

**① AMI 굽기는 pull 한다.** `infra/demo/aws/packer/demo-ami.pkr.hcl:381` — 프로젝트마다 `docker compose … build` 뒤
`docker compose … pull --ignore-buildable`, 실패하면 `!!! pull FAILED` + `exit 1`. ⇒ 이 수정 전에 재굽기를 시작했으면 ecommerce 차례에서
AMI 가 죽었다(Failure Scenario 2 가 실재). 현 배포 AMI 는 이미 두 이미지를 담고 있으므로 **지금 데모 부팅은 이 결함과 무관**(pull 을 안 하므로)
— 이건 코드 읽기로 낸 판정이고 데모 호스트에서 잰 것은 아니다.

**② 나이틀리** (`gh run list --workflow nightly-e2e.yml --branch main`):

| 런 | 커밋 | 시작(UTC) | web-store full-stack | image liveness |
|---|---|---|---|---|
| 36000571901 | `809eda97b` | 12:40 | 🟢 success | 🟢 success |
| 36003895383 | `08742324b` | 13:10 | 🔴 failure | 🔴 failure |
| 36005054674 | `578d62dfd` | 13:21 | 🔴 failure | — |
| 36009277930 | `437426d34` | 13:57 | 🔴 failure (`minio-init Error unauthorized` at `Start docker compose stack`) | 🔴 failure (`quay.io/minio/mc…` · `quay.io/minio/minio…`) |

⇒ 경계는 **12:40 → 13:10 UTC 사이의 시각**이다(BE-591 과 같은 모양 — 우리 diff 가 아니라 외부).

## AC-1 — 후보 실측 (모두 같은 방식, 2026-09-24T14:05–14:07Z)

| 후보 | manifest | 비고 |
|---|---|---|
| `docker.io/minio/minio:RELEASE.2024-10-13T13-34-11Z` · `:latest` | 401 | BE-591 이후 그대로 닫힘 |
| `docker.io/minio/mc:RELEASE.2024-10-08T09-37-26Z` | 401 | |
| `mirror.gcr.io/minio/minio:…` · `mirror.gcr.io/minio/mc:…` | 404 | Google 의 Hub 미러 — 원본이 닫혀 캐시도 없음(대조군 `mirror.gcr.io/library/postgres:16` = 200) |
| `ghcr.io/minio/minio:…` · `ghcr.io/minio/mc:…` | 401 (토큰 403) | 없음/비공개 |
| `public.ecr.aws/minio/minio:…` | 404 | |
| `public.ecr.aws/bitnami/minio:latest` · `…/minio-client:latest` | 404 | |
| `registry.gitlab.com/minio/minio:latest` | 401 (토큰 403) | |
| `docker.io/rancher/mirrored-minio-minio:…` · `…:latest` · `rancher/mirrored-minio-mc:…` | 404 | |
| `docker.io/bitnami/minio:latest` · `bitnami/minio-client:latest` | 404 | Bitnami 가 `bitnamilegacy` 로 옮김 |
| `https://dl.min.io/…/minio.RELEASE.2024-10-13T13-34-11Z` · mc 바이너리 · `.sha256sum` | **410 Gone** | MinIO 바이너리 배포도 닫힘 → «공식 바이너리로 우리가 굽기» 도 불가 |
| `quay.io/minio/aistor/minio:latest` | 200 | 🔴 **AIStor = MinIO 의 상용 제품**(라이선스). 제품 교체라 제외 |
| `cgr.dev/chainguard/minio:latest` · `minio-client:latest` (+ `docker.io/chainguard/…` 동일 다이제스트) | 200, amd64/arm64 | 무료 등급은 `latest`/`latest-dev` 만 — **버전 고정 불가**(다이제스트만), 버전이 2024-10 에서 크게 뜀. `latest` 는 셸·curl 없음(distroless) → healthcheck·init 스크립트 불가 |
| `cgr.dev/chainguard/minio:latest-dev` · `minio-client:latest-dev` | 200, amd64/arm64 | 셸 있음. 버전 고정 문제는 같음 |
| `docker.io/pgsty/minio:latest` | 200 | 🔴 Pigsty 커뮤니티 포크 — 출처 불확실, 제외 |
| `ghcr.io/coollabsio/minio:latest` | 200 | 🔴 Coolify 커뮤니티 빌드 — 출처 불확실, 제외 |
| `docker.io/bitnamilegacy/minio:latest` · `minio-client:latest` | 200 | |
| **`docker.io/bitnamilegacy/minio:2024.10.13-debian-12-r1`** | 🟢 **200**, amd64/arm64 | **선택** |
| `docker.io/bitnamilegacy/minio:2024.10.13-debian-12-r0` | 200 | r1 이 같은 버전의 마지막 리비전 |
| **`docker.io/bitnamilegacy/minio-client:2024.10.8-debian-12-r1`** | 🟢 **200**, amd64/arm64 | **선택** |
| `docker.io/bitnamilegacy/minio-client:2024.10.8-debian-12-r0` | 200 | |

**선택과 근거**

| | minio | mc |
|---|---|---|
| 참조 | `docker.io/bitnamilegacy/minio:2024.10.13-debian-12-r1@sha256:faf554135d50ff8b382f79df7795e080726cb2d6f4ce7f056d733a6437546f6a` | `docker.io/bitnamilegacy/minio-client:2024.10.8-debian-12-r1@sha256:c3e82110529b30658cdcdf6414619fe901c2ec820eba973bda936cdcb4c6193c` |
| amd64 manifest | `sha256:7cd5c0a55e74af4df590679bc9f34cf9fe4feaf1424d470a71c08c0f6ce7d23f` | `sha256:d7e8074891e09cae8d7a48813cd3f24914ae1427aa438aa34c009fba1fc38e14` |
| 라벨 | vendor `Broadcom, Inc.`, source `github.com/bitnami/containers/tree/main/bitnami/minio`, created `2024-10-24T16:45:43Z`, license Apache-2.0 | 같은 형식, `…/bitnami/minio-client`, `2024-10-24T16:45:28Z` |
| **같은 릴리스인가** | 바이너리 안에 `2024-10-13T13:34:11Z` 3회 · `2024-10-13T13-34-11Z` 1회 (실측, 레이어에서 꺼내 grep) | `2024-10-08T09:37:26Z` 3회 · `2024-10-08T09-37-26Z` 1회 |
| Hub 태그 | `2024.10.13` = r1 과 같은 다이제스트 (Hub API) | `2024.10.8` = r1 과 같은 다이제스트 |

- 🔵 **제품 교체가 아니다** — 같은 MinIO 서버·같은 `mc`, 같은 릴리스 타임스탬프. S3 API·`MINIO_ROOT_*` 환경값·`/minio/health/*` 경로 무변경.
- 🔵 **버전도 안 바꿨다**(BE-591 의 규율 — 레지스트리 문제와 버전 문제를 한 커밋에 섞지 않는다). Chainguard 를 안 고른 이유가 이것이다.
- 🔴 **바이트 동일성은 아니다** — Bitnami 가 소스에서 다시 빌드했다(`mod github.com/minio/minio (devel)`). 공식 quay 판과 같은 코드·다른 빌드다.

**선택의 대가 (소유자가 알아야 할 것)**

- 🔴 `bitnamilegacy` 는 Hub 설명 그대로 **«Legacy Bitnami images (no longer updated)»** — 보안 패치가 없고, Broadcom 이 언젠가
  지울 수 있다. 다이제스트로 고정했으므로 **조용히 바뀌지는 않지만, 사라질 수는 있다**(그때 칸 (h) 가 문다 — AC-4).
- 🔵 대안(나중에 옮길 곳): Chainguard(`cgr.dev/chainguard/minio` + `minio-client`, 유지보수 중인 소스 빌드) — 대가는 버전 점프 +
  무료 등급의 태그 고정 불가 + `latest` 판은 셸이 없어 healthcheck·init 을 `-dev` 판으로 받거나 다시 써야 함. 근본 처방(사내 미러 /
  pull-through 캐시 / 소스에서 직접 굽기)은 BE-591 이 적은 대로 **비용이 드는 소유자 결정**이다.

## AC-2 — 교체와 이미지 차이 흡수

이미지 설정·파일은 레지스트리에서 amd64 config·레이어를 받아 **직접 확인**했다(Docker 데몬 없이, `tar -tvzf`):

| 확인 | minio 이미지 | mc 이미지 |
|---|---|---|
| `Entrypoint` / `Cmd` | `/opt/bitnami/scripts/minio/entrypoint.sh` / `…/run.sh` | `/opt/bitnami/scripts/minio-client/entrypoint.sh` / `…/run.sh` |
| entrypoint 동작 | 인자에 `run.sh` 가 있을 때만 setup, 그 뒤 `exec "$@"` ⇒ `server /data` 를 주면 **`server` 를 exec 하려다 죽는다** | 같은 구조 |
| `User` | `1001` (passwd 에 없음 → Docker 기본 gid 0) | `1001` |
| `HOME` | `/` | `/` |
| `PATH` | `…/minio-client/bin:/opt/bitnami/minio/bin:…` | `/opt/bitnami/minio-client/bin:…` |
| 바이너리 | `/opt/bitnami/minio/bin/minio` (+ mc 도 들어 있음) | `/opt/bitnami/minio-client/bin/mc` |
| `/bin/sh` | `bin -> usr/bin`, `usr/bin/sh -> dash` ✅ | ✅ |
| `curl` (healthcheck) | `/usr/bin/curl` ✅ | — |
| `/data` | **이미지에 없음** → 새 named volume 의 루트는 root 755 | — |
| `/.mc` | `0:0 775` | `0:0 775` (root 그룹 쓰기) |
| 선언 볼륨 | `/bitnami/minio/data`, `/certs` (익명 볼륨이 생긴다 — 무해, 안 씀) | 없음 |

그래서 바꾼 것:

| 파일 | 변경 | 이유 |
|---|---|---|
| `docker-compose.yml` minio | `image:` 교체 · `entrypoint: ["/opt/bitnami/minio/bin/minio"]` · `user: "0:0"` | `command: server /data …` 를 그대로 쓰기 위해. root 는 이전 공식 이미지와 같은 값 — 기존 `minio-data` 볼륨(root 소유)과 새 볼륨(루트 755) 둘 다 쓸 수 있다 |
| `docker-compose.yml` minio-init | `image:` 교체만 | entrypoint 를 이미 `/bin/sh init.sh` 로 덮고 있다. mc 는 이미지 PATH, 1001:0 이 `/.mc`(root 그룹 775)에 쓸 수 있다 |
| `k8s/base/storage-minio.yaml` minio | `image:` 교체 · `command: [/opt/bitnami/minio/bin/minio]` | args 무변경 |
| `k8s/base/storage-minio.yaml` mc Job | `image:` 교체 · env `MC_CONFIG_DIR=/tmp/.mc` | Job 이 1000:1000 이라 `/.mc`(root 그룹만 쓰기)에 못 쓴다. `MC_CONFIG_DIR` 문자열이 mc 바이너리에 있음을 확인(grep 1건) |

| 명령 | 결과 |
|---|---|
| `bash infra/demo/verify-demo-wrapper.sh` | **rc=0** · 칸 (h) `이미지 실재 검증 커버리지 17/17 (skip 0건)` · 마지막 줄 `정적 검증 PASS` |
| `docker compose -f docker-compose.yml config` | rc=0 · 렌더에 새 image·entrypoint·`user: "0:0"` 확인 |
| `kubectl kustomize` (임시 kustomization, 오프라인) | rc=0 · StatefulSet/Job 에 새 image·command·`MC_CONFIG_DIR` |
| `docker manifest inspect <새 참조 tag@digest>` ×2 | 둘 다 rc=0 (데몬 없이 동작 — 칸 (h) 와 같은 호출) |
| `git grep quay.io/minio` (done/ 제외) | **`image:` 참조 0건** — 남은 것은 compose·k8s 의 사유 주석 3줄과 이 티켓·INDEX 의 서술뿐. 워크플로·스크립트·문서엔 없음 |

## AC-3 이 요구하는 것과 CI 가 재는 것

- ⚪ **로컬 기동 불가** — `docker version` → `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`.
- 기동을 실제로 도는 잡 = **`Nightly E2E` → `Frontend E2E full-stack (web-store, Playwright + docker compose)`** (`nightly-e2e.yml`
  `Start docker compose stack` 이 `minio minio-init` 을 올린다). 🔴 **`main` push 에서만 돈다** — PR 초록은 기동에 대해 아무것도 증명하지 않는다.
  같은 워크플로의 `Demo compose image liveness` 도 머지 후 그 커밋에서 초록이어야 한다.
- 🔴 그 잡이 증명하는 것: **pull 성공 + minio 가 healthy**(`product-service` 가 `minio: service_healthy` 에 걸려 있으므로 minio 가
  healthcheck(`curl …/minio/health/live`)를 못 넘기면 스택이 안 선다).
- 🔴 그 잡이 증명하지 **않는** 것: minio-init 의 종료코드, 버킷 존재, 이미지 업로드 경로 — 아무것도 minio-init 에 depends_on 하지 않고,
  잡에 그 단언이 없다. ⇒ AC-3 의 «버킷을 만들고 업로드 경로가 동작» 은 **그 잡의 로그를 열어도 스스로는 안 닫힌다**. 닫으려면
  (a) Docker 가 있는 호스트에서 `docker compose up minio minio-init` 후 `docker inspect -f '{{.State.ExitCode}}' ecommerce-minio-init` = 0
  과 `mc ls local/` 에 버킷, 그리고 대조군(버킷 없는 상태에서 같은 확인이 빨강), 또는 (b) 다음 데모 재굽기의 부팅에서 같은 확인.
- 그래서 이 파일은 AC-3 을 열어 둔 채 `review/` 로 간다 — close chore 는 4차원 (d) 에서 이 칸을 **열어서** 보고 판단해야 한다.

## AC-4 — «레지스트리 한 곳» 의존은 또 끊긴다

- 2주 새 두 번이다: 2026-09-11 Docker Hub `minio/*` → 2026-09-24 `quay.io/minio/*`, 그리고 MinIO 바이너리 배포(`dl.min.io`)도 410.
  **MinIO 자신이 배포를 닫아 가는 중**이므로, 원출처를 따라가는 처방은 더 이상 없다. 이번 선택(`bitnamilegacy`)도 «갱신 안 됨» 아카이브라
  같은 방식으로 끊길 수 있다.
- 🔵 **잡는 것은 칸 (h) 다** — 이번에도 `Demo wrapper smoke`(PR) 와 `Demo compose image liveness`(나이틀리)가 13:10 런에서 바로 물었다.
  다이제스트 고정이라 «다른 바이트로 조용히 바뀜» 은 없고, 남는 실패 모드는 «없어짐» 뿐인데 그것이 (h) 가 재는 것이다.
- 🔴 칸 (h) 는 **태그 존재**를 잰다 — 레포가 공개된 채 옛 태그만 지워지는 경우도 잡지만, `--require-coverage` 없는 PR 실행에서
  레이트리밋으로 전부 skip 되면 아무것도 안 잰다(MONO-359). 나이틀리가 그 공백을 메운다.
- 근본 처방(사내 미러 / pull-through 캐시 / 소스에서 직접 굽기)은 여전히 **비용이 드는 소유자 결정**이라 여기 안 끼웠다.
