package com.example.admin.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

/**
 * TASK-BE-306 — request body for
 * {@code PATCH /api/admin/operators/me/profile}.
 *
 * <p>Schema:
 * <pre>{@code
 * { "operatorContext": { "defaultAccountId": "acc-uuid-7" } }   // set
 * { "operatorContext": { "defaultAccountId": null } }            // clear
 * }</pre>
 *
 * <p>The nested {@code operatorContext} carrier is the write-side mirror of
 * the read-side {@code GET /api/admin/console/registry} response shape
 * ({@code operatorContext.defaultAccountId}) — request and response carry the
 * same JSON path so a UI flow can {@code read → mutate → re-read} without
 * shape transforms (admin-api.md § PATCH /api/admin/operators/me/profile +
 * TASK-BE-306 § Decision authority).
 *
 * <p>Validation rules enforced at deserialization time + controller path:
 * <ul>
 *   <li>The top-level {@code operatorContext} key must be present — empty
 *       body {@code {}} → null carrier → controller throws
 *       {@code InvalidRequestException} → 400 {@code INVALID_REQUEST}.</li>
 *   <li>Inside {@link OperatorContextDto}, the {@code defaultAccountId} key
 *       must be present in the JSON (even if its value is {@code null} —
 *       that is the explicit clear-intent encoding). Empty carrier
 *       {@code {"operatorContext":{}}} → {@link OperatorContextDto#isDefaultAccountIdPresent()}
 *       returns {@code false} → controller throws.</li>
 *   <li>Unknown nested keys (e.g. {@code {"wmsDefaultWarehouseId":"x"}})
 *       → {@link OperatorContextDto#hasUnknownKey()} returns {@code true} →
 *       controller throws (no future-compat trap; v1 schema is closed).</li>
 *   <li>Non-string JSON value at {@code defaultAccountId} (e.g. number,
 *       boolean, nested object) → {@link OperatorContextDto#isValueTypeInvalid()}
 *       returns {@code true} → controller throws.</li>
 * </ul>
 *
 * <p>Whitespace / length / control-character validation on the actual UUID
 * string lives on the controller / use case path (not here) so the resulting
 * 400 carries the canonical {@code INVALID_REQUEST} code.
 */
public record UpdateOperatorProfileRequest(
        OperatorContextDto operatorContext
) {

    /**
     * Nested operatorContext carrier. The {@code defaultAccountId} key
     * presence is detected via a {@link JsonCreator} that consumes the whole
     * nested-object {@link Map} so we can distinguish "key absent" from
     * "key present, value null".
     */
    public static final class OperatorContextDto {
        /** v1 accepts ONLY this nested key. */
        public static final String DEFAULT_ACCOUNT_ID_KEY = "defaultAccountId";

        private final String defaultAccountId;
        /** {@code true} iff the JSON object contained the {@code defaultAccountId} key
         *  (regardless of its value being null or non-null). */
        private final boolean defaultAccountIdPresent;
        /** {@code true} iff the JSON object contained at least one unknown key
         *  (i.e. anything other than {@link #DEFAULT_ACCOUNT_ID_KEY}). */
        private final boolean hasUnknownKey;
        /** {@code true} iff the JSON object contained {@code defaultAccountId}
         *  with a non-string non-null value (e.g. number, boolean, nested object). */
        private final boolean valueTypeInvalid;

        @JsonCreator
        public static OperatorContextDto fromMap(Map<String, Object> properties) {
            if (properties == null) {
                return new OperatorContextDto(null, false, false, false);
            }
            boolean present = properties.containsKey(DEFAULT_ACCOUNT_ID_KEY);
            boolean hasUnknown = false;
            for (String key : properties.keySet()) {
                if (!DEFAULT_ACCOUNT_ID_KEY.equals(key)) {
                    hasUnknown = true;
                    break;
                }
            }
            Object raw = present ? properties.get(DEFAULT_ACCOUNT_ID_KEY) : null;
            String value;
            boolean typeInvalid = false;
            if (raw == null) {
                value = null;
            } else if (raw instanceof String s) {
                value = s;
            } else {
                value = null;
                typeInvalid = true;
            }
            return new OperatorContextDto(value, present, hasUnknown, typeInvalid);
        }

        private OperatorContextDto(String defaultAccountId,
                                    boolean defaultAccountIdPresent,
                                    boolean hasUnknownKey,
                                    boolean valueTypeInvalid) {
            this.defaultAccountId = defaultAccountId;
            this.defaultAccountIdPresent = defaultAccountIdPresent;
            this.hasUnknownKey = hasUnknownKey;
            this.valueTypeInvalid = valueTypeInvalid;
        }

        public String defaultAccountId() {
            return defaultAccountId;
        }

        public boolean isDefaultAccountIdPresent() {
            return defaultAccountIdPresent;
        }

        public boolean hasUnknownKey() {
            return hasUnknownKey;
        }

        public boolean isValueTypeInvalid() {
            return valueTypeInvalid;
        }
    }
}
