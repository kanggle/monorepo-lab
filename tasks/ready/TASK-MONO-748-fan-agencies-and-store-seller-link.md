# Task ID

TASK-MONO-748

# Title

`ADR-MONO-079` D1·D2 — 팬 **소속사 엔티티**(artist-service) · 아티스트/그룹 소속 · 소속사 ↔ 스토어 셀러 연결(0..1)

# Status

ready

# Owner

monorepo

# Task Tags

- fan-platform
- data-migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (자유 텍스트 → 엔티티 이전 · 다른 프로젝트 셀러 검증)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A (2026-10-02)
- **후속**: `TASK-MONO-750`(운영자 관리 경로) · `TASK-MONO-751`(콘솔 화면)

# Goal

ADR-079 D1·D2 를 구현한다. 지금 소속사는 `artists.agency` · `artist_groups.agency` 의 자유 텍스트다. 이를 artist-service 의 엔티티 `agencies` 로 만들고, 아티스트·그룹이 `agency_id` 로 소속하게 하며, 소속사가 굿즈를 파는 스토어 셀러를 0..1 개 가리키게 한다(라이더 R2 기본값).

# Scope

## In Scope

- 마이그레이션: `agencies(id, tenant_id, name, status, store_seller_id NULL, created_at, updated_at, version)` · `UNIQUE(tenant_id, name)` · `artists.agency_id` / `artist_groups.agency_id`(NULL 허용 FK)
- 이전: 기존 자유 텍스트 값이 같은 것끼리 소속사 행 하나로 묶고 `agency_id` 채움(공백·대소문자 정규화 규칙을 정해 적는다). 자유 텍스트 컬럼은 이번에 지우지 않는다
- 소속사 CRUD API(관리 경로 — 권한은 `TASK-MONO-750` 이 여는 운영자 경로를 쓴다; 그 전에는 지금의 관리 역할 게이트 그대로)
- 셀러 연결 쓰기 때 스토어 셀러 존재·상태 검증(없음 · `CLOSED` → 거절) — 프로젝트 간 조회 경로는 계약을 먼저 쓴다
- 공개 읽기(아티스트 상세의 소속사 이름) — 표시를 소속사 이름에서
- 데모 시드(`seed-fan.sh`)와 공개 스냅숏(`infra/demo/public-data`)의 소속사 반영

## Out of Scope

- 콘솔 화면(`TASK-MONO-751`) · 운영자 assume 경로(`TASK-MONO-750`) · 자유 텍스트 컬럼 제거(별도)

# Acceptance Criteria

- [ ] **AC-1** — 기존 볼륨 이전 시험: 자유 텍스트가 같은 아티스트·그룹이 같은 소속사 행을 가리키고, 비어 있으면 NULL.
- [ ] **AC-2** — 소속사 CRUD · 중복 이름 409 · 소속 변경 시험.
- [ ] **AC-3** — 셀러 연결: 존재하는 ACTIVE 셀러 → 저장 · 없는/`CLOSED` 셀러 → 거절 · 셀러 조회 실패 → 저장 안 함(fail-closed).
- [ ] **AC-4** — 공개 페이지의 소속사 표시가 그대로 보인다(회귀 없음).

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D1 · D2
- `projects/fan-platform/specs/services/artist-service/`

# Related Contracts

- artist-service HTTP 계약(소속사 API 추가) · 셀러 조회 계약(프로젝트 간)

# Edge Cases

- 같은 소속사를 다르게 쓴 자유 텍스트(«SM» / «SM Entertainment») — 자동 병합하지 않는다(정규화 규칙 밖은 별도 행).
- 소속사 없는 아티스트(솔로 무소속).

# Failure Scenarios

1. 이전이 일부 행만 채워 소속사 표시가 사라진다.
2. 셀러 조회 장애 때 검증 없이 저장해 존재하지 않는 셀러를 가리킨다.
