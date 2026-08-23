# Task ID

TASK-MONO-570

# Title

복합 `Service Type` 표기가 두 갈래로 갈라졌다 — 정경은 A형만 쓰는데 **13개 서비스가 B형**이다. 섞여 있는 동안 백틱 단위 파서는 한쪽 전건에 오탐을 낸다.

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- consistency

---

# Goal

`projects/*/specs/services/*/architecture.md` 의 복합 `Service Type` 표기를 **정경 한 가지**로 통일하고, 다시 갈라지면 CI 가 발화하도록 가드를 남긴다.

정책 변경이 아니다. `platform/service-types/INDEX.md` § Selection Rules 1 이 이미 표기를 정해 두었는데 13개 파일이 다른 모양으로 쓰고 있는 **드리프트 교정**이다.

---

# 배경 — 실측 (2026-08-23 UTC, `/validate-rules` 산물)

## ① 두 표기가 공존한다 — 49개 전수

| 표기 | 건수 | 예 |
|---|---:|---|
| **A형** — 한 백틱 안에 `+` | **10** | `` `rest-api + event-consumer` `` |
| **B형** — 백틱을 나눠 씀 | **13** | `` `rest-api` `` + `` `event-consumer` `` |
| 단일 타입 (해당 없음) | 26 | `` `frontend-app` `` |

- **A형 10건**: ecommerce `auth/order/payment/promotion/search/settlement/shipping/user` · finance `ledger` · iam `account`
- **B형 13건**: ecommerce `notification/product` · erp `notification/read-model` · fan `notification` · iam `security` · scm `demand-planning/inventory-visibility/logistics` · wms `admin/inbound/inventory/outbound`

## ② 정경은 A형이다 — 다수결이 아니다

`platform/service-types/INDEX.md` 안의 복합 표기 예시 **4개가 전부 A형**이고 B형은 **0개**다:

```
`<primary> + <secondary>[ + <secondary> …]`
`event-consumer + batch-job + rest-api`
`+ <secondary>`
`+ event-consumer (CQRS read model, BE-046)`
```

`CLAUDE.md` § Source of Truth Priority 상 **`platform/`(5층) > `<project>/specs/services/`(7층)** 이므로 **이탈한 쪽은 B형 13건**이다.

🔴 **이 판정을 다수결로 하면 거꾸로 간다.** 파일 수는 B형이 13 대 10 으로 많아서, 세어 보고 고르면 정경을 프로젝트 스펙에 맞추는 셈이 된다. 우선순위는 개수가 아니라 층이다.

## ③ 왜 지금 고치나 — 섞여 있으면 파서가 한쪽에 전건 오탐

`Service Type` 값을 읽는 자연스러운 추출식은 백틱 span 단위다:

```js
[...line.matchAll(/`([a-z0-9-]+)`/g)]     // 식별자 한 개짜리 span 을 가정
```

이 식은 **A형 10건에서 0매칭**을 낸다(`rest-api + event-consumer` 는 `[a-z0-9-]+` 에 안 맞는다).
2026-08-23 `/validate-rules` 실행에서 실제로 **A형 10건이 전부 "Service Type 값이 백틱으로
표기되지 않음" Critical 로 보고**됐고, 술어를 고친 뒤에야 49/49 통과로 뒤집혔다.

⇒ **표기가 하나면 추출식도 하나다.** 지금은 어느 쪽으로 짜도 상대편이 통째로 오탐이 된다.

---

# Scope

## In Scope

1. **B형 13개 `architecture.md`** 의 `Service Type` 행을 A형으로 정규화. **값·순서·괄호 주석은 그대로 두고 백틱 위치만** 바꾼다.
2. `platform/service-types/INDEX.md` § Selection Rules 1 에 **표기를 명문화**한다 — 지금은 예시로만 A형이고 *"한 백틱 안에 쓴다"* 라는 문장이 없어서, 다음 사람이 B형을 써도 규칙을 어긴 것이 아니다.
3. **가드** `scripts/check-service-type-notation.sh` + `--self-test` + CI 배선.
4. `tasks/INDEX.md` 행 이동.

## Out of Scope

- **A형 10건** — 이미 정경대로다. 손대지 않는다.
- **단일 타입 26건** — `+` 가 없으므로 이 축과 무관하다.
- **primary/secondary 순서 변경** — 어느 타입이 primary 인지는 이 티켓이 판단할 사안이 아니다(INDEX 규칙 2 상 재분류는 ADR 사안). **표기만 바꾸고 순서는 보존한다.**
- **`Service Type Composition` 절 본문** — 각 서비스가 자기 복합 사유를 적어 둔 산문이고 표기 축이 아니다.
- **새 service type 추가/삭제** — 카탈로그는 8/8 일치 확인됨(같은 스캔).

---

# Acceptance Criteria

## AC-0 — 착수 시점 재측정 (verify-then-act)

```bash
node -e "
const fs=require('fs'),cp=require('child_process');
const a=cp.execSync('git ls-files -- projects/*/specs/services/*/architecture.md',{encoding:'utf8'}).split('\n').filter(Boolean);
let A=0,B=0,S=0;
for(const f of a){const l=fs.readFileSync(f,'utf8').split('\n').find(x=>/Service Type/i.test(x)&&/\|/.test(x));
 const sp=[...l.matchAll(/\`([^\`]+)\`/g)].map(m=>m[1]);
 if(sp.length===1&&sp[0].includes('+'))A++; else if(sp.length>1)B++; else S++;}
console.log('A형',A,'B형',B,'단일',S,'합계',a.length);"
```

- **합계가 49 가 아니면** 서비스가 늘거나 줄어든 것이므로 위 목록을 다시 만든다.
- **B형이 0 이면** 누가 이미 고친 것 → STOP, 티켓을 닫는다.
- 🔴 **A형/B형 비율이 뒤집혀 있어도 정경(§②)은 안 바뀐다.** 개수로 판단하지 않는다.

## AC-1 — 13개 파일 정규화, 값은 불변

- B형 13건이 전부 A형이 된다.
- 🔴 **값·순서·괄호 주석 보존을 기계로 단언한다** — 변경 전후로 각 파일에서 추출한
  **타입 토큰 시퀀스가 동일**해야 한다(`['event-consumer','batch-job','rest-api']` 순서까지).
  백틱만 옮기는 변경이므로 이 시퀀스가 달라지면 그건 재분류이고 범위 밖이다.
- `git diff --stat` = 13 files, 각 파일 1 insertion / 1 deletion.

## AC-2 — 정경에 표기를 명문화

`platform/service-types/INDEX.md` § Selection Rules 1 이 **문장으로** 다음을 말한다:

- 복합 타입은 **하나의 백틱 span 안에** `<primary> + <secondary>` 로 적는다.
- 괄호 주석은 **백틱 밖**에 둔다(`` `rest-api + event-consumer` (CQRS read model, BE-046) ``).
- 🔴 **예시만으로는 규칙이 아니다** — 지금 상태가 정확히 그래서 갈라졌다. 예시 4개가 전부
  A형인데 문장이 없어서 B형 13건이 규칙을 어긴 것도 아닌 채로 자랐다.

## AC-3 — 가드: 다시 갈라지면 발화한다

`scripts/check-service-type-notation.sh`:

- **모집단을 트리에서 유도**한다 — `git ls-files 'projects/*/specs/services/*/architecture.md'`.
  🔴 **파일 목록을 하드코딩하지 않는다**(새 서비스가 조용히 통과한다 — 이 저장소가 반복해서 밟은 모양).
- **모집단 하한 단언**: 추출된 `architecture.md` 가 **40개 미만이면 실패**. 🔴 추출 0건은 통과가
  아니라 **판정 불가**다.
- **술어**: `Service Type` 행의 백틱 span 이 2개 이상이면서 그중 하나가 카탈로그 값이면 **B형 → 실패**.
  단일 span(A형·단일타입)만 통과.
- **`--self-test`** 3케이스, 각각 **주입이 실제로 일어났는지 먼저 단언**:
  (a) B형을 주입하면 **문다** (b) 현재 트리는 통과 (c) **3항 복합**(`a + b + c`)도 A형이면 통과.

## AC-4 — 배선: 가드가 실제로 CI 에서 돈다

형제 가드(`check-index-queue-drift.sh` 등)와 동일한 3스텝(`bash -n` → `--self-test` → 본 실행)
+ `changes` 잡 paths-filter 에 `projects/*/specs/**` 와 스크립트 경로 엔트리.

🔴 **PR 의 실제 체크 목록에서 이 스텝이 *돌았음* 을 확인한다** — 워크플로에 적혀 있는 것과 돈 것은
다르다. 🔴 그리고 **게이트 블록 안에 갇히지 않았는지** 본다(이 저장소는 가드를 `--live` 전용
블록에 넣어 CI 8/8 초록인데 한 번도 안 돈 전례가 있다).

---

# Related Specs

- `platform/service-types/INDEX.md` § Catalog · § Selection Rules — 정경 (변경 대상)
- `platform/entrypoint.md` — primary 하나만 읽는다는 규칙 (변경 없음, 참조만)
- `CLAUDE.md` § Source of Truth Priority — platform(5층) > project specs(7층) 판정 근거
- `scripts/check-index-queue-drift.sh` — 가드 형태·`--self-test`·배선의 참조 구현

# Related Contracts

없음. 스펙 표기 규약이고 서비스 간 계약을 건드리지 않는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 3항 복합 (`event-consumer + batch-job + rest-api`, scm/demand-planning) | A형 한 span 안에 `+` 두 개. AC-3 `--self-test` (c) 가 이 케이스를 명시적으로 통과시킨다. |
| 괄호 주석이 타입마다 붙어 있다 (`` `rest-api` `` + `` `event-consumer` (CQRS…) ``) | 타입은 span 안으로 모으고 주석은 **span 밖 뒤쪽**에 이어 붙인다. 주석 문구 자체는 보존. |
| 새 서비스가 B형으로 추가된다 | AC-3 가드가 모집단을 트리에서 유도하므로 **자동 포함**. 이것이 가드를 남기는 이유다. |
| 어떤 파일의 primary 가 실은 틀렸다 | **이 티켓 밖**이다. 표기만 바꾸고, 발견하면 별건으로 적는다(재분류는 INDEX 규칙 2 상 ADR 사안). |
| `Service Type` 행이 표가 아닌 산문으로 적힌 서비스가 나온다 | 현재 49/49 가 표 행이다(실측). 나오면 가드가 **판정 불가**로 실패해야지 조용히 통과하면 안 된다. |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 다수결로 B형을 정경으로 골랐다 | 정경 파일을 프로젝트 스펙에 맞춰 고치게 됨 | AC-0 이 *"비율이 뒤집혀도 정경은 안 바뀐다"* 를 명시한다. 판정은 **SoT 층**이지 개수가 아니다. |
| 백틱만 옮긴다면서 순서가 바뀌었다 | primary 가 뒤바뀐 채 머지 | AC-1 이 **토큰 시퀀스 동일**을 기계로 단언한다. 순서 변경 = 재분류 = 범위 밖. |
| 가드가 안 문다 | `--self-test` (a) 가 초록 | **주입이 실제로 됐는지부터 단언한다.** "안 물었다" 보다 "주입됐나" 가 먼저다. |
| 가드가 건강한 상태에 빨간불 | 단일 타입 26건이 걸림 | 술어를 **span 2개 이상 + 카탈로그 값**으로 좁힌다. 기대를 낮춰 통과시키지 않는다. |
| 가드가 CI 에서 안 돈다 | 체크 목록에 스텝 없음 / 게이트 블록 안 | AC-4 가 이것만 본다. 잡 로그에서 실행 줄을 **직접 확인**한다. |
| 파서가 죽어 조용히 통과 | B형을 넣어도 초록 | AC-3 의 **모집단 하한 40** 이 이것을 판정 불가로 만든다. |

---

분석=**Opus 5** / 구현 권장=**Sonnet** — 13개 파일 1줄 편집은 단순하지만 AC-3 가드(모집단 유도 + self-test + 배선)는 Haiku 로 부족하다.
