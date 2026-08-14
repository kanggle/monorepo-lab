# Task ID

TASK-BE-585

# Title

`admin-service` 의 `V3` 테넌트 축 마이그레이션이 **기존 admin_db 에 영원히 적용되지 않는다** — V99 가 `db/migration/` 에 있어 out-of-order

# Status

done

# Owner

wms-platform

# Task Tags

- bug
- migration
- read-model

---

# 배경 — `TASK-BE-584` AC-0 이 리셋 **직전**에 발굴

`TASK-BE-583` 이 `ADR-MONO-065` D1 을 구현하며 `V3__order_shipment_tenant_axis.sql` 을 추가했다.
IT 54건이 통과했고 3차원 머지 검증도 통과했다. 그런데 **손대지 않은 데모 볼륨에서 그 코드를 띄우면
admin-service 가 아예 뜨지 않는다.**

## 실측 (2026-08-14, `TASK-BE-584` AC-0 — 리셋 전 볼륨)

```
admin_db flyway_schema_history :  V1__init · V2__init_readmodel · V99__seed_dev_data
                                  (installed_rank 1·2·3 — V3 없음)

wms-admin-service   restarts=12 · health=starting          ← 크래시 루프
admin-service 로그  org.flywaydb.core.api.exception.FlywayValidateException:
                    Validate failed: Migrations have failed validation
                    "Detected resolved migration not applied to database: 3."

같은 순간, 운영자 토큰으로
  GET /api/v1/admin/dashboard/orders      →  HTTP 500
  GET /api/v1/admin/dashboard/shipments   →  HTTP 500
gateway 로그        okhttp connect 실패 — admin-service 에 도달조차 못 한다
```

## 원인 — `admin-service` 만 V99 를 `db/migration/` 에 둔다

| 서비스 | V99 위치 | 언제 적용되나 |
|---|---|---|
| `master` · `inbound` · `inventory` · `outbound` | `db/seed/` | devseed 오버레이가 `spring.flyway.locations` 를 열 때만 |
| **`admin`** | **`db/migration/`** | **항상 — 모든 환경에서** |

⇒ admin_db 는 처음부터 V99 를 적용해 왔고 이력의 마지막 version 이 **99** 다. 새로 들어온 `V3` 는
99 **앞**으로 정렬되므로 Flyway 기본값(`outOfOrder=false`)의 validate 가 거부한다.

🔴 **신선 볼륨은 이 결함에 대해 영구히 초록이다** — V1·V2·V3·V99 를 한 번에 순서대로 적용하기
때문이다. BE-583 의 IT 54건은 Testcontainers(매번 신선)에서 돌았고, `TASK-BE-584` 의 재시드 직후
admin_db 도 **V3 가 정상 적용**됐다. 즉 **가드가 볼 수 없는 자리에 결함이 있다.**
[[env_fresh_volume_ci_is_permanently_green_on_migration_order]] — 결함은 파일이 아니라 **이력 행**에 있다.

## 파급

- **`ADR-MONO-065` 의 테넌트 축이 기존 admin_db 에 도달하지 못한다.** 축을 넣었다는 사실과
  축이 적용됐다는 사실이 갈라져 있고, 지금까지 아무도 후자를 재지 않았다
- 기존 볼륨을 쓰는 **모든** 환경에서 admin-service 가 기동 불가 ⇒ 콘솔 wms 대시보드 8개 표면 전부 500
- 복구 경로가 현재는 **볼륨 초기화뿐**이다(= 데이터 폐기). 실 배포라면 성립하지 않는 선택지다

🔵 `outbound_db` 는 우연히 피했다 — tenant 마이그레이션이 `V17` 이고 V99 가 rank 19 로 **뒤에**
적용됐다. **다만 outbound 의 다음 마이그레이션(V19~)은 같은 함정에 걸린다** — 이 티켓은 admin 하나를
고치는 것으로 끝나지 않는다.

---

# Goal

`ADR-MONO-065` 의 `V3` 가 **기존 admin_db 에도 적용**되고, 같은 함정이 형제 서비스에서 재발하지
않도록 구조가 정리된다.

---

# Scope

## In Scope

- **AC-0 실측**: 5개 서비스의 V99 위치·flyway locations·현재 이력의 마지막 version 전수.
  🔴 outbound 의 다음 마이그레이션이 같은 함정에 걸리는지도 **세어서** 확인
- **AC-1 결정 + 구현**. 후보(배타 아님, 조합 가능):
  - (A) `admin-service` 의 V99 를 형제와 같이 `db/seed/` 로 옮기고 devseed 오버레이에 admin 추가.
    🔴 이미 V99 를 적용한 기존 DB 의 이력 행 처리가 남는다(**코드 이전에 데이터 문제**)
  - (B) dev-seed 를 version 이 아닌 **repeatable**(`R__`)로 바꾼다 — 정렬 문제 자체가 사라진다
  - (C) `spring.flyway.out-of-order=true` — 🔴 **가장 싸 보이고 가장 위험하다.** 순서 보증을
    통째로 포기하므로 이 티켓이 막으려는 종류의 사고를 다른 곳에서 허용한다. 채택하려면 근거를 적을 것
- **AC-2 가드**: **기존 볼륨을 재현하는** 검증. 신선 볼륨 테스트는 이 결함을 구조적으로 못 본다 —
  V1·V2·V99 만 적용된 DB 를 만들고 그 위에서 기동이 성립하는지 단언해야 **무는 가드**다

## Out of Scope

- `ADR-MONO-065` 의 결정 재검토 — 축의 내용은 옳다. 도달하지 못하는 것이 문제다
- 데모 볼륨 리셋으로 덮기 — 그것은 회피이고, 실 배포에는 없는 선택지다

---

# ✅ 실측 + 구현 (2026-08-14)

## AC-0 ① V99 위치 전수 — **admin 만 프로덕션 위치에 있다**

| 서비스 | dev 시드 위치 | 언제 적용되나 |
|---|---|---|
| `master` · `inbound` · `inventory` · `outbound` | `db/seed/` | devseed 오버레이가 열 때만 |
| **`admin`** | **`db/migration/`** | **항상 — 모든 환경에서** |
| `notification` · `gateway` | 없음 | — |

`admin-service` 의 yml 은 `application.yml` **하나뿐**이고 `locations: classpath:db/migration`,
프로파일 변형 0개다. 그래서 admin_db 는 처음부터 V99 를 적용해 왔고 이력의 마지막 version 이 **99** 다.
`ADR-MONO-065` 의 `V3` 는 99 **앞**으로 정렬되어 Flyway 기본값(`outOfOrder=false`)이 거부한다.

🔵 outbound 는 tenant 축이 `V17` 이고 V99 가 rank 19 로 **뒤에** 적용돼 우연히 피했다.
다만 형제 4개의 `db/seed/` 밴드(V99~V103)는 그대로 남아 있다 — outbound 의 다음 마이그레이션(`V19`)이
같은 크래시를 재현한다 ⇒ **`TASK-MONO-531`** 로 분리.

## AC-0 ② 🔴🔴 **가드는 이미 있었다 — 술어가 어긋났다**

`scripts/check-dev-seed-migration-band.sh`(`TASK-MONO-524`)가 정확히 이 불변식을 인코딩하고
있고, iam auth-service 가 **같은 예외**로 크래시한 실측까지 담고 있다. 그런데 초록이었다:

```bash
git ls-files ... '*/db/migration/*.sql' '*/db/migration-dev/*.sql'   # ← 글롭
# 분류는 디렉터리 이름: migration=프로덕션, migration-dev=dev 시드
```

- **`db/seed/` 가 글롭에 없다** ⇒ wms 형제 4개의 8개 파일은 검사된 적이 없다
- **admin 의 V99 는 `db/migration/` 에 있어 `prod_max=99` 로 집계된다** ⇒ 못 보는 정도가 아니라
  **천장 자체를 잘못 센다**. admin 은 `migration-dev` 가 없어 비교 대상에도 안 들어간다

⇒ 가드의 초록은 "wms 가 안전하다" 를 **한 번도 의미한 적이 없다**.
[[feedback_guard_predicate_wrong_verify_the_artifact]] · [[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

🔴 **이름 기반 술어는 실측으로 배제했다** — `db/migration/` 안의 `*seed*` 는 **31개**이고
대부분 iam·ecommerce 의 **정당한 프로덕션 마이그레이션**(테넌트·OIDC 클라이언트 시드)이다.
`*seed* ⇒ dev` 로 판정하면 오탐 ~30건이다. 그래서 가드 확장은 `db/seed/` 를 위치로 인식하는
방향이어야 하고, 그러면 형제 4개가 **즉시 빨개진다**(그들이 실제 위반이므로) ⇒ 가드 확장과
형제 해소는 **같은 PR** 이어야 하며 이 티켓의 범위를 넘는다 ⇒ **`TASK-MONO-531`**.

## AC-1 결정 + 구현 — `R__` 전환 (저장소가 이미 두 번 고른 답)

`TASK-MONO-524` 가 iam 에서 같은 계열을 닫으며 고른 것이 `R__` 다. repeatable 은 **버전이 없어**
순서 위반이 불가능하고 항상 versioned 뒤에 실행된다.

1. `V99__seed_dev_data.sql` → **`R__seed_dev_data.sql`** (`git mv`, 같은 위치)
2. 🔴 **전 INSERT 에 `ON CONFLICT DO NOTHING`** — 4/4. repeatable 은 checksum 이 바뀌면 재실행되고,
   기존 DB 에서는 V99 가 이미 넣은 행 위로 곧장 달린다. 이 절이 없으면 **이 변경이 구하려던 바로 그
   호스트에서** duplicate-key 로 죽는다(bite 로 확인 — 아래)
3. 🔴 **`R__` 전환만으로는 기존 DB 가 낫지 않는다** — 결함은 파일이 아니라 **이력 행**이다.
   version=99 행이 남아 `V3` 는 여전히 그 아래다. ⇒ `SPRING_FLYWAY_OUT_OF_ORDER: "true"` **호환 슬림**을
   `infra/demo/wms-devseed.override.yml` 의 admin 항목에 두고 **일몰 조건**을 적었다
   (`version='99'` 행이 유통 중 하나도 없으면 삭제). 🔵 신선 볼륨에서는 **no-op** 이다
4. 🔴 **`application.yml` 에 넣지 않았다** — out-of-order 는 순서 보증을 푸는 것이라 프로덕션 정책으로
   굳히면 안 된다. 고아 행을 가진 것은 데모 볼륨이므로 슬림도 데모에만 둔다
5. 파일 헤더의 거짓 주장(*"prod 프로파일이 location filter 로 걸러낸다"*)을 실측으로 대체.
   🔴 그 게이트는 **존재하지 않고** 이 시드는 전 환경에 적용된다 — 적용 **범위**를 바꾸는 것은
   프로덕션 provisioning 결정이라 **`TASK-BE-587`** 로 분리(이 티켓은 순서만 고치고 범위는 현상 유지)

## AC-2 가드 — 기존 볼륨을 **재현**하는 IT

`ExistingVolumeMigrationOrderIT` 신설(`@Tag("integration")`). 신선 DB 테스트로는 이 결함을
구조적으로 볼 수 없으므로, V1·V2 적용 후 **V99 워터마크 행을 심어** 실제 admin_db 형상을 만든다.
🔵 `target=2` 는 repeatable 을 막지 못한다(내 첫 판이 여기서 틀려 duplicate-key 로 죽었다) —
시드 행은 그대로 두고 R__ 이력 행만 V99 로 바꿔치기하는 것이 **더 충실한** 재현이다.

세 칸 (대조군이 곧 결함):

| 칸 | 단언 |
|---|---|
| `outOfOrderDisabled_reproducesTheCrash` | 🔴 **슬림을 끄면 여전히 죽는다** — `FlywayValidateException … not applied to database: 3`. 이 칸이 없으면 통과 칸이 "아무것도 재현 못 하는 테스트" 와 구별되지 않는다 |
| `outOfOrderEnabled_appliesTheTenantAxisOverAnExistingDatabase` | V3 적용 + `tenant_id` 2개 컬럼 생성 + `admin_role` **4행 유지**(중복 0) |
| `freshDatabase_migratesWithoutTheShim` | 신선 DB 는 슬림 없이도 통과 — 슬림이 no-op 임을 고정 |

🔴 **bite**: `ON CONFLICT DO NOTHING` 하나를 제거하니 정확히 두 번째 칸만 **RED**(3 tests, 1 failed),
복원 후 GREEN. 순서 절반은 첫 칸이 곧 bite 다(재현이 틀렸다면 그 칸이 실패한다).
회귀: `admin-service:test` + `integrationTest` 전체 **BUILD SUCCESSFUL**.

## AC-3 라이브 — 리셋하지 **않은** 볼륨에서 500 → 200

`TASK-BE-584` AC-0 이 측정한 상태를 그 볼륨 위에 그대로 되돌렸다(V3 이력 행 삭제 +
`tenant_id` 컬럼 2개 drop ⇒ V1·V2·V99 적용 + V3 미적용, 시드 4행 존재). 제품 데이터를 위조한 것이
아니라 **마이그레이션 이력을 복원**한 것이다. 이미지도 다시 구웠고 jar 안에 `V99` 가 없음을 직접 확인했다
(`R__seed_dev_data.sql` · `V1` · `V2` · `V3` — `TASK-MONO-524` 의 교훈).

```
before (TASK-BE-584 AC-0)   restarts=12 · health=starting · /dashboard/orders  HTTP 500
after  (이 변경)             restarts=0  · health=healthy  · /dashboard/orders  HTTP 200

이력   1|V1 · 2|V2 · 4|V99 · 5|V3 · 6|R__      ← V3 가 99 뒤에 적용됐다
컬럼   admin_order_summary.tenant_id · admin_shipment_summary.tenant_id
       ⇒ ADR-MONO-065 의 축이 **기존 DB 에 처음으로 도달**했다
admin_role = 4행                                ← R__ 가 기존 행 위로 재실행되고도 중복 0
```

🔵 `totalElements=0` 은 결함이 아니다 — 프로젝션 행이 `tenant_id=NULL` 이고(V3 는 nullable 추가,
소급 stamp 금지) `demo-corp` 는 restricted 라 제외된다. **`TASK-BE-584` 가 원래 예측한 `1 → 0` 이
이제야 확인된 것**이다. 그때는 500 이 그것을 가리고 있었다. 원본 `outbound_order.tenant_id=demo-corp`
와 대조해 귀속을 확정했다.

## 분리한 결함

| 티켓 | 내용 |
|---|---|
| `TASK-BE-587` | 이 시드가 **존재하지 않는 게이트 뒤에서 전 환경에 적용**된다 — 공개 UUID 의 `WMS_SUPERADMIN` 부트스트랩 계정 포함. 유일한 `admin_role` 출처라 그냥 지울 수 없다 |
| `TASK-MONO-531` | 밴드 가드가 `db/seed/` 를 못 본다 + 형제 4개의 잠재 밴드(V99~V103) 해소 |

---

# Acceptance Criteria

- [x] **AC-0 (실측)** — 완료. V99 위치 전수 ✅ · 🔴🔴 **가드가 이미 존재하는데 술어가 어긋나 있었다**(신규) ·
      이름 기반 술어는 오탐 31건 실측으로 배제 · 형제 4개 잠재 밴드 확인 → `TASK-MONO-531`. 상세는 위 §. 원문:
- [x] **AC-1 (결정 + 구현)** — 완료. **(B) `R__` 전환** + 멱등성 보강(4/4) + **호환 슬림**
      (`SPRING_FLYWAY_OUT_OF_ORDER`, 데모 한정 · 일몰 조건 명시). `TASK-MONO-524` 가 iam 에서 고른
      것과 같은 답이다. 🔴 기존 이력 행 복구를 포함한다 — R__ 전환만으로는 안 낫는다는 것이 이 AC 의 핵심.
      (C) 를 `application.yml` 에 넣는 것은 **거부**했다(프로덕션 순서 보증을 푸는 것). 원문:
- [x] **AC-2 (가드)** — 완료. `ExistingVolumeMigrationOrderIT` 3칸(대조군=결함 재현 포함).
      🔴 **bite 확인**: `ON CONFLICT` 하나 제거 → 해당 칸만 RED, 복원 → GREEN.
      `test` + `integrationTest` 전체 BUILD SUCCESSFUL. 원문:
- [x] **AC-3 (라이브)** — 완료. 리셋하지 **않은** 볼륨에 `TASK-BE-584` AC-0 상태를 복원한 뒤 기동:
      `restarts=12 · 500` → **`restarts=0 · health=healthy · 200`**, V3 가 99 뒤에 적용되고
      `tenant_id` 2개 컬럼이 **기존 DB 에 처음 도달**, `admin_role` 4행 유지(중복 0). 원문:

# Related Specs

- `projects/wms-platform/tasks/ready/TASK-BE-584-*.md` § AC-0 ② — 이 티켓의 출처(실측 원본)
- [`docs/adr/ADR-MONO-065`](../../../../docs/adr/ADR-MONO-065-wms-admin-read-plane-tenant-axis.md) § D1 — 도달하지 못하고 있는 그 결정
- `projects/wms-platform/tasks/done/TASK-BE-580-*.md` — V99 위치 불일치를 한 번 겪은 선례(`db/dev` vs `db/seed`)

# Related Contracts

- 없음(스키마 적용 경로 문제 — 계약 표면 불변)

# Edge Cases

- 🔴 **V99 를 `db/seed/` 로 옮기면 기존 DB 의 이력 행(version 99)이 남는다** — Flyway 는 resolved
  되지 않는 applied 마이그레이션에 대해서도 validate 에서 실패할 수 있다(`ignoreMigrationPatterns`
  없이는). 옮기는 것만으로 끝나지 않는다
- 데모는 리셋으로 살아나므로 **이 결함은 데모에서 잘 안 보인다.** 판정은 반드시 기존 볼륨에서

# Failure Scenarios

- **신선 볼륨 테스트로 검증하고 닫는다** → 결함은 그 자리에 그대로 있고 가드만 초록이 된다.
  이 티켓이 발굴된 경위가 정확히 그것이다
- **`out-of-order=true` 한 줄로 덮는다** → 증상은 사라지고 순서 보증도 사라진다. 다음 사고는
  더 조용하다
- **파일만 옮기고 기존 이력 행을 안 본다** → 기존 DB 는 여전히 죽어 있다

# Definition of Done

- [x] AC-0 ~ AC-3 전부
- [x] 기존 볼륨에서 라이브 확인 — 500 → 200, restarts 12 → 0
- [x] 발견한 결함은 별도 티켓 — `TASK-BE-587`(시드가 전 환경 적용) · `TASK-MONO-531`(가드 사각 + 형제 4개)
- [x] Ready for review
