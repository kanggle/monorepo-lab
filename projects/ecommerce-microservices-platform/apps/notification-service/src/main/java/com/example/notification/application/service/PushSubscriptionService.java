package com.example.notification.application.service;

import com.example.notification.application.command.RegisterPushSubscriptionCommand;
import com.example.notification.application.port.in.ManagePushSubscriptionUseCase;
import com.example.notification.application.port.out.PushSubscriptionRepository;
import com.example.notification.application.port.out.WebPushGateway;
import com.example.notification.application.result.RegisterSubscriptionResult;
import com.example.notification.domain.exception.PushNotConfiguredException;
import com.example.notification.domain.model.PushSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService implements ManagePushSubscriptionUseCase {

    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushGateway webPushGateway;

    @Override
    @Transactional
    public RegisterSubscriptionResult register(RegisterPushSubscriptionCommand command) {
        // Idempotent on endpoint: a browser re-subscribing (keys rotated) updates the stored
        // keys rather than creating a duplicate row; a fresh endpoint inserts a new row.
        return subscriptionRepository.findByEndpoint(command.endpoint())
                .map(existing -> {
                    existing.updateKeys(command.p256dh(), command.auth());
                    PushSubscription saved = subscriptionRepository.save(existing);
                    return new RegisterSubscriptionResult(saved.getSubscriptionId(), false);
                })
                .orElseGet(() -> {
                    PushSubscription created = PushSubscription.register(
                            command.userId(), command.endpoint(), command.p256dh(), command.auth());
                    PushSubscription saved = subscriptionRepository.save(created);
                    return new RegisterSubscriptionResult(saved.getSubscriptionId(), true);
                });
    }

    @Override
    @Transactional
    public void unregister(String userId, String endpoint) {
        // Only the owner may remove their endpoint; absent/foreign endpoint → no-op (idempotent 204).
        subscriptionRepository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUserId().equals(userId))
                .ifPresent(subscription -> subscriptionRepository.deleteByEndpoint(endpoint));
    }

    @Override
    @Transactional(readOnly = true)
    public String getVapidPublicKey() {
        if (!webPushGateway.isConfigured()) {
            throw new PushNotConfiguredException();
        }
        return webPushGateway.publicKey();
    }
}
