# Task ID

TASK-MONO-531

# Title

dev-seed 밴드 가드가 **wms 가 쓰는 디렉터리를 보지 못한다** — 같은 결함이 형제 4개에 그대로 남아 있다

# Status

ready

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

- [ ] **AC-0 (실측)** — dev 시드 디렉터리 전수 + 서비스별 프로덕션 최고 버전 + 위반 목록.
      🔴 세지 않고 시작하면 낙오가 남는다(이 가드가 처음 놓친 이유가 정확히 그것이다)
- [ ] **AC-1 (가드 확장)** — `db/seed/` 포함. **확장 전 가드로 위반이 안 잡히고 확장 후 잡히는 것**을
      실행으로 보인다(술어가 실제로 넓어졌다는 증거)
- [ ] **AC-2 (형제 해소)** — 위반 0건. 🔴 master 의 FK 순서가 R__ 사전순으로도 유지되는지
      **실제로 적용해서** 확인한다(순서가 틀리면 FK 위반으로 죽는다 — 이것이 이 AC 의 진짜 위험이다)
- [ ] **AC-3 (기존 볼륨 재현)** — V99~V103 이 적용된 상태를 재현하고 그 위에서 기동한다.
      🔴 신선 볼륨 테스트는 이 결함 계열을 **구조적으로** 볼 수 없다 —
      `TASK-BE-585` 의 `ExistingVolumeMigrationOrderIT` 가 쓸 수 있는 형틀이다
- [ ] **AC-4 (bite)** — 가드가 무는지: 위반을 하나 되돌리면 RED, 복원하면 GREEN

# Related Specs

- `scripts/check-dev-seed-migration-band.sh` — 확장 대상
- `tasks/done/TASK-MONO-524-*.md` — 이 가드의 출처. `R__` 전환 결정과 그 근거
- `projects/iam-platform/docs/flyway-dev-seed-migrations.md` § 6 — 기존 DB 복구 절차
- `projects/wms-platform/tasks/ready/TASK-BE-585-*.md` — 이 티켓의 출처(admin 을 고치며 사각 발견)

# Related Contracts

- 없음(빌드/가드 표면)

# Edge Cases

- 🔴 **이미 적용된 `R__` 는 rename 하면 validate 가 실패한다**(`TASK-MONO-524` § finding 2) —
  전환 시 파일명을 처음부터 최종형으로 정할 것
- 🔴 R__ 는 **매 checksum 변경마다 재실행**된다 ⇒ 전 문장이 멱등이어야 한다(`ON CONFLICT DO NOTHING`).
  wms 시드 8개는 현재 멱등이 아니다(admin 도 `TASK-BE-585` 에서 보강이 필요했다)
- 없어진 **versioned** 마이그레이션은 `future` 로 조용히 용인되지만 없어진 **repeatable** 은 아니다 —
  이 비대칭이 전환 순서를 정한다

# Failure Scenarios

- **가드만 넓히고 형제를 안 고친다** → CI 가 빨개진다. 둘은 같은 PR 이어야 한다
- **`*seed*` 이름으로 판정** → 오탐 ~30건(실측). § Out of Scope 가 막는다
- **신선 볼륨에서 검증하고 닫는다** → 이 결함 계열은 거기서 영원히 초록이다
- **master 를 사전순 고려 없이 R__ 로 바꾼다** → FK 위반으로 데모가 기동 실패

# Definition of Done

- [ ] AC-0 ~ AC-4 전부
- [ ] 가드 초록이 "안전하다" 를 의미한다
- [ ] Ready for review
