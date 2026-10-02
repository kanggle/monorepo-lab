# Task ID

TASK-MONO-749

# Title

`ADR-MONO-079` D3 — 상품 **`collection_ref`**(= 팬 아티스트 id) · 공개 스냅숏에 싣기 · 팬 아티스트 페이지가 그것으로 굿즈를 고른다

# Status

ready

# Owner

monorepo

# Task Tags

- ecommerce
- fan-platform
- public-data

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (두 프로젝트 + 공개 스냅숏 생성기)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A
- **후속**: `TASK-MONO-739`(팬 굿즈 시드 — 이 필드 위에서 시드한다, 739 의 R4 예명 접두어 규칙을 대체)

# Goal

상품에 `collection_ref VARCHAR(64) NULL`(인덱스 `(tenant_id, collection_ref)`)을 두고, 값은 팬 아티스트 id(라이더 R1 기본값)다. 공개 스냅숏(`@demo/public-data` store)에 그 필드를 싣고, 팬 아티스트 페이지는 `collection_ref = <그 아티스트 id>` 인 상품만 카드로 보인다 — 익명 방문 «백엔드 호출 0» 불변식 유지(ADR-077).

# Scope

## In Scope

- product-service: 마이그레이션(postgres · h2 둘 다 — ADR-077 D2 의 시드 경로) · 도메인 · 상품 생성/수정 API 의 선택 필드 · 공개 상품 응답에 노출
- 콘솔 상품 편집 화면의 입력 칸(선택)
- 공개 스냅숏 생성기(`infra/demo/public-data`)가 필드를 싣는다
- 팬 웹: 아티스트 페이지가 스냅숏에서 그 아티스트의 굿즈를 고른다(739 가 카드 UI 를 만든다면 이 티켓은 선택 함수와 시험까지)
- 계약: 필드 의미(«팬 아티스트 id, FK 없음»)

## Out of Scope

- 굿즈 시드 데이터(`TASK-MONO-739`) · 그룹/소속사 단위 컬렉션(R1 밖)

# Acceptance Criteria

- [ ] **AC-1** — `collection_ref` 가 있는 상품만 그 아티스트 페이지에 보이고, 다른 아티스트의 굿즈는 보이지 않는다(대조군).
- [ ] **AC-2** — 필드가 없는 기존 상품은 무변경(NULL) — 스토어 화면 회귀 없음.
- [ ] **AC-3** — 공개 스냅숏 생성기 시험이 필드를 싣는다(스냅숏 원장/가드가 있으면 갱신).
- [ ] **AC-4** — 팬 아티스트 페이지가 백엔드 호출 0 을 유지한다(기존 가드).

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D3
- `docs/adr/ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md` D1 · D2 · D5(D)

# Related Contracts

- product-service 상품 API · 공개 스냅숏 스키마

# Edge Cases

- 아티스트가 보관(ARCHIVED)되면 그 굿즈 카드는 팬에 안 보인다(팬이 보관 아티스트를 안 보이므로) — 상품은 스토어에 그대로.

# Failure Scenarios

1. postgres 만 바꾸고 h2(standalone)를 빠뜨려 데모/로컬 한쪽이 깨진다.
