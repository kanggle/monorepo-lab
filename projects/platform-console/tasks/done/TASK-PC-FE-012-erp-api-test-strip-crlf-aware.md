# Task ID

TASK-PC-FE-012

# Title

console-web — `tests/unit/erp-api.test.ts` strip-comment regex CRLF-aware fix (TASK-PC-FE-011 § Honest gaps (b) closure)

# Status

done

# Owner

frontend

# Task Tags

- test

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

- **closes (explicit)**: TASK-PC-FE-011 § Honest gaps **(b)** — *"pre-existing `tests/unit/erp-api.test.ts:915` failure (Retry-After strip assertion) reproduces on `HEAD~1` via `git stash` round-trip — unrelated to this PR, requires separate fix-task."* (INDEX entry for FE-011, this repo `tasks/INDEX.md` done list, fixed 2026-05-20.)
- **no producer / feature / route changes**: this is a TEST-only fix. `erp-api.ts` semantics and on-disk doc-comments are byte-unchanged.
- **no ADR change**: 약속된 fix-task 이행. ADR-MONO-013/017 inviolate.

# Goal

`tests/unit/erp-api.test.ts:902-918` 의 *"the erp source carries NO 429/Retry-After handling code (grep-asserted)"* 단언이 strip-comment regex 의 line-ending 가정 결함 때문에 false-fail 한다. 

**Root cause (확인됨)**: `erp-api.ts` 가 CRLF (`\r\n`) 줄 끝이라 `.split('\n')` 후 각 line element 가 trailing `\r` 를 보존. 이어지는 strip regex `l.replace(/\/\/.*$/, '')` 는 `.` 가 default 에서 `\r` 매치 불가 + `$` (no `m` flag) 는 string end (`\r` 다음) 만 매치 → line-comment 안에 trailing `\r` 가 있으면 매치가 fail → strip 통과. 그 결과 line 291 `// land HERE (no Retry-After / backoff branch; erp has no` 같은 *line-comment* 가 stripped 결과에 잔류하여 `\bRetry-After\b` regex 가 true 매치.

본 task 는 **test 내부의 strip 로직만** CRLF-aware 하게 보강하고 (단언 강도는 절대 약화하지 않음 — STRENGTHEN-ONLY), `erp-api.ts` production source 의 doc-comment 어휘는 byte-unchanged 보존한다. doc-comment 의 "Retry-After" / "429" 단어는 *honest difference* 를 narrate 하는 본질적 표현이므로 의역으로 회피하는 것은 spec narration loss — 회피하지 않는다.

# Scope

## In Scope

### Test-only fix (`apps/console-web/tests/unit/erp-api.test.ts`)

- 단일 helper 도입 또는 inline patch — block-comment + line-comment 를 line-ending 무관하게 strip:
  - block-comment: `replace(/\/\*[\s\S]*?\*\//g, '')` (변경 없음 — `[\s\S]` 가 이미 `\r` 포함).
  - line-comment: **`replace(/\/\/[^\n]*/g, '')`** 또는 `replace(/\/\/.*$/gm, '')` 등 CRLF-safe regex 로 교체. `.split('\n').map(l => l.replace(/\/\/.*$/, ''))` 의 `\r` 함정 회피.
- 동일 strip 패턴을 사용하는 다른 grep-asserted source-shape test (있다면) 도 동일 보강 — 본 PR 의 scope 범위 내 `tests/unit/` 전수 sweep 1회.
- 단언 자체 (`/\bRetry-After\b/i.test(stripped)`.toBe(false) / `/\b429\b/` / `/RateLimited/`) STRENGTHEN-ONLY — 약화 / skip / TODO 처리 절대 불가.
- 추가 적극 단언 1줄: stripped 결과에 `\r` 가 잔류하지 않음 (`expect(stripped).not.toMatch(/\r/)`) — line-ending 회귀 차단.

### Code-side (production source) — byte-unchanged 검증

- `src/features/erp-ops/api/erp-api.ts` byte-unchanged 보장 (diff = 0 line). `tests/unit/erp-api.test.ts` 외 production 파일 수정 부재 grep-asserted.
- 단언: stripped 결과 안에 `Retry-After` / `\b429\b` / `RateLimited` 가 부재 (즉, line-comment 만이 그 토큰의 sole occurrence 여야 함 — 만약 production code line 에 잔류하면 본 fix 가 mask 한 것이므로 STOP).

## Out of Scope

- `erp-api.ts` 의 doc-comment 어휘 변경 (semantic loss).
- 다른 도메인 (`wms-api.ts` / `scm-api.ts` / `finance-api.ts`) 의 grep-asserted test — `Retry-After` 단언이 그쪽엔 없음 (scm 은 429 path 가 *있어야* 하므로 grep-assert 형태 다름; finance 의 finance-api.test 는 `tests/unit/erp-api.test.ts` 처럼 strip-CRLF 에 의존하지 않음 — 본 PR 의 sweep 결과로 한 번에 확인).
- production source 의 line-ending 변환 (LF 강제) — repo 차원 정책 부재 시 단일 파일 변환은 회귀 위험. test-side strip 이 root cause 해소면 충분.
- `console-bff` / Phase 7 dashboard / memory save — 별 task 또는 별 step.

# Acceptance Criteria

1. `pnpm vitest run tests/unit/erp-api.test.ts` 가 29/29 PASS (현재 28/29, the strip-grep test 만 fail) — 회귀 0. 
2. `pnpm vitest run` 전체 = 0 regression (43+ test files, 540+ tests, FE-010/011 회귀 부재).
3. `pnpm lint` 0 error.
4. `pnpm build` ✓ (route bundle 변경 부재).
5. Production source `src/features/erp-ops/api/erp-api.ts` byte-unchanged (`git diff origin/main -- src/features/erp-ops/api/erp-api.ts` empty).
6. Test 의 단언 강도는 STRENGTHEN-ONLY: `Retry-After` / `\b429\b` / `RateLimited` 3 단언 보존 + `\r` not-match 단언 추가.
7. 다른 `tests/unit/*-api.test.ts` 의 strip 함수 sweep 완료 — 동일 CRLF 함정 있는 곳도 일괄 보강 (없으면 본 검사 결과를 task review 메모에 명시).
8. self-CI = ALL GREEN (frontend job 회귀 부재).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (test discipline 의 source-shape grep-assert pattern 권위)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8 (erp NO-429 invariant — 단언 본질)

# Related Contracts

- 없음. test-internal regex hardening only.

# Edge Cases

- LF-only ts 파일 (현재 다른 `*-api.test.ts` 가 그러할 가능성) — 본 fix 는 CRLF/LF 둘 다 통과해야 함 (`[^\n]` 또는 `.*$` + `m` 플래그). 회귀 차단.
- doc-comment 안의 `*/` (e.g. block-comment 안에 `*/` literal — 없음) — block-comment regex non-greedy `*?` 가 처음 `*/` 에서 닫히므로 OK.
- string literal 안의 `//` (e.g. `'http://...'`) — 본 strip 함수는 *문자열 인식 안 함*. line-comment regex 가 URL `//` 부분도 일부 strip. 그러나 erp-api.ts 의 production URL 은 `${env.ERP_BASE_URL}` env 기반이고 hardcoded URL 없음. assertion target (`Retry-After` 등) 도 URL token 이 아니라 무관. test-only sweep 의 다른 파일에 hardcoded URL 가 있다면 별 검사 — 본 PR 범위에서 발생 시 STOP & escalate.

# Failure Scenarios

| 조건 | 본 PR 의 반응 |
|---|---|
| TASK-PC-FE-011 머지 직후 main 회귀 | self-CI 가 동일 fail surface → 본 fix-task 가 정확히 그 fail 을 GREEN 으로 회복. |
| LF 환경 (CI runner Linux) 에서는 PASS, CRLF (Windows dev) 에서만 FAIL | 본 fix 는 line-ending 무관 strip 으로 두 환경 모두 PASS. |
| production code line (not doc-comment) 에 `Retry-After` literal 이 진짜 생긴 경우 | fix-task 가 strip 만 보강해 mask 한 것 → STOP. 이는 본 PR 의 in-scope 가 아님 (production 코드 변경 별 task). |
| 다른 `*-api.test.ts` 의 sweep 에서 동일 CRLF 함정 발견 → 단언 약화/skip | 절대 불가. STRENGTHEN-ONLY. 일괄 보강하거나 별 fix-task 발급. |

---

# Implementation Notes (impl PR 단계 reference)

권장 minimal diff:

```ts
// before (broken under CRLF)
const stripped = src
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split('\n')
  .map((l) => l.replace(/\/\/.*$/, ''))
  .join('\n');

// after (CRLF-safe)
const stripped = src
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\/\/[^\n]*/g, '');
```

또는 helper 추출:

```ts
function stripComments(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
}
```

추가 단언 (line-ending 회귀 차단):

```ts
expect(stripped).not.toMatch(/\r/);
```

---

# Approval

- 분석 = Opus 4.7
- 구현 권장 = Sonnet 4.6 (단순 regex hardening, scope 좁음)
- 리뷰 = Opus 4.7 (dispatcher 독립 재검증 + acceptance criteria 8/8 단언)
