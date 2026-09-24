# TASK-BE-598 — quay.io 의 minio 도 닫혔다 (BE-591 의 우회로가 끊겼다)

**Status:** ready

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

- [ ] **AC-0** — 착수 직전 재측(위 표와 같은 방식 + 대조군). 여전히 401 인지. 그리고 ① packer/AMI 굽기가 이미지를 pull 하는지
      (`infra/demo/aws/packer/**` 읽기) ② 최근 나이틀리 full-stack 런 결과 — 둘 다 결과를 기록.
- [ ] **AC-1** — 대체 출처 후보를 **실측**으로 고른다(익명 manifest 200 + `linux/amd64` 포함 + 태그 고정 가능). 후보 예: 다른 공개 미러 ·
      공급자 빌드 이미지 · 소스 빌드 · S3 호환 대체물. 🔴 **MinIO 를 다른 제품으로 바꾸는 선택**이면 API 호환·초기화(mc 로 버킷 생성)
      경로가 달라지므로 **소유자 결정**을 받는다.
- [ ] **AC-2** — 4곳 교체, 태그/다이제스트 고정. `bash infra/demo/verify-demo-wrapper.sh` 칸 (h) 초록, CI `Demo wrapper smoke` 초록.
- [ ] **AC-3** — 스택 기동 실측: minio + mc 초기화 컨테이너가 버킷을 만들고, 이미지 업로드를 쓰는 서비스 경로 하나가 동작(로컬 또는 CI 로그).
- [ ] **AC-4** — BE-591 의 교훈대로 «레지스트리 한 곳» 의존이 또 끊길 수 있음을 기록하고, (h) 가 이번처럼 그것을 잡는다는 사실을 적는다.

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
