# Task ID

TASK-MONO-752

# Title

`ADR-MONO-079` D5 — **셀러 구성원**: 사람의 풀 계정을 셀러에 연결(초대 → 로그인 본인 수락) · `SELLER` 사이트 역할 쓰기/회수 · 셀러 정지 = 역할 회수 (`TASK-MONO-745` 흡수분)

# Status

ready

# Owner

monorepo

# Task Tags

- ecommerce
- iam
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (초대 수락 = 계정 탈취 경로가 될 수 있는 흐름 · 두 프로젝트)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A · `ADR-MONO-078` A(풀 계정 · `consumer_site_roles`)

# Goal

한 셀러에 사람 여럿을 구성원으로 붙인다(라이더 R4 기본값 — 역할 하나 `MEMBER`). 운영자가 콘솔 셀러 화면에서 이메일로 초대하고, 그 이메일의 사람이 **스토어에 로그인한 상태로** 수락해야 붙는다(이메일 일치만으로는 안 붙는다 — ADR-034 § 1.3). 수락 시 IAM 이 그 풀 계정에 `consumer_site_roles(account, ecommerce, SELLER)` 를 쓰고, 셀러 정지·폐점은 구성원의 그 역할을 **회수**한다(계정 잠금 아님). 셀러 기계 계정의 잠금(ADR-042 D4)은 그대로.

# Scope

## In Scope

- product-service: `seller_members(tenant_id, seller_id, account_id, role, status, joined_at)` · 초대(토큰 · 만료 · 1회) · 수락 API(로그인 본인 · 초대 이메일 = 계정 이메일) · 정지/폐점 시 구성원 역할 회수
- IAM: `consumer_site_roles` 쓰기/회수 내부 API(계약 먼저) — 풀 계정·스토어 멤버십 전제(없으면 멤버십 생성 규칙을 정한다)
- 콘솔 셀러 화면의 구성원 목록·초대
- `AccountStatusChangedSellerConsumer` 는 **기계 계정에만** 반응한다는 것을 시험으로 고정(구성원 한 명 잠금 ≠ 셀러 정지)

## Out of Scope

- 셀러가 **일하는 화면**(셀러 센터 · 운영자 토큰의 셀러 범위 클레임 주입) — ADR-079 범위 밖
- 구성원 등급(R4 밖)

# Acceptance Criteria

- [ ] **AC-1** — 🔴 대조군: 초대받지 않은 사람 · 다른 이메일 계정 · 만료/재사용 초대 → 수락 거절. 같은 시험에서 올바른 수락만 성공.
- [ ] **AC-2** — 수락 뒤 그 사람의 스토어 토큰 역할 = `["CUSTOMER","SELLER"]` · 팬 토큰에는 `SELLER` 없음(평탄화 금지).
- [ ] **AC-3** — 셀러 정지 → 구성원 토큰에서 `SELLER` 만 빠지고 `CUSTOMER` 와 팬 로그인은 그대로.
- [ ] **AC-4** — 구성원 계정 잠금은 셀러를 정지시키지 않는다 · 기계 계정 잠금은 지금처럼 정지시킨다.

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D5 · R4
- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` § 4 · CORRECTION 2026-10-02
- `docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md` D4

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/`(사이트 역할 쓰기/회수 — 신설) · product-service 셀러 API

# Edge Cases

- 이미 다른 셀러의 구성원인 사람 — 여러 셀러 구성원 허용 여부를 정한다(`SELLER` 역할은 하나라 회수 시 다른 셀러 구성원 자격이 남으면 회수하지 않는다).
- 풀 계정이 아닌(사이트) 스토어 계정의 사람 — 수락 거절 또는 안내(사이트 계정은 `consumer_site_roles` 를 못 가진다).

# Failure Scenarios

1. 초대 이메일과 로그인 계정 이메일을 비교하지 않아, 초대 링크를 받은 누구나 셀러가 된다.
2. 셀러 정지가 사람 계정을 잠가 쇼핑·팬까지 막힌다.
