package com.wms.inbound.application.service;

import com.wms.inbound.application.port.out.WebhookInboxStorePort;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper component holding the {@code REQUIRES_NEW} transactional methods that
 * back {@link ErpWebhookInboxProcessor}.
 *
 * <p>Why a separate bean?
 * Spring's {@code @Transactional} works through AOP proxies. When a method on a
 * bean calls another method on the <em>same</em> bean ({@code this.x()}), the
 * call goes through the raw object reference and bypasses the proxy — so the
 * {@code REQUIRES_NEW} annotation has no effect. The processor wants each
 * inbox-row status flip to commit independently of the (already-completed)
 * domain TX, so the helper must live behind a real proxy. Eliminates the
 * {@code @Lazy self} self-injection workaround that the previous in-bean
 * {@code updateStatus} method required.
 *
 * <p>Each method delegates to {@link WebhookInboxStorePort}, which itself runs
 * inside a Spring-managed transaction. The {@code REQUIRES_NEW} on this layer
 * guarantees the row update is isolated from any outer transaction the caller
 * may inadvertently start, which is the property the processor depends on for
 * "one row's failure does not block the rest of the batch".
 */
@Component
public class WebhookInboxStatusUpdater {

    private final WebhookInboxStorePort inboxStore;

    public WebhookInboxStatusUpdater(WebhookInboxStorePort inboxStore) {
        this.inboxStore = inboxStore;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markApplied(String eventId, Instant at) {
        inboxStore.markApplied(eventId, at);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, Instant at, String reason) {
        inboxStore.markFailed(eventId, at, reason);
    }
}
