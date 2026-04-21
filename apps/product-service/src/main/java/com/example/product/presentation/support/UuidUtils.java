package com.example.product.presentation.support;

import java.util.UUID;

public final class UuidUtils {

    private UuidUtils() {}

    public static String toString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }
}
