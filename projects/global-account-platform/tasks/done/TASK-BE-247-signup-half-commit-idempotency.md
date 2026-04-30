# TASK-BE-247: account → auth signup half-commit 방어 (멱등성 + timeout 정렬)

## Goal

`POST /api/accounts/signup` 흐름에서 분산 트랜잭션 half-commit으로 인해 **고아 credential**이 발생할 수 있다. cold-start 또는 일시적 슬로우다운 시 재현된다.

### 재현된 시나리오

1. account-service: `@Transactional` 시작 → `accounts` row 삽입
2. account-service → auth-service `POST /internal/auth/credentials` (read-timeout 5s)
3. auth-service: 처리가 5초 초과 (cold-start, GC pause, 등)
4. account-service: read timeout → `AuthServiceUnavailable` → `@Transactional` rollback
5. auth-service: 처리 완료 → credential row commit (caller가 이미 포기한 상태)

**결과:** `account_db.accounts` 비어있고 `auth_db.credentials`만 남는 orphan. 동일 이메일 재가입 시 auth-service가 409 반환 → **영구 차단**.

(2026-04-30 e2e 검증 중 3건 재현됨. 수동 DELETE로 임시 해소.)

## Scope

**In:**
- **Option B — auth-service `/internal/auth/credentials` 멱등성 강화** (권장 핵심):
  - 동일 (accountId, email, password_hash 매치) 재시도 → 200 OK / 204로 응답 (현재처럼 409가 아니라 success로 취급)
  - 또는 멱등 키(`Idempotency-Key` 헤더) 도입 — 클라이언트(account-service)가 signup마다 UUID 생성하여 전송, auth-service가 짧은 TTL로 dedup
  - 시그니처가 다르면(같은 accountId에 다른 email/password) 409 유지 — 정합성 위반 방지
- **Option A — read-timeout 보정**:
  - `account.auth-service.read-timeout-ms`를 5000 → 15000으로 상향. 또는 `e2e` profile에서만 별도 값 적용
  - cold-start 마스킹 효과. Option B만으로 부족할 수 있는 burst 시나리오 보강
- **테스트**:
  - Integration 테스트: WireMock으로 auth-service가 4초 후 응답하는 스텁 → account-service 재시도 시 멱등 success 응답으로 인해 signup 성공 검증
  - 단위 테스트: `AuthServiceClient`가 멱등 응답(200/204)을 정상 처리하는지 검증
  - 회귀: 기존 contract spec(`account-api.md`, `auth-internal.md`)과 정합 유지

**Out:**
- Saga 패턴 도입 (옵션 D)은 본 태스크 범위 밖. 현 시점 ROI 대비 과함
- 백그라운드 cleanup job (옵션 C)도 별도 태스크로 분리 — 운영 도구 성격이라 멱등성 fix와 책임이 다름

## Acceptance Criteria

- [ ] auth-service `/internal/auth/credentials` 동일 (accountId, email) 재시도 시 success(200/204) 응답
- [ ] 시그니처 불일치(같은 accountId, 다른 email)는 여전히 409 반환
- [ ] read-timeout 15s로 상향 (또는 e2e profile에서만)
- [ ] integration test: 4초 지연 stub 환경에서 signup 성공 (재시도 후 멱등 성공 시나리오)
- [ ] contract spec(`auth-internal.md`)에 멱등성 보장 명시
- [ ] e2e 환경 cold-start 시 orphan 미발생 확인 (수동 검증)
- [ ] 4개 서비스 회귀 없음

## Related Specs

- `specs/contracts/http/account-api.md §POST /api/accounts/signup`
- `specs/contracts/http/internal/auth-internal.md §POST /internal/auth/credentials`
- `specs/services/auth-service/architecture.md`
- `specs/services/account-service/architecture.md`

## Related Contracts

- `specs/contracts/http/internal/auth-internal.md` (멱등성 명시 추가)

## Edge Cases

- 동일 accountId로 다른 password 재요청: 409로 계속 거절 (replay attack 방지)
- 멱등 키 도입 시: 키 충돌(다른 페이로드, 같은 키) → 422 또는 409 — 정확한 정책은 구현 시 결정
- Resilience4j Retry가 Idempotency-Key를 그대로 재사용해야 멱등 의미 살아남 — 클라이언트 retry 정책과 정합 점검
- 멱등 응답 후 새 행 삽입 안 함 — DB unique constraint 활용

## Failure Scenarios

- 멱등 응답 로직에 버그 → 동일 (accountId, email)이지만 password_hash가 다른 경우를 success로 처리하면 보안 결함. 시그니처 비교 매우 신중히
- read-timeout 상향이 클라이언트 응답 지연으로 이어짐 → UX 영향. 15s가 한계, 그 이상 필요하면 비동기 전환 고려

## Implementation Notes

- `apps/auth-service/src/main/java/com/example/auth/presentation/internal/CredentialController.java` (또는 동등 경로) 에서 dedup 로직 구현
- `apps/account-service/src/main/resources/application.yml` 의 `account.auth-service.read-timeout-ms` 조정. e2e profile override는 `application-e2e.yml`에 추가 가능
- BE-246으로 e2e 환경 자체의 도달성 결함은 해소됐으나, cold-start race는 별도 결함이라 본 태스크에서 다룸
- 본 PR과 별개로 e2e harness가 만든 3건의 historical orphan(`direct@`, `trigger2@`, `aftergw...`)은 수동 DELETE로 정리됨 — 재현 시 동일 패턴

## Risk Assessment

**중간 위험도** — auth-service 핵심 보안 흐름(credential 등록) 동작 변경. 시그니처 비교 잘못 구현 시 보안 결함 가능. 단위 + integration 테스트 필수. 멱등 응답 정책은 contract에 명시되어야 다른 클라이언트도 안전하게 사용 가능.
