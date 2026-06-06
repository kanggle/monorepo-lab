package com.example.admin.application.console;

import java.util.List;

/**
 * TASK-BE-296: The full platform-console registry response envelope.
 *
 * <p>{@code { "products": [ ... ] }} — matches
 * {@code specs/contracts/http/console-registry-api.md}.
 */
public record ConsoleRegistry(
        List<ConsoleProduct> products
) {
}
