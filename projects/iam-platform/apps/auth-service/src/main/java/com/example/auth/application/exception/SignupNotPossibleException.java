package com.example.auth.application.exception;

/**
 * TASK-BE-580 — account-service refused the signup for a reason that <b>retrying cannot
 * change</b>.
 *
 * <p>Distinct from {@link AccountServiceUnavailableException}, which means "ask again later".
 * Before this existed, {@code AccountServiceClient.signup} folded every unclassified 4xx into
 * that outage exception, so a permanent refusal was reported to the visitor as
 * <i>"잠시 후 다시 시도해 주세요. 인증 서비스가 일시적으로 불가합니다."</i> — advice that can
 * never come true, and which also erased the reason: neither the page nor the log summary
 * contained the word {@code TENANT_NOT_FOUND}, so the defect read as flaky availability.
 *
 * <p><b>4xx means "the request was wrong", not "try later".</b> Of the 4xx the signup endpoint
 * can produce, only {@code 429} is genuinely transient.
 *
 * @see com.example.auth.infrastructure.client.AccountServiceClient
 */
public class SignupNotPossibleException extends RuntimeException {

    /** The {@code code} field of account-service's error body, e.g. {@code TENANT_NOT_FOUND}. */
    private final String errorCode;

    public SignupNotPossibleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
