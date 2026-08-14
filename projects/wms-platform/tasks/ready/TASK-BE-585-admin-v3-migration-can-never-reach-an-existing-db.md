# Task ID

TASK-BE-585

# Title

`admin-service` 의 `V3` 테넌트 축 마이그레이션이 **기존 admin_db 에 영원히 적용되지 않는다** — V99 가 `db/migration/` 에 있어 out-of-order

# Status

ready

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

# Acceptance Criteria

- [ ] **AC-0 (실측)** — 5개 서비스 × (V99 위치 · flyway locations · 현재 이력 마지막 version)
      전수 표. outbound 의 다음 마이그레이션 위험 포함
- [ ] **AC-1 (결정 + 구현)** — A/B/C(또는 조합) 중 하나 + 근거. 🔴 **기존 이력 행을 가진 DB 가
      어떻게 복구되는지**를 반드시 포함한다(파일만 옮기면 기존 DB 는 그대로 죽어 있다)
- [ ] **AC-2 (가드)** — **기존 볼륨을 재현한** 상태에서 무는 것을 확인. 🔴 bite 검증:
      결함을 되돌리면 RED 가 되는지 실제로 본다. 신선 볼륨만 쓰는 가드는 이 AC 를 만족하지 않는다
- [ ] **AC-3 (라이브)** — 리셋하지 **않은** 데모 볼륨에서 admin-service 가 뜨고
      `/dashboard/orders` 가 200 을 낸다

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

- [ ] AC-0 ~ AC-3 전부
- [ ] 기존 볼륨에서 라이브 확인
- [ ] Ready for review
