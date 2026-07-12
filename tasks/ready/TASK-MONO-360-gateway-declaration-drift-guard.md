# Task ID

TASK-MONO-360

# Title

게이트웨이 **선언 ↔ 모듈 ↔ 노출 표면** 드리프트 가드 — `TASK-MONO-347` AC-3 승계. **357 은 드리프트의 인스턴스를 닫았고, 재발 경로는 열려 있다.**

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- guard
- architecture
- docs

---

# Dependency Markers

- **선행 (머지됨)**: [`TASK-MONO-357`](../done/TASK-MONO-357-finance-erp-gateways.md) (PR #2431 `7296d1ac6`) — finance/erp 게이트웨이 신설. 이 task 는 **그것의 재발 방지**다.
- **승계 원본**: [`TASK-MONO-347`](../done/TASK-MONO-347-gateway-policy-finance-erp-drift.md) **AC-3**. 347 은 방향 A 로 닫혔으나 AC-3(가드)은 **미충족인 채 닫혔고**, 그 사실이 347 § RESOLUTION 에 명시돼 있다.
- **참조 (같은 계열)**: `TASK-MONO-345`(service-map 가드 — 확장 후보) · `TASK-MONO-339` · `TASK-MONO-341` · `TASK-MONO-352` · **`TASK-MONO-359`(가드는 *무는가* 만이 아니라 *물 기회를 얻는가* 도 증명해야 한다 — 이 task 의 AC-4 가 그것)**.

---

# Goal

**347 이 발견한 드리프트는 지금도 같은 방식으로 재발할 수 있다.** 357 이 고친 것은 그 드리프트의 **현재 인스턴스**(finance/erp 에 게이트웨이가 없었다)이지, **그것이 아무에게도 들키지 않은 채 몇 달을 버틴 메커니즘**이 아니다.

메커니즘은 이렇다 — 게이트웨이에 관한 **사람이 손으로 유지하는 선언**이 세 곳에 있고(`platform/api-gateway-policy.md` · `projects/<p>/PROJECT.md` · `projects/<p>/docker-compose.yml` 의 Traefik 라벨), **빌드가 읽는 진실은 `settings.gradle` 하나**인데, **둘을 대조하는 것은 아무것도 없다.**

`MONO-345` 의 `scripts/check-service-map-drift.sh` 가 있지만 **`settings.gradle` ↔ `docs/project-overview.md` 만** 본다. `PROJECT.md` 도, `api-gateway-policy.md` 도, **compose 의 Traefik 라벨도 읽지 않는다.**

---

# 왜 이게 진짜인지 — 347 의 원 진단이 틀렸던 지점

347 은 finance/erp 를 두고 *"런타임 외부 표면 0 — console-bff 내부 호출 전용이라 위험이 미발현"* 이라 적었다. **틀렸다.** 357 의 착수 전 실측에서 두 프로젝트의 `docker-compose.yml` 은 이미:

- **Traefik 이 `finance.local` → `account-service` 로 직결**하고,
- **`erp.local` 을 `PathPrefix` 로 백엔드 4개에 분배**하고 있었다.

즉 **리버스 프록시가 게이트웨이의 라우팅 일을 하면서 검증(JWT · 신원헤더 strip→enrich · rate limit · 에러 envelope)은 하나도 하지 않는** 상태였다. `platform/api-gateway-policy.md` L13(*"All external traffic MUST pass through the gateway"*) · L14(*"No backend service may be directly exposed"*)는 **미발현이 아니라 이미 위반돼 있었고, 아무것도 실패하지 않았다.**

**347 조차 그것을 놓쳤다** — 코드 호출 경로(BFF → 서비스)만 보고 "외부 노출 없음" 을 판정했기 때문이다. 로컬 네트워크 컨벤션에서 **외부 표면을 결정하는 것은 Traefik 라벨**이다. 사람이 두 번(원 발굴 · 백로그 재검토) 들여다보고도 놓친 축이라면, 그건 **사람 눈으로 지킬 표면이 아니다.**

---

# Scope

## 착수 전 실측 (2026-07-12, `7296d1ac6` — 재검증할 것)

`projects/*/docker-compose.yml` 의 Traefik `Host()` 라우터 보유 현황:

| 프로젝트 | Host 라우터 보유 서비스 | 게이트웨이 모듈 |
|---|---|---|
| ecommerce | `gateway-service`(`ecommerce.local`) · `web-store`(`web.ecommerce.local`) | ✅ |
| erp | `gateway-service`(`erp.local`) | ✅ (357 신설) |
| finance | `gateway-service`(`finance.local`) | ✅ (357 신설) |
| fan | `gateway-service`(`fan-platform.local`) | ✅ |
| scm | `gateway-service`(`scm.local`) | ✅ |
| platform-console | `console-web`(`console.local`) · `console-bff`(`console-bff.local`) | ❌ (게이트웨이 없음 — 설계) |
| **wms** | `kafka-ui` · `grafana` **만** | ✅ |
| **iam** | `kafka-ui` · `grafana` **만** | ✅ |

**⚠️ 오탐 주의 — wms/iam 의 `docker-compose.yml` 은 인프라 전용이다.** postgres/redis/kafka/observability 만 정의하고 **앱 서비스를 하나도 정의하지 않는다**(파일 헤더가 *"gateway-service is reached via http://wms.local/"* 라 적어두고 정작 그 서비스는 이 파일에 없다 — 앱은 fed-e2e compose 나 IDE/gradle 로 뜬다). **"게이트웨이가 프로젝트 호스트명을 보유한다" 를 무조건 요구하면 wms/iam 이 즉시 RED 가 된다.** 가드는 **파일 안에 앱 서비스가 있을 때만** 발화해야 한다.

## 기계화 가능한 축 (그리고 아닌 축)

- ✅ **`PROJECT.md` ↔ `settings.gradle`** — 둘 다 구조화돼 있다.
- ✅ **compose Traefik 라벨 ↔ 게이트웨이 모듈** — 둘 다 구조화돼 있다.
- ❌ **`api-gateway-policy.md` 의 산문(L3/L13/L14)** — *"Every project that exposes HTTP traffic…"* 는 **전칭 명제**이지 프로젝트 목록이 아니다. 산문을 diff 할 수는 없다. **이 축은 위 두 축을 통해 간접적으로만 지켜진다** — 그 한계를 스크립트 주석에 명시할 것(가드가 정책 전문을 지킨다고 **과잉 주장하지 말 것**).

## 구현

**불변식 2개.** 기존 `scripts/check-service-map-drift.sh` 확장 vs 신규 스크립트는 구현자 판단 — 345 는 `settings.gradle`↔`project-overview.md` 라는 **단일 대조축**에 맞춰 설계됐고, I2 는 **데이터 소스가 다르다**(YAML). 확장이 그 스크립트의 "deliberate limits" 주석을 흐린다면 분리가 낫다.

- **I1 — 선언 ↔ 모듈**: 각 프로젝트에 대해, `PROJECT.md` 가 `gateway-service` 를 **v1 범위로 선언**하면(Service Map v1 표 행 **또는** "v1 IN" 산문) `settings.gradle` 에 `projects:<p>:apps:gateway-service` 가 **있어야** 하고, 그 역도 성립해야 한다. **347 이 걸려 넘어진 바로 그 축이다** — 두 `PROJECT.md` 가 v1 IN 이라 적어둔 채 모듈이 없었고, 아무것도 실패하지 않았다.
- **I2 — 노출 표면 (정책 L14)**: **게이트웨이 모듈을 가진 프로젝트**의 `docker-compose.yml` 안에서, **앱 서비스**가 Traefik `Host()` 라우터를 보유한다면 그것은 **게이트웨이여야** 한다. 명시 allowlist: 프런트엔드(`web-store` · `console-web`) · 운영 도구(`kafka-ui` · `grafana`). **allowlist 는 파일 상단에 이유와 함께 열거할 것 — 정규식으로 뭉뚱그리면 다음 백엔드가 조용히 통과한다.**

## CI 배선

- `dorny/paths-filter` **pure-positive** (negation 금지 — MONO-074/075 quirk).
- **`code-changed` 와 AND 하지 말 것.** 이 드리프트는 **markdown-only 편집으로 도착한다**(`PROJECT.md` 한 줄). AND 하면 **존재 이유인 바로 그 변경에서 가드가 꺼진다** — MONO-345 가 같은 함정을 주석으로 못박아 뒀고(ci.yml L165-169), MONO-359 는 그 반대편(도달 불가) 사례다.
- 필터 경로: `projects/*/PROJECT.md` · `settings.gradle` · `projects/*/docker-compose.yml` · `platform/api-gateway-policy.md` · 스크립트 자신.
- **`nightly-e2e.yml` 갱신 불필요** (CI 전용 가드, e2e 서비스 추가 아님) — 확인 후 진행.

---

# Acceptance Criteria

- [ ] **AC-1 — I1 가드**: `PROJECT.md` 게이트웨이 선언 ↔ `settings.gradle` 모듈, **양방향**. 현 트리(`7296d1ac6`)에서 **GREEN**(8프로젝트 전부 정합).
- [ ] **AC-2 — I2 가드**: 게이트웨이 보유 프로젝트의 compose 에서 비-게이트웨이 앱 서비스가 `Host()` 라우터를 들면 RED. **wms/iam(인프라 전용 compose)에서 오탐 없음** — 이걸 못 지키면 가드가 첫날부터 RED 이고, 그러면 사람은 가드를 끈다.
- [ ] **AC-3 — mutation 4방향, 전부 물어야 함**:
  1. **유령 선언**: `platform-console/PROJECT.md` 에 `gateway-service` v1 IN 을 주입 → **RED**.
  2. **모듈 증발**: `settings.gradle` 에서 `projects:finance-platform:apps:gateway-service` 제거(PROJECT.md 는 그대로) → **RED**.
  3. **노출 회귀**: finance `account-service` 에 `Host(\`finance.local\`)` 라우터 라벨 재부착 → **RED**. **이것이 몇 달간 라이브였던 바로 그 상태다.**
  4. **allowlist 우회**: `web-store` 가 아닌 새 백엔드에 프런트처럼 보이는 이름(`*-web`)을 주고 라벨 부착 → **RED**(allowlist 가 정규식이면 여기서 샌다).
- [ ] **AC-4 — 도달 가능성 (MONO-359 의 교훈)**: `PROJECT.md` **한 줄만 고친 markdown-only PR** 에서 이 잡이 **실제로 실행되는지** 확인한다. **skip 은 초록으로 보고된다** — 물 수 있는지와 **물 기회를 얻는지는 별개**이며, 359 는 후자만으로 가드 하나가 무력했음을 보였다. `code-changed` AND 금지가 이 AC 의 실질이다.
- [ ] **AC-5 — 과잉 주장 금지**: 스크립트 주석이 **무엇을 지키지 못하는지** 명시한다 — `api-gateway-policy.md` 의 전칭 산문은 대조 대상이 아니며, 새 프로젝트가 `PROJECT.md` 에 게이트웨이를 **아예 안 적고** 백엔드를 직접 노출하면 I1 은 침묵한다(I2 는 게이트웨이 모듈이 없으므로 발화하지 않는다). **이 구멍을 아는 채로 남기는 것과 모르는 채로 남기는 것은 다르다.**
- [ ] **AC-6** — `./gradlew check` 무영향(스크립트/워크플로 전용) · 로컬 `bash scripts/<guard>.sh` GREEN · `bash -n` 통과.

---

# Related Specs

- [`platform/api-gateway-policy.md`](../../platform/api-gateway-policy.md) — L3 / L13 / L14 (가드가 간접적으로 지키려는 정책) · L86 (인증만, 인가 아님 — **가드가 흐리면 안 되는 구분**)
- [`TEMPLATE.md` § Local Network Convention](../../TEMPLATE.md) — Traefik 호스트명 = **외부 표면의 정의**. 이 문서가 I2 의 근거다.
- [`docs/adr/ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — D7(게이트웨이 라이브러리) · 347 direction A 의 근거
- `scripts/check-service-map-drift.sh` (MONO-345) — 확장 후보. **그 "deliberate limits" 주석을 먼저 읽을 것.**
- `.github/workflows/ci.yml` L165-169 (MONO-345 의 `code-changed` 비-AND 주석) · L319-343 (`service-map-drift` 잡)

# Related Contracts

없음 — CI 가드. 런타임 표면 불변.

---

# Target Service

없음(공유 `scripts/` + `.github/workflows/`) · 대상은 8개 프로젝트의 선언 파일.

---

# Edge Cases

- **wms/iam 인프라 전용 compose** — § 착수 전 실측의 ⚠️. **첫날부터 RED 인 가드는 꺼진다.**
- **`platform-console` 은 게이트웨이가 없다(설계)** — `console-bff` 가 `console-bff.local` 을 직접 보유한다. I2 는 **게이트웨이 보유 프로젝트에만** 적용되므로 발화하지 않아야 한다. 이 프로젝트를 I2 대상에 넣으면 **정책이 명시적으로 허용한 구조를 위반이라 부르게 된다.**
- **allowlist 를 정규식으로 뭉뚱그리는 유혹** — AC-3-4 가 이걸 잡는다. `web-store`/`console-web` 은 **이름으로 열거**하고 이유를 적을 것.
- **`PROJECT.md` 의 "v1 IN" 표기 흔들림** — 산문 문장과 Service Map 표, 두 곳에 있다(347 § Edge Cases 가 *"행만 지우고 문장을 남기면 드리프트가 절반만 해소된다"* 고 경고했다). 파서가 **둘 다** 보게 하거나, 한쪽만 본다면 **왜 그쪽인지** 주석에 적을 것.

# Failure Scenarios

- **가드를 `code-changed` 와 AND 한다** → markdown-only 편집에서 꺼지고, **그게 정확히 이 드리프트의 도착 경로다.** Guard: AC-4. (MONO-345 가 주석으로 남긴 함정 · MONO-359 가 실증한 실패.)
- **첫날부터 RED (wms/iam 오탐)** → 사람이 가드를 끄거나 `continue-on-error` 를 붙인다. **꺼진 가드는 없는 가드보다 나쁘다 — 초록으로 보고되니까.** Guard: AC-2.
- **가드가 정책 전문을 지킨다고 과잉 주장** → 다음 사람이 "게이트웨이는 CI 가 지킨다" 고 믿고 새 프로젝트를 게이트웨이 없이 노출한다. Guard: AC-5.
- **mutation 이 적용 안 되거나 컴파일/파싱 실패한 것을 "가드가 안 문다" 로 오독** → 357 에서 실제로 겪었다(perl 이 `@Component` 를 두 번 붙여 컴파일 에러 → 그건 가드가 문 게 아니라 mutation 이 무효인 것). **mutation 은 적용됐는지 먼저 확인할 것.**

---

# Provenance

`TASK-MONO-347` AC-3 승계 (2026-07-12). 347 을 direction A 로 닫으면서, **그 AC-3(재발 방지 가드)만 미충족이라는 사실을 조용히 떨어뜨리지 않기 위해** 분리 발행한다. 347 의 close 노트와 INDEX 가 이 승계를 명시한다.

**이 task 의 값어치는 "가드 하나 추가" 가 아니다.** 347 이 지적한 드리프트는 **사람이 두 번 들여다보고도(원 발굴 · 백로그 재검토) 노출 축을 놓친** 종류다 — 347 자신이 "외부 표면 0" 이라 오진했고, 그 오진 위에서 "위험 미발현" 이라는 안심이 세워졌다. 그런 표면은 사람이 지킬 수 없다.

분석=Opus 4.8 / 구현 권장=Sonnet (기계적 CI 가드 — 단, **AC-3/AC-4 의 mutation·도달성 검증을 생략하면 이 task 는 무의미하다**. 359 가 정확히 그 이유로 존재했다).
</content>
</invoke>
