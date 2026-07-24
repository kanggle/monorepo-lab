package com.example.scmplatform.logistics.adapter.outbound.dispatch;

/**
 * Raised by the 굿스플로 client on a {@code 429 Too Many Requests}. Distinct from
 * {@code HttpClientErrorException} <b>on purpose</b>: 429 must be <b>retried</b> (§2.6), whereas
 * every other 4xx is a permanent failure that is ignored by the retry/circuit
 * (external-integrations.md §2.5/§2.6). Referenced by FQN in {@code application.yml}'s
 * resilience4j {@code retry-exceptions} / {@code record-exceptions} for the {@code goodsflowDispatch}
 * instances — an <b>independent</b> instance set from EasyPost's (I9).
 */
public class GoodsflowRateLimitedException extends RuntimeException {

    public GoodsflowRateLimitedException(String message) {
        super(message);
    }
}
