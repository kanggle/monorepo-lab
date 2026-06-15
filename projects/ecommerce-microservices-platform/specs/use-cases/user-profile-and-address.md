# Use Case: 사용자 프로필/배송지 관리

---

## UC-1: 프로필 조회

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 사용자 프로필이 존재함 (IAM `account.created` 이벤트로 최소 프로필 자동 생성 — ADR-MONO-037)

### 정상 흐름

1. 사용자가 마이페이지에 접근한다.
2. 클라이언트가 GET /api/users/me 요청을 보낸다.
3. user-service가 인증된 사용자의 프로필을 반환한다 (userId, email, name, nickname, phone, profileImageUrl, status, timestamps).

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 프로필 미존재** — 프로필이 아직 생성되지 않은 경우 `USER_PROFILE_NOT_FOUND` 오류를 반환한다 (404).
- **EF-2: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-2: 프로필 수정

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 사용자 프로필이 존재함

### 정상 흐름

1. 사용자가 프로필 수정 페이지에서 정보를 변경한다 (nickname, phone, profileImageUrl).
2. 클라이언트가 PATCH /api/users/me 요청을 보낸다.
3. user-service가 입력값을 검증한다.
4. 프로필 정보를 업데이트한다.
5. user-service가 `UserProfileUpdated` 이벤트를 발행한다 (userId, nickname, phone, profileImageUrl, updatedAt).
6. 시스템이 수정된 프로필 정보를 포함한 200 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 입력값 오류** — 유효하지 않은 필드값이면 `VALIDATION_ERROR` 오류를 반환한다 (400).
- **EF-2: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-3: 배송지 목록 조회

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음

### 정상 흐름

1. 사용자가 배송지 관리 페이지에 접근한다.
2. 클라이언트가 GET /api/users/me/addresses 요청을 보낸다.
3. user-service가 해당 사용자의 배송지 목록을 반환한다.

### 대안 흐름

- **AF-1: 배송지 없음** — 등록된 배송지가 없으면 빈 목록을 반환한다.

### 예외 흐름

- **EF-1: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-4: 배송지 등록

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 사용자의 등록된 배송지가 10개 미만임

### 정상 흐름

1. 사용자가 새 배송지 정보를 입력한다.
2. 클라이언트가 POST /api/users/me/addresses 요청을 보낸다.
3. user-service가 배송지 수 제한(최대 10개)을 확인한다.
4. 배송지를 생성하고 addressId를 발급한다.
5. 시스템이 addressId를 포함한 201 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 배송지 수 초과** — 이미 10개의 배송지가 등록되어 있으면 `ADDRESS_LIMIT_EXCEEDED` 오류를 반환한다 (422).
- **EF-2: 입력값 오류** — 필수 필드 누락 시 `VALIDATION_ERROR` 오류를 반환한다 (400).
- **EF-3: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-5: 배송지 수정

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 해당 배송지가 존재함

### 정상 흐름

1. 사용자가 배송지 정보를 수정한다.
2. 클라이언트가 PATCH /api/users/me/addresses/{addressId} 요청을 보낸다.
3. user-service가 배송지 소유권을 확인한다.
4. 배송지 정보를 업데이트한다.
5. 시스템이 addressId를 포함한 200 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 배송지 미존재** — addressId에 해당하는 배송지가 없으면 `ADDRESS_NOT_FOUND` 오류를 반환한다 (404).
- **EF-2: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-6: 배송지 삭제

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 해당 배송지가 존재함

### 정상 흐름

1. 사용자가 배송지 삭제를 요청한다.
2. 클라이언트가 DELETE /api/users/me/addresses/{addressId} 요청을 보낸다.
3. user-service가 배송지 소유권을 확인한다.
4. 기본 배송지 여부를 확인한다.
5. 배송지를 삭제한다.
6. 시스템이 204 응답을 반환한다.

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 기본 배송지 삭제 불가** — 다른 배송지가 존재하는 상태에서 기본 배송지를 삭제하려 하면 `DEFAULT_ADDRESS_CANNOT_BE_DELETED` 오류를 반환한다 (422).
- **EF-2: 배송지 미존재** — addressId에 해당하는 배송지가 없으면 `ADDRESS_NOT_FOUND` 오류를 반환한다 (404).
- **EF-3: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).

---

## UC-7: 회원 탈퇴

### 액터

- 인증된 사용자 (Authenticated User)

### 사전조건

- 사용자가 로그인되어 있음
- 사용자 상태가 ACTIVE임

### 정상 흐름

1. 사용자가 회원 탈퇴를 요청한다.
2. user-service가 사용자 상태를 WITHDRAWN으로 변경한다.
3. user-service가 `UserWithdrawn` 이벤트를 발행한다 (userId, withdrawnAt).
4. order-service가 이벤트를 수신하여 미완료 주문을 자동 취소한다.
5. IAM (iam-platform)가 탈퇴 신호를 수신하여 해당 사용자의 모든 인증 세션을 IAM-internal 로 무효화한다 (ecommerce 비소유).

### 대안 흐름

- 없음

### 예외 흐름

- **EF-1: 미인증** — 인증 토큰이 없거나 유효하지 않으면 `UNAUTHORIZED` 오류를 반환한다 (401).
- **EF-2: 이미 탈퇴한 사용자** — 이미 탈퇴한 사용자가 재요청하면 `USER_ALREADY_WITHDRAWN` 오류를 반환한다 (422).

---

## Related Contracts
- HTTP: `specs/contracts/http/user-api.md`
- Events: `specs/contracts/events/user-events.md`
