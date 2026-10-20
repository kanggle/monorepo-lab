package com.example.security.servlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.RequestRejectedHandler;

/**
 * The answer to a request the servlet firewall rejects — {@code 400 BAD_REQUEST} written
 * directly, in a JSON envelope, without echoing what was rejected (TASK-MONO-801).
 *
 * <h2>Why a service needs this</h2>
 *
 * {@code StrictHttpFirewall} rejects a path containing {@code ;}, {@code //}, {@code %25},
 * {@code %2E}, … (and a disallowed method) <em>up front</em>, inside {@code FilterChainProxy},
 * before any security filter or the dispatcher runs. Spring Security's default handler then
 * calls {@code sendError(400)}; the container dispatches to {@code /error}; and a chain that
 * ends in {@code denyAll()} or {@code authenticated()} meets that dispatch anonymous — so the
 * client receives <strong>401 «Authentication required»</strong> even with a valid token, and
 * is told to re-authenticate for a request no token could ever make acceptable. A chain that
 * permits {@code /error} gets Boot's default body instead, which echoes the path.
 *
 * <p>This handler writes the response itself: no error dispatch, so the chain's treatment of
 * {@code /error} no longer matters, and nothing else about {@code /error} changes.
 * {@code FilterChainProxy} also routes here a rejection thrown lazily by a security filter
 * (a header read during authentication); one thrown inside the dispatcher is caught first by
 * the shared servlet exception handler (TASK-MONO-800), which answers the same code.
 *
 * <h2>How to register it</h2>
 *
 * This library ships no auto-configuration. Each service declares exactly one bean; Spring
 * Security's {@code WebSecurity} picks it up from the context for the single
 * {@code FilterChainProxy}, so every chain of a multi-chain service is covered:
 *
 * <pre>{@code
 * @Bean
 * RequestRejectedHandler requestRejectedHandler() {
 *     return FirewallRejections.handler();
 * }
 * }</pre>
 *
 * 🔴 Declare <strong>one</strong>. {@code WebSecurity} looks the bean up by type and treats
 * «more than one» like «none» — it silently falls back to the default handler.
 *
 * <p>A service with its own error envelope passes a {@link BodyWriter}; it receives the fixed
 * {@link #CODE} and {@link #MESSAGE} and returns the body text.
 */
public final class FirewallRejections {

    /** Body code — the same one the shared servlet exception handler uses for a lazy rejection. */
    public static final String CODE = "BAD_REQUEST";

    /**
     * Fixed message. Never the exception's own message: that one quotes the rejected path or
     * header value, which may carry a token.
     */
    public static final String MESSAGE =
            "The request was rejected because its URL, method or headers are not allowed";

    private static final Logger log = LoggerFactory.getLogger(FirewallRejections.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private FirewallRejections() {
    }

    /** Writes the body for a rejection, given the fixed code and message. */
    @FunctionalInterface
    public interface BodyWriter {
        String write(String code, String message) throws IOException;
    }

    /** The default: the common flat envelope {@code {code, message, timestamp}}. */
    public static RequestRejectedHandler handler() {
        return handler(FirewallRejections::flatEnvelope);
    }

    /** A service-specific envelope. The writer never sees the request or the exception. */
    public static RequestRejectedHandler handler(BodyWriter bodyWriter) {
        Objects.requireNonNull(bodyWriter, "bodyWriter");
        return (request, response, rejected) -> {
            // One WARN, class name only: the exception message quotes the rejected path/value.
            log.warn("Request rejected by the servlet firewall ({})", rejected.getClass().getSimpleName());
            if (response.isCommitted()) {
                return;
            }
            write(response, bodyWriter.write(CODE, MESSAGE));
        };
    }

    private static void write(HttpServletResponse response, String body) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    private static String flatEnvelope(String code, String message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        node.put("timestamp", Instant.now().toString());
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            // Three flat strings, both fixed but the timestamp; still emit a parseable body.
            return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        }
    }
}
