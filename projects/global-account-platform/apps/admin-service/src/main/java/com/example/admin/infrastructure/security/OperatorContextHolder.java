package com.example.admin.infrastructure.security;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class OperatorContextHolder {

    private OperatorContextHolder() {}

    public static OperatorContext require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OperatorContext ctx)) {
            throw new OperatorUnauthorizedException("operator context not available");
        }
        return ctx;
    }
}
