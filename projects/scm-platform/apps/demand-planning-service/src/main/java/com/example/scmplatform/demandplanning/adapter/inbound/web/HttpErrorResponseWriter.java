package com.example.scmplatform.demandplanning.adapter.inbound.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the platform JSON error envelope ({@code {code, message, timestamp}})
 * straight to the servlet response for security-layer failures that occur
 * <b>before</b> the controller advice runs — the OAuth2 authentication entry
 * point / access-denied handler ({@code SecurityConfig}) and the tenant
 * fail-closed filter ({@code TenantClaimEnforcer}). Centralising it keeps the
 * pre-controller error shape byte-identical across both call sites.
 */
public final class HttpErrorResponseWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpErrorResponseWriter() {
    }

    public static void writeError(HttpServletResponse response, int status,
                                  String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ObjectNode node = JSON.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        node.put("timestamp", Instant.now().toString());
        try {
            response.getWriter().write(JSON.writeValueAsString(node));
        } catch (JsonProcessingException ex) {
            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
