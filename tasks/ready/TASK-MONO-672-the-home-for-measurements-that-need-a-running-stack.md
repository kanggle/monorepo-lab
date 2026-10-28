# Task ID

TASK-MONO-672

# Status

ready (2026-09-24 UTC — **14차 AMI 창**(재굽기 `ami-03789993d93a320c1` · 인스턴스 교체 = 신선 볼륨 · 약 15분 · 1453→1468/1800 ⇒ 잔여 332분). 항목 **13 PASS**(셋 다) · 항목 **1 라이브 PASS** · 항목 **2② PASS**(API) · 항목 **4① PASS** · 항목 **5 의 가용재고 한 줄** 기록. 남은 것: **2③ ⚪ · 4② · 5②③ · 9의 사슬** — § 14차 AMI 창 수확. ‖ 이전: 2026-09-23 UTC — **16차 창**(소유자가 켜 둔 창에 얹음, **약 5분** 소모 · 1353/1800 ⇒ 잔여 447분). 항목 **11 PASS**(719 의 힌트가 대조군 둘과 함께 뜬다) · 항목 **12 FAIL**(717 의 프로비저닝은 게이트웨이 403·직접경로 무網 둘 다 막힘 → `TASK-MONO-721` 기안). 🔵 **15차의 「재굽기 선행」은 과했다** — 시드·compose·demo.env 는 **클론**이라 SSM 패치면 되고, 이미지 안인 것은 Flyway 마이그레이션뿐이다. 남은 항목 **1 · 2②③ · 4 · 5 · 9의 사슬** 은 **신선 볼륨/선행 티켓** 대기이지 굽기 대기가 아니다)

# Title

⏳ **스택이 떠야만 잴 수 있는 것들의 집** — `TASK-MONO-645` 가 닫히면서 그 역할을 넘겨받는다

# Owner

monorepo

# Task Tags

- demo
- verification
- scheduled

---

# 🔴🔴 이 티켓은 «빈 집» 이다 — 지금은 들고 있는 것이 없다

`TASK-MONO-645` 는 *"닫히면서 집을 잃는 것들"* 의 집이었다. 그 티켓의 § Goal 이 이렇게 적었다:

> 🔵 **그래서 이 집은 자란다.** … 🔴 다음 사람도 같은 부류를 만나면 **여기에 붙여라.**
> 창은 한 번만 열린다.

🔴 그런데 645 는 **자기 AC 를 전부 닫았다.** 닫으면 `done/`(frozen)이고, 그러면 **그 집이
사라진다** — 다음에 누가 «스택이 떠야 잴 수 있는» 항목을 남기면 **갈 곳이 없다.**

🔵 소유자 결정(2026-09-11): **「닫는다 + 후속 집 기안」.** 이 티켓이 그 후속 집이다.

## 🔴 그러므로 «할 일이 없다» 가 정상 상태다

이 티켓은 **지금 아무 측정도 안 들고 있다.** 그것이 결함이 아니라 설계다 —
**집이 먼저 있어야 다음 항목이 갈 곳이 생긴다.** 645 가 겪은 것이 그 반대였다:
`TASK-MONO-656` 이 *"다음에 데모가 켜지는 창에 얹어라"* 라고 적고 `review/` 로 갔는데,
얹을 곳을 이름으로 지목하지 않아 **그 의무가 아무 큐에도 없이 사라질 뻔했다**(645 AC-6).

---

# 넘겨받은 항목

> 🔵 위 «빈 집» 절은 기안 당시의 상태다. 아래가 지금 들고 있는 것이다. 항목마다 **출처 티켓**과
> **창만으로 풀리는가**를 적는다(Scope § 제외 · Edge Cases).

## ✅ 항목 1 (닫힘 2026-09-22 — 판정 FAIL + 시드 고침) — `TASK-MONO-679`: `seed-fan.sh` 가 실제로 **사진 달린 공개 글**을 발행하는가 (2026-09-15 수령)

- **무엇을 재나**: 데모 스택에서 로그인해 팔로우 피드(`/`)와 DB 글 상세를 열었을 때 공개 글 카드에 사진이 그려지는가.
  API 로는 `GET /api/v1/community/feed` 항목의 `mediaRefs` 가 비어 있지 않은가(PUBLIC 글) · 잠긴 항목은 `[]` 인가.
- 🔴🔴 **창만으로는 안 풀린다 — AMI 재굽기가 필요하다.** `publish_artist_post` 는 **제목으로** «이미 있음» 을 판단하고 발행을 건너뛴다.
  구워진 AMI 의 DB 에는 사진 없는 글이 이미 있으므로, 재굽기 전 창에서는 **사진이 없는 것이 정상**이다(결함 아님).
  재굽기(신선 볼륨) 뒤의 창에서만 판정이 난다.
- **함께 볼 것**: 노아의 잠긴 글(«멤버십 전용 — 다음 EP 트랙 리스트 초안»)이 새로 생겼는가 — 679 가 시드에 추가했다.
- 출처: `tasks/done/TASK-MONO-679-…` § Verification ⚪2 · § CORRECTION.

## 🟡 항목 2 (① 닫힘 2026-09-22 — PASS · ② PASS 2026-09-24(API) · ③ ⚪ 술어 미정) — `TASK-MONO-679` AC-0: 실제 로그인으로 **정정의 전제**를 재현한다 (2026-09-15 수령)

- **무엇을 재나** (세 칸): ① 공개 카드 → `/posts/<샘플 id>` 가 로그인 상태에서도 **사진 있는 공개 판**인가(게이트웨이 404 → 폴백)
  ② 팔로우 피드의 DB 글 → 회원 상세가 사진을 **싣는가**(679 이후 — 항목 1 과 같은 재굽기 조건) ③ 홈의 팔로우 피드와 공개 피드 제목이
  **같은 글 목록**을 말하는가(679 가 두 시드를 정렬했다).
- 🔵 ①은 **창만으로 풀린다**(재굽기 불필요). ②③은 항목 1 과 같이 **재굽기 뒤**.
- 출처: `tasks/done/TASK-MONO-679-…` § AC-0 · § Verification ⚪3.

## ✅ 항목 3 (닫힘 2026-09-18) — `TASK-MONO-685` AC-7 (a): 꺼진 인스턴스에 온 **첫 요청**이 선택을 그 묶음 하나로 **교체**하는가 (2026-09-15 수령)

- **무엇을 재나**: 인스턴스가 `stopped` 인 상태에서 누군가(소유자·방문자) 론처 카드 **하나**의 「데모 서버 켜기」를 누른 뒤,
  `GET <제어 API>/bundles` 의 `selection` 이 **그 묶음 하나**인가(예: `["fan"]`). 🔴 지난 세션의 묶음이 남아 있으면 FAIL.
- 🔵 **창만으로 풀린다 — 그리고 창을 일부러 열 필요도 없다.** Lambda 는 2026-09-15 11:30Z `terraform apply` 로 이미 배포됐다
  (AMI 재굽기 불필요). 판정은 **읽기 전용 GET 한 번**이고, 필요한 사건은 «꺼진 상태의 첫 클릭» 뿐이다 — 다음 데모 창을 여는 바로 그 클릭이 재료다.
- 🔴 **읽는 시점**: 그 클릭 **직후** 120초 안에 두 번째 카드가 눌리면 설계상 합집합이다(describe 지연 창, ADR-MONO-071 § D4.1).
  첫 클릭 직후에 읽거나, 두 번째 클릭이 120초 뒤였는지 확인한다. 🔴 인스턴스가 **꺼지기만** 해서는 선택이 안 비워진다 — 그것은 정상이다(비우기는 종료가 아니라 다음 첫 요청 때).
- **함께 볼 것(대조군)**: 켜진 뒤 다른 카드를 누르면 선택이 **더해지는가**(세션 안 합집합, D4).
- 출처: `tasks/done/TASK-MONO-685-…` § AC-7 · § CORRECTION 두 절. 코드 수준 증거 = `test_cold_start_replaces_the_previous_sessions_selection`(라이브 8묶음 그대로, 옛 handler 에서 빨강).

## 🟡 항목 4 (① PASS 2026-09-24 · ② 여전히 막힘) — `TASK-BE-595` AC-4: 테넌트가 안 맞는 토큰에 이커머스 게이트웨이가 **403** 으로 답하고, 콘솔이 «세션 만료» 가 아니라 권한 문구를 보이는가 (2026-09-16 수령)

> 🔵 **2026-09-23 갱신 — § 창 준비 실측을 먼저 보라.** AMI 조건은 **해소됐다**(수정 `23f416e64` 가 구워진 커밋의 조상). ①은 **지금 창이면 잰다**(외부 테넌트 토큰 = `product-service-client`, `tenant_id=global-account-platform`) — 🔴 단 `code` 로 갈라라(`TENANT_FORBIDDEN`=PASS · 401=FAIL · 다른 403=**측정 불가**). ②는 **여전히 막혀 있다**.

- **무엇을 재나** (두 칸): ① 엔타이틀먼트 없는 외부 테넌트 토큰으로 ecommerce 게이트웨이의 보호 경로(예: `GET /api/orders/…`)를 부르면
  **403 + `code=TENANT_FORBIDDEN`** 인가(401 `UNAUTHORIZED` 면 FAIL). ② 같은 상황에서 콘솔이 `/login?error=session_expired` 로 보내지 않고
  inline 권한 문구를 보이는가.
- 🔴 **창만으로 풀리는지 미확인.** 데모 AMI 는 fresh clone 으로 굽는다(`infra/demo/aws/packer/demo-ami.pkr.hcl`) — 이 수정이 머지된 뒤
  구운 AMI 인지(또는 게이트웨이 이미지가 창에서 다시 빌드되는지)를 **먼저** 확인하라. 옛 AMI 에서 401 이 나오면 결함이 아니라 옛 코드다.
- 🔵 ②는 `TASK-PC-FE-292`(콘솔이 운영용 슬러그 `iam` 을 활성 테넌트로 삼는 것)와 얽힌다 — 292 가 먼저 고쳐지면 평소 경로로는 테넌트
  거절이 안 일어나므로, 외부 테넌트 토큰을 **일부러** 만들어야 ①②를 잴 수 있다.
- 출처: `projects/ecommerce-microservices-platform/tasks/…/TASK-BE-595-…` § AC-4. 코드 수준 증거 = `SecurityConfigRealDecoderPathTest`
  (고친 트리 초록, 고침을 되돌리면 테넌트 칸 5개 빨강).

## 🟡 항목 5 (가용재고 한 줄 기록 2026-09-24 · ②③ 측정 불가 유지) — `TASK-MONO-683` AC-4: 보충 제안 승인 → 발주 확정 → **wms 인바운드 예정(ASN) 생성**이 끝까지 가는가 (2026-09-16 수령)

> 🔴🔴 **2026-09-23 갱신 — 「신선 볼륨/선행 티켓 대기」는 과했다. § 창 준비 실측을 보라.** 새 시드 `a7a8f4e67` 은 **구워진 클론에 있고**, 시드 스크립트 자신이 *"683 이전 볼륨에서는 `SUP-001` 이 **새 행**으로 생긴다"* 라고 적어 뒀다(`seed-scm.sh:109`) ⇒ **지금 창이면 잰다.** ①이 0건이어도 **0 이 판정**이다(아래 본문이 그 경우를 닫는 법을 이미 적었다).

> 🔴🔴 **2026-09-23 정정 (같은 날 두 번째) — 앞의 「지금 창이면 잰다」는 틀렸다.**
> ✅ 살아남는 부분: **신선 볼륨은 필요 없다**(새 시드 `a7a8f4e67` 이 구워진 클론에 있고,
> `seed-scm.sh:109` 가 *"683 이전 볼륨에서는 `SUP-001` 이 **새 행**으로 생긴다"* 라고 적어 뒀다).
> 🔴 **죽는 부분: ①은 «재면 된다» 가 아니라 이미 2026-09-18 창에서 «0건» 으로 재졌다** —
> `TASK-MONO-683` § 창 실측(2026-09-18)이 그 관측과 이유(*"제안은 **wms 저재고 알림 또는 IVS
> 야간 스윕**에서만 생긴다"*)를 적었고, **"그러므로 다음 창에서 그냥 다시 봐도 또 0건이다"**
> 라고 못 박았다. ⇒ 표의 *"선행 티켓 뒤"* 가 **옳았다.**
> 🔵 **남아 있는 창 작업은 하나뿐**: 항목이 요구한 *"`seed-wms.sh` 이후 `SKU-APPLE-001` 가용재고"*
> 를 적는 것(09-18 기록에 **그 수치가 없다**). 나머지 ②③ 은 **선행이 생기기 전까지 측정 불가**다.

- **무엇을 재나** (순서대로, 🔴 앞 칸이 안 되면 뒤 칸은 «측정 불가» 로 적는다 — «실패» 가 아니다):
  ① `/scm/replenishment` 에 `SKU-APPLE-001` 의 보충 제안이 **한 건이라도 생기는가** — 683 이 측정하지 못한 전제다. 제안은 wms 저재고 알림
  또는 IVS 야간 스윕에서만 생긴다. 0건이면 그 사실과 `seed-wms.sh` 이후 `SKU-APPLE-001` 가용재고를 적는다.
  ② 제안 승인 → DRAFT → submit → 공급사 ack → confirm 뒤 `scm.procurement.inbound-expected.v1` 이 나가는가(procurement outbox).
  ③ wms `/wms/inbound` 에 `source=SCM_PROCUREMENT` ASN 이 생기는가, 그리고 `scm.procurement.inbound-expected.v1.DLT` 에 새 레코드가 **없는가**.
- 🔴 **창만으로는 안 풀릴 수 있다 — 새 시드가 한 번 돌아야 한다.** 683 은 `seed-scm.sh` 의 공급사·SKU 코드를 wms 코드(`SUP-001`/`SKU-APPLE-001`)로
  바꿨다. 683 이전 볼륨엔 옛 매핑(`SKU-DEMO-*` → UUID)이 남지만 그 SKU 에는 제안이 생길 경로가 없어 판정을 오염시키지 않는다.
  `SUP-001` 매핑은 새 시드가 돌아야 생긴다 — AMI 가 시드된 데이터를 굽는다면 재굽기 뒤의 창이다(683 은 이것을 확인하지 않았다).
- **함께 볼 것(대조군)**: 콘솔 `/scm/replenishment` 「공급사」 칸이 `SUP-001` 로 그려지는가(UUID 면 옛 매핑 — `이름 확인 불가` 로 보여야 한다).
- 출처: `tasks/in-progress/TASK-MONO-683-…` § AC-4 · § Phase 2. 코드 수준 증거 = `ScmInboundExpectedDemoSeedShapeDltTest#demoSeedMapping_resolvesInWmsDevSeed`
  (시드 스크립트를 읽어 wms dev 시드로 해석 — 옛 코드·옛 UUID 매핑에서 빨강).
- 🔴 **Failure Scenario 2 대조**: 이 집은 «닫히면서 집을 잃는» 의무만 받는다. 683 은 수령 시점(2026-09-16)에 아직 `in-progress` 이고,
  AC-4 를 ⚪ 로 둔 채 이 항목을 넘기고 닫히는 경로다 — 683 이 **닫히지 않고 살아 남는다면** 이 항목은 683 으로 되돌려야 한다(의무 이중 보유 금지).

---

## ✅ 항목 8 (닫힘 2026-09-22 — 판정 **FAIL**, 원인 확정 → `TASK-MONO-719`) — `TASK-MONO-718` ⓑ: 안 맞는 테넌트 안내가 **화면에 뜨는가** (2026-09-22 수령)

- **무엇을 재나** (한 줄): 콘솔에 **`demo-corp`** 로 로그인한 상태로 `/ecommerce/orders` 를 열면
  `data-testid="domain-tenant-mismatch"` 가 보이는가. 🔵 보이면 그 안에 **양쪽 테넌트 이름**
  (`demo-corp` · `ecommerce`)이 함께 있어야 한다 — 어느 쪽으로 가라는 말이 없으면 안내가 아니다.
- **대조군**: `ecommerce` 로 전환하면 안내가 사라지고 **주문 목록이 찬다**. 🔴 이 칸이 없으면
  「안내가 항상 뜬다」와 구별이 안 된다.
- 🔵 **촬영으로도 잡힌다**: `DEMO_TENANT=demo-corp` 매니페스트에서 `/ecommerce/*` 가 여전히
  `empty: true` 인데 **안내 마커가 없으면** 배선이 안 걸린 것이다.
- 🔴 **창만으로 풀린다 — 다만 재굽기가 선행이다.** 이 변경은 **프런트**(Vercel)라 배포는 따라오지만,
  § AC-2 의 시드 「테넌트 대조」 줄은 **구워진 클론**에서 도므로 다음 재굽기 전에는 안 보인다.
  ⇒ 안내 마커는 다음 창에서 바로, 시드 대조 줄은 재굽기 뒤.
- 출처: `tasks/done/TASK-MONO-718-…` § AC-3 한계.

## ✅ 항목 9 (닫힘 2026-09-22 — 양방향 bite **PASS**) — `TASK-MONO-710` AC-3: 런타임 사후조건의 **bite** (2026-09-22 수령)

- **무엇을 재나**: 시드에서 **주문 생성을 지우면** 사후조건이 실제로 **빨개지는가**(`seed_fail` →
  `seed_summary` rc≠0). 2026-09-22 창은 **양성 발화만** 봤다 — 하한을 넘겨 초록이 뜬 것.
- 🔴 **양성만 본 초록은 가드의 증거가 아니다** — 이 저장소가 반복해서 댄 대가다(«일을 하나도 안 하고
  rc=0»). 음성 방향을 한 번 보면 그 층이 선다.
- 🔵 **창이 꼭 필요하진 않을 수 있다** — 스택만 있으면 되므로 로컬 compose 로도 가능하다. 먼저
  그것을 시도하고, 안 되면 창에서.
- 출처: `tasks/done/TASK-MONO-710-…` § ⚪ 여전히 안 잰 것.

## ✅ 항목 10 (닫힘 2026-09-22 — 도착지 **PASS** · 카드 절반은 **영구 불가**) — `TASK-PC-FE-296` ⓒ: 링크가 **한 클릭으로 필터된 목록에 닿는가** (2026-09-22 수령)

- **무엇을 재나**: 개요 카드의 `operator-overview-card-wms-lowstock-link` 를 **브라우저에서 눌러**
  `/wms/inventory` 가 **저재고 체크박스가 켜진 채** 열리고 목록이 그 필터로 걸리는가.
- 🔵 단위 칸이 두 층(카드의 href · 화면의 시드)을 **각각** 물지만, 그 둘이 **한 항해에서 만나는지**는
  아무도 안 쟀다 — 이 저장소가 이름 붙인 «독립 표면 N개의 교집합은 아무도 안 잰다».
- **함께 볼 것**: 데모/운영의 **저재고 분모**(`AC-0 ③` 이 ⚪ 로 남긴 것). 0이면 「링크는 맞는데 볼 것이
  없다」이고, 그것은 결함이 아니라 데이터 상태다 — 🔴 둘을 섞지 마라.
- 출처: `projects/platform-console/tasks/done/TASK-PC-FE-296-…` § ⚪ 안 잰 것.


## ✅ 항목 11 (닫힘 2026-09-23 — **PASS**, 대조군 둘) — `TASK-MONO-719` AC-3: 안 맞는 테넌트에서 **힌트가 실제로 뜨는가** (2026-09-22 수령)

- **무엇을 재나**: 콘솔에 **`demo-corp`** 로 로그인한 상태로 `/ecommerce/orders` 를 열면
  「표시할 주문이 없습니다」**아래에** `data-testid="other-tenant-hint"` 가 보이는가.
  🔵 그 안에 **양쪽 이름**(`demo-corp` · `ecommerce`)이 함께 있어야 한다.
- **대조군 둘** (🔴 하나만으로는 「항상 뜬다」와 구별이 안 된다):
  ① `ecommerce` 로 전환 → 목록이 **차고** 힌트는 **사라진다**(빈 분기에만 산다).
  ② 🔴 `demo-corp` 에서 **목록이 찬** 도메인 화면(wms 등)에는 힌트가 없다 — 배선이
     ecommerce 주문 **한 장**에만 걸려 있다는 것까지가 이 창의 판정이다.
- 🔵 **창만으로 풀린다 — 재굽기 불필요.** 프런트(Vercel)이고 백엔드 데이터는 지금 상태로 충분하다
  (`ecommerce` 5건 · `demo-corp` 0건이 이미 그 대비를 만든다).
- 🔴 **718 의 전철을 밟지 마라**: 그 티켓은 단위 칸 10개 초록으로 닫혔고 창에서 FAIL 했다.
  719 의 칸들은 bite 를 양방향으로 봤지만, **화면에 뜨는가는 여전히 창만 답한다.**
- 출처: `tasks/…/TASK-MONO-719-…` § AC-3.


## ✅ 항목 12 (닫힘 2026-09-23 — 판정 **FAIL**, 원인 확정 → `TASK-MONO-721`) — `TASK-MONO-717` AC-1: 셀러 프로비저닝이 **실제로 성공하는가** (2026-09-23 수령)

- **무엇을 재나** (순서대로, 🔴 앞 칸이 안 되면 뒤는 «측정 불가» 로 적는다):
  ① `ecommerce-product-service` 로그에 `seller provisioning failed` / `seller left
     PENDING_PROVISIONING` 두 WARN 이 **사라졌는가**.
  ② 🔴 **그러나 로그 침묵은 판정이 아니다** — `account_db` 에 그 셀러-운영자 계정 **행이 생겼는가**,
     그리고 셀러가 `PENDING_PROVISIONING` 에서 **벗어났는가**. 이것이 티켓이 못박은 술어다.
- 🔴 **창의 첫 질문은 배선이 아니라 도달성이다**: `http://iam.${DEMO_DOMAIN}` 이 **ecommerce
  컨테이너 안에서** 해소되는가. 시드 스크립트는 **호스트에서** 그 주소를 쓰므로 그것이
  컨테이너 안의 증거는 아니다. 안 되면 갈래는 컨테이너 이름/공유 네트워크다.
- **대조군**: 토큰 단계와 accounts 단계를 **갈라서** 본다 — 401/`invalid_client` 면 등록·비밀 문제,
  404 면 **게이트웨이 라우트** 문제(`/internal/accounts/{a}/lock` 은 지금도 라우트가 없다).
- 🔴 **재굽기가 선행이다** — V0036(Flyway) · compose · `demo.env` 가 전부 **구워지는 표면**이다.
  stop/start 로는 안 온다(15차 창이 실측한 그 사실).
- 출처: `tasks/…/TASK-MONO-717-…` § AC-1.

## ✅ 항목 13 (닫힘 2026-09-24 — **PASS**, 셋 다) — `TASK-MONO-721` AC-4: 워크로드 교환으로 셀러 프로비저닝이 **실제로 성립하는가** (2026-09-23 수령)

- **무엇을 재나** (셋, 🔴 앞 칸이 안 되면 뒤 칸은 «측정 불가» 로 적는다):
  ① 셀러 등록 → `account_db` 에 셀러-운영자 **행이 생기는가**. 🔵 항목 12 가 FAIL 로 닫은 바로 그 자리다.
  ② 🔴 **대조군(이 항목의 본체)** — 같은 자격으로 **`wms`** 를 assume 하려 하면 **교환이 거절되는가**.
     🔴 기대 모양이 바뀌었다: 게이트웨이의 `403 TENANT_SCOPE_DENIED` 가 아니라 **발급자의
     `invalid_grant`** 다(`ADR-MONO-076` D6 · 721 AC-7). 403 이 나오면 그것은 **교환이 아니라
     기본 자격으로 불렀다**는 뜻이고, PASS 가 아니라 **배선 결함**이다.
  ③ 🔴 **D4 를 라이브에서 한 번** — 교환된 워크로드 토큰을 디코드해 `entitled_domains` ·
     파생 `roles` · `org_scope` 가 **없고** `sub` 가 **클라이언트**인지 본다. 단위 층에서는
     물었지만, 「운영자 토큰처럼 보이는 워크로드 토큰」이 ADR 이 이름 붙인 가장 비싼 실패다.
- 🔵 **무엇을 기다리나 — 재굽기다**(항목 12 의 그 조건이 여기도 그대로 적용된다). 이 변경은
  `V0037`(Flyway) 과 auth-service·product-service **코드**이고, 셋 다 **이미지 안**이다.
  🔴 시드·compose 와 달리 SSM 패치로는 안 온다.
- **함께 볼 것**: 그 창은 `TASK-MONO-717` 의 `review/` 잔여도 함께 푼다 — 717 AC-1 이 FAIL 로
  남아 있고, 그것을 푸는 것이 이 티켓이다.
- 출처: `tasks/…/TASK-MONO-721-…` § AC-4. 🔴 **Failure Scenario 2 대조**: 721 은 수령 시점에
  `review/` 이고 이 항목을 넘기며 닫히는 경로다 — **721 이 닫히지 않고 살아남으면 이 항목은
  721 로 되돌려야 한다**(의무 이중 보유 금지). 항목 5 가 683 에서 밟은 그 조건과 같다.

## ✅ 항목 14 (닫힘 2026-09-26 UTC 소유자 결정 — ① ⚪ 측정 불가 + 이유 · ② `TASK-MONO-735`(#4048 병합)로 이관 — 2026-09-26 두 창, § 16차 창 수확) — `TASK-MONO-726`: batch-worker → order-service `/api/internal/**` 가 **실제로 성립하는가** · `lockAccount` 라우트 (2026-09-24 수령)

- **무엇을 재나** (둘, 🔴 앞 칸이 안 되면 뒤 칸은 «측정 불가»):
  ① 🔴 **결과 상태** — 결제 뒤 PAID 인 주문이 `batch.jobs.stale-paid-order-confirmation.older-than-minutes`(30) 뒤 **CONFIRMED** 로 넘어가는가.
     보조: batch-worker 로그에 `StalePaidOrderConfirmationJob FAILED` 가 없는가(🔴 로그 침묵은 판정이 아니다).
     대조군: 토큰 단계와 호출 단계를 갈라 본다 — `invalid_client`=등록 · 연결 실패=주소 · order-service **401**=JWKS/issuer.
  ② `lockAccount` → `POST /internal/accounts/{a}/lock` — 셀러를 정지시켜 `account_db.accounts.status` 가 바뀌는가(iam 게이트웨이에 라우트가 없음은 정적으로 확인됨 — `TASK-MONO-726` § AC-0 ①).
- 🔵 **무엇을 기다리나 — 재굽기다.** `V0038`(Flyway) · ecommerce compose · `demo.env` 가 전부 구워지는 표면이다(compose·demo.env 는 SSM 패치로도 되지만 V0038 은 이미지 안).
- 출처: `tasks/…/TASK-MONO-726-…` § 구현 기록. 🔴 **Failure Scenario 2 대조**: 726 은 수령 시점에 `review/` — **726 이 닫히지 않고 살아남으면 이 항목은 726 으로 되돌린다**(의무 이중 보유 금지).

### 🔵 소유자 결정 (2026-09-26 UTC) — 항목 14 닫힘

- **① 결과 상태(PAID→CONFIRMED) — ⚪ 측정 불가 + 이유로 닫는다.** 정상 흐름에서 결제된 주문은 약 4초 만에 CONFIRMED 로 넘어간다
  (시드 15:11:42 → 15:11:46, 15차 창 실측). 판정이 요구하는 후보(결제됐는데 확정이 유실된 주문)는 **장애 주입으로만** 생긴다 — 15차 창이
  이유를 실측했다: 시드 주문은 PENDING·`payment_id` NULL 뿐이라 이 잡의 대상이 아니고, 합성 후보(행에 `payment_id` 삽입)는 원격 DB
  쓰기라 자동 모드 분류기(Remote Shell Writes)가 막는다. 로컬 장애 주입 IT 는 후속 후보로만 남기고 티켓은 기안하지 않는다(소유자 결정).
- **② `lockAccount` — `TASK-MONO-735`(#4048, 2026-09-26 병합)로 이관됐다.** 라이브 창 판정(AC-3, 계정 잠금이 계정의 테넌트로 찾는지 —
  자동·SUPER_ADMIN·셀러 정지)은 아직 남아 있다 — 735 의 AC 를 참조하라. 이 항목은 그 의무를 넘긴 것으로 닫는다(의무 이중 보유 금지).

## ✅ 항목 15 (② 닫힘 2026-09-25 — **PASS** · ① 닫힘 2026-09-26 — **PASS**, § 2026-09-26 창 수확) — `TASK-MONO-727` AC-3: 두 주소 고침이 **결과 상태로** 성립하는가 (2026-09-24 수령)

### 2026-09-25 창 기록 (04:06–04:13Z · 예산 1483 → 1486/1800 · 재굽기 없음)

🔴 **이 창은 03:00 을 걸치지 못했다** — 01:36Z 에 시각을 한 번 재고 그 뒤 다시 재지 않은 채 `/start` 를 불렀더니
04:06Z 였다. ⇒ **② 만 판정, ① 은 여전히 03:00 창이 필요하다.** 🔵 다음 창 교훈: `/start` 직전에 시각을 다시 재라.

**방법** — 클론(`/opt/monorepo-lab`, HEAD=`f1da21800`)에 `git fetch --depth 1 origin main` 후 **두 파일만**
`checkout FETCH_HEAD --`(`git pull` 전체 아님), `DEMO_DOMAIN` 을 IMDSv2 로 `demo-boot.sh` 와 같게 파생, `demo-up.sh`
과 같은 `-p`/`-f` 로 `up -d --no-deps --force-recreate` 두 컨테이너. 🔴 재생성 **전후 env diff**:
security-service **+1줄**(`ACCOUNT_SERVICE_BASE_URL`)뿐, batch-worker +3줄(727 의 1 + 726 의 `IAM_TOKEN_URI`·`ORDER_SERVICE_BASE_URL`),
그 밖(DEMO_DOMAIN 포함) 불변. 창 끝에 두 파일을 `checkout HEAD --` 로 되돌림(다음 부팅 = 구운 상태).
🔵 iam 의 `account_db` 는 **MySQL**(`iam-mysql`)이다 — Postgres 가 아니다.

**② 자동 잠금 — PASS** (합성 `auth.token.reuse.detected` 로 쟀다 — 앞 구간 ⚪ 은 아래 § 창 전제 그대로)

| | 고치기 전 (대조군) | 고친 뒤 (판정) |
|---|---|---|
| security-service `ACCOUNT_SERVICE_BASE_URL` | `<unset>` | `http://account-service:8082` |
| 일회용 계정(`fan-platform`, signup 201) | `db7e2de3-…` | `93e1d733-…` |
| 로그 | `Auto-lock attempt 1..3 threw: java.net.ConnectException` → `Auto-lock FAILURE — emitted pending event` | `account.locked recorded … source=system` |
| `security_auto_lock_failures_total` | 0 → **1** | 0 → **0** (재생성으로 카운터 초기화 — 불변 판정은 이 컨테이너 안에서) |
| **`account_db.accounts.status`** | **ACTIVE** (40초 대기) | **LOCKED** (~4초) |

🔵 같은 방법·같은 창에서 결과가 **반대로** 갈렸다 ⇒ 차이는 주소 한 줄이다. 401 없음(토큰 축 이상 없음).

**① 검색 색인 정합성 잡 — ⚪ 판정 안 됨(03:00 미포함)**. 보조(판정 아님): batch-worker 컨테이너 안에서
`product-service:8082/actuator/health` = `UP`, 예전 기본값 `:8081` = `Connection refused` · 컨테이너 시계 UTC(`TZ` 미설정 —
cron 03:00 은 UTC 로 돈다). ⇒ **다음 창은 02:50–03:10Z 면 된다**: batch-worker 하나만 같은 방법으로 재생성 → 03:00 뒤
ShedLock `batch-search-index-consistency-check` 의 `locked_at` + 잡 로그(연결 실패 부재 · 완료 기록).
🔴 727 은 `review/`(동결)라 거기엔 못 적는다 — **727 을 닫는 close chore 는 이 절을 읽고 ① 이 남았음을 봐야 한다**(4번째 차원).

🔴 **덤으로 본 것** — 클론의 `infra/demo/demo-boot.sh` 가 **이 창 전부터** 수정(unstaged) 상태였다. 이 창은 손대지 않았다.
누가·언제 바꿨는지 모른다(부팅 시 이 파일이 도는 버전이므로 «구운 상태 = 커밋» 가정이 이 파일엔 성립하지 않을 수 있다).
🔵 **같은 날 저장소에서 찾은 유력 원인 — 내용이 아니라 모드다**: git 이 `infra/demo/demo-boot.sh` 만 `100644` 로 들고 있고
(`demo-up.sh`·`demo-down.sh` 는 `100755`), packer 가 굽는 중에 `test -x … || sudo chmod +x …/demo-boot.sh`
(`packer/demo-ami.pkr.hcl:432`) 를 한다 ⇒ 클론은 모드만 바뀐 ` M` 이 된다. ⚪ **추론이지 관측이 아니다** — 다음 창에서
`sudo -u ubuntu git -C /opt/monorepo-lab diff --summary infra/demo/demo-boot.sh` 가 `mode change 100644 => 100755` 한 줄만
내면 닫는다(내용 diff 가 나오면 이 원인이 아니다 — 그땐 별도 티켓).
🟢 **닫힘 (2026-09-25 11:28Z · 15차 AMI 새 인스턴스 `i-09f10c696375ba99b`)** — AMI 에서 **막 만든** 인스턴스에서도 ` M` 이었고,
`diff --summary` = **`mode change 100644 => 100755 infra/demo/demo-boot.sh`** 한 줄 · 내용 diff **0줄**. ⇒ 굽기의 `chmod +x` 가 원인이 확정.
누가 손으로 고친 것이 아니다. (근본 정리 — git 에 `100755` 로 올리면 ` M` 이 사라진다 — 는 선택 사항, 판정에는 영향 없음.)

### 🔴 15차 AMI 첫 부팅에서 드러난 것 (2026-09-25 11:24–11:30Z)

security-service 가 `DETECT_VELOCITY_THRESHOLD: "1000000"`(BE-599 데모 완화)로 `@Max(10_000)` 을 넘어 **기동 실패 재시작 루프**.
SSM 으로 클론 값을 `"10000"` 으로 고쳐 재생성 → healthy · restarts=0 · 스택 47개 healthy → `/stop`(예산 1491/1800). 저장소 수정 = 같은 날
`fix(demo)` PR. ⇒ **내일 창의 인스턴스는 이미 고친 클론을 들고 있다**(stop/start 는 볼륨을 보존). 상세 = `TASK-BE-599` § CORRECTION.

- **무엇을 재나** (둘, 서로 독립):
  ① ecommerce batch-worker `SearchIndexConsistencyJob` — 로그에 product-service 연결 실패가 없고 잡이 완료를 기록하는가(예전엔 `:8081` 로 매번 실패).
  ② iam security-service 자동 잠금 — ~~의심 로그인(탐지 규칙을 넘기는 시도)을 일으켜~~ 🔴 **토큰 재사용 이벤트를 넣어**(아래 § 창 전제 — 의심 로그인으로는 못 일으킨다) `account_db.accounts.status` 가 **LOCKED** 가 되는가. 보조: `security_auto_lock_failures_total` 이 늘지 않는가. 🔴 로그 침묵은 판정이 아니다. 401 이 나오면 주소가 아니라 토큰 축(별건).
- 🔵 **재굽기 불필요** — 둘 다 compose 변경(클론)이라 SSM 으로 `git pull` 후 두 컨테이너만 재생성하면 된다(`TASK-MONO-726` 항목 14 와 달리 이미지 안의 변경이 없다).
- 출처: `tasks/…/TASK-MONO-727-…` § 구현 기록. 🔴 **Failure Scenario 2 대조**: 727 은 수령 시점에 `review/` — **727 이 닫히지 않고 살아남으면 이 항목은 727 로 되돌린다.**

### 🔴 창 전제 — 창을 열기 전에 저장소에서 확인한 두 사실 (2026-09-24 UTC · 창 없음)

**① 은 03:00 UTC 를 걸친 창에서만 잴 수 있다.**
`SearchIndexConsistencyScheduler.runConsistencyCheck` 는 `@Scheduled(cron = "0 0 3 * * *")` — **하루 한 번
03:00 UTC**(= 12:00 KST)에만 돈다. cron 은 어노테이션 리터럴이라 속성으로 못 바꾸고, `SearchIndexConsistencyJob`
을 부르는 곳도 이 스케줄러 하나뿐이다(수동 트리거 없음). ⇒ 03:00 을 안 걸친 창에서 볼 수 있는 것은 «주소가 닿는가»
(배선)뿐이고 그것은 이 항목이 요구한 판정이 아니다. 🔵 **권장 창 = 02:45–03:15 UTC** — 웜업(약 10분 30초) 뒤
03:00 전에 두 컨테이너를 재생성하고 ② 를 먼저 잰 뒤, 03:00 에 ① 의 로그를 본다. ShedLock 행
(`batch-search-index-consistency-check`)의 `locked_at` 도 03:00 실행의 증거로 같이 읽는다.

**② 는 «의심 로그인» 으로는 일으킬 수 없다 — 토큰 재사용 이벤트로 잰다.**
- 🔴 **브라우저 폼 로그인은 로그인 이벤트를 하나도 내지 않는다.** 유일한 비밀번호 경로인
  `CredentialAuthenticationProvider`(auth-service) 의 클래스 주석이 그렇게 적는다 — `LoginUseCase` 의 rate-limit ·
  `auth.login.*` 발행 · 디바이스 세션은 JSON `/api/auth/login` 의 몫이었고 그 엔드포인트는 BE-398 에서 제거됐다.
  `TASK-BE-309` 는 이것을 «추후 enhancement (별 task)» 로 남겼고, 🔴 **그 별 task 는 이번 grep 에서 찾지 못했다**
  (iam `tasks/**/*.md` — 부재 판정이 아니라 «이 검색으로는 못 찾음»). ⇒ security-service 의 VELOCITY ·
  DEVICE_CHANGE · GEO 규칙은 데모에서 **입력을 받을 길이 없다**. 이 항목의 판정 범위 밖이지만 기록한다.
- 설령 이벤트가 나와도 VELOCITY 로는 못 닿는다: 점수 80(AUTO_LOCK)은 **계정이 식별된** 실패 10회/1시간이 필요한데,
  `LoginUseCase` 는 5회째부터 요청을 막고(`auth.login.max-failure-count:5`, 창 900초) 그 뒤의 `RATE_LIMITED` 이벤트는
  `accountId=null` 이라 규칙이 무시한다.
- ⇒ **`TokenReuseRule`**(고정 점수 100 → 곧바로 `AccountServiceClient.lock`)로 잰다. 인스턴스 안에서:
  1. 일회용 계정 — account-service 공개 `POST /api/accounts/signup`(테넌트 헤더 없음 → `fan-platform`)을 컨테이너 IP 로.
     🔴 **데모 계정을 잠그지 마라** — 방문자·촬영이 쓰는 계정이다.
  2. iam Kafka 에 `auth.token.reuse.detected` 봉투 하나: `eventId`(uuid) · `eventType="auth.token.reuse.detected"` ·
     `occurredAt` · `tenantId` · `payload{accountId, tenantId, timestamp}` (`AbstractAuthEventConsumer` 는 `tenantId`
     가 없으면 DLQ 로 보낸다).
  3. 판정 = `account_db.accounts.status` 가 **LOCKED**. 보조 = security-service 로그의 `Auto-lock` WARN 부재 ·
     `security_auto_lock_failures_total` 불변. 401 이면 주소가 아니라 토큰 축(별건).
  - ⚪ **이 방법이 재지 않는 구간**: auth-service 가 실제 리프레시 재사용에서 그 이벤트를 내는 앞 구간
    (`SasRefreshTokenAuthenticationProvider`). 727 이 고친 것은 security-service → account-service 주소뿐이므로
    판정에는 충분하지만, 판정문에 «합성 이벤트로 쟀다» 를 적어라.
- 🔴 **`git pull` 은 726 의 compose 변경도 같이 올린다** — batch-worker 를 재생성하면 726 의 order-service 주소·토큰
  환경값도 그 컨테이너에 들어간다(V0038 은 이미지 안이라 여전히 없다). ⇒ 그 창의 batch-worker 로그에 `StalePaidOrder…`
  토큰 실패(`invalid_client`)가 보이면 **726 의 예상된 미완**이지 727 의 회귀가 아니다.
- 🔴 **창 제어 API 호출이 자동 모드 분류기에 막혔다**(2026-09-24 09:00Z — 읽기 전용 `GET /status` 가 «Exfil Scouting»).
  다음 창은 허용 규칙을 먼저 세우거나 소유자가 명령을 직접 실행해야 한다.

## ✅ 항목 16 (측정 완료 2026-09-26 — 불일치 세션 0/6 · (b) 모양 재현=fall-through 200, § 2026-09-26 창 수확) — `TASK-BE-604` AC-0 ⓑ: 테넌트 불일치로 **fall-through 로만 갱신되는 세션**이 실제로 있는가 (2026-09-25 수령)

- **왜 재나**: 소유자 결정(2026-09-25 UTC) = ⓑ **측정 먼저**. SAS 기본 refresh provider 를 제거하면, 미러 행 테넌트 ≠ client
  테넌트인 세션은 지금 `TOKEN_TENANT_MISMATCH` → 기본 provider 로 흘러 **갱신되고 있다가** 끊긴다. 그 모집단이 0 인지, 누구인지를 잰다.
- **무엇을 재나** (셋, 🔴 ① 이 주 판정 · ②③ 은 교차 확인):
  ① **구조 — 지금 살아 있는 SAS 세션 중 불일치 세션 수** (`auth_db`, 계정 = `auth_user`/`auth_pass`):
  ```sql
  SELECT c.client_id, c.tenant_id AS client_tenant, rt.tenant_id AS mirror_tenant, COUNT(*) AS n
  FROM oauth2_authorization a
  JOIN oauth_clients c ON c.id = a.registered_client_id
  LEFT JOIN refresh_tokens rt ON rt.jti = CAST(a.refresh_token_value AS CHAR)
  WHERE a.refresh_token_value IS NOT NULL AND a.refresh_token_expires_at > NOW()
  GROUP BY 1, 2, 3 ORDER BY n DESC;
  ```
  판정 = `mirror_tenant IS NOT NULL AND mirror_tenant <> client_tenant` 행의 `n` 합(= 제거 시 끊길 세션).
  🔴 `mirror_tenant IS NULL` 행은 **별도 코호트**(미러 행 없음 — 지금 provider 는 도메인 검사를 전부 건너뛴다. BE-604 Edge Case 2)로 따로 적어라.
  🔴 **유효성 술어**: 전체 `n` 합 > 0. 0 이면 «불일치 0» 이 아니라 «세션이 없었다» — 판정 불가로 적는다. 🔴 `CAST(… AS CHAR)` 조인이
  한 행도 안 맞으면(전부 NULL) 조인 키가 틀린 것이다 — 이때도 판정 불가(대조군: 창 안에서 방금 로그인한 세션은 반드시 맞아야 한다).
  ② **흐름 — 창 동안 실제로 fall-through 한 refresh 수**: auth-service 로그의 `SAS_REFRESH: cross-tenant attempt detected` 줄 수와
  `SELECT event_type, COUNT(*) FROM outbox WHERE event_type LIKE 'auth.token.%' GROUP BY 1;` 의 `auth.token.tenant.mismatch` 행.
  🔴 **유효성 술어**: 같은 창에 `auth.token.refreshed` 가 1건 이상. refresh 가 0 이면 mismatch 0 은 아무것도 재지 않았다.
  ⚪ outbox 행이 발행 뒤 지워지는지는 코드에서 정리 잡을 못 찾았다(부재 판정 아님) — 로그와 outbox 두 값이 어긋나면 적어라.
  ③ **예측된 모집단을 직접 만든다** (코드 판독상 가설 둘 · 🔴 가설이지 관측이 아니다):
     (a) SUPER_ADMIN(테넌트 `'*'`)이 스토어 client 로 로그인 → 클레임 `'*'`(`TenantClaimTokenCustomizer.java:895` 부근) vs client `ecommerce`;
     (b) BE-507 이전 `fan-platform` 계정이 다른 테넌트 client 로 로그인(`CredentialAuthenticationProvider.java:190-213` 교차 조회).
     각각 로그인 → refresh 1회 → ② 의 로그 줄이 **그 세션 jti 로** 찍히는가. 🔵 대조군: 콘솔(`gap` client · `gap` 클레임 예상)
     로그인 → refresh 에서 mismatch 가 **안** 찍혀야 한다. 🔴 공유 데모 계정은 잠그지 마라(여기선 로그인·refresh 만 — 잠금 없음).
- 🔵 **재굽기 불필요** — 15차 AMI 로 잰다(BE-603 이전 이미지라 미러 행 `account_id` 가 이메일이지만 테넌트 축과 무관).
  🔴 단, 긴 이메일 계정은 미러 행이 없어 ① 의 NULL 코호트로 간다 — 섞지 마라.
- 🔴 **이 측정이 답하지 못하는 것**: 데모의 모집단은 운영(실사용자)이 아니다. ① 이 0 이어도 «운영에 없다» 의 증거가 아니라
  «데모에 없다» 다. ③ 이 재현되면 그것이 제거 판정의 실질 입력이다(«이 모양의 사용자가 있으면 끊긴다» 가 구조적으로 참).
- 출처: `projects/iam-platform/tasks/ready/TASK-BE-604-…` § AC-0. 🔴 **Failure Scenario 2 대조**: 604 는 `ready/` — 결과는 604 AC-0 에도
  적고, 604 가 착수되면 이 항목은 604 로 돌아간다(의무 이중 보유 금지).

## ⏳ 항목 19 (수령 2026-09-29 UTC · 소유자 결정 «다음 AWS 데모 창에서 잰다») — `TASK-MONO-740` AC-3: erp 단일 테넌트 래칫(라이브 절반)의 rc

- **왜 재나**: 740 의 AC-3 은 «스크립트 가드 **전부** rc=0» 인데 37개 중 이것 하나가 미측정으로 남았다. 로컬 Docker 가 비어 있어(이미지·볼륨 0) 재려면
  iam·erp·console 서비스 ~13개를 처음부터 빌드해야 했고, 창에서는 스택이 이미 떠 있으므로 **명령 하나**다. 740 은 이 의무를 여기로 넘기고 닫혔다.
- **무엇을 재나**: 창에서 erp 가 켜진 상태로, 클론에서 `bash scripts/check-erp-single-tenant-ratchet.sh` 의 rc 와 출력
  (`tenant_id` 컬럼 보유 테이블 수 · erp 전체 distinct `tenant_id` 목록). 컨테이너 이름은 기본값 `erp-platform-mysql` 이 compose 의 `container_name` 과 같다;
  루트 비밀번호가 기본값(`root`)이 아니면 `MYSQL_ROOT_PASSWORD` 로 준다.
- 🔴 **유효성 술어는 스크립트가 이미 갖고 있다**: 컨테이너 없음 · 테이블 0 · 테넌트 값 0 은 전부 **실패**(rc≠0)로 나온다 — 그 rc=1 을 «래칫 위반» 으로 읽지 마라.
  위반은 distinct ≥ 2 뿐이다(ADR-ERP-001 — D: 그때가 Option B 재논의 시점).
- 🔵 **재굽기 불필요** — 스크립트는 클론에 이미 있고(`TASK-ERP-BE-043` AC-7), erp 번들만 켜면 된다.
- 🔵 **740 이 이 측정에 기대하는 것은 «영향 없음» 의 확인뿐이다** — 740 이 바꾼 파일(`CLAUDE.md` · `platform/testing-strategy.md`)을 이 스크립트는 읽지 않는다(grep 확인).
  즉 결과가 무엇이든 740 을 다시 열 이유는 되지 않는다; distinct ≥ 2 가 나오면 그것은 **ADR-ERP-001 의 사건**이고 새 티켓이다.
- 출처: `tasks/done/TASK-MONO-740-promote-four-memory-only-rules.md` § CORRECTION.

# 🟢 2026-09-27 17차 창 수확 (06:13Z 부팅 ~ 07:15Z · 17차 AMI `ami-01a237c49e6a385d1` · 클론 `a6f0ab791` · 인스턴스 `i-08d11a45750b42955` · 신선 볼륨 · 예산 1602→1667/1800 · 분석=Opus 5.5)

부팅: `demo-stack` active, SSM `demo-boot.sh console wms scm fan erp finance` 추가 부팅 → **47 → 96 running · unhealthy 0** · 시드 요약 전부 실패 0. Lambda `AMI_REPO_COMMIT = a6f0ab791`(정확 — 16차 창의 오류가 재발하지 않았다. 그때는 핀 파일이 apply 전에 되돌려져 Lambda 가 15차 커밋을 들고 있었다; 교훈 = **핀 PR 을 머지하고 pull 한 뒤에** `terraform plan`).

| 항목 | 판정 | 근거 |
|---|---|---|
| 부팅 점검 | 🟢 | RepoCommit `a6f0ab791` 정확 일치 · 96 running · unhealthy 0 · 시드 실패 0 |
| `TASK-MONO-735` AC-3 스텝 1(자동 잠금, ecommerce) | 🟢 **PASS** | 합성 재사용 2건 → LOCKED(~2s) · `account.locked … source=system` · 404 없음(16차 창은 같은 계정 모양에서 404였다) |
| `TASK-MONO-735` AC-3 스텝 1 대조군(fan-platform) | 🟢 **PASS** | LOCKED(~2s) |
| `TASK-MONO-735` AC-3 스텝 3 = `TASK-BE-602` AC-3 | 🟢 **PASS** | 잠긴 스토어 소셜 계정 로그인 → `/login?error=account_unavailable` · `login_history` FAILURE 1(대조군 SUCCESS 1) |
| `TASK-MONO-735` AC-3 스텝 5(§⑧ 교차 테넌트 세션) | 🟢 **PASS** | ecommerce 계정, 스토어+팬 클라이언트 로그인, 이벤트 tenantId=fan-platform 2건 → LOCKED(~2s) |
| `TASK-MONO-735` AC-3 스텝 2(콘솔 SUPER_ADMIN 잠금) | 🔴 **FAIL** | 404 「대상 계정을 찾을 수 없습니다」×3 · 계정 ACTIVE 유지 → `TASK-MONO-737` |
| `TASK-MONO-735` AC-3 스텝 4(셀러 정지) | 🔴 **FAIL** | 셀러 SUSPENDED, 계정 ACTIVE 유지 · product-service 로그 404(fail-soft) → `TASK-MONO-737` |
| `TASK-MONO-726` 항목 14 ②(`lockAccount`) | 🔴 **FAIL**(위와 동일 근거) | `review/` 유지 — `TASK-MONO-737` 대기 |
| `TASK-MONO-730` AC-1 | 🟢 **PASS → close** | viewer 게이트 화면에 ⓒ 안내(«접근 가능한 테넌트가 없습니다») 렌더 |
| `TASK-BE-597` AC-2 | 🟢 **PASS → close**(동일 근거) | 〃 |
| `TASK-PC-FE-301` AC-2 | 🟢 **PASS → close**(동일 근거) | 〃 |
| `TASK-MONO-648` 재촬영 | 🔵 완료 | 콘솔만(`DEMO_TENANT=ecommerce`) — 계획 101 · 촬영 86 · 실패 15(거부 11 = ecommerce 미구독 도메인 erp/finance/scm/tenants/partnerships · 동적 미해결 4). 후보 목록은 648 § 2026-09-27 절에 기록, `in-progress` 유지(소유자 최종 승인 대기) |
| ⚪ 미설명 관측 | ⚪ | viewer 첫 로그인 시도에서 JSON `{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}` 1회 — 그러나 `login_history` 는 06:57:42Z SUCCESS, 어떤 서비스도 401 을 로깅하지 않음. `https://auth.hubwang.com/login` 에서 재시도하니 정상. 원인 미확인, 1회만 관측 |
| 예산 | 정보 | 1602 → 1667/1800(창 소모 약 65분) |

🔴 **새 결함(기안)**: `TASK-MONO-735` 가 고친 잠금 경로 중 **헤더 없는 호출은 성공하고 구체 테넌트 헤더를 싣는 호출(admin-service·product-service)은 여전히 404** — 원인 미확정 → **`TASK-MONO-737`** 신규 기안(root, ready). `TASK-MONO-735`·`TASK-MONO-726`(항목 14②)에 CORRECTION 절로 각각 기록, 둘 다 `review/` 유지.

🟢 **닫힘(4차원 검증)**: `TASK-MONO-730`(#4043) · `TASK-BE-602`(#4029) · `TASK-PC-FE-301`(#4049) · `TASK-BE-597`(#4001) — 넷 다 이 창의 라이브 판정으로 남은 AC 가 해소돼 `done/` 로 이동.

---

# 🟢 2026-09-26 16차 창 수확 (15:06Z 부팅 ~ 16:3xZ · 16차 AMI `ami-0134ac19b5c15ef0d` · 클론 `58d4920c4` · 인스턴스 `i-0917a5e39bd75f1d3` · 신선 볼륨 · 분석=Opus 5.5)

🔵 **부팅 경위**: 옛 인스턴스 `i-09f10c696375ba99b` 가 apply 전에 **이미 AWS 에 없었다**(태그·id 조회 0건 · 볼륨 0) ⇒ plan 이 «replace» 가 아니라 «create»(0 destroy). 누가 지웠는지는 `cloudtrail:LookupEvents` 권한이 없어 미상. 새 인스턴스는 `/start` 를 거치지 않아 선택이 `iam ecommerce` 뿐이었고, 에이전트가 SSM 으로 `demo-boot.sh console wms scm fan erp finance` 를 더 올렸다(96 컨테이너 · unhealthy 0).
하트비트 = 에이전트가 4분마다 `POST /heartbeat`(지난 창의 유휴 정지 교훈). 14차 AMI(`ami-03789993d93a320c1` · `snap-0c27fc0f645e5fa53`)는 이 창에서 prune(소유자 승인) — 남은 AMI = 15차(롤백본) · 16차.

| 항목 | 판정 | 근거 |
|---|---|---|
| 부팅 점검 | 🟢 | RepoCommit `58d4920c4` · `demo-stack` active(7분) · security-service healthy · `DETECT_VELOCITY_THRESHOLD=10000`(컨테이너 env + override 파일) |
| `TASK-BE-596`(done) 시드 실패 0 · `TASK-MONO-706` 사가 | 🟢 **PASS** | 시드 요약 ecommerce 14 · wms 3 · scm 9 · finance 7 · erp 20 · fan 18 — **실패 0 전부** · `outbound_db.outbound_saga` **COMPLETED=1**(STUCK 0) · `wms.outbound.shipping.confirmed.v1.DLT` **토픽 자체가 생기지 않음** · 모든 DLT/dlq 끝 오프셋 0 |
| `TASK-BE-599` AC-4 | 🟢 **PASS** | 두 네트워크 `119.204.*.*` ↔ `118.235.*.*` (티켓 § CORRECTION) |
| `TASK-BE-607` AC-3 | 🟢 **PASS** (서비스 수준) | 변경·재설정 204 · 결과 상태 · `OptimisticLock` 0 · 🔴 게이트웨이 경유는 둘 다 401 → `TASK-BE-609` |
| `TASK-BE-602` AC-3 | ⚪ 전제 미충족 | 스토어 소셜 계정 생성·이벤트 테넌트 🟢 · 잠금 불가(404) → `TASK-MONO-735` |
| `TASK-BE-597` AC-1′ · AC-2 | 🟢 · 🔴 | 콘솔 촬영 68/56 · 거부 = `/partnerships` · `/tenants`(676 의 의도된 거부) · viewer 는 403 에 도달 불가 → `TASK-PC-FE-301` |
| `TASK-MONO-730` AC-1 | 🔴 **FAIL** | 위와 같은 원인 · 소유자 결정 ⓒ → `TASK-PC-FE-301` |
| `TASK-MONO-697` AC-0 | ⚪ 관측 불가 | prometheus 3곳 게이트웨이 타깃 down(ecommerce = 401) · `result: []` → `TASK-MONO-736` |
| 항목 14 ① | ⚪ 후보 없음 | 정상 흐름에서 결제 주문은 ~4초에 CONFIRMED(시드 15:11:42 → 15:11:46) ⇒ 후보(결제됐는데 확정 유실)는 **장애 주입으로만** 생긴다 · 원격 DB 쓰기는 분류기가 막는다 |
| 항목 14 ② `lockAccount` | ⚪ 미측정 · **코드 판독상 같은 결함** | product-service `AccountServiceSellerProvisioner.lockAccount` 도 `X-Tenant-Id` 없음 — 오늘 잰 두 잠금 경로가 같은 모양으로 404 ⇒ `TASK-MONO-735` 에 셋째 호출처로 넣었다(시드 셀러를 정지시켜야 재므로 재지 않음) |
| 항목 17 | ⚪ 이 창에 재사용 없음 | 실제 트래픽 재사용 **0** — `TOKEN_REUSE` 4행은 전부 BE-602 합성 · 콘솔 `refresh_error` 는 console-web(Vercel) 로그라 **인스턴스에서 측정 경로 없음** |
| 항목 18 | ⚪ 모집단 측정 불가 · 🟢 **구조적 재현** · **② 구현 티켓 기안 = `TASK-BE-611`** | 모집단 `social_identities` = **0**(유효성 술어) · `refresh_tokens` 22 중 이메일 키 0. 재현(Kakao 스텁, 16:25Z): 한 신원 `60218261625` 으로 스토어 → 팬 소셜 로그인 둘 다 코드 발급 · 계정 **1**(`ecommerce`) · 신원 **1**(`ecommerce`) ⇒ 팬 client 로그인이 스토어 계정으로 들어간다. (② 미러 행 쿼리는 코드→토큰 교환을 안 해서 0행 — 판정은 계정·신원 행으로) · 이 항목의 기안 의무는 **BE-611 로 이행 — 항목 닫힘** |
| `TASK-PC-FE-299` AC-4 | ⏳ | 데모 정지 뒤 소유자 확인 |
| `TASK-MONO-648` | 🔵 | 콘솔만 재촬영 — 동적 미해결은 테넌트/앱 사유(티켓 § 2026-09-26) |

🔴 **이 창의 새 결함 (전부 티켓 기안)**: `TASK-MONO-735` 잠금 호출이 계정 테넌트를 안 싣는다(자동 · SUPER_ADMIN · 셀러) · `TASK-BE-609` 게이트웨이 경유 비밀번호 변경/재설정 401 · `TASK-BE-610` 같은 브라우저 소비자 로그인 뒤 콘솔 = `/onboarding`(SSO 가 소비자 principal 을 재사용) · `TASK-PC-FE-301` 선택 가능 테넌트 0 의 막다른 안내 · `TASK-MONO-736` 게이트웨이 audience 카운터 관측 불가.
🔴 **이 창에서 에이전트가 낸 사고(기록)**: BE-602 스텁용으로 auth-service 를 `demo.env` 없이 재생성 → `OIDC_ISSUER_URL` 이 `http://iam.local` 로 **약 3분간** 바뀌었다(워크로드 토큰 401). 같은 창에서 `demo.env` 를 source 해 복구 · 최종 원상복구 뒤 발급자 `https://auth.hubwang.com` · KAKAO env 0 확인.
⚪ 곁관측: `login_history` 에 인스턴스 자신의 공인 주소(`43.203.*.*`, UA `Other`)로 찍힌 `demo@demo.com` 로그인 3건(15:25Z) — 출처 미확인(시드 또는 서버 쪽 호출로 추정), 판정에서 제외.
# 🟢 2026-09-26 창 수확 (02:42:55–~03:05Z · 예산 1491 → 1512/1800 · 15차 AMI `ami-004f04b67daf40b89` · 클론 `46aa3191` · 분석=Opus 5.5)

🔴 **창이 약 03:05Z 에 스스로 꺼졌다 — 유휴 정지다.** 제어 Lambda 는 마지막 하트비트 뒤 `idle_minutes`(20) 가 지나면 정지한다
(`infra/demo/aws/README.md:236`, EventBridge 5분 주기). 이 창은 론처 페이지를 열지 않고 **SSM 으로만** 일해서 하트비트가 한 번도 없었다
⇒ 부팅 약 20분 뒤 정지. 🔵 **다음 창: 론처를 브라우저로 열어 두거나 `POST /heartbeat` 를 주기적으로 부른다.** 꺼진 뒤 남은 항목은 아래 표.

| 항목 | 판정 | 근거 |
|---|---|---|
| 15 ① `TASK-MONO-727` 검색 색인 정합성 잡 | 🟢 **PASS** | ShedLock `batch-search-index-consistency-check` `locked_at=03:00:00.105Z` · `PRODUCT_SERVICE_BASE_URL=http://product-service:8082` · 02:58Z 이후 연결 실패 줄 **0** · `SearchIndexConsistencyJob completed (executionId=4)` + `totalProducts=24` (= product-service 를 실제로 읽었다) |
| 14 ① `TASK-MONO-726` batch → order `/api/internal/**` | 🟡 **배선 PASS · 결과 상태 ⚪** | `StalePaidOrderConfirmationJob completed … scanned=0` 02:50Z·03:00Z 2회 · FAILED 0. 이 잡은 토큰 실패·4xx/5xx 면 `FAILED` 를 남긴다(`StalePaidOrderConfirmationJob.java:23`) ⇒ 토큰 발급 + order-service 2xx 성립. ⚪ 결과 상태(PENDING+payment 주문 → CONFIRMED)는 **후보가 없었다**: 시드 주문은 PENDING 1건뿐이고 `payment_id` 가 NULL(= 이 잡의 대상 아님, `OrderJpaRepository.java:135-137`), 02:50Z 에 order-service 자체의 결제 타임아웃 탐지기가 CANCELLED 로 바꿨다(`order_auto_cancelled_payment_timeout attempts=5`). 합성 후보(행에 `payment_id` 넣기)는 **원격 DB 쓰기로 분류기에 막혔다**(Remote Shell Writes). 🔴 «PAID 상태» 는 없다 — 판정 술어는 `status=PENDING AND payment_id IS NOT NULL AND created_at < now-30m` 이다(이 항목의 산문이 PAID 라고 적은 것을 정정). |
| 14 ② `lockAccount` 라우트 | ⚪ 미시도 | 콘솔에서 셀러 정지가 필요 — 브라우저 항목 |
| 16 `TASK-BE-604` AC-0 ⓑ | 🟢 **측정 완료** | 아래 § 항목 16 결과 |
| `TASK-BE-598` AC-3 | 🟢 **PASS** | `ecommerce-minio-init` 종료코드 **0** · 이미지 `ghcr.io/kanggle/mirror-minio-client:2024.10.8-debian-12-r1` · `mc ls` 에 `firstproject-local-product-images/` · 익명 권한 `download` |
| `TASK-BE-599` AC-3 | 🟢 **PASS** | 아래 § 로그인 판정 |
| `TASK-BE-599` AC-4 | 🟡 **기전 PASS · 라이브 ⚪** | `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` · XFF `198.51.100.9` 로그인 → `ip_masked=198.51.*.*` (헤더 없는 로그인 = `172.19.*.*`). 라이브 판정(서로 다른 두 네트워크에서 공개 주소로 로그인)은 소유자 기기가 필요 |
| `TASK-BE-600` AC-3 | 🟢 **PASS** | 아래 § 로그인 판정 |
| `TASK-BE-601` AC-3 | 🟢 **PASS** | 아래 § 로그인 판정 |
| `TASK-MONO-730` AC-0 | 🟢 **참** | `git merge-base --is-ancestor a3f5d52ec 46aa31911` rc=0 · 라이브: `auth_db.credentials` 에 `viewer@demo.com`(iam, `…ad05`) · `admin_db.admin_operators` ACTIVE, 역할 **0** |
| `TASK-BE-597` AC-1′ · AC-2 | ⚪ 미시도 | 콘솔 브라우저 항목(nav 전 라우트 HTTP 상태 · viewer 403 화면) |
| `TASK-BE-596`(done) 시드 실패 0 · 706 사가 | ⚪ 미판정 | 시드 로그 위치를 추측으로 찾다 창이 꺼졌다. 시드는 `demo-up.sh:404` → `seed/seed.sh` 이고 출력은 부팅 서비스 로그로 간다 — 다음 창은 그 유닛 이름부터 `ls /etc/systemd/system` 로 잰 뒤 journal 을 **시간 범위를 좁혀** 읽어라(범위 없는 `journalctl` 이 60초 SSM 한도를 넘겼다) |
| `TASK-PC-FE-299` AC-4 | ⏳ 지금 가능 | 데모가 꺼져 있다 — 콘솔 로그인 화면 확인은 브라우저 항목 |
| `TASK-MONO-648` 재촬영 | ⚪ 미시도 | |

## 로그인 판정 (BE-599 · 600 · 601) — 일회용 계정, 공유 데모 계정 잠금 없음

방법: 인스턴스 안에서 `curl` — 공개 클라이언트 `demo-spa-client`(V0008, `["none"]`, PKCE, redirect `http://localhost:3000/callback`, 테넌트 fan-platform)로
authorization_code 폼 로그인. 일회용 계정 A=`wa-260249@ex.io`(`7e39cb51-…`, 잠글 것) · B=`wb-260249@ex.io`(`6fd1e88f-…`, 대조군), 36자 이하.
잠금 = 합성 `auth.token.reuse.detected`(727 ② 와 같은 방법) → `TokenReuseRule` 자동 잠금 → `account.locked`. 🔴 «합성 이벤트로 잠갔다».

| 단계 | A | B |
|---|---|---|
| 가입 · 폼 로그인 · 토큰 | 201 · 성공 · 발급 | 201 · 성공 · 발급 |
| `login_history` (BE-599 ①) | ATTEMPTED → **SUCCESS** (fan-platform) | ATTEMPTED → **SUCCESS** |
| 틀린 비밀번호 (BE-599 ②) | — | `/login?error` · **FAILURE** 행 · velocity `security:velocity:fan-platform:<B>:3600` **없음 → 1** |
| 대조군: 없는 이메일로 틀린 로그인 | velocity 키 총수 **1 → 1**(늘지 않음 = 계정 없는 실패는 안 센다) | |
| 잠그기 전 refresh (대조군) | 200 · 200 | 200 · 200 |
| 잠금 | ACTIVE → **LOCKED** (~2s) · auth-service `revoked sessions … revokedTokens=2 propagationLagMs=655` | ACTIVE |
| **BE-601** 같은 refresh 토큰 | **400 `invalid_grant`** | **200** |
| **BE-600** 폼 로그인 | **`/login?error`** | 성공 |

⚪ auth `outbox` 에서 `auth.*` 행이 하나도 안 보였다(발행 뒤 삭제로 보인다 — 정리 코드는 이번에 확인 못 함) ⇒ outbox 는 이벤트 **발생량**의 계기가 아니다. 판정은 security `login_history`·로그로 했다.

## 항목 16 결과 — `TASK-BE-604` AC-0 ⓑ

① **구조** (살아 있는 SAS 세션, 로그인 판정 전 02:47Z): `platform-console-web` iam/iam **4** · `ecommerce-web-store-client` ecommerce/ecommerce **2** ⇒
불일치 **0 / 6**, 미러 없음 **0**. 유효성 술어 충족(세션 > 0 · 조인 전부 맞음). 🔴 내 예측 «콘솔 client 테넌트 = `gap`» 은 **틀렸다** — 라이브 값은 `iam`.
② **흐름**: 창 전체의 `cross-tenant attempt detected` = 재현 전 **0**(그 사이 refresh 다수 — 술어 충족).
③ **재현**:
- (a) `demo@demo.com` → `demo-spa-client`(fan-platform): 불일치 **안 남** — 이 계정은 테넌트별 자격 행이 셋(ecommerce · fan-platform · iam)이라
  fan-platform 자격으로 들어온다. 🔴 테넌트 `'*'` 자격 행은 없다 ⇒ 가설 (a) 의 모양은 이 데모에 없다.
- (b) 🔴 **재현됨** — fan-platform 에만 있는 B 로 **`platform-console-web`(iam)** 에 로그인 → 토큰 클레임 `tenant_id=fan-platform` · 미러 행 fan-platform →
  refresh → 로그 `SAS_REFRESH: cross-tenant attempt detected. clientTenant=iam, tokenTenant=fan-platform` **그리고 HTTP 200**.
  ⇒ 우리 provider 가 거부하고 SAS 기본 provider 가 통과시킨다는 BE-604 의 전제가 **라이브에서 참**. 기본 provider 를 그냥 제거하면 **이 모양의 세션은 끊긴다**.
🔵 곁가지: fan-platform 소비자 계정이 콘솔 public client 로 토큰을 받는다(교차 테넌트 조회). 콘솔 권한은 operator 교환에서 걸러지므로 결함 판정은 아니다 — 기록만.

## 🔴 새 관측 — 검색 색인에 시드 상품이 거의 없다

ES `products` 인덱스 `docs.count=3` · 상품 24개 전부 `product not found in search index` ⇒ `suspectedDrift=24`. 727 의 주소 문제와 **무관**하다(잡은 product-service 를 읽었다).
데모 시드 상품이 색인 경로(이벤트 → search-service)를 타지 않는 것으로 보인다 — ⚪ 원인 미확인, 별도 티켓 후보.

---

# Goal

«스택이 떠야만 잴 수 있어서 지금 못 재는» 측정 항목의 **집**이 된다. 새 일을 만들지 않고,
닫히는 티켓이 들고 있던 남의 의무를 **넘겨받는다.**

---

# Scope

## 포함

- 다른 티켓이 남긴 «창이 필요한» ⚪ 를 **항목으로 받는다**(출처 티켓을 이름으로 인용).
- 창이 열릴 때 그 항목들을 **한 번에** 수확한다.

## 제외

- 🔴 **이 티켓만을 위해 데모를 켜지 않는다.** 645 의 Failure 1 이 그대로 적용된다.
- 🔴 **여기서 결함을 고치지 않는다.** 이 티켓은 «재는 것» 이 일이고, 결함이 나오면 별도 티켓이다.
- 🔴 **AMI 재굽기가 필요한 항목** — 그건 다른 축이다(승인 사항). 항목에 그 사실을 적고 받되,
  창만으로 풀린다고 적지 마라.

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [ ] 🔴 **창이 이미 열렸는가를 먼저 확인한다.** 소유자가 다른 이유로 데모를 켰는가?
      아니라면 **STOP** — no-op 이 올바른 구현이다.
- [ ] 🔴 **항목이 하나라도 있는가.** 없으면 **STOP** — 빈 집인 것이 정상이다.
- [ ] 예산을 **다시 잰다**(`/status` 의 `used_minutes` / `budget_minutes`). 🔴 티켓에 적힌
      어떤 수치도 그대로 믿지 마라 — 645 가 세 번 다 낡은 값을 들고 있었다.

## AC-1 — 항목마다 «아직 유효한가» 를 다시 묻는다

- [ ] 🔴 각 항목은 «그때 안 쟀다» 이지 «지금도 못 잰다» 가 아니다. 그 사이 코드가 바뀌어
      재현 조건이 사라졌을 수 있다 ⇒ **그러면 그 사실을 적고 항목을 닫는다.**
- [ ] 🔴 «창으로 풀리는가» 를 항목마다 확인하라. 🔵 645 의 분개 8칸이 반례다 —
      **탐색 경로가 없어서 창을 열어도 영영 못 쟀다.**

## AC-2 — 수확

- [ ] 잰 결과를 이 티켓의 § Verification 에 적는다.
- [ ] 🔴 결함이 나오면 **별도 티켓**이다. 여기서 고치지 말고 기안하라.
- [ ] 🔴 못 잰 항목이 남으면 **⚪ 로 «왜 못 쟀는지»** 를 적고, **어디로 갔는지**도 적어라.

## AC-3 — 🔴 이 티켓이 닫힐 때도 **집을 남겨라**

- [ ] 🔴🔴 이 티켓이 자기 항목을 다 비워서 닫히게 되면, **그 전에 후속 집을 기안하라.**
      645 → 672 가 그 선례다. 🔵 집이 사라지는 순간이 의무가 사라지는 순간이다.

---

# Related Specs / Contracts

- `TASK-MONO-645` — 🔴 **이 티켓의 전신.** 그 티켓의 AC-6 이 «집이 없으면 의무가 사라진다» 를
  실측으로 보여 준다(`TASK-MONO-656` 이 이름으로 지목하지 않아 `645`·`633` 둘 다 grep 0건이었다)
- `TASK-MONO-537` — 그 모양으로 9일이 사라진 사례(645 가 인용)
- `TASK-MONO-668` — 「켜지는 중」. 🔵 창이 필요하지만 **자기 티켓이 있다** ⇒ 여기 항목이 아니다
- `TASK-MONO-660` · `667` · `648` — 창/재굽기가 필요한 현재 티켓들. 🔴 **각자 살아 있으므로
  여기로 옮기지 마라** — 이 집은 «티켓이 닫히면서 집을 잃는» 항목만 받는다

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 항목이 0개인 채로 오래 간다 | 🟢 **정상이다.** 집은 비어 있어도 집이다 |
| 항목이 창으로 안 풀리는 것으로 밝혀짐 | 🔴 ⚪ 로 적고 **사유를 「창이 없었다」가 아닌 진짜 사유**로 적어라 |
| 창이 열렸는데 예산이 빠듯하다 | 항목 간 우선순위를 적고, 못 한 것은 ⚪ 로 남긴다 |
| 어떤 항목이 AMI 재굽기를 요구한다 | 🔴 창만으로는 안 풀린다. 그 사실을 항목에 적어라 |

---

# Failure Scenarios

1. 🔴🔴 **이 티켓만을 위해 EC2 를 켠다** → 645 Failure 1 그대로. 예산을 쓴다.
2. 🔴 **살아 있는 티켓의 ⚪ 를 여기로 옮긴다** → 그 티켓이 자기 의무를 잃는다. 이 집은
   **닫히는 티켓**의 의무만 받는다.
3. 🔴 **빈 것을 결함으로 읽고 항목을 만들어 채운다** → 새 일을 만드는 티켓이 아니다.
4. 🔴🔴 **이 티켓이 닫히면서 후속 집을 안 남긴다** → 645 가 막으려던 그 일이 다시 일어난다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 재는 일이다. 🔴 다만 AC-1 의 «창으로 풀리는가» 판정은
기계적이지 않다(645 의 분개 8칸이 그 반례다).

## ✅ 항목 6 (닫힘 2026-09-22 — ①② 둘 다) — `TASK-MONO-705`: refresh 응답의 **본문 키 목록**과, 수정 후 **창 재판정** (2026-09-18 수령)

`TASK-MONO-705` 가 `done` 으로 닫히면서 넘긴 둘이다. 🔴 **AMI 재굽기가 선행**한다 — 백엔드
(`auth-service`) 변경이므로 현재 핀(`af0018aa6`)으로는 이 측정이 **옛 코드를 잰다**.

**① 응답 본문의 키 목록** — 705 의 AC-0 (1) 이 못박은 방법이고, 그 티켓은 **코드 독해 + 쿠키
부재 + 단위 단언**으로 답했다(셋 다 서로를 지지하지만 **엔드포인트 본문을 뜬 것은 아니다**).
`POST /oauth2/token` `grant_type=refresh_token` 응답의 **키 이름만** 찍는다(🔴 값 출력 금지).
기대: `access_token` · `token_type` · `expires_in` · `refresh_token` · `scope` · **`id_token`**.

**② 수정 후 창 재판정** — 로그인 → 31분 유휴 → 갱신 → 로그아웃 → 다시 «로그인».
🔴🔴 **대조군 없이 하면 또 교란된다.** 2026-09-18 창이 정확히 그랬다: 31분 뒤 IAM 폼이 떠서
«무해» 로 보였는데, 실은 **IAM 세션이 스스로 만료**한 것이었고 결함은 살아 있었다. 그래서
재판정은 **두 칸을 같이** 잰다:
  ⓐ 31분 유휴 경로 — 이제 갱신 뒤 `console_id_token` 이 **다시 서는가**(이것이 수정의 직접 판정)
  ⓑ 갓 로그인 세션에서 `console_id_token` 만 지우고 로그아웃 → 다시 로그인 —
     **여전히 비밀번호 없이 들어가는가**(들어가면 그건 정상이다: 쿠키를 지운 건 나다.
     이 칸은 ⓐ 의 «세션이 살아 있었는가» 를 세우는 **유효성 술어**로 쓴다)
🔵 판정 술어는 «비밀번호 칸 개수» 가 **아니라** «`console_access_token` 이 다시 섰는가» 다 —
705 에서 앞엣것으로 물었다가 정반대 판정을 찍었다.


🔵 **2026-09-18 보강 — 유휴 31분이 필요 없다.** 그날 창에서 쓴 **주입 형태**로 3분이면 된다:
갓 로그인 → `console_access_token` 만 지워 갱신을 강제 → 갱신 응답에 `id_token` 이 오는가 →
`console_id_token` 쿠키가 **다시 서는가**. 유휴 31분은 «원래 결함을 재는» 방법이었지 수정 확인의
방법이 아니다. 🔴 판정 술어는 «비밀번호 칸 개수» 가 **아니라** «세션 쿠키가 다시 섰는가» 다.

## 🔴 다음 창 런북 (2026-09-18 갱신)

2026-09-18 둘째 창(17분)이 **분모는 만들고 분자를 못 읽은** 채 끝났다. 원인은 하나다 —
`aws ssm send-command` 가 자동 모드 분류기에 막혀 **소유자만 실행할 수 있고**, 그 출력이
세션 안에 돌아오지 않았다. 다음 세션이 같은 것을 다시 유도하지 않도록 **명령 전문**을 여기 둔다.

### 순서 (부팅 직후 ①, 트래픽 뒤 ②)

**① 읽기 전용 — 675 · 706 을 한 번에** (wms 묶음이 `ready` 면 즉시)

```bash
aws ssm send-command --region ap-northeast-2 \
  --instance-ids <INSTANCE_ID> \
  --document-name AWS-RunShellScript \
  --parameters 'commands=["echo == 675 master dlq ==","for t in warehouse zone location sku partner lot; do docker exec wms-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic wms.master.$t.v1.dlq; done","echo == 706 shipping DLT ==","docker exec wms-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic wms.outbound.shipping.confirmed.v1.DLT","echo == 706 record ==","docker exec wms-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic wms.outbound.shipping.confirmed.v1.DLT --from-beginning --max-messages 1 --timeout-ms 15000 --property print.headers=true"]' \
  --query 'Command.CommandId' --output text
```

**② 697 — 🔴 트래픽을 먼저 돌린 뒤에** (분모가 없으면 «불일치 0» 은 미측정이다)

```bash
aws ssm send-command --region ap-northeast-2 \
  --instance-ids <INSTANCE_ID> \
  --document-name AWS-RunShellScript \
  --parameters 'commands=["for g in ecommerce wms scm erp finance fan; do c=$(docker ps --format {{.Names}} | grep -E \"^${g}.*gateway|gateway.*${g}\" | head -1); echo \"== $g -> ${c:-<컨테이너없음>}\"; [ -n \"$c\" ] || continue; echo -n \"  mismatch WARN 줄수: \"; docker logs \"$c\" 2>&1 | grep -c \"JWT audience not on allowlist\"; docker exec \"$c\" sh -lc \"curl -s localhost:8080/actuator/prometheus 2>/dev/null | grep gateway_jwt_audience_total || echo (prometheus 미노출)\"; done"]' \
  --query 'Command.CommandId' --output text
```

출력 회수(둘 다):

```bash
aws ssm get-command-invocation --region ap-northeast-2 \
  --instance-id <INSTANCE_ID> --command-id <CommandId> \
  --query 'StandardOutputContent' --output text
```

### 🔴 다음 창에서 반드시 다르게 할 것 (2026-09-18 에 비싸게 배운 셋)

1. **트래픽에 `fan` 을 넣어라.** 콘솔은 fan 도메인을 안 그린다 ⇒ 콘솔만 돌면 fan 게이트웨이는
   **분모 0 = 미측정**이다. fan 웹 로그인 한 번이 필요하다.
2. **일회용 측정 스크립트에도 «테넌트 적용» 판정을 복사해 넣어라.** `TASK-MONO-707` 이 고친
   것은 `capture-portfolio.mjs` 이고, 그날 쓰는 임시 스크립트는 그 교훈을 **안 물려받는다**.
   판정 = `셀렉트값 == 요구값 && «테넌트를 선택» 문구 부재`, 실패하면 다른 테넌트 **경유** 후 재시도.
   2026-09-18 에 이 판정이 없어 한 측정을 통째로 버렸다.
3. **부팅이 묶음 수에 비례한다.** 7묶음 = **14분**(6묶음은 8분). 상한을 정할 때 이것부터 빼라.

### 🔴 항목 5(`TASK-MONO-683` AC-4)는 «측정» 이 아니라 «선행을 만드는 일» 이었다

2026-09-18 창에서 `/scm/replenishment` 가 테넌트 적용 상태로 **보충 추천 0건**이었다. 화면이
이유를 말한다 — 추천은 **wms 저재고 알림 / IVS 야간 스윕**에서만 생기고 신선 볼륨에는 그
트리거가 없다. ⇒ **다음 창에서 그냥 다시 봐도 0건이다.** 이 항목을 열려면 먼저 ⓐ 시드가
저재고를 만들거나 ⓑ 스윕을 수동 트리거해야 하고, **그 선행은 아직 아무 티켓도 안 들고 있다.**

### 🔵 다음 창에서 **한 줄만** 확인할 것 (`TASK-MONO-711` ③ 이 남긴 확인 사살)

`TASK-MONO-711` ③ 이 `/console` 의 도메인 상태 실패를 마커(`catalog-health-unavailable`)로
드러냈다. 그 사슬의 마디는 단위 테스트로 각각 쟀지만 **촬영 매니페스트에서의 최종 판정**만
창이 없어 못 쟀다. ⇒ 다음 촬영 후 매니페스트에서 한 줄:

```bash
node -e "const m=require('<manifest>.json');const s=m.shots.find(x=>x.path==='/console');console.log(s.degraded, s.degradedBy)"
```

기대: `true [ 'catalog-health-unavailable' ]`. 🔴 `false` 면 마커가 렌더되지 않은 것이므로
**`TASK-MONO-711` 을 다시 열어라** — 단위 테스트가 초록인데 화면에 없으면 그 사이(빌드·배포·
라우트)가 범인이다. 🔵 `true` 는 «화면이 나빠졌다» 가 아니라 «이전 «저하 아님» 이 오보였다» 다.

---

## ✅ 항목 7 (닫힘 2026-09-22 — 결함 확정, 후속 티켓으로) — `TASK-MONO-713` 이 넘긴 것: `ACCOUNT_SERVICE_BASE_URL` 이 **어디에도 설정되지 않는다** (2026-09-18 UTC 수령)

`TASK-MONO-713` 의 AC-0 인바운드 모집단 측정 중 나온 **곁발견**이다. 713 의 범위 밖이지만 관측은
진짜이고, **판정에 창이 필요해서** 여기로 왔다(713 은 `done/` 으로 닫히므로 거기 두면 안 읽힌다).

**관측**: ecommerce `product-service` 의 셀러 프로비저닝
(`AccountServiceSellerProvisioner` — `POST /internal/tenants/{t}/accounts` ·
`…/identities:resolveOrCreate` · `PATCH …/accounts/{id}/status`)이 base URL 로
`${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}` 을 읽는데, **그 환경변수를 설정하는
compose / env / override 가 저장소에 하나도 없다**(`infra/**` · `projects/**` 전수).

**왜 조용한가**: 이 호출은 **fail-soft** 다(ADR-MONO-042 D3 — account-service 가 응답하지 않아도
셀러 등록 자체는 진행된다). ⇒ 실패해도 **아무 화면도 빨개지지 않는다**. 「안 보이는 고장」의 모양.

🔴 **창에서 물을 것 (한 줄이면 된다)**: 데모에서 셀러를 하나 만들고 —

1. `product-service` 로그에 그 프로비저닝 호출의 **결과**가 무엇으로 찍히는가(성공 / 타임아웃 /
   connection refused). 🔵 `localhost:8081` 은 컨테이너 **자기 자신**이므로 refused 가 예상이지만,
   **예상은 측정이 아니다**.
2. `account_db` 에 그 셀러-운영자 계정 행이 **실제로 생겼는가**. 🔴 이쪽이 진짜 판정이다 — 로그가
   조용해도 행이 없으면 고장이고, 행이 있으면 어딘가로 정상 도달하고 있다는 뜻이다.

🔵 **결함으로 나오면 별도 티켓**이다(AC-2 규율). 이 항목은 «재라» 까지다.
🔵 **곁가지**: 713 의 갈래 ⓑ(「`/internal/tenants/**` 로 좁혀 rule 5 적용」)를 언젠가 고르면
**그 배선이 선행**이다 — 지금은 그 경로가 게이트웨이를 지나지 않기 때문이다. 즉 이 항목은
713 의 면제가 만료되는 경로이기도 하다.

---

# 🟢 수확 — 항목 3 닫힘 (2026-09-18 UTC · 창 «열지 않고» 쟀다 · 분석=Opus 5)

## AC-0 착수 게이트

| 칸 | 실측 |
|---|---|
| 창이 이미 열렸는가 | **아니다** — `GET /status` = `stopped`. ⇒ 소유자가 다른 이유로 켠 것이 아니므로 STOP 조건 아님 |
| 항목이 있는가 | **있다** — 7항목 |
| 예산을 **다시** 쟀다 | 🔴 **`used 1170 / budget 1200` — 남은 것이 30분**. 티켓 어디에도 이 수치는 없었다(AC-0 이 «어떤 수치도 믿지 마라» 고 한 이유) |

🔴 **그리고 SSM 이 지금도 막힌다** — 런북의 기록이 낡았을 수 있어 **꺼진 인스턴스로 시험**했다
(예산 0 소모): `aws ssm send-command` 는 여전히 자동 모드 분류기에 막힌다. ⇒ 675 · 706 · 697 ·
항목 7 은 **이 세션이 읽을 수 없다**(전부 `docker exec` / 컨테이너 로그 경유).

⇒ **전략**: 30분을 부팅에 태우지 않고, **항목 3 만** 닫는다. 그 항목은 티켓 자신이
*"창만으로 풀린다 — 그리고 **창을 일부러 열 필요도 없다**. 필요한 사건은 «꺼진 상태의 첫 클릭» 뿐"*
이라고 적어 뒀다.

## 항목 3 — `TASK-MONO-685` AC-7 (a): 🟢 **PASS**

**측정 (제어 API, HTTP 만):**

| 시점 | `selection` |
|---|---|
| **클릭 전** (`GET /bundles`, 인스턴스 `stopped`) | `console, console-ecommerce, console-erp, console-finance, console-scm, console-wms, fan` — **7묶음** |
| **클릭** (`POST /bundle/start {"bundles":["fan"]}`) | 200 · 응답 본문이 `"selection": ["fan"], "started": ["fan"]` |
| **클릭 직후** (`GET /bundles`, **경과 2초**) | 🟢 **`fan`** — 하나 |

🔴 **이것이 왜 강한 판정인가**: 기준선이 **빈 선택이 아니라 7묶음**이었다. 즉 «지난 세션의 묶음이
남아 있는» 상태가 **실제로 재현돼 있었고**, 첫 클릭이 그것을 **합집합이 아니라 교체**로 처리했다.
티켓이 적은 FAIL 조건(*"지난 세션의 묶음이 남아 있으면 FAIL"*)의 정확한 반대다.

🔵 **120초 창을 밟지 않았다**: 티켓이 *"그 클릭 직후 120초 안에 두 번째 카드가 눌리면 설계상
합집합"* 이라고 경고했다. 읽기까지 **2초**였고 두 번째 클릭은 **하지 않았다**.

⚪ **대조군(«켜진 뒤 다른 카드를 누르면 더해지는가»)은 안 쟀다.** 그것은 인스턴스를 **띄운 채**
두 번째 클릭을 해야 하고 ⇒ 부팅 4~10분을 예산에서 빼야 한다. **남은 30분 중 그만큼을 쓰는
판단은 소유자 것**이라 하지 않았다. 🔵 그 축은 코드 수준 증거가 이미 있다
(`test_cold_start_replaces_the_previous_sessions_selection`, 685).

## 💰 예산 — 이 측정이 쓴 것: **0분**

`used_minutes` 가 측정 전후 모두 **1170** 이다(반올림 단위 미만). 순서: `stopped` 확인 → 클릭 →
2초 뒤 읽기 → 즉시 `POST /stop` → `stopping` → **`stopped` 재확인**. 🔵 즉 **이번 달 마지막 30분은
그대로 남아 있다** — 재굽기 뒤의 창에 쓸 수 있다.

## 🔴 남은 항목들의 상태 (AC-1: «아직 유효한가» 재확인)

| 항목 | 이번 세션 판정 |
|---|---|
| **3** | 🟢 **닫힘** (위) |
| 1 · 2②③ · 5 | 🔴 **재굽기 전엔 판정 불가** — 티켓이 각 항목에 이미 명시. 창을 열어도 안 풀린다 |
| 2① | 🟡 창만으로 풀리지만 **부팅이 선행** (이번엔 안 열었다) |
| 4 | 🟡 «창만으로 풀리는지 미확인» 그대로 — 게다가 외부 테넌트 토큰을 **일부러 만들어야** 한다 |
| 6 · 7 · (675 · 706 · 697) | 🔴 **SSM 필요 — 이 세션이 못 읽는다.** 소유자가 실행해야 한다 |

🔴 **그러므로 다음 창의 순서가 바뀐다**: 「창을 열어 측정한다」가 아니라 **「재굽기 → 창」** 이다.
재굽기 전에 창을 열면 위 표의 🔴 4항목이 **또** 미측정으로 남고, 예산만 준다.

---

# 🔴🔴 재굽기 준비 표 — «재굽기가 정확히 무엇을 사는가» (2026-09-18 UTC · 분석=Opus 5)

이 표는 **굽기 전에** 만들어졌다. 이 저장소가 이름 붙인 함정이 *"두 번 굽는 것이 가장 비싼 실수"* 이고, 그 실수는 «무엇을 사는지 안 세고 굽는 것»에서 나온다. 아래는 전부 **파일과 라이브 API 에서 읽은 값**이다(추정 아님).

## 0. 기준선 — 지금 배포된 것

| | 값 | 출처 |
|---|---|---|
| 구운 세대 | **`af0018aa6`** (12차) | `infra/demo/aws/deployed-ami.env` |
| AMI | `ami-02613b0378621b124` / `portfolio-demo-1789658583` | 같은 파일 |
| 굽기 시작 | `2026-09-17T15:23:03Z` | 같은 파일 |
| provenance | 🔴 **`operator-record`** — 사람이 적었다, 이미지가 스스로 한 말이 **아니다** | 같은 파일(11차 굽기가 `unexpected EOF` 로 죽어 태그 발행 전이었다) |
| 라이브 상태 | `stopped` · `used 1170 / budget 1200` | 제어 API `GET /status`, 2026-09-18 UTC 실측(예산 **0분** 소모) |

## 1. 🔴 예산 인상은 **저장소에만 있고 배포되지 않았다**

`5edf53592`(소유자 결정 2026-09-18)가 `monthly_budget_minutes` 를 **1200 → 1800** 으로 올렸다. 그런데 방금 잰 라이브 값은 **여전히 1200** 이다.

- 그 변수는 `main.tf:340` 에서 Lambda 환경변수 `MONTHLY_BUDGET_MINUTES` 로 들어간다 ⇒ **`terraform apply` 전까지 Lambda 에 도달하지 않는다.**
- ⇒ **지금 열 수 있는 창은 30분이다**(1200 − 1170), 630분이 아니다. 🔴 1800 을 가정하고 계획하면 **30분 뒤 `/start` 가 429** 를 낸다.

## 2. 재굽기가 **사는 것** — 구운 세대 이후 «구워지는 표면» 을 건드린 커밋

`af0018aa6..origin/main` 은 **33 커밋**이고, 그중 백엔드·시드 파일을 건드린 것은 **여섯**이다:

| 커밋 | 티켓 | 무엇이 데모에 도달하나 | 그래서 열리는 판정 |
|---|---|---|---|
| `366d7908d` | 710 | 이커머스 시드가 주문을 **실제 API** 로 만든다 | **710 AC-2** · 648 의 이커머스 동적 경로 |
| `28bb47056` | 705 | refresh 그랜트가 `id_token` 을 다시 낸다 | **672 § 항목 6**(응답 본문 키 + 창 재판정) |
| `3cc659375` | 712 | console-bff 가 엣지로서 `aud` 를 본다(403) | ~~콘솔 운영자 경로의 회귀 여부~~ **대상 은퇴 — `TASK-MONO-757`**(2026-10-02 UTC). 그 엣지 자체가 저장소에서 지워져 잴 대상이 없다 |
| `880e59f20` | 714 | order-service 의 fail-open 검증기 삭제 | 이커머스 인증 경로 회귀 여부 |
| `a9bd9bd7b` | 716 | iam `admin-service` `/internal/**` 요구 scope | iam 내부 호출 경로 회귀 여부 |
| `baca8c4ce` | 295 | 🔵 **런타임 변화 0** — 이 커밋의 java 파일은 **테스트 하나**뿐이다 | — (아래 § 4) |

> 🔵 **2026-09-25 UTC 추가 (위 표는 09-18 굽기의 기록 — 이 줄은 그 표의 행이 아니다)** — `TASK-MONO-734` 가 ecommerce
> `minio`·`minio-init` 의 `image:` 를 `bitnamilegacy` → `ghcr.io/kanggle/mirror-*` 로 바꿨다(compose + k8s). **digest 가 같다**
> (미러 run `36100595693` 이 복사 후 대조) ⇒ **이것 때문에 굽을 이유는 없다**. 다음 굽기에서 packer 가 새 주소로 pull 하고,
> 이미 구운 AMI 는 같은 바이트를 옛 주소로 들고 있을 뿐이다. 🔴 단, 다음 굽기의 pull 이 GHCR 에 닿는지는 그 굽기가 처음 잰다.

## 3. 🔴🔴 재굽기가 **사지 않는 것** — 항목 1·2②③ 은 «코드» 를 기다리는 게 아니다

티켓은 항목 1·2②③ 을 «재굽기 전엔 판정 불가» 로 적었다. **그 이유를 다시 쟀고, 티켓이 적은 것과 다르다**:

- `TASK-MONO-679` 의 시드 변경(`2c3c494b0`)과 `TASK-MONO-683` 의 시드 변경(`a7a8f4e67`)은 **둘 다 이미 `af0018aa6` 의 조상**이다 — `git merge-base --is-ancestor` 로 확인했다. ⇒ **코드는 이미 구워져 있다.**
- 그리고 **AMI 는 시드된 DB 를 담지 않는다** — packer 템플릿이 그렇게 적는다: *"프로젝트 .env 는 gitignored 라 fresh clone 에 없다. demo.env 가 값을 제공한다(MONO-346). **여기서 별도 seeding 을 하지 않는 이유다.**"* 굽기는 도커 **이미지 레이어**만 만든다.

⇒ 그러면 «사진 없는 옛 글» 은 어디 있나. **stop/start 를 건너 살아남는 인스턴스의 볼륨**이다. `publish_artist_post` 가 **제목으로** «이미 있음» 을 판단하므로, 그 행이 남아 있는 한 몇 번을 구워도 새 시드는 건너뛴다.

🔵 **그래서 이 항목들이 실제로 기다리는 것은 «새 AMI» 가 아니라 «신선 볼륨» 이다.** `main.tf:148` 의 `aws_instance.demo.ami = var.ami_id` 는 Terraform 에서 **교체를 강제하는 속성**이므로, 새 AMI id 로 `apply` 하면 인스턴스가 교체되고 루트 볼륨이 비어 시드가 처음부터 돈다.

🔴 **이 마지막 한 줄은 추론이다 — 실측이 아니다.** 이 세션은 AWS 자격증명으로 `terraform plan` 을 돌릴 수 없다. **apply 전에 plan 출력에서 `must be replaced` 를 눈으로 확인하라.** 교체가 아니라 in-place 로 잡히면 신선 볼륨을 못 사고, 항목 1·2②③ 은 **또** 미측정으로 남는다(= 두 번 굽기).

## 4. 🔵 재굽기가 **필요 없는** 남은 판정들

| 판정 | 왜 재굽기 불필요 |
|---|---|
| **`TASK-PC-FE-295` AC-0 ②**(운영자 개요 카드 셋에 값이 보이는가) | 고친 것은 **console-web** 이고 콘솔 프런트는 **Vercel** 이라 구워지지 않는다. bff 는 이 티켓에서 **테스트만** 바뀌어 런타임이 동일하다 ⇒ **창만 열면 된다.** |
| **§ 항목 2①**(홈 팔로우 피드) | 티켓 자신이 «창만으로 풀린다(재굽기 불필요)» 라고 적었다 |
| **§ 항목 4**(테넌트 불일치 403) | 창 + **외부 테넌트 토큰을 일부러 만드는** 일. 재굽기와 무관 |
| **§ 항목 3** | ✅ 이미 닫혔다(2026-09-18, 예산 0분) |

## 5. ⇒ 순서 제안 (소유자 몫)

1. **`terraform plan` 을 읽는다** — 🔵 **한 번의 `apply` 가 둘을 산다**: 새 AMI(→ 인스턴스 교체 → 신선 볼륨)와 **예산 1800**. 🔴 plan 이 인스턴스 교체를 보이지 않으면 거기서 멈춘다.
2. **굽는다** — 굽기 뒤 `deployed-ami.env` **커밋까지가 절차다**(그 파일이 그렇게 적는다). 🔵 `TASK-MONO-709` 가 «등록 뒤 죽으면 이미지를 구조한다» 를 넣었으므로 11차 같은 실패는 덜 아프다.
3. **apply** — 인스턴스 교체 + 예산 반영.
4. **창을 연다** — 그때의 예산은 1800 − 1170 = **630분**(apply 가 됐다면). 🔴 apply 전이면 **30분**이다.
5. 창 안 순서: § 다음 창 런북의 ①(읽기 전용 SSM, wms `ready` 되자마자) → 트래픽(🔴 **fan 로그인 포함** — 콘솔만 돌면 fan 게이트웨이는 분모 0) → ②(697) → 항목 1·2·4·5·6 → 🔵 `TASK-PC-FE-295` AC-0 ②(카드 셋 한 눈).

## 6. 🔴 이 표가 **안 잰 것**

- **`terraform plan` 출력** — 자격증명 없음(위 § 3 의 추론이 여기 걸려 있다).
- **인스턴스 볼륨에 실제로 옛 행이 있는지** — 읽으려면 SSM 이고, SSM 은 이 세션에서 **여전히 분류기에 막힌다**(2026-09-18 에 꺼진 인스턴스로 재시험했고 예산 0분 소모). 즉 § 3 의 진단은 **티켓의 기록 + 코드**로 세운 것이지 볼륨을 열어 본 것이 아니다.
- **굽기 시간·비용** — 이 표는 «무엇을 사는가» 만 센다.

---

# 🟢 위 표의 ⚪ 를 닫는다 — `terraform plan` 을 **실제로 돌렸다** (2026-09-18 UTC · 분석=Opus 5)

## 🔴 먼저, 위 표가 «못 돌린다» 고 적은 것은 **확인이 아니라 가정**이었다

위 § 6 은 *"terraform plan 출력 — 자격증명 없음"* 이라고 적었다. **자격증명을 확인한 적이 없다.** 실제로 물어보니 있다:

```
aws sts get-caller-identity
  → arn:aws:iam::248508119468:user/portfolio-demo-deployer
```

🔵 이 저장소가 이름 붙인 규율 그대로다 — **«막힌다» 는 표가 아니라 시도가 정한다.** SSM 이 막힌다는 사실이 *AWS 전체가 막힌다* 로 번진 것이고, 그래서 답할 수 있는 질문을 ⚪ 로 남겼다.

## 🟢 ⚪ → 측정: **새 AMI 는 인스턴스를 교체한다**

```
terraform plan -refresh=false -var 'ami_id=<다른 값>'
  # aws_instance.demo must be replaced
  ~ ami = "ami-02613b0378621b124" -> "<다른 값>" # forces replacement
  Plan: 1 to add, 2 to change, 1 to destroy.
```

⇒ 위 § 3 의 **추론이 측정이 됐다.** 새 AMI id 로 apply 하면 인스턴스가 교체되고 루트 볼륨이 비어, 시드가 처음부터 돈다 ⇒ **항목 1·2②③ 이 기다리는 신선 볼륨을 apply 가 산다.**

🔵 plan 이 건드리는 자원은 **셋**이다:

| 자원 | 동작 | 뜻 |
|---|---|---|
| `aws_instance.demo` | **replace** | 신선 볼륨 |
| `aws_lambda_function.control` | in-place | 예산 `1200 → 1800` |
| `aws_iam_role_policy.lambda` | in-place | 🔴 아래 |

## 🔴 새로 드러난 함정 — IAM 정책이 **인스턴스 id 를 박고 있다**

정책의 `Resource` 가 `arn:aws:ec2:*:248508119468:instance/i-07ddb6b41233f2673` 로 **현재 인스턴스를 지목**한다. 교체되면 id 가 바뀌므로 정책도 같이 바뀌어야 Lambda 가 `/start`·`/stop` 을 할 수 있다.

🔵 **정상 경로는 안전하다** — 같은 apply 가 둘을 함께 바꾼다(plan 이 그렇게 보인다). 🔴 **위험한 것은 우회 경로다**: 인스턴스를 terraform 밖에서 갈거나 apply 를 중간에 끊으면, **Lambda 가 새 인스턴스를 켤 권한을 잃는다** — 증상은 «`/start` 가 되는데 안 켜진다» 쪽이라 원인이 안 보인다.

## 🔵 예산 apply 는 **이미 준비돼 있다**

`infra/demo/aws/terraform/budget-1800.tfplan` (2026-09-18 17:38 KST — 머지 **2분 뒤**)가 저장돼 있고 내용은:

```
# aws_lambda_function.control will be updated in-place
  ~ "MONTHLY_BUDGET_MINUTES" = "1200" -> "1800"
Plan: 0 to add, 1 to change, 0 to destroy.
```

⇒ 소유자가 **plan 까지 만들고 apply 를 안 했다.** 🔴 그 plan 은 **인스턴스를 안 건드린다**(당시 `ami_id` 가 그대로였으므로). 즉 그것만 apply 하면 예산은 1800 이 되지만 **신선 볼륨은 안 산다** — 두 목적을 한 apply 로 묶으려면 **굽기 뒤 `ami_id` 를 바꾼 새 plan** 이어야 한다.

## 🔵 핀 ↔ 이미지 대조 — 저장소의 검사기로 확인했다

`bash infra/demo/aws/check-ami-generation.sh --with-aws` → **rc=0**

- AMI 태그 `RepoCommit = af0018aa6504` — 핀과 **일치**
- 인스턴스가 물고 있는 AMI = `ami-02613b0378621b124` — 핀과 **일치**

🔴 **그래도 provenance 는 `operator-record` 그대로다.** 11차 굽기가 태그 발행 전에 죽어 **태그도 사람이 붙였기** 때문이다 ⇒ «핀과 태그가 일치한다» 는 **독립 확인이 아니라 같은 사람의 두 기록이 같다**는 뜻이다. 검사기 자신이 그렇게 말한다(*"이 값은 이미지를 읽은 것이 아니라 사람이 적은 것"*).

## 🔴🔴 그리고 그 검사기의 초록을 **이렇게 읽으면 안 된다**

검사기는 `✔ 같은 세대 — 계약 파일이 전부 일치합니다` 로 **rc=0** 을 낸다. **그것은 «재굽기가 살 것이 없다» 가 아니다.**

- 그 술어가 비교하는 것은 **계약 파일 6개**(코어 3 + 억제 오버레이)다 — «데모의 계약이 어긋났나» 를 묻는다.
- 이 표가 묻는 것은 **«재굽기가 어떤 새 동작을 데모에 싣나»** 다. 시드·백엔드 변경은 그 6개 파일에 없으므로 검사기는 **원래 안 본다**.
- 실제로 검사기 자신이 *"(참고) `infra/demo/` 전체로는 **5개 파일이 다릅니다** — 판정에는 안 넣습니다"* 라고 적는다.

⇒ **`710 AC-2` 는 검사기가 초록인 채로도 여전히 재굽기를 기다린다.** 두 질문이 다르고, 초록 하나로 다른 질문에 답하면 틀린다.

## ⇒ § 5 순서의 갱신

1. ~~`terraform plan` 을 읽는다~~ → 🟢 **읽었다.** 교체는 확정이다.
2. **굽는다** → `deployed-ami.env` 커밋까지가 절차.
3. **`ami_id` 를 새 AMI 로 둔 plan 을 새로 만들어 apply** — 🔴 `budget-1800.tfplan` 을 그대로 쓰면 **예산만** 사고 신선 볼륨은 못 산다.
4. **창** — apply 가 됐다면 예산은 `1800 − 1170 = 630분`.

## 🔴 여전히 안 잰 것

- **인스턴스 볼륨에 실제로 옛 행이 있는지** — SSM 이 필요하고 이 세션에서 막힌다. § 3 의 진단은 티켓 기록 + 코드 + packer 주석으로 세운 것이지 볼륨을 연 것이 **아니다**. 🔵 다만 이제 «교체되면 볼륨이 빈다» 쪽은 측정됐으므로, 틀릴 수 있는 곳은 «지금 볼륨에 옛 행이 있다» 한 군데로 좁혀졌다.
- **굽기 시간·비용.**

---

# 🟢 굽기 사전점검 — **돈을 쓰기 전에** 실패할 이유를 찾았다 (2026-09-22 UTC · 분석=Opus 5)

굽기는 **~55분 + AWS 과금**이고 `bake.sh` 가 *"소유자가 명시적으로 지목했을 때만 돌려라. 에이전트가 스스로 시작하지 않는다"* 라고 못박았다 — **굽지 않았다.** 대신 **굽기 없이 확인 가능한 모든 관문**을 돌렸다.

## 🟢 예산은 이제 문제가 아니다 (2026-09-22 apply 완료)

| | 전 | 후 |
|---|---|---|
| `budget_minutes` | 1200 | **1800** |
| `used_minutes` | 1170 | 1170 (그대로) |
| **쓸 수 있는 분** | 30분 | 🟢 **630분** |

소유자가 `terraform apply budget-1800.tfplan` 을 실행했다(`0 added, 1 changed, 0 destroyed`). 🔴 **그 plan 은 소진됐으므로 삭제했다** — 남겨 두면 다음 사람이 «아직 대기 중인 변경» 으로 읽는다. 🔴 그리고 그 apply 는 **인스턴스를 안 건드렸다**(Lambda 환경변수만) ⇒ **신선 볼륨은 아직 안 샀다.**

🔵 `terraform apply` 는 **자동 모드 분류기에 막힌다**(2026-09-22 실측). 축은 «AWS 를 만지는가» 가 아니라 **«상태를 바꾸는가»** 다 — `plan`·`show`·`aws ec2 describe-*`·`sts get-caller-identity` 는 전부 통과한다. 🔴 같은 결과를 `aws lambda update-function-configuration` 으로 낼 수 있지만 **하면 안 된다**: 차단 의도를 우회하는 것이고, terraform 상태와 실물이 갈라져 **다음 apply 가 그 변경을 되돌린다**.

## 관문 — 전부 통과

| 관문 | 결과 |
|---|---|
| `bake.sh --dry-run` | **rc=0** · 굽을 커밋을 **origin 에서** `8a404a95a` 로 해석(로컬 아님) |
| `bake.sh --self-test` | **7/7** — 구조 판정 술어 4종 |
| `packer` 설치 | **v1.15.4** |
| `packer init .` | **rc=0** |
| `packer validate -var repo_ref=main -var repo_commit=8a404a95a… ` | **rc=0** — *"The configuration is valid."* |

🔵 **`validate` 가 값싼 이유**: 12차 굽기는 **37분 55초**를 태운 뒤에야 죽었다. 템플릿·플러그인·변수 오류를 그 전에 잡는 유일한 관문이다.

## 🟢 고아 빌더 없음 — 독립적으로 확인했다

`terraform.tfvars` 는 12차의 고아 빌더(`i-054b8d179af391052`, c6i.4xlarge)·packer SG·키페어를 *"정리했다"* 고 **적어 두었다**. 🔴 그것은 기록이지 관측이 아니므로 직접 셌다:

```
aws ec2 describe-instances --filters "Name=instance-state-name,Values=running,pending,stopping,stopped"
  → 인스턴스 1개: i-07ddb6b41233f2673 (portfolio-demo-host, r6i.2xlarge, stopped)
```

⇒ **떠 있는 빌더가 없다.** 5일치 c6i.4xlarge 요금이 새고 있지 않다.

## 🔴 AMI 정리 — 「옛 이미지 prune」이 **지워선 안 되는 것**을 가리키고 있었다

소유자 선호에 «재빌드 후 옛 이미지 prune» 이 있다. 그대로 적용하려다 **멈췄다**:

| AMI | 커밋 | 상태 |
|---|---|---|
| `ami-02613b0378621b124` | `af0018aa6` (12차) | **배포본** (핀) |
| `ami-0d30513151d07e163` | `b54296645` (11차) | 🟡 **롤백 경로로 의도적 유지** — `terraform.tfvars:90` 이 그렇게 적는다 |

🔴 **`b54296645` 는 `af0018aa6` 의 조상**이라 «완전히 대체됨» 인데도 **지우면 안 된다** — 대체됐다는 것과 필요없다는 것은 다른 명제다. 🔵 10차(`ami-058f6293…`)는 **이미 목록에 없다**(prune 됨) ⇒ **정상 상태는 «배포본 + 롤백본» 2개**이고, 지금이 정확히 그 상태다.

⇒ **13차를 구운 뒤의 prune 대상은 12차가 아니라 11차(`ami-0d30513151d07e163`)다.** 12차가 새 롤백본이 된다.

## ⇒ 소유자가 실행할 것 (순서)

```bash
# 1) 굽는다 (~55분 · 과금)
bash infra/demo/aws/packer/bake.sh
#    성공하면 스크립트가 AMI 태그에서 되읽어 deployed-ami.env 를 다시 쓴다
#    ⇒ provenance 가 operator-record → **ami-tag** 로 승격된다(12차는 수동 태그라 못 했다)
# 🔴 굽기 뒤 deployed-ami.env 를 **커밋하는 것까지가 절차다**
# 🔴 등록 뒤에 죽으면: bash infra/demo/aws/packer/bake.sh --rescue-only --ref main

# 2) 새 AMI 로 plan 을 새로 뜬다 — 🔴 방금 쓴 budget plan 과 다르다
cd infra/demo/aws/terraform
terraform plan -var "ami_id=<새 AMI>" -out new-ami.tfplan
#    plan 에서 `aws_instance.demo must be replaced` 를 **눈으로 확인**한다(신선 볼륨의 근거)

# 3) apply — 인스턴스 교체 ⇒ 신선 볼륨
terraform apply new-ami.tfplan

# 4) 11차 AMI prune (12차가 새 롤백본이 된 뒤)
```

## ⚪ 사전점검이 **못 잰 것**

- **스냅샷의 실제 과금 용량** — 각 AMI 가 100GB 볼륨을 스냅샷으로 들고 있지만 스냅샷은 증분·압축이라 청구량은 그보다 작다. 읽으려면 `ebs:ListSnapshotBlocks` 인데 **이 계정에 권한이 없다**(핀 파일이 이미 기록한 사실). ⇒ «100GB × 2» 를 비용으로 적지 않는다.
- **굽기가 네트워크에서 죽을지** — 12차의 `unexpected EOF` 는 정적 검증이 못 잡는 종류다. 🔵 `TASK-MONO-709` 의 구조 경로가 그 대비이고 self-test 7/7 로 그 판정이 서 있음을 확인했다.
- **인스턴스 볼륨에 옛 행이 실제로 있는지** — SSM 필요, 여전히 막힌다.

---

# 🟢 14차 AMI 창 수확 (2026-09-24 UTC · 약 15분 · 분석=Opus 5.5)

**창**: 재굽기 `ami-03789993d93a320c1`(RepoCommit `f1da21800`, provenance=ami-tag, 핀 #3980) → 소유자 `terraform apply`
(교체 `i-01bf30…` → `i-008d1ac1ce23665ad`, 05:42Z 기동 = **신선 볼륨**). 묶음 `console · console-ecommerce · store`
위에 `console-wms · fan` 을 `/bundle/start` 로 추가. 예산 `1453 → 1468 / 1800`. 🔵 SSM 은 **그냥 됐다**(Online).

| 항목 | 판정 | 근거 (한 줄) |
|---|---|---|
| **13** (`721` AC-4) | 🟢 **PASS ①②③** | `demo-seller` ACTIVE + `account_db.accounts` 행(`tenant_id=ecommerce`) + `account_roles=SELLER` · `audience=wms` → **400 `invalid_grant`** (ecommerce·demo-corp 는 200) · 교환 토큰에 `entitled_domains`/`roles`/`org_scope` 없음, `sub`=클라이언트. 상세 `TASK-MONO-721` § AC-4 |
| **1** (`679`) | 🟢 **PASS** (라이브) | 소비자 토큰 `GET /api/v1/community/feed` 5건 — PUBLIC 2건 `mediaRefs=2` · PREMIUM(locked) `[]` · 노아 «멤버십 전용 — 다음 EP 트랙 리스트 초안» 존재 |
| **2②** | 🟢 **PASS** (API) | 회원 상세 `GET /api/v1/community/posts/{id}` — PUBLIC DB 글 둘 다 `mediaRefs=2` |
| **2③** | ⚪ | 홈 전체 캡처(`capture-portfolio --app fan`)의 **공개 절은 번들 스냅샷**(바닥 `샘플 데이터 (아직 발행 전)`)이라 DB 팔로우 피드와 «같은 목록» 을 무엇으로 판정할지 술어가 없다. 관측: 팔로우 5건 중 «새 싱글 「밤의 끝」» · «다음 EP 트랙 리스트 초안» 은 공개 절에도 있다. 🔵 공개 절 카드 사진 다수가 회색 자리표시로 찍혔다 — 전체 페이지 캡처의 lazy-load 인지 결함인지 **안 쟀다** |
| **4①** (`BE-595`) | 🟢 **PASS** | `product-service-client` cc 토큰(`tenant_id=global-account-platform`)으로 ecommerce 게이트웨이 `/api/orders` · `/api/admin/orders` · `/api/products` → 전부 **403 `TENANT_FORBIDDEN`**. 대조군: 토큰 없음 → **401 `UNAUTHORIZED`** |
| **4②** | ⚪ 막힘 유지 | 콘솔 화면 경로는 이 창에서 안 열었다(외부 테넌트로 콘솔 세션을 만드는 경로가 여전히 없다) |
| **5** 한 줄 | 🔵 기록 | `seed-wms.sh` 이후 `SKU-APPLE-001`: `available_qty=85` · `reserved_qty=0` (lot NULL 행 1개, 출고 10 차감 뒤). ②③ 은 제안 선행 경로가 없어 **측정 불가 유지**(다시 열지 않음) |
| **9의 사슬** | 🔵 `706` 로 | shipping.confirmed `.DLT` 토픽 **부재** · inventory 소비 lag 0 · `inventory.confirmed` 1건 · 사가 `COMPLETED` · STUCK_* 0 ⇒ `TASK-MONO-706` § AC-1·AC-3 |

같은 창의 다른 판정: `TASK-MONO-717` AC-1 PASS(721 이 풀었다) · `TASK-MONO-725` AC-6 재촬영(위·아래 7,900/17,900 일치, README 04 복원) · `TASK-MONO-722` AC-0 = 행 있음 → 멈춤(그리고 그 술어가 떠 있는 스택에서 참이 될 수 없다는 관측).

---

# 🟢 13차 AMI 창 수확 (2026-09-22 UTC · 30분 소모 · 분석=Opus 5)

**창**: 08:04Z 기동(terraform 교체가 켰다) → 08:5xZ `/stop`. **`used 1170 → 1200 / 1800`** ⇒ **600분 남음.**

## 🟢 신선 볼륨이 증명됐다 — 항목 1 의 선행이 풀렸다

부팅 시드 로그(SSM):

```
[seed] 대상 도메인: iam fan  (DEMO_DOMAIN=15-164-242-136.sslip.io)
[seed:fan] 생성  ARTIST_POST(PUBLIC · 루미) … (6 아티스트 × PUBLIC/MEMBERS_ONLY)
[seed:fan] 요약 — 생성 18 · 기존 0 · 실패 0
```

🔵 **`기존 0` 이 핵심이다.** 항목 1 이 막혀 있던 이유가 *"`publish_artist_post` 는 **제목으로** «이미 있음» 을 판단하고 발행을 건너뛴다 — 구워진 AMI 의 DB 에 사진 없는 글이 이미 있다"* 였는데, **기존이 0 이므로 건너뛰기가 일어나지 않았고 18건이 새로 발행됐다.** ⇒ **재굽기가 사려던 것을 실제로 샀다.**

⚪ **그러나 `mediaRefs` 자체는 아직 못 쟀다** — 판정은 `GET /api/community/feed` 인데 *"All endpoints require `Authorization: Bearer`"* 라 사용자 토큰이 필요하고, 시드는 **호스트 안에서 내부 호스트명**으로 그 토큰을 얻는다(`iam.${DEMO_DOMAIN}`). 밖에서 재현하려면 공개 호스트의 client 등록을 추측해야 해서 창 시간을 태울 위험이 컸다. 🔵 **다음 창의 한 줄**: SSM 으로 호스트 안에서 `user_token` 을 얻어 `curl` 한 번.

## 🔴🔴 두 번 틀렸다 — 둘 다 기록한다

### ① «console-bff 기동 실패» 가설 — **반증됐다**

`/dashboards/overview` 와 `/dashboards/health` 가 둘 다 `*-bff-unavailable` 로 저하라, 이번 AMI 에 처음 들어간 `TASK-MONO-712`(console-bff 의 `aud` fail-closed)가 **빈 allowlist 로 기동 실패**한 것이라고 의심했다. 로그가 그것을 죽였다:

```
platform-console-bff    Up 19 minutes (healthy)
```

🔴 **떠 있고 healthy 다.** 그리고 로그 꼬리는 전부 **OTLP 수출 실패 스팸**(`localhost:4318` 연결 거부)이라 **진짜 원인이 밀려나 안 보인다** — 이 저장소가 이미 이름 붙인 함정이다(`env_docker_container_json_log_unbounded_otlp_spam`). ⇒ 다음에 로그를 받을 때는 **`grep -v` 로 OTLP 를 걷어낸 꼬리**를 요청해야 한다.

### ② «새로 드러난 결함» 이라고 부른 것 — **새것이 아니다**

`operator-overview-bff-unavailable` 은 **이미 기록돼 있다** — `TASK-MONO-645` § · `TASK-MONO-711`(제목 자체가 *"기록된 한계와 진짜 장애가 같은 화면으로 나온다"*) · `TASK-MONO-648` § 표. 🔵 **새로운 것은 하나뿐**: **신선 볼륨 + 최신 코드에서도 그대로**라는 것. ⇒ «낡은 데이터/낡은 빌드» 가설이 배제된다.

## ⚪ ecommerce · wms · scm 이 비었다 — **원인은 미확정이다**

촬영 실측(콘솔 55장, 20분 간격 **두 번**):

| 도메인 | 결과 |
|---|---|
| IAM (`/operators` `/permissions` `/permission-sets`) | 🟢 데이터 있음 |
| ERP (`/erp/masters` **1646자**) | 🟢 있음 (과거 실측 1677자와 일치) |
| **ecommerce · wms · scm** (빈값 12~13) | 🔴 **빈 목록** |

🔵 **시간 문제가 아니다** — 20분 간격 두 측정이 같았고, 그 사이 `/audit` 만 채워졌다(내 로그인이 쌓인 것 = **계측기가 작동한다는 대조군**).

🔴🔴 **그런데 «시드가 안 돌았다» 로 읽으면 안 된다.** 부팅 시드는 선택(`fan`)만 시드했지만, `demo-up.sh:404` 가 **그 호출에서 올리는 도메인 집합으로** 시드를 부르므로 `/bundle/start` **도 시드를 부른다**. 내가 받은 journalctl 은 `tail -60` 으로 잘려 **부팅 시드만** 보였고 `/bundle/start` 뒤의 시드 로그는 그 안에 없다.

⇒ **「안 돌았다」와 「돌았는데 실패했다」를 이 세션은 구별하지 못한다.** 인스턴스가 꺼져 그 구별은 다음 창이다. 🔵 `demo-up.sh` 주석이 *"시드 13~14분"* 이라고 적으므로 **첫 촬영(기동 12분 뒤)은 시드 중일 수 있었고, 둘째 촬영(40분 뒤)은 끝났어야 한다** — 그래서 실패 쪽이 더 그럴듯하지만 **그럴듯함은 관측이 아니다.**

## 🔵 곁에서 확인된 것

- **OIDC 콜백 재등록이 작동한다** — `[seed] OK — 죽은 sslip 등록 0건 · 4 개 클라이언트가 15-164-242-136.sslip.io 콜백을 갖는다`. 인스턴스 교체로 IP 가 바뀌어도 부팅 시드가 따라간다.
- **`platform-console-web` 에 `https://console.hubwang.com/api/auth/callback` 이 등록돼 있다** ⇒ 콘솔 로그인은 살아 있고, 실제로 촬영이 로그인에 성공했다.
- **묶음 9개 전부 `ready`** 까지 갔다(부팅 ~12분).

## ⇒ 다음 창에서 **가장 먼저** 할 것 (순서가 중요하다)

1. 🔴 **부팅 선택을 먼저 바꿔라.** 부팅 시드는 «그때의 선택» 만 시드한다. 지금 SSM 파라미터에 남은 선택은 **8묶음**이므로 다음 부팅은 그 전부를 시드할 것이다 — 그것이 § ⚪ 의 구별을 자동으로 준다(부팅 시드 한 번으로 전 도메인 데이터가 생기면 「/bundle/start 경로의 시드가 문제였다」가 확정된다).
2. **OTLP 를 걷어낸 로그**: `docker logs --tail 400 <c> 2>&1 | grep -viE "otlp|4318|okhttp|opentelemetry" | tail -80`
3. **항목 1 판정**: 호스트 안에서 `user_token` → `curl /api/community/feed` → PUBLIC 글의 `mediaRefs` 비어 있지 않은가 · 잠긴 항목은 `[]` 인가.
4. `TASK-PC-FE-295` AC-0 ② 는 ①이 풀린 뒤에야 의미가 있다(카드가 값을 그리려면 레그가 살아야 한다).

---

# 🟢 14차 창 수확 — 항목 1·6·7 을 닫고, 오진 하나를 잡았다 (2026-09-22 UTC · 분석=Opus 5)

**창**: 09:16:58Z 기동(8묶음) → 10:35Z `/stop`. **`used 1200 → 1232 / 1800`** ⇒ **568분 남음.**

## 🔴🔴 먼저 — 이 티켓의 런북이 적은 「SSM 은 에이전트에게 막혀 있다」는 **낡았다**

§ 다음 창 런북과 `TASK-MONO-706` AC-0 이 둘 다 *"명령은 소유자가 실행한다(SSM 은 에이전트에게
막혀 있다)"* 라고 적는다. **이 세션에서 시도하니 통과했다** — `aws ssm send-command` 가
`CommandId` 를 돌려줬고(`ff4cc0e2-…`), 이후 20여 번의 측정이 전부 에이전트 손으로 돌았다.

🔵 이것이 이 저장소의 규율 그대로다 — **«막힌다» 는 표가 아니라 시도가 정한다.** 지난 창의
수확이 죽은 이유가 이 한 줄이었고, 그 줄은 **측정이 아니라 기록된 기억**이었다.
🔴 그러므로 어떤 티켓에서도 «SSM 이 막힌다» 를 ⚪ 의 **사유**로 적지 마라. 그 자리에서 한 번
시도하고, 정말 막히면 그때 적어라.

## 🟢 항목 1 (`TASK-MONO-679`) — 닫힌다. 판정은 **FAIL**, 그리고 **진단이 틀렸다**

신선 볼륨(13차 AMI)의 첫 부팅이 만든 글 18건을 실제로 읽었다 —
`GET /api/v1/community/feed` (호스트 안에서 `user_token` 으로 발급):

| 술어 | 결과 |
|---|---|
| PUBLIC 글의 `mediaRefs` 가 비어 있지 않은가 | 🔴 **`[]` — 피드 5건 전부** |
| 잠긴 항목은 `[]` 인가 | 🟢 **PASS** (`locked:true` + `title:null` + `bodyPreview:null`) |

🔴🔴 **원인은 「제목 건너뛰기」가 아니다.** `seed-fan.sh` 의 `publish_artist_post` 는 6번째 인자로
사진 배열을 **받고**(루미·노아의 PUBLIC 글에 2장씩) `extra=",\"mediaRefs\":$media"` 를 **조립까지**
한다. 그런데 요청 본문이 그 변수를 **안 싣는다**:

```bash
api_create "$label" "$GW/api/v1/community/posts" \
  "{\"postType\":…,\"visibility\":…,\"title\":…,\"body\":\"$body\"}"   # ← $extra 없음
```

⇒ **빈 볼륨에서도 결과는 같다.** 이 항목이 «재굽기가 선행» 이라고 적힌 근거
(*"구워진 AMI 의 DB 에 사진 없는 글이 이미 있어서 건너뛴다"*)는 **오진**이었다. 건너뛰기는 실재
하지만 사진이 없는 원인이 아니었고, 그래서 재굽기는 이 항목에 대해 **아무것도 사지 않았다.**

🔵 **producer 는 그 필드를 받는다** — `PublishPostRequest.mediaRefs`
(`@Size(max=MediaRefRules.MAX_COUNT)` · 원소마다 `@Pattern(HTTPS_URL)`). 즉 시드 한 줄이 전부다.

**고침 + bite**: `$extra` 를 본문에 싣는다 ·
`FanArtistDemoSeedTest#assembledMediaRefsReachTheRequestBody`(되돌리면 **rc=1**, 그 칸만 빨강 — 실측).
⚪ **화면 판정은 다음 신선 부팅이다** — 이미 발행된 18건은 제목으로 건너뛰므로 이 창에서
사진이 붙지 않는다(그 건너뛰기는 이제 진짜로 그 역할만 한다). 🔴 **이 한 줄만 남기고 항목 1 을
닫는다** — 「사진이 실제로 그려지는가」는 아래 § 후속 항목으로 옮긴다.

## 🟢 항목 6 (`TASK-MONO-705`) — ①② 둘 다 닫힘

**① refresh 응답의 키 목록** (값 출력 없음, 키만):

```
authorization_code → access_token expires_in id_token refresh_token scope token_type
refresh_token      → access_token expires_in id_token refresh_token scope token_type
```

🟢 **`id_token` 이 온다** — 705 의 수정이 의지하는 바로 그 사실이고, 그 티켓은 코드 독해로만
답했던 자리다. 이제 **엔드포인트 본문**으로 답했다.

**② 창 재판정** (`scripts` 밖의 일회용 Playwright 프로브, 판정 술어 = **쿠키**):

```
① 로그인 → console_access_token(830) · console_id_token(878) · console_refresh_token(128)
② console_access_token 만 삭제
③ /dashboards/overview → 200 · /login 으로 밀리지 않음
   console_access_token  🟢 다시 섬(830)
   console_refresh_token 🟢 **값 회전됨**   ← 갱신이 실제로 돌았다는 유효성 술어
   console_id_token      🟢 그대로 서 있음
```

🔵 **refresh_token 의 회전이 대조군 노릇을 한다** — 「세션이 그냥 살아 있어서 통과한 것」이면
회전이 일어날 이유가 없다. 705 가 «비밀번호 칸 개수» 로 물었다가 정반대 판정을 찍은 자리를
쿠키로 물어 닫았다.

## 🟢 항목 7 (`TASK-MONO-713` 인계) — 「안 보이는 고장」이 **관측됐다**

```
ACCOUNT_SERVICE_BASE_URL  → ecommerce-product-service 컨테이너 환경에 **없음**
로그:
  seller provisioning failed (fail-soft, seller stays PENDING) tenant=ecommerce seller=demo-seller:
    I/O error on POST request for "http://localhost:8081/oauth2/token": Connection refused
  seller left PENDING_PROVISIONING (IAM unavailable, retryable) tenant=ecommerce seller=demo-seller
```

- 술어 ①(로그에 무엇으로 찍히나) → **connection refused**. 티켓이 *"refused 가 예상이지만 예상은
  측정이 아니다"* 라고 적은 그 자리를 실측으로 채웠다. 🔵 다만 refused 대상은 accounts 엔드포인트가
  아니라 **IAM 토큰 엔드포인트**(`/oauth2/token`)다 — 프로비저닝은 **토큰도 못 받고** 죽는다.
- 술어 ②(account_db 에 행이 생겼나) → **행 이전에 호출 자체가 없다.** 그리고 셀러는
  `PENDING_PROVISIONING` 에 남는다(시드의 「셀러 활성화」는 **다른 경로**다).

⇒ **결함 확정.** AC-2 규율대로 여기서 고치지 않고 **별도 티켓**으로 기안한다.

## 🟡 항목 2 (`TASK-MONO-679` AC-0) — ① 은 성립, ②③ 은 항목 1 과 함께 다음 창

`fan` 촬영 **11/11 · 실패 0**, `✔ /posts/[id] → /posts/01a0c825-…`(로그인 상태에서 상세가 열린다)
⇒ ① 「게이트웨이 404 → 폴백」 경로는 **살아 있다**. ②(사진) 는 위 § 의 시드 수정이 실려야
판정되고, ③(두 피드의 목록 일치)는 이 창에서 따로 안 쟀다 — ⚪.

## ⚪ 항목 5 (`TASK-MONO-683` AC-4) — 열지 못했다, 그리고 **내 술어가 틀렸다**

`/scm/replenishment` 은 촬영에서 **빈 목록**이다(런북이 이미 예고한 대로 — 추천은 wms 저재고
알림/IVS 야간 스윕에서만 생기고 신선 볼륨엔 그 트리거가 없다). 🔴 API 로 분모를 세려 했으나
**경로를 계약서에서 읽지 않고 세 개를 추측했고 셋 다 404** 였다 — 이 저장소가 이름 붙인 실패를
그대로 밟았다. ⇒ 이 항목은 **여전히 ⚪ 이고, 남은 것은 「측정」이 아니라 「선행 만들기」** 라는
런북의 판단이 유지된다.

## ⚪ 항목 4 (`TASK-BE-595` AC-4) — 시도하지 않았다

외부 테넌트 토큰을 **일부러** 만들어야 하는데, 이번 창은 그 설계를 하지 않았다. 🔵 다만 재료가
하나 늘었다 — `operator_token <tenant>` 가 임의 테넌트로 **assume 을 시도**할 수 있음을 확인했고
(`ecommerce`·`demo-corp` 둘 다 토큰 발급 성공), 엔타이틀먼트 없는 테넌트 이름만 고르면 된다.

## 🔵 곁에서 닫힌 것들 (각 티켓에도 적었다)

| 티켓 | 결과 |
|---|---|
| `TASK-MONO-675` AC-1 | 🟢 **ⓐ 확정** — `wms.master.*.v1` 6종 **전 파티션 오프셋 0** · `.dlq` 도 **0** ⇒ 「시드가 이벤트를 안 낸다」가 관측이 됐고 「소비 실패」(ⓓ)는 배제됐다 |
| `TASK-MONO-706` AC-0 | 🟢 **실패 지점 지목** — `ShippingConfirmedConsumer.applyConfirm:148` · `IllegalArgumentException: … has no matching reservation line on reservation d987aea0-…` 🔴 그런데 **이벤트 페이로드의 `reservationId` 는 `01a0c831-fb7b-…`** 로 **다르다**(eventId 대조로 같은 이벤트 확인) |
| `TASK-MONO-711` ③ | 🟢 **확인 사살 통과** — 매니페스트가 `/console degraded=true ["catalog-health-unavailable"]` (기대값과 동일). `/dashboards/health` 는 `domain-health-bff-unavailable` |
| `TASK-MONO-648` | 🟢 **세 앱 촬영** — console 53 · store 21 · fan 11. 🔴 커밋하지 않는다(15–50MB) |
| `TASK-MONO-697` | ⚪ **미측정 — 그리고 「불일치 0」이 아니다**(아래) |

### 🔴 `TASK-MONO-697` — 왜 ⚪ 인가 (숫자를 0 으로 적지 마라)

게이트웨이 7개 전부 `JWT audience not on allowlist` **0줄**이다. 그것을 「불일치 없음」으로 읽으면
안 된다. 유효성 술어를 세워 봤다:

- 컨테이너에 `curl` 은 **있다**(`/usr/bin/curl`) ⇒ 「도구가 없어서 못 읽었다」는 배제.
- `actuator/prometheus` 에 `gateway_jwt_audience*` **없음**, `actuator/env` 는 **401**.
- 배포 이미지는 **13차 굽기 산물**(`created=2026-09-22T06:55Z`)이고 `java-security.jar` 도 들어 있다.
- 설정은 기본값으로 켜져 있어야 한다 — `allowed-audiences: ${OIDC_ALLOWED_AUDIENCES:platform-console-web}` ·
  `audience-mode: ${OIDC_AUDIENCE_MODE:SHADOW}`.
- **주입**: `aud=fan-platform-user-flow-client` 토큰(= wms 허용목록 밖)으로 wms 를 호출 → **403**,
  그런데 경고는 **여전히 0줄**. 그리고 그 게이트웨이의 **WARN 총 줄 수도 0** 이다.

⇒ 「검사가 돌고 전부 일치했다」와 「검사가 그 요청에 도달하지 못했다」를 **이 세션은 구별하지
못한다**. 🔵 **다음 탐침**: 그 403 이 JWT 디코딩 **전**에 나는지 뒤에 나는지 가른다 — 콘솔 토큰이
200 을 받는 경로에 같은 fan 토큰을 보내고, 게이트웨이가 INFO 액세스 로그를 내는지부터 확인한다.

## 🔵 지난 창의 ⚪ 하나가 닫혔다 — 「ecommerce·wms·scm 이 비었다」

세 도메인을 하나로 묶은 것이 틀렸다. **셋 다 원인이 다르다:**

- **ecommerce** — 🟢 **원인 확정, 그리고 결함이 아니다.** 데이터는 **있다**. 시드는
  `operator_token ecommerce` 로 쓰고, 콘솔 기본 세션은 `demo-corp` 다. 같은 순간·같은 URL 대조군:
  `ecommerce` = orders 5 · products 24 · users 1 · sellers 2 / `demo-corp` = **전부 0**.
  `DEMO_TENANT=ecommerce` 로 다시 찍으니 **21장 중 빈 장 0**. ⇒ 테넌트 불일치다(별도 티켓).
### 🔴🔴 정정 — 테넌트 축은 **내가 발견한 것이 아니다**

위를 처음 쓸 때 나는 이것을 「두 창에 걸친 ⚪ 를 푸는 새 발견」처럼 적었다. **아니다.**
`TASK-MONO-648` 이 (2026-09-10 이전에) 이미 적어 두었다:

> 🔴 `/ecommerce/*` 3장은 **테넌트 `ecommerce` 로 재촬영**한 뒤에만 승인 목록으로 확정한다.
> 🔵 `demo-corp` 로 찍어서 「승인 목록대로 찍었다」고 적으면, 그 매니페스트는 **빈 표 세 장을
> 승인된 것으로** 기록한다.

🔴 이 저장소가 같은 실수를 이미 이름 붙였다(지난 창의 «새로 드러난 결함» 이 실은 645·711·648 에
있던 것이었던 자리) — 그리고 **나는 그것을 한 창 만에 다시 밟았다.**

🔵 **그래서 이번에 진짜로 새로운 것만 남기면 셋이다:**
1. **수치 대조군** — 같은 순간·같은 URL 에서 `ecommerce` 5/24/1/2 대 `demo-corp` 0/0/0/0.
   648 의 문장은 «빈 표가 된다» 였고, 이것은 **분자와 분모**다.
2. **`TASK-MONO-710` AC-3 의 사후조건이 그 축에 대해 공허하다**는 것 — 648 의 노트는 촬영에
   대한 것이었고, 시드의 자기검증이 같은 함정에 빠져 있다는 연결은 아무도 안 적었다.
3. 이 티켓이 «ecommerce·wms·scm 이 비었다» 를 **한 덩어리 ⚪** 로 들고 있었다는 것 —
   648 에 답이 반쯤 있었는데 **그 두 티켓이 서로를 안 봤다.**

- **wms** — 🔵 **원래 비어 있지 않았다.** `/wms/inventory`·`/wms/inbound`·`/wms/outbound`·`/wms/master`
  전부 데이터. 빈 것은 랜딩 `/wms` 한 장이다. ⇒ 지난 창의 요약이 **과장**이었다.
- **scm** — `/scm/inventory`·`/scm/replenishment` 둘만 빈다. 후자는 **예상된 0건**(위 항목 5).

🔴 그리고 **「개요·상태 카드의 저하」는 결함이 아니다** — `infra/demo/console-vercel.override.yml`
이 그것을 **기록된 영구 한계**로 적고 있다: *"`console-bff` 는 공개 호스트명이 없고
(`TASK-MONO-362` 가 그 Traefik 라우터를 일부러 없앴다), Vercel 콘솔은 그것을 못 부른다. 영향 레그
셋뿐 — 운영 개요 합성 · 도메인 상태 합성 · 알림 인박스."* 실측이 그 문장과 정확히 맞는다:
console-bff 는 `healthy` 인데 **09:25:23Z 서블릿 초기화 이후 요청 로그가 한 줄도 없다.**
🔵 지난 창에서 내가 «console-bff 기동 실패» 를 의심했다가 반증당한 자리의 **진짜 답**이 이것이다.

---

# 🟢 15차 창 수확 (2026-09-22 UTC · 12:30–13:03Z · **33분 소모** · 1232 → 1265 / 1800 · 잔여 535분)

묶음 5개(`console` · `console-ecommerce` · `console-wms` · `console-scm` · `fan`) = 도메인 6개.
부팅 **11분**(12:30:53 기동 → 12:41:45 `selection_ready`). 🔵 런북의 «7묶음=14분» 과 일관된다.

## AC-0 착수 게이트

- 🔴 **①「창이 이미 열렸는가」는 «아니오» 였다.** 이 티켓 단독으로는 STOP 이 옳은 구현이다 —
  창은 **소유자의 명시 지시**로 열렸다(「진행」). ⇒ 게이트는 «이미 켜져 있었다» 가 아니라
  «소유자가 켜기로 했다» 로 충족됐고, 그 구별을 여기 적어 둔다.
- ② 항목 있음(2 · 4 · 5 · 8 · 9 · 10).
- ③ 예산 **재측**: `used 1232 / 1800` ⇒ 568분. 🔴 티켓에 적힌 «600분» 은 낡은 값이었다.

## 🔴🔴 창을 열기 전에 확정한 것 — **이 부팅이 어느 커밋을 들고 있나**

구운 커밋 `1d47c5ad6`(13차) vs `origin/main`. `git diff --stat` 로 **실측**:

| | 구워진 백엔드 | Vercel 프런트 |
|---|---|---|
| 710 사후조건(#3922 `366d7908d`) | 🟢 조상이다 | — |
| `variantId` 추출 고침(#3945) | 🔴 **없다** | — |
| `seed-fan.sh` `$extra` 고침 | 🔴 **없다** | — |
| 718 안내 · 296 링크 | — | 🟢 있다(`main`) |

🔵 그래서 이 창은 **항목 1·2②③(fan mediaRefs)을 못 산다** — 그 고침은 다음 굽기 뒤다.
항목 8·10·2①·711③ 은 프런트/폴백이라 **지금 잴 수 있다**.

## 🔴🔴 그리고 **stop/start 는 신선 볼륨이 아니다** — 이 창이 그것을 실측으로 확정했다

인스턴스 클론(`/opt/monorepo-lab`)이 **지난 창에서 내가 SSM 으로 넣은 한 줄 고침을 그대로 들고 있었다**:

```
git -C /opt/monorepo-lab rev-parse HEAD   →  1d47c5ad6…   (구운 커밋)
git status --porcelain                    →  M infra/demo/seed/seed-ecommerce.sh
                                             ?? infra/demo/seed/seed-ecommerce.sh.orig13
git diff  →  변경 딱 한 줄: variantId 추출식 → variants[] 추출식
```

⇒ 이 티켓이 여러 번 적은 *«기다리는 것은 새 AMI 가 아니라 신선 볼륨»* 이 **왜** 참인지가
여기서 완전히 닫힌다: `stop`/`start` 는 루트 EBS 를 보존하고, **인스턴스 교체만이** 볼륨을 새로 만든다.

---

## 🟢 항목 9 (`TASK-MONO-710` AC-3) — **닫힘. 양방향 bite.**

사후조건 블록(`seed-ecommerce.sh` 672–726행, **55줄 그대로**)을 복사본으로 떼어 하네스에 물렸다.
🔴 **실행 중인 시드 파일은 건드리지 않았다**(아래 § 정정이 그 이유다).

```
===== tenant=ecommerce =====                      <- 대조군: 하네스가 «일을 한다»
  사후조건 — 운영자 평면 주문 5 건 · 서로 다른 상태 4 종 (CANCELLED CONFIRMED DELIVERED SHIPPED)
  사후조건 — 배송 건 3 건
  요약 — 생성 0 · 기존 0 · 실패 0                 ->  rc = 0

===== tenant=demo-corp =====                      <- 음성 방향: 같은 URL, 주문 0건인 평면
  사후조건 — 운영자 평면 주문 0 건 · 서로 다른 상태 0 종 ()
  사후조건 — 배송 건 0 건
  X 주문이 0 건입니다(하한 5) — /ecommerce/orders · orders/[id] 가 빕니다
  X 주문 상태가 0 종입니다(하한 4: CANCELLED·CONFIRMED·SHIPPED·DELIVERED)
  X 배송 건이 0 건입니다(하한 2: SHIPPED 에서 멈춘 것 + DELIVERED 까지 간 것)
  요약 — 생성 0 · 기존 0 · 실패 3                 ->  rc = 1
```

🟢 **세 단언 전부 발화하고 `seed_summary` 가 rc≠0 으로 끝난다.** 항목 9 가 세우려던 층이 섰다.

🔴 **이것이 증명하지 않는 것** — 항목이 적은 문장은 *"시드에서 **주문 생성을 지우면**"* 이었고,
나는 지운 것이 아니라 **주문이 0건인 평면을 먹였다**. 술어가 보는 입력(`totalElements=0`)은 같지만,
「생성 블록 삭제 → 시드가 계속 돌아 → 사후조건이 빨개져 → 드라이버가 도메인 실패를 보고」의
**한 실행으로서의 사슬**은 안 쟀다. 그 사슬은 신선 볼륨이 있어야 하고, 그때는 «지우는 것» 대신
**깨진 추출식이 이미 그 역할을 한다**(아래 § 정정의 09:26 실행이 바로 그 모양이었다).

### 🔴🔴 정정 — 09:26 로그의 「문법 오류」는 **AMI 결함이 아니라 내 소행이다**

저널에 지난 창의 부팅 시드가 남아 있었고 그 실행은 이랬다:

```
09:26:35  X 주문 시드: 상품 …0002 에서 variantId/price 를 추출하지 못했습니다   x5
09:26:40     SHIPPED 에서 멈출 배송 건이 없습니다
09:28:42  seed-ecommerce.sh: line 670: syntax error near unexpected token
09:28:42  [seed] X 실패한 도메인: ecommerce
```

첫 판독은 *"구워진 시드가 670행에서 죽어 사후조건이 한 번도 실행된 적이 없다"* 였다.
**그 판독대로 갔으면 AMI 에 거짓 결함 티켓을 기안했다.** 유효성 술어가 막았다:

- `git show 1d47c5ad6:…seed-ecommerce.sh | bash -n` → **PARSES OK** (`366d7908d` · `origin/main` 도 OK)
- 인스턴스 파일의 mtime = **09:28:30** — 문법 오류 **12초 전**
- 인스턴스의 diff = 내가 넣은 **한 줄**뿐, 백업 `…orig13` 도 내 것

⇒ **부팅 시드가 그 파일을 읽으며 실행 중인데 내가 그것을 제자리에서 갈아 끼웠다.**
bash 는 스크립트를 **바이트 오프셋으로 이어 읽으므로** 인터프리터가 낡은 오프셋에서 재개해
깨진 토큰을 만났다. 🔵 규칙으로 남긴다: **돌고 있는 셸 스크립트를 in-place 로 고치지 마라** —
증상이 「그 스크립트의 문법 오류」로 나오고, 그건 파일이 아니라 **타이밍**의 결함이다.

---

## 🔴 항목 8 (`TASK-MONO-718` ⓑ) — **닫힘. 판정 FAIL, 그리고 원인은 내가 출하한 술어다.**

```
로그인 → 테넌트 demo-corp «적용» 확인(셀렉트값 + «테넌트를 선택» 부재 둘 다) → /ecommerce/orders
  domain-tenant-mismatch  ->  없음
  order-empty             ->  있음  («표시할 주문이 없습니다»)
  표 행                    ->  0
대조군: ecommerce 로 전환 → 같은 URL → 표 행 **5** · 안내 없음   (게이트 자체는 산다)
```

원인을 게이트의 **증거원 그대로** 읽었다 — 콘솔 자신이 그리는 `/console` 타일 전수:

```
iam     -> [demo-corp, ecommerce]      scm       -> [demo-corp]
wms     -> [demo-corp, ecommerce]      erp       -> [demo-corp]
finance -> [demo-corp]                 ecommerce -> [demo-corp, ecommerce]   <- demo-corp 가 있다
```

⇒ `tenantMismatch('ecommerce')` 는 `includes('demo-corp') === true` 라 **`null` 을 돌려준다.**
안내는 **구조적으로 뜰 수 없다.**

🔵 레지스트리가 틀린 게 아니다 — `demo-corp` 는 ecommerce 제품에 **자격이 있다**(역할을 든다).
행이 `tenant_id=ecommerce` 에 사는 것은 **다른 명제**다. 게이트는 **자격을 묻고 그 답을
데이터 소유의 답으로 썼다.**

🔴 **단위 칸 10개가 왜 못 막았나**: 픽스처가 `tenants:['ecommerce']` — **라이브가 내지 않는 입력**이다.
⇒ **후속 티켓 `TASK-MONO-719`** 로 기안했다(AC-2: 여기서 고치지 않는다). 술어의 근거 갈래 셋과
추천(ⓑ)을 그 티켓에 적었고, 판정은 다시 이 집으로 돌아온다(719 AC-3).

---

## 🟡 항목 10 (`TASK-PC-FE-296` ⓒ) — **닫힘. 도착지는 PASS, 카드 절반은 «영구히 못 잼».**

**도착지 (🟢 PASS)** — ⓒ 가 실제로 바꾼 동작:

```
/wms/inventory?lowStockOnly=true   ->  「저재고만」 체크박스 checked=true  · 표 행 0
/wms/inventory        (대조군)      ->  「저재고만」 체크박스 checked=false · 표 행 1
```

🔵 대조군이 두 가지를 동시에 준다 — 파라미터가 **체크박스**를 켰고(첫 층), 질의도 **실제로
달라졌다**(1행 → 0행, 둘째 층). 「체크만 켜고 질의는 안 바뀐다」와 구별된다.

**카드 절반 (⚪ — 창이 아니라 구조가 막는다, 측정으로 확정)**:

```
/dashboards/overview  ->  data-testid="operator-overview-bff-unavailable"
                          «통합 개요를 일시적으로 불러올 수 없습니다.»
                          operator-overview-card-wms-lowstock-link  ->  없음
```

`operator-overview` 카드는 `/dashboards/overview` **한 곳**에만 그려지고 그 화면은 `console-bff`
합성이다. `console-vercel.override.yml` 이 **기록된 영구 한계**로 적은 대로 BFF 는 공개 호스트명이
없다(`TASK-MONO-362` 가 Traefik 라우터를 일부러 없앴다) ⇒ **어떤 창에서도** 그 링크를 누를 수 없다.
🔵 설정 파일에서 **유도하지 않고** 화면을 열어 마커로 확인했다(부재 판정에 대리지표 금지).

**저재고 분모 (🔵 0 — `296 AC-0 ③` 의 ⚪ 가 닫힌다)**: 재고 행 **전체가 1건**, 저재고 **0건**.
⇒ 「링크는 맞는데 볼 것이 없다」이고 **결함이 아니라 데이터 상태**다. 항목이 미리 경고한 대로
둘을 섞지 않는다.

🔴 **그러므로 «한 항해에서 만나는가» 는 이 데모에서 영영 못 잰다.** 그 교집합은 운영(BFF 도달
가능) 또는 로컬 `pnpm console:up` 에서만 잴 수 있다 ⇒ **다음 창에 다시 시도하지 마라.**

---

## 🟢 항목 2 ① (`TASK-MONO-679` AC-0 ①) — **닫힘. PASS.**

공개 피드에서 **사진을 든 카드**의 링크를 골라(첫 링크는 잠긴 글이라 안 된다 — 실측) 비로그인/로그인
양쪽에서 같은 id 를 열었다:

```
anon      /posts/0199de80-…b011  ->  public-post-detail · public-post-gallery · post-image x2 · provenance-banner
logged-in /posts/0199de80-…b011  ->  동일 (내비만 로그인 크롬으로 바뀜)
```

⇒ 게이트웨이가 모르는 id 라 **404 → 샘플 폴백**이 로그인 여부와 무관하게 유지되고, **사진 있는
공개 판**이 그려진다. ①이 묻던 것 그대로다.
🔵 ②③(회원 상세가 사진을 싣는가 · 두 피드 정렬)은 **`seed-fan.sh` 고침이 안 구워져서** 이 창의
범위 밖이다 — 다음 굽기 뒤.

---

## 🟢 곁에서 닫힌 것 — `TASK-MONO-711` ③ 의 «확인 사살»

런북이 다음 창에 한 줄만 보라고 했던 것:

```
/console  ->  data-testid="catalog-health-unavailable"  ->  있음  (기대값 그대로)
```

🔵 이것은 «화면이 나빠졌다» 가 아니라 «이전 «저하 아님» 이 오보였다» 의 확인이고,
711 ③ 의 마커가 **배포된 화면에 실제로 렌더된다**는 뜻이다. 711 을 다시 열 필요가 없다.

---

## ⚪ `TASK-MONO-697` — 분자·분모 **둘 다 닫혀 있고 이유가 서로 다르다** (그 티켓에도 적었다)

**탐침 ① — 경로를 계약서에서 읽으니 200 이다.** 지난 창의 `/api/wms/inventory` 404 는 **내 경로
오류**였다(`admin-service-api.md` Base path `/api/v1/admin`):

```
GET http://wms.<domain>/api/v1/admin/dashboard/inventory?page=0&size=1
  A) 콘솔 운영자 토큰 (aud="platform-console-web")  ->  200  {"content":[{…}]}
  C) 토큰 없음                                      ->  401  UNAUTHORIZED
  D) 쓰레기 토큰                                    ->  401  UNAUTHORIZED
  B) fan aud 토큰                                   ->  ⚪ 못 만들었다 (token 교환 invalid_client)
```

**탐침 ② — 분모가 없다.** `docker logs wms-gateway-service` **3273줄**에 요청 줄 **0**,
`audience` **0**, 그 경로 **0**. ⇒ 게이트웨이는 요청당 로그를 내지 않는다.

**🔴🔴 그리고 지난 창의 내 판정을 정정한다.** 나는 *"`actuator/prometheus` 에
`gateway_jwt_audience*` 없음"* 이라고 적었다. 이번에 재니:

```
/actuator/health      ->  200   (액추에이터는 산다)
/actuator/prometheus  ->  401   <- wms · ecommerce · scm 게이트웨이 셋 다
```

⇒ **「메트릭이 없다」가 아니라 「401 이라 못 읽었다」** 였고, 그때의 「0건」은 **401 오류 본문을
grep 한 0건**이다. 부재 판정에 대리지표를 쓴 것이다.

🔵 **다음 탐침 하나로 좁혀진다**: 각 도메인 스택에 **`<domain>-prometheus` 컨테이너가 이미 있고**
그것은 게이트웨이를 스크레이프할 자격을 가진다 ⇒ `wms-prometheus` 에
`gateway_jwt_audience_total` 을 **질의**하라(내가 401 로 막힌 그 자격을 그쪽은 갖고 있다).

---

## 🔴 이 창이 **안 잰 것** (다음 집으로 가는 것)

| 항목 | 왜 못 쟀나 | 언제 풀리나 |
|---|---|---|
| 1 · 2②③ (fan mediaRefs) | 고침이 **13차 AMI 에 없다** | 다음 굽기 |
| 4 (`TASK-BE-595` AC-4) | 외부 테넌트 토큰을 일부러 만들어야 하는데 `invalid_client` 로 못 만들었다(697 ①과 같은 벽) | 토큰 발급 경로가 풀리는 창 |
| 5 (`TASK-MONO-683` AC-4) | 런북이 적은 대로 **선행(저재고 알림/야간 스윕)이 없고 아무 티켓도 안 든다** | 선행을 만드는 티켓이 생긴 뒤 |
| 9 의 «한 실행으로서의 사슬» | 신선 볼륨이 필요 | 다음 굽기 뒤 첫 부팅 |
| 10 의 «한 항해» 교집합 | 🔴 **구조적으로 영구 불가**(BFF 공개 호스트 없음) | 데모에서는 영영. 운영/로컬에서만 |

---

# 🟢 16차 창 수확 (2026-09-23 UTC · **약 5분** 소모 · 1348 → 1353 / 1800 · 잔여 447분)

## AC-0 착수 게이트 — 이번엔 «이미 열려 있었다»

🔵 소유자가 `store` 묶음으로 **이미 켜 둔 창**이었다(그래서 이 항목이 쓴 예산이 5분이다).
`console` · `console-ecommerce` 두 묶음만 얹었다.

🔴 **그리고 IP 가 바뀐 것을 「인스턴스 교체 ⇒ 신선 볼륨」으로 읽을 뻔했다** —
재 보니 인스턴스 id·AMI 가 **동일**(`i-01bf309118aceee70`, 13차)하고 **공개 IP 만** 바뀌었다
(Elastic IP 없는 stop/start 의 정상 동작). 볼륨은 그대로다.

## 🔵 그리고 「재굽기 선행」이 과했다는 것을 이 창이 확인했다

15차 창 기록과 항목 12 초안에 *"재굽기가 선행이다 — V0036 · compose · demo.env 가 **전부**
구워지는 표면"* 이라고 적었는데, 셋 중 **하나만** 맞았다:

| 바뀌는 것 | 어디서 읽히나 | 실제로 필요한 것 |
|---|---|---|
| 시드 `infra/demo/seed/*.sh` | 인스턴스 **클론** | SSM 패치 |
| `compose` · `demo.env` | 같은 클론 | SSM 패치 + 그 스택 재기동 |
| Flyway `V0036` | **이미지 안**(JAR) | 그 이미지 재빌드 |
| 프런트(콘솔·팬·스토어) | Vercel | 없음 |
| 「제목으로 건너뛰기」 류 | 기존 **DB 행** | **신선 볼륨**(≠ 굽기) |

🔴 이 창은 클론이 **GitHub 에 직접 닿는다**는 것도 확인했다(`git fetch` rc=0,
`origin/main` = `77d389b1`) ⇒ 패치 경로는 열려 있다.

---

## 🟢 항목 11 (`TASK-MONO-719` AC-3) — **닫힘. PASS, 대조군 둘.**

```
demo-corp /ecommerce/orders
    order-empty        →  있음
    other-tenant-hint  →  **있음**, 양쪽 이름(demo-corp · ecommerce) 모두
    표 행              →  0
    문구: "표시할 주문이 없습니다. / 현재 테넌트 demo-corp 에는 없습니다.
           이 화면의 데이터는 ecommerce 테넌트에 있을 수 있습니다 — …"

대조군 ①  ecommerce 로 전환   →  힌트 **사라지고** 5행      (빈 분기에만 산다)
대조군 ②  demo-corp /wms/inventory (0행)  →  힌트 **없음**   (배선된 한 장에만)
```

🔵 **대조군 ②가 이 판정의 핵심이다** — wms 도 0행인데 힌트가 없다 ⇒ 「목록이 비면 아무 데나
뜬다」가 아니라 **opt-in 경계가 화면에서 지켜진다**.

🔴 그리고 이것은 `TASK-MONO-718` 이 **FAIL 했던 바로 그 자리**다(같은 테넌트·같은 URL).
718 은 «자격» 을 물었고 719 는 «이 테넌트에 지금 없다» 를 말한다.

---

## 🔴 항목 12 (`TASK-MONO-717` AC-1) — **닫힘. 판정 FAIL.** → `TASK-MONO-721`

### 🟢 맞았던 것 둘 (라이브 확증)

```
등록 전 토큰  →  401 {"error":"invalid_client"}       ← AC-0 의 저장소 판정이 사실이었다
등록 후 토큰  →  200

scope 생략            →  scope 클레임 **비어 있음**
scope=internal.invoke →  "scope":["internal.invoke"]
```

🔴 **「생략하면 서버가 등록 스코프를 전부 준다」는 거짓이었다.** 717 은 그 기본에 기대지 않는
쪽을 골랐고, 이 측정이 그 선택을 **필수**로 만든다.

### 🔴🔴 틀렸던 것 — 주소를 **게이트웨이**로 준 것

```
게이트웨이 경유 (스코프 실은 토큰으로도)  →  403 TENANT_SCOPE_DENIED
product-service → account-service 직접     →  000 (네트워크 경로 없음)
```

- `JwtAuthenticationFilter` 가 위조 `X-Tenant-Id` 를 제거하고 **검증된 토큰의 `tenant_id`** 로 주입
- `TenantScopeGuard` 는 그 헤더가 **있으면** 경로 `{tenantId}` 와 같기를 요구(없으면 건너뜀)
- 자격은 `global-account-platform`, 경로는 `ecommerce` ⇒ **영원히 불일치**
- 네트워크: `ecommerce_ecommerce-net` vs `iam_iam-e2e`·`traefik-net` ⇒ **공유 0**

🔵 **V0019 헤더의 «수신 측은 테넌트를 핀하지 않는다» 가 낡았다** — 717 은 그 문장을 인용해
테넌트를 골랐고, 그 인용이 FAIL 의 절반이다.

### 🔵 실험 중에 밟은 함정 하나 — **`docker exec` 에 `-i` 가 없으면 stdin 이 안 간다**

heredoc 으로 넘긴 `INSERT` 가 mysql 에 **도달조차 못 했는데 `rc=0`** 으로 끝났다. 그래서
「등록했는데도 401」이라는 **가짜 결론**을 한 번 냈다. 🔵 갈라 준 것은 **대조군**이었다 —
`account-service-client` 로는 같은 경로·같은 비밀이 **200** 이었다.
🔴 규칙: `docker exec` 로 stdin 을 먹이려면 **`-i`**. 없으면 조용히 아무것도 안 하고 성공한다.

### 🔵 정리

손으로 넣은 `oauth_clients` 행은 **지웠다**(남은 행 0) — Flyway 가 모르는 행이고 남겨도
프로비저닝은 여전히 실패하므로 이득 0, 드리프트만 남는다.
🔵 창은 **소유자 것**이라 끄지 않았다.

---

## 🔴 이 창이 안 잰 것

| 항목 | 왜 | 언제 |
|---|---|---|
| 1 · 2②③ (fan mediaRefs) | **신선 볼륨** 필요(굽기가 아니라 인스턴스 교체가 사는 것) · fan 묶음도 안 띄웠다 | 다음 교체 |
| 4 (`TASK-BE-595` AC-4) | 외부 테넌트 토큰 발급 경로 미해결 | 그 경로가 풀리는 창 |
| 5 (`TASK-MONO-683` AC-4) | 선행(저재고 알림/야간 스윕)을 아무 티켓도 안 든다 | 선행 티켓 뒤 |
| 9 의 «한 실행으로서의 사슬» | 신선 볼륨 | 다음 교체 |

---

# 🔎 창 준비 실측 (2026-09-23 UTC) — **창을 열지 않고, 예산 0분**

> 🔵 **왜 이 절이 있나.** 위 § 「이 창이 안 잰 것」 표는 남은 항목마다 *«언제»* 를 적었는데,
> 그 «언제» 의 근거가 **전부 산문 인용**이었다. 이 세션이 그것을 **저장소에서 직접 쟀다**
> — EC2 를 켜지 않았고 예산을 쓰지 않았다(§ Failure Scenario 1 을 지킨 채로).
> 🔴 **그리고 표의 둘이 틀렸다.**

## 0단계 — 「AMI 의 조상인가」가 **유효한 술어인가**부터 확인했다

```
deployed-ami.env   AMI_ID=ami-0e7232cd54e3d9fc9 · REPO_COMMIT=1d47c5ad6 · PROVENANCE=ami-tag
demo-ami.pkr.hcl   클론에서 ./gradlew bootJar  →  docker compose build (40여 이미지)
                   + 서드파티 image: 만 pull (--ignore-buildable)
```

⇒ 서비스 이미지는 **그 커밋에서 빌드된다**(레지스트리의 낡은 `:latest` 를 당겨 오는 것이
아니다). 그러므로 **«그 커밋의 조상 = 도는 이미지에 들어 있다»** 가 참이다.
🔵 이 확인을 먼저 한 이유: 이 저장소가 이름 붙인 «pull 한 체크아웃의 빌드는 낡다» 가
바로 이 자리의 함정이고, 술어가 틀리면 아래 판정 전부가 무의미하다.

## 🟢 항목 4 — 표가 적은 사유 중 **AMI 조건은 해소됐다**

항목 본문이 *"**먼저** 확인하라 — 이 수정이 머지된 뒤 구운 AMI 인지"* 라고 시킨 대조:

```
23f416e64  fix(ecommerce): TASK-BE-595 — 테넌트 거절을 403 TENANT_FORBIDDEN 으로   (2026-09-16)
   → git merge-base --is-ancestor 23f416e64 1d47c5ad6   ⇒ rc=0  🟢 구워져 있다
3c946e6c2  696 phase 1 (aud allowlist, SHADOW)                                    🟢 구워져 있다
3cc659375  712 (console-bff 가 엣지로 aud 를 본다)                                 🟢 구워져 있다
```

⇒ **«옛 AMI 라 401 이 나온다» 는 이제 가능한 설명이 아니다.** 창에서 401 을 보면 그것은 결함이다.

### 🔵 그리고 표가 적은 진짜 막힘(«외부 테넌트 토큰 발급 경로 미해결»)은 **①에 한해 풀렸다**

16차 창이 그 경로를 **우연히 만들어 두었다** — `product-service-client` 는
`tenant_id=global-account-platform` 이고, ecommerce 게이트웨이에게 그것은 **외부 테넌트**다.

```
curl -u 'product-service-client:secret' -d grant_type=client_credentials \
     -d scope=internal.invoke  http://iam.<domain>/oauth2/token      → 200 (16차에서 실측)
```

🔴 **다만 판정을 오염시킬 수 있는 것이 하나 있다** — 이 토큰은 `roles` 가 없다. 계약 § 규칙 6 이
*"role 또는 scope 중 하나라도 유효하면 admit"* 이므로, 거절이 **테넌트 때문인지 role/scope
때문인지**가 갈리지 않으면 ①은 판정이 아니다. ⇒ **본문의 `code` 로 가른다**:
`TENANT_FORBIDDEN` 이어야 PASS 이고, `UNAUTHORIZED`(401) 면 FAIL, 그 외 403 코드면
**«측정 불가»** 로 적는다(«실패» 가 아니다).

🔴 **②는 여전히 막혀 있다.** 콘솔이 권한 문구를 보이는지는 «엔타이틀먼트 없는 테넌트의
콘솔 세션» 이 있어야 하는데, `TASK-PC-FE-292` 가 고쳐진 뒤로 평소 경로에는 그런 세션이 없고
(테넌트 스위처가 주는 `demo-corp`·`ecommerce` 는 **둘 다 엔타이틀드**다 — 719 가 실측),
만드는 경로를 아무 티켓도 안 든다. ⇒ **①만 닫고 ②는 열어 둔다.**

## 🔴🔴 항목 5 — 표의 *"선행 티켓 뒤"* 는 **과했다. 지금 창이면 된다**

> 🔴🔴 **이 절의 결론은 철회됐다 — 맨 아래 § 같은 날 두 번째 정정을 보라.** 「신선 볼륨 불필요」만 참이고 「지금 창이면 잰다」는 거짓이다(683 의 **09-18 창**이 ①을 이미 0건으로 쟀고, 그 0 의 이유까지 적었다).

항목 본문의 걱정은 *"`SUP-001` 매핑은 새 시드가 돌아야 생긴다 — AMI 가 시드된 데이터를
굽는다면 재굽기 뒤의 창"* 이었다. 둘 다 반증된다:

```
a7a8f4e67  fix(demo): TASK-MONO-683 — scm 데모 시드를 wms 코드(SUP-001/SKU-APPLE-001)로
   → git merge-base --is-ancestor a7a8f4e67 1d47c5ad6   ⇒ rc=0  🟢 구워진 클론에 있다
```

그리고 **시드 스크립트 자신이** 옛 볼륨에서의 동작을 적어 두었다
(`infra/demo/seed/seed-scm.sh:109`):

> *"🔵 **683 이전 볼륨**(공급사 `SUP-DEMO-01` 이 이미 있는 DB)에서는 `SUP-001` 이 **새 행**으로
> 생긴다 — 옛 행은 지우지 않는다(그 id 로 만든 옛 발주가 참조한다)."*

⇒ **신선 볼륨이 필요 없다.** 옛 볼륨에서 시드를 돌려도 `SUP-001` 은 생긴다.

🔵 **남는 것은 ①의 값이 0일 가능성뿐이고, 항목 본문이 이미 그 경우를 닫는 법을 적어 뒀다** —
*"0건이면 그 사실과 `seed-wms.sh` 이후 `SKU-APPLE-001` 가용재고를 적는다."*
즉 **0 도 판정이다.** «선행 티켓이 없어서 못 잰다» 가 아니라 «재면 0 이고, 그 0 이 다음
티켓의 입력» 이다.

## ⏸️ 신선 볼륨을 **진짜로** 기다리는 것 — 둘뿐이다

| 항목 | 왜 신선 볼륨인가 |
|---|---|
| **2 ②③** | `publish_artist_post` 가 **제목으로** 건너뛰므로 기존 DB 글에는 사진이 안 붙는다 |
| **9 의 «한 실행으로서의 사슬»** | 「생성 블록 삭제 → 시드 계속 → 사후조건 빨강 → 드라이버가 도메인 실패 보고」를 한 실행으로 봐야 한다 |

## 🔵 그리고 창이면 함께 닫을 수 있는 것 하나 더 — `TASK-MONO-722` AC-0

읽기 전용 한 줄이고, 다음 창에서 **같이** 재면 비용이 0 이다:

```sql
SELECT version, checksum, installed_on FROM flyway_schema_history WHERE version = '19';
```

🔴 이 집의 항목으로 **넣지 않는다** — 722 는 살아 있고, § Failure Scenario 2 가 그것을 금한다.
여기 적는 이유는 «같은 창에서 같이 재라» 는 **절차 메모**이지 의무의 이전이 아니다.

## 🔴 위 § Status 산문의 정정

- *"남은 항목 **1** · 2②③ · 4 · 5 · 9의 사슬"* — **항목 1 은 닫혔다**(2026-09-22, 판정 FAIL + 시드 고침).
  16차 창을 적으면서 옛 목록을 그대로 옮겼다.
- *"**신선 볼륨**/선행 티켓 대기"* — **4 와 5 는 둘 다 아니다**(위 실측). 신선 볼륨을 기다리는 것은 **2②③ 과 9의 사슬뿐**이다.

## 🔵 예산

```
2026-09-23 실측:  state=stopped · used=1448 / 1800  ⇒ 잔여 352분
```

🔴 16차 창 기록의 *"잔여 447분"* 은 그 시점의 값이고 **지금은 낡았다**(그 뒤 창이 70분 더 돌았다).
이 티켓만을 위해 켜지 마라(§ Failure Scenario 1) — 위의 **4① · 5 · 722 AC-0** 은 다음에 창이
열릴 때 **얹는** 것이다.

---

# 🔴🔴 같은 날 두 번째 정정 (2026-09-23) — **위 § 창 준비 실측의 「항목 5」가 틀렸다**

## 무엇을 틀렸나

§ 창 준비 실측은 항목 5 에 대해 *"표의 「선행 티켓 뒤」는 **과했다**. 지금 창이면 된다"* 라고 적었다.
**그 문장은 거짓이다.** `TASK-MONO-683` 자신의 § 창 실측(**2026-09-18** 둘째 창)이 ①을
**이미 쟀다**:

```
SCM 보충 운영 (재고보충 추천)   08:14 UTC · 테넌트 demo-corp 적용 확인 후
  → 표시할 보충 추천이 없습니다.        (표 행 0)
```

그리고 **이유까지** 적어 뒀다 — 화면 자신의 문구가 말한다: *"**wms 저재고 알림이 만든** 보충
추천을 검토하고 승인…"* ⇒ 제안은 **wms 저재고 알림 또는 IVS 야간 스윕**에서만 생기고,
시드만 돈 데모에는 그 트리거가 일어난 적이 없다. 그래서 683 은 못 박았다:

> 🔴 *"**그러므로 다음 창에서 그냥 다시 봐도 또 0건이다.** 이 칸을 열려면 **먼저 제안을 만드는
> 경로**가 있어야 한다 — 후보 둘: ⓐ 시드가 저재고 상태를 만들어 알림을 유발한다
> ⓑ IVS 스윕을 수동 트리거한다."*

⇒ **표의 *"선행 티켓 뒤"* 가 옳았다.**

## 🔴 왜 틀렸나 — 봐야 할 것을 안 봤다

항목 5 의 **본문**과 § 「이 창이 안 잰 것」 **표**는 읽었지만, 그 항목의 **출처 티켓
(`TASK-MONO-683`)의 창 기록은 열지 않았다.** 본문은 이 항목이 수령된 **2026-09-16** 시점의
글이고, 그 뒤 **09-18 창이 실제로 재서** 상태를 바꿔 놓았다. 🔵 이 저장소가 이름 붙인
**«내 티켓이 인용한 문서를 열어 봤지만 그 문서가 낡았는지는 안 쟀다»** 의 거울상이다 —
이번엔 **인용한 문서가 낡은 것이 아니라, 더 새 기록을 안 열었다.**

## ✅ 그래도 살아남는 것 (섞지 않는다)

| 주장 | 판정 |
|---|---|
| 굽기가 클론에서 빌드하므로 「AMI 커밋의 조상 = 도는 이미지에 있다」 | ✅ 유효 |
| 항목 4 의 **AMI 조건 해소**(`23f416e64` 가 조상) | ✅ 유효 |
| 항목 4 ①의 외부 테넌트 토큰 재료가 이미 있다(+`code` 로 가르라) | ✅ 유효 |
| 항목 5 에 **신선 볼륨은 필요 없다**(`SUP-001` 이 옛 볼륨에서 새 행으로 생긴다) | ✅ 유효 — 🔵 다만 **이것이 막던 것이 아니었다** |
| 항목 5 **「지금 창이면 잰다」** | 🔴 **거짓** |
| 항목 5 **「0 도 판정이다」** | 🔴 **거짓** — 그 0 은 **09-18 에 이미 기록됐다.** 다시 적는 것은 아무것도 안 닫는다 |

## 🔵 그래서 항목 5 에 **창이 할 수 있는 일은 하나 남았다**

항목 본문이 요구한 *"0건이면 그 사실과 **`seed-wms.sh` 이후 `SKU-APPLE-001` 가용재고**를 적는다"*
중에서, **가용재고 수치가 09-18 기록에 없다.** 그 한 줄은 다음 창에서 싸게 얻을 수 있고,
**선행 경로(ⓐ/ⓑ)를 설계하는 쪽의 입력**이 된다.

🔴 ②③(승인 → confirm → ASN)은 **선행이 생기기 전까지 측정 불가**다. 다시 열지 마라.

## ⇒ 다음 창의 수확 목록 (정정판)

```
얹을 것:   항목 4①  ·  항목 5 의 «SKU-APPLE-001 가용재고» 한 줄  ·  TASK-MONO-722 AC-0
기다리는 것: 항목 2②③ · 항목 9의 사슬        (인스턴스 교체)
            항목 5 ②③                        (제안을 만드는 선행 경로)
```

## ✅ 항목 17 (닫힘 2026-09-26 UTC 소유자 결정 — ⚪ 측정 불가 + 이유, § 16차 창 수확) — `TASK-BE-608` AC-4: 늦은 재제출 오탐의 빈도 (2026-09-26 수령)

- **왜 재나**: `TASK-BE-606`(자동 잠금을 재사용 **횟수**로 가르는 변경)이 후속으로 남긴 넷 중 하나 —
  콘솔이 refresh 응답을 잃으면 쿠키를 그대로 두고(`console-web/src/shared/lib/session-refresh.ts:229-231`)
  몇 분 뒤 같은 토큰을 다시 낸다. 30초 유예(`auth.refresh-token.reuse-grace-seconds`) 밖으로 밀린
  재시도는 **재사용 1건**(패밀리 폐기 + ALERT, 잠금은 아님)으로 잡힌다 — 사용자는 로그아웃되지만
  계정이 잠기지는 않는다. 빈도가 ⚪ 미측정 상태다(`TASK-BE-608` § Goal ④).
- **무엇을 재나** (창이 서야 잴 수 있는 것 — 실제 트래픽/로그 필요):
  ① 콘솔 서버 로그의 `refresh_error`(`console-web/src/shared/lib/session-refresh.ts` 주변, 정확한
  로그 키는 그 창에서 grep 으로 확정) 빈도 — 응답 유실이 실제로 얼마나 자주 나는가.
  ② web-store(있다면 동일 계열 프런트)의 `refresh_failed` 로그 빈도 — 같은 클라이언트 경쟁 모양이
  콘솔 밖에서도 나는지.
  ③ 같은 창에서 security-service 의 `auth.token.reuse.detected` **건수**(= 실제로 패밀리가 폐기된
  횟수) 대 `TokenReuseRule` 이 **단일 ALERT**(잠금 없이 끝난 건, 1시간 안 2건째로 안 이어진 건)로
  끝난 비율 — 늦은 재제출이 "재사용 1건 = ALERT, 잠금 없음"으로 무해하게 끝나는 비율의 대리 지표.
- 🔴 **유효성 술어**: ①②가 0이면 "오탐이 없다"가 아니라 "그 창에 응답 유실이 없었다" — 판정 불가로
  적는다(트래픽 의존 사건, 시드로 만들 수 없다). ③의 분모(`auth.token.reuse.detected` 총건수)가
  0이면 비율 자체가 정의되지 않는다 — "이 창에 재사용이 없었다"로 적는다.
- 🔵 **창이 필요한 이유**: 이것은 실제 사용자/클라이언트 경쟁이 만드는 트래픽 패턴이고, 시드 스크립트로
  합성할 수 없다(합성 `auth.token.reuse.detected` 로는 "늦은 재제출"이라는 **원인**을 못 판별한다 —
  이벤트 자체가 유예 창 안/밖을 구분해 담지 않는다, `auth-events.md` § auth.token.reuse.detected 참조).
  로컬 compose 로도 재현 가능은 하다 — 응답 유실을 인위로 만들면(예: 콘솔 API 라우트에 지연/장애
  주입) 창 없이도 ①③을 재현할 수 있을 것이나, 그 인위성 자체를 판정문에 적어야 한다.
- 출처: `projects/iam-platform/tasks/ready/TASK-BE-608-…` § Goal ④ · § Scope 포함 ④. 🔴 **Failure
  Scenario 대조 없음** — 608 은 이 AC 를 "측정값 또는 «측정 불가 + 이유»"로 닫는 것을 허용했고,
  이 항목으로의 이관 자체가 그 이유다(창이 서야만 잴 수 있다).

### 🔵 소유자 결정 (2026-09-26 UTC) — 항목 17 닫힘, ⚪ 측정 불가 + 이유

트래픽 의존 측정이라 합성으로 만들 수 없다(합성 `auth.token.reuse.detected` 로는 «유예 창 안/밖»이라는 원인을 못 구별한다 —
위 유효성 술어 그대로). 16차 창은 실제 재사용 **0**건을 관측했다 — `TOKEN_REUSE` 4행은 전부 `TASK-BE-602` 의 합성 이벤트였다.
콘솔의 `refresh_error` 로그는 console-web(Vercel)에 남고 이 인스턴스에서는 측정 경로가 없다. ⇒ 더 연다고 다음 창이 새 답을
낼 구조가 아니라서 닫는다 — `TASK-BE-608` AC-4 는 이 닫힘을 그 근거로 삼는다.

## ✅ 항목 18 (닫힘 2026-09-26 16차 창 — 모집단 0 · 구조적 재현 성립 · ② 구현 = `TASK-BE-611` 기안, § 16차 창 수확) — `TASK-BE-605` ② (iii): 소셜 신원 조회를 **client 테넌트로 한정**하면 누가 영향을 받는가 (2026-09-26 수령)

- **왜 재나**: 소유자 결정(2026-09-26 UTC) = ② (iii) «소셜 신원 조회를 시작 client 의 테넌트로 한정» — 단 **모집단을 먼저 잰다**.
  지금 조회는 전역이다(`SocialIdentityRepository.findByProviderAndProviderUserId` — `OAuthLoginUseCase.java:250` · `SocialLoginSteps.java:47`)
  인데 스펙(`multi-tenancy.md` § 적용 범위 `social_identities` 줄)과 unique 인덱스(`V0007__add_tenant_id_to_auth_tables.sql:49`
  `(tenant_id, provider, provider_user_id)`)는 **테넌트별**이다. 그리고 세션 테넌트는 신원 행이 아니라 **시작 client** 로 찍힌다
  (`SocialLoginBrowserController.java:185`). 한정하면 «다른 테넌트에서 만든 신원으로 이 client 에 들어오던 사람» 은 그 테넌트에서
  **새 계정으로 가입**된다(폼 로그인의 BE-604 결과와 같은 모양) — 그 사람이 몇 명인지가 결정의 실질 입력이다.
- **무엇을 재나** (`auth_db`, 계정 = `auth_user`/`auth_pass` · ③ 은 `account_db`):
  ① **전역 조회가 이미 모호한 신원** — 같은 제공자 사용자가 둘 이상의 테넌트에 신원 행을 가진 수(전역 조회가 결과 둘 = 지금 JPA 가
  `IncorrectResultSize` 로 던지는 모양):
  ```sql
  SELECT provider, provider_user_id, COUNT(DISTINCT tenant_id) AS tenants
  FROM social_identities GROUP BY 1, 2 HAVING tenants > 1;
  ```
  ② **신원 테넌트 ≠ 세션 테넌트** — 신원이 있는 계정의 미러 행(`refresh_tokens.tenant_id` = 세션 테넌트, BE-604) 중 신원 행과 테넌트가 다른 것.
  🔴 계정이 폼 자격도 가지면 그 세션은 폼일 수 있다 ⇒ **자격 없는 계정(소셜 전용)** 과 **자격 있는 계정** 을 **따로** 적어라:
  ```sql
  SELECT si.tenant_id AS identity_tenant, rt.tenant_id AS session_tenant,
         (SELECT COUNT(*) FROM credentials c WHERE c.account_id = si.account_id) > 0 AS has_credential,
         COUNT(DISTINCT si.account_id) AS accounts, COUNT(*) AS rows_
  FROM social_identities si JOIN refresh_tokens rt ON rt.account_id = si.account_id
  GROUP BY 1, 2, 3 ORDER BY accounts DESC;
  ```
  판정 = `identity_tenant <> session_tenant AND has_credential = 0` 의 `accounts` 합 (= 한정하면 **새 계정으로 갈라질** 소셜 사용자).
  ③ **신원 테넌트 ≠ 계정 테넌트** — `SELECT account_id, tenant_id FROM auth_db.social_identities` 와 `SELECT id, tenant_id FROM account_db.accounts`
  를 `account_id = id` 로 대조(교차 스키마 조인 권한이 없으면 두 번 뽑아 대조). BE-507 이전 계정은 계정 행이 `fan-platform` 인데 신원 행이
  다른 테넌트일 수 있다(`OAuthLoginUseCase.java:272-275` 주석) — 한정 후 이들이 어느 테넌트로 들어가야 하는지의 입력.
- 🔴 **유효성 술어**: ① `SELECT COUNT(*) FROM social_identities` = **0 이면 전 항목 «측정 불가»** 다(«모집단 없음» 이 아님 — 데모는 소셜
  로그인을 시드하지 않고 실제 구글/카카오 로그인이 필요하다). ② 는 조인 결과 행 > 0 이어야 한다 — 0 이면 «세션이 없었다».
  🔴 BE-603 이전 미러 행은 `account_id` 가 **이메일**이라 조인에서 빠진다(항목 16 과 같은 NULL 코호트) — 빠진 수(`refresh_tokens` 중
  `account_id LIKE '%@%'`)를 함께 적어라.
- 🔴 **이 측정이 답하지 못하는 것**: 데모에는 실사용자가 없다 ⇒ 0 이 나와도 «운영에 없다» 가 아니라 «데모에 없다» 다. 그 경우 답은
  **구조적**이다 — «한 사람이 두 테넌트 client 에서 같은 구글 계정으로 로그인하면 지금은 한 계정(첫 신원의 테넌트)으로, 한정 후에는
  테넌트마다 한 계정으로 들어간다» 가 코드 판독상 참이다. 창에서 이것을 **직접 재현**(한 구글 계정으로 스토어 → 팬 소셜 로그인 → ② 쿼리에
  그 계정이 `identity_tenant=ecommerce, session_tenant=fan-platform` 으로 찍히는가)하면 그것이 판정의 실질 입력이다(항목 16 ③ 과 같은 방식).
  🔴 BE-605 의 SSO 게이트가 머지된 이미지에서 재현할 것 — 게이트 이전 이미지는 두 번째 client 에서 로그인 화면이 아예 안 뜬다.
- **결과 뒤에 할 일 (🔴 이 항목이 의무를 든다)**: 측정(또는 «측정 불가 + 구조적 답») 을 적은 뒤 `projects/iam-platform/tasks/ready/` 에
  **② 구현 티켓을 기안**한다 — 조회 한정 + 한정 미스 시 동작(그 테넌트에서 가입) + 기존 교차 신원의 이관 여부. `TASK-BE-605` 는 ② 구현을
  들고 있지 않다(그 티켓 AC-2 가 이리로 넘겼다) — 이 항목이 닫히며 기안하지 않으면 그 의무는 아무 큐에도 없다.
- 출처: `projects/iam-platform/tasks/review/TASK-BE-605-…` § AC-0 ② · AC-2.

---

## 🔵 소유자 결정 (2026-09-26 UTC) — 상설 유지

오늘 항목 14 · 17 · 18 이 닫혀 새로 들어온 열린 항목이 없지만, 이 티켓은 **닫지 않고 상설로 둔다**(§ «할 일이 없다» 가 정상 상태다). 이유: 다음 창들(17차 이후)에서 닫히는 티켓이 넘길 측정이 또 생긴다 — 그때마다 집을 새로 만드는 것보다 이 집이 받는 편이 의무 유실이 적다.

# ⏳ Next window (re-bake) — run order (written 2026-10-14 UTC · not an item, a pointer)

> 🔴 **This section owns no obligation.** Each check stays an AC of the ticket named in its row, and its result is written to **that ticket**. This is only the order in which to open them inside one window. (Do not hold an obligation twice — this ticket's own rule.)
> The re-bake runs only on the owner's command (~55 min, billed). Everything below assumes the AMI includes `main` ≥ the commits listed in § Bake contents.
> 🟢 **This run order was executed in the 28th window (2026-10-16 UTC)** — results in § 28th window — harvest. The closed steps were removed from § Order (their text is in git history at `1c8ee22cb`); what is left there is only the open ⚪.

## Bake contents (backend — rides only in the AMI)

🟢 Every row below is in the 28th AMI `ami-00ef8cc1057cca59b` (`RepoCommit` `8905d2c29`) — each merge commit checked as an ancestor of `8905d2c29` (`git merge-base --is-ancestor`, 2026-10-16 UTC).

| Ticket | Merge | What changes live |
|---|---|---|
| `TASK-BE-631` (ADR-083 P2) | `7d663829d` · `871ab5dc9` | Business permission sets · V0052 (tables, presets, legacy-compat backfill) |
| `TASK-BE-634` | `d3a097f3f` | Adding/removing a partnership participant goes 500 → 201/204 |
| `TASK-BE-632` (ADR-083 P3) | `08f2da4a3` | 🔴 **assume roles = assigned sets ∩ subscription** · operator token loses `erp.write` · assume TTL 300 s |
| `TASK-BE-635` | `3eb6cbebd` | A client `Idempotency-Key` over 100 characters → 400 instead of 500. Bulk-lock header limit 63 (contract change) |
| `TASK-BE-633` | `ee7738b02` | Admin-role group fan-out tracked per source (`source_key` PK) · V0053 re-fan-out · a role row orphaned on a group path keeps its membership as direct |
| `TASK-BE-636` | `2d3a0661d` | A group-given membership is handed to another group that still gives the same company instead of being deleted · domain set rows also keep a membership as direct (group paths + company exit) · node grant to a target whose home company is not a membership → 422 `ORG_ADMIN_GRANT_TARGET_NOT_MEMBER`. **No migration** (owner decision 2026-10-14: in this bake) |
| `TASK-MONO-773` S2–S5 (added 2026-10-15 UTC) | S2 `19d0a0a6b` · S3 `bf2d0236f` · S4 `c98e80ab5` · S5 `f6e850cb0` | account: tenant status `PENDING` · admin: self-creation trust gate (verified email 403 · `iam`-credential operators exempt) · creator `ORIGIN` set · owned-tenant cap **3** with row lock · gateway `onboarding` scope 5/60 s per IP · `received-invitations` · `SUPER_ADMIN` creation = `PENDING` + first-admin invitation (V0055) · activation on acceptance · `tenant.activated` topic · auth: pool accounts with no / non-`ACTIVE` operator facet get the console **shell token** (no admin call; `console-eligibility` endpoint deleted). V0054 (`ORIGIN` CHECK) · V0055 run at boot |
| `TASK-MONO-792` (ADR-083 P5, added 2026-10-16 UTC) | `81d00faf5` | SCM 4 services gate per business role (8 `SCM_*_READ/WRITE`) · auth derives `SCM_OPERATOR` (`OperatorRoleDerivation.ENTRY_ROLES`) · V0056 moves every set + partnership scope holding `SCM_OPERATOR` · role-less machine client → 403 on SCM business APIs (accepted). Live checks: § Next window — `TASK-MONO-792` |
| `TASK-MONO-793` (ADR-083 P6, added 2026-10-16 UTC) | `baff0fd45` | ERP 4 services: subscription-only read removed · 4 `ERP_*_READ/WRITE` · auth derives `ERP_OPERATOR` · V0057. Live checks: § Next window — `TASK-MONO-793` |
| `TASK-MONO-794` (ADR-083 P7, added 2026-10-16 UTC) | `cf36d1609` | Finance account/ledger: `FINANCE_VIEWER` removed · 5 roles incl. `FINANCE_LEDGER_CONTROL` · auth derives `FINANCE_OPERATOR` · V0058. Live checks: § Next window — `TASK-MONO-794` |
| `TASK-MONO-795` (ADR-083 P8, added 2026-10-16 UTC) | `2eef1d91f` | 9 E-commerce services gate per area (13 roles) — order/product admin had no service check before · role-header check single-sourced (`libs/java-web` `RoleListHeader`) · auth derives `ECOMMERCE_OPERATOR` · V0059. Live checks: § Next window — `TASK-MONO-795` |
| `TASK-BE-637` (wms, added 2026-10-16 UTC) | `f17216b1d` (#4389) | inventory-service list queries (inventory · transfer · adjustment · movement) read native-query timestamps through `NativeRowValues` — the inventory list `500` (27th window) becomes `200`. Live checks: step 7 |
| `TASK-BE-638` (iam gateway, added 2026-10-16 UTC) | `a3eef4249` (#4390) | iam gateway no longer routes `POST`/`GET /internal/tenants` or `GET`/`PATCH /internal/tenants/{id}` (route `Path=/internal/tenants/*/*/**` + filter 403 `TENANT_SCOPE_DENIED`, `'*'` included). Image: `iam-gateway-service`. Live checks: step 7 |
| `TASK-BE-639` (iam auth + account, added 2026-10-16 UTC) | `0f544dd64` (#4392) | operator assume into a `PENDING`/`SUSPENDED` tenant → `400 invalid_grant` `error_description=tenant_not_active`, nothing issued (`'*'`, company and partnership host reach; workload unchanged) · account `entitled-domains` carries `tenantStatus` and `domainKeys: []` for non-`ACTIVE` (so a set write into a non-`ACTIVE` tenant answers 422 `DOMAIN_NOT_SUBSCRIBED` until reactivation; the 773 `ORIGIN` write is unaffected — no entitlement check). Images `iam-auth-service` + `iam-account-service` in the same bake (if ever split: account first). Live checks: step 7 |
| `TASK-BE-640` (iam account + admin, added 2026-10-17 UTC — **not yet baked**: after the 28th AMI) | `da99f32bb` (#4397) | account-service `/internal/tenants` lifecycle (`POST` · list · `PATCH /{id}` → `admin-service-client` only; `GET/HEAD /{id}` → + `auth-service-client`) by token `sub` allow-list, fail-closed when the keys are blank · admin `list()` 4xx no longer retried. Live checks: § Order «Still open» 9 |
| `TASK-MONO-800` (`libs/java-web-servlet`, added 2026-10-20 UTC — **not yet baked**: after `ami-01681ef55def7d15c` / `e44142d34`) | `8e74909c3` (#4431) | A header the servlet firewall rejects when it is read (UTF-8 bytes 0x80–0x9F as Tomcat delivers them — most Korean, `④`) → **400 `BAD_REQUEST`** instead of 500 `INTERNAL_ERROR`; fixed message, one `WARN`. Every servlet service that subclasses `CommonGlobalExceptionHandler`. Live checks: § Order «Still open» 12 |
| `TASK-MONO-801` (`libs/java-security-servlet` + 24 services, added 2026-10-20 UTC — **not yet baked**) | `4d35a61ee` (#4434) | A path/method the firewall rejects up front (`;` · `//` · `%25` …) → **400 `BAD_REQUEST`** written directly by a `RequestRejectedHandler` bean, instead of 401 (deny-all / authenticated chains — even with a valid token) or Boot's path-echoing body (permit-all). wms master · admin keep their nested envelope. Reachable from outside only through the iam gateway (the other six gateways already answer 400 first). Live checks: § Order «Still open» 12 |

🔵 **Owner decision 2026-10-20 UTC: do not bake for 800 · 801 alone.** Their behaviour is proven by red → green tests through the real `FilterChainProxy`; they ride the next bake that has other server changes. The console checks in «Still open» 10 · 11 · 13 · 14 need no bake.

🔴 **792–795 are one unit**: the domain services, admin (V0056–V0059) and auth must go out in the **same** bake — a split leaves the catalog advertising a split nothing enforces, or old tokens 403 on every screen. The console follow-ups `TASK-PC-FE-342` (SCM) · `343` (ERP) · `344` (Finance) · `345` (E-commerce) merge **only after** this bake's live checks pass. Owner decision 2026-10-16 UTC: `TASK-BE-637` · `TASK-BE-638` · `TASK-BE-639` also ride this bake (rows added when they merge).

The console (Vercel) has nothing to do with the bake. PC-FE-338 a `56a15be8b` · b `27cdcebd2` · c-1 `808fa9e84` → c-1 fix `ca176d570` (re-assume from the base token — before merging it, a federation e2e run on the branch was green, run 38555155825). Check the Production deployment record of each commit before the window.

## Order

🟢 **The 28th window (2026-10-16 UTC) ran steps 0–7 and the four «Next window — `TASK-MONO-792`…`795`» blocks.** The closed steps and blocks were removed here (text at `1c8ee22cb`); every result is in § 28th window — harvest, and also in the owning ticket where that ticket was still open. What follows is only what is still open.

### Still open → next window (owned by this list unless a ticket is named)

🔴 **Four of these share one cause: the demo has no unconfined `'*'` `SUPER_ADMIN` with an IdP login.** `platform@demo.com` is confined to `fan-platform` (refused at the assignment gate, before any tenant-status read). `admin` / `dev1` are `'*'`, but with a placeholder `oidc_subject` and no credential. The 28th window reached the `'*'` tenant API only through the break-glass dev `admin` path, which has no console session.
- ✅ **Owner question — decided 2026-10-17: seeded owner-only, SUSPENDED by default** (`TASK-MONO-798`: unconfined `'*'` `SUPER_ADMIN` with an IdP login, in no public doc, no fixed password in the repo — generated at boot into SSM SecureString `/portfolio-demo/owner-operator/password`; an owner-run wrapper `infra/demo/owner-operator.sh` opens and closes the window).
- 🟡 **29th window (2026-10-20 UTC) — items 1 and 3 closed, 2 and 4 still open** (§ 29th window — harvest). The bake carried 798 and the apply granted the put; the owner `activate`d once (before 07:56Z — exact time not recorded), items 1 and 3 were measured at 07:56–07:57Z, then the window idle-stopped while the owner was away; the reboot came up SUSPENDED with a new password, and the window was closed at 10:02Z before a second `activate`.
- 🟢 **30th window (2026-10-20 UTC, 10:36Z–11:17Z) — items 2 and 4 closed ⇒ items 1–4 all closed** (§ 30th window — harvest). The `'*'` cause is gone; what stays open below is 5–9 plus the console finding under item 4.
- ⏳ **Next window — once baked** (a bake containing `TASK-MONO-798`, and `terraform apply` of its one `ssm:PutParameter` grant before the boot): **activate via wrapper → measure items 1–4 → deactivate.** Owner commands (over SSM, as in the 28th): `sudo bash /opt/monorepo-lab/infra/demo/owner-operator.sh status` (confirm «password provisioned this boot: yes») → `… activate` → read the password locally with `aws ssm get-parameter --with-decryption --name /portfolio-demo/owner-operator/password` (never echo it) → items 1–4 → `… deactivate` (SUSPENDED + refresh tokens revoked; an already-minted operator access token lives to its `exp`).

1. **`TASK-BE-639` check 1 — `'*'` × `PENDING` / `SUSPENDED`** (`'*'` cause). Exchange an unconfined `'*'` operator's base token with `audience` = a `PENDING` and a `SUSPENDED` tenant → `400 invalid_grant`, `error_description` exactly `tenant_not_active`, no `access_token`. Until then it is covered only by the BE-639 auth IT. (A `PENDING` tenant has to be made again through the 773 AC-4 flow — the 28th's throwaways are all `SUSPENDED` now.)
   - ✅ **Closed 2026-10-20 UTC (29th).** As `star-owner@demo.com` (IdP login, base token `tenant_id=iam`): created `m798-0756-pend` (201 `PENDING`) and `m798-0756-susp` (201 `PENDING` → PATCH 200 `SUSPENDED`). Exchange with `audience` = each → **400 `{"error":"invalid_grant","error_description":"tenant_not_active"}`, no `access_token`** (2/2). Control `audience=demo-corp` → 200 with a token (`expires_in` 299).
2. **`TASK-BE-632` AC-6, kind 4 — subscription cancel** (`'*'` cause). Throwaway tenant: subscribe → an operator assumes → cancel the subscription → record when the existing token stops (expected ≤ `exp` + 60 s, at most about 360 s; the other three kinds measured `exp` + 62–63 s in the 28th) and that a re-exchange carries no role of that domain at once. No demo identity can subscribe a throwaway tenant.
   - ✅ **Closed 2026-10-20 UTC (30th).** As `star-owner@demo.com` (owner decision: a NEW `finance` subscription on a seeded tenant, then cancel). `umbrella-corp` already had `finance` (409 `SUBSCRIPTION_ALREADY_EXISTS`) → `globex-corp` `POST /api/admin/subscriptions` 201 `ACTIVE`. Assume `globex-corp` → 200, roles = `FINANCE_OPERATOR` + 5 (`ACCOUNT_READ/WRITE` · `LEDGER_READ/WRITE/CONTROL`), `entitled_domains` ∋ `finance`; finance `GET /api/finance/ledger/periods` 200. Cancel 11:04:51Z (`PATCH …/globex-corp/finance/status {CANCELLED}` 200; token `exp` 11:09:49Z). **Immediate re-exchange → 200 with FINANCE roles 0, `entitled_domains` without `finance`, periods 403.** The existing token turned **401 at cancel + 368 s = `exp` + 70 s** (10 s poll — the previous poll, ≈ `exp` + 60 s, was still 200) — the same shape as kinds 1–3 (`exp` + 62–63 s) within the poll resolution, consistent with «`exp` + 60 s, at most about 360 s».
3. **`TASK-MONO-795` wildcard non-regression** (`'*'` cause). The `'*'` `SUPER_ADMIN` overview ecommerce card (`GET /api/admin/products?page=0&size=1`) still `ok`; `GET /api/admin/users` with `'*'` still 403. (28th: as `demo@demo.com` in `demo-corp` the card renders «상품 수 0». That is correct data — `demo-corp` has 0 products, http 200, `ecommerce` has 42 — not this check.)
   - ✅ **Closed at the API level 2026-10-20 UTC (29th).** The `'*'` token = the IdP base token exchanged with `audience='*'` → 200, claims `tenant_id='*'`, no roles. With it: `GET /api/admin/products?page=0&size=1` → **200** (`totalElements` 0) · `GET /api/admin/users?page=0&size=1` → **403** `ACCESS_DENIED`. 🔴 **Premise correction**: this operator's IdP **base** token is `tenant_id=iam`, not `'*'` — with the base token both endpoints are **403 `TENANT_FORBIDDEN`** («tenant_id 'iam' is not allowed»). The console sends the base token for a non-switched operator (`session.ts` `getDomainFacingToken`), so whether the overview card actually renders `ok` for this operator depends on whether the console assumes `'*'` first — ⚪ the card itself was not looked at (item 4's browser session did not happen). Look at it in the same session as item 4.
   - 🔵 **30th (owner browser): the «`'*'` overview card» path does not exist in the console today.** The switcher offers no `*` for this operator (owner: «* 없어») — `selectableTenants()` is the union of the registry products' tenants, which never contains `*`. Without a selected tenant the overview renders the catalog grid, not the cards (PC-FE-310). With `demo-corp` selected the card is the `demo-corp` card, and it read `DOWNSTREAM_ERROR` because the `ecommerce` bundle was not booted in that window (`console` · `console-finance` · `console-erp` only) — not a verdict on 795. The API-level verdict above stands; the card-level wording of this item describes a path the console cannot take.
4. **`TASK-MONO-773` AC-4 — the console `/tenants` screen** (`'*'` cause). The 28th measured AC-4 at the API level only; the «관리자 대기» row and the row actions (re-invite · 정리 · 활성화 마치기) on screen are unmeasured.
   - ⚪ 29th: not measured (owner browser session did not happen before the window closed). Item 2 also ⚪ — the owner chose «a NEW `finance` subscription on a seeded tenant (`umbrella-corp`, then `globex` / `initech` / `acme` / `ip-pilot` if already subscribed) → cancel», script ready, but the second `activate` did not come. Next window: boot `console` + `console-finance` (≈2 min to ready), `activate`, run both.
   - ✅ **Closed 2026-10-20 UTC (30th, owner browser as `star-owner@demo.com`).** With `demo-corp` selected, `/tenants` lists `m798-0756-pend` · B2B · **«관리자 대기»** · created 2026. 10. 20. 16:57:04 (KST), row actions **«다시 초대» · «정리»**, no «활성화 마치기» — correct for `firstAdmin.state = INVITED` (that action is for an `ACCEPTED` invitation on a still-`PENDING` tenant, admin-api § PATCH tenants R4). Not clicked.
   - 🔴 **Finding (console copy, not 773):** with no tenant selected, `/tenants` says «상단의 테넌트 스위처에서 플랫폼 스코프(*)를 선택한 뒤 다시 시도하세요», but the switcher has no `*` (see item 3). The page's real gate is «any active tenant» (`tenants/page.tsx` `state.noTenant`), so the instruction cannot be followed as written — the owner reached the list by picking `demo-corp`. The same sentence is in `tenants/[tenantId]/page.tsx` · `org-hierarchy/page.tsx`, and «SUPER_ADMIN 은 플랫폼 스코프 *» in `operator-groups/page.tsx` · `domain-permission-sets/page.tsx`. → owned by **`TASK-PC-FE-353`** (drafted 2026-10-20 UTC; AC-0 measures, then the owner chooses copy vs offering `*`).
5. **`TASK-MONO-773` AC-3 live** — `demo@demo.com` creates a second company → `201`, and its `demo-corp` role and set rows are byte-unchanged (`SELECT` before / after). Not run in the 28th by choice: it adds a membership and an owned tenant to the shared demo account.
6. **`TASK-BE-636` ① ②** — needs a company U in the operator's `operator.manage` scope other than `demo-corp`; `demo@demo.com` has none. ① G1 · G2 both give U → revoke G1 → the member is still in U, audit `detail` `reownedToGroup=1` → revoke G2 → U is gone. ② a member holding only a set in U, U from G → revoke G → the membership stays (`keptAsDirect=1`). (③ optional — not run.)
   - ✅ **Closed 2026-10-20 UTC (31st).** As the `'*'` operator, member = `demo-assigned-only` (seed-only, cannot log in), groups G1–G3 in `acme-corp`, U = `initech-corp`. ① G1 + G2 give U → revoke G1 (204) → still in U → audit `downstream_detail` `grant=…,reownedToGroup=1` → revoke G2 (204) → U gone, detail `grant=…` with no reowned/kept. ② G3 gives U + a DIRECT `wms-inventory-read` set in U → revoke G3 (204) → membership stays (`sets=wms-inventory-read[DIRECT]`) → detail `grant=…,keptAsDirect=1`. Detail read from `admin_actions` on the host (not exposed by `GET /api/admin/audit`). Undo: G1–G3 deleted, the member back to `demo-corp` only (`legacy-compat[MIGRATION]` untouched) — before/after snapshots equal.
7. **`TASK-BE-634`** — add / remove a partnership participant = 201 / 204 (both were 500). `demo@demo.com` lacks `partnership.manage` in `demo-corp` (403 on the list), so it needs an operator holding it.
   - ✅ **Closed 2026-10-20 UTC (31st).** The `'*'` SUPER_ADMIN does **not** hold `partnership.manage` either (by design — admin `V0040:9-11`), so the `'*'` operator made `requester@demo.com` (`demo-requester`) a member + `TENANT_ADMIN` of `initech-corp` and `umbrella-corp`; as requester: invite `initech-corp → umbrella-corp` 201 `PENDING` → accept 200 `ACTIVE` → **participant ADD 201** (`assignedAt` returned) → **REMOVE 204**. Undo: partnership `TERMINATED` (no delete in the contract — the row stays), requester suspended → removed from both tenants (the last-`TENANT_ADMIN` guard counts ACTIVE operators only) → reactivated `ACTIVE`; memberships before = after = `demo-corp` only.
8. ~~**`TASK-FE-109` AC-1** — owned by that ticket (`review/`): 0 lines of `POST /login 405` in the store's Vercel log, plus the vitals control. Needs the Vercel request log, not the AMI.~~ ✅ **Closed 2026-10-27 UTC — ticket moved to `done/`.** Measured without a demo window: an injected `POST /login` 405 did **not** appear in either the CLI runtime log or the dashboard Logs (CDN request logs need Observability Plus), so the Vercel log cannot judge this; instead live anonymous browsing on `store.hubwang.com` showed **0** `POST /login` and every `POST /api/vitals` → 204, with 28 web-vitals function-log lines as the vitals control. Owner decision: ⚪ with that substitute instrument. Detail: the ticket's final `## CORRECTION`.
9. **`TASK-BE-640` live** (needs a bake containing `da99f32bb`; ticket stays in `in-progress/` until then): ① `docker network inspect traefik-net` — list members; for each non-IAM member check its env for an `internal.invoke` client (confirms the files-only AC-0 census). ② From a container on `traefik-net`, a `product-service-client` token (client_credentials, `internal.invoke`) directly to `http://iam-account-service:8082`: `PATCH /internal/tenants/ecommerce {"status":"ACTIVE"}` · `POST /internal/tenants` · `GET /internal/tenants` · `GET /internal/tenants/ecommerce` → each **403**; `ecommerce` still `ACTIVE`. ③ Controls: an `admin-service-client` token → 200 on `GET /internal/tenants/ecommerce` and `…/entitled-domains`; console tenant create / suspend / reactivate through admin-service still work; an IdP login (auth reads `GET /internal/tenants/{id}`) still works.
   - ✅ **Closed 2026-10-20 UTC (31st) on `ami-01681ef55def7d15c` (⊇ `da99f32bb`)** — host script over SSM, PASS 8 · FAIL 0. ② `product-service-client` (secret = app default; product-service not running in this window) → `PATCH /internal/tenants/ecommerce {ACTIVE}` · `POST /internal/tenants` (body `{}` on purpose) · `GET /internal/tenants` · `GET /internal/tenants/ecommerce` → **403 ×4**; `ecommerce` `ACTIVE` before and after. ③ `admin-service-client` → `GET …/ecommerce` 200 · `…/entitled-domains` 200 `{"domainKeys":["ecommerce","wms"],"tenantStatus":"ACTIVE"}`; admin-service tenant create / suspend measured in the 29th (`m798-0756-*`); IdP login worked on every run. ⓘ the product token's sub-resource `GET …/entitled-domains` → 200 (BE-640 out of scope). ① 🟡 **partial**: only the booted bundles were on `traefik-net` (iam 8 + erp 5 + traefik) — non-IAM members with an `internal.invoke` client: 0. The other domains' members (ecommerce · wms · scm · finance · fan) were not up, so the census is confirmed for erp only.
10. 🟢 **32nd: screen checks done — results in each ticket (AC-4 ticked; the four stay `in-progress` because their AC-0/AC-2 boxes are still empty)** — § 32nd window — harvest. **`TASK-PC-FE-342` · `343` · `344` · `345` AC-4 live** (console-only — Vercel, no bake; but the screens need a running backend, so any window): first check the Production deployment records of `ce1277dfa` (342) · `d64a6b8a7` (345) · `82d857680` (343) · `465b39a94` (344) are `success`. Then, in the console as `demo@demo.com` (`demo-corp`, legacy-compat): 🔴 **no** «업무 권한 없음» badge on any SCM · ERP · Finance · E-commerce menu (345 fixed a live false badge on all 9 E-commerce menus introduced by the 795 bake). With a throwaway subject (`requester@demo.com`, `run-338.sh`-style wrapper, restore afterwards): one area set at a time → the other areas' menus badged, that area's open; read-only set → SCM replenishment approve/dismiss and config save hidden + «조회 전용» · WMS outbound «발송 재시도» hidden without `SCM_LOGISTICS_WRITE` · `/ledger` «해소» · FX «새로고침» hidden without `FINANCE_LEDGER_WRITE` (CONTROL alone does not show them); an operator with no ERP role → the notification bell does not report `degradedDomains ∋ erp`. Results go to each ticket (345 also waits for `TASK-PC-FE-346`).
    - 🔵 2026-10-18 UTC: the four Production deployment records above were measured `success` (7128043458 · 7128901643 · 7129951620 · 7130932170) — the first check of this item is done; the screen checks remain.
11. 🟢 **32nd: 346 · 347 closed (→ done); ⚪ order status buttons (`demo-corp` has 0 orders) · approve/reject on a pending request (subject's inbox empty)** — § 32nd window — harvest. **`TASK-PC-FE-346` (E-commerce) · `347` (ERP) AC-4 live — write buttons follow the area `*_WRITE` role** (console-only, added 2026-10-18 UTC; same window and same throwaway subject as 10). Production deployments `23b0cc1bd` (7153236004) · `8a12cf6b8` (7153567913) are already measured `success`. Assign one set at a time to `requester@demo.com` (`demo-corp`), log in again (assumed token cached ~4 min), and check: ① `ecommerce-read` only — `/ecommerce/products` · `/sellers` · `/shippings` · `/promotions` · `/settlements` · `/notifications/templates` show **no** 등록/수정/삭제/상태 buttons and a «조회 전용 — …» note; opening `/ecommerce/products/new` directly shows the notice (testid `product-form-read-only`) **instead of** the form; an order detail `/ecommerce/orders/<id>` shows no 상태 변경 buttons. ② `ecommerce-order-operate` only — the order detail shows its 상태 변경 buttons; `/ecommerce/products` still shows no 등록 link. ③ `erp-read` only — `/erp/masters` (5 lists) · `/erp/approval` · `/erp/delegation` show no 등록/상신/승인/위임 생성 buttons and the «조회 전용» note. ④ the ERP approval set only — `/erp/approval` shows 상신 and (on a pending request) 승인/반려; `/erp/masters` shows no 등록. Also open 위임 생성 on `/erp/delegation` and record what the 대결자 field shows (`TASK-PC-FE-349`: expected today = the manual-id fallback with the «불러오지 못해» text). ⑤ `demo@demo.com` (legacy-compat) — every one of those buttons is visible (non-regression). Restore the subject byte-identically afterwards (same rule as 10).
    - 🟡 **④ partly, 2026-10-20 UTC (31st)** — `requester@demo.com` held **only** `erp-approval-operate` in `demo-corp` (assumed roles `ERP_APPROVAL_READ` · `ERP_APPROVAL_WRITE` · `ERP_OPERATOR`). Owner browser: «결재 요청 작성» opened (상신 visible) and its 대상 직원 + 결재선 fields are **employee lists**; 위임 생성's 대결자 is an **employee list** — 🔴 the «expected today = manual-id fallback» above is superseded by `TASK-ERP-BE-045` (#4421). The 위임 현황 filters (위임자 · 대행자) are employee lists too (owner, before the 12:28Z stop — the assumed token is cached ~4 min, so it was still the approval-only one). ⚪ not checked: 승인/반려 on a pending request · `/erp/masters` shows no 등록. Restore: `legacy-compat` cannot be re-granted through the API (400 `VALIDATION_ERROR`, admin-api.md § legacy-compat) ⇒ the five `<domain>-full` presets DIRECT (owner-approved fallback); assumed roles identical to the snapshot (42), rows **not** byte-identical until the next bake.
12. 🟡 **33rd (2026-10-23 UTC): 801 closed (→ done) · 800 ⚪ (no raw client carrying the Bearer token in this window)** — § 33rd window — harvest. **`TASK-MONO-800` · `TASK-MONO-801` AC-4 live** (⏳ **needs a bake** containing `4d35a61ee` — not the next console-only window). From the host (or through the iam gateway for 801): ① iam admin `POST /api/admin/operators/...` with a raw UTF-8 `X-Operator-Reason: ④` (not percent-encoded) → **400 `BAD_REQUEST`**, body without the value, one `WARN` in the service log, no `ERROR` (800). ② `GET /api/admin/x;y` and `/%25` through the iam gateway with a valid operator token → **400 `BAD_REQUEST`** in the common envelope (801; today 401). ③ wms master `GET /x;y` direct → 400 in the nested `{"error":{…}}` envelope. Controls: the console's percent-encoded reason still 200/201; a normal unauthenticated request still 401. Results go to each ticket (both in `review/`).
13. 🟢 **32nd (2026-10-20 UTC): 354 closed (→ done) · 353 half (screens ✅, the no-tenant notice is unreachable in the normal flow — owner to judge)** — § 32nd window — harvest. **`TASK-PC-FE-353` AC-3 · `TASK-PC-FE-354` AC-4 live** (console-only — Vercel `7201847137` · `7205740591` already `success`; needs the owner's `activate` of the `'*'` operator, same wrapper as items 1–4). Bundles: `console` (+ any domain only if the overview should render cards).
    - 353: as the `'*'` operator with **no** tenant selected, open `/tenants` · `/org-hierarchy` · `/operator-groups` · `/domain-permission-sets` → each shows the new «테넌트를 하나 선택한 뒤 …» notice (no «플랫폼 스코프(*)»); pick `demo-corp` → each list renders. `/security-settings` with `demo-corp` → the policy control (no `*` branch).
    - 354: `/operators` shows the «현재 테넌트 / 모든 테넌트» toggle (and `demo@demo.com` does **not**). «모든 테넌트» → a `'*'` operator row (e.g. `demo-platform`, home `'*'`) is listed; its company rows hide 역할 · 조직 스코프 · 업무 권한 · 내보내기. «플랫폼 운영자 등록» form is visible to the `'*'` operator only. On the `'*'` target's «업무 권한»: grant a **preset** (e.g. `wms-inventory-read`) with «모든 테넌트 (기본 제공 세트만)» → 201 and a «모든 테넌트» source appears → «모든 테넌트에서 회수» → **200/204** and the source is gone (net zero). 🔴 **Do not revoke `demo-platform`'s seeded `legacy-compat` `'*'` row** — `legacy-compat` cannot be re-granted through the API (400). Then `deactivate`.
14. 🟢 **32nd: 308 · 311 · 350 closed (→ done) · 310 half (logged-in landing ✅, anonymous sample visitor ⚪)** — § 32nd window — harvest. **Console `review/` live ACs that need only a running backend** (no bake): `TASK-PC-FE-308` AC-3 — a Korean reason on **reject** → 200 `REJECTED` (needs a pending request whose approver is the demo operator; `withdraw` was measured in the 23rd) · `TASK-PC-FE-310` AC-11 — logged in, «제품·테넌트 전체» → another tenant's button lands on that product screen; anonymous sample visitor → the section renders the sample tenants · `TASK-PC-FE-311` AC-4 — `demo@demo.com`'s 결재함 two requests show the corrected approver cell · `TASK-PC-FE-350` — the logged-in operator overview renders (no whole-overview degrade) and its AC-3 deploy state. Results go to each ticket.
15. 🟢 **33rd (2026-10-23 UTC): baked and carried** — `283fd39cf` → `ami-02b3ba00a3b2ff622`, pin #4498 (`c5e3ddad3`); every impl merge below is an ancestor of `283fd39cf` — § 33rd window — harvest. 🆕 **Next bake — what it must carry** (added 2026-10-22 UTC; owner: «굽기 전에 할 일을 먼저», so every server ticket below is merged before the bake and rides **one** bake). Already in the queue: `TASK-MONO-800` · `801` (item 12) · `TASK-BE-641` · `TASK-BE-642` (iam). New, merged 2026-10-22 UTC: wms `TASK-BE-643` (#4475 — throughput counters keep late / equal-time events) · `TASK-BE-644` (#4478 — low-stock judged at query time from the setting, `<`; **Flyway V5 drops `low_stock_flag` — roll forward only**) · `TASK-BE-647` (#4479 — inventory-service default low-stock threshold 10) · ecommerce `TASK-BE-645` (#4474 — operator cannot confirm a `BACKORDERED` order) · `TASK-BE-646` (#4480 — admin status-change cancel publishes `OrderCancelled`) · scm `TASK-SCM-BE-062` (#4477 — suggestion carries `reorderPointAtTrigger`, Flyway V4). Merged 2026-10-22 UTC as well: ecommerce `TASK-BE-648` (#4485 — operator confirm removed). Added 2026-10-23 UTC (ADR-MONO-022 D4 v2(d) ACCEPTED — X-A / Y-A): wms `TASK-BE-650` (#4488 — outbound-service consumes `ecommerce.fulfillment.cancel-requested.v1`; RECEIVED/PICKING auto-cancel, PICKED–PACKED ALERT only; **also touches `infra/demo/relay/mm2.properties`** — the ecommerce→wms relay whitelist gains the topic) · ecommerce `TASK-BE-649` (#4490 — shipping-service consumes `order.order.cancelled`: PREPARING → terminal `CANCELLED`, wms-routed → cancel request). The screens for it are already live through Vercel and only show «취소됨» once the bake runs: console `TASK-PC-FE-367` (#4492) · web-store `TASK-FE-110` (#4491). 🔴 Before baking, re-list `git log <deployed-ami commit>..origin/main -- projects/*/apps` so nothing merged later is missed.
16. 🟢 **33rd: done** — PR #4496 (`0824ed547`) merged after the bake was live; branch nightly `39957126998` · Vercel `7279505197` success; live `/wms/guide` old sentence 0, new sentences 1 each. 🆕 **Right after the bake, before the window — console copy PR** (`TASK-BE-644` § 콘솔 후속 문구): the WMS guide «고정값 · 가용 10개 이하» → «설정값 · 가용 수량이 기준(기본 10)보다 적을 때» etc. Console deploys on merge, so this must land **after** the bake goes live (earlier = the screen describes a rule the server does not run yet). User-visible copy → owner approval first.
17. 🟡 **33rd: measured and recorded in each ticket — 5 closed (→ done: `TASK-BE-642` · `TASK-BE-643` · `TASK-BE-648` · `TASK-PC-FE-367` · `TASK-FE-110`), the rest ⚪ / 🟡 / ❌ with three new tickets (`TASK-BE-651` · `TASK-BE-652` · `TASK-PC-FE-369`)** — § 33rd window — harvest (results table). 🆕 **Live checks for the 2026-10-22 batch** (each result goes to its ticket; all in `review/` unless noted):
    - `TASK-BE-643` AC-3 — after the seed's putaways / shipment, `admin_throughput_*_daily` counts = the number of `putaway.completed` / `shipping.confirmed` events (`admin_event_dedupe` by `event_type`); the outbound side had no outcome trail before the fix (BE-643 AC-0 ②).
    - `TASK-BE-644` AC-3 — change `inventory.low_stock.default_threshold_qty` (console WMS 운영설정) 10 → 25 → the console `/wms/inventory` «저재고만» result changes with no inventory event; a row with available = threshold is **not** low.
    - `TASK-BE-647` AC-2 — after the (re)started inventory-service, push one SKU below 10 → a `LOW_STOCK` alert appears without any settings change.
    - `TASK-BE-645` AC-2 — a `BACKORDERED` order (if the demo has one) → operator `POST /api/admin/orders/{id}/status {CONFIRMED}` → **400 `INVALID_ORDER_REQUEST`**.
    - `TASK-BE-646` AC-2 — cancel one `CONFIRMED` order from the console → `OrderCancelled(OPERATOR)` in `order_outbox`, payment refunded, product reservation released; plus the three-DB query in BE-646 § AC-0 ③ for orders already cancelled without an event.
    - `TASK-BE-648` AC-2 (if merged) — operator confirm of a `PENDING` order → 400.
    - `TASK-SCM-BE-062` AC-3 — a newly triggered suggestion carries `reorderPointAtTrigger` (policy SKU) and an older one has **no key**; when closing, draft the console follow-up (replenishment bar «재주문점 · 발동 재고 · 제안 수량»).
    - `TASK-BE-649` AC-3 + `TASK-BE-650` AC-3 — cancel one `CONFIRMED` order whose wms outbound is still `RECEIVED`/`PICKING` → ecommerce Shipping `CANCELLED`, an `ecommerce.fulfillment.cancel-requested.v1` row in the shipping outbox, the wms outbound order `CANCELLED` with its reservation released, and **no** false `ALERT wms outbound cancelled` in shipping-service logs (the echo). If a demo order sits at `PICKED`–`PACKED`, cancelling it must leave the wms order untouched and log `ALERT ecommerce cancel request refused after pick`.
    - `TASK-PC-FE-367` AC-3 · `TASK-FE-110` AC-3 — the cancelled order's shipping shows «취소됨» in the console (list badge, filter, overview bar) and the web-store order detail shows «주문이 취소되어 배송이 취소되었습니다.»
    - `TASK-PC-FE-358` AC-4 (logged-in WMS throughput; an empty 14-day window shows the empty text) · `TASK-PC-FE-364` AC-3 (overview cards per business permission set — the «권한 없음» table in the ticket, incl. the inferred `'*'` SUPER_ADMIN ERP row) · `TASK-PC-FE-355` AC-3 · `TASK-PC-FE-310` AC-11 (item 14) — console, no bake needed but a running backend.
18. 🆕 **Next bake — carry the 33rd window's fixes** (added 2026-10-24 UTC; merged after `283fd39cf`, so NOT in `ami-02b3ba00a3b2ff622`). wms: `TASK-BE-651` (#4503 `d10796189` — inventory reads `reservationId` from `picking.cancelled`, so a cancel while `PICKING` releases the reservation; listener failures now log their cause) · `TASK-BE-652` (#4502 `adae12ea4` — unfiltered `GET /settings` · `GET /users` no longer 500). Plus whatever ADR-MONO-084 (`TASK-MONO-806`…`812`) has merged by then — owner decision 2026-10-23: «ADR-084 는 다음 굽기에». 🔴 Re-list `git log <deployed-ami commit>..origin/main -- ':(glob)projects/*/apps/**' ':(glob)libs/**' ':(glob)infra/demo/**'` before baking. (A bare `projects/*/apps` pathspec does not match the files under it — it silently returned 6 of 15 commits on 2026-10-23.) 🟢 **34th: baked and carried** — `d5bb28f14` → `ami-0d9f8da7d20d7a180` (§ 34th window — harvest).
19. 🆕 **Live checks for that bake**:
    - `TASK-BE-651` AC-2 + the re-check of `TASK-BE-650` AC-3 — cancel one outbound that is `PICKING`/`RESERVED` (the ecommerce route of the 33rd window, or the operator REST cancel) → inventory `reservation` `RELEASED`, SKU `reservedQty` back, saga `CANCELLED`, no `Record in retry` for `wms.outbound.picking.cancelled.v1`. If the 33rd window's order `49eae2f6…` outbound is still `CANCELLATION_REQUESTED` on a reused volume, the sweeper re-emission should now release it — but a fresh bake = fresh volume, so expect a new order.
    - `TASK-BE-652` AC-2 — console WMS 운영설정 + `GET /api/wms/settings` 200 with `inventory.low_stock.default_threshold_qty`; then `TASK-BE-644` AC-3 (10 → 25 → «저재고만» changes) — 🔴 the console has no settings **write** path, so the change needs an operator API call: plan it as a user-run command.
    - `TASK-MONO-814` AC-6 + `TASK-PC-FE-369` AC-2 — fix already live on Vercel (`45fa46428`, no bake needed): open the logged-in `demo-corp` overview **cold** (first render after >15 s) → all six cards draw a value (no `DOWNSTREAM_ERROR`, no «WMS 점검 불가»), and CloudWatch `AWS/Lambda Throttles` for `portfolio-demo-control` = 0 in that minute (`aws cloudwatch get-metric-statistics … --period 60`). Then `TASK-PC-FE-364` AC-3 again.
    - Still open from the 33rd: · the ⚪ rows that need demo data (`TASK-BE-645` backorder, `TASK-BE-647` SKU below 10, `TASK-SCM-BE-062` a triggered suggestion) — create that data deliberately in the window or record ⚪ again.
20. 🆕 **Checks the 35th window left for the next window** (added 2026-10-26 UTC; owner decisions of that day). 🔴 Measurement targets only — **each ticket stays in `review/` and owns its own AC** (this list holds no obligation, per the rule at the top of § Order). Deployed demo AMI = the 35th bake: `54b79233c` → `ami-068ae07b2b3ac7797` (§ 35th window — results); ancestry read with `git merge-base --is-ancestor <impl merge> 54b79233c` on 2026-10-26 UTC.
    - **Needs a bake** (impl merged **after** `54b79233c`, so NOT in the deployed AMI — they ride the next bake with a re-list of `git log 54b79233c..origin/main -- ':(glob)projects/*/apps/**' ':(glob)libs/**' ':(glob)infra/demo/**'`):
      - `TASK-BE-661` AC-4 (impl #4568 `d5e2928b1`) — on a fresh volume drop `SKU-APPLE-001` below 10 → the scm suggestion carries `"warehouseCode":"WH01"` (the 35th window's side finding: the suggestion's `warehouseId` held the LOCATION id). Same data step as the 35th preparation steps 1 → 3 (§ 35th window — preparation).
      - `TASK-FAN-BE-053` AC-8 (impl #4563 `b3a113aa6`) — reaction `PUT` → `GET /api/v1/community/feed` `reactionCount` equals the post detail's **while the Redis key `feed:v3:…` TTL > 0** (the 35th measured 1 vs 0 with ttl 297), then `DELETE` → 204 and the feed count follows.
    - **Needs a running backend, no bake** (impl already in the deployed AMI or console/web-only):
      - `TASK-BE-624` AC-4 (impl #4184 `3eb21c693`, **already in** `54b79233c`) — a fresh **IAM-email signup** (new account) → `GET /api/users/me` returns the email filled (not blank).
      - `TASK-PC-FE-373` AC-8 (impl #4565 `a733cac85`, console on Vercel) — logged-in `/wms` overview with ≥ 1 alert row renders (no whole-overview degrade) and the acknowledge button acts on the row `id`; needs a wms alert row (step 3 of the 35th preparation creates one).
      - `TASK-FAN-FE-033` AC-6 (impl #4562 `eabc96810`, web on Vercel) — click a pressed reaction again → it is removed (DELETE) and the press clears; click a different type → it switches.
      - **One shared ≥ 35-min idle run:** `TASK-FE-106` AC-2 · AC-3 (AC-0 is ⚪ — owner decision 2026-10-26 UTC, see that ticket's CORRECTION) + `TASK-FAN-FE-027` AC-1 — idle the store and fan sessions together, return, and record both; run alongside other work.
    - 🟢 36th window (2026-10-28 UTC): measured — see § 36th window — results.
21. 🆕 **`TASK-MONO-820` — demo-seed backfill of the past 28 KST days (SCM · ERP · IAM login · WMS read model)** (added 2026-10-28 UTC). 🔴 **Needs a bake** — the seed change (impl branch `task/mono-820-seed-backfill`) reaches the demo only through the next AMI. Measurement targets only — the ticket stays in `review/` and owns them:
    - The SQL really runs on the live DBs: seed log shows «생성» on the first boot and «존재 … +0» on a same-day second boot for all four domains (Postgres data-modifying CTE + `ON CONFLICT` on admin_db · MySQL `INSERT … SELECT … WHERE NOT EXISTS` · the `dbexec` users `security_user` / `account_user` / `admin` / `erp`). The four seeds still finish inside the 240 s `POST_UP_BUDGET` — erp runs last and is the one that can be cut.
    - 820 AC-2 / AC-6: logged-in `demo-corp` SCM · ERP · IAM · WMS overview «실적» bars ≥ 10 days and the operator-overview «지난 주» bars > 0 for 발주 금액 · 승인 · 로그인 실패 · 출고 수량. 🔴 `period=month` is the **calendar** month — «≥ 10 days» only holds on or after the 11th; measure then, or read the last-28-day series directly.
    - 820 AC-3: WMS `/dashboard/throughput` daily counters equal `/dashboard/metrics` on the same days.
    - ERP inbox equality (§9) includes the one pending backfilled request.
    - 🟢 36th window (2026-10-28 UTC): measured — see § 36th window — results.
22. 🆕 **`TASK-PC-FE-364` AC-3 — the per-permission «권한 없음» table, with a narrow operator made in the window** (added 2026-10-28 UTC; owner «진행»). **No bake, no seed change** — every API used is already deployed. The ticket stays in `review/` and owns the AC; this item is the procedure only.
    - **Why not a seed account.** A permanent single-set operator would sit in the public demo's operator list, and making one needs a seed change + bake. Re-scoping an existing seeded operator is not reversible either: every seeded demo operator (`demo-operator` · `demo-requester` · `demo-cs` · `demo-viewer` · `demo-assigned-only`) holds exactly one row, `legacy-compat` with `source_key='MIGRATION'` (`projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_zz_demo_domain_permission_sets.sql:37-53`) — none is narrow — and revoking then re-granting writes `DIRECT` (`admin-api.md` § 출처 (`source_key`) 와 회수 규칙) — the undo would not restore the volume.
    - **Make the operator.** As `demo@` (demo-corp, `domain_access.manage`): `POST /api/admin/operator-invitations` (`admin-api.md` § POST /api/admin/operator-invitations) for a throwaway address (e.g. `narrow-<date>@example.com`). The mail lands in `iam-mailpit` (receive-only); read the accept link with `docker exec iam-mailpit wget -qO- http://localhost:8025/api/v1/messages`, accept, set a password. 🔴 Confirm first that the new operator holds **zero** domain sets: `GET /api/admin/operators/{id}/domain-access`.
    - **One set at a time.** For each set S: `POST /api/admin/operators/{id}/domain-permission-sets` (S's preset `setId`, catalogue § 시스템 프리셋) → mint the narrow operator's exchanged token for `demo-corp` (`operator_token demo-corp <email> <password>`; a fresh exchange, because assume roles are computed at exchange time — ADR-083 P3) → call the six card sources of `console-integration-contract.md` § 2.4.9.1 → record 200 / 403 per card → `DELETE …/domain-permission-sets/{setId}`. Minimum sets (each «이전 → 이후» flip in the ticket's `## 권한별 «권한 없음» 변화` table): `scm-procurement-operate` · `scm-inventory-visibility-operate` · `finance-ledger-operate` · `finance-account-operate` · `erp-approval-operate` · `erp-masterdata-operate` · `ecommerce-order-operate` · `ecommerce-product-operate`; control = one `*-read` preset (all its domain card 200).
    - **Expected** = the «이후» column of that table: the operator's own domain card is ok for the `*-procurement` / `*-ledger` / `*-approval` / `*-order` sets and **권한 없음 (403)** for `*-inventory-visibility` / `*-account` / `*-masterdata` / `*-product`; every other domain's card is 권한 없음. `erp-approval-operate` shows «직원 연결 없음» (the new operator has no employee link) — that is the expected ok-shape, not a failure. Owner screen once (any one set) so the «권한 없음» wording is seen, not just the 403.
    - The ⚪ row (platform SUPER_ADMIN `'*'` token on the ERP card) needs `owner-operator.sh activate` (§ 35th window — preparation, step 5); measure it only if the owner opts in, then `deactivate`.
    - **Undo:** cancel nothing (the invitation is accepted); `PATCH …/operators/{id}/status` → `SUSPENDED` (or «이 회사에서 내보내기»). Record the leftover operator on the volume.

### Not measured, and owned by no open AC (recorded so the gap is visible — not carried)

These were sub-checks of the removed «Next window — 792…795» blocks, not ACs. Each of those tickets' deploy AC asks for the deploy state to be recorded, and the 28th recorded it.
- 792: `/scm/replenishment` approve as the legacy-compat operator.
- 793: department create and approval submit → approve as the legacy-compat operator · the bell's `degradedDomains ∋ erp` for an operator with no ERP set · the IAM `org_scope` department-picker fallback (Q5) · separation of duties (self-approve).
- 794: discrepancy resolve · FX refresh · period close as the legacy-compat operator · the overview Finance card `forbidden` for an operator with no Finance set.
- 795: `demo-cs` assume still carries the E-commerce roles · the nine console E-commerce screens · consumer non-regression (web-store login + order + payment + shipping + notifications).
- `TASK-BE-638`: the seller-provisioning control (`POST /internal/tenants/ecommerce/accounts` 201) was not exercised separately; `seed-ecommerce` ran green on the fresh volume.
- `TASK-BE-633` ①: not in the 28th results (0 rows to restore by construction on a fresh volume).

## When the window ends
Move each ticket that closed from review → done (four dimensions), and clear the closed entries from the window memory (`project_demo_window_27_pending_checks`).
🔵 Once `TASK-MONO-784` **and** `TASK-BE-632` are in `done/`, `TASK-MONO-773` may start (owner decision 2026-10-15 UTC — gate recorded in 773's Dependency Markers). Not before.

## 27th window — harvest (2026-10-14 UTC, 16:57Z–18:37Z · budget 1258 → 1353 / 1800)

Bake `73daae263` → `ami-0775fa009a12a824d` (pin #4364) · apply by the agent passed the classifier this time · instance `i-029065c2b2d7b8121`, stopped at 18:37Z on both sides (EC2 + `/status`). Results live in each owning ticket.

| Step | Result | Where |
|---|---|---|
| 0 Boot | ✅ commit `73daae263` · 89 containers · unhealthy 0 · seed failures 0 | — |
| 1 Backfill gate | ✅ ① 0 · ② 0, with non-empty populations. 🔴 First try was vacuous (`version='52'` vs stored `0052`) | `TASK-BE-632` § 27th window |
| 2 BE-632 | AC-4 ✅ · AC-1 ✅ · AC-8 ✅ · AC-5 🟡 (workload client ⚪) · AC-6 🟡 — existing token lives to **`exp` + ~60 s** (≈ 360 s, contract says 300 s), other revocation kinds ⚪ (owner decision) | `TASK-BE-632` (stays in `review/`) |
| 784 ⚪② | ✅ ④ = 2 · OD-3 · export → `done/` | `TASK-MONO-784` |
| Found | WMS inventory list 500 (`Instant` → `Timestamp` cast, 4 sites) | new `TASK-BE-637` |

**Not done in this window → next window** (owner decision 2026-10-15 — «784 내보내기까지만»):
- step 3: PC-FE-338 c-2 — now the AC-1 / AC-2 live checks (step 3 rewritten 2026-10-15 UTC).
- step 4: PC-FE-332 AC-3 · FE-109 AC-1 · FE-108 AC-3.
- step 5: BE-634 · BE-635 · BE-633 ② · BE-636 ①②③. 🔵 `TASK-MONO-796` (artist goods photos) also rode this bake — see its screen in the next window.

Demo data the window changed (restored by the next re-bake's fresh volume): `requester@demo.com` and `demo-assigned-only` in `demo-corp` now hold the five subscribed `*-full` presets, not `legacy-compat` (which the API cannot grant). Their roles are byte-identical.

Owner decisions (2026-10-15 UTC — all three took the recommendation):
1. **BE-632 closed with the recorded ⚪** → `done/`. Its two ⚪ are now next-window items **owned by this list**:
   - ⚪ **AC-5 workload client**: an ERP masterdata write with the ERP workload client's client-credentials token must be 200. It needs that client's secret on the host. A probe with a valid body creates a row, so use a name like `BE632-PROBE`.
   - ⚪ **AC-6 four kinds** (group leave · operator suspend · subscription cancel · set edit): for each, record when the existing token stops working (expected ≤ `exp` + 60 s) and check that re-exchange is immediate. Subscription cancel and suspend touch the whole demo, so use a throwaway tenant or operator, not `demo-corp` / `demo@demo.com`. These are permission writes, so prepare an owner-run wrapper (the classifier blocks them over SSM).
2. **AC-6 skew → contract sentence corrected** (`auth-api.md` · `admin-api.md` · `rbac.md`: «`exp` + 60 s, at most about 360 s»). Not reduced. The source is Spring's default `JwtTimestampValidator` skew in `libs/java-security-servlet` and `libs/java-gateway`.
3. **AMI prune done**: 23rd · 24th · 25th deregistered and their snapshots deleted. Remaining: 26th (rollback) · 27th (deployed).

⇒ `TASK-MONO-773` start gate (784 · BE-632 `done/`) is **met**.

## 28th window — harvest (2026-10-16 UTC, 16:13Z–18:18Z · budget 1353 → 1473 / 1800)

Bake `bake.sh --ref bake-8905d2c29` → `ami-00ef8cc1057cca59b` (`portfolio-demo-1792163503`, 53 m 33 s, tag `RepoCommit` `8905d2c29`) · pin #4394 (`1c8ee22cb`) · `terraform plan` (EC2 replace, Lambda / IAM in place, nothing else destroyed) → apply by the agent passed the classifier · instance `i-023d5b6c2129a4919` · Lambda `AMI_REPO_COMMIT=8905d2c29` · 9 bundles requested at once → ready ≈ 16:24Z · stopped at 18:18Z on both sides (EC2 + `/status`). Raw outputs stayed in the session scratchpad: `tenant-flow.out` PASS 50 / FAIL 0 / ⚪ 2 · `perm-matrix.out` PASS 60 / FAIL 1 (→ re-measured PASS, row BE-632 AC-5) · `ac6.out` PASS 15 / 3 «FAIL» (the predicate was too strict, row BE-632 AC-6) / ⚪ 4.

| Step | Result | Where |
|---|---|---|
| 0 Boot | ✅ commit `8905d2c2` · 97 containers · unhealthy 0 · restarting 0 · starting 0 · `tenant.activated` topic on iam-kafka = 1 · Flyway admin V0052..V0059 all success · `seed-wms` «생성 89 · 기존 0 · 실패 0» (journal) · the other seeds green by later checks (ecommerce products 42 in `ecommerce`, finance / erp data present) | — |
| 1 Backfill gate | ✅ ② = 0 with non-empty populations (assignments 6 · set grants 9 · sets 33 · set-role rows 172 · partnerships 0). ① = 0 but vacuous by construction (fresh volume: no assignment before the cutover, 16:17:24 = boot) — same as the 27th | here |
| V0056–V0059 counts | Fresh volume, so «before» = the 3 presets that held the entry role (`<domain>-full` · `all-domains-full` · `legacy-compat`). After: **792** `SCM_OPERATOR` 0 · `SCM_PROCUREMENT_WRITE` 4 (`all-domains-full` · `legacy-compat` · `scm-full` · `scm-procurement-operate`) · partnerships 0. **793** `ERP_OPERATOR` 0 · `ERP_APPROVAL_WRITE` 4 · `ERP_MASTERDATA_WRITE` 4 · partnerships 0. **794** `FINANCE_OPERATOR` 0 · `FINANCE_LEDGER_CONTROL` 3 · `FINANCE_LEDGER_WRITE` 4 · partnerships 0. **795** `ECOMMERCE_OPERATOR` 0 · `ECOMMERCE_SETTLEMENT_WRITE` 4 · `ECOMMERCE_MEMBER_READ` 4 · partnerships 0. No set holds any legacy entry role. 🔴 792's predicate said «before + 2» — **wrong, it is + 1** (of the five new presets only `scm-procurement-operate` has `SCM_PROCUREMENT_WRITE`; code correct). The `data-model.md` § V0056 comment is corrected in this harvest's PR | 792 · 793 · 794 · 795 |
| 792–795 AC-2 (legacy-compat, same 200) | ✅ `demo@demo.com` assumes `demo-corp`: `roles` = all WMS + `SCM_OPERATOR` + 8 SCM + `ERP_OPERATOR` + 4 + `FINANCE_OPERATOR` + 5 + `ECOMMERCE_OPERATOR` + 13 (entry roles derived) · entitled `[ecommerce, erp, finance, scm, wms]` · `scope` `[openid, profile, tenant.read, email]` (no `erp.write` / `finance.write`) · TTL 300. GET 200: wms inventory · scm PO + suggestions · erp departments · finance trial balance; assume `ecommerce` → `/api/admin/orders` · `products` · `users` · `sellers` 200 | 792 · 793 · 794 · 795 |
| 792–795 AC-1 controls | ✅ `requester@demo.com` in `demo-corp`, `legacy-compat` revoked, one preset at a time, then restored byte-identical (rows · `granted_at` · roles). **SCM:** `scm-read` → PO / suggestions / iv-nodes GET 200 · dispatch-by-id 404 (gate passed) · PO create 403 · policy PUT 403; outbound-only → `scm dispatch :retry` 403 (Q3). **ERP:** `erp-approval-operate` → departments 403 · `employees/me` 200 · approvals list 200 · Q3 submit with a department subject → 422 `approver_unresolved` (not `subject_unresolved`); `erp-read` → GETs 200 · department create 403 · approval create 403; no ERP set → departments 403. **Finance:** `finance-account-operate` → account GET 404 (gate passed) · trial balance 403; `finance-read` → trial balance / periods 200 · transfer / journal 403; `finance-ledger-operate` → journal 422 (gate passed) · period close 403 · FX override 403; tenant set {`FINANCE_LEDGER_CONTROL`} → period close 404 (gate passed) · journal 403 · trial balance 200; no Finance set → trial balance 403. **E-commerce:** `ecommerce-order-operate` → orders 200 · products POST 403 · users 403 · order status (nonexistent) 404 (gate passed); `ecommerce-read` → orders / products / users / sellers 200 · writes 403; no E-commerce set → orders via the gateway 403. **Direct bypass:** `X-User-Role: ECOMMERCE_OPERATOR` alone → order-service 403 · product-service 403; with `ORDER_READ` / `PRODUCT_READ` → 200 (before 795 both returned 200; also measured separately at 16:27Z, same) | 792 · 793 · 794 · 795 |
| `TASK-BE-632` AC-5 workload ⚪ (owned here since the 27th) | ✅ **closed.** The first cell's 403 `AUDIENCE_FORBIDDEN` was the measurement path (the ERP edge gateway admits only the `platform-console-web` audience by design, MONO-696/697). Re-measured directly on `erp-platform-masterdata:8080` with the `erp-platform-internal-services-client` client-credentials token (scope `erp.read erp.write`, tenant `erp`): GET 200 · POST department `BE632-PROBE` **201** | here (BE-632 frozen) |
| `TASK-BE-632` AC-6 ⚪ four kinds | 🟡 **three closed, one open.** `requester@demo.com`, restored byte-identical afterwards (set rows · memberships · admin-role rows · status `ACTIVE` · roles). K1 group leave · K2 set edit · K3 operator suspend: an immediate re-exchange carries no ERP role → ERP GET 403 (suspend: exchange refused); reactivation brings ERP back with nothing re-granted. The existing token turned 401 at `exp` + 63 / + 62 / + 62 s (10 s poll; the previous poll ≈ `exp` + 53 s was still 200), 362 s after the revocation — consistent with the corrected contract «`exp` + 60 s, at most about 360 s». The script's «≤ `exp` + 60» predicate was too strict for a 10 s poll (not a defect). K4 subscription cancel ⚪ | here — K4 → § Order open 2 |
| `TASK-BE-633` ② | ✅ G1 + G2 both give `SUPPORT_READONLY` → 2 rows (`GROUP:2`, `GROUP:3`) → leave G1 → 1 (`GROUP:3`) → leave G2 → 0 | here (frozen) |
| `TASK-BE-635` | ✅ 101-char `Idempotency-Key` → 400 `VALIDATION_ERROR`; control 100-char → 404 `ACCOUNT_NOT_FOUND` | here (frozen) |
| `TASK-BE-636` ① ② · `TASK-BE-634` | ⚪ no company U in demo@'s `operator.manage` scope besides `demo-corp`; demo@ lacks `partnership.manage` in `demo-corp` (403 on the list) | § Order open 6 · 7 |
| `TASK-MONO-773` AC-4 | ✅ (API level) via the break-glass dev `admin` (`'*'`): POST `w28-pending` with `firstAdmin` → 201 `PENDING`, first admin `INVITED`, delivery `SENT`, no token / link in the response · `?status=PENDING` list ok · account_db `PENDING` · PATCH `ACTIVE` → 409 `TENANT_FIRST_ADMIN_PENDING`. Accept page without a session → `login_required`; accept before email verification → 403 `email_not_verified` (tenant still `PENDING`); after verification → 200 accepted → tenant `ACTIVE`, admin `ACCEPTED`, exactly 1 `ORIGIN` row `all-domains-full` `granted_by` NULL, roles `TENANT_ADMIN` + `TENANT_BILLING_ADMIN` + assignment, outbox `tenant.created` / `tenant.activated` published, admin_actions `TENANT_CREATE` / `INVITATION_CREATE` / `INVITATION_ACCEPT` / `TENANT_ACTIVATE` SUCCESS, `tenant.activated` on Kafka 1, re-invite on `ACTIVE` → 409 `TENANT_NOT_PENDING`. Row actions (`w28-cleanup`): re-invite while pending → 409 `OPERATOR_INVITATION_ALREADY_PENDING` · no `Idempotency-Key` → 400 · cancel → 200, stays `PENDING`, state `NONE` · re-invite to B → 201 `INVITED` · PATCH `ACTIVE` → 409 · 정리 PATCH `SUSPENDED` → 200, 0 pending invitations, `tenant.suspended` `previousStatus=PENDING` · reactivate → 409 `TENANT_FIRST_ADMIN_PENDING` · re-invite on `SUSPENDED` → 409 `TENANT_NOT_PENDING`. ⚪ the console `/tenants` screen itself | here (773 frozen) — screen → § Order open 4 |
| `TASK-MONO-773` AC-1 | ✅ owner browser, pool account `w28-shell@example.com`: shell, sidebar «가이드 · 테넌트 생성» only, no `console_operator_token`, `/operators` and `/onboarding` → `/new-tenant` | here |
| `TASK-MONO-773` AC-2 | ✅ owner browser + post: `w28-shell` creates `w28-shellco` → dashboards · `ORIGIN` 1 row `all-domains-full`. First-admin path too: assume before any subscription → 200, roles none → subscribe `wms` → roles = the `wms` catalog (8); `w28-shellco` the same after its `wms` subscription | here |
| `TASK-MONO-773` AC-3 | ⚪ not run (by choice — it would add a membership / owned tenant to `demo@demo.com`) | § Order open 5 |
| `TASK-MONO-773` AC-7 = `TASK-PC-FE-331` AC-3 | ✅ owner browser: logout → the IdP form is shown (no silent SSO) → `demo@demo.com` operator login | `TASK-PC-FE-331` → `done/` |
| `TASK-BE-637` AC-3 | ✅ demo operator `GET /api/v1/inventory?size=1` → 200 with non-null `lastMovementAt` · `createdAt` · `updatedAt` (27th: 500) · transfers 200 · console «WMS 재고» renders rows (owner, during the 338 check) | `TASK-BE-637` → `done/` |
| `TASK-BE-638` | ✅ `product-service-client` workload token (client credentials, tenant `global-account-platform`) and the same assumed into `ecommerce`: PATCH `/internal/tenants/ecommerce` {`ACTIVE`} 404 · GET `/internal/tenants/ecommerce` 404 · GET `/internal/tenants` 404 · POST `/internal/tenants` 404 · dot-segment `/internal/tenants/ecommerce/../x` 404 · `ecommerce` still `ACTIVE` · control GET `/internal/tenants/ecommerce/entitled-domains` (assumed) 200 `{"tenantId":"ecommerce","domainKeys":["ecommerce","wms"],"tenantStatus":"ACTIVE"}`. Seller-provisioning control not exercised separately (⚪ — `seed-ecommerce` green on the fresh volume) | `TASK-BE-638` → `done/` |
| `TASK-BE-639` | ✅ checks 2–4: company operator × `SUSPENDED` → 400 `invalid_grant` `tenant_not_active`, no token, membership / grant snapshot unchanged (2 samples: `w28-pending`'s first admin, `w28-shellco`'s creator) · reactivate → 200, roles byte-identical, nothing re-granted · account-service direct `entitled-domains` for `SUSPENDED` → `{"domainKeys":[],"tenantStatus":"SUSPENDED"}`, `demo-corp` `ACTIVE` with keys · the gateway response carries `tenantStatus`. Check 1 (`'*'` × `PENDING` / `SUSPENDED`) ⚪ — no unconfined `'*'` IdP login; confined `platform@demo.com` is refused at the assignment gate, not by the status read | `TASK-BE-639` → `done/` — check 1 → § Order open 1 |
| `TASK-PC-FE-338` c-2 AC-1 · AC-2 | ✅ owner browser (`requester@demo.com`, `run-338.sh` inv / out / ro / restore) + server in the same session. AC-1: `wms-inventory-operate` only → sidebar WMS ▸ «출고» «업무 권한 없음», opening it → «이 화면을 조회할 권한이 없습니다» (server 403); «재고» no badge, opens (server: inventory GET 200 · `/admin/dashboard/inventory` 200 · outbound orders 403); + `wms-outbound-operate` (re-login) → badge gone, outbound opens (server 200; `:cancel` on a nonexistent id passes the gate → 400). AC-2: tenant set {`OUTBOUND_READ`} → outbound list opens, the order drill shows no pick / pack / ship / cancel and the «조회 전용 — …» note; server outbound GET 200, `:cancel` 403. Observation: «발송 재시도» (SCM logistics) still shows — server 403 since 792 Q3; hiding it is `TASK-PC-FE-342`'s scope. The console keeps the assumed token up to ~4 min, so a set change shows only after re-login / re-assume. Restore: `legacy-compat` `MIGRATION` row only (`granted_at` 16:17:27.189715), roles full | `TASK-PC-FE-338` → `done/` |
| `TASK-PC-FE-332` AC-3 | ✅ `cs@demo.com` locks `demo@demo.com` → «demo@demo.com 은(는) 팬·스토어가 함께 쓰는 IAM 공용 계정이라 계정 전체가 아니라 ecommerce 사이트에서만 잠겼습니다. …» · unlocked (account-service log: lock 18:09:20Z `ACTIVE->LOCKED`, unlock 18:10:00Z `LOCKED->ACTIVE`) | `TASK-PC-FE-332` → `done/` |
| `TASK-FE-108` AC-3 | ✅ a new store signup → the first login succeeds without a retry | `TASK-FE-108` → `done/` |
| `TASK-FE-109` AC-1 | ⚪ not measured (needs the Vercel request log) | `TASK-FE-109` (stays in `review/`) |
| `TASK-MONO-796` | ✅ artist goods: 18 photos fit their product types, all distinct, no broken image | here (frozen) |

**Tickets this harvest moves to `done/`:** `TASK-MONO-792` · `793` · `794` · `795` (root) · `TASK-PC-FE-338` · `TASK-PC-FE-331` · `TASK-PC-FE-332` (platform-console) · `TASK-BE-637` (wms) · `TASK-BE-638` · `TASK-BE-639` (iam) · `TASK-FE-108` (ecommerce). Stays: `TASK-FE-109` (AC-1 ⚪).
**Merge gate met:** `TASK-PC-FE-342` · `343` · `344` · `345` — the bake carrying 792–795 was live-verified in this window (one dated line appended to each).

**Not done in this window → next window:** § Order «Still open» 1–8. 🔴 Items 1–4 share one cause (no unconfined `'*'` `SUPER_ADMIN` with an IdP login) — the owner question recorded there was decided 2026-10-17 (`TASK-MONO-798`, rides the next bake).

Demo data the window changed (restored by the next re-bake's fresh volume):
- Throwaway tenants left `SUSPENDED`: `w28-pending` · `w28-cleanup` · `w28-shellco` (the 13 seeded tenants are `ACTIVE`). The pool account `w28-shell@example.com` created `w28-shellco`.
- ERP: department `BE632-PROBE` (tenant `erp`) and a `DRAFT` approval `MONO793-PROBE`.
- Restored in the window (no residue): `requester@demo.com`'s sets (`legacy-compat` `MIGRATION` only) and roles · `demo@demo.com`'s site lock (unlocked 18:10:00Z).

## 29th window — harvest (2026-10-20 UTC, 06:08Z–idle stop (time not recorded, before 08:47Z) and 09:23Z–10:02Z · budget 1473 → 1654 / 1800)

Bake `bake.sh --ref bake-e44142d34` → `ami-01681ef55def7d15c` (`portfolio-demo-1792472487`, tag `RepoCommit` `e44142d34` — carries `TASK-MONO-798` · `TASK-ERP-BE-045` · `TASK-BE-640`) · pin #4425 (`b506c8f3f`) merged and pulled **before** the plan · `terraform plan` = EC2 replace + `aws_iam_role_policy.ec2_health` adds only `ssm:PutParameter` on `parameter/portfolio-demo/owner-operator/password` + Lambda policy / env re-rendered (instance id) — nothing destroyed but the instance · apply by the agent passed the classifier · instance `i-0949f210c543c84cd` · Lambda `AMI_REPO_COMMIT=e44142d34` · boot selection = all 9 bundles → ready ≈ 06:21Z · `demo-stack` active 06:24:42Z.

| Step | Result | Where |
|---|---|---|
| 798 boot provision | ✅ last post-up step: `[owner-operator] provisioned: new password in SSM … (SecureString) · credential updated · operator SUSPENDED`. `status` (agent, SSM — not blocked): SUSPENDED · credential present 06:24:06Z · 0 refresh tokens · «provisioned this boot: yes». SSM parameter `SecureString` v1 06:24:06Z — the instance-role put **and its kms path** are now measured, not inferred | `TASK-MONO-798` |
| 798 IdP login | ✅ `star-owner@demo.com` with the SSM password (read into a 0600 file, used via `curl --data-urlencode password@file`, never printed) → authorization code → base token `sub` `…ad09` · `tenant_id=iam` · admin-service exchange → operator token `sub=demo-star-owner` `token_type=admin` | `TASK-MONO-798` |
| 798 reboot edge | ✅ the window idle-stopped while the operator was `ACTIVE`; the next boot (09:23Z) came up **SUSPENDED** with a new credential (09:25:14Z) — the «stopped mid-window → next boot SUSPENDED» edge case, live | `TASK-MONO-798` |
| Item 1 (`TASK-BE-639` check 1) | ✅ see § Order «Still open» 1 | — |
| Item 3 (`TASK-MONO-795` wildcard) | ✅ API level, with a premise correction — see § Order «Still open» 3 | — |
| Items 2 · 4 | ⚪ the owner's second `activate` did not come before the window was closed | § Order «Still open» 2 · 4 |
| `TASK-ERP-BE-045` AC-3 · `TASK-BE-640` live | ⚪ not attempted (the window was spent on items 1–4) | those tickets |

🔴 **Measurement-hygiene incident (agent):** the first run's failure branch printed the first 200 characters of the admin-service exchange response — the header and part of the payload of one operator JWT, **no signature** — into the session scratchpad output and the conversation. The file was deleted; every later branch redacts `eyJ…` and the re-run output had 0 `eyJ` matches. The token was an admin access token with a 1 h TTL and its signature never left the process.

Demo data the window changed (restored by the next re-bake's fresh volume): throwaway tenants `m798-0756-pend` (`PENDING`, first-admin invitation to `m798-0756-pend@example.com`) and `m798-0756-susp` (`SUSPENDED`). The `'*'` operator was left `SUSPENDED` (forced by the reboot); no subscription was changed.

## 30th window — harvest (2026-10-20 UTC, 10:36Z–11:17Z · budget 1654 → 1692 / 1800)

Same AMI and instance as the 29th (`ami-01681ef55def7d15c` · `i-0949f210c543c84cd`, a stop/start, so the 29th's volume and its `m798-0756-*` tenants were still there). `/bundle/start` `console` · `console-finance` · `console-erp` → all three `ready` 10:39:00Z (≈3 min) · `demo-stack` active 10:39:17Z · provision again: SUSPENDED + new credential 10:38:38Z. The owner instructed the agent to run `activate` (11:04:29Z → ACTIVE) and later `deactivate` was run by the agent to close (11:16:18Z → SUSPENDED, refresh tokens revoked, 0 unrevoked). `/stop` 11:16:32Z → EC2 `stopped` 11:17:14Z and `/status` `stopped`.

| Step | Result | Where |
|---|---|---|
| Item 2 (`TASK-BE-632` AC-6 kind 4) | ✅ see § Order «Still open» 2 | — |
| Item 4 (`TASK-MONO-773` AC-4 screen) | ✅ + one console-copy finding — see § Order «Still open» 4 | — |
| Item 3 card | 🔵 the card path does not exist for a `'*'` operator (no `*` in the switcher) — see § Order «Still open» 3 | — |
| `TASK-ERP-BE-045` live non-regression | ✅ `demo@demo.com` assume `demo-corp` (legacy-compat): `GET /api/erp/masterdata/employees/picker?page=0&size=3` → 200, `meta.scope = "TENANT"`, 3 of 4, element keys exactly `accountLinked,departmentId,departmentName,employeeNumber,id,name,status`; `?active=true` → 200 same keys; the list `GET /employees` → 200, no `meta.scope`, its keys unchanged (`accountId` · `audit` · `costCenterId` · `effectivePeriod` · `jobGradeId` …). ⚪ AC-3's «결재 운영 only» screen check needs a set change on a throwaway subject — not run | `TASK-ERP-BE-045` |
| `TASK-MONO-798` `deactivate` | ✅ live (the one ⚪ left in its done record) | `TASK-MONO-798` (frozen — CORRECTION appended) |

Demo data the window changed (restored by the next re-bake's fresh volume): `globex-corp`'s `finance` subscription is now `CANCELLED` (it had none before). The `'*'` operator is `SUSPENDED`.

## 31st window — harvest (2026-10-20 UTC, 11:51Z–12:28Z · budget 1692 → 1724 / 1800)

Same AMI / instance (stop-start). `/bundle/start` `console` · `console-erp` → ready 11:54:30Z · `demo-stack` active 11:54:45Z · the agent ran `activate` (owner-approved window scope) → ACTIVE, and `deactivate` at the end → SUSPENDED (refresh tokens revoked). `/stop` → EC2 `stopped` 12:28:14Z and `/status` `stopped`. Scripts were prepared before the window (offline) and run in this order: BE-640 (host, SSM) → BE-636 → BE-045 apply → owner browser → BE-045 restore → BE-634.

| Step | Result | Where |
|---|---|---|
| Item 9 (`TASK-BE-640` live) | ✅ PASS 8 · FAIL 0, census ① partial (erp only) — § Order «Still open» 9 | `TASK-BE-640` → `done/` |
| Item 6 (`TASK-BE-636` ① ②) | ✅ PASS 13 · FAIL 0 + host audit detail 3/3 — § Order «Still open» 6 | here (BE-636 frozen) |
| Item 11 ④ / `TASK-ERP-BE-045` AC-3 | 🟡 API PASS 8 · FAIL 0 (approval-only operator: picker 200 `TENANT`, `/employees` 403) + owner browser: all four pickers are lists (AC-3 closed); 11 ④ 승인/반려 · `/erp/masters` 등록 ⚪ | `TASK-ERP-BE-045` |
| Item 7 (`TASK-BE-634`) | ✅ PASS 9 · FAIL 0 — § Order «Still open» 7 | here (BE-634 frozen) |

🔴 **Finding — a header the firewall rejects becomes 500, not 400.** The first BE-045 apply sent `X-Operator-Reason` containing a non-ASCII character (`④`). admin-service's Spring Security `StrictHttpFirewall` threw `RequestRejectedException` («The request was rejected because the header "X-Operator-Reason " has a value …») and `CommonGlobalExceptionHandler` answered **500 `INTERNAL_ERROR`** for both the revoke and the grant; nothing was written. The console is not affected (it percent-encodes this header — `TASK-PC-FE-308`), but any other client gets a 500 that names no cause. → owned by **`TASK-MONO-800`** (drafted 2026-10-20 UTC — root, because the handler is the shared `libs/java-web-servlet` `CommonGlobalExceptionHandler`, not iam code).

Demo data the window changed (restored by the next re-bake's fresh volume): partnership `initech-corp → umbrella-corp` `TERMINATED`; `requester@demo.com`'s `demo-corp` sets = the five `<domain>-full` presets DIRECT (was `legacy-compat` MIGRATION; same assumed roles); audit rows. `demo-assigned-only` unchanged. The `'*'` operator is `SUSPENDED`.

## 32nd window — harvest (2026-10-20 UTC, 17:12Z–18:47Z · budget 1724 → 1815 · cap raised 1800 → **2400** mid-window)

**Bake:** none (owner decision «굽지 않고 창 하나로 콘솔 라이브 확인부터»). AMI `ami-01681ef55def7d15c` (`e44142d34`). Bundles: `console` + `console-erp` from 17:12Z; at 18:06Z `console-ecommerce` · `console-scm` · `console-finance` · `console-wms` added (all ready 18:20Z). IP `16.184.32.19`. The `'*'` operator: `activate` 17:16Z → `deactivate` 18:47Z over SSM by the agent (owner-approved); refresh tokens 0 after. Owner browser; agent API/host checks. Subject `requester@demo.com` swapped one set combination at a time (`swap.sh`) and restored: five `<domain>-full` DIRECT, assumed roles identical to the snapshot (42).

| Item | Result |
|---|---|
| 13 · 354 | ✅ → done. `/operators` toggle + platform create form for the `'*'` operator; «모든 테넌트» = 9 rows (= server `?tenantId=*`); preset grant/revoke on `platform@demo.com` ×2 → `admin_actions.tenant_id='*'` SUCCESS ×4, net zero |
| 13 · 353 | 🟡 screens open with `demo-corp` selected; the new no-tenant notice is **not reachable** — login auto-selects a tenant. Stays `review` |
| 14 · 308 | ✅ → done. Korean-reason **reject → 반려됨** |
| 14 · 311 | ✅ → done. Approval line «EMP-0001 · 김본부 (나)» — drawn by `TASK-PC-FE-318`'s account link, which replaced 311's own fallback (⚪ 311's sentence never seen live) |
| 14 · 350 · 310 | ✅ 350 → done (AC-3 deploy state). 310: logged-in overview + another tenant's button lands ✅; anonymous half ⚪ — stays `review` |
| 10 (342–345) | demo@ no badge on SCM · ERP · Finance · E-commerce ✅ (345's live regression fixed) · one area at a time → the others badged ✅ · SCM replenishment «조회 전용» ✅ · `/ledger` FX «새로고침» shown/hidden/shown across legacy-compat / `finance-read` / `finance-ledger-operate` ✅ · bell: no ERP degradation without ERP roles ✅. ⚪ WMS «발송 재시도» (the only order is `SHIPPED`/`COMPLETED` — non-discriminating) · ⚪ «해소» (no OPEN discrepancy) · ⚪ SCM config save not looked at. WMS menus carry no badge without a WMS set — by design (`permission-map.ts:722-725`) |
| 11 (346–347) | ✅ → done. `ecommerce-read` six screens «조회 전용» · `/products/new` notice instead of the form · `erp-read` three screens «조회 전용» · approval set: «결재 요청 작성» · «위임 생성» shown · masters refused. ⚪ as in the row above |

**Findings (new tickets):**
- `TASK-PC-FE-355` — console **submit** of an approval request answers «요청을 처리하지 못했습니다» (the DRAFT was created; the same DRAFT submitted through the API by the same subject → 200 `SUBMITTED`) · `/erp/delegation` «내가 위임한» shows an error **and** the empty-list text for every set, while `?role=DELEGATOR` answers 200 `[]`.
- `TASK-BE-641` — the IdP's advertised `end_session_endpoint` (`https://auth.hubwang.com/connect/logout`) answers **401 «Missing or invalid internal credentials»** with or without `id_token_hint` — console logout stops on a JSON error page.
- `TASK-MONO-802` — the mid-window budget apply also flipped `BUNDLE_SELECTION_CAPABLE` `yes → unknown` (`terraform.exe` ran the capability script under a bash without git); bundle boot was refused until a re-plan from PowerShell with Git bash first on PATH (only change `unknown → yes`) was applied. 🔴 The agent read the first plan with a grep for one variable and missed the second changed line.

**Observations (no ticket):** a «세션이 만료되어 로그아웃되었습니다» for `requester@` mid-window, right after a set swap (base token lifetime 1800 s; not reproduced, cause not measured). The console banner «데모 서버가 꺼져 있어» on a page opened before `selection_ready` — three later fetches of `/login` had no banner (15 s state cache).

**Budget:** owner decision 2026-10-20 — `monthly_budget_minutes` 2400 (live from 18:02Z; `variables.tf` default 2400 in the harvest PR). The console guide still says «한 달에 최대 1800분» (`global-guide/data.ts:381-382`) — user-visible copy, changed only after owner approval.

**Left behind on the demo volume (cleared by the next bake):** the «308 반려 확인» request (REJECTED); the requester's five `<domain>-full` rows have new `granted_at`; audit rows of this window.

## 33rd window — harvest (2026-10-23 UTC · budget 1815 → 1854 / 2400, 39 min)

**Bake (item 15):** `283fd39cf` → AMI `ami-02b3ba00a3b2ff622` (51 min 36 s, tag RepoCommit matches) · pin PR #4498 (`c5e3ddad3`) · `terraform apply` (owner): 1 add · 2 change · 1 destroy · Lambda `AMI_REPO_COMMIT=283fd39cf640848f274060a428be0de6c253c9fe`, `BUNDLE_SELECTION_CAPABLE=yes` · instance `i-0f39aec49ddbaa899`. Every impl merge listed in item 15 (and 800 · 801 of item 12) is an ancestor of `283fd39cf` (`git merge-base --is-ancestor`, checked when recording). **Window:** nine bundles in one request, all ready; at the end `/stop` + EC2 `stopped`, both confirmed. **Item 16:** the console copy PR #4496 (`0824ed547`) merged after the bake was live — branch nightly `39957126998` success, Vercel `7279505197` success, live `/wms/guide`: old sentence 0, new sentences 1 each.

**Item 17 (and 12, 14) — results.** The evidence is in each ticket's § 라이브 확인 (2026-10-23 UTC · 33차 창), verbatim from the window record.

| Ticket | AC | Result | Where it went |
|---|---|---|---|
| iam `TASK-BE-642` | AC-3 | ✅ unknown `client_id` → 400 `invalid_request` JSON | → `done/` (AC-1 «format» = owner approval 2026-10-21, § CORRECTION) |
| iam `TASK-BE-641` | AC-3 | ✅ console logout → `console.hubwang.com/login`; garbage hint → 400 `invalid_token` | stays `review` — AC-1's owner-browser hint half not measured |
| `TASK-MONO-801` | AC-4 | ✅ `/oauth2/authorize;y` → 400 `BAD_REQUEST` (`/x;y` → 404: the auth-forwarder treats it as `/x`) | → `done/` |
| `TASK-MONO-800` | AC-4 | ⚪ no raw client carrying the Bearer token (the console percent-encodes the header) | stays `review` |
| wms `TASK-BE-643` | AC-3 | ✅ inbound 87 = 87 · outbound 1 = 1 | → `done/` |
| wms `TASK-BE-644` | AC-3 | ⚪ `GET /settings` 500 (pre-existing) · no console write path; partial: «저재고만» 0, 99 rows `lowStockFlag=false` | stays `review` · new `TASK-BE-652` |
| wms `TASK-BE-647` | AC-2 | ⚪ no SKU below 10 (available 99) · alerts 0 · stock not lowered on purpose | stays `review` |
| ecommerce `TASK-BE-645` | AC-2 | ⚪ 0 `BACKORDERED` orders | stays `review` |
| ecommerce `TASK-BE-648` | AC-2 | ✅ `PENDING` confirm → 400 `INVALID_ORDER_REQUEST` | → `done/` |
| ecommerce `TASK-BE-646` | AC-2 | 🟡 cancel → 200 · «환불 완료» · shipping `CANCELLED`; outbox row · product reservation · three-DB query not measured | stays `review` |
| ecommerce `TASK-BE-649` | AC-3 | ✅ shipping `PREPARING → CANCELLED`, then wms outbound `CANCELLED` · ⚪ false-ALERT echo not checked | stays `review` |
| wms `TASK-BE-650` | AC-3 | ❌ auto-cancel at `PICKING` ✅, but the reservation is not released (inventory `picking.cancelled` → DLT; DLT topic missing; exception not logged) | stays `review` · new `TASK-BE-651` |
| scm `TASK-SCM-BE-062` | AC-3 | ⚪ 0 suggestions | stays `review` |
| console `TASK-PC-FE-367` | AC-3 | ✅ «취소됨» badge · filter · overview 1 · raw `CANCELLED` 0 | → `done/` |
| ecommerce `TASK-FE-110` | AC-3 | ✅ «주문이 취소되어 배송이 취소되었습니다.» | → `done/` |
| console `TASK-PC-FE-358` | AC-4 | 🟡 14-day chart ✅ (10/23 inbound 86) · the empty-window text cannot be reached on this data | stays `review` |
| console `TASK-PC-FE-364` | AC-3 | ❌ «WMS 출고» · «SCM 발주» cards `DOWNSTREAM_ERROR` · «WMS 점검 불가» · permission-set table not measured | stays `review` · new `TASK-PC-FE-369` |
| console `TASK-PC-FE-355` | AC-3 | 🟡 «내가 위임한» ✅ · submit not done | stays `review` |
| console `TASK-PC-FE-310` | AC-11 | not attempted | stays `review` |

**Findings (new tickets):**
- `TASK-BE-651` (wms) — inventory-service does not release the reservation on `wms.outbound.picking.cancelled.v1`: saga stuck at `CANCELLATION_REQUESTED`, reservation `RESERVED`, DLT topic missing, listener exception not logged. The existing `CancelOrderUseCase` compensation path (operator cancel likely affected — its AC-0).
- `TASK-BE-652` (wms) — admin-service `GET /settings` without filters → 500 (`AdminSettingJpaRepository.java:13-18`, PostgreSQL `character varying ~~ bytea`); the console WMS 운영설정 screen cannot read settings.
- `TASK-PC-FE-369` (console) — operator overview «WMS 출고» · «SCM 발주» cards `DOWNSTREAM_ERROR` + «WMS 점검 불가»; the services saw no such request, `/wms` on the same `WMS_ADMIN_BASE_URL` works, Vercel logs unavailable.

**Small findings — recorded, not ticketed:**
- `https://auth.hubwang.com/x` → 404, but the body code is `INTERNAL_ERROR` «Unexpected server-side error».
- The console shipping screen subtitle «상태 전이(PREPARING→SHIPPED→IN_TRANSIT→DELIVERED)» does not mention cancellation.
- `demo-corp`'s `/api/wms/outbound?source=FULFILLMENT_ECOMMERCE` returns MANUAL rows (the filter looks ignored).
- Two seed orders have ecommerce shipping `SHIPPED` / `DELIVERED` while their wms outbound is `PICKING`.

**Left behind on the demo volume:** order `49eae2f6-…` cancelled and refunded; its wms saga stuck at `CANCELLATION_REQUESTED` with reservation `2f397b48-…` `RESERVED` (SKU …0013 `reservedQty=1`) — `TASK-BE-651`.

## 34th window — harvest (2026-10-24 UTC · budget 1874 → 1924 / 2400, 50 min)

**Bake (item 18):** `d5bb28f14` → AMI `ami-0d9f8da7d20d7a180` (pin PR #4533, `e191e9d18`) · `terraform apply` (owner) → instance `i-0cebb64967faf3d33` · `check-ami-generation.sh --with-aws` rc=0 («same generation»). Every impl merge closed below is an ancestor of `d5bb28f14` (`git merge-base --is-ancestor`, checked when recording) except `TASK-MONO-815` (`3cea93ad0`, console only — it rides Vercel, not the AMI). **Window:** 16:20Z–17:11Z, nine bundles in one request, all ready; closed with `/stop` at 17:11Z, EC2 `stopped` and the control API `stopped` both confirmed. Owner browser; agent API/host checks. A fresh volume (new instance), so nothing from the 33rd window's data survived.

**Results.** The evidence is in each ticket's AC (a «34차 창(2026-10-24 UTC)» sub-bullet). Every impl PR below was checked with the four dimensions (`state=MERGED`, merge sha an ancestor of `origin/main`, 0 failing checks, the AC text opened) before the ticket moved.

| Ticket | AC | Result | Where it went |
|---|---|---|---|
| `TASK-MONO-807` | AC-6 | ✅ ecommerce metrics 200; `demo-corp` 0 (the tenant has 0 orders); tenant ecommerce revenue 3,570,000 · units 3 · orders 3, after one cancel 80,000 · 2 · 2 (the cancelled order is excluded live); owner screen `/ecommerce` strip renders | → `done/` |
| `TASK-MONO-808` | AC-5 | ✅ wms metrics 200 `Asia/Seoul`: qtyReceived 8,695 · qtyShipped 10 · shipmentCount 1, all in the KST 10/25 bucket (events at 16:05Z = 01:05 KST); `/wms` strip same values, monthly bar 10/25 = 10; no backfill needed (fresh volume); per-warehouse: inbound WH-MAIN 86/8,600 + WH01 1/95, outbound WH01 1/10 = strip totals | → `done/` |
| `TASK-MONO-809` | AC-4 | ✅ erp metrics 200: submitted 2 · approved 0 · rejected 0; screen renders | → `done/` |
| `TASK-MONO-810` | AC-4 | ✅ scm `GET /api/v1/procurement/po/metrics` 200: poCount 2 · poAmount 200,000 KRW; home card ₩200,000 | → `done/` |
| `TASK-MONO-811` | AC-5 | ✅ iam `GET /api/admin/metrics?tenantId=demo-corp` 200: loginSuccess 0 · newAccount 1; without `tenantId` → 400 `VALIDATION_ERROR` (the operator holds `audit.read` in more than one tenant — the documented omission rule) | → `done/` |
| `TASK-MONO-812` | AC-6 | ✅ finance metrics 200: topupCount 2 · topup 1,000,000 · transferCount 1 · transfer 100,000 KRW; home card «이번 주 이체 금액 ₩100,000» | → `done/` |
| `TASK-MONO-817` (already `done/`, not edited) | wildcard `*` | ⚪ the finance `*` tenant read needs the `*` operator activated — not done in this window | recorded here only |
| `TASK-MONO-813` | AC-3 | ✅ `.DLT` topics pre-created (iam 32 · wms 29 · erp 10 · scm 6 · finance 2 · ecommerce 1 · fan 0). One malformed record on `wms.admin.settings.v1` p1 off0 at 16:32:01Z landed within 10 s on `wms.admin.settings.v1.DLT` p0 off0, header `kafka_dlt-exception-cause-fqcn=java.lang.IllegalArgumentException`. inventory-service logged WARN `kafka_listener_failed … cause=JsonParseException`, then ERROR `kafka_listener_dead_lettered … dlt=wms.admin.settings.v1.DLT` (`TASK-MONO-816`'s logging, live) | → `done/` · finding below |
| `TASK-MONO-814` | AC-5 | ✅ Vercel Production for the four consumers (`kanggle-console` `7284959963` · `kanggle-store` `7284948646` · `kanggle-fan` `7284936901` · `kanggle-auth` `7284934419`, all `success`, recorded in the ticket's § 배포 기록) | → `done/` |
| `TASK-MONO-814` | AC-6 | ✅ owner opened the logged-in `demo-corp` overview first after login at 17:01:53Z: all six cards drew values (IAM 0 · WMS 출고 0 + 이번 주 출고 수량 10 · SCM 초안 1 + ₩200,000 · Finance 0 + ₩100,000 · ERP 내 결재 대기 2 · ecommerce 0 + ₩0), status «정상 6 · 주의 0 · 점검 불가 0». CloudWatch `AWS/Lambda` `portfolio-demo-control` at 17:01Z: Invocations 22 · Throttles 0 · Errors 0 (the 33rd window's same minute: Throttles 4–12). 15:30Z–16:35Z: 65 one-minute points, Throttles all 0 | → `done/` |
| console `TASK-PC-FE-369` | AC-2 | ✅ same measurement as `TASK-MONO-814` AC-6 (the 33rd window's `DOWNSTREAM_ERROR` cards and «WMS 점검 불가» are gone) | → `done/` (from `in-progress/`) |
| `TASK-MONO-815` | AC-3 | ✅ `/wms` throughput card label «최근 14일 · 한국 시간 기준 (2026-10-12 ~ 2026-10-25)», last day = KST today; card default WH-MAIN shows 10/25 inbound 86 (8,600), outbound 0 — the 10/25 shipment belongs to WH01, consistent with the strip total | → `done/` |
| console `TASK-PC-FE-368` | AC-4 · AC-5 | ✅ first nightly after merge: run `40087927639` success (`540dccd88`) · Vercel deploys on merge · live: home cards show the headline metric for all six domains | → `done/` |
| console `TASK-PC-FE-355` | AC-3 | ✅ owner created «308 반려 확인» (DRAFT 17:07:53Z, erp `approval_request`) and pressed 상신 → `SUBMITTED` 17:09:39Z, stage 1/1 EMP-0002 진행중, history row; «내가 위임한» shows one active grant with 회수 and no error text | → `done/` |
| `TASK-MONO-800` | AC-4 | ✅ raw UTF-8 `X-Operator-Reason: ④` on `GET /api/admin/audit` via the iam gateway and direct (18085) → 400 `BAD_REQUEST` «disallowed header or value», value not echoed, one WARN per request, ERROR 0; ASCII control → 200 | → `done/` |
| scm `TASK-SCM-BE-063` | AC-3 | ✅ deployed in `d5bb28f14`; the `TASK-MONO-810` footnote «ASN 이 상태 검사를 건너뛴다» is gone from `projects/scm-platform/specs` · ⚪ live (no `CANCELED` PO on the volume to send an ASN against) | → `done/` |
| ecommerce `TASK-BE-646` | AC-2 | ✅ order `7e457d10-56e6-4c9b-a54d-6f7f7f83885f` (`CONFIRMED`, wms `PICKING`) cancelled via the ecommerce gateway `POST /api/admin/orders/{id}/status` 200 at 16:30:29Z → `order_outbox` `OrderCancelled` 16:30:29.83 (published 16:30:30.57) · payment `REFUNDED` 3,490,000 · product reservation `RESERVED→RELEASED` · «Reservation released on cancel». AC-0 ③: the existing `CANCELLED` order `f72dcd1d` — payment `PENDING`, reservation `RELEASED` (no stranded state) | → `done/` |
| ecommerce `TASK-BE-649` | AC-3 | ✅ same order: shipping `PREPARING→CANCELLED`, one `FulfillmentCancelRequested` row in `shipping_outbox`, «echo of our own cancel request, ALERT suppressed», `ALERT wms outbound cancelled` 0 lines | → `done/` |
| wms `TASK-BE-650` | AC-3 | ✅ wms outbound `PICKING→CANCELLED` 16:30:34Z, `CancelOrderService order_cancelled … reservationHeld=true` | → `done/` |
| wms `TASK-BE-651` | AC-2 | ✅ PICKING path: reservation `21391989…` `RELEASED` (reason `CANCELLED`, `system:picking-cancelled-consumer`), SKU …2013 `reservedQty` 1→0, available 99→100 (2 RELEASE movement rows), saga `CANCELLED`, `wms.outbound.picking.cancelled.v1.DLT` 0, inventory log errors only OTel export · ⚪ the 33rd window's stuck saga (gone with the fresh volume) | → `done/` |
| iam `TASK-BE-641` | AC-3 | ✅ console logout landed on `console.hubwang.com/login` (owner) | AC-3 ticked; stays `review` — AC-1's owner-browser hint half is still open |
| wms `TASK-BE-652` | AC-2 | ✅ settings: `GET /api/v1/admin/settings` unfiltered 200, 4 rows incl. `inventory.low_stock.default_threshold_qty` = 10; `/users` → 403 `FORBIDDEN` (the demo-corp token has no `WMS_ADMIN` — not 500, and not measurable with it) · ⚪ owner did not check the console WMS 운영설정 screen separately | AC-2 ticked; stays `review` — AC-0 is open |
| ecommerce `TASK-BE-645` | AC-2 | ⚪ 0 `BACKORDERED` orders | stays `review` |
| wms `TASK-BE-647` | AC-2 | ⚪ min available 85, no SKU below 10, 0 alerts | stays `review` |
| wms `TASK-BE-644` | AC-3 | ⚪ setting write → 403 (`WMS_ADMIN`); not decidable on data either (min available 85; threshold 25 would still give 0 rows) | stays `review` |
| scm `TASK-SCM-BE-062` | AC-3 | ⚪ 0 `reorder_suggestion` rows (the column exists) | stays `review` |
| console `TASK-PC-FE-358` · `TASK-PC-FE-364` · `TASK-PC-FE-310`; `TASK-FE-106`; `TASK-FAN-FE-*` | AC-4 · AC-3 · AC-11 · … | not measured in this window | stay `review` |

**Findings:**
- wms inventory-service's dead-letter resolver still returns the source partition (`KafkaConsumerConfig.java:49-50`, `record.partition() < 0 ? 0 : record.partition()`), so Kafka warned «Destination resolver returned non-existent partition wms.admin.settings.v1.DLT-1, KafkaProducer will determine partition». The record was still delivered (p0). Recorded in `TASK-MONO-813` AC-3, not reopened and not ticketed — the owner decides whether it gets one.
- The ERP delegation rows show raw ids (`01a16eaa-980d-…`) instead of employee names (recorded in `TASK-PC-FE-355`; it was already that ticket's Out of Scope).
- The first publish of `ecommerce.fulfillment.cancel-requested.v1` warned `UNKNOWN_TOPIC_OR_PARTITION` once (the topic was auto-created; the flow was fine) — recorded in `TASK-BE-649`.
- The two seed orders with ecommerce shipping `SHIPPED` / `DELIVERED` while their wms outbound is `PICKING` are still there (carried from the 33rd window's small findings).
- The scm metrics path is `/api/v1/procurement/po/metrics`; the `tasks/INDEX.md` row of `TASK-MONO-810` wrote it without `v1`.

**Left behind on the demo volume:** order `7e457d10-…` cancelled and refunded (wms outbound `CANCELLED`, reservation released); 1 malformed record on `wms.admin.settings.v1` and 1 on `wms.admin.settings.v1.DLT`; the ERP request «308 반려 확인» `SUBMITTED` (stage 1/1 EMP-0002); `login_history` rows from the measurement logins; `AUDIT_QUERY` rows.

## 35th window — preparation: make the data the ⚪ ACs lacked (2026-10-25 UTC, before the window)

The 34th window left five ACs ⚪ because the demo volume did not hold the state they measure (§ 34th, results table). Run the same window again and they stay ⚪. This is the procedure to create that state on purpose and to undo it afterwards. **Read from code, not run** — every step names its source; re-check the first response of each call before going on. Tags: [R] read in code/spec/seed, [I] inferred.

**Common.** Operator token = `operator_token <tenant>` (`infra/demo/seed/lib.sh:218-235`, `demo@demo.com` → assume `demo-corp`; ecommerce writes use `ecommerce`) [R]. wms gateway accepts `/api/v1/**` only; every write needs a fresh UUID `Idempotency-Key` (`infra/demo/seed/seed-wms.sh:64-77`) [R]. Bundles: wms · scm · ecommerce + the relay (`infra/demo/projects.sh:105-106` `RELAY_DOMAINS`, `infra/demo/relay/mm2.properties:103-104` carries `wms.inventory.alert.v1` to scm) [R]; check the relay with `bash infra/demo/relay/probe-relay.sh`.

**The one SKU to use: `SKU-APPLE-001`** — the only SKU with an scm mapping and reorder policy (`seed-scm.sh:162-171`: `reorderPoint 10 · safetyStock 5 · reorderQty 50`) [R]; wms SKU `01910000-0000-7000-8000-000000000403` (`seed-wms.sh:136-139`) [R]. The 34th window's «min available 85» is this SKU (95 received − 10 shipped) [I] — read it first: `GET /api/v1/inventory?skuId=01910000-0000-7000-8000-000000000403` (`INVENTORY_READ`). Any other SKU raises the wms alert but scm sends it to the DLT as unmapped (`EvaluateReorderUseCase.java:106-113`) [R].

| Step | For | Action | Expect | Source |
|---|---|---|---|---|
| 0 | all | baselines: `lowStockOnly=true` rows, `GET /api/wms/alerts`, `GET $SCM/api/v1/demand-planning/suggestions?skuCode=SKU-APPLE-001`, policy present | 0 low-stock rows · 0 alerts for the row · 0 open suggestions | 672 § 34th |
| 1 | `TASK-BE-644` AC-3 | `POST /api/v1/inventory/adjustments` `{"inventoryId":"<id>","bucket":"AVAILABLE","delta":-70,"reasonCode":"ADJUSTMENT_LOSS","reasonNote":"35th window 644"}` → available 15 | 201 · **no** alert (15 is not `< 10`) · console `GET /api/wms/inventory?lowStockOnly=true` still 0 rows (threshold 10) | `AdjustmentController.java:69-90` (`INVENTORY_WRITE`) · `LowStockDetectionService.java:33,81` |
| 2 | `TASK-BE-644` AC-3 | **owner decision 2026-10-25 UTC: write the setting row on the host** (no IAM token can carry `WMS_ADMIN` — `DomainRoleCatalog` excludes it, `TASK-MONO-547`): `dbexec --why "35th window: BE-644 AC-3 threshold 25" wms-postgres psql admin_db postgres` ← `UPDATE admin_setting SET value_json='25'::jsonb, version=version+1, updated_at=now(), updated_by='demo-window' WHERE key='inventory.low_stock.default_threshold_qty' AND warehouse_id='00000000-0000-0000-0000-000000000000';` | the same console read now returns the row (15) with `lowStockFlag=true` — the filter follows the setting row (`<`, decision (i)). 🔵 Measures the read path only; the write API and its permission are not measured (record that in the ticket) | `admin-service/.../R__seed_dev_data.sql:172,185-193` · `lib.sh:278-295` · `admin-service` datasource `admin_db` |
| 3 | `TASK-BE-647` AC-2 · `TASK-SCM-BE-062` AC-3 | same row `delta:-10` (→ 5; anything ≤ 9) | wms: 201, log `inventory.low-stock-detected emitted … threshold=10`, alert row in `GET /api/wms/alerts`. scm (via relay, seconds): a `SUGGESTED` row with **`reorderPointAtTrigger: 10`** (`triggerAvailableQty` 5 [I]) | `LowStockDetectionService.java:73-105` · `OutboxPublisher.java:70-72` (`wms.inventory.alert.v1`) · `EvaluateReorderUseCase.java:97-164` |
| 4 | `TASK-BE-645` AC-2 | buyer token (`seed-ecommerce.sh:252-254`) → `POST /api/orders` with `quantity: 100000` → pay (`/api/payments` + `/confirm`, `demo-pg`) → poll until `BACKORDERED` → `operator_token ecommerce` `POST /api/admin/orders/{id}/status` `{"status":"CONFIRMED"}` | **400 `INVALID_ORDER_REQUEST`**, order unchanged (guard `AdminOrderStatusService.java:37-53`, now `TASK-BE-648`) | `ReservationService.java:93-140` · `Order.java:239-243` · `order-api.md:441-490` |
| 5 | `TASK-MONO-817` (wildcard, recorded) | `sudo bash /opt/monorepo-lab/infra/demo/owner-operator.sh activate` → `star-owner@demo.com` (password from SSM `/portfolio-demo/owner-operator/password`, never echoed) → token exchange `audience='*'` → finance `GET /api/finance/accounts/metrics` | **403 `PERMISSION_DENIED`** «metrics require a concrete tenant»; ecommerce `GET /api/admin/orders/metrics` 403; control: `demo-corp` 200 | `owner-operator.sh:18-24,238-250` · `TransactionMetricsController.java:43,61` |

**Order matters.** Step 1 → 2 before step 3: step 3's alert reads inventory-service's threshold (10, unchanged by the row write [I]) and the Redis SETNX debounce is **1 hour per inventory row** (`LowStockDetectionService.java:84` → `RedisLowStockAlertDebounceAdapter.java:22,33`) — a second drop on the same row inside the hour raises nothing. scm will not raise a second open suggestion for the same sku · warehouse (`EvaluateReorderUseCase.java:131,140`).

**Undo, in order:** dismiss the suggestion (`POST $SCM/api/v1/demand-planning/suggestions/{id}/dismiss`, `SCM_DEMAND_PLANNING_WRITE` — leaves a `DISMISSED` row) · restore stock (`delta:+80`, `ADJUSTMENT_FOUND`) · threshold row back to `'10'` (same `dbexec`) · cancel the backordered order (`{"status":"CANCELLED"}` → refund, reservation released) · `owner-operator.sh deactivate`. Record what stays on the volume (alert row, DISMISSED suggestion, CANCELLED order, REFUNDED payment).

**Also measurable in the same window (already merged, no preparation):** `TASK-BE-657` AC-3 (a poisoned record from a partition ≠ 0 reaches `.DLT` with no «non-existent partition» warning — needs a source partition ≠ 0) · `TASK-PC-FE-371` AC-5 (`/erp/delegation` rows show `번호 · 이름`; console, Vercel) · `TASK-BE-656` (audit view of a non-fan-platform / pool account shows login history; needs the next bake).

## 35th window — results (2026-10-25 UTC, ~07:55Z–09:25Z · budget 1934 → 2019 / 2400)

**Bake (item 18, continued):** owner approval «35차 굽기 승인 — 54b79233c» → `bake.sh --ref bake-54b79233c`, packer rc=0, 53 min 40 s, AMI `ami-068ae07b2b3ac7797` (`portfolio-demo-1792908961`), tag RepoCommit `54b79233c` → provenance `ami-tag`. Pre-checks: open PRs 0 · `main` `54b79233c` CI and Nightly E2E success · dry-run rc=0 · demo host stopped. Pin PR #4558 (squash `1ccccdb88`). Plan after the pin merge: 1 add · 2 change · 1 destroy (instance replace; Lambda and IAM in place); `terraform apply` (owner) → instance `i-0fce644708d189f79`, Lambda `AMI_REPO_COMMIT=54b79233c`, `BUNDLE_SELECTION_CAPABLE=yes`. `check-ami-generation.sh --with-aws` rc=0 («same generation»). The previous AMI `ami-0d9f8da7d20d7a180` (`d5bb28f14`) is the rollback copy; pruning the older 33rd-generation AMI is an owner decision and was not done. **Window:** fresh volume, nine bundles in one request, all ready; closed with `/stop`, EC2 `stopped` and the control API `/status` `stopped` both confirmed. Owner browser (Vercel production) plus agent API/host checks over SSM; raw outputs stay in the session scratchpad, not in the repo.

**Boot gate (`TASK-MONO-818`).** Journal `[seed:` lines «주문 4: wms 출하 확정 → 이벤트로 배송 SHIPPED (wms created)» and «주문 5: … (wms created)»; «wms 경로로 출고하지 못했습니다» = 0; seed ✗ lines = 0 (the only ⚠ is the known minio image-seed warning); 100 containers, unhealthy or exited 0 except `ecommerce-minio-init` Exited (0) (normal); the second `seed-wms` run: «존재 출고 SO-DEMO-0001 이미 SHIPPED».

**Results.** The evidence is in each ticket's `## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)` section, appended by the recording PR. Nothing moved between queues: «close chore» below means the ticket's own text reads all ACs closed against the verb each AC uses, and the four-dimension close chore comes later.

| Ticket | AC | Result | Where it goes |
|---|---|---|---|
| `TASK-MONO-818` | AC-1 | ✅ slot 4 / 5 boot lines; second run «이미 SHIPPED» | close chore |
| `TASK-MONO-818` | AC-3 | ✅ slot 4 order `c76625ca…`: ecommerce `SHIPPED`, wms outbound `SHIPPED` saga `COMPLETED`, tracking `SHP-20261025-5153` = wms `shipment_no`, `wmsRouted:true`. Slot 5 `9d05ca68…`: `DELIVERED`, wms `SHIPPED/COMPLETED`, tracking `SHP-20261025-2800` = `shipment_no`, history PREPARING→SHIPPED→IN_TRANSIT→DELIVERED | close chore |
| `TASK-MONO-818` | AC-4 | ⚪ needs a boot with ecommerce but without wms; every bundle started. The AC text accepts «⚪ with the reason» | close chore |
| `TASK-BE-644` | AC-3 | ✅ **read path only.** `SKU-APPLE-001` available 85 → adjustment −70 → 15 (201). Threshold 10: `GET /api/v1/admin/dashboard/inventory?lowStockOnly=true` 0 rows, alerts 0. `admin_setting` row 10 → 25 (`dbexec --why`, version 0→1): the same read returns the row (available 15) with `lowStockFlag:true`. The write API and its permission were not measured (owner decision 2026-10-25) | close chore |
| `TASK-BE-647` | AC-2 | ✅ adjustment −10 → 5 (201); inventory-service log `inventory.low-stock-detected emitted inventoryId=d58d1efa… available=5 threshold=10`; alert row `LOW_STOCK` `thresholdQty 10` `actualQty 5` in `GET /api/v1/admin/dashboard/alerts` | close chore |
| `TASK-SCM-BE-062` | AC-3 | ✅ new-row half: within 5 s via the relay, suggestion `4d55514c…` `SUGGESTED` source `ALERT`, `reorderPointAtTrigger: 10`, `triggerAvailableQty: 5`, `suggestedQty: 50` · ⚪ old-row-null half (a fresh volume has no pre-migration suggestion) | stays `review` · console follow-up ticket not drafted (AC-3 asks for it) |
| `TASK-BE-645` | AC-2 | ✅ buyer order quantity 100000 (오버핏 후드 집업) → payment confirm 200 → `BACKORDERED` within 10 s → operator (ecommerce) `POST /api/admin/orders/{id}/status {"status":"CONFIRMED"}` → 400 `INVALID_ORDER_REQUEST` «An order cannot be confirmed by an operator (current status: BACKORDERED) …»; order stays `BACKORDERED` | close chore |
| `TASK-BE-657` | AC-3 | ✅ `wms.admin.settings.v1` 3 partitions, `.DLT` 1. Six keyed malformed records at 08:11:16Z landed p0×2 p1×1 p2×3; `.DLT` offset 0 → 6 within 10 s; inventory-service 6 × `kafka_listener_dead_lettered` incl. partition=1 and 2; «non-existent partition» 0 in every wms service (34th window, same shape: warned) | close chore |
| `TASK-BE-652` | AC-2 | ✅ API half again (`GET /api/v1/admin/settings` 200, 4 rows incl. the threshold) · screen half ✅ (owner): WMS 운영설정 lists 예약 TTL 24 and 저재고 기본 임계치 10 with no error, «프로젝션 상태 = 권한 없음». Screen 2 rows vs API 4 rows (cause not measured); the 권한 없음 is probably the no-`WMS_ADMIN` token (inference) | stays `review` — AC-0 untouched |
| `TASK-BE-641` | AC-1 | ✅ edge log 08:34:37Z `GET /connect/logout?id_token_hint=…&post_logout_redirect_uri=…console.hubwang.com%2Flogin&client_id=platform-console-web` → 302 (8 ms); browser on `console.hubwang.com/login`; «로그인» again shows the password form (the console sends no `prompt`/`max_age`, so the IdP session ended). The call came server-side (source `13.217.225.232`, UA «-») | close chore |
| `TASK-PC-FE-371` | AC-5 | ✅ (owner) `/erp/delegation`: «내가 위임한» `EMP-0002 · 이운영`; 위임 현황 delegator `EMP-0001 · 김본부`, delegate `EMP-0002 · 이운영`; no raw id; Vercel deploy-hook run on `main` success | close chore |
| `TASK-PC-FE-358` | AC-4 | ✅ both halves (owner): logged-in `/wms` «일별 입고 · 출고» 14 days 10/12–10/25, empty days 0, 10/25 입고 86 · 출고 2; anonymous sample: same card with WH01 sample values · ⚪ the empty-14-day-window text (the volume has data today) | close chore (the AC conditions that text on an empty seed) |
| `TASK-PC-FE-364` | AC-3 | logged-in ✅ and anonymous ✅ (owner): the four new labels (미해소 대사 차이 · 내 결재 대기 · 제출 대기 발주 (초안) · 출고 대기 주문 (결제 완료)), six cards with values, 도메인 상태 정상 6 · ⚪ the per-permission «권한 없음» table (needs operators with single permission sets; the seed has none) | stays `review` |
| `TASK-PC-FE-310` | AC-11 | ✅ anonymous half (owner): the overview ends with «제품·테넌트» listing six «(샘플) · 1개 테넌트 이용 가능 · sample» products (the screen title is «제품·테넌트», the ticket says «제품·테넌트 전체»); logged-in half closed in the 32nd window | close chore |
| `TASK-FAN-FE-028` | AC-0 | ✅ (owner) «팔로잉» → «팔로우» in under 1 s (23rd window: ≥ 3 s, needed reload); one direction measured (unfollow) | close chore |
| `TASK-FAN-FE-029` | AC-2 | ✅ (owner) a post the viewer reacted to reopens with the reaction pressed; an unreacted post is not pressed | close chore |
| `TASK-FAN-FE-032` | AC-5 | 200 path ✅ (owner: comment list visible, compose and delete work) · ⚪ the 404 branch (the current backend serves the list endpoint, so a 404 cannot be produced) | stays `review` |
| `TASK-FE-106` · `TASK-FAN-FE-027` | AC-0 · AC-2 · AC-3 / AC-1 | ⚪ not measured — the owner chose to skip the 35-min idle. FE-106 AC-0 needs a pre-fix deploy, which production no longer is; AC-2 / AC-3 can run alongside other work in a later window | stay `review` |
| `TASK-MONO-817` (`done/`, CORRECTION appended) | `*` live | owner-operator activated → token `tenant_id '*'`: finance `GET /api/finance/accounts/metrics?period=month` → 403 `PERMISSION_DENIED` «metrics require a concrete tenant» ✅; ecommerce `GET /api/admin/orders/metrics?period=month` → 403 `ACCESS_DENIED` «Insufficient permissions to access resource» — 403, but from the generic permission gate, not the metrics-specific message. Controls: `demo-corp` finance 200, `ecommerce`-tenant ecommerce 200. The first attempt without `period` gave 400 (an invalid request, not a verdict). Deactivated after (`SUSPENDED`) | recorded only |
| `TASK-BE-656` (`done/`) | live observation | ✅ `demo@` (account `0199de70-…-ad03`) has 44 `login_history` rows, all `tenant_id='iam'`; with the `*` operator `GET /api/admin/audit?accountId=…ad03&tenantId=iam&source=login_history` → 200 with rows (SUCCESS/ATTEMPTED), `tenantId=*` the same. 🔴 the demo-corp operator gets 403 `TENANT_SCOPE_DENIED` for `tenantId=iam`/`*`/`fan-platform` and an empty 200 for `demo-corp` — it cannot see its own operators' login history. DB: `consumer-pool` 24 rows / 7 accounts, `iam` 56 / 3 | recorded only |
| `TASK-BE-660` (`done/`) | live confirmation | ✅ `source=LOGIN_HISTORY` → 400 `VALIDATION_ERROR` «source must be one of [suspicious, admin, login_history] (was: LOGIN_HISTORY)»; an omitted `tenantId` with `audit.read` in more than one tenant → 400 «tenantId is required» | recorded only |
| `TASK-MONO-819` | AC-2 | ⚪ still no script-only PR since #4550 | stays open |

**Findings (three new defects — each drafted in a separate PR, not here):**
1. **Console wms alert schema vs the producer's key.** `projects/platform-console/apps/console-web/src/features/wms-ops/api/types.ts:238` `AlertRowSchema.alertId: z.string()` is required, but wms admin-service `AlertLogResponse` (`projects/wms-platform/apps/admin-service/src/main/java/com/wms/admin/api/dashboard/dto/AlertLogResponse.java:8`) serialises the id as `id`. With ≥ 1 alert the zod parse throws → `getWmsSectionState` catch → degraded (`wms-state.ts:107-109`) → the whole logged-in `/wms` overview shows «wms 운영 정보를 일시적으로 불러올 수 없습니다». Live contrast: 1 alert row → degraded; row deleted (owner decision, `dbexec --why`) → 0 rows → the page renders. The anonymous sample renders the alert table because its fixture uses `alertId` — fixture and producer drifted and hid it. The acknowledge button also uses `alertId` (`WmsOpsScreen.tsx:112`, `use-wms-ops.ts:453`), which is `undefined` against real rows. The contract (`admin-service-api.md` § `GET /api/v1/admin/dashboard/alerts`) says only «paginated list of AlertLog rows» with no field names, so the fix must pin the name in the contract first.
2. **Fan reaction cannot be removed.** `projects/fan-platform/web/fan-platform-web/src/features/post/ui/ReactionBar.tsx:31-38` re-PUTs the active type (the backend is an idempotent no-op); `removeReaction` (`features/post/api/reactions.ts:21-28`, DELETE) has no caller. The backend DELETE exists (`ReactionController.java`, 204; contract `community-api.md:464-466`). The owner saw clicking an active reaction not cancel it.
3. **Fan following-feed counts are stale for up to 5 minutes.** `GET /api/v1/community/feed` serves Redis `FeedItemSnapshot` pages that carry `commentCount`/`reactionCount`; invalidation is TTL only (`FeedCacheRepository.java:36-37,62`, 5 min) and reaction/comment writes never evict. Live (09:23Z): PUT reaction → post detail `reactionCount` 1, `/feed` the same moment 0; Redis key `feed:v3:fan-platform:<account>:0:10` ttl 297 (the reaction was undone with DELETE 204). The owner-approved direction is «결함으로 보고 고친다»; the recommended shape is to keep the page cache and overlay live `countsByPostIds` at read time (as entitlement already is) rather than evict on write, because the key is per account × page and a write would have to evict every follower's pages.

**Side findings (recorded, not ticketed here):** `TASK-SCM-BE-062` — the suggestion's `warehouseId` holds the LOCATION id (`01910000-…-000000001001`) while the wms alert carries `warehouseId: null` + `locationId`. `TASK-BE-652` — the screen shows 2 settings rows where the API returns 4 (cause not measured).

**Left behind on the demo volume:** the wms alert row was deleted (owner decision); suggestion `4d55514c…` `DISMISSED`; `SKU-APPLE-001` available 85 (adjustments −70 / −10 / +80); the threshold row value 10, version 2, `updated_by` `demo-window`; order `d48c1c34…` `CANCELLED` (the payment refund was not measured); 6 poisoned records on `wms.admin.settings.v1.DLT`; the owner operator `SUSPENDED`.

**Still open → 36th window:**
- `TASK-MONO-818` AC-4 is ⚪ and accepted by its own text; nothing to run unless the owner wants the ecommerce-only boot measured.
- `TASK-PC-FE-364` AC-3 — the per-permission «권한 없음» table: needs operators holding single permission sets (the seed has none).
- `TASK-PC-FE-358` — the empty-14-day-window text (only reachable on a volume with no throughput data in the window).
- `TASK-BE-652` AC-0 — ①~③ as file:line / IT output, especially ③ (the console has no setting write path); also why the screen shows 2 rows against the API's 4.
- `TASK-FE-106` AC-0 · AC-2 · AC-3 and `TASK-FAN-FE-027` AC-1 — the ≥ 35-min idle return; run alongside other work (FE-106 AC-0 needs a pre-fix deploy, so it needs an owner decision).
  - 🔵 2026-10-26 UTC: FE-106 AC-0 is ⚪ (owner decision — no pre-fix build in production); AC-2 · AC-3 and FAN-FE-027 AC-1 are in the one shared idle run listed in § Order item 20.
- `TASK-FAN-FE-032` AC-5 — the 404 branch cannot be produced against the current backend (owner decision: ⚪ plus the unit test, or keep open).
  - 🔵 2026-10-26 UTC: decided ⚪ + `comment-section-404.test.tsx`; the ticket moved to `done/` (4-dim, #4197).
- `TASK-SCM-BE-062` AC-3 — the old-row-null half, and the console follow-up ticket the AC asks for.
  - 🔵 2026-10-26 UTC: old-row half ⚪ (owner decision), follow-up drafted as `TASK-PC-FE-377`; the ticket moved to `done/` (4-dim, #4477).
- Checks added 2026-10-26 UTC (§ Order item 20): `TASK-BE-661` AC-4 · `TASK-FAN-BE-053` AC-8 (next bake) · `TASK-BE-624` AC-4 · `TASK-PC-FE-373` AC-8 · `TASK-FAN-FE-033` AC-6 (no bake).
- `TASK-MONO-819` AC-2 — no script-only PR yet.
- The live ACs of the three new defect tickets, once they are filed and merged.
- Owner decision (not a measurement): pruning the older 33rd-generation AMI.

## 36th window — results (2026-10-28 UTC, 02:54Z–04:46Z · budget 2019 → 2128 / 2400)

**Bake (item 18, continued):** `bake.sh --ref bake-0777e56ea`, packer rc=0, 52 min 47 s, AMI `ami-0bb9778ca4a73e75c`, tag RepoCommit `0777e56ea` → provenance `ami-tag`. Pre-checks: open PRs 0 · remote task branches 0 · `main` CI and Nightly E2E success at `0777e56ea` · dry-run rc=0 · demo host stopped. Pin PR #4594 (squash `5b91bffc6`). Owner `terraform apply`: 1 add · 2 change · 1 destroy → instance `i-02489b46109b29714`, Lambda `AMI_REPO_COMMIT=0777e56ea`; `check-ami-generation.sh --with-aws` rc=0. The 34th-generation AMI was pruned (#4595, owner decision). **Window:** opened 02:54:31Z with nine bundles in one `/bundle/start` request, all ready; closed 04:46Z with `/stop`, EC2 `stopped` and the control API `/status` `stopped` both confirmed; used_minutes 2019 → 2128 of 2400. Agent checks ran over SSM `AWS-RunShellScript`; the owner used the browser on Vercel production.

**Boot gate.** 97 containers Up, 3 Exited (0) init containers (`ecommerce-minio-init`, `wms-kafka-init`, `iam-kafka-init`); seed ✗ lines 0; the only ⚠ is the known minio image-seed warning; journal line «[seed] 모든 도메인 시드 완료».

**Results.** The evidence is in each ticket's `## CORRECTION — 36차 창 라이브 결과 (2026-10-28 UTC)` section. Nothing moved between queues; the four-dimension close chore comes later.

| Ticket | AC | Result | Where it goes |
|---|---|---|---|
| `TASK-BE-661` | AC-4 | ✅ `SKU-APPLE-001` available 85 → −80 → 5 (201, 03:11:17Z); within ~4 s scm suggestion `c873edf0…` `SUGGESTED` source `ALERT` with `"warehouseCode":"WH01"`, `reorderPointAtTrigger` 10, `triggerAvailableQty` 5, `suggestedQty` 50; inventory-service log `inventory.low-stock-detected emitted … available=5 threshold=10`. The suggestion's `warehouseId` still holds the LOCATION id (35th side finding, unchanged). Console «창고» column shows WH01 (owner) | close chore |
| `TASK-FAN-BE-053` | AC-8 | ✅ account `…ec01`, post `01a1807a-e70a…`: before detail 0 / feed 0, key `feed:v3:fan-platform:<account>:0:10` ttl 245; PUT LOVE 200 → detail 1 (LOVE) and feed 1 at ttl 244; DELETE 204 → 0 / 0, ttl 244 (key not evicted — counts are overlaid at read). 35th measured 1 vs 0 | close chore |
| `TASK-PC-FE-373` | AC-8 | ✅ logged-in `/wms` (demo-corp) with 1 alert row rendered the whole overview; row «LOW_STOCK 현재 5 / 임계 10 · 위치 01910000-0000-7000-8000-000000001001»; «확인 처리» → «확인됨»; server alert `01a1807d-98a2-…` `acknowledgedAt 2026-10-28T03:26:57.810517Z` `acknowledgedBy 0199de70-0000-7000-8000-00000000ad03` | close chore |
| `TASK-PC-FE-377` | AC-7 | ✅ `/scm/replenishment` row SKU-APPLE-001 · WH01 · SUP-001 · 50 shows «재주문점 10 · 발동 5 · 제안 50»; owner confirmed the bar is drawn | close chore |
| `TASK-MONO-820` | AC-1 (extra evidence) | first boot: `[seed:wms]` 적치 50 · 출하 52, `[seed:scm]` 창 안 60 건, `[seed:erp]` 창 안 49 건, `[seed:iam]` 로그인 178 · 잠금 6; second same-day `seed.sh iam wms scm erp` rc=0 in 10 s, all four «존재 … (수렴, +0)» (PO rows stayed 63) | recorded in the ticket |
| `TASK-MONO-820` | AC-2 | ✅ API (demo-corp token), period=month: SCM poAmount:KRW nonzero days 28, ERP approved 27, WMS qtyShipped 28; previous week: SCM 11,497,000, ERP 4, WMS qtyShipped 201. IAM computed by SQL on `login_history` (admin API 401 `TOKEN_INVALID`: IAM admin-service pins `ADMIN_OIDC_ISSUER=https://auth.hubwang.com`, a measurement-route issue): failure days 27, week current 11/3, previous 10/4 | recorded in the ticket |
| `TASK-MONO-820` | AC-3 | ✅ WH01 + WH-MAIN `/dashboard/throughput` summed per day vs `/dashboard/metrics?period=month`: 28 days compared, 0 mismatches | recorded in the ticket |
| `TASK-MONO-820` | AC-6 | ✅ (owner) WMS «출고 수량 · 일별» 10/1–10/28 all nonzero and equal to the API; SCM · ERP · IAM monthly bars ≥ 10 days; operator cards: 로그인 실패 3 (직전 4), 출고 수량 138 (직전 201), 발주 금액 ₩1,064,800 (직전 ₩11,497,000), 승인 3 (직전 4) | close chore (AC-4 · AC-5 untouched) |
| `TASK-MONO-820` | ERP inbox §9 bullet | not measured | open |
| `TASK-BE-624` | AC-4 | ✅ owner signed up `be624-1028@example.com` (store, incognito) and logged in; IAM account `613e0a5d-88b5-4605-ba90-1b0b4e963a19` ACTIVE 04:41:26Z; `user_db.user_profiles` row with the same user_id has that email (created 04:41:27Z, updated 04:41:49Z — not blanked). `GET /api/users/me` itself was not called; the row it reads was inspected | close chore |
| `TASK-FAN-FE-033` | AC-6 | ✅ (owner) re-clicking the pressed reaction removed it | close chore |
| `TASK-FE-106` · `TASK-FAN-FE-027` | AC-2 / AC-1 | ✅ owner logged into store and fan, idled from 03:33:59Z, returned after 04:09Z: both sessions stayed logged in; Traefik: `POST /oauth2/token` 200 from Vercel at 04:10:15Z (2388 bytes) and 04:10:16Z (2255 bytes) = the two rotations at return; auth-service log since 03:33Z: no `replay` / `reuse detected`. A first idle start at 03:07:50Z was void (owner not logged in). FE-106 AC-0 stays ⚪ (earlier owner decision) | close chore |
| `TASK-FE-106` | AC-3 | ✅ fan client refresh token A → 200, rotated to B; 40 s later (> 30 s grace) A → 400 `invalid_grant` «Refresh token reuse detected — all sessions have been revoked.»; then B → 400 `invalid_grant`; log `SAS_REFRESH: reuse detected — revoking the refresh-token family. account=0199de70-0000-7000-8000-00000000ec01, children=1`. Side effect: that demo fan account's sessions were revoked | close chore |
| `TASK-FAN-FE-034` | AC-4 | ✅ impl PR #4597 (squash `563c02501`); Vercel `Production – kanggle-fan` deployed `563c02501` at 04:07:01Z; owner confirmed light background with dark text in the comment input | close chore |
| `TASK-PC-FE-364` | AC-3 | ⚪ not measured again — needs operators holding single permission sets; the seed has none | stays `review` |

**Findings:**
1. `TASK-PC-FE-373` side finding: the alert table's location column shows the raw location UUID, not the code, because the alert row carries no `locationCode`. Recorded, not ticketed.
2. `TASK-MONO-820`: the IAM admin metrics API cannot be measured with a token minted inside the host via `iam.<ip>.sslip.io` (401 `TOKEN_INVALID`), because IAM admin-service pins `ADMIN_OIDC_ISSUER=https://auth.hubwang.com`. A measurement-route limit, not a defect; IAM was computed by SQL instead.
3. `TASK-BE-661`: the suggestion's `warehouseId` still holds the LOCATION id (35th side finding, unchanged).

**Left behind on the demo volume:** wms alert `01a1807d…` acknowledged; suggestion `c873edf0…` `DISMISSED`; `SKU-APPLE-001` available 85 (adjustments −80 / +80); new account `be624-1028@example.com`; fan account `…ec01` refresh-token family revoked; a second seed run of `iam wms scm erp`.

**Still open → next window:**
- `TASK-PC-FE-364` AC-3 — single-permission operators.
- `TASK-MONO-820` — the ERP inbox §9 bullet.
- `TASK-MONO-648` — capture window.
- `TASK-MONO-819` AC-2 — script-only PR.
