package com.example.admin.presentation;

import com.example.admin.application.SelfServiceOnboardingUseCase;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S4 — regression: changing the subject-token port to return «sub + amr» must NOT change
 * the self-service onboarding entry (security.md row 7: «그쪽 판정은 sub 만 본다»). A caller whose token
 * carries no second factor still onboards exactly as before; onboarding has no second-factor gate.
 *
 * <p>The validator here is a real implementation of the port (not a mock), so the controller's call goes
 * through the port's {@code validateAndExtractSubject} default — the very code path S4 introduced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OnboardingController — unchanged by the S4 amr port change (TASK-MONO-771)")
class OnboardingControllerSecondFactorRegressionTest {

    @Mock AccountServiceClient accountServiceClient;
    @Mock SelfServiceOnboardingUseCase onboardingUseCase;

    @Test
    @DisplayName("subject token WITHOUT mfa (amr=[pwd]) → onboarding proceeds with the sub, 201")
    void noSecondFactor_onboardingUnchanged() {
        IamOidcSubjectTokenValidator validator =
                token -> new IamOidcSubjectTokenValidator.ValidatedSubject("acc-1", Set.of("pwd"));
        OnboardingController controller =
                new OnboardingController(validator, accountServiceClient, onboardingUseCase);
        when(accountServiceClient.getDetail("acc-1")).thenReturn(new AccountServiceClient.AccountDetailResponse(
                "acc-1", "founder@example.com", "ACTIVE", Instant.now(), null));
        when(onboardingUseCase.onboard("new-org", "New Org", "acc-1", "founder@example.com",
                "founder@example.com"))
                .thenReturn(new SelfServiceOnboardingUseCase.Result("new-org", "op-1",
                        List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN")));

        var response = controller.onboardOrganization(
                new OnboardOrganizationRequest("t", "new-org", "New Org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(onboardingUseCase).onboard("new-org", "New Org", "acc-1", "founder@example.com",
                "founder@example.com");
    }
}
