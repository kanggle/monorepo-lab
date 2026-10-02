# Task ID

TASK-MONO-750

# Title

`ADR-MONO-079` D4-A — 플랫폼 운영자의 **팬 디렉터리 관리 경로**(관리 경로만 열고 대리 저작·커뮤니티·멤버십은 계속 닫는다) · `ADR-MONO-059` 부분 개정의 구현

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- fan-platform
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (운영자가 B2C 테넌트에 들어오는 첫 길 — 대조군이 본체)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A · `TASK-MONO-748`(관리할 소속사 API)
- **후속**: `TASK-MONO-751`(콘솔 화면)

# Goal

플랫폼 운영자(라이더 R3 기본값 — 고객사 운영자 아님)가 콘솔에서 `fan-platform` 을 assume 해 artist-service 의 **디렉터리 관리 경로**(소속사 · 아티스트 · 그룹 · 팬덤 쓰기)만 쓸 수 있게 한다. 지금 그 경로는 발급될 수 없는 역할에만 열려 있다(`fan` 도메인 구독 0 · `FAN_OPERATOR` 파생 없음 · `trustEntitledDomains()` 꺼짐 — `FanTenantGatePolicyTest`).

# Scope

## In Scope

- 착수 전 실측(AC-0): 지금 막는 층 전부의 목록(구독 · 역할 파생 · 게이트웨이 · 서비스 디코더/필터 · `WorkloadRoleCatalog`) — 무엇을 어디서 여는지 표로
- `fan` 도메인 구독(플랫폼 운영자만 선택 가능하게) · `FAN_OPERATOR` 파생
- artist-service: **관리 경로에만** 운영자 토큰 신뢰(엔타이틀먼트) — 읽기·다른 경로는 지금 그대로
- community · membership · notification 서비스: 무변경 — 운영자 토큰이 계속 거절됨을 시험으로 고정
- `FanTenantGatePolicyTest` 등 기존 핀 시험을 새 정책에 맞게(«관리 경로만» 으로) 갱신 — 핀을 지우지 않는다
- 계약·스펙: 운영자의 팬 관리 범위

## Out of Scope

- 콘솔 화면(`TASK-MONO-751`) · 고객사 운영자(R3 밖)

# Acceptance Criteria

- [ ] **AC-0** — 막는 층 실측 표.
- [ ] **AC-1** — 🔴 대조군 한 시험 안에서: 운영자 토큰으로 artist-service 관리 경로 2xx · 커뮤니티 `ARTIST_POST` 403 · 멤버십 403 · 고객사 운영자의 `fan-platform` assume 거절.
- [ ] **AC-2** — 소비자(팬) 토큰은 관리 경로에 여전히 403.
- [ ] **AC-3** — `ADR-MONO-059` 의 부분 개정 범위와 시험이 일치한다(059 «부분 개정» 절).

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D4-A · R3
- `docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md` § 부분 개정

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`(엔타이틀먼트·역할) · artist-service 관리 API

# Edge Cases

- 운영자가 팬 테넌트에 있을 때 팬 웹 화면(소비자 쪽)에 그 토큰이 새지 않는다.

# Failure Scenarios

1. 엔타이틀먼트 신뢰를 서비스 전체에 켜 커뮤니티 쓰기까지 열린다 — 059 가 배제한 대리 저작.
