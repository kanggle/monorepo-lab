package com.example.account.application.util;

import java.util.List;

/**
 * Minimal JSON string-array rendering shared by the role-mutation use-cases.
 *
 * <p>Centralizes the {@code ["a","b"]} rendering used to build the
 * {@code account_status_history.details} audit payload, replacing the
 * verbatim {@code toJsonStringArray} copies previously duplicated in
 * {@code AddAccountRoleUseCase}, {@code RemoveAccountRoleUseCase}, and
 * {@code AssignRolesUseCase}. Output is byte-identical to those copies
 * (audit-content preserving).
 */
public final class JsonArrayUtils {

    private JsonArrayUtils() {
        // utility class
    }

    /**
     * Render a list of strings as a JSON array literal with each element
     * double-quoted and embedded quotes backslash-escaped.
     *
     * @param items the strings to render (a {@code null} or empty list yields {@code "[]"})
     * @return a JSON array string such as {@code ["ROLE_ADMIN","ROLE_OPERATOR"]}
     */
    public static String toJsonStringArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
