# Task ID

TASK-INT-022-fix-001

# Title

TASK-INT-022 리뷰 수정 — MinIO /tmp 마운트, .env.example, infra 네임스페이스 PSA, K8s Job anonymous 정책, minio-init 리소스 제한

# Status

in-progress

# Owner

integration

# Task Tags

- deploy
- code

# Goal

TASK-INT-022 코드 리뷰에서 발견된 blocker 3건 + should-fix 4건 + nit 2건 수정.

**Blockers:**
1. MinIO StatefulSet `readOnlyRootFilesystem: true` + `/tmp` emptyDir 누락 → 업로드 시 런타임 크래시
2. `.env.example`에 MINIO_* 환경변수 항목 없음 → AC 미충족
3. `infra` 네임스페이스 PSA 레이블 매니페스트 없음

**Should-fix:**
4. Headless Service에 console(9001) 포트 노출 불필요
5. `minio-init` docker-compose 리소스 제한 누락
6. K8s bucket-init Job `mc anonymous set download` → spec과 불일치 (dev 버킷은 private)
7. `object-storage-policy.md` env var 패턴 불일치 (`PRODUCT_IMAGES_BUCKET` vs `STORAGE_BUCKETS_PRODUCT_IMAGES`)

**Nit:**
8. `infra/minio/init.sh` CORS dead code 제거
9. `verify-object-storage.sh` ACCESS_KEY fallback 개선

# Scope

## In Scope
- 위 9건 수정

## Out of Scope
- 기능 변경 없음

# Acceptance Criteria
- [ ] MinIO StatefulSet에 `/tmp` emptyDir 마운트 추가
- [ ] `.env.example`에 MINIO_ROOT_USER, MINIO_ROOT_PASSWORD, PRODUCT_IMAGES_BUCKET 추가
- [ ] `infra` 네임스페이스 매니페스트에 PSA 레이블 추가
- [ ] k8s Headless Service에서 console 포트 제거
- [ ] docker-compose `minio-init`에 리소스 제한 추가
- [ ] k8s bucket-init Job에서 anonymous download 제거
- [ ] `object-storage-policy.md` env var 패턴 통일
- [ ] `init.sh` dead code 제거
- [ ] verify 스크립트 ACCESS_KEY fallback 개선

# Related Specs
- `specs/platform/object-storage-policy.md`

# Related Contracts
없음

# Target Service
- infra

# Edge Cases
없음 — 단순 수정

# Failure Scenarios
없음

# Test Requirements
- docker compose config -q 통과
- YAML 파싱 통과
- bash -n 통과

# Definition of Done
- [ ] All fixes applied
- [ ] Tests passing
- [ ] Ready for review
