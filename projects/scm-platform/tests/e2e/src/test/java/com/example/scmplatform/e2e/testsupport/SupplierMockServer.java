package com.example.scmplatform.e2e.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Lightweight supplier stand-in for the scm-platform e2e suite. Plays the
 * external supplier system that {@code procurement-service}'s
 * {@code RestSupplierAdapter} hits on PO submit
 * ({@code POST /api/supplier/orders}).
 *
 * <p>Two operating modes:
 *
 * <ul>
 *   <li><b>queue</b> (default) — pre-enqueue specific responses per call via
 *       {@link #enqueue(MockResponse)}. Suitable for the happy-path
 *       (single 200) and supplier-ack flows.</li>
 *   <li><b>scripted</b> — install a {@link Dispatcher} that always returns the
 *       same response (e.g. constant 503) until {@link #setQueueMode()} flips
 *       back. Used by the circuit-breaker scenario which needs N consecutive
 *       5xx, then a single 200 to verify cooldown re-entry.</li>
 * </ul>
 *
 * <p>Binds to {@code 0.0.0.0} on an ephemeral port so the URL is reachable
 * from inside Testcontainers containers via {@code host.docker.internal}.
 *
 * <p>Adapted from {@code projects/fan-platform/tests/e2e/.../JwksMockServer}
 * with a different request/response shape (PO body in / receiptRef out).
 */
public final class SupplierMockServer implements AutoCloseable {

    private final MockWebServer server;
    private final AtomicReference<Dispatcher> activeDispatcher = new AtomicReference<>();

    public SupplierMockServer() throws IOException {
        this.server = new MockWebServer();
        // Default: queue dispatcher — tests pre-enqueue per-call responses.
        QueueDispatcher queueDispatcher = new QueueDispatcher();
        this.activeDispatcher.set(queueDispatcher);
        this.server.setDispatcher(queueDispatcher);
        this.server.start(InetAddress.getByName("0.0.0.0"), 0);
    }

    /** URL reachable from inside Testcontainers containers via host.docker.internal. */
    public String containerBaseUrl() {
        return "http://host.docker.internal:" + server.getPort();
    }

    /** URL reachable from the host JVM — useful for ad-hoc verification. */
    public String hostBaseUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort();
    }

    /** Pre-enqueue a single response for the next supplier POST. */
    public void enqueue(MockResponse response) {
        Dispatcher d = activeDispatcher.get();
        if (d instanceof QueueDispatcher q) {
            q.enqueueResponse(response);
        } else {
            throw new IllegalStateException(
                    "enqueue() is only valid in queue mode; current dispatcher=" + d.getClass().getSimpleName());
        }
    }

    /** Pre-enqueue a successful supplier ACK with a synthetic receipt reference. */
    public void enqueueSuccess(String receiptRef) {
        enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"" + receiptRef + "\",\"status\":\"RECEIVED\"}"));
    }

    /** Pre-enqueue {@code count} consecutive 503 responses (circuit-breaker arming). */
    public void enqueue503(int count) {
        for (int i = 0; i < count; i++) {
            enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":\"supplier upstream unavailable\"}"));
        }
    }

    /**
     * Switch to a "always 503" dispatcher — ignores the queue and returns 503
     * regardless of how many requests come in. Useful when the procurement
     * retry+circuit-breaker stack issues an unknown number of upstream calls
     * before we tear the wall down via {@link #setQueueMode()}.
     */
    public void setAlways503() {
        Dispatcher always = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setResponseCode(503)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":\"supplier upstream always-503 mode\"}");
            }
        };
        this.activeDispatcher.set(always);
        this.server.setDispatcher(always);
    }

    /** Switch back to queue mode (preserving any pre-existing queue is not supported). */
    public void setQueueMode() {
        QueueDispatcher q = new QueueDispatcher();
        this.activeDispatcher.set(q);
        this.server.setDispatcher(q);
    }

    /** Number of HTTP requests received so far. */
    public int requestCount() {
        return server.getRequestCount();
    }

    @Override
    public void close() throws IOException {
        server.shutdown();
    }
}
