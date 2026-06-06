---
id: TASK-FE-007
title: "fix(TASK-FE-006): EditRolesDialog X-Operator-Reason 헤더 Korean 문자열 수정 + ChangeStatusDialog 헤더 값 안전성 보장"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-006에서 발견된 HTTP 헤더 ByteString 안전성 문제를 수정한다.

`EditRolesDialog`의 `EDIT_ROLES_REASON` 상수가 Korean(non-ASCII) 문자열(`'운영자 역할 변경'`)로 정의되어 `X-Operator-Reason` 헤더에 직접 전달된다. 동일 feature의 `CreateOperatorDialog`는 동일한 제약(HTTP 헤더는 ByteString-safe여야 함, undici/jsdom 엄격 검증)을 인식하고 의도적으로 ASCII 상수(`'operator.create'`)를 사용하고 있으나, `EditRolesDialog`는 이 규칙을 따르지 않아 일관성이 깨져 있다.

또한 `ChangeStatusDialog`는 사용자 입력 Korean 텍스트를 `X-Operator-Reason` 헤더로 직접 전달하므로, 엄격한 HTTP 클라이언트 환경에서 ByteString 검증 실패 가능성이 있다. 헤더 값 안전성을 보장하는 처리가 필요하다.

## Scope

1. **`EditRolesDialog.tsx`** — `EDIT_ROLES_REASON` 상수를 ASCII-safe 값(`'operator.roles.change'`)으로 교체.

2. **`ChangeStatusDialog.tsx`** — 사용자 입력 reason 값을 `X-Operator-Reason` 헤더에 전달하기 전에 ASCII-safe 보장 처리를 추가한다:
   - 방안 A: 헤더에는 고정 ASCII 상수(`'operator.status.change'`)를 사용하고, 실제 사유는 요청 body의 별도 필드(예: JSON body에 `reason` 포함)로 전달. 단, 현재 `PATCH .../status` 계약이 body에 `reason` 필드를 포함하지 않으므로 계약 변경이 필요한 경우 방안 B 선택.
   - 방안 B (권장): 사용자 입력 reason을 헤더 전달 전에 `encodeURIComponent` 또는 Latin-1 안전 변환 처리. `X-Operator-Reason` 헤더의 non-ASCII 문자를 percent-encode하거나 Latin-1 범위 외 문자를 제거하여 ByteString 검증을 통과시킨다.
   - 백엔드 계약(`specs/contracts/http/admin-api.md`)에 `X-Operator-Reason` 헤더의 허용 문자셋 제약이 명시되어 있지 않으므로, 가장 안전한 방안은 공유 API 클라이언트(`shared/api/client.ts`)에서 헤더 설정 시 non-ASCII 문자를 안전하게 처리하는 로직을 추가하는 것이다.

3. **공유 클라이언트 레이어 (`shared/api/client.ts`)** — `operatorReason` 옵션 처리 시 non-ASCII 문자를 안전하게 처리하는 헬퍼를 추가(선택적: client 레이어에서 일괄 처리 가능). 이렇게 하면 모든 feature의 `X-Operator-Reason` 사용처가 자동으로 보호됨.

4. **테스트 업데이트** — 수정된 동작을 검증하는 테스트를 추가 또는 수정:
   - `EditRolesDialog` 제출 시 `X-Operator-Reason` 헤더가 ASCII-safe 값으로 전달됨을 검증하는 단위 테스트 추가.
   - `ChangeStatusDialog` 제출 시 Korean reason이 포함된 경우에도 헤더 ByteString 오류가 발생하지 않음을 검증.

## Acceptance Criteria

- [ ] `EditRolesDialog`의 `X-Operator-Reason` 헤더 값이 ASCII-safe 상수(`'operator.roles.change'`)로 전달됨
- [ ] `ChangeStatusDialog`에서 사용자가 Korean 텍스트를 reason으로 입력해도 `X-Operator-Reason` 헤더 ByteString 검증 에러가 발생하지 않음
- [ ] `CreateOperatorDialog` 동작은 변경 없음 (이미 ASCII-safe)
- [ ] 기존 테스트 27개 전부 계속 통과
- [ ] `EditRolesDialog` 제출 시 헤더 값 검증 단위 테스트 추가

## Related Specs

- `specs/features/operator-management.md`
- `specs/contracts/http/admin-api.md` — X-Operator-Reason 헤더 정의

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Edge Cases

- Korean reason 문자열이 `X-Operator-Reason` 헤더로 전달되는 경우 (ChangeStatusDialog 사용자 입력)
- reason이 비어 있거나 3자 미만인 경우 — 기존 폼 유효성 검사로 이미 차단됨

## Failure Scenarios

- Korean 문자열 헤더 전달 시 `TypeError: Invalid header value` (undici 엄격 모드) — 수정 후 발생하지 않아야 함
- `X-Operator-Reason` 헤더 누락 시 서버 400 `REASON_REQUIRED` — 헤더는 반드시 전달되어야 함
