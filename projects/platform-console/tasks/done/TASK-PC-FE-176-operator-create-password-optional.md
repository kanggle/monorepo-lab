# TASK-PC-FE-176 — 운영자 계정 생성 폼: break-glass 비밀번호 **필수 → 선택** (ADR-MONO-035 O2 정합)

**Status:** done
**Area:** platform-console / console-web · **Scope:** `features/operators` create 폼 + 프록시 스키마
**Type:** spec-drift 정정 (폼이 ACCEPTED ADR·백엔드 DTO·계약보다 더 엄격) — 동작 정합
**Implemented:** branch `task/pc-fe-176-operator-create-password-optional` → **#2175 merged** (squash `41cedd06d`). `next lint` + `tsc --noEmit` + `vitest` (86/86) green; CI 21 checks pass.
**Analysis model:** Opus 4.8 · **Impl model:** Opus.

## Goal

운영자 계정 생성 폼이 비밀번호를 **필수**로 강제(`pwOk = password.length > 0`)했으나, 이는 **ADR-MONO-035 O2
(ACCEPTED)** · 백엔드 DTO(`CreateOperatorRequest`, TASK-BE-377) · 계약(admin-api.md L1292/1301) 모두가
규정한 **"password OPTIONAL — 생략 시 OIDC-only 운영자, 주 로그인은 통합 IAM 자격증명; 입력 시 demoted
break-glass 로컬 로그인"** 보다 **더 엄격한 drift**였다. 폼을 스펙에 맞춰 비밀번호를 선택으로 만들고,
break-glass 성격을 라벨·helper로 명확히 해 "로그인 안 되는 운영자를 조용히 만드는" 혼선을 제거한다.

## Scope (implemented)

- **`CreateOperatorForm.tsx`**:
  - `canSubmit` — 비밀번호 blank 허용(`password === '' || pwError === null`); 입력 시에만 정책 강제.
  - submit — blank면 draft에서 password **omit**(빈 문자열 전송 금지; 생산자 `@Size(min=10)`가 `""`를 400).
  - 라벨 `초기 비밀번호 *` → **`break-glass 로컬 비밀번호 (선택)`**; `required`/`aria-required` 제거.
  - helper — "비워두면 OIDC-only 운영자; 주 로그인은 이 이메일의 통합 IAM 자격증명; 입력 시 비상 로컬 로그인(정책)".
- **`api/types.ts`** — `CreateOperatorInput.password` → optional(`password?`).
- **`app/api/operators/_proxy.ts`** — `CreateBodySchema.password` → optional; present 시 정책(≥10·영숫자특) 미러(생산자 최종 권위).
- **`operators-crud-api.ts`** — 무변경(optional password가 undefined면 `JSON.stringify`가 omit).

## Out of scope (후속 — 별도 제품/스펙 결정 필요)

- **2-모드 UI**(기존 계정 권한부여 / 신규 초대) + **이메일 계정존재 lookup**(`getAccountByEmail`) + **invite 흐름**.
  현재 스펙만으로 근거가 확정되지 않고 제품 결정이 필요 → 별도 task. (참고: 병행 진행 중인 **TASK-BE-470**
  self-service signup 화면이 "②계정 생성" 축을 담당.)

## Acceptance Criteria

- [x] **AC-1** 비밀번호 미입력으로 운영자 생성 제출 가능(submit 활성); draft에 password 부재 → 생산자가 OIDC-only 생성.
- [x] **AC-2** 비밀번호 입력 시 정책(≥10·영문·숫자·특수문자) 미충족이면 submit 차단 + 인라인 에러(기존 UX 유지).
- [x] **AC-3** 프록시 `CreateBodySchema`가 password omit 허용 + present 시 정책 검증(빈 문자열/약한 값 boundary 차단).
- [x] **AC-4** 라벨·helper가 break-glass·OIDC-only 성격 명시(오해 유발 "초기 비밀번호 *" 제거).
- [x] **AC-5** `next lint` + `tsc --noEmit` + `vitest` green; role-filter 등 기존 테스트 불변 + password-optional 신규 테스트 2건.

## Related Specs / Contracts

- **ADR-MONO-035 O2** (ACCEPTED) — operator 자격증명 수렴: OIDC primary + `password_hash` demote-to-break-glass.
- `iam-platform/specs/contracts/http/admin-api.md` §POST operators L1292/1301 — password optional 명문.
- `iam-platform/.../dto/CreateOperatorRequest.java` — password nullable(정책은 present 시).
- `platform-console/specs/contracts/console-integration-contract.md` §2.4.3 — password는 정책 pre-check 미러(필수 아님).

## Edge Cases

- blank password → **omit**(≠ `""`): `""`는 `@Size(min=10)` 400을 유발하므로 반드시 키 자체를 제거.
- 정책 위반 password 입력 → 제출 전 차단(클라이언트 mirror) + 생산자 400이 최종 권위.
- 기존 break-glass 사용처: 값 입력 흐름은 그대로 동작(회귀 없음).

## Failure Scenarios

- 프록시 스키마가 password를 여전히 필수(min 1)로 두면 → blank 제출이 422로 막혀 목표 미달 → `_proxy` 동시 수정으로 방지.
- 폼이 `""`를 전송하면 생산자 400 → submit에서 conditional omit으로 방지(테스트로 password 부재 단언).
