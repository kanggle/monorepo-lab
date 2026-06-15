# TASK-FIN-BE-035 — finance 스펙 구조/가독성 refactor (meaning-preserving)

- **Status**: done
- **Project**: finance-platform
- **Service**: ledger-service (+ account-service / contracts)
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: refactor-spec (구조/가독성만 — 요구사항·계약·결정 변경 0)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (권위 스펙 다중 파일 — 정확도 우선)

## Goal

`/refactor-spec finance-platform` Scan 결과 도출된 **의미보존(meaning-preserving)** 구조/일관성/가독성 개선을
적용한다. 방금 머지된 TASK-FIN-BE-034(드리프트 정합) 결과를 유지하면서, 요구사항·API/이벤트 계약·아키텍처
결정을 일절 바꾸지 않고 **표현/구조/네비게이션만** 다듬는다.

## Scope (적용 — In)

1. **F6 (clarity/dedup)** `ledger-service/architecture.md` Provenance 블록 — 25증분을 한 문장씩 나열하던 58줄
   run-on을 17줄로 축약하고, 증분 상세는 정본인 **§ Increment Scope**(+ 각 § 섹션)로 포인터. 정보 손실 0
   (모든 증분 narrative 는 § Increment Scope 에 이미 존재).
2. **F13 (consistency/structure)** `ledger-service/architecture.md` `### Allowed / Forbidden dependencies`를
   account-service 와 동일하게 `### Allowed dependencies` + `### Forbidden dependencies` 2섹션으로 분리.
   본문 텍스트 verbatim 보존(아래 Findings DF-1 참조).
3. **F11 (clarity)** `contracts/events/finance-account-events.md` `finance.ledger.*` 노트 — "no ledger-service"
   stale 문구를 "ledger-service(별도 서비스)가 발행; account-service 는 미발행, 본 계약은 account-service
   이벤트만 다룸"으로 정정. account-service 의 이벤트 계약 자체는 불변.
4. **F7 (consistency/naming)** `contracts/http/ledger-api.md` `## § FX rates (read) — 14. GET ...` 헤딩을
   §12/§13 과 동일한 number-first(`## 14. FX rates (read) — GET ...`)로 정렬. "FX rates (read)" 부분문자열
   유지 → 타 파일의 `§ FX rates (read)` 산문 교차참조 그대로 유효.

## Acceptance Criteria

- **AC-1 — meaning-preserving.** 요구사항/AC/API 엔드포인트/요청·응답 스키마/이벤트 페이로드/상태코드/아키텍처
  결정 변경 0. 구조·표현만 변경.
- **AC-2 — dead-ref 0.** 편집 후 finance 스펙 상대 링크·`§` 산문 참조가 전부 실존 대상 가리킴(특히 "FX rates
  (read)" 교차참조 유효).
- **AC-3 — net-zero on code.** 코드/마이그레이션/테스트 무영향(doc-only).

## Related Specs / Contracts

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (F6, F13)
- `projects/finance-platform/specs/services/account-service/architecture.md` (F13 정렬 기준)
- `projects/finance-platform/specs/contracts/http/ledger-api.md` (F7)
- `projects/finance-platform/specs/contracts/events/finance-account-events.md` (F11)

## Findings (보고-전용 — 본 태스크에서 미수정)

리팩토링 원칙(의미 변경은 report-only)에 따라 적용하지 않고 기록만 한다:

- **DF-1 (drift, 별도 fix 권장)** `ledger-service/architecture.md` `### Forbidden dependencies` 의
  "an outbox/publish path (terminal consumer)" 는 **3rd 증분(FIN-BE-009) 이후 stale** — ledger 는 이제
  publishing consumer(ledger_outbox 보유). FIN-BE-034 오버뷰 정합과 모순. 1줄 드리프트 fix 후보(refactor
  범위 밖 — 의미 변경).
- **F1 (navigation, deferred)** `ledger-service/architecture.md`(2284줄) clickable ToC 권장하나, headings 에
  `—`/`→`/`§`/`+`/`/` 포함 → github-slugger 미설치 + 라이브러리↔GitHub anchor 차이로 **dead-anchor 위험**.
  CI에 slugger 검증 도입 또는 heading anchor-정규화 선행 시 추가 권장.
- **F2** Increment Scope(380줄) 를 Responsibilities 뒤로 이동 — churn 과다·배치 의도성(로드맵 우선 읽기) 논쟁 →
  skip.
- **F5** 도메인 §섹션 heading 포맷 통일/`Multi-tenancy / Security / Audit` 분리 — 저가치·관례 혼재 → skip.
- **F8** ledger-api `## 1~4` vs `### 5~11` vs `## 12~14` 깊이 혼재 — ToC(F1) 도입 시 완화, 재번호는 과다 → skip.
- **F9** event 페이로드 중복(architecture.md § Event publication ↔ finance-ledger-events.md) dedup — 섬세,
  스캔 line 참조 부정확 → 재검토 후 별도 판단.
- **F10** ledger-api Error codes 표에 `architecture.md § Failure Modes` 교차참조 1줄 — 저가치 → skip.
- **F12** iam-integration.md 헤딩 한/영 혼재 — 형제 파일(scm/wms) 동일 패턴 → 단독 수정 부적절.
- **F14** reconciliation-api.md ToC — 191줄, 불요.

## Edge Cases

- "FX rates (read)" 헤딩 number-first 재정렬 시 부분문자열 보존 → 산문 교차참조 무손상(앵커 아님).
- Provenance 축약은 § Increment Scope 가 정본이라는 전제 — 두 곳 동기화 의무 해소(단일 출처).

## Failure Scenarios

- 교차참조 깨짐 → AC-2 dead-ref 점검에서 검출.
- 의미 변경 혼입 → AC-1 위반; 본 태스크는 DF-1 같은 의미성 항목을 명시적으로 report-only 처리해 방지.
