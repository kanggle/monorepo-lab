package com.example.notification.adapter.out.external;

import com.example.notification.application.port.out.WebPushGateway;
import com.example.notification.application.port.out.WebPushSendResult;
import com.example.notification.config.WebPushProperties;
import com.example.notification.domain.model.PushSubscription;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.security.Security;

/**
 * Web Push provider adapter backed by {@code nl.martijndwars:web-push} (TASK-BE-464). VAPID-signs
 * and encrypts the payload for a single subscription and POSTs it to the browser's push service.
 *
 * <p>When no VAPID keypair is configured the {@link PushService} is not built and
 * {@link #isConfigured()} returns false — {@code WebPushSender} then skips delivery (dev/standalone
 * behaves byte-identically to no-push; the context still boots).
 */
@Slf4j
@Component
public class MartijndwarsWebPushGateway implements WebPushGateway {

    private final WebPushProperties properties;
    private final PushService pushService;

    public MartijndwarsWebPushGateway(WebPushProperties properties) {
        this.properties = properties;
        this.pushService = buildPushService(properties);
        if (this.pushService == null) {
            log.warn("Web Push (VAPID) not configured — push delivery disabled. "
                    + "Set app.notification.push.vapid.public-key/private-key/subject to enable.");
        }
    }

    private static PushService buildPushService(WebPushProperties properties) {
        if (!properties.isConfigured()) {
            return null;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid VAPID keypair configuration", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return pushService != null;
    }

    @Override
    public String publicKey() {
        return properties.publicKey();
    }

    @Override
    public WebPushSendResult send(PushSubscription subscription, byte[] payload) {
        if (pushService == null) {
            throw new WebPushDeliveryException("Web Push not configured", null);
        }
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payload);
            HttpResponse response = pushService.send(notification);
            return new WebPushSendResult(response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            throw new WebPushDeliveryException(
                    "Web Push delivery failed for endpoint " + subscription.getEndpoint(), e);
        }
    }
}
