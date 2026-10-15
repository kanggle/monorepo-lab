package com.example.admin.presentation;

import com.example.admin.application.OnboardingCallerAccount;
import com.example.admin.application.ReceivedInvitationsQueryUseCase;
import com.example.admin.application.SelfServiceOnboardingUseCase;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.presentation.dto.OnboardOrganizationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S4 — regression: changing the subject-token port to return «sub + amr» must NOT change
 * the self-service onboarding entry (security.md row 7: «그쪽 판정은 sub 만 본다»). A caller whose token
 * carries no second factor still onboards exactly as before; onboarding has no second-factor gate.
 *
 * <p>The validator here is a real implementation of the port (not a mock), so the controller's call goes
 * through the port's {@code validateAndExtractSubject} default — the very code path S4 introduced.
 *
 * <p>TASK-MONO-773 S3 — the account read's outcome is handed to the use case as the trust gate's input: a record →
 * present; account-service {@code 404} → empty (option A — not an error any more); any other failure propagates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OnboardingController — unchanged by the S4 amr port change (TASK-MONO-771) · 773 S3 account read")
class OnboardingControllerSecondFactorRegressionTest {

    @Mock AccountServiceClient accountServiceClient;
    @Mock SelfServiceOnboardingUseCase onboardingUseCase;
    @Mock ReceivedInvitationsQueryUseCase receivedInvitationsUseCase;

    private OnboardingController controller() {
        IamOidcSubjectTokenValidator validator =
                token -> new IamOidcSubjectTokenValidator.ValidatedSubject("acc-1", Set.of("pwd"));
        return new OnboardingController(validator, accountServiceClient, onboardingUseCase,
                receivedInvitationsUseCase);
    }

    @Test
    @DisplayName("subject token WITHOUT mfa (amr=[pwd]) → onboarding proceeds with the sub, 201")
    void noSecondFactor_onboardingUnchanged() {
        when(accountServiceClient.getDetail("acc-1")).thenReturn(new AccountServiceClient.AccountDetailResponse(
                "acc-1", "founder@example.com", "ACTIVE", Instant.now(), null));
        Optional<OnboardingCallerAccount> account =
                Optional.of(new OnboardingCallerAccount("founder@example.com", "founder@example.com"));
        when(onboardingUseCase.onboard("new-org", "New Org", "acc-1", account))
                .thenReturn(new SelfServiceOnboardingUseCase.Result("new-org", "op-1",
                        List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN")));

        var response = controller().onboardOrganization(new OnboardOrganizationRequest("t", "new-org", "New Org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(onboardingUseCase).onboard("new-org", "New Org", "acc-1", account);
    }

    @Test
    @DisplayName("🔴 773 option A: account-service 404 (no account record — e.g. an iam credential) → the use case gets an EMPTY account, not a 503")
    void accountRecord404_isTheGatesInput() {
        when(accountServiceClient.getDetail("acc-1")).thenThrow(new NonRetryableDownstreamException(
                "account-service error 404", null, 404, "ACCOUNT_NOT_FOUND"));
        when(onboardingUseCase.onboard("new-org", "New Org", "acc-1", Optional.empty()))
                .thenReturn(new SelfServiceOnboardingUseCase.Result("new-org", "op-41",
                        List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN")));

        var response = controller().onboardOrganization(new OnboardOrganizationRequest("t", "new-org", "New Org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(onboardingUseCase).onboard("new-org", "New Org", "acc-1", Optional.empty());
    }

    @Test
    @DisplayName("773 fail-closed: any other account-read failure (a 400, a 5xx) propagates — never read as «no record»")
    void otherAccountReadFailures_propagate() {
        NonRetryableDownstreamException bad = new NonRetryableDownstreamException(
                "account-service error 400", null, 400, "VALIDATION_ERROR");
        when(accountServiceClient.getDetail("acc-1")).thenThrow(bad);
        assertThatThrownBy(() -> controller().onboardOrganization(
                new OnboardOrganizationRequest("t", "new-org", "New Org"))).isSameAs(bad);

        DownstreamFailureException down = new DownstreamFailureException("account-service unavailable");
        org.mockito.Mockito.doThrow(down).when(accountServiceClient).getDetail("acc-1");
        assertThatThrownBy(() -> controller().onboardOrganization(
                new OnboardOrganizationRequest("t", "new-org", "New Org"))).isSameAs(down);
        verifyNoInteractions(onboardingUseCase);
    }

    @Test
    @DisplayName("773 fail-closed: a 200 without the record (no email) is not «no record» → 503-class failure, use case not called")
    void emptyRecord_isNotAbsence() {
        when(accountServiceClient.getDetail("acc-1")).thenReturn(new AccountServiceClient.AccountDetailResponse(
                "acc-1", null, "ACTIVE", Instant.now(), null));
        assertThatThrownBy(() -> controller().onboardOrganization(
                new OnboardOrganizationRequest("t", "new-org", "New Org")))
                .isInstanceOf(DownstreamFailureException.class);
        verify(onboardingUseCase, org.mockito.Mockito.never()).onboard(any(), any(), any(), any());
    }
}
