# Task ID

TASK-MONO-814

# Title

`@demo/backend-resolver` sends one `/status` per caller when its cache is cold — a console overview render fans out past the account's 10 concurrent Lambda executions, the throttled lookups fall back to `*.local`, and those legs answer `DOWNSTREAM_ERROR`

# Status

ready

# Owner

frontend / platform

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (concurrency semantics in a shared module with three consumer apps + a live check)
> Drafted 2026-10-24 UTC from `projects/platform-console/tasks/in-progress/TASK-PC-FE-369-*.md` § AC-0 mechanism (e), confirmed by the CloudWatch measurement below. Owner decision 2026-10-24 (selection window): «주소 조회를 한 번으로 묶기» — fix in code, no AWS change.

# Dependency Markers

- 출처: `TASK-PC-FE-369` AC-0 기전 (e) · 측정 (2026-10-24 UTC). That ticket stays open until this ticket's live AC passes (its AC-2).
- 후속: `TASK-PC-FE-364` AC-3 is re-measured in the same window (`TASK-PC-FE-369` § Dependency Markers).
- 관련: `TASK-PC-FE-352` § Vercel 확인 결과 ④ named the same path («데모 on 인데 `/status` 가 실패해 설정값을 그대로 쓰는 경우») and read it as transient.
- 선행: 없음.

# Goal

Fix issue found in TASK-PC-FE-364 / TASK-PC-FE-369.

On the 33rd window (2026-10-23 UTC) the logged-in operator overview showed «WMS 출고» · «SCM 발주» as `DOWNSTREAM_ERROR` and «WMS 점검 불가», while `/wms` (same URL, same headers, same token) worked and the services logged nothing. `TASK-PC-FE-369` AC-0 excluded four suspects by code and left one mechanism, (e): every leg calls `resolveBackendUrl` → `resolveDemoBackend`, and when the resolver cache is empty each call sends its **own** `/status`. One render issues 13+ of them at once.

## 측정 (2026-10-24 UTC, read-only CloudWatch · 33rd window 2026-10-23 13:25–13:45 UTC)

| Metric (Sum or Max per minute) | 13:32 | 13:33 | 13:35 | 13:37 | other minutes |
|---|---|---|---|---|---|
| Lambda `portfolio-demo-control` `Throttles` (Sum) | 4 | 4 | 12 | 4 | 0 |
| Lambda `ConcurrentExecutions` (Max) | 10 | 10 | 10 | 10 | 2 |
| API Gateway `$default` `5xx` (Sum) | 4 | 4 | 12 | 4 | 0 |
| API Gateway `$default` `4xx` (Sum) | 0 | 0 | 0 | 0 | 0 |

- `aws lambda get-account-settings`: account `ConcurrentExecutions` limit = **10**, unreserved = 10. The function has no reserved concurrency. The APIGW stage throttle is rate 5 / burst 10 (`infra/demo/aws/terraform/main.tf:516-517`). The stage throttle did not fire (4xx = 0). The account concurrency limit did.
- Those minutes match the logged-in overview loads (13:33, ~13:36) and the probes in between.
- ⇒ Mechanism (e) is confirmed at one-minute granularity. Simultaneous `/status` lookups exceed 10 concurrent executions, so Lambda throttles them and API Gateway answers 5xx. `fetchStatus` then returns `null`, and `resolveSnapshot` caches `value: null`, which overwrites any good snapshot. The leg falls back to the configured `http://*.local`, the fetch throws on Vercel, and the card shows `DOWNSTREAM_ERROR`. The request never reaches the demo host, which is why the service logs were empty.
- 🔵 Still inference: **which** legs received the throttled lookups. The metric counts requests, not callers.

## Owner decision (2026-10-24)

«주소 조회를 한 번으로 묶기». The shared resolver coalesces concurrent lookups into **one** in-flight `/status`, and a failed lookup does not overwrite a good cached snapshot. No AWS change (no reserved concurrency, no limit increase, no stage throttle change).

# Scope

## In Scope

- `infra/demo/backend-resolver/src/index.ts`:
  - **single-flight**: callers that find the cache empty or expired while a lookup is in flight await that same promise. One instance sends at most one `/status` at a time.
  - **keep-last-good**: a *failed* lookup (non-2xx, timeout, network error, non-JSON or non-object body) keeps serving the last good snapshot if it is younger than a documented bound. It returns `null` only when there has never been a good snapshot, or the last one is too old.
  - A *definitive* answer is not a failure. `state ≠ running` or a missing/implausible `ip` still returns `null` immediately and clears the last good snapshot. Do not attach silently to an old IP: header rule 2 of that file.
  - Everything else stays the same: 15 s TTL, failed lookups cached for the TTL (the web-store suite pins this), 2 s timeout, `DEMO_API_BASE` absent → no lookup, `starting` semantics, the fallback env chain.
- `infra/demo/backend-resolver/README.md`: document the two properties and the bound.
- Tests in a consumer suite (the package has no runner by design — README § «이 패키지는 자기 테스트를 갖지 않는다»). The fix's tests go in `projects/platform-console/apps/console-web/tests/unit/demo-backend.test.ts`, the consumer that showed the defect.

## Out of Scope

- AWS changes (reserved concurrency, account limit, stage throttle). Excluded by the owner decision.
- Cross-instance coalescing. Single-flight is per resolver instance, so per server process. Separate Vercel function instances each still do their own lookup. The live AC measures whether that is enough.
- The `logLeg` cause field (`TASK-PC-FE-369` § 다음 창 item 5). It is diagnostic only and stays in that ticket.

# Acceptance Criteria

- [ ] **AC-1** — single-flight: N concurrent calls on a cold cache → exactly **1** `fetch`, and all N get the same result. This includes the case where the lookup fails.
- [ ] **AC-2** — keep-last-good: a good snapshot, then TTL expiry, then a failed lookup → the good snapshot is returned. A following call within the TTL still returns it, so the cache is not clobbered. A first-ever failure → `null` and the configured `.local` URL. A good snapshot older than the bound, then a failure → `null`. A definitive `state=stopped` after a good snapshot → `null`, not the old IP.
- [ ] **AC-3** — the AC-1 and AC-2 tests are committed **before** the fix and fail on today's code on behaviour, not on compilation. Record the red run, then the green run.
- [ ] **AC-4** — console-web `tsc --noEmit` · `next lint` · vitest (`--maxWorkers=2`) · `scripts/check-demo-resolver-copies.sh` (+ `--self-test`) · Client graph guards · the three queue guards (after staging) all rc=0.
- [ ] **AC-5** — shipping: the change reaches every consumer's Vercel build. All four consumers use `link:`, and each `vercel-ignore.sh` includes `:/infra/demo/backend-resolver`. Record the Vercel Production deployment for console-web after merge.
- [ ] **AC-6** — live, next demo window. Load the logged-in (`demo-corp`) overview **cold**, as the first render in more than 15 s. All six data cards draw a value or a legitimate «권한 없음», and the domain status summary has no «점검 불가» for a running bundle. CloudWatch Lambda `Throttles` = **0** in that minute. Closes `TASK-PC-FE-369` AC-2.

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` D2 (runtime resolution) · `docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md` § D6 = B2 (one shared implementation)
- `infra/demo/backend-resolver/README.md`

# Related Contracts

- `/status` of the demo control plane (`infra/demo/aws/terraform/lambda/handler.py`). The response shape is unchanged.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 · § 2.4.9.2. The legs are unchanged; only their address resolution changes.

# Edge Cases

- The demo is stopped while a good snapshot is cached. `/status` answers `stopped`, which is definitive, so the result is `null` at once. Keep-last-good applies only when `/status` itself fails.
- The IP changes on boot. A new boot takes about 11 minutes, so a stale-on-error bound well under that cannot point at a new instance's address. It could still point at a released IP while `/status` is failing; the bound limits that window.
- An in-flight lookup that rejects must not leave the promise stuck. The next call after the TTL must fetch again.
- `__resetDemoBackendCache` must also clear the in-flight promise and the last good snapshot, or test cases leak into each other.

# Failure Scenarios

1. Single-flight only → the first render after a throttle still caches `null` for 15 s, and every leg falls back. AC-2 prevents this.
2. Keep-last-good treats `stopped` as a failure → the console keeps calling a released IP. AC-2's `stopped` case prevents this.
3. Tests that pass on today's code → AC-3 requires the red run.
4. Only the package changes, and a consumer's Vercel project does not rebuild → the fix exists in the repo but not live. AC-5 checks each `vercel-ignore.sh`.
