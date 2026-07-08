# TASK-PC-FE-231 — console-web architecture.md erp 섹션 정합 (read-only 드리프트 해소)

**Status:** done
**Area:** platform-console / specs (console-web architecture.md) · **Type:** refactor-spec (문서 정합, 동작 변경 없음)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (문서 정합, 코드 무변경) (분석=Opus 4.8 / 구현 권장=Sonnet)
**Origin:** 2026-07-08 ERP 알림 딥링크 작업(PC-FE-230 / ERP-BE-028) 중 발견 — architecture.md의 erp 섹션이 실제 콘솔 표면보다 심하게 stale.
**Impl:** architecture.md 5블록 정합 — route-tree(104: 4 sub-route+write) · erp-ops header(190: read+write) · api(191: 3 서비스·masters CUD·approval 전이·delegation·`getDomainFacingToken`) · hooks(192: 변이 훅) · components(194: approval/delegation/orgview 컴포넌트+`ErpMastersScreen`) · prose bullet(341: write 표면·credential) · producer(387). E2/E3·resilience·429-absent·confidential 등 유효 normative 보존. diff=architecture.md 단일 파일(코드·계약 무변경). 잔여 stale은 전부 타 도메인(범위 밖).

---

## Goal

`specs/services/console-web/architecture.md`의 erp 섹션이 erp 콘솔을 **read-only masterdata 전용**으로 기술하나, 실제 `features/erp-ops`는 PC-FE-046/048/049/051/053/054/055/076을 거쳐 **read + write, 4개 백엔드 서비스**로 성장했다:

- **masterdata-service**: 5 master read(10 GET) + **전 5 master CUD**(create/update/retire, department move-parent).
- **approval-service**: 결재함 — requests/inbox read + create·submit·approve·reject·withdraw 전이(reject/withdraw reason 필수, **erp 유일 `X-Operator-Reason` 전송**) + delegation grant/revoke.
- **read-model-service**: 통합 조회 employee org-view + delegation facts (strictly read-only).

architecture.md는 이를 반영하지 않아 "**read-only**", "**mutation 0**", "approval-service/read-model-service **명시 범위 밖**", credential `getAccessToken()`(실제 `getDomainFacingToken()`) 등 **다수 clause가 사실과 반대**다. 이를 실제 상태(및 이미 최신인 `console-integration-contract.md §2.4.8`)에 맞게 정합한다.

**코드·계약·동작 무변경** — 순수 spec 문서 정합. `console-integration-contract.md §2.4.8`은 이미 최신(masters-write·approval·delegation·orgview 반영)이므로 **architecture.md 한 파일만** 수정한다.

## 배경 사실 (검증됨 2026-07-08)

- `features/erp-ops/index.ts` 배럴 + `app/(console)/erp/{page,approval,orgview,delegation}` 4 라우트가 위 표면 전부 노출(3중 검증: 배럴 export + api 클라이언트 POST/PATCH + proxy route).
- credential 전 클라이언트 `getDomainFacingToken()`(erp-client.ts:207 등), `getAccessToken()` 아님. architecture.md line 278은 이미 `getDomainFacingToken` 기술(자기모순).
- `console-integration-contract.md §2.4.8`(line 1683~)이 masters-write(PC-FE-046/048)·approval(051/053)·delegation(054)·orgview(049) 전부 이미 문서화 — 정합의 SoT.

## Scope (architecture.md 편집 대상, 5블록)

1. **route-tree (line 104)** — `erp/` 항목 "read-only masterdata …" → sub-routes(`/erp` masters + `/erp/approval` 결재함 + `/erp/orgview` 통합조회 + `/erp/delegation` 위임) + read+write 반영(finance line 103 스타일 미러).
2. **erp-ops feature-tree header (line 190)** — "**read-only**" 제거 → read + write.
3. **api/ (line 191)** — "mutation 0", `getAccessToken()` → 실제(4 서비스 소비·masters CUD·approval 전이·delegation·`getDomainFacingToken()`). read-model read-only 명시.
4. **hooks/ (line 192)** — "변이 훅 없음" → approval·delegation·masters 변이 훅 존재.
5. **components/ (line 194)** — 누락된 approval/delegation/orgview 컴포넌트 추가, `ErpOpsScreen`→`ErpMastersScreen`(라우트 분할).
6. **prose bullet (line 341)** — "기존 read-only 표면 … read-only 소비만", credential, "**read-only — mutation scaffolding 0**" clause → 실제 write 표면 기술. E2/E3·resilience·429-absent·confidential 등 **여전히 유효한 normative 내용은 보존**.
7. **producer-contract inventory (line 387)** — "read-only 소비만", "write surface + approval/read-model-service 명시 범위 밖" → 소비 반영.

## Out of Scope (의도적 유지)
- `console-integration-contract.md §2.4.8` — 이미 최신, 무변경.
- erp 코드·docstring(`erp-client.ts`·`index.ts` 배럴이 "read + DEPARTMENT WRITE PILOT"·`getAccessToken()`로 stale) — 코드 변경은 별건(이 task는 spec-only). 관찰만 기록.
- E2/E3 effective-dating, resilience(401/403/404/503), 429-absent, confidential discipline 등 **정확한 normative 서술 보존**(재작성 금지).
- 다른 도메인(wms/scm/finance) 섹션의 `getAccessToken()` 표기 — 별건(erp만 정합; wms/scm/finance는 이 task 범위 밖).

## Acceptance Criteria
- **AC-1** architecture.md에 "erp … read-only" / "mutation 0" / "approval-service|read-model-service 명시 범위 밖" 서술이 남지 않음(erp 섹션 5블록 전부 정합).
- **AC-2** erp 4 라우트(masters/approval/orgview/delegation)와 각 표면의 read/write 성격이 정확히 기술됨(masters CUD·approval 전이+X-Operator-Reason·delegation grant/revoke·read-model read-only).
- **AC-3** erp credential이 `getDomainFacingToken()`로 정정(line 278과 일관).
- **AC-4** 보존 대상(E2/E3·resilience·429-absent·confidential·`console-integration-contract §2.4.8` canonical 참조)이 그대로 유지됨.
- **AC-5** 코드·계약 무변경(diff는 architecture.md 단일 파일). markdown 링크 무파손.

## Edge Cases / Failure Scenarios
- 거대 단일 라인(341/387) 편집 시 유효 normative 내용 실수 삭제 → AC-4 보존 단언으로 가드(surgical substring 편집, 전체 라인 재작성 지양).
- §2.4.8을 중복 재기술 → SoT 이원화. cross-reference로 위임(canonical=계약)하고 architecture.md는 요약만.

## Related Specs
- `projects/platform-console/specs/services/console-web/architecture.md` (편집 대상)
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.8 (정합 SoT — 무변경)

## Related
- 발견 경위: TASK-PC-FE-230(딥링크 오라우팅, done)·TASK-ERP-BE-028(deepLink 파생, done) 중 architecture.md erp 섹션 stale 확인. 원 성장 태스크: PC-FE-046/048(masters write)·049(orgview)·051/053(approval)·054/055(delegation)·076(라우트 분할).
