package com.example.payment.application.port.out;

public interface PaymentMetricRecorder {

    void incrementPaymentCreated();

    void incrementPaymentCompleted();

    void incrementPaymentRefunded();

    /**
     * Money-safety counter (TASK-BE-437): the confirm() post-capture auto-refund
     * failed at the PG, leaving captured funds stranded pending operator action.
     */
    void incrementRefundStranded();

    /**
     * Money-safety counter (TASK-BE-438): the stranded-refund sweeper auto-healed a stranding
     * (PG already cancelled, or a retry cancel succeeded) — no operator action was needed.
     */
    void incrementRefundStrandedResolved();

    /**
     * Money-safety counter (TASK-BE-438): a stranding reached the terminal {@code UNRESOLVED}
     * state (attempt cap exhausted or a definitive PG rejection) — an operator must act.
     */
    void incrementRefundStrandedUnresolved();

    /**
     * Registers the {@code payment_refund_stranded_open} gauge (TASK-BE-438) — the current count
     * of open ({@code STRANDED}) obligations, for an alerting SLO. Idempotent: registering the
     * same gauge name twice is a no-op in Micrometer. Called once at sweeper startup with a
     * supplier backed by {@code StrandedRefundRepository.countOpen()}.
     */
    void registerStrandedOpenGauge(java.util.function.Supplier<Number> openCount);

    void addPaymentAmount(long amount);

    void incrementEventPublishFailure(String eventType);
}
