# Task ID

TASK-MONO-510

# Title

WMS · SCM · ERP · Finance 도메인 데이터 시드 — 콘솔 도메인 운영 23개 화면을 채운다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- test

---

# 배경 — `TASK-MONO-506` 의 S4 · S5 슬라이스

MONO-506 이 시드 프레임워크와 ecommerce 시드를 만들고 라이브 검증까지 마쳤다. 나머지 네
도메인은 **의도적으로 분리했다**:

1. **호스트 자원.** 도커 가용 11.7 GiB 중 iam + console + ecommerce(축소본) 35 컨테이너가
   9.2 GiB 를 쓴다(실측 2026-08-05). S5 는 iam + scm + erp + finance + console 을 동시에
   요구하므로 별도 슬라이스가 필요하고, 그 자체로 메모리 실측 대상이다.
2. **검증하지 않은 시드는 거짓 약속이다.** 띄워 보지 않고 스크립트만 커밋하면 MONO-506 이
   세운 원칙("넣는 행위가 곧 검증")을 스스로 어긴다.

## 이미 확보된 것 — **운영자 토큰 하나가 네 도메인을 전부 연다**

MONO-506 이 `infra/demo/seed/lib.sh` 에 `operator_token` 을 만들었다. 콘솔 로그인
(공개 클라이언트 `platform-console-web`, PKCE) → RFC 8693 assume `demo-corp` 를 거치면:

```
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_READ/WRITE, INBOUND_READ/WRITE,
         INVENTORY_READ/WRITE, MASTER_READ]
entitled_domains = [ecommerce, erp, finance, scm, wms]
```

즉 이 티켓은 **인증 문제를 새로 풀지 않는다.** 각 도메인의 쓰기 엔드포인트와 필수 필드만
확인하면 된다.

보조 경로로 도메인별 워크로드 클라이언트(`client_credentials`)도 있다 —
`wms-internal-services-client` · `scm-platform-internal-services-client` ·
`erp-platform-internal-services-client` · `finance-platform-internal-services-client`
(secret 은 각 마이그레이션 헤더에 명시된 dev 리터럴; wms 는 `"secret"` 로 실측 확인).
운영자 역할로 막히는 항목이 있을 때만 쓴다.

---

# Goal

`bash infra/demo/demo-up.sh iam wms console` (S4) 과
`bash infra/demo/demo-up.sh iam scm erp finance console` (S5) 로 뜬 스택에서, 콘솔의
해당 도메인 섹션 화면이 **비어 있지 않다.**

---

# Scope

## In Scope

- `infra/demo/seed/seed-wms.sh` · `seed-scm.sh` · `seed-erp.sh` · `seed-finance.sh`
- 콘솔 도메인 운영 화면 23개 라이브 검증
- 슬라이스별 메모리 실측
- 워크스루 가이드의 한계 표 갱신

## Out of Scope

- 제품 코드 변경 — 발굴 결함은 별도 티켓
- 팬 도메인 — `TASK-MONO-509`
- 콘솔이 스토어프런트 테넌트 데이터를 못 보는 문제 — `TASK-BE-576`
  (**이 티켓과 상호작용한다** — 아래 AC-0 참조)

---

# 대상 화면 (2026-08-05 `console-nav-config.ts` 실측 — 착수 시 재측정)

| 도메인 | 리프 | 경로 |
|---|---|---|
| WMS | 7 | `/wms` `/wms/guide` `/wms/inbound` `/wms/inventory` `/wms/outbound` `/wms/master` `/wms/operations` |
| SCM | 6 | `/scm` `/scm/guide` `/scm/procurement` `/scm/inventory` `/scm/replenishment` `/scm/config` |
| Finance | 4 | `/finance` `/finance/guide` `/finance/accounts` `/ledger` |
| ERP | 6 | `/erp` `/erp/guide` `/erp/masters` `/erp/orgview` `/erp/approval` `/erp/delegation` |

MONO-506 의 콘솔 전수 스윕(iam + console + ecommerce 슬라이스)에서 이 중 **15개가
데이터 부재로 degrade** 했다(가이드 페이지와 일부 개요는 통과). 그 15개가 이 티켓의 최소 표적이다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정 + 테넌트 확인)** — 화면 모집단을 `console-nav-config.ts` 에서 다시 센다.
      **그리고 각 도메인이 `TASK-BE-576` 과 같은 테넌트 분리 증상을 갖는지 먼저 확인한다** —
      운영자 토큰으로 목록 API 를 호출해 **원소 수가 DB 실측과 일치하는지** 본다.
      200 은 판정 근거가 아니다(그 결함은 200 이었다). 불일치하면 이 티켓은 BE-576 에
      **블록된다** — 시드를 아무리 넣어도 화면은 비어 있다
- [ ] **AC-1 (API 우선)** — `operator_token` 으로 각 도메인의 쓰기 API 를 쓴다. 직접-DB 는
      `dbexec --why` 로만, 사유는 재검증 가능하게(막힌 엔드포인트와 실제 응답을 적는다)
- [ ] **AC-2 (라이브 검증)** — 23개 화면을 **브라우저로** 연다
- [ ] **AC-3 (프로젝션 의존 화면)** — ERP 통합 조회 · WMS 재고는 read-model 프로젝션에
      의존한다. **프로듀서만 시드하면 화면은 여전히 빈다** — 프로젝션 서비스 기동을 슬라이스
      정의에 포함하고, 프로젝션이 따라잡을 때까지 기다린 뒤 판정한다
- [ ] **AC-4 (대표 쓰기)** — 도메인마다 최소 1건의 쓰기 동작이 브라우저에서 성공한다
      (예: WMS 출고 지시, ERP 결재 승인, Finance 거래 등록)
- [ ] **AC-5 (멱등)** — 각 시드를 연속 2회 실행해도 수렴한다
- [ ] **AC-6 (메모리 실측)** — S4 · S5 각각의 컨테이너 수 + 메모리를 기록한다.
      **S5 가 로컬에서 아예 불가능하면 그 사실을 수치와 함께 적는다**(MONO-399 AC-2 의 입력이며,
      "못 했다" 도 유효한 측정이다)
- [ ] **AC-7 (가드)** — `verify-demo-wrapper.sh` 가드 (y) 통과
- [ ] **AC-8 (발굴 결함 분리)** — 별도 티켓. 0건이면 "0건" 이라고 적는다

---

# Related Specs

- `infra/demo/seed/README.md` — 시드 규약 + 도메인 추가 절차
- `infra/demo/seed/seed-ecommerce.sh` — 선례
- 각 프로젝트 `specs/` — **enum·필수 컬럼의 권위는 DB 제약이지 spec 이 아니다**

# Edge Cases

- WMS 출고는 외부 TMS 스텁에 의존한다(`WMS_TMS_BASE_URL` = 도달 불가 스텁) — 서킷브레이커로
  degrade 하는 것이 정상 경로인지 확인
- ERP 결재는 위임 규칙과 얽힌다 — 결재함이 비지 않으려면 결재선이 필요하다
- Finance 시산표는 원장 마감 상태에 의존한다
- SCM 보충 추천은 `sku-supplier-map` 이 없으면 `SKU_SUPPLIER_UNMAPPED` 다 —
  `/scm/config` 가 그 fix-path 다(시드가 그것을 써야 한다)

# Failure Scenarios

- **BE-576 을 확인하지 않고 착수** → 시드는 201 인데 화면은 계속 빈다. AC-0 이 막는다
- **프로젝션을 기다리지 않고 판정** → 방금 넣은 것이 아직 안 보이는데 "빈 화면" 으로 오진
- **S5 를 로컬에서 강행** → 호스트 OOM. 소배치 기동(2~3 서비스씩)과 실측 기록이 정답

# Definition of Done

- [ ] 도메인 시드 4개 커밋 + 각 2회 실행
- [ ] 23개 화면 브라우저 증거
- [ ] S4 · S5 메모리 실측 기록
- [ ] 가이드 한계 표 갱신
- [ ] Ready for review
