package com.example.admin.application.exception;

/**
 * Base type for account-service <em>business</em> responses (4xx conflicts / not-found)
 * surfaced by the admin&rarr;account clients ({@code AccountServiceClient},
 * {@code AccountServiceTenantClient}, {@code AccountServiceOrgNodeClient}). These represent a
 * <strong>definitive downstream answer at the transport level — not a downstream fault</strong>,
 * so resilience4j must NOT count them toward the circuit-breaker failure rate and must NOT retry
 * them. The {@code accountService} retry + circuit-breaker instances list this base in
 * {@code ignore-exceptions}, which — because resilience4j matches by assignability — covers every
 * subtype (present and future), so a new account business exception is ignored automatically
 * (no config list to keep in sync). TASK-BE-516.
 *
 * <p><strong>Deliberately a sibling of {@link RuntimeException}, NOT a subtype of
 * {@link DownstreamFailureException}.</strong> admin-service has several {@code catch
 * (DownstreamFailureException)} fault-handling blocks (e.g. finalize-to-FAILURE, CIRCUIT_OPEN
 * mapping); making business exceptions extend {@code DownstreamFailureException} would divert them
 * into that fault path. Keeping this base under {@code RuntimeException} leaves those blocks — and
 * the per-type {@code @ExceptionHandler}s — unchanged.
 */
public abstract class AccountBusinessException extends RuntimeException {

    protected AccountBusinessException(String message) {
        super(message);
    }

    protected AccountBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
