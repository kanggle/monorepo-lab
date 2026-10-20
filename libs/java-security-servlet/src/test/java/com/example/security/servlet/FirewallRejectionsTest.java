package com.example.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * TASK-MONO-801 — the handler a service registers so that a request the servlet firewall
 * rejects UP FRONT (its path or method) is answered 400 directly, instead of Spring Security's
 * default {@code sendError(400)} whose {@code /error} dispatch a deny-all chain answers 401.
 *
 * <p>Every rejection here is a REAL one, produced by {@link StrictHttpFirewall} — a hand-built
 * {@code RequestRejectedException} would carry whatever message the test chose, and the point
 * of the no-echo assertion is that the library's own message DOES carry the path.
 */
@DisplayName("FirewallRejections — up-front firewall rejection → 400 BAD_REQUEST")
class FirewallRejectionsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Logger logger = (Logger) LoggerFactory.getLogger(FirewallRejections.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static RequestRejectedException realRejection(MockHttpServletRequest raw) {
        try {
            new StrictHttpFirewall().getFirewalledRequest(raw);
        } catch (RequestRejectedException e) {
            return e;
        }
        throw new AssertionError("StrictHttpFirewall did not reject " + raw.getRequestURI());
    }

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        return request;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/x;y", "//x", "/%25", "/api/%2E%2E/x"})
    @DisplayName("default handler — 400, flat {code,message,timestamp}, BAD_REQUEST, path not echoed, one WARN")
    void defaultHandler_answers400FlatEnvelope(String uri) throws Exception {
        MockHttpServletRequest request = get(uri);
        RequestRejectedException rejected = realRejection(request);
        // sanity: the library's own message does carry the rejected path — so not echoing it
        // is something the handler has to do, not something that happens by default.
        assertThat(rejected.getMessage()).isNotBlank();

        MockHttpServletResponse response = new MockHttpServletResponse();
        FirewallRejections.handler().handle(request, response, rejected);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

        String body = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = JSON.readTree(body);
        assertThat(json.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(json.get("message").asText()).isEqualTo(FirewallRejections.MESSAGE);
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.size()).isEqualTo(3);
        assertThat(body).doesNotContain(uri).doesNotContain(rejected.getMessage());

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events.get(0).getFormattedMessage())
                .isEqualTo("Request rejected by the servlet firewall (RequestRejectedException)")
                .doesNotContain(uri);
        assertThat(events.get(0).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("a disallowed method is rejected up front too — same answer")
    void defaultHandler_disallowedMethod() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("FOO", "/x");
        RequestRejectedException rejected = realRejection(request);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FirewallRejections.handler().handle(request, response, rejected);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(response.getContentAsString()).get("code").asText())
                .isEqualTo("BAD_REQUEST");
        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("custom body writer — receives the fixed code and message, its bytes are the body")
    void customWriter_writesItsOwnEnvelope() throws Exception {
        MockHttpServletRequest request = get("/x;y");
        RequestRejectedException rejected = realRejection(request);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FirewallRejections.handler((code, message) ->
                        "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}")
                .handle(request, response, rejected);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.at("/error/code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(json.at("/error/message").asText()).isEqualTo(FirewallRejections.MESSAGE);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("an already-committed response is left alone — still one WARN, no write attempt")
    void committedResponse_isNotRewritten() throws Exception {
        MockHttpServletRequest request = get("/x;y");
        RequestRejectedException rejected = realRejection(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("partial");
        response.flushBuffer();

        FirewallRejections.handler().handle(request, response, rejected);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("partial");
        assertThat(appender.list).hasSize(1);
    }

    @Test
    @DisplayName("null body writer is refused at wiring time")
    void nullWriter_refused() {
        assertThatThrownBy(() -> FirewallRejections.handler(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("is a Spring Security RequestRejectedHandler")
    void isRequestRejectedHandler() {
        RequestRejectedHandler handler = FirewallRejections.handler();
        assertThat(handler).isNotNull();
    }
}
