# TASK-SCM-BE-030 — demand-planning-service refactor tail (behavior-neutral)

**Status:** done

**Type:** TASK-SCM-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (small, but security-path adjacent — extraction must be byte-identical)

---

## Goal

`/refactor-code scm-platform` discovery pass. The May-2026 cross-project sweep
(TASK-SCM-BE-016/017) drove the gateway / procurement / inventory-visibility
cluster to TRUE-0; the only scm code added since is `demand-planning-service`
(TASK-SCM-BE-024/025/026, 2026-06-11~) plus the BE-025 procurement
DRAFT-PO-from-suggestion surface — none of it had been through a refactor sweep.

A read-only discovery pass over that new surface found the codebase already
clean (textbook Hexagonal layering, no layer violations, no dead code, no
pattern anti-patterns). This task applies the **only** high-confidence,
behavior-preserving wins found and deliberately skips the marginal /
behavior-risk candidates.

This is **refactoring only** (no behavior change, no contract/schema/ADR change),
per `platform/refactoring-policy.md`.

## Scope

**In scope** — `projects/scm-platform/apps/demand-planning-service/src/main/`:

1. **Reduce duplication (#2)** — `writeError(HttpServletResponse,int,String,String)`
   was byte-for-byte duplicated in `config/SecurityConfig` and
   `adapter/inbound/web/filter/TenantClaimEnforcer` (each with its own
   `private static final ObjectMapper JSON`). Extract to a single
   package-level `adapter/inbound/web/HttpErrorResponseWriter` (public static),
   used by both call sites. The pre-controller error envelope
   (`{code, message, timestamp}` + the `JsonProcessingException` string
   fallback) is byte-identical. Unused imports (`ObjectMapper`, `ObjectNode`,
   `JsonProcessingException`, `MediaType`, `Instant`) removed from both classes.
2. **Dead/stale comment (#1)** — `application/usecase/SuggestionQueryUseCase`
   javadoc claimed "Approve (BE-025) is not implemented … returns a 501 stub";
   approve has shipped (`ApproveSuggestionUseCase`, BE-025) and this class has no
   stub. Corrected the class javadoc to describe its actual read/dismiss role.
3. **Javadoc link (#6)** — `adapter/outbound/visibility/InventoryVisibilityRestAdapter`
   `{@link SweepReorderUseCase}` referenced an un-imported class (unresolved
   link); replaced with the fully-qualified `{@link}`.

**Out of scope (deliberately skipped — discovery found these marginal or
behavior-risky):**

- `isEntitled(Jwt,String)` duplication across `ServiceLevelOAuth2Config`
  (JWT validator) and `TenantClaimEnforcer` (filter) — **skipped**: collapsing it
  spans the config↔filter security path on a clean codebase; the risk of
  touching the entitlement-trust gate outweighs removing ~8 duplicated lines.
- The `policy-present → degraded fallback` block shared between
  `EvaluateReorderUseCase` and `SweepReorderUseCase` — **skipped**: the two
  fallback rules differ intentionally (alert path uses `alertThreshold`; batch
  uses `availableQty`), so merging risks altering a distinct rule.
- Inlining `availableQty > reorderPoint` to `ReorderPolicy.shouldReorder()` —
  **skipped**: only applies cleanly to the policy-present branch; partial win,
  behavior-adjacent in a transactional decision path.
- gateway-service / inventory-visibility-service / the rest of procurement-service
  — already TRUE-0 (TASK-SCM-BE-016/017), not re-scanned.

**Unchanged:** no test code (existing tests pass unmodified — proof of behavior
preservation), no contracts, no Flyway, no `application.yml`, no ADR,
`PROJECT.md` frontmatter byte-unchanged.

## Acceptance Criteria

- **AC-1** — `writeError` exists in exactly one place (`HttpErrorResponseWriter`);
  `SecurityConfig` and `TenantClaimEnforcer` call it; neither retains a private
  `writeError` or an unused `ObjectMapper JSON` field. The emitted error JSON
  (status, `{code,message,timestamp}`, fallback) is byte-identical to before.
- **AC-2** — No stale "501 stub" claim remains in `SuggestionQueryUseCase`; no
  unresolved `{@link}` remains in `InventoryVisibilityRestAdapter`.
- **AC-3 (behavior-neutral)** — `./gradlew :projects:scm-platform:apps:demand-planning-service:test`
  is green both before and after, with **zero test-code changes**. Production
  `compileJava` introduces no new warnings.
- **AC-4** — No contract / event / schema / ADR change; `PROJECT.md` frontmatter
  byte-unchanged. Change is confined to `demand-planning-service/src/main/`.

## Related Specs

- `projects/scm-platform/specs/services/demand-planning-service/architecture.md` (Hexagonal layering — the target shape)
- `platform/refactoring-policy.md`

## Related Contracts

- None — no contract touched (refactoring only).

## Edge Cases

- **Error-envelope byte-identity** — the extracted writer must keep the exact
  field order (`code`, `message`, `timestamp`) and the `JsonProcessingException`
  string fallback, since security-layer errors bypass the controller advice and
  any drift would change the pre-controller error shape.
- **config → adapter.inbound.web dependency** — `SecurityConfig` (composition
  root, `config/`) importing the web error writer is the wiring layer depending
  on the web layer it configures (acceptable in Hexagonal); not a layer
  violation.

## Failure Scenarios

- **F1 — extraction changes the error body** — if the extracted method altered
  field order / fallback, security-layer 401/403 responses would change shape.
  Guarded by AC-1 (byte-identical) + the slice/integration tests asserting the
  error envelope.
- **F2 — over-reach into skipped items** — touching `isEntitled` or the reorder
  fallback would risk behavior change in security / decision paths. Guarded by
  the explicit Out-of-scope list + AC-3 (tests green, no test changes).
