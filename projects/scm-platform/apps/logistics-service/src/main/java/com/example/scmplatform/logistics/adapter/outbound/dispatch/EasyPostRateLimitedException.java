package com.example.scmplatform.logistics.adapter.outbound.dispatch;

/**
 * Raised by the EasyPost client on a {@code 429 Too Many Requests}. Distinct from
 * {@code HttpClientErrorException} <b>on purpose</b>: 429 must be <b>retried</b> (§1.6), whereas
 * every other 4xx is a permanent failure that is ignored by the retry/circuit
 * (external-integrations.md §1.5/§1.6). Referenced by FQN in {@code application.yml}'s
 * resilience4j {@code retry-exceptions} / {@code record-exceptions}.
 */
public class EasyPostRateLimitedException extends RuntimeException {

    public EasyPostRateLimitedException(String message) {
        super(message);
    }
}
