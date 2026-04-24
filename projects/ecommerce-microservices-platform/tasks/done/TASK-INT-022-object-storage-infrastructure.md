# Task ID

TASK-INT-022

# Title

상품 이미지 객체 스토리지 인프라 구성 — MinIO(local/dev) + S3(staging/prod) 배선

# Status

review

# Owner

integration

# Task Tags

- deploy
- code

# Goal

상품 이미지 업로드 기능(TASK-BE-125, TASK-FE-066)의 사전 인프라로,
`specs/platform/object-storage-policy.md`에 정의된 객체 스토리지 백엔드를
모든 환경에서 사용 가능한 상태로 만든다.

작업 완료 후:

1. 로컬 개발자가 `docker-compose up`만으로 MinIO를 띄우고
   `firstproject-local-product-images` 버킷을 자동으로 사용할 수 있다.
2. K8s `dev` / `staging` 환경에 MinIO 또는 S3 접근이 매니페스트로 배선되어
   있다.
3. product-service 가 `storage.s3.*` 설정 키를 통해 백엔드를 의존성 없이
   읽을 수 있다 (실제 SDK 호출은 TASK-BE-125 범위).
4. 시크릿/엔드포인트가 환경별로 분리되며, 평문 시크릿이 커밋되지 않는다.

---

# Scope

## In Scope

- `docker-compose.yml` 에 MinIO 서비스 추가 (single node, healthcheck, 리소스 제한)
- MinIO 부트스트랩 스크립트(또는 init 컨테이너)로 `firstproject-local-product-images`
  버킷 자동 생성 + anonymous read 정책 적용
- `infra/minio/` 디렉토리에 MinIO 관련 init 스크립트와 README 배치
- `k8s/base/` 에 MinIO StatefulSet + Service + PVC 매니페스트 추가 (dev 한정)
- `k8s/base/secrets.yaml` 에 `storage-credentials` Secret 추가 (CHANGE_ME 패턴, SealedSecrets 자리)
- `k8s/services/product-service/configmap.yaml` 에 `storage.s3.*` 설정 키 주입
- `specs/platform/object-storage-policy.md` 갱신 (실제 컨테이너 이름, 포트, 매니페스트 경로 반영)
- `specs/platform/error-handling.md` 에 `STORAGE_UNAVAILABLE`, `MEDIA_NOT_FOUND`,
  `MEDIA_VALIDATION_FAILED` 에러 코드 등록
- 검증 스크립트: 로컬 환경에서 MinIO에 PUT/GET 가능한지 확인하는 smoke 스크립트

## Out of Scope

- product-service 측 Java SDK 코드, 업로드 API, 컨트랙트 변경 (TASK-BE-125)
- admin-dashboard UI (TASK-FE-066)
- AWS S3 버킷 실제 생성 / Terraform 모듈 작성 (별도 인프라 PR로 분리;
  본 태스크는 staging/prod용 매니페스트 placeholder까지만)
- CloudFront/CDN 구성
- 이미지 리사이즈/썸네일 파이프라인
- 바이러스 스캔
- 버킷 lifecycle 정책의 실제 적용 (정책 문서화는 spec에서 끝)

---

# Acceptance Criteria

- [ ] `docker-compose up -d minio` 로 MinIO가 정상 기동하고 healthcheck PASS
- [ ] 컨테이너 기동 시 `firstproject-local-product-images` 버킷이 자동 생성됨
- [ ] 호스트에서 `mc` 또는 `aws --endpoint-url=http://localhost:9000` 로 PUT/GET 성공
- [ ] product-service ConfigMap에 `STORAGE_S3_ENDPOINT`, `STORAGE_S3_REGION`,
      `STORAGE_S3_PATH_STYLE_ACCESS`, `STORAGE_BUCKETS_PRODUCT_IMAGES` 환경 변수가 주입됨
- [ ] product-service Secret 참조에 `STORAGE_S3_ACCESS_KEY`, `STORAGE_S3_SECRET_KEY` 가 추가됨
- [ ] `k8s/base/secrets.yaml` 에 평문 자격 증명이 들어가지 않음 (CHANGE_ME 또는 SealedSecrets 자리)
- [ ] `specs/platform/error-handling.md` 에 신규 에러 코드 3종이 등록됨
- [ ] `infra/minio/README.md` 에 로컬 부트스트랩 절차 문서화
- [ ] `scripts/verify-object-storage.sh` (또는 동등) 가 로컬에서 PUT/GET 라운드트립 검증

---

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/platform/object-storage-policy.md` (신규 — 본 태스크에서 갱신)
- `specs/platform/deployment-policy.md`
- `specs/platform/security-rules.md`
- `specs/platform/error-handling.md` (본 태스크에서 갱신)
- `specs/rules/traits/content-heavy.md`
- `specs/services/product-service/architecture.md`

# Related Skills

- `.claude/skills/infra/kubernetes.md`
- `.claude/skills/infra/docker-compose.md` (있다면)

# Related Contracts

본 태스크에서 직접 변경하는 컨트랙트 없음. (`specs/contracts/http/product-api.md`
업로드 엔드포인트 추가는 TASK-BE-125 범위)

# Participating Components

- `docker-compose.yml`
- `infra/minio/` (신규)
- `k8s/base/storage-minio.yaml` (신규)
- `k8s/base/secrets.yaml`
- `k8s/services/product-service/configmap.yaml`
- `k8s/services/product-service/deployment.yaml` (envFrom/secretRef 보강)
- `specs/platform/object-storage-policy.md`
- `specs/platform/error-handling.md`
- `scripts/verify-object-storage.sh` (신규)

# Trigger

수동 — 상품 이미지 관리 기능 도입 사전 인프라

# Expected Flow

1. `object-storage-policy.md` 검토 및 부족분 보완 (컨테이너 이름, 포트 확정)
2. `docker-compose.yml` 에 MinIO 서비스 + init 컨테이너 추가
3. `infra/minio/init.sh` 작성 (mc alias → mb → policy set anonymous)
4. `k8s/base/storage-minio.yaml` 작성 (StatefulSet + Service + PVC)
5. `k8s/base/secrets.yaml` 에 `storage-credentials` Secret 추가
6. `k8s/services/product-service/{configmap,deployment}.yaml` 에 storage 환경변수 배선
7. `specs/platform/error-handling.md` 신규 에러 코드 추가
8. `scripts/verify-object-storage.sh` 작성 — 로컬 PUT/GET 라운드트립
9. `infra/minio/README.md` 작성 — 로컬 사용법, prod 전환 시 변경 포인트
10. K8s manifest lint (kubeval) 및 docker-compose config 검증

---

# Edge Cases

- MinIO 컨테이너가 product-service보다 늦게 기동 → product-service 의 readiness probe가 storage 의존성을 어떻게 다루는지는 TASK-BE-125 범위. 본 태스크에서는 MinIO healthcheck만 보장
- 호스트 9000 포트 충돌 (개발자 환경에서 다른 MinIO/S3 emu 사용 중) → README에 포트 변경 가이드 명시
- PVC 미지원 K8s 환경 (local kind/minikube without storage class) → emptyDir fallback 옵션 또는 README 안내
- bootstrap init 스크립트 재실행 시 버킷 이미 존재 → `mc mb --ignore-existing`
- MinIO 서버 자격 증명을 docker-compose에서 평문으로 주는 케이스 → `.env` 파일 사용, `.env.example` 만 커밋

# Failure Scenarios

- MinIO 기동 실패 (포트 점유, 디스크 권한) → docker-compose logs로 진단, README 트러블슈팅 절
- init 스크립트 실패 (mc 미설치, alias 등록 오류) → init 컨테이너 종료 코드로 알림, 재시도
- K8s Secret 미생성 상태에서 product-service 배포 → deployment의 envFrom가 실패 → CrashLoopBackOff. 배포 순서 README에 명시
- 로컬 verify 스크립트 실패 → 자격 증명 mismatch 또는 endpoint 불일치 우선 의심
- staging/prod 매니페스트 placeholder를 그대로 적용 → secrets controller가 거부하도록 SealedSecrets 패턴 안내

# Test Requirements

- docker-compose config 검증 (`docker compose config -q`)
- MinIO healthcheck 통과 확인
- `scripts/verify-object-storage.sh` 가 PUT → HEAD → GET → DELETE 라운드트립 성공
- K8s manifest lint (kubeval/kube-score) 통과
- ConfigMap/Secret 키가 product-service deployment의 envFrom 와 일치하는지 정적 검사

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (smoke 검증 스크립트 포함)
- [ ] Tests passing
- [ ] Specs updated (`object-storage-policy.md`, `error-handling.md`)
- [ ] README documented (`infra/minio/README.md`)
- [ ] Ready for review
