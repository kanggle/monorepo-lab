package com.wms.outbound.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Request body for {@code PATCH /api/v1/outbound/packing-units/{id}}.
 *
 * <p>This endpoint's sole operation is <b>Seal</b> (OPEN → SEALED). Packing
 * units are fully populated at create time (§3.1 {@code lines[]}); the
 * "add-lines" operation the contract once advertised was never implemented
 * (TASK-BE-550).
 *
 * <p>{@code seal} and {@code addLines} are bound <em>only</em> to <b>reject</b>
 * the historical footgun: a client following the old two-operation contract
 * with {@code {"seal": false, "addLines": [...]}} would otherwise have those
 * fields silently dropped by Jackson (they were unbound) and get an
 * <em>irreversible unconditional seal</em> — the exact opposite of
 * {@code seal:false}. Binding + validating them turns that into a 400
 * {@code VALIDATION_ERROR} instead. A legacy {@code "seal": true} is tolerated
 * for back-compat (the console-web client still sends it); a bare
 * {@code {"version": N}} is the canonical form.
 */
public record SealPackingUnitRequest(
        Boolean seal,
        List<Object> addLines,
        @Min(0) long version
) {

    /**
     * Rejects the removed operations: {@code seal:false} (this endpoint always
     * seals — you cannot request "don't seal") and any non-empty
     * {@code addLines} (never implemented). {@code seal} absent/true and
     * {@code addLines} absent/empty are the only accepted shapes.
     */
    @JsonIgnore
    @AssertTrue(message = "This endpoint only seals a packing unit; 'seal:false' and 'addLines' are not "
            + "supported (packing units are fully populated at create time — §3.1)")
    public boolean isSealOnlyRequest() {
        return (seal == null || seal) && (addLines == null || addLines.isEmpty());
    }
}
