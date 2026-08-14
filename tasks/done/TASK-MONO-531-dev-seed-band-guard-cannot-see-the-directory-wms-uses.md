# Task ID

TASK-MONO-531

# Title

dev-seed 밴드 가드가 **wms 가 쓰는 디렉터리를 보지 못한다** — 같은 결함이 형제 4개에 그대로 남아 있다

# Status

done

# Owner

monorepo

# Task Tags

- guard
- migration
- drift

---

# 배경 — `TASK-BE-585` AC-0 이 발굴. **가드는 있었고, 술어가 어긋났다**

`scripts/check-dev-seed-migration-band.sh`(`TASK-MONO-524`)는 정확히 이 결함 계열을 위해
존재한다. 헤더가 직접 적고 있다:

> *"A dev-only Flyway seed must never carry a version ABOVE the highest version in its sibling
> production db/migration directory. … 🔴 CI IS STRUCTURALLY BLIND TO THIS."*

그리고 iam auth-service 가 **같은 예외로** 크래시한 실측까지 담고 있다
(`Detected resolved migration not applied to database: 0032.`, 2026-08-11).

그런데 2026-08-14 `TASK-BE-584` 가 발견한 wms admin-service 의 크래시를 이 가드는 **잡지 못했다.**

## 실측 — 술어가 못 보는 곳이 두 군데다

가드의 글롭은 이것뿐이다:

```bash
git ls-files --cached --others --exclude-standard \
    '*/db/migration/*.sql' '*/db/migration-dev/*.sql'
```

그리고 디렉터리 **이름**으로 분류한다: `migration`=프로덕션, `migration-dev`=dev 시드.

| 사각 | 실측 |
|---|---|
| ① **`db/seed/` 가 글롭에 없다** | wms 형제 4개는 dev 시드를 전부 여기 둔다 — `master`(V99·V100·V101·V102·V103) · `inbound`(V99) · `inventory`(V99) · `outbound`(V99). **8개 파일이 한 번도 검사된 적 없다** |
| ② **`db/migration/` 안의 dev 시드는 프로덕션으로 집계된다** | admin 의 `V99__seed_dev_data.sql` 은 `migration` 디렉터리에 있어 `prod_max=99` 로 **읽힌다**. 못 보는 정도가 아니라 **천장 자체를 잘못 세운다**. admin 은 `migration-dev` 가 없어 `dev_dirs` 에도 안 들어가므로 비교가 아예 일어나지 않는다 |

⇒ 가드는 `dev_dirs` 3개(iam auth/account/admin)에 대해서만 판정하며 **초록이었고**, 그 초록은
"wms 가 안전하다" 를 한 번도 의미한 적이 없다.

## 🔴 남은 위험은 실재한다 — 형제 4개는 아직 그대로다

`TASK-BE-585` 는 admin 하나만 `R__` 로 고쳤다(그것만 실제로 터져 있었다). 형제 4개는:

```
outbound  db/migration 최고 = V18   ·  db/seed = V99
inbound   db/migration 최고 = V9    ·  db/seed = V99
master    db/migration 최고 = ?     ·  db/seed = V99·V100·V101·V102·V103
inventory db/migration 최고 = ?     ·  db/seed = V99
```

이 위치는 데모 오버레이(`wms-devseed.override.yml`)가 열 때만 적용되지만, **열리는 순간
V99 가 그 DB 의 최고 적용 버전이 된다.** 그 다음 프로덕션 마이그레이션(outbound 의 `V19`)이
추가되면 admin 과 **한 글자도 다르지 않은 크래시**가 데모에서 재현된다.

🔵 지금 안 터진 이유는 설계가 아니라 **아직 아무도 V19 를 안 써서**다.

---

# Goal

가드의 술어가 이 저장소가 실제로 쓰는 **모든** dev-seed 위치를 덮고, 형제 4개의 잠재 밴드가
해소된다. 가드가 초록이면 그것이 "안전하다" 를 의미한다.

---

# Scope

## In Scope

- **AC-0 실측**: dev 시드가 사는 디렉터리 전수(`migration-dev` · `seed` · 그 밖에 있는지) ·
  각 서비스의 프로덕션 최고 버전 · 밴드 위반 목록
- **AC-1 가드 확장**: `db/seed/` 를 dev 위치로 인식. 🔴 **"0건은 계측 실패" 로직도 함께 확장**할 것 —
  지금 가드는 `migration-dev` 를 못 찾으면 fail-closed 하는데, `seed` 에 대해서도 같은 보호가 필요하다
- **AC-2 형제 4개 해소**: 밴드 위반을 없앤다. `TASK-MONO-524`·`TASK-BE-585` 가 두 번 고른 답은
  **`R__` 전환**이다. 🔴 master 는 5개 파일에 **FK 순서 의존**이 있다(warehouse→zone→location→sku→partner)
  — repeatable 은 description 사전순으로 실행되므로 `R__01_…` 형태의 접두사가 필요하다
- **AC-3 기존 볼륨**: 이미 V99~V103 을 적용한 데모 DB 가 깨지지 않는다(고아 이력 행 처리)

## Out of Scope

- `admin-service` — `TASK-BE-585` 가 이미 `R__` 로 전환했다
- admin 시드의 **적용 범위**(프로덕션에 심기는가) — `TASK-BE-587`
- 🔴 **이름 기반 술어**(`*seed*` 를 dev 로 간주) — 실측으로 배제됐다: `db/migration/` 안의
  `*seed*` 는 **31개**이고 대부분 iam·ecommerce 의 **정당한 프로덕션 마이그레이션**이다
  (테넌트·OIDC 클라이언트 시드). 이 술어를 쓰면 오탐 ~30건이다

---

# Acceptance Criteria

- [x] **AC-0 (실측)** — dev 시드 디렉터리 전수 + 서비스별 프로덕션 최고 버전 + 위반 목록.
      🔴 세지 않고 시작하면 낙오가 남는다(이 가드가 처음 놓친 이유가 정확히 그것이다)
- [x] **AC-1 (가드 확장)** — `db/seed/` 포함. **확장 전 가드로 위반이 안 잡히고 확장 후 잡히는 것**을
      실행으로 보인다(술어가 실제로 넓어졌다는 증거)
- [x] **AC-2 (형제 해소)** — 위반 0건. 🔴 master 의 FK 순서가 R__ 사전순으로도 유지되는지
      **실제로 적용해서** 확인한다(순서가 틀리면 FK 위반으로 죽는다 — 이것이 이 AC 의 진짜 위험이다)
- [x] **AC-3 (기존 볼륨 재현)** — V99~V103 이 적용된 상태를 재현하고 그 위에서 기동한다.
      🔴 신선 볼륨 테스트는 이 결함 계열을 **구조적으로** 볼 수 없다 —
      `TASK-BE-585` 의 `ExistingVolumeMigrationOrderIT` 가 쓸 수 있는 형틀이다
- [x] **AC-4 (bite)** — 가드가 무는지: 위반을 하나 되돌리면 RED, 복원하면 GREEN

# Related Specs

- `scripts/check-dev-seed-migration-band.sh` — 확장 대상
- `tasks/done/TASK-MONO-524-*.md` — 이 가드의 출처. `R__` 전환 결정과 그 근거
- `projects/iam-platform/docs/flyway-dev-seed-migrations.md` § 6 — 기존 DB 복구 절차
- `projects/wms-platform/tasks/done/TASK-BE-585-*.md` — 이 티켓의 출처(admin 을 고치며 사각 발견)
- `projects/wms-platform/apps/master-service/src/test/java/com/wms/master/integration/ExistingSeedVolumeMigrationOrderIT.java` — AC-2·AC-3 판정 장치

# Related Contracts

- 없음(빌드/가드 표면)

# Edge Cases

- 🔴 **이미 적용된 `R__` 는 rename 하면 validate 가 실패한다**(`TASK-MONO-524` § finding 2) —
  전환 시 파일명을 처음부터 최종형으로 정할 것
- 🔴 R__ 는 **매 checksum 변경마다 재실행**된다 ⇒ 전 문장이 멱등이어야 한다(`ON CONFLICT DO NOTHING`).
  ~~wms 시드 8개는 현재 멱등이 아니다~~ — **틀렸다. 8/8 전부 이미 멱등이었다**(실측 § 참조).
  admin 이 보강을 필요로 했다는 사실을 세어 보지 않고 형제에게 전이시킨 서술이다
- 없어진 **versioned** 마이그레이션은 `future` 로 조용히 용인되지만 없어진 **repeatable** 은 아니다 —
  이 비대칭이 전환 순서를 정한다

# Failure Scenarios

- **가드만 넓히고 형제를 안 고친다** → CI 가 빨개진다. 둘은 같은 PR 이어야 한다
- **`*seed*` 이름으로 판정** → 오탐 ~30건(실측). § Out of Scope 가 막는다
- **신선 볼륨에서 검증하고 닫는다** → 이 결함 계열은 거기서 영원히 초록이다
- **master 를 사전순 고려 없이 R__ 로 바꾼다** → FK 위반으로 데모가 기동 실패

# Definition of Done

- [x] AC-0 ~ AC-4 전부
- [x] 가드 초록이 "안전하다" 를 의미한다
- [x] Ready for review

---

# 실측 (2026-08-15)

## AC-0 — dev 시드 위치 전수. 🔴 사각이 **두 개가 아니라 세 개**였다

`src/main/resources/db/` 아래에 실재하는 디렉터리는 4종이다(전수):

| `db/<kind>/` | 파일 | 옛 가드가 본 것 |
|---|---|---|
| `migration` | 309 | 프로덕션(단, **중첩은 못 봄** — 아래 ③) |
| `migration-dev` | 13 | dev 시드 ✅ |
| `seed` | **8** | **없는 셈** ① |
| `migration-h2` | 11 | 없는 셈 (**정당** — 아래) |

③ **티켓이 몰랐던 세 번째 사각**: 옛 가드는 `kind` 를 파일의 **부모 디렉터리 이름**으로 읽었다
(`${dir##*/}`). fan·scm 8개 서비스는 프로덕션 마이그레이션을 한 단계 더 안에 둔다
(`db/migration/artist`, `db/migration/procurement` … — 각자의 `spring.flyway.locations` 가
그 하위 경로를 부른다). 그래서 `kind=artist`·`kind=procurement` 로 읽혀 `case *) continue` 로
**조용히 버려졌다 — 프로덕션 마이그레이션 21개가 어떤 천장에도 합산된 적이 없다.**
지금은 `db/` **직후** 세그먼트를 kind 로 읽는다.

`migration-h2` 는 **제외가 맞다**(추측 아님): ecommerce product-service 의
`application-local.yml` 이 `locations: classpath:db/migration-h2` 를 **단독으로** 지정해
`db/migration` 을 **대체**한다 ⇒ 두 위치가 한 시퀀스에 합쳐지지 않으므로 서로 out-of-order 가
될 수 없다. 근거를 스크립트의 `INERT_KINDS` 주석에 적었다.

**위반 목록(전수 8건)** — 확장한 가드가 실제로 출력한 것:

| 서비스 | production 최고 | dev 시드 | 초과폭 |
|---|---|---|---|
| master-service | **V8** | V99·V100·V101·V102·V103 | +91 ~ +95 |
| inbound-service | **V9** | V99 | +90 |
| inventory-service | **V6** | V99 | +93 |
| outbound-service | **V18** | V99 | +81 |

🔴 outbound 가 절벽에 가장 가까웠다 — 타임라인이 이미 V18 이고 `TASK-BE-586` 이 그 위에 더
얹으려는 참이었다. `V99` 인 채로 그 마이그레이션이 들어왔으면 **기존 데모 볼륨 전부가 기동
실패**했을 것이고, 그것이 정확히 admin 이 죽은 방식이다.

## 🔴 티켓의 Edge Case 하나가 **틀렸다** — 8개는 이미 멱등이었다

이 티켓은 *"wms 시드 8개는 현재 멱등이 아니다"* 라고 적었다. 세어 보니 **8/8 전부**
`ON CONFLICT … DO NOTHING` 이 걸려 있었다(INSERT 문 수 ≤ ON CONFLICT 수). admin 은
보강이 필요했지만 그 사실을 형제에게 **확인 없이 전이**시킨 것이다. 멱등화 작업은 하지 않았고,
대신 헤더에 *"repeatable 은 checksum 이 바뀔 때마다 살아 있는 데이터 위로 재실행된다"* 는
이유를 명시해 이후에 이 성질이 조용히 깨지지 않게 했다.

## AC-1 — 술어가 실제로 넓어졌다 (같은 트리, 가드만 교체)

**변환 전 트리**에서 두 버전을 각각 실행한 것이 증거다. 파일은 한 글자도 안 바꾼 상태다:

| 실행 | rc | 검사한 디렉터리 | 판정 |
|---|---|---|---|
| **옛 가드** | **0** | 3개 (iam 만) | `OK — 버전 있는 dev 시드 3개 전부 범위 안` |
| **새 가드** | **1** | **7개** | `✗` **8건** + `FAIL` |

⇒ 옛 초록은 "wms 가 안전하다" 가 아니라 "wms 를 안 봤다" 였다. 같은 파일, 다른 술어, 반대 판정.

**넓힘 자체가 다시 좁아지지 않도록** — 이 가드가 실패한 방식은 비교식의 버그가 아니라
**디렉터리 이름 목록이 트리와 어긋났는데 아무도 알아챌 수 없었던 것**이다. 그래서
`LOCATION INVENTORY` 를 넣었다: `src/main/resources/db/<kind>/` 를 전수 열거해 **분류가 없는
kind 가 나오면 실패**한다. 새 dev 시드 위치는 이제 조용히 들어올 수 없다.
0건 계측-실패 검사도 **위치별로** 쪼갰다 — 예전엔 `migration-dev` 만 셌기 때문에 `seed` 가
0건이어도 iam 3개가 그 검사를 통과시켰다. **집계된 non-zero 가 위치별 0 을 가린다.**

## AC-2 — 형제 4개 해소. 🔴 FK 순서가 이 AC 의 진짜 내용이었다

8개 전부 `R__` 로 전환(`git mv`, **내용·UUID 무변경**):

```
master  V99 →R__01_seed_dev_warehouse   V100→R__02_zones  V101→R__03_locations
        V102→R__04_skus                 V103→R__05_partners
inbound / inventory / outbound   V99__seed_dev_masterref → R__seed_dev_masterref
```

`01`~`05` 접두사는 장식이 아니다 — Flyway 는 repeatable 을 **description 사전순**으로 실행하므로
warehouse → zone → location 을 지키는 것이 저 숫자뿐이다(account-service 선례와 동형).
**말이 아니라 실행으로 확인했다**: 신규 IT 의 첫 칸이 신선 DB 에 실제 적용하고 1·3·3·3·3 행과
`locations ⋈ zones ⋈ warehouses = 3` 을 단언한다. 순서가 틀리면 데이터가 이상해지는 게 아니라
**FK 로 죽는다** — 그것이 판정 장치다.

가드: 변환 후 **rc=0**.

## AC-3 — 기존 볼륨. 🔴 "오늘 no-op" 을 초록으로 팔지 않았다

`ExistingSeedVolumeMigrationOrderIT`(master-service, `@Tag("integration")`, **3칸 전부 통과**):

| 칸 | 내용 | 결과 |
|---|---|---|
| 신선 볼륨 | R__01~05 FK 순서 + `version > 8` 인 적용 이력 **0건** | GREEN |
| **대조군** | V99~V103 이력 행 재구성 + **다음 프로덕션 마이그레이션(V9) 대기** → 슬림 off | `FlywayValidateException: not applied to database: 9` — **재현됨** |
| 슬림 on | 같은 상태에서 V9 적용 + 시드 행 **중복 없음**(1·3·3·3·3) | GREEN |

🔴 **정직하게 적는다: 오늘 기존 볼륨은 슬림 없이도 뜬다.** 99 아래에 대기 중인
마이그레이션이 아직 없기 때문이다. 그래서 대조군에 `db/futureproduction/V9__…`(테스트 전용
리소스)를 **주입**했다 — 그것 없이는 대조군이 *빈 질문*을 재고 통과했을 것이고, 그 초록은
아무것도 뜻하지 않는다. 오버레이의 `SPRING_FLYWAY_OUT_OF_ORDER` 4줄이 필요해지는 시점은
**다음 마이그레이션이 추가되는 순간**이고 그때는 이미 늦으므로 지금 넣었다. 일몰 조건
(`DELETE FROM flyway_schema_history WHERE version IN ('99'…'103')`)을 파일에 적었다.

🔵 `R__` 전환만으로 기존 DB 가 낫지 않는 이유는 `TASK-BE-585` 와 같다 — 결함은 파일이 아니라
**이력 행**에 있다. 없어진 versioned 는 `future` 로 조용히 용인되므로 전환 자체는 안전하다.

## AC-4 — bite 3종 (전부 주입 → RED → 복원 → GREEN)

| bite | 주입 | 결과 |
|---|---|---|
| A. **FK 순서** | `R__01` → `R__99` (마지막으로 밀기) | IT **3/3 FAILED**, 사유 확인: `ERROR: insert or update on table "zones" violates foreign key constraint "fk_zones…"` |
| B. **밴드 재도입** | `V99__seed_dev_warehouse.sql` 추가 | 가드 **rc=1**, `✗ master-service/db/seed/V99__…` |
| C. **미분류 위치** | `db/bootstrap/V1__x.sql` 신설 | 가드 **rc=1**, `✗ 분류되지 않은 마이그레이션 위치: db/bootstrap/` |

복원 후: 가드 **rc=0**, IT **3/3 통과**(`tests="3" skipped="0" failures="0" errors="0"`).
🔴 bite A 는 "빨개졌다" 로 끝내지 않고 **실패 사유가 FK 인지** 리포트에서 확인했다 — 다른 이유로
빨개진 RED 는 그 가드가 문 것이 아니다.

## 부수 정리 (이름이 바뀌면 이름을 가리키던 것도 바뀐다)

| 파일 | 조치 |
|---|---|
| `docs/adr/ADR-MONO-052` | `V99__seed_dev_warehouse.sql` **마크다운 링크가 깨졌다** → 새 경로 + 개명 사실. 표 행도 갱신 |
| `outbound-service/application-dev.yml` | 주석의 파일 포인터 갱신 + V18/V99 격차를 명시 |
| `infra/demo/seed/seed-wms.sh` | 서술은 **당시 이름 그대로 두고**(역사다) 개명 사실만 한 문단 추가 |
| `tasks/done/**` | **손대지 않음** — 완료 기록은 당시 사실이다 |

## 남은 것

- 🔴 **`TASK-BE-587` 이 존재하지 않는 `TASK-BE-588` 을 Out of Scope 로 가리키고 있었다**
  (형제 밴드 = 실제로는 이 티켓). 같은 커밋에서 참조를 고쳤다.
- `TASK-BE-587` — admin 시드의 **적용 범위**(프로덕션에 공개 UUID SUPERADMIN 이 심기는가).
  이 티켓은 **순서/배치**만 닫았고 범위는 건드리지 않았다.
