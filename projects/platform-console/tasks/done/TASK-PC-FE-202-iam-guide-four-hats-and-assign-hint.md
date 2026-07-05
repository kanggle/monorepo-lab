# Task ID

TASK-PC-FE-202

# Title

console-web IAM 가이드 화면(`/iam/guide`)에 "하나의 계정, 4개의 모자" 절 추가 + AssignOperatorForm 힌트에 가이드 연결 — 계정·테넌트 관계 4유형(소비자/내가 운영하는 회사/내가 다니는 회사/남의 회사) 콘솔 표시, cross-org 차원 보강

# Status

done

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 또는 Opus — 정적 참조 화면 데이터+렌더 추가 + 힌트 링크, 백엔드/계약 무변경)

# Task Tags

- code
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-163(iam-guide 화면 도입 — role 카탈로그·접근 매트릭스·위임 체인·온보딩 3축·도메인 롤) + TASK-PC-FE-195(AssignOperatorForm 배정 vs 파트너십 disambiguation 힌트) + TASK-BE-482(repo 가이드 § 6 "4개의 모자" — 콘솔 미러의 SoT) + ADR-MONO-045(cross-org 파트너십).
- **note (갭)**: 현 iam-guide 화면은 협력업체를 **intra-org 배정(온보딩 3축)** 으로만 설명하고, cross-org 파트너십(남의 회사 테넌트를 bounded 운영, 모자 ④) 차원이 **아예 없다**. "4개의 모자" 절이 소비자→owner→직원→cross-org 를 한 표로 orient 하고 그 빠진 차원을 채운다.

# Goal

콘솔 IAM 가이드 화면에 **"하나의 계정, 4개의 모자"** 절을 추가한다 — 하나의 통합 IAM 계정이 관계에 따라 쓰는 4유형(① 소비자 ② 내가 운영하는 회사(owner) ③ 내가 다니는 회사(직원-운영자) ④ 내 회사가 운영하는 다른 회사(cross-org 참여자))을 관계·정체성/역할·토큰(인가)·콘솔 진입점으로 표. ②↔③(owner vs 직원), ③↔④(intra-org vs cross-org) 구분 문단. 이 화면의 나머지(role·배정·도메인 롤)가 ②~④ 모자의 세부임을 orient.

추가로 AssignOperatorForm 의 기존 파트너십 힌트(PC-FE-195)에 IAM 가이드(`/iam/guide`) **연결 링크**를 덧붙여, 폼에서 큰 그림으로 이동 가능하게 한다.

- 콘솔 미러의 SoT = repo 가이드 `operator-auth-token-model.md` § 6(BE-482) + `admin-service/rbac.md`(role 모델) + ADR-MONO-045.
- 순수 정적 참조(server component, 데이터 페치·게이트 없음). 백엔드/계약 0.

# Scope

## In Scope

- **`src/features/iam-guide/data.ts`** — 신규 `AccountHat` 타입 + `ACCOUNT_HATS`(①~④, 각 marker/relation/role/token/consoleNote) export.
- **`src/features/iam-guide/components/IamGuideScreen.tsx`** — intro 다음에 "계정·테넌트 관계 (하나의 계정, 4개의 모자)" 섹션(테이블 `iam-guide-hats` + 행 `iam-guide-hat-{i}`) + ②↔③·③↔④ 구분 문단. `ACCOUNT_HATS` import.
- **`src/features/operators/components/AssignOperatorForm.tsx`** — 기존 파트너십 힌트에 `/iam/guide` 연결(`next/link`, testid `assign-operator-guide-link`) 한 문장 additive. 기존 `assign-operator-partnership-hint`/`-link`(PC-FE-195) 불변.
- **Tests** — `tests/unit/IamGuideScreen.test.tsx`에 4-모자 렌더/순서 테스트 추가(axe-clean 유지). `tests/unit/operators-assignment-ui.test.tsx`에 가이드 링크(href `/iam/guide`) 테스트 추가. 기존 테스트 무회귀.

## Out of Scope

- role 모델·접근 매트릭스·온보딩 3축·도메인 롤 등 기존 iam-guide 콘텐츠 변경(추가만).
- 토큰 축(1축/operator/2축) 자체 재서술 — repo 가이드(BE-482) 소유, 콘솔은 관계 4유형만 표시.
- 파트너십/구독 등 실기능 변경, 백엔드/계약/producer 변경.
- 다른 도메인 가이드 화면(wms/scm/ecommerce-guide).

# Acceptance Criteria

- [ ] **AC-1** `/iam/guide`에 "하나의 계정, 4개의 모자" 섹션 렌더 — `ACCOUNT_HATS` ①~④ 각 행(관계·정체성/역할·토큰·콘솔).
- [ ] **AC-2** 각 모자 토큰 매핑 정확: ①=1축만 ②=1축+operator ③=+2축 assume-tenant ④=2축 cap·admin 없음. ②↔③·③↔④ 구분 문단 존재.
- [ ] **AC-3** AssignOperatorForm 힌트에 `/iam/guide` 링크(`assign-operator-guide-link`) additive 추가, 기존 `assign-operator-partnership-hint`/`-link` 불변.
- [ ] **AC-4** `IamGuideScreen.test.tsx` 4-모자 테스트 추가(렌더+순서 ①②③④) + 기존 8 테스트 무회귀 + **axe-clean 유지**.
- [ ] **AC-5** `operators-assignment-ui.test.tsx` 가이드 링크 테스트 추가 + 기존(assignment + PC-FE-195 파트너십 힌트) 무회귀.
- [ ] **AC-6** `pnpm exec vitest run` green, `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars — CI 두 프런트 잡). scope = console-web only. 백엔드/계약 0.

# Related Specs

- `projects/iam-platform/docs/guides/operator-auth-token-model.md` § 6 (BE-482 — 콘솔 미러의 개념 SoT).
- `projects/iam-platform/specs/services/admin-service/rbac.md` (role 모델 — ②③ 역할 근거) + § Cross-Org Partner Delegation Confinement (④ 근거).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / Layered-by-Feature (정적 server component 소비).

# Related Contracts

- 변경 없음(정적 참조 화면 콘텐츠 + 폼 힌트 링크).

# Target Service

- `platform-console` / `apps/console-web` — `features/iam-guide/{data.ts,components/IamGuideScreen.tsx}` + `features/operators/components/AssignOperatorForm.tsx`. 정적 참조 데이터/렌더 추가 + 힌트 링크(additive).

# Architecture

- 기존 iam-guide 정적 데이터-구동 패턴(`data.ts` SoT 미러 + `IamGuideScreen` 렌더 + 구조 단언 테스트)에 새 축(관계 4유형) 추가. 백엔드 무관·server component·no 'use client'. 힌트는 기존 `next/link` 패턴(PC-FE-195) 재사용.

# Edge Cases

- axe-clean: 4-모자 테이블은 `<caption class=sr-only>` + `<th scope=col>`(헤더) + `<th scope=row>`(관계 셀) 로 접근성 유지(기존 도메인 롤/매트릭스 테이블과 동일 패턴).
- marker(①~④) 는 순서 마커 — 테스트가 `ACCOUNT_HATS` 순서를 ①②③④로 단언(가로축 방향성 회귀 방지).
- AssignOperatorForm 힌트: 기존 파트너십 링크(`/partnerships`)와 새 가이드 링크(`/iam/guide`)가 공존, 각각 별 testid — 기존 PC-FE-195 테스트 셀렉터 불변.
- iam-guide 데이터 미러는 SoT(repo 가이드/rbac.md) 드리프트 시 사람이 갱신(테스트는 구조만 단언, 문구 정합은 사람) — data.ts doc-comment 에 SoT 링크 명시.

# Failure Scenarios

- 4-모자 테이블 접근성 위반(th scope 누락 등) → axe 테스트 RED: 기존 테이블 패턴 준수로 가드(AC-4).
- 기존 힌트 testid 오염(파트너십 링크 깨짐) → PC-FE-195 테스트 RED: additive 만, 기존 testid 불변(AC-3).
- 잔여 미사용 import(ACCOUNT_HATS 미사용 등) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(AC-6).
- 토큰 매핑 문구가 repo 가이드/rbac.md 와 어긋남 → 개념 수준 유지·SoT 링크 위임(data.ts doc-comment).

# Definition of Done

- [ ] `ACCOUNT_HATS` + IamGuideScreen "4개의 모자" 섹션 + ②↔③·③↔④ 구분 문단
- [ ] AssignOperatorForm 힌트에 `/iam/guide` 링크 additive(기존 파트너십 힌트 불변)
- [ ] IamGuideScreen 4-모자 테스트 + assignment-ui 가이드 링크 테스트 추가, 전체 vitest+tsc+lint clean 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
