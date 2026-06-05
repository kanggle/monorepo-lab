package com.example.erp.notification.infrastructure.channel;

import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.notification.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * v1 stub external channel (SLACK placeholder). <b>No-op — green-wash
 * forbidden</b>: it logs only and returns {@link DeliveryOutcome#noop()}, never
 * claiming an external delivery occurred. The v1 pipeline routes only to the
 * IN_APP channel, so this adapter is never selected; it exists so the v2
 * external channel (real Slack/SMTP + the exercised Category C
 * {@code DeliveryRetryScheduler}) binds against the same {@link
 * NotificationChannelPort} without touching the domain (wms notification-service
 * {@code ChannelPort} precedent).
 */
@Slf4j
@Component
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
