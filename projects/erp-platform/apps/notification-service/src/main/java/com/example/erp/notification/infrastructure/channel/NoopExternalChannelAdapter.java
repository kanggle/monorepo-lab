package com.example.erp.notification.infrastructure.channel;

import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.notification.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default / no-op stub external channel (SLACK placeholder). <b>No-op — green-wash
 * forbidden</b>: it logs only and returns {@link DeliveryOutcome#noop()}, never
 * claiming an external delivery occurred. Active when
 * {@code erpplatform.notification.external.mode} is unset or anything other than
 * {@code slack} (the default, {@code matchIfMissing}), so it is the SLACK bean when
 * the real {@link SlackWebhookChannelAdapter} is off — exactly one {@code SLACK}
 * {@link NotificationChannelPort} per mode (net-zero default: no external delivery is
 * even created unless {@code external.enabled=true}).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erpplatform.notification.external.mode",
        havingValue = "noop", matchIfMissing = true)
public class NoopExternalChannelAdapter implements NotificationChannelPort {

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.SLACK;
    }

    @Override
    public DeliveryOutcome deliver(Notification notification) {
        log.debug("External channel (SLACK) is a v1 no-op stub; notification id={} NOT externally "
                + "delivered (v2)", notification.id());
        return DeliveryOutcome.noop();
    }
}
