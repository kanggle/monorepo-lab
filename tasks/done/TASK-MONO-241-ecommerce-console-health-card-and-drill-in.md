# Task ID

TASK-MONO-241

# Title

ecommerce 를 platform-console 의 6번째 **도메인-health 카드**로 노출 + **`/ecommerce` 드릴인 페이지** 추가 (ADR-MONO-030 Step 4 facet (a-후속) — 콘솔 통합 수직 슬라이스 2). console-bff `DomainTarget.ECOMMERCE` fanout(health 경로) + `EcommerceHealthReadPort`/어댑터 + `CompositionEngine` per-route 카드오더 분리(health=6, overview=5 net-zero) + console-web `/ecommerce` 드릴인 라우트(클릭 시 미존재 라우트 해소 = MONO-240 의 명시적 후속) + 계약 §2.4.9.2 정합. **cross-project** (platform-console + ecommerce 게이트 actuator 확인). operator-overview **스냅샷 leg**(도메인 메트릭)는 net-new ecommerce operator-plane read 엔드포인트가 필요 → facet (a-후속-2) 로 분리.

# Status

done

# Owner

backend

# Task Tags

- monorepo
- cross-project
- console-integration
- adr-mono-030
- platform-console

---

# Dependency Markers

- **선행 (prerequisite, 전부 done)**:
  - `TASK-MONO-240` — facet (a): ecommerce 가 카탈로그 6번째 구독가능 도메인 타일로 렌더 (done, squash `4c06338b9` + close `db4eb6b6f`). 본 태스크는 그 타일의 `baseRoute=/ecommerce` 드릴인 라우트 부재를 해소하고 health 가시성을 추가한다.
  - `TASK-BE-357` — Step 2 바깥 축: ecommerce gateway `TenantClaimValidator` 고정슬러그→entitlement-trust 진화 (done). 본 태스크는 ecommerce 도메인 코드를 건드리지 않는다(게이트 actuator/health 노출 확인만).
  - `TASK-PC-FE-013` — Domain Health Overview dashboard (done) + `TASK-PC-BE-005` — `CompositionEngine` L6 추출 (done). 본 태스크가 확장하는 fanout 엔진.
- **근거 (ADR)**: [ADR-MONO-030](../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) §3.4 Step 4 (D5/D6 deferred follow-up) "콘솔 통합 — 원래 동기"; MONO-240 task § Out of scope 가 본 슬라이스(=health/overview 카드 + DomainTarget fanout + 드릴인 페이지)를 명시적 후속으로 지정.
- **scope 결정 (user, 2026-06-13)**: facet **(a-후속) 콘솔 드릴인** 선택. 본 슬라이스 = **도메인-health 6번째 카드 + `/ecommerce` 드릴인 페이지**. operator-overview **스냅샷 leg**(ecommerce 도메인 메트릭, 예: 활성 상품 수)는 ecommerce 에 net-new operator-plane tenant-scoped count 엔드포인트가 없어(현재 `AdminProductController` 는 전부 write) **facet (a-후속-2) 로 분리** — 별 세션.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (console-bff fanout 엔진 per-route 분리 + 다계층 계약/테스트 정합; 단순 어댑터 복제가 아니라 공유 `CompositionEngine.CARD_ORDER` 결합 해제가 핵심 설계 변경).

---

# Goal

ADR-MONO-030 의 원래 동기 — "웹스토어 어드민을 콘솔에서 보고 싶다" — 의 **나머지 절반**을 닫는다. MONO-240 이 카탈로그 타일을 띄웠으나 타일 클릭 시 `baseRoute=/ecommerce` 라우트가 부재했다. 본 태스크는 (1) ecommerce 를 **도메인-health 대시보드의 6번째 카드**로 노출하고(console-bff 가 ecommerce 게이트 `/actuator/health` 로 fanout), (2) console-web 에 **`/ecommerce` 드릴인 페이지**(read-only, 다른 도메인 섹션과 동형 degrade)를 추가해 타일→페이지 흐름을 완성한다. operator-overview 스냅샷 카드(도메인 메트릭)는 ecommerce 에 operator-plane read 엔드포인트가 필요하므로 정직하게 후속으로 분리한다.

# Scope

본 변경은 **platform-console 내부**(console-bff + console-web + 계약)가 주이며, ecommerce 는 **게이트 actuator/health 노출 확인(+필요시 permitAll 1줄)** 만 — cross-project 이나 ecommerce 도메인/데이터 코드는 불변.

## In scope

### A. 계약 (contract-first — 구현 전 갱신)

1. **`projects/platform-console/specs/contracts/console-integration-contract.md`**:
   - **§ 2.4.9.2 (Domain Health Overview)**: 카드 집합 `iam|wms|scm|finance|erp` → **`ecommerce` 추가(6번째)**. 고정 leg 순서·envelope 스키마를 6 으로 정합. ecommerce health leg = `GET /actuator/health` (auth 없음, tenant 없음 — 다른 5 health leg 와 동형, ADR-MONO-017 §D4 는 data-API leg 에만 적용).
   - **§ 2.4.9.1 (Operator Overview)**: **5 유지** + ecommerce 스냅샷 leg 가 후속(facet a-후속-2)임을 1줄 명시(health 와 overview 의 도메인 집합이 의도적으로 다름 — health=6, overview=5; 두 대시보드는 독립 표면). "all 5 domains" 류 문구가 health(§2.4.9.2)에 있으면 6 으로, overview(§2.4.9.1)는 5 로 정확히 구분.
   - **§ 2.4.5/6 per-domain credential 표**: `ECOMMERCE → IAM OIDC access token`(WMS/SCM/FINANCE/ERP 와 동일) 행 추가 — 현재 health leg 는 credential-less 이나 `DomainTarget` enum 확장이 credential 셀렉터의 exhaustive switch 를 깨므로 매핑을 계약에 명시(후속 overview leg 대비).
   - **`/ecommerce` 드릴인 라우트**: console-web nav destination 으로 §(드릴인/섹션 라우팅 절)에 ecommerce 추가(scm/wms/erp/finance 와 동형, catalog `baseRoute` data-driven 유지).

### B. console-bff 구현 (Java)

2. **`DomainTarget.java`**: enum 끝에 `ECOMMERCE` 추가(IAM,WMS,SCM,FINANCE,ERP **다음**). ⚠️끝에 추가해야 `EnumMap` iteration order 가 기존 5 도메인에 대해 byte-stable(overview net-zero). javadoc 의 셀렉터 표에 `ECOMMERCE → IamOidcAccessToken` 추가.

3. **`CompositionEngine` per-route 카드오더 결합 해제** (핵심 설계 변경):
   - 현재 `fanOut` 은 정적 `CompositionEngine.CARD_ORDER`(5) 를 iterate → 두 use-case 가 동일 5 집합에 결합. ecommerce 를 health 에만 추가하려면 결합을 풀어야 한다.
   - `fanOut` 을 **전달된 `legBodies`(EnumMap → enum 선언 순서) 를 iterate** 하도록 변경(future 빌드 loop + resolve loop 둘 다). 정적 `CARD_ORDER` 에 대한 iteration 의존 제거.
   - 각 use-case 가 자신의 카드오더 상수를 소유:
     - `OperatorOverviewCompositionUseCase.CARD_ORDER` = `List.of(IAM,WMS,SCM,FINANCE,ERP)` (5, 불변).
     - `DomainHealthCompositionUseCase.CARD_ORDER` = `List.of(IAM,WMS,SCM,FINANCE,ERP,ECOMMERCE)` (6).
   - **behavior byte-equal 검증**: 기존 5-도메인 overview 의 leg 순서·타이머/카운터 태그·timeout fallback 모두 불변(EnumMap enum-order = 기존 CARD_ORDER 순서). 엔진 javadoc 의 "Fixed leg order: IAM,WMS,SCM,FINANCE,ERP" 불변식 문구를 "use-case 가 카드오더를 소유; 엔진은 전달된 legBodies 순서를 따른다" 로 정정.

4. **`EcommerceHealthReadPort`** (interface, `ErpHealthReadPort` 미러) + **`EcommerceHealthReadAdapter extends AbstractHealthReadAdapter implements EcommerceHealthReadPort`** (`@Qualifier("ecommerceRestClient")`, `ErpHealthReadAdapter` 미러).

5. **`RestClientConfig`**: `ecommerceRestClient` 빈 추가(`consolebff.outbound.ecommerce.base-url`, 2s per-leg timeout, 다른 5 빈 미러). javadoc 의 "5 per-domain RestClient beans" → 6.

6. **`application.yml` + `application-test.yml`**: `consolebff.outbound.ecommerce.base-url` 추가(prod=ecommerce 게이트 hostname 예 `http://ecommerce.local` 또는 기존 ecommerce 게이트 등록 hostname 재사용 — TEMPLATE Local Network Convention 확인; test=`http://localhost:...` placeholder, 다른 도메인 test 값 미러).

7. **`DomainHealthCompositionUseCase`**: `ecommercePort` 주입 + `ECOMMERCE` leg 추가(`timed(DomainTarget.ECOMMERCE, ecommercePort::read)`) + 자체 6-원소 `CARD_ORDER` 사용. javadoc "5 backend domains"→6, "all 5 legs"→6.

8. **`CredentialSelectionAdapter`** (또는 셀렉터 switch 보유처): `DomainTarget` 위 exhaustive switch 에 `ECOMMERCE → IamOidcAccessToken` case 추가(WMS/SCM/FINANCE/ERP 와 동일 가지). enum 확장으로 컴파일이 깨지지 않게 — health 는 credential-less 라도 셀렉터는 exhaustive 여야 함.

9. **console-bff 테스트 5→6 정합**:
   - `DomainHealthCompositionUseCaseTest` — leg 6개 단언, ecommerce leg ok/degraded/timeout 케이스(기존 erp 케이스 미러), all-down 시 6 leg 방출.
   - `DomainHealthSliceTest` — envelope 카드 6개.
   - `CompositionEngineTest` — fanOut 이 전달 legBodies 순서를 따름(5 와 6 둘 다); 기존 5-도메인 순서 byte-stable 회귀.
   - `OperatorOverviewCompositionUseCaseTest` / `OperatorOverviewSliceTest` — **5 불변** 회귀(ecommerce 누출 0).
   - `CredentialSelectionTest` — `selectFor(ECOMMERCE)` = IamOidcAccessToken.
   - `ConsoleBffSmokeIntegrationTest` — health 6 / overview 5.

### C. console-web 구현 (TS/Next.js)

10. **`src/app/(console)/ecommerce/page.tsx`** (신규 드릴인 라우트): `/scm/page.tsx` 패턴 미러 — server component, STRICTLY READ-ONLY, catalog eligibility pre-flight(`getCatalog()` → `productKey==='ecommerce'` available+tenants), degrade/not-eligible/forbidden/ratelimited 분기. **본 슬라이스 v1 콘텐츠**: ecommerce 도메인-health 섹션(또는 "E-Commerce 운영" 헤더 + health 카드 요약 read) — 운영 데이터 표면은 후속(a-후속-2). 최소한 **존재하는 라우트 + 카탈로그/health 연동 + degrade 안전**이면 동기 충족(타일 클릭 시 미존재 라우트 해소).
    - feature 모듈: 기존 `features/scm-ops` 류 과중하면 **얇은 ecommerce 섹션**(health envelope 의 ecommerce 카드 + "상세 운영 표면 준비중" 명시)로 시작. data-driven catalog `baseRoute` 는 불변.

11. **도메인-health 대시보드 렌더**(`/dashboards/health/page.tsx` + 그 feature): BFF envelope 의 6번째 카드(ecommerce)가 렌더되는지 확인 — 이미 data-driven(카드 배열 map)이면 0-change, 고정 5 가정이 있으면 6 으로.

12. **`ProductKeySchema`**: MONO-240 에서 이미 `ecommerce` 포함 — **확인만**(재추가 금지). 드릴인/health 타입에 ecommerce 가 필요한 별도 enum/리터럴 있으면 확장.

13. **console-web 테스트**: 드릴인 페이지 존재/degrade 스냅샷 또는 라우팅 단언; health 대시보드 6 카드 단언(해당 테스트 있으면).

### D. ecommerce 게이트 actuator 확인 (도메인 코드 불변)

14. **`projects/ecommerce-microservices-platform/apps/gateway-service`**: `/actuator/health` 가 **public(permitAll, 무인증)** 으로 노출되는지 `SecurityConfig`/actuator 설정 확인. 이미 노출이면 0-change(검증만 기록). 부재면 `/actuator/{health,info,prometheus}` permitAll 1줄(다른 도메인과 동형) — 이 경우 ecommerce gateway-service `:check` GREEN 필요.

## Out of scope (명시적 후속)

- **facet (a-후속-2) — operator-overview ecommerce 스냅샷 카드**: ecommerce 에 operator-plane tenant-scoped 도메인 메트릭 read 엔드포인트(예: `GET /api/admin/products?...&size=1` 페이지 total = 활성 상품 수, erp departments 미러) 신규 + console-bff overview leg(credential + tenant pass-through) + overview CARD_ORDER 5→6. 별 세션(net-new ecommerce 엔드포인트 = 추가 도메인 표면).
- **/ecommerce 드릴인 풍부한 운영 표면**(상품/주문/셀러 관리 CRUD) — 본 v1 은 라우트 존재 + health 가시성까지.
- **federation-hardening-e2e Playwright 렌더 단언** — nightly. 본 슬라이스는 console-bff slice/unit + console-web 테스트로 증명.
- 셀러 정산/수수료(facet b), 나머지 11 서비스 tenant_id 전파(facet c), ADR-022 이벤트 스레딩(facet d), M7 quota(facet e), 셀러 온보딩(facet f).

# Acceptance Criteria

- **AC-1 (계약 우선)**: console-integration-contract.md §2.4.9.2 가 ecommerce 를 6번째 health 카드로 포함하고, §2.4.9.1(overview)은 5 유지 + ecommerce overview 후속 명시; §2.4.5/6 credential 표에 `ECOMMERCE→IamOidcAccessToken`; 드릴인 라우트 절에 `/ecommerce`. 구현 전 갱신.
- **AC-2 (DomainTarget)**: `DomainTarget.ECOMMERCE` 가 enum 끝에 추가; 모든 `DomainTarget` exhaustive switch(credential 셀렉터 등) 컴파일 GREEN.
- **AC-3 (엔진 결합 해제 net-zero)**: `CompositionEngine.fanOut` 이 전달 `legBodies` 순서를 따름. 기존 5-도메인 overview 의 leg 순서·메트릭 태그·timeout fallback **byte-equal**(회귀 테스트 GREEN). health=6, overview=5 독립.
- **AC-4 (health 6 카드)**: `DomainHealthCompositionUseCase.compose()` 가 6 leg(ecommerce 포함, 고정 순서 IAM,WMS,SCM,FINANCE,ERP,ECOMMERCE)을 방출; ecommerce leg = `GET /actuator/health` credential-less; all-down 시에도 6 leg + 200.
- **AC-5 (overview 5 불변)**: `OperatorOverviewCompositionUseCase` 가 5 leg 불변; ecommerce 누출 0; 기존 단언 byte-equal GREEN.
- **AC-6 (드릴인 라우트)**: console-web `/ecommerce` 라우트 존재 — 카탈로그 타일(`baseRoute=/ecommerce`) 클릭 시 미존재 라우트 해소. eligibility/degrade 분기 안전(다른 도메인 섹션과 동형, 일시 장애 시 콘솔 나머지 정상).
- **AC-7 (health 대시보드 렌더)**: console-web 도메인-health 대시보드가 6번째 ecommerce 카드를 렌더(data-driven 0-change 또는 6 정합).
- **AC-8 (ecommerce actuator)**: ecommerce gateway-service `/actuator/health` 가 public 노출 확인(또는 permitAll 1줄 + `:check` GREEN). ecommerce 도메인/데이터 코드 0-change.
- **AC-9 (빌드 GREEN)**: 영향 서비스 `:check` GREEN — console-bff(`:projects:platform-console:apps:console-bff:check`) + console-web(`pnpm test`/lint) + (필요시) ecommerce gateway-service. HARDSTOP-03 N/A.
- **AC-10 (net-zero degrade / standalone)**: ecommerce 게이트 부재(health leg DOWN) 시 health 대시보드는 ecommerce 카드만 `degraded` 로, 나머지 5 카드 정상; overview 불변. console-web 드릴인은 degrade 분기로 안전.

# Related Specs / Code

- ADR: `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` §3.4 Step 4.
- 스펙: `projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md` §7(보류 — 콘솔 통합).
- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5/6/9.1/9.2.
- console-bff 코드(미러 선례):
  - `.../domain/credential/DomainTarget.java`
  - `.../application/composition/CompositionEngine.java` (CARD_ORDER 결합 해제)
  - `.../application/usecase/DomainHealthCompositionUseCase.java` / `OperatorOverviewCompositionUseCase.java`
  - `.../adapter/outbound/http/AbstractHealthReadAdapter.java` + `ErpHealthReadAdapter.java` (미러)
  - `.../adapter/outbound/http/CredentialSelectionAdapter.java` (switch 확장)
  - `.../infrastructure/config/RestClientConfig.java` + `src/main/resources/application.yml`
  - `.../application/port/outbound/ErpHealthReadPort.java` (미러)
- console-web 코드:
  - `src/app/(console)/scm/page.tsx` (드릴인 미러) → 신규 `src/app/(console)/ecommerce/page.tsx`
  - `src/app/(console)/dashboards/health/page.tsx` + health feature
  - `src/shared/api/registry-types.ts` (`ProductKeySchema` — MONO-240 에서 ecommerce 이미 포함, 확인만)
- ecommerce: `apps/gateway-service/.../config/SecurityConfig.java` (actuator permitAll 확인).
- 참조 선례: TASK-MONO-240(카탈로그 타일), TASK-PC-FE-008(scm 드릴인), TASK-PC-FE-013(health 대시보드), TASK-PC-BE-005(CompositionEngine 추출).

# Related Contracts

- `console-integration-contract.md` §2.4.9.2(health 6) / §2.4.9.1(overview 5) / §2.4.5/6(credential) — health 카드 멤버십 확장. 이벤트 계약 없음(동기 read 경로).

# Edge Cases / Failure Scenarios

- **공유 CARD_ORDER 결합 함정**: `CompositionEngine.CARD_ORDER`(5) 를 fanOut 이 iterate 하므로, ECOMMERCE 를 health 에만 추가하려면 fanOut 이 전달 legBodies 를 iterate 하도록 결합 해제 필수. 안 하면 (a) health 에 ecommerce body 를 넣어도 fanOut 이 안 부르거나 (b) overview 가 ecommerce body 없음으로 `IllegalStateException`("missing leg body for ECOMMERCE"). → fanOut 을 legBodies-iterate 로.
- **EnumMap 순서 함정**: ECOMMERCE 를 enum 중간에 넣으면 기존 5 도메인 iteration 순서가 밀려 overview 메트릭/스냅샷 순서 회귀. **반드시 enum 끝**에 추가.
- **exhaustive switch 컴파일 깨짐**: `DomainTarget` 위 sealed/exhaustive switch(credential 셀렉터, classifier 등)는 enum 확장 시 ECOMMERCE case 누락이면 컴파일 RED(Java 21 enhanced switch) 또는 default 누락 경고. 전 switch 검색해 ECOMMERCE 처리.
- **ecommerce actuator 미노출**: 게이트가 `/actuator/health` 를 permitAll 하지 않으면 health leg 가 401/redirect → 항상 `degraded`. AC-8 에서 노출 확인; 부재면 permitAll 1줄(ecommerce gateway `:check` 필요).
- **health leg base-url 미설정**: `consolebff.outbound.ecommerce.base-url` 누락 시 빈 생성 실패(`@Value` resolve) → 컨텍스트 부트 RED. application.yml + test yml 둘 다.
- **드릴인 라우트 degrade**: catalog/health 일시 장애 시 `/ecommerce` 가 콘솔 셸을 깨면 안 됨 — scm/wms 동형 degrade 분기(섹션만 degraded, 셸+타 섹션 정상).
- **overview 회귀(가장 중요)**: 본 변경의 zero-regression 핵심은 overview 5-leg byte-equality. `OperatorOverviewCompositionUseCaseTest`/`SliceTest` 가 leg 순서·태그·all-down 5 leg 를 그대로 단언하는지 확인 — RED 면 결합 해제가 순서를 바꾼 것.
- **ecommerce IT 트랩 무관**: 본 태스크는 ecommerce 도메인/데이터 코드 미변경(게이트 actuator 확인만) → `-PrunIntegration`·multiple-@SpringBootConfiguration 잠복-red 무관. console-bff + console-web + (선택) ecommerce gateway 만.

# Notes

- ⚠️ JDT.LS OOM 리스크 — facet 1건(health 카드 + 드릴인)만 종결하고 마무리. overview 스냅샷 leg(a-후속-2)·정산(b) 등은 별 세션.
- 구현은 격리 worktree `mlab-mono241`(브랜치 `task/mono-241-ecommerce-console-drill-in`, base=origin/main `db4eb6b6f`)에서만. 메인 체크아웃 + 동시 세션 worktree(mlab-pcfe071/072) 미접촉.
- 머지 후 close-chore: review→done 은 Status 만, closure narrative 는 커밋 메시지에(HARDSTOP-05). 3-dim 검증(state=MERGED / origin tip 일치 / pre-merge 0 failing required).
- 동시 세션 TASK 카운터 충돌 주의 — 머지 직전 root `tasks/` MONO 최대값 재확인(현재 240 done 기준 241).
