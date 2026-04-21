package com.wms.master.testsupport;

/**
 * Constants shared across controller and contract tests. Keeping these in one
 * place avoids verbatim duplication (see TASK-BE-018 item 7).
 *
 * <p>Non-instantiable — access via the public constants.
 */
public final class TestConstants {

    /**
     * Regex matching the ISO 8601 UTC timestamp format used by the platform
     * error envelope (see {@code platform/error-handling.md} § Error Response
     * Format). Accepts optional fractional seconds and requires a trailing
     * {@code Z} for the UTC zone.
     */
    public static final String ISO_TIMESTAMP_REGEX =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    private TestConstants() {
        // prevent instantiation
    }
}
