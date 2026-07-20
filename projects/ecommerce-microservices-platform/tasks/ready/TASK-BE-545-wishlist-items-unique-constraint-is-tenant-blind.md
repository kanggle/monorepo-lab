# TASK-BE-545 — `uq_wishlist_items_user_product` 가 테넌트를 모른다 (도달 불가이지만 결함)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (마이그레이션 1개 + 회귀 확인. 판정은 이미 끝났다)

> `TASK-BE-543` AC-2 에서 분리. 저쪽은 payment `order_id` 축을 닫았고, 이 축은 **범위를 좁게 유지하려고** 별 티켓으로 뺐다.

---

## Goal

`user-service` 의 `wishlist_items` 는 선체크와 제약의 축이 어긋나 있다:

| | 범위 |
|---|---|
| 제약 `uq_wishlist_items_user_product` (`V3__*.sql:8`) | `(user_id, product_id)` — **테넌트 미포함** |
| 선체크 (리포지터리) | `...AndTenantId` — **테넌트 스코프** |

선체크가 제약보다 **좁다.** `V4__add_tenant_id.sql:6` 이 *"unique constraints are unchanged"* 라고 **적어 두고 그 상태로 남겼다** — 즉 알려진 채 방치된 지점이다.

## 이미 판정된 것 (재측정 금지)

**도달 불가다.** `TASK-BE-540` AC-0 이 `user_id` 축을 확정했다 — 한 `user_id` 는 한 테넌트로 해석되므로 같은 `(user_id, product_id)` 가 두 테넌트에 존재할 수 없다. `TASK-BE-543` AC-0 이 그 판정의 현재 위치(BE-540 done 기록)를 재확인했다.

⇒ **런타임 사고는 나지 않는다.** 이 티켓은 500/409 결함이 아니라 **정합성 결함**이고, 우선순위는 낮다. `TASK-BE-540` § Edge 1 이 적은 대로 **도달 불가가 결함 없음은 아니다.**

## 왜 그래도 고치는가

1. 제약이 **표현하려는 불변식을 표현하지 못한다** — 의도는 "한 사용자가 한 상품을 두 번 찜하지 않는다" 이고, 그 사용자는 테넌트 안에서만 의미가 있다.
2. `user_id` 가 전역 유일이라는 성질이 **미래에 바뀌면** 이 제약은 조용히 교차 테넌트 충돌을 만든다. 지금 도달 불가인 이유가 제약 자신에 적혀 있지 않다.
3. `TASK-BE-540` 이 review 쪽 같은 결함을 `V7` 로 이미 정렬했다 — **형제가 정렬됐는데 이쪽만 남았다.**

## Scope

### In Scope

1. `wishlist_items` 유니크 제약을 `(tenant_id, user_id, product_id)` 로 재정의하는 Flyway 마이그레이션.
2. 선체크와 제약의 축이 일치하는지 확인(선체크는 이미 테넌트 스코프이므로 대개 무변경).

### Out of Scope

- **`user_id` 축 재측정** — `TASK-BE-540` 이 확정했다. 다시 재는 것은 낭비다(`TASK-BE-543` F1).
- payment `order_id` 축 — `TASK-BE-543` 이 닫았다.
- 무근거 선체크 2건(`product-service sellers.account_id`, `settlement-service commission_accrual`) — **백스톱조차 없는 더 무거운 클래스**이고 별 축이다(`TASK-BE-540` AC-4 목록).

## Acceptance Criteria

- **AC-0 (gate — 재측정)** — 착수 시 제약과 선체크를 **직접 다시 읽는다.** 위 표는 2026-07-20 스냅샷이고 출처가 아니라 가설이다. 이미 정렬돼 있으면 **STOP 후 보고**(다른 티켓이 먼저 고쳤을 수 있다).
- **AC-1** — 마이그레이션이 **무손실을 가정하지 않는다.** 재정의 전에 충돌 그룹을 세고, 있으면 `RAISE EXCEPTION` 으로 멈춘다(`V7`/`V8` 선례). "도달 불가니까 데이터도 깨끗할 것" 은 연역이지 관측이 아니다.
- **AC-2** — 제약 재정의 후에도 **정당한 사용이 막히지 않는다**: 서로 다른 테넌트의 같은 `(user_id, product_id)` 는 이제 둘 다 허용되고, **같은 테넌트 안의 중복은 여전히 거부**된다. 후자가 진짜 불변식이므로 테스트로 고정한다.
- **AC-3** — 마이그레이션이 왜 필요했는지(도달 불가지만 표현력 결함)를 마이그레이션 주석에 남긴다. 다음 사람이 "쓸데없는 변경" 으로 읽지 않도록.
- **AC-4** — `user-service` 빌드 + 테스트 GREEN. 로컬 Testcontainers 는 FLAKY — **CI Linux 가 권위.**

## Related Specs

- `apps/user-service/src/main/resources/db/migration/V3__*.sql:8` — 대상 제약
- `apps/user-service/src/main/resources/db/migration/V4__add_tenant_id.sql:6` — 그 상태로 남긴 지점(*"unique constraints are unchanged"*)
- `tasks/done/TASK-BE-540-*.md` — `user_id` 축 판정 + review 쪽 `V7` 선례
- `tasks/review/TASK-BE-543-*.md` — 이 티켓의 분리 출처

## Related Contracts

- 없음. 응답 코드가 바뀌지 않는다(도달 불가 경로이므로 관측 가능한 거동 변화 0).

## Edge Cases

1. **`migration-h2` 이중 관리** — 이 저장소에서 `migration-h2` 를 따로 두는 서비스가 있다. `user-service` 에 있는지 확인하고, 있으면 **양쪽을 함께** 갱신한다(한쪽만 고치면 테스트와 프로덕션이 갈라진다).
2. **인덱스 이름 재사용** — 같은 이름으로 재정의할지 새 이름을 쓸지 정하고, 롤백 시 충돌하지 않게 한다.
3. **도달 불가가 "테스트 불가" 는 아니다** — AC-2 의 교차 테넌트 허용 케이스는 픽스처로 직접 만들 수 있다. 단 그 픽스처가 **프로덕션에서 생성 가능한 상태인지**는 별개이고, 여기서는 *제약의 거동* 을 재는 것이므로 인위성이 정당하다 — 그 점을 테스트 Javadoc 에 적어라(`TASK-BE-542` 가 이 구분을 놓쳐 대가를 치렀다).

## Failure Scenarios

- **F1 — 도달 불가를 근거로 데이터 정합성 확인을 생략한다.** AC-1 이 막는다. 연역은 관측이 아니다.
- **F2 — 제약을 넓히기만 하고 같은 테넌트 중복 거부를 확인하지 않는다.** 진짜 불변식을 잃고도 초록이 뜬다. AC-2 가 막는다.
- **F3 — `user_id` 축을 다시 잰다.** 이미 답이 있다(`TASK-BE-540`). 모집단 물려받기의 반대 얼굴 — 이미 측정된 것의 재측정.

## Definition of Done

- [ ] AC-0 제약·선체크 재측정 (이미 정렬됐으면 STOP)
- [ ] AC-1 무손실 가정 없는 마이그레이션
- [ ] AC-2 교차 테넌트 허용 + 동일 테넌트 거부 테스트
- [ ] AC-3 마이그레이션 주석에 사유
- [ ] AC-4 user-service GREEN (CI Linux 가 권위)

## Notes

- **분량**: small.
- **dependency**: `선행` = `TASK-BE-540`(done, `user_id` 축 판정) · `TASK-BE-543`(review, 분리 출처). 실제 코드 의존은 없다.
- **이 task 가 방어하는 실패 모드**: **"지금 도달 불가" 는 제약이 옳다는 뜻이 아니다.** 도달 불가의 이유가 제약 밖(다른 테이블의 `user_id` 발급 규칙)에 있으면, 그 규칙이 바뀌는 날 이 제약은 아무 경고 없이 틀린 것이 된다.
