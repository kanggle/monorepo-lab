package com.wms.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.inbound.adapter.in.messaging.masterref.MasterEventParser;
import com.wms.inbound.adapter.in.webhook.erp.ErpAsnWebhookController;
import com.wms.inbound.adapter.in.webhook.erp.ErpWebhookIngestService;
import com.wms.inbound.adapter.in.webhook.erp.HmacSignatureVerifier;
import com.wms.inbound.adapter.in.webhook.erp.TimestampWindowVerifier;
import com.wms.inbound.application.port.out.EventDedupePort;
import com.wms.inbound.application.port.out.IdempotencyStore;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.application.port.out.OutboxWriter;
import com.wms.inbound.application.port.out.WebhookSecretPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context under the {@code standalone} profile to catch
 * bean wiring regressions that slice tests miss.
 *
 * <p>The {@code Master*Consumer} beans are profile-gated to {@code !standalone}
 * because the {@code standalone} profile excludes Kafka autoconfiguration —
 * a Testcontainers Kafka integration test verifies their behaviour separately.
 *
 * <p>The webhook inbox processor is disabled in standalone via
 * {@code inbound.webhook.inbox.processor.enabled=false} in
 * {@code application-standalone.yml}.
 */
@SpringBootTest
@ActiveProfiles("standalone")
class ApplicationContextSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void infrastructureBeansAreWired() {
        assertThat(context.getBean(MasterEventParser.class)).isNotNull();
        assertThat(context.getBean(EventDedupePort.class)).isNotNull();
        assertThat(context.getBean(MasterReadModelPort.class)).isNotNull();
        assertThat(context.getBean(MasterReadModelWriterPort.class)).isNotNull();
        assertThat(context.getBean(IdempotencyStore.class)).isNotNull();
        assertThat(context.getBean(OutboxWriter.class)).isNotNull();
    }

    @Test
    void webhookBeansAreWired() {
        assertThat(context.getBean(ErpAsnWebhookController.class)).isNotNull();
        assertThat(context.getBean(ErpWebhookIngestService.class)).isNotNull();
        assertThat(context.getBean(HmacSignatureVerifier.class)).isNotNull();
        assertThat(context.getBean(TimestampWindowVerifier.class)).isNotNull();
        assertThat(context.getBean(WebhookSecretPort.class)).isNotNull();
    }

    @Test
    void securityFilterChainIsPresent() {
        assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
    }
}
