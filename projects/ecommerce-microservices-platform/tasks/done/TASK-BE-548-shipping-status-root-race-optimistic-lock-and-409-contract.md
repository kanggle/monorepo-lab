# TASK-BE-548 — 배송 상태 동시 전이의 root 경합 제거 (`@Version` 낙관적 락 + 409 계약)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (마이그레이션 + 에러 계약 + BE-547 과의 매개자 상호작용 판정. "컬럼 하나 추가"가 아니다)

> `TASK-BE-537`(done, 재현·근본원인)이 root 경합을 확인하고, `TASK-BE-547`(done, PR #2811)이 그 **실사용 피해(중복 알림)**를 발행측 결정적 event_id 로 종결하면서 명시적으로 후속으로 미룬 그 작업. **BE-547 은 피해를 없앴지 root 경합 자체를 없애지 않았다.**

---

## Resolution — AC-0 판정 = **won't-do** (2026-07-23, 근거 있는 종결)

AC-0 게이트("해야 하는가")를 착수 시 실측 판정한 결과 **won't-do**. 티켓 본문의 F3·Notes·AC-0 이 명시적으로 허용한 결과이며, 코드 실측이 "순가치"를 뒷받침하지 못했다.

**재측정 근거 (코드가 이긴다):**

1. **`@Version` 부재 재확인 (AC-0.1)** — `ShippingJpaEntity` 에 `@Version` 0건(known-positive `wms AsnJpaEntity` 대조 — 패턴은 저장소에 유효, 이 서비스에만 없음). ✔ 전제 유효.
2. **고객 피해·클라이언트 응답은 이미 해결 (AC-0.2)** — `ConcurrentStatusTransitionIntegrationTest`(BE-547 회귀 가드)가 증명: 동시 이중 SHIPPED 에서 정확히 한 tx 만 커밋, 패자는 아웃박스 PK 유니크 위반(23505)→`DataIntegrityViolationException`→`GlobalExceptionHandler` DIVE 백스톱→**이미 409**. `ShippingStatusChanged`·`ManualShipConfirmRequested` 각 1건, 최종 SHIPPED. 즉 **피해(중복 알림)와 패자 409 응답은 BE-547 로 이미 완결.**
3. **"무해한 중복 UPDATE 제거"라는 순가치가 존재하지 않음** — `SpringShippingEventPublisher` 는 아웃박스 INSERT 를 **호출자 트랜잭션 안에서** 수행([SpringShippingEventPublisher.java:84-92](../../apps/shipping-service/src/main/java/com/example/shipping/infrastructure/event/SpringShippingEventPublisher.java)). 패자 tx 의 `shippings` UPDATE 와 outbox INSERT 는 **같은 트랜잭션**이라, PK 충돌이 tx 전체를 원자적으로 롤백한다 ⇒ **패자의 중복 UPDATE 는 애초에 commit 되지 않는다.** 티켓 Goal 의 "두 번째 트랜잭션이 shippings 에 무해한 중복 UPDATE 를 쓰는 것 자체는 그대로다" 전제가 코드로 **반증**됨.
4. **남은 것은 순수 미관뿐** — `@Version` OptimisticLock 이든 아웃박스-PK 충돌이든 결과가 **동일**(롤백·409·이벤트 1건·최종 SHIPPED). 실패 모드의 "정확성"은 관측 가능한 행동 차이가 없다.

**비용은 실질 (F3 gold-plating):**

- DB 마이그레이션(`version` 컬럼 + 기존 행 backfill + `migration-h2` 이중 관리).
- **F2 위험** — 같은 "동시 충돌"에 409 코드가 둘(`DATA_INTEGRITY_VIOLATION` vs 신규 `OPTIMISTIC_LOCK_CONFLICT`)로 갈려 계약이 혼란.
- **AC-2 dead-path** — `@Version` 이 UPDATE 에서 먼저 터지면 BE-547 의 아웃박스-PK→DIVE 경로가 동시 케이스에서 도달불가가 되지만, event_id 결정성은 소비자 dedup·순차 재시도·carrier 발행 경로용으로 **유지 필수**(F4/Out of Scope) ⇒ 작동하는 가드를 그림자로 덮고 둘 다 유지 + 죽은 경로 문서화. 부품만 늘고 행동은 동일.
- **AC-3 회귀 위험** — 무인 `CarrierAdvanceProcessor`(REQUIRES_NEW 배치)에 현재 없는 `ObjectOptimisticLockingFailureException` 실패 모드를 신규 도입 → 재시도/무시 정책이 새로 필요.

**결론**: 피해는 사라졌고, 패자는 이미 409 를 받고, 제거 대상이라던 중복 UPDATE 는 persist 조차 안 된다. 순가치 ≈ 0 인데 마이그레이션·2차 409 코드·dead-path·신규 배치 실패 모드를 감수하는 것은 티켓 자신이 정의한 **F3**. BE-547 이 이미 **공급원**에서 접었으므로 `@Version` 은 중복 2차 가드 — [[project_deterministic_event_id_outbox_pk_collapses_dupes]] 원칙("공급원을 고쳐라")과도 일치.

**BE-547 은 되돌리지 않는다** (F4) — 결정적 event_id 는 소비자 idempotency 키로 독립적으로 필요. 상보 관계, 대체 아님.

> 아래 원본 Goal/Scope/AC 는 이력 보존용. 구현되지 않았다.

---

## Goal

동시 이중 `PUT /api/shippings/{id}/status`(→SHIPPED)에서 두 트랜잭션이 **둘 다 `PREPARING` 을 읽고 둘 다 상태기계를 통과해 둘 다 `status=SHIPPED` 를 커밋**하는 root 경합은 여전히 남아 있다(`ShippingCommandService.updateStatus` 는 `@Version` 없는 read-modify-write). BE-547 은 그 **결과**(중복 이벤트/알림)를 발행측에서 접었을 뿐, 두 번째 트랜잭션이 `shippings` 행에 **무해한 중복 UPDATE**(같은 값 `SHIPPED`)를 쓰는 것 자체는 그대로다.

이 티켓은 `shippings` 에 `@Version` 낙관적 락을 걸어 **두 번째 커밋을 애초에 거부**하고, 그 거부를 **500 이 아니라 409 로 정식 응답**하게 한다(BE-537 이 "지금은 500 으로 샌다"고 지적한 바로 그 경로).

**⚠️ 이 티켓은 "해야 하는가"부터 판정한다.** BE-547 이 이미 (a) 고객 피해를 없앴고 (b) 경합 패자에게 **이미 409 를 준다**(아웃박스 PK 충돌 → 기존 DIVE 백스톱). 남은 것은 **무해한 중복 UPDATE 제거 + 더 깨끗한 실패 모드**뿐이다. 이는 버그 수정이 아니라 **미관/견고성 개선**이다. AC-0 이 "비용 대비 가치"를 판정하고, **"할 가치 없음 → won't-do 로 종결"도 유효한 결과다**(BE-537 의 음성 결과가 유효했던 것과 같은 틀).

---

## Scope

### In Scope

1. `ShippingJpaEntity` 에 `@Version` 필드 + `shippings` 테이블에 `version` 컬럼 마이그레이션(기존 행 안전 backfill).
2. 두 번째 동시 커밋이 `OptimisticLockException`(또는 Spring `ObjectOptimisticLockingFailureException`)으로 롤백되게 하고, 그것을 `GlobalExceptionHandler` 에서 **409** 로 매핑(현재는 미매핑 → 500 누출).
3. **BE-547 과의 매개자 상호작용 판정**(아래 🔴). `@Version` 이 status UPDATE flush 에서 먼저 터지므로 BE-547 의 아웃박스-PK-충돌 경로가 이 동시 케이스에서 **도달 불가**가 될 수 있다 — 그 관계를 실측하고, 에러코드 일관성(둘 다 409 인데 코드가 다른가)을 판정한다.
4. 계약 갱신(409 응답 + 에러코드).

### Out of Scope

- **BE-547 의 발행측 결정적 event_id 는 유지·불변**(이 티켓이 대체하지 않는다). 그 결정적 키는 아웃박스 PK 매개자 역할 외에도 **소비자 dedup 의 idempotency 키**로 여전히 필요하다 — `@Version` 은 발행 자체를 막지만, 순차 재시도·다른 발행 경로(carrier)의 중복까지 소비자가 접는 건 event_id 몫이다. **둘은 상호 대체가 아니라 상보다.**
- notification-service / 소비자 측 — BE-547 로 이미 정상.
- 다른 상태 전이 엔드포인트로의 `@Version` 일반 확산("일관성" 스윕) — 이 경로에 국한.

---

## Acceptance Criteria

- **AC-0 (gate — 가치 판정 + 재측정)** — 착수 시 다음을 **직접 확인**하고, **"진행"과 "won't-do" 를 이 게이트에서 판정**한다:
  1. `@Version` 이 여전히 `shipping-service` 에 0건인가(known-positive `wms .../AsnJpaEntity.java` 로 자기검증).
  2. BE-547 의 결정적 event_id + 아웃박스 PK 가 여전히 경합 패자에게 409 를 주고 있는가(즉 **고객 피해와 클라이언트 응답은 이미 해결됨** — 이 티켓의 순가치는 "중복 UPDATE 제거 + OptimisticLock 이라는 더 정확한 실패 모드"뿐임을 확인).
  3. 그 순가치가 마이그레이션 + 에러코드 + (아래) 도달불가 경로 정리의 비용을 정당화하는가. **정당화 못 하면 근거를 적고 won't-do 로 종결**(F3).
- **AC-1 (진행 시)** — `@Version` 추가 후 두 번째 동시 커밋이 `ObjectOptimisticLockingFailureException` 으로 롤백되고, `GlobalExceptionHandler` 가 그것을 **409** 로 매핑(500 아님). 마이그레이션이 기존 `shippings` 행에 `version` 기본값을 안전하게 부여.
- **AC-2 (BE-547 매개자 재조정)** — `@Version` 이 status UPDATE 에서 먼저 터지는지 **실측**하고, 그 결과 BE-547 의 아웃박스-PK→DIVE→409 경로가 이 동시 케이스에서 도달 불가가 되는지 판정. 도달 불가라면 그 사실을 코드/주석/계약에 명시(죽은 경로를 설명 없이 남기지 않는다). **에러코드 일관성**: `@Version` 409 와 아웃박스-PK 409(`DATA_INTEGRITY_VIOLATION`)가 "같은 동시 충돌"에 대해 서로 다른 코드를 내지 않도록 통합/구분을 판정(F2).
- **AC-3** — 정당한 전진 전이(`PREPARING→SHIPPED→IN_TRANSIT→DELIVERED`)와 정당한 비경합 업데이트가 `@Version` 도입으로 깨지지 않음. carrier advance / `markShippedByOrderId` 등 다른 read-modify-write 발행 경로가 `@Version` 하에서 의도대로 동작하는지(경합 시 OptimisticLock, 정상 시 통과) 확인.
- **AC-4** — 동시성 테스트가 이제 두 번째 스레드의 실패 지점이 **status UPDATE 의 `@Version`**(아웃박스 PK 아님)임을 보이고, 여전히 이벤트 1건·최종 SHIPPED. BE-547 이 회귀 가드로 전환한 `ConcurrentStatusTransitionIntegrationTest` 를 확장/조정(2→1 은 유지, 실패 지점만 재확인).
- **AC-5** — 계약 표면 변경(409 응답, 에러코드)은 `specs/contracts/http/shipping-api.md` 를 **구현 전에** 갱신. BE-547 이 이미 적은 409 DATA_INTEGRITY_VIOLATION 항목과의 관계(통합/구분)를 함께 정리.
- **AC-6** — `shipping-service` 빌드 + 테스트 GREEN. **로컬 Windows Testcontainers FLAKY — CI Linux 가 권위.**

---

## Related Specs

- `apps/shipping-service/.../ShippingCommandService.java:90-120` — `updateStatus` read-modify-write(@Version 부재 지점)
- `apps/shipping-service/.../infrastructure/event/ShippingOutboxEntity.java` + `SpringShippingEventPublisher.java` — BE-547 이 결정적 event_id=아웃박스 PK 로 만든 지점(이 티켓과 매개자가 겹침)
- `apps/shipping-service/.../interfaces/rest/controller/GlobalExceptionHandler.java:147-160` — 현재 DIVE→409 백스톱(OptimisticLock 은 아직 미매핑 → 500)
- `tasks/done/TASK-BE-537-*.md` — root 경합 재현 + `@Version` 이 실패 모드를 500 으로 바꾼다는 지적
- `tasks/done/TASK-BE-547-*.md` — 발행측 결정적 채번으로 피해 종결, 이 root 수정을 후속으로 명시

## Related Contracts

- `specs/contracts/http/shipping-api.md` — PUT status 엔드포인트 409 항목(BE-547 이 DATA_INTEGRITY_VIOLATION 로 추가). `@Version` 경로의 409 와 통합/구분을 선행 갱신.
- `platform/error-handling.md` — 새 에러코드를 등록한다면(예: `OPTIMISTIC_LOCK_CONFLICT`) 레지스트리 갱신. 기존 `DATA_INTEGRITY_VIOLATION` 로 통합한다면 그 판정을 기록.

---

## Edge Cases

1. **🔴 BE-547 매개자와의 선후 관계** — `@Version` UPDATE 가 아웃박스 INSERT 보다 flush 순서상 먼저 터지면(대개 그렇다) BE-547 의 아웃박스-PK 충돌은 동시 케이스에서 도달 불가. 하지만 event_id 결정성은 소비자 dedup·재시도 방어로 여전히 필요 — **BE-547 을 되돌리지 말 것.**
2. **다른 발행 경로도 `@Version` 대상이 된다** — carrier webhook / refresh-tracking(`CarrierAdvanceProcessor`, REQUIRES_NEW)도 read-modify-write 라 `@Version` 하에서 경합 시 OptimisticLock 을 던진다. 언대 배치 sweep 은 항목별 격리(REQUIRES_NEW)라 한 건 실패가 배치를 오염 안 시켜야 함 — 재시도/무시 정책 확인.
3. **마이그레이션 backfill** — 기존 행 `version` 기본값(0/1), H2 이중 관리(`migration-h2`) 확인.
4. **에러코드 이중화(F2)** — "동시 충돌"이 경로에 따라 `DATA_INTEGRITY_VIOLATION`(아웃박스 PK)와 새 코드(@Version) 둘로 갈리면 계약이 혼란스럽다. 하나로 통합하거나, 서로 다른 의미임을 명확히.

## Failure Scenarios

- **F1 — `@Version` 만 추가하고 `OptimisticLockException` 을 500 으로 방치.** BE-537 이 지적한 바로 그 누출. 반드시 409 매핑(AC-1).
- **F2 — 같은 동시 충돌에 409 코드가 둘.** 아웃박스-PK 409 와 @Version 409 가 다른 코드면 소비자·운영자에게 혼란. AC-2/AC-5 가 통합/구분을 강제.
- **F3 — 가치 판정 없이 gold-plating.** 남은 것은 무해한 중복 UPDATE 뿐 — 버그가 아니다. AC-0 이 "할 가치 있는가"를 먼저 묻고, 없으면 won't-do 가 정답.
- **F4 — BE-547 을 "대체됐다"며 되돌림.** event_id 결정성은 소비자 idempotency 키로 독립적으로 필요. 상보 관계이지 대체 아님(Out of Scope).

---

## Definition of Done

- [ ] AC-0 가치 판정 + 재측정 (진행 or 근거 있는 won't-do)
- [ ] AC-1 `@Version` + OptimisticLock→409 (진행 시)
- [ ] AC-2 BE-547 매개자 재조정 + 에러코드 일관성
- [ ] AC-3 정당한 전이·타 발행 경로 무회귀
- [ ] AC-4 동시성 테스트 실패 지점 재확인 (여전히 이벤트 1건)
- [ ] AC-5 계약 선행 갱신 (409 통합/구분)
- [ ] AC-6 GREEN (CI Linux 권위)

---

## Notes

- **분량**: small–medium(진행 시). 파일은 적으나 마이그레이션·에러계약·BE-547 매개자 상호작용 판정이 실질. AC-0 이 won't-do 로 끝나면 doc-only.
- **dependency**: `선행` = `TASK-BE-537`(done, 재현·root 원인·500 누출 지적), `TASK-BE-547`(done, 피해 종결·이 티켓을 후속으로 명시). **BE-547 과 상보**(대체 아님).
- **이 task 가 방어하는 실패 모드**: **피해가 이미 사라졌다고 root 경합이 사라진 건 아니다 — 그러나 "root 를 고치는 게 항상 옳다"도 아니다.** 남은 중복 UPDATE 는 무해하다. 이 티켓의 첫 일은 코드가 아니라 **"이 정도 순가치에 마이그레이션을 감수할 가치가 있는가"의 정직한 판정**이다. [[project_deterministic_event_id_outbox_pk_collapses_dupes]] [[env_test_fixture_impossible_input_proves_nothing]]
