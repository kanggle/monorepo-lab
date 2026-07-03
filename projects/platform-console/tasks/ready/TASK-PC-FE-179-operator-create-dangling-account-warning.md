# TASK-PC-FE-179 — 운영자 계정 생성 폼: **가입 안 된 이메일 = "허수 운영자" 프리플라이트 경고**

**Status:** ready
**Area:** platform-console / console-web · **Scope:** 운영자 관리(/operators) 생성 폼 이메일 프리플라이트 lookup
**Type:** UX 안전장치 (non-blocking 소프트 경고 — 동작/계약 무변화, FE-only)
**Analysis model:** Opus 4.8 · **Impl 권장:** Opus (프리플라이트 lookup + fail-soft 상태기계 + 테스트)

## Goal

운영자 계정 생성 시 폼/백엔드가 **이메일이 실제 가입된 계정인지 검증하지 않는다**(실측: 미가입 이메일로 생성 →
HTTP 201 성공, `admin_operators` ACTIVE row 생성, 그러나 `auth_db.credentials` 0건). break-glass 비밀번호도
비우면 `password_hash=NULL` → **아무도 로그인할 수 없는 "허수 운영자"**가 조용히 만들어진다. 생성 시점엔
에러가 안 나고(201), 실패는 나중에 그 사람이 로그인할 때만 드러난다.

이 갭을 **FE 프리플라이트 소프트 경고**로 메운다: 이메일+테넌트가 채워지면 기존 계정 검색 BFF를 debounce 조회해
"이 테넌트에 가입된 계정인가"를 확인하고, **가입 안 됨 AND break-glass 비밀번호 공란**이면 허수 운영자 위험을
inline 경고로 알린다. **차단하지 않는다**(ADR-MONO-035 O6 fail-soft·break-glass 가용성 우선 준수) — 제출은
그대로 가능하며 producer 검증이 여전히 최종 권위.

## 왜 하드블록이 아니라 소프트 경고인가 (설계 판정)

- ADR-MONO-035 O2는 OIDC-only 운영자(비밀번호 생략)를 **의도적으로 허용**하고, O6은 "operator login
  availability preserved at every step" + `resolveOrCreateIdentity` fail-soft를 명시. lookup 불가 시 생성을
  막는 **fail-closed 하드블록은 ADR 취지와 충돌**.
- credential 존재를 정확히 답하는 EP(`POST /internal/auth/credentials/account-id-by-email`)는 `/internal`이라
  console 도달 불가(BE 신규 public EP + contract 필요 → 범위 확대). 반면 계정 존재 검색
  `GET /api/accounts?email=`은 console-web이 **이미 부를 수 있고**, 이 시스템은 signup 시 account와
  credential이 동시 생성되므로 **계정 존재 = 로그인 가능의 견고한 프록시**.
- 따라서 **BE·contract 무변화, platform-console FE-only** 로 목표 달성.

## Scope

**IN:**

- `src/app/api/accounts/route.ts` — GET에 **`tenantId` 쿼리 passthrough** 추가(생성 폼이 활성 테넌트가 아닌
  **선택한 테넌트**를 검색해야 정확). `searchAccounts({tenantId})`가 이미 지원(TASK-BE-357). producer가
  operator 스코프로 게이팅(403 → 경고 skip).
- `src/features/operators/api/account-existence.ts` (신규) — `checkAccountExistsForTenant(email, tenantId):
  Promise<boolean | null>` 클라이언트 헬퍼. 동일 출처 `GET /api/accounts?email=&tenantId=&size=1` fetch,
  content 유무로 true/false, **모든 실패(4xx/5xx/timeout/parse)는 `null`**(=unavailable → 경고 skip, fail-soft).
- `src/features/operators/components/CreateOperatorForm.tsx` — 선택적 prop `checkAccountExists`(default=헬퍼),
  이메일/테넌트 변경 시 debounce(≈400ms) useEffect + Abort/stale-guard, `acctState` 상태기계
  (`idle|checking|exists|absent|unavailable`), **non-blocking** advisory 블록. `canSubmit`은 이 상태에
  **의존하지 않는다**.
- 테스트 — `tests/unit/features/operators/CreateOperatorForm.test.tsx`에 `checkAccountExists` 스텁 주입 케이스
  추가(absent+무비번=경고 / absent+비번=완화문구 / exists=확인문구 / unavailable=무경고 / 제출 항상 가능).

**OUT (의도적 비범위):**

- admin-service / auth-service 신규 EP·검증, contract 변경 — BE 하드게이트는 별 task(수요 시). 현 task는 순수 FE.
- 플랫폼 스코프 `tenant='*'` 선택 시 — 계정 검색 대상 불명확 → **경고 skip**(idle).

## Acceptance Criteria

- [ ] **AC-1** 이메일 형식 OK + 테넌트 선택(≠`*`)이면 debounce 후 `GET /api/accounts?email=&tenantId=&size=1`가
  **선택한 테넌트**로 1회 조회된다(연타 시 이전 요청 취소/무시, 최신만 반영).
- [ ] **AC-2** 조회결과 계정 없음 **AND** break-glass 비밀번호 공란 → `data-testid="create-operator-account-warning"`
  경고가 뜬다("이 이메일은 <테넌트>에 가입된 계정이 아닙니다 … 허수 운영자 … 먼저 회원가입 또는 비상 비밀번호 입력").
- [ ] **AC-3** 계정 없음 **AND** 비밀번호 입력됨 → 완화 문구(비상 로컬 로그인 가능)로 바뀌고 위험 경고는 사라진다.
- [ ] **AC-4** 계정 있음 → `data-testid="create-operator-account-ok"` 확인 문구("가입된 계정 — OIDC 로그인 가능").
- [ ] **AC-5** lookup이 401/403/503/timeout/parse 실패(=`null`) 또는 `tenant='*'` → **경고/확인 문구 모두
  미표시**, 제출 정상 가능(fail-soft, 회귀 없음).
- [ ] **AC-6** `canSubmit`은 `acctState`에 무관 — 경고가 떠도 제출 버튼 활성(non-blocking) 확인.
- [ ] **AC-7** `pnpm lint` + `pnpm exec tsc --noEmit` + `pnpm exec vitest run` green(기존 6 케이스 불변 + 신규 추가).

## Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` — O2(OIDC-only 운영자 허용)·O6(로그인 가용성
  보존, fail-soft). 이 task는 O2의 "이메일은 이미 통합 IAM 자격증명을 가진다"는 **암묵 전제를 사용자에게
  가시화**(강제는 안 함).
- `projects/platform-console/apps/console-web` architecture.md § Forbidden Dependencies — 브라우저 직접 IAM
  호출 금지 → 동일 출처 BFF(`/api/accounts`) 경유 준수.

## Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/accounts` — `tenantId` 쿼리 스코프
  (TASK-BE-357, 소비만; 변경 없음).
- `console-integration-contract.md` § 2.4.1(accounts) / § 2.4.3(operators) — 소비 계약, 변경 없음.

## Edge Cases

- **활성 테넌트 ≠ 선택 테넌트**: 폼 드롭다운에서 다른 테넌트를 고르면 활성 테넌트가 아닌 **선택 테넌트**로 조회해야
  정확 → BFF `tenantId` passthrough로 해결. 크로스테넌트 검색 권한 없으면 producer 403 → `null` → skip.
- **계정 있음이지만 credential 없음(희귀)**: signup은 account+credential 동시 생성이라 실질 드묾. 계정 존재를
  프록시로 사용함을 task가 명시(정밀 credential 검사는 BE EP 필요 = 비범위).
- **debounce 경쟁/언마운트**: AbortController + cancelled 플래그로 stale 응답 무시.
- **비-ASCII/특수 이메일**: `encodeURIComponent`로 쿼리 인코딩.
- **플랫폼 스코프 `*`**: 검색 대상 불명확 → idle(경고 없음).

## Failure Scenarios

- lookup 엔드포인트 장애/느림 → `null` 반환 → 경고 미표시·제출 정상(사용자 흐름 차단 0). 로그만 남고 UX 무영향.
- 스텁 미주입 테스트 환경에서 default 헬퍼가 실제 fetch 시도 → 신규 테스트는 **반드시 `checkAccountExists`
  스텁 주입**(전역 fetch 미의존). 기존 6 케이스는 prop 생략(default) but lookup 조건(email+tenant 동시 충족)
  전까지 트리거 안 됨 → 회귀 없음(그래도 `tsc`/lint/vitest로 확증).
- BFF `tenantId` passthrough가 기존 accounts 화면 검색에 영향? → accounts-api가 이미 `params.tenantId`
  우선·미지정 시 활성 테넌트 default이므로 **기존 호출(쿼리에 tenantId 미포함) 동작 불변**. 회귀 없음.
