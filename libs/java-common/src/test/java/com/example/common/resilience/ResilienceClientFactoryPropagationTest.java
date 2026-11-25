package com.example.common.resilience;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-845 AC-2 — measures trace propagation where it matters: the header
 * the <em>receiving</em> server got. Observing that an observation started would
 * not prove a {@code traceparent} was written onto the wire.
 *
 * <p>Control: the same factory with {@link ObservationRegistry#NOOP} sends no
 * {@code traceparent} — the state every factory-built client was in before.
 */
class ResilienceClientFactoryPropagationTest {

    private HttpServer server;
    private final AtomicReference<String> receivedTraceparent = new AtomicReference<>();
    private OpenTelemetrySdk openTelemetry;
    private Tracer tracer;
    private ObservationRegistry tracingRegistry;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            receivedTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ContextPropagators propagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance());
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
                .setPropagators(propagators)
                .build();
        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer("resilience-client-factory-test");
        tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> { });
        OtelPropagator propagator = new OtelPropagator(propagators, otelTracer);

        tracingRegistry = ObservationRegistry.create();
        tracingRegistry.observationConfig()
                .observationHandler(new PropagatingSenderTracingObservationHandler<>(tracer, propagator));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        openTelemetry.close();
    }

    @Test
    void clientBuiltWithTracingRegistry_sendsTraceparentOfTheCurrentTrace() {
        RestClient client = ResilienceClientFactory.buildRestClient(
                baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2), tracingRegistry);

        Span parent = tracer.nextSpan().name("incoming-request").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            client.get().uri("/ping").retrieve().body(String.class);
        } finally {
            parent.end();
        }

        String traceparent = receivedTraceparent.get();
        assertThat(traceparent)
                .as("the downstream server must receive a W3C traceparent header")
                .isNotNull();
        // traceparent = version-traceId-parentSpanId-flags
        assertThat(traceparent.split("-")[1])
                .as("the header must continue the caller's trace, not start a new one")
                .isEqualTo(parent.context().traceId());
    }

    @Test
    void clientBuiltWithNoopRegistry_sendsNoTraceparent_control() {
        RestClient client = ResilienceClientFactory.buildRestClient(
                baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2), ObservationRegistry.NOOP);

        Span parent = tracer.nextSpan().name("incoming-request").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            client.get().uri("/ping").retrieve().body(String.class);
        } finally {
            parent.end();
        }

        assertThat(receivedTraceparent.get())
                .as("control: without an observation registry nothing injects the header")
                .isNull();
    }

    @Test
    void millisecondOverload_alsoPropagates() {
        RestClient client = ResilienceClientFactory.buildRestClient(baseUrl(), 2000, 2000, tracingRegistry);

        Span parent = tracer.nextSpan().name("incoming-request").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            client.get().uri("/ping").retrieve().body(String.class);
        } finally {
            parent.end();
        }

        assertThat(receivedTraceparent.get()).isNotNull();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
