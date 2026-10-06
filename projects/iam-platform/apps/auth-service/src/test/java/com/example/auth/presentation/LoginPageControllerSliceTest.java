package com.example.auth.presentation;

import com.example.auth.application.port.TenantSignupEligibilityPort;
import com.example.auth.infrastructure.oauth.OAuthProperties;
import com.example.auth.infrastructure.security.LoginBrandingResolver;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-623 — the login page draws a social button only for a provider whose
 * credentials are configured (not the demo {@code test-*} default).
 *
 * <p>Asserts on the {@code providers} model attribute rather than rendered HTML: the
 * template's divider/button markup (<code>th:if="${!providers.isEmpty()}"</code> +
 * <code>th:each="provider : ${providers}"</code>) is unit-covered ground truth for
 * "0 configured → no buttons, no divider" / "1 configured → exactly that button" once
 * the list itself is right, and {@code LoginBrandingPageSliceTest} already renders the
 * real template end-to-end for the button-present case (its {@code loginFormContract}
 * cell asserts {@code href="/login/oauth/google"} with google configured).
 */
class LoginPageControllerSliceTest {

    private OAuthProperties oAuthProperties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        oAuthProperties = new OAuthProperties();

        SavedRequestTenantResolver savedRequestTenantResolver = mock(SavedRequestTenantResolver.class);
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution("fan-platform", "B2C", null));
        TenantSignupEligibilityPort tenantSignupEligibilityPort = mock(TenantSignupEligibilityPort.class);
        when(tenantSignupEligibilityPort.isSignupOffered(any())).thenReturn(true);

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");

        mockMvc = MockMvcBuilders
                .standaloneSetup(new LoginPageController(
                        savedRequestTenantResolver, tenantSignupEligibilityPort,
                        new LoginBrandingResolver(savedRequestTenantResolver),
                        oAuthProperties))
                .setViewResolvers(viewResolver)
                .build();
    }

    private void configure(OAuthProperties.ProviderProperties props) {
        props.setClientId("real-client-id");
        props.setClientSecret("real-client-secret");
    }

    @Test
    @DisplayName("AC-1: all four providers at the demo default (test-*) → providers model "
            + "attribute is empty (login.html's divider + buttons are both gated on this)")
    void noProviderConfigured_modelProvidersEmpty() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("providers", org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("AC-2: only Google configured → providers carries exactly GOOGLE")
    void onlyGoogleConfigured_modelProvidersContainsOnlyGoogle() throws Exception {
        configure(oAuthProperties.getGoogle());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("providers",
                        java.util.List.of(new LoginPageController.ProviderView("GOOGLE", "google"))));
    }

    @Test
    @DisplayName("AC-2 control — Google client-id is real but the secret is still the demo "
            + "default → NOT shown (a half-configured provider cannot finish the token exchange)")
    void googleHalfConfigured_isHidden() throws Exception {
        oAuthProperties.getGoogle().setClientId("real-google-client-id");
        oAuthProperties.getGoogle().setClientSecret("test-google-client-secret");

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("providers", org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("every provider configured → providers carries all four, in enum order")
    void allConfigured_modelProvidersContainsAllFour() throws Exception {
        configure(oAuthProperties.getGoogle());
        configure(oAuthProperties.getKakao());
        configure(oAuthProperties.getMicrosoft());
        configure(oAuthProperties.getNaver());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("providers", java.util.List.of(
                        new LoginPageController.ProviderView("GOOGLE", "google"),
                        new LoginPageController.ProviderView("KAKAO", "kakao"),
                        new LoginPageController.ProviderView("MICROSOFT", "microsoft"),
                        new LoginPageController.ProviderView("NAVER", "naver"))));
    }
}
