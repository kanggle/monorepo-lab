# Task ID

TASK-MONO-747

# Title

`ADR-MONO-079` 기안 — **소속사 · 셀러 · 콘솔 팬 관리**: 소속사를 관리 대상으로 두고 스토어 셀러와 연결 · 굿즈는 스토어 상품 + «컬렉션» 속성 · 사람 계정을 셀러에 연결(`TASK-MONO-745` 흡수)

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- ecommerce
- fan-platform
- identity

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (세 프로젝트에 걸친 도메인 모델 결정 — ADR 기안, 코드 변경 없음)

---

# Dependency Markers

- **선행**: `ADR-MONO-078` A(ACCEPTED). 소유자가 정한 순서 **078 → 079 → 080**(2026-10-01) — 078 의 남은 단계(`TASK-MONO-743` · `TASK-BE-617` · `TASK-MONO-744`)와 **기안은 병행 가능**하다(이 티켓은 문서만 쓴다). 구현 티켓은 ACCEPT 뒤에 기안한다.
- **후속**: `TASK-MONO-739`(팬 굿즈 — 079 뒤로 미룸) · `TASK-MONO-746`(`ADR-MONO-080` 후보 — 079 결정이 선행).
- **흡수**: `TASK-MONO-745`(2026-10-02 구현 없이 종결 — 아래 Goal ③).

# Goal

소유자가 대화에서 정한 목표 상태를 ADR 로 기안한다(2026-10-01 · 10-02 UTC). 원문 요지: *«팬플랫폼은 여러 소속사의 여러 아티스트와 굿즈가 들어갈 수 있고, 이커머스 스토어에는 여러 판매자의 여러 물품이 판매될 수 있고, 콘솔에서 팬플랫폼의 아티스트와 굿즈가 관리되고 스토어의 물품을 관리하고 싶어.»*

소유자가 이미 고른 갈래(2026-10-01, AskUserQuestion 답):

| 질문 | 소유자 선택 |
|---|---|
| 소속사는 무엇인가 | **관리 대상(팬플랫폼 테넌트 안의 엔티티) + 스토어 셀러와 연결** |
| 굿즈는 어디 사는가 | **스토어 상품 그대로**(`ADR-MONO-077` D) |
| 아티스트 ↔ 굿즈 연결 | **상품의 «컬렉션» 속성** |

이 ADR 이 정해야 할 것:

1. **소속사 모델** — 팬플랫폼 테넌트 안의 엔티티(아티스트가 소속), 스토어 셀러와의 연결(1:1? 소속사가 셀러를 하나 가진다?), 연결의 주인(어느 서비스가 그 링크를 소유하나).
2. **콘솔에서 팬 관리** — 콘솔 운영자가 팬플랫폼의 소속사·아티스트를 관리하는 경로. 🔴 `ADR-MONO-059`(운영자는 B2C 팬 테넌트를 assume 할 수 없다)와 충돌한다 — 부분 재개방(① 관리 표면만 연다 · 권장했던 안) vs ② 다른 경로. **소유자가 아직 고르지 않았다** — ADR 의 선택지로 올린다.
3. **사람 계정 ↔ 셀러 연결 (`TASK-MONO-745` 에서 흡수, 소유자 결정 2026-10-02 «079로 합치기»)** — 지금 셀러 계정은 사람이 로그인하지 않는 **기계 계정**이다(`seller+<tenant>+<sellerId>@marketplace.local`, 무작위 비밀번호 — 745 닫기 기록). «셀러인 사람이 같은 계정으로 쇼핑도 한다» 를 성립시키려면 사람의 풀 계정을 셀러(또는 소속사)에 연결하고 그 계정에 스토어 사이트 역할 `SELLER` 를 줘야 한다(`consumer_site_roles` — `ADR-MONO-078` § 4 역할 규칙: 시드 ∪ 사이트 역할 → `["CUSTOMER","SELLER"]`). 셀러 한 곳에 사람 여럿인가도 같이 정한다.
4. **상품 «컬렉션» 속성** — 상품에 아티스트(또는 소속사) 컬렉션을 붙이는 모양 · 팬 페이지가 그것으로 굿즈를 고르는 방법(지금은 공개 스냅숏 `@demo/public-data` 의 고정 목록, `TASK-MONO-739`).

# Scope

## In Scope

- `docs/adr/ADR-MONO-079-*.md` PROPOSED 기안 + `docs/adr/INDEX.md` 행
- 위 Goal 1~4 의 선택지 표(장단점 · 저장소 현황 실측)와 권장안
- ACCEPT 뒤 구현 티켓 목록의 스케치(기안은 ACCEPT PR 에서 — `project_adr_accept_gate_exact_intent`)

## Out of Scope

- 구현(코드 · 마이그레이션)
- 직원·협력사 직원의 풀 계정 — `ADR-MONO-080` 후보(`TASK-MONO-746`)

# Acceptance Criteria

- [ ] **AC-0** — 착수 전 실측: 팬플랫폼의 아티스트·소속사 모델 현황(artist-service), 상품 모델에 컬렉션 같은 속성이 있는지(product-service), 콘솔의 팬 관련 화면 현황, `ADR-MONO-059` 의 정확한 금지 범위. 결과를 ADR § Context 에 적는다.
- [ ] **AC-1** — ADR 이 Goal 1~4 를 각각 선택지 · 권장안으로 정한다. 소유자가 이미 고른 갈래(위 표)는 **다시 묻지 않고** 결정으로 적는다.
- [ ] **AC-2** — Goal 3 에 셀러 정지의 의미를 정한다: 사람 계정이 셀러에 연결되면 «셀러 정지 → 계정 잠금»(`ADR-MONO-042` D4)이 그 사람의 쇼핑·팬 이용까지 막는다 — 계정 잠금 vs 사이트 역할 회수 중 무엇인가. product-service `AccountStatusChangedSellerConsumer` 가 이벤트 `tenantId` 를 셀러 테넌트로 읽는 문제(풀 계정이면 `consumer-pool`)도 다룬다(745 닫기 기록의 넘긴 의무).
- [ ] **AC-3** — ADR 은 PROPOSED 로 머지되고, ACCEPT 는 소유자의 정확형(`ADR-MONO-079 ACCEPTED — <letter>`)으로만 넘어간다.

# Related Specs

- `docs/adr/ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md` (D)
- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` (§ 소유자 후속 결정 + CORRECTION 2026-10-02)
- `docs/adr/ADR-MONO-042-*.md` (셀러 기계 계정 · D4 정지)
- `docs/adr/ADR-MONO-059-*.md` (팬 B2C 테넌트와 운영자)
- `projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md`

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Role Strategy (사이트 역할)

# Edge Cases

- 셀러 운영을 하는 사람이 이미 그 이메일로 풀 쇼퍼 계정이 있다 — 연결은 본인 확인으로만(이메일 일치만으로 붙이지 않는다, `ADR-MONO-036` P6).
- 소속사가 셀러 없이 존재(굿즈를 안 파는 소속사).

# Failure Scenarios

1. 셀러 정지가 사람 계정 전체를 잠가, 셀러였던 사람이 쇼핑·팬 이용까지 못 한다.
2. 콘솔 팬 관리 경로가 `ADR-MONO-059` 의 금지를 우회하는 새 assume 경로로 생긴다(결정 없이).
