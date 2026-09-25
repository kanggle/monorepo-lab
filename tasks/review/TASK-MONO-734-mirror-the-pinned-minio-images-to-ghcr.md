# Task ID

TASK-MONO-734

# Status

review

# Title

고정해 둔 MinIO 이미지 두 개를 GHCR 로 미러한다 — `bitnamilegacy` 가 사라져도 CI·재굽기가 안 깨지게

# Owner

monorepo

# Task Tags

- infra
- ci
- supply-chain

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 워크플로 하나 + 목록 파일 + compose 두 줄. 판단은 이미 소유자가 했다.

---

# Goal

`TASK-BE-598` 이 ecommerce 의 `minio` · `minio-init` 를 `docker.io/bitnamilegacy/*` 로 옮겼다(digest 고정). 그 저장소는 Hub 설명
그대로 **«Legacy Bitnami images (no longer updated)»** 이고 언제든 사라질 수 있다. MinIO 자신의 배포처는 이미 전부 닫혔다
(Docker Hub `minio/*` → BE-591, `quay.io/minio` → BE-598, `dl.min.io` 410). ⇒ 다음에 끊기면 **따라갈 원출처가 없다.**

🔵 **소유자 결정 (2026-09-25 UTC)** — 근본 대책 넷(GHCR 미러 · S3 호환 대체품 · ECR pull-through 캐시 · 그대로 둠) 중
**GHCR 미러**. 이유: 공개 패키지라 비용 0 · 바이트가 같다(digest 보존) · 코드 변경 없음 · 제3자가 지워도 안 깨진다.
받아들인 대가: **보안 패치는 여전히 없다**(같은 바이트다) — 데모 내부망 전용이라 수용.

# Scope

## 포함

- `.github/workflows/mirror-images.yml` — `workflow_dispatch` 전용. 목록 파일의 각 원본(`src@sha256:…`)을 GHCR 로 **digest 보존 복사**
  하고, 복사본의 digest 가 원본과 **같은지 검증**한다(다르면 실패). 인증은 `GITHUB_TOKEN`(`packages: write`) — 새 비밀값 없음.
- `infra/mirror/images.txt` — 미러 목록(원본 · 대상). 이번엔 둘: `bitnamilegacy/minio:2024.10.13-debian-12-r1@sha256:faf5541…` ·
  `bitnamilegacy/minio-client:2024.10.8-debian-12-r1@sha256:c3e8211…` (값은 compose 에서 **복사**, 손으로 치지 않는다).
- 워크플로 실행 → 🔴 **소유자가 두 패키지를 Public 으로 전환**(GHCR 첫 게시물은 비공개가 기본이고 가시성 변경은 웹 UI 로만 된다)
  → 익명 pull 확인 → ecommerce compose 두 `image:` 를 `ghcr.io/kanggle/…@sha256:<같은 digest>` 로.

## 제외

- 이미지 갱신(보안 패치) — 같은 바이트를 옮기는 일이다. 갱신은 대체품 결정이 따로 필요하다.
- 다른 이미지의 미러 — 목록 파일이 그 자리를 만들지만, 이번엔 MinIO 둘만 넣는다.
- 주기 실행(schedule) — 고정 digest 복사라 한 번이면 된다.

# Acceptance Criteria

- [x] **AC-0** — 착수 시 compose 의 두 `image:` 참조를 읽어 목록에 **복사**한다(digest 64자 전체). BE-598 이후 바뀌었으면 바뀐 값으로.
- [x] **AC-1** — 워크플로 1회 실행 로그에 두 이미지 각각 «원본 digest == 복사본 digest» 가 찍히고 run 이 success.
      🔴 digest 가 다르면 실패해야 한다 — bite: 목록의 기대 digest 한 글자를 바꿔 로컬에서 스크립트를 돌리면 rc≠0(또는 워크플로 실패).
- [x] **AC-2** — 🔴 **소유자 수동 단계**: 두 GHCR 패키지 Public. 판정 = 토큰 없이 `https://ghcr.io/token?scope=repository:kanggle/<name>:pull`
      로 받은 익명 토큰으로 manifest HEAD 200(두 개 다). 이 판정 전에는 AC-3 에 들어가지 않는다.
- [x] **AC-3** — ecommerce compose 두 `image:` 를 GHCR 참조로 바꾸고(digest 동일), 주석에 BE-591 → BE-598 → 734 의 이력을 잇는다.
      `infra/demo/verify-demo-wrapper.sh` 의 (h) 칸이 두 참조를 **확인**했는가(skip 이 아니라) — CI `Demo wrapper smoke (infra/demo)` 로 본다.
- [x] **AC-4** — 🔴 재굽기 표면 기록: packer 가 compose 이미지를 pull 하므로 다음 굽기부터 GHCR 에서 가져온다. 이미 구운 AMI 는 옛 참조를
      들고 있다 — `TASK-MONO-672` 재굽기 목록에 한 줄(재굽기 필요 여부 = 아니오, 바이트 같음 · 다음 굽기에 자연 반영).

# Related Specs

- `projects/ecommerce-microservices-platform/tasks/review/TASK-BE-598-quay-minio-is-closed-too.md` § AC-1 «선택의 대가» · AC-4
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-591-…` (Docker Hub 차단)
- `infra/demo/verify-demo-wrapper.sh` (h) — 참조 이미지 실재 검사

# Related Contracts

- 없음. 서비스 계약·S3 API 불변.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 원본이 이미 사라진 뒤에 실행 | 복사 실패 → run 실패. 🔴 그래서 **지금** 한다(원본이 살아 있을 때가 유일한 기회) |
| 복사가 멀티아치 인덱스를 단일 매니페스트로 줄인다 | digest 가 달라져 AC-1 이 실패한다 — 그게 이 검증의 목적이다(amd64/arm64 둘 다 보존) |
| 패키지가 비공개인 채 AC-3 로 간다 | (h) 가 `unauthorized` 로 빨갛다 — AC-2 가 순서를 막는다 |
| 누가 GHCR 패키지를 지운다 | 같은 워크플로를 다시 돌리면 되지만 원본이 없으면 불가 — 🔴 GHCR 쪽이 이제 **유일본**일 수 있다는 사실을 README/주석에 적는다 |

# Failure Scenarios

1. **태그로만 복사한다** → 원본 태그가 움직이면 다른 바이트를 미러한다. 원본은 `@sha256:` 로 지정한다.
2. **digest 검증 없이 성공으로 본다** → 도구가 인덱스를 재작성해도 모른다(AC-1 🔴).
3. **공개 전환 전에 compose 를 바꾼다** → CI·데모 pull 이 401(AC-2 순서).

---

# 구현 기록

## 1단계 (2026-09-25 UTC · 분석=Opus 5.5) — 워크플로 · 목록 · 스크립트

🔴 **이 호스트에서는 복사할 수 없었다** — crane/skopeo/oras 없음 · Docker 데몬 꺼짐 · 로컬 gh 토큰에 `write:packages` 없음
(`gist, read:org, repo, user, workflow`). ⇒ 복사는 Actions 의 `GITHUB_TOKEN`(`packages: write`)으로 한다. 이것이 오히려 낫다 —
무엇을 어디로 옮겼는지가 run 로그로 남는다. 🔴 `workflow_dispatch` 는 **기본 브랜치의 워크플로 파일**만 돈다 ⇒ 이 PR 머지가 실행의 선행이다.

| 파일 | 내용 |
|---|---|
| `infra/mirror/images.txt` | AC-0 — compose 의 두 `image:` 를 **grep 으로 뽑아** 만들었다(손으로 치지 않음). 대상 = `ghcr.io/kanggle/mirror-minio:2024.10.13-debian-12-r1` · `ghcr.io/kanggle/mirror-minio-client:2024.10.8-debian-12-r1` |
| `infra/mirror/mirror-images.sh` (`100755`) | 원본이 `@sha256:` 가 아니면 실패 · `crane copy` 후 `crane digest` 가 원본 digest 와 다르면 실패 · `--visibility`/`--require-public` = 익명 토큰으로 manifest HEAD · `--self-test` = 가짜 crane 으로 bite |
| `.github/workflows/mirror-images.yml` | `workflow_dispatch` 전용 · crane 은 `go install …@v0.20.2`(모듈 프록시 체크섬 검증, 제3자 action 없음) · self-test → 로그인 → 복사+검증 → 가시성 보고(실패 아님) |

🔵 git 모드를 `100755` 로 올렸다 — 오늘 `TASK-MONO-672` 항목 15 에서 본 «`100644` + 누군가의 `chmod +x` = 영원한 ` M`» 을 되풀이하지 않으려고.

### 게이트 (로컬)

| 칸 | 결과 |
|---|---|
| `bash -n` | 🟢 |
| `--self-test` (AC-1 의 bite) | 🟢 일치 → rc=0 · **digest 다름 → rc≠0**(`digest changed`) · `@sha256` 없는 원본 → rc≠0 · 다른 디렉터리에서 호출해도 rc=0 · 잘못된 인자 rc=2 |
| `--visibility` 대조군(음성) | 🟢 미러 전이라 두 대상 **HTTP 404** · `--require-public` rc=1 |
| `--visibility` 대조군(양성) | 🟢 공개 이미지 `ghcr.io/github/super-linter:latest` → **HEAD 200** · `--require-public` rc=0 ⇒ 판정기가 양방향으로 문다 |

⏳ **남은 것**(1단계 시점): AC-1 실제 run(머지 뒤 dispatch) · AC-2 소유자 Public 전환 → 익명 200 · AC-3 compose 전환 · AC-4 재굽기 목록 한 줄.

1단계 PR #4015 → squash `807774652`(CI 69건 실패 0).

## 실행 (2026-09-25T05:55Z) — AC-1 · AC-2

- `gh workflow run mirror-images.yml --ref main` → run **`36100595693` success**(head `807774652`). 로그 요지:
  - `self-test OK: match → rc=0 · changed digest → rc≠0 ('digest changed') · unpinned source → rc≠0` (러너에서도 bite)
  - `✓ digest preserved: sha256:faf554135d50…6f6a` (minio) · `✓ digest preserved: sha256:c3e82110529b…193c` (minio-client)
    ⇒ 멀티아치 인덱스가 **바이트 그대로** 옮겨졌다(재작성됐다면 digest 가 달랐을 것).
  - `✓ public (anonymous manifest HEAD 200)` × 2
- 🔵 **AC-2 의 소유자 수동 단계는 필요 없었다** — 기안은 «첫 게시물은 비공개가 기본» 을 전제했지만, 공개 저장소의 워크플로가
  `GITHUB_TOKEN` 으로 올린 패키지는 **저장소 가시성을 물려받아 처음부터 공개**였다. 전제가 틀렸음을 러너 한 곳의 판정으로 믿지 않고
  **이 호스트에서 따로** `--require-public` → 200 × 2 · rc=0 으로 확인했다. (다음에 미러를 추가할 때도 같으리라는 추론은 하지 않는다 —
  같은 확인을 매번 한다. 워크플로 마지막 스텝이 그것을 보고한다.)

## 2단계 — AC-3 · AC-4

| 파일 | 변경 |
|---|---|
| ecommerce `docker-compose.yml` § `minio` · `minio-init` | `image:` → `ghcr.io/kanggle/mirror-minio…@sha256:faf5…` · `…/mirror-minio-client…@sha256:c3e8…` + 이력 주석(BE-591 → BE-598 → 734, «원본이 사라지면 유일본 — 지우지 마라») |
| 🔴 ecommerce `k8s/base/storage-minio.yaml` (둘) | **compose 밖의 형제** — 저장소 전체 `bitnamilegacy` grep 으로 찾았다. 안 고쳤으면 k8s 판만 옛 출처에 남았다 |
| `infra/mirror/images.txt` 주석 | 원본은 출처 기록으로 남는다는 한 줄 |
| `TASK-MONO-672` § 재굽기 준비 표 아래 | AC-4 — **재굽기 불필요**(같은 바이트) · 다음 굽기의 GHCR pull 은 그 굽기가 처음 잰다 |

🔵 **기계 대조**: compose 2 + k8s 2 참조의 digest 가 목록의 원본 digest 와 전부 일치(`uniq -c` = 각 2) · (h) 칸과 같은 방식
`docker manifest inspect <ghcr ref@digest>` → 두 참조 모두 rc=0. 🔴 **(h) 칸이 실제로 이 둘을 «확인» 했는지(skip 아님)는 CI
`Demo wrapper smoke (infra/demo)` 가 판정**한다 — 이 PR 의 CI 로 본다.
