package com.wms.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.adapter.in.messaging.consumer.MasterEventParser;
import com.wms.outbound.adapter.in.webhook.erp.ErpOrderWebhookController;
import com.wms.outbound.adapter.in.webhook.erp.HmacVerifier;
import com.wms.outbound.adapter.in.webhook.erp.TimestampWindowValidator;
import com.wms.outbound.adapter.out.persistence.adapter.WebhookInboxPersistenceAdapter;
import com.wms.outbound.application.port.in.ProcessWebhookInboxUseCase;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.IdempotencyStore;
import com.wms.outbound.application.port.out.MasterReadModelPort;
import com.wms.outbound.application.port.out.MasterReadModelWriterPort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.WebhookSecretPort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
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
 * <p>Master consumers are profile-gated to {@code !standalone} because the
 * standalone profile excludes Kafka autoconfiguration entirely.
 */
@SpringBootTest
@ActiveProfiles("standalone")
class OutboundServiceSmokeTest {

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
        assertThat(context.getBean(OutboxWriterPort.class)).isNotNull();
        assertThat(context.getBean(ShipmentNotificationPort.class)).isNotNull();
        assertThat(context.getBean(OutboundSagaCoordinator.class)).isNotNull();
        assertThat(context.getBean(ProcessWebhookInboxUseCase.class)).isNotNull();
    }

    @Test
    void webhookBeansAreWired() {
        assertThat(context.getBean(ErpOrderWebhookController.class)).isNotNull();
        assertThat(context.getBean(WebhookInboxPersistenceAdapter.class)).isNotNull();
        assertThat(context.getBean(HmacVerifier.class)).isNotNull();
        assertThat(context.getBean(TimestampWindowValidator.class)).isNotNull();
        assertThat(context.getBean(WebhookSecretPort.class)).isNotNull();
    }

    @Test
    void securityFilterChainIsPresent() {
        assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
    }
}
