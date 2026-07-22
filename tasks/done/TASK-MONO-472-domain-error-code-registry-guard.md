# Task ID

TASK-MONO-472

# Title

도메인 규칙 에러코드 레지스트리 드리프트 가드 (DUP1 사용자 결정 — dedup 대신 가드) — 4 clean 도메인 커버

# Status

done

# Owner

monorepo (root tasks/ — shared `scripts/` + `.github/workflows/`)

# Task Tags

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: MONO-470 감사의 DUP1(도메인 6파일이 error-handling.md 코드 재기술). 사용자 결정 2026-07-23 = "**그대로 두고 드리프트 가드 추가**" (`rules/README.md § Index File Rule ⚠️` 가 비-갈라짐 사본 반사적 dedup 경고; 처방=삭제 아닌 정경선언+가드). MONO-471 이 고친 phantom `OPERATION_NOT_PERMITTED` 이 살아남은 이유 = 도메인 에러코드 doc-side 가드 부재.
- **prerequisite for**: `TASK-MONO-473`(20코드 reconcile → erp/fan/scm 가드 편입).
- **execution constraint**: `scripts/` + `.github/workflows/ci.yml`. classifier block 아님.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (predicate FP frontier + 측정 판정).

---

# Goal

`rules/domains/<d>.md § Standard Error Codes` 가 선언한 에러코드가 `platform/error-handling.md` 에 등록됐는지 매 PR 검사하는 doc-side 가드를 추가한다. `check-error-code-registry.sh`(Java-emission side)의 보완 — 마크다운 목록은 emission 이 아니라 그 가드가 못 잡는다.

---

# Scope

## In Scope

1. **`scripts/check-domain-error-code-registry.sh`** (신규): 각 도메인 파일 § Standard Error Codes 섹션의 **각 불릿 첫 backtick UPPER_SNAKE 토큰**(=선언 코드)을 추출, `error-handling.md` 의 `| CODE |` 등록 행과 대조. 미등록 → RED. `--selftest`(추출기 FP frontier pin: description-embedded ABAC 속성 `SOURCE_IP`/`TIME_WINDOW`·role·event-enum 제외, out-of-section 제외, non-code 제외) + vacuity 가드.
2. **측정 기반 스코프**: 첫 런 26건 → 분류 = **6 FP**(description-embedded, 추출기 first-backtick 로 수정) + **20 진짜 gap**(erp 1·fan-platform 7·scm 12 = 등록 전무 pre-existing drift). **깨끗한 4도메인(wms/ecommerce/fintech/saas, gap 0)만 커버**, erp/fan/scm 은 `UNRECONCILED` 로 명시 제외(각 gap 코드 헤더/주석 열거) → **day-one GREEN**(§G2 red-day-one 회피, §G8 미커버 명시). MONO-473 reconcile 시 도메인별 편입.
3. **ci.yml 배선 3곳**: filter output `domain-errcode`(raw, NOT code-changed AND) + pure-positive paths(`rules/domains/**`·`error-handling.md`·스크립트) + job `domain-error-code-registry`(bash -n → --selftest → guard).

## Out of Scope

- **20코드 등록**(erp/fan/scm) = `TASK-MONO-473`. 코드별 HTTP status 배정 = platform 계약 결정(refactor 아님).
- **DUP1 포인터화** = 사용자가 "가드" 선택으로 명시 기각.
- reverse 방향(레지스트리 코드가 도메인 파일에 없음) 미검사(관심 드리프트 아님).

---

# Acceptance Criteria

- [x] **AC-1**: 스크립트 bash -n·`--selftest`·라이브(4 도메인·90 코드) GREEN.
- [x] **AC-2 (bite)**: scm 미제외 시 12 UNREGISTERED RED (측정으로 확인).
- [x] **AC-3 (FP 없음)**: description-embedded `SOURCE_IP`/`TIME_WINDOW`/`ORG_ADMIN`/`CREDENTIALS_INVALID` 미검출(selftest + saas 라이브 0).
- [x] **AC-4 (측정 문서화)**: 20 gap 코드가 스크립트 헤더 + `UNRECONCILED` 주석에 도메인별 열거, MONO-473 참조.
- [ ] **AC-5**: ci.yml YAML OK + `domain-errcode` raw filter + job --selftest 필수 first-step.
- [ ] **AC-6 (scope-lock)**: diff = 신규 스크립트 + ci.yml + 2 task lifecycle(472 review + 473 ready).
- [ ] **AC-7**: 본 PR 에서 `domain-error-code-registry` job 실제 실행 GREEN(스크립트 경로 filter 매치).

---

# Related Specs

- `scripts/check-domain-error-code-registry.sh`(신규) / `scripts/check-error-code-registry.sh`(형제, emission side).
- `platform/error-handling.md`(레지스트리) / `rules/domains/*.md`(선언측) / `rules/README.md § Index File Rule ⚠️`(가드 vs dedup 근거).

# Related Contracts

- None. CI/tooling. 20 gap 코드는 emitter 0(MONO-471 grep 패턴)으로 계약 미방출.

---

# Edge Cases

- **ecommerce 포인터화(코드 열거 없음)** — 섹션에 불릿 코드 0 → 검사 0, GREEN(FP 아님).
- **description-embedded UPPER_SNAKE** — first-backtick-on-bullet 로 제외(measured FP class, selftest pin).
- **`DOMAIN_ERRCODE_EXCLUDE` 빈 문자열** — `:-` 기본값이라 빈값=기본 제외셋. 재측정은 명시 override(`"erp fan-platform"`)로.

---

# Failure Scenarios

- **day-one RED** — 20 gap 이 원인 → 4도메인 스코프로 회피(측정). erp/fan/scm 은 473 후 편입.
- **다른 파일 수정** → AC-6 fail.

---

# Verification

- 2026-07-23, `task/mono-472-domain-errcode-guard` 브랜치 (off `main` @ a8048ec57).
- 로컬: bash -n·--selftest(FP-pin 포함)·라이브 4도메인/90코드 GREEN·scm 미제외 bite 확인·YAML OK.
- 3-dim merge 검증은 close chore 시. 본 PR 에서 job 실제 실행 GREEN 확인(AC-7).
- 분석·구현=Opus 4.8.
