# Task ID

TASK-BE-079-FIX3-rewrite-soft-delete-re-review-integration-test

# Title

review-service: 소프트 삭제 후 재리뷰 통합 테스트 누락 추가

# Status

done

# Owner

backend

# Task Tags

- test

---

# Goal

TASK-BE-079-FIX2 리뷰 결과, 소프트 삭제된 리뷰가 있는 상품을 재구매한 사용자가 해당 상품에 리뷰를 다시 작성할 수 있음을 검증하는 통합 테스트가 누락되어 있다.

Acceptance Criteria 항목:
> review-service 통합 테스트에 소프트 삭제 후 재리뷰 시나리오 테스트 추가

V2 마이그레이션(`V2__fix_unique_constraint_for_soft_delete.sql`)으로 `ACTIVE` 리뷰만 unique 제약이 적용되도록 수정되었으나, 이 동작을 실제 DB 레이어에서 검증하는 통합 테스트가 없다.

---

# Scope

## In Scope

- `apps/review-service/src/test/java/com/example/review/infrastructure/persistence/ReviewRepositoryIntegrationTest.java`에 다음 테스트 추가:
  - 동일 사용자+상품 조합으로 리뷰 저장 → 소프트 삭제 → 동일 사용자+상품 조합으로 리뷰 재저장 성공 검증
  - 소프트 삭제 후 `existsByUserIdAndProductId` 결과가 `false`로 변경됨 검증 (이미 ACTIVE 리뷰만 체크하는 경우)

## Out of Scope

- 기능 로직 변경
- 다른 서비스 변경

---

# Acceptance Criteria

- [ ] `ReviewRepositoryIntegrationTest`에 소프트 삭제 후 동일 사용자+상품 재리뷰 저장 성공 시나리오 테스트 추가
- [ ] 테스트가 실제 PostgreSQL TestContainer에서 V2 partial index를 사용하여 실행됨
- [ ] 테스트 메서드 이름이 `{scenario}_{condition}_{expectedResult}` 패턴 준수

---

# Related Specs

- `specs/services/review-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/review-api.md`

---

# Edge Cases

- V1 unique 제약이 V2로 교체되었음을 DB에서 실제로 확인해야 함
- `existsByUserIdAndProductId`가 ACTIVE 상태의 리뷰만 체크하는지도 함께 검증

---

# Failure Scenarios

- Partial index가 제대로 적용되지 않으면 재리뷰 저장 시 DB unique violation 발생

---

# Background

## 현황 (FIX2 리뷰 결과)

### Issue (Warning): 소프트 삭제 후 재리뷰 통합 테스트 누락

`ReviewRepositoryIntegrationTest`에 소프트 삭제 후 재리뷰 시나리오 테스트가 없다.

V2 마이그레이션 파일은 존재하나 해당 동작을 통합 테스트로 검증하지 않아 Acceptance Criteria가 미충족 상태다.
