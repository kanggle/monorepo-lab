package com.example.auth.presentation;

import com.example.auth.application.exception.SignupEmailConflictException;
import com.example.auth.application.exception.SignupInvalidException;
import com.example.auth.application.port.AccountServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * TASK-BE-470 / fix-001 — the signup page controller. A standalone MockMvc setup
 * (no security filters — the public access + CSRF are governed by
 * {@code WebLoginSecurityConfig}, exercised by the form-login integration suite)
 * verifies the GET view mapping and the POST server-side proxy branches. The
 * {@link AccountServicePort} is mocked so no real account-service call is made.
 */
class SignupPageControllerTest {

    private AccountServicePort accountServicePort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountServicePort = mock(AccountServicePort.class);
        // InternalResourceViewResolver special-cases the "redirect:" prefix (→ RedirectView,
        // so redirectedUrl() works) while resolving plain names (e.g. "signup") to an
        // InternalResourceView that only records forwardedUrl — no throw in a standalone setup.
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        // Non-empty prefix/suffix so a plain view name ("signup") forwards to a distinct
        // path (/WEB-INF/views/signup.jsp) and does not trip Spring's circular-view-path
        // guard against the "/signup" handler URL. "redirect:" is still special-cased.
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");
        mockMvc = MockMvcBuilders.standaloneSetup(new SignupPageController(accountServicePort))
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    @DisplayName("GET /signup renders the signup view with 200")
    void signupPageRendersView() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"));
    }

    @Test
    @DisplayName("POST /signup success proxies to account-service and redirects to /login?registered")
    void signupSuccessRedirects() throws Exception {
        doNothing().when(accountServicePort).signup(any(), any(), any());

        mockMvc.perform(post("/signup")
                        .param("email", "new@example.com")
                        .param("displayName", "New User")
                        .param("password", "Str0ng!pass")
                        .param("confirmPassword", "Str0ng!pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        verify(accountServicePort).signup("new@example.com", "Str0ng!pass", "New User");
    }

    @Test
    @DisplayName("POST /signup with mismatched passwords re-renders signup without calling account-service")
    void signupPasswordMismatchReRenders() throws Exception {
        mockMvc.perform(post("/signup")
                        .param("email", "new@example.com")
                        .param("password", "Str0ng!pass")
                        .param("confirmPassword", "different!"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("error"));

        verify(accountServicePort, never()).signup(any(), any(), any());
    }

    @Test
    @DisplayName("TASK-BE-472: malformed email (valid password) re-renders with an email-specific "
            + "error and never calls account-service")
    void signupMalformedEmailReRendersWithEmailError() throws Exception {
        // A valid password must NOT be blamed when the real fault is the email format.
        mockMvc.perform(post("/signup")
                        .param("email", "test@test") // no TLD — rejected by account-service Email regex
                        .param("password", "test1234!")
                        .param("confirmPassword", "test1234!"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attribute("error", containsString("이메일 형식")));

        verify(accountServicePort, never()).signup(any(), any(), any());
    }

    @Test
    @DisplayName("TASK-BE-472: SignupInvalidException from the proxy re-renders a message naming "
            + "both email and password")
    void signupInvalidExceptionMessageNamesEmailAndPassword() throws Exception {
        doThrow(new SignupInvalidException("validation failed"))
                .when(accountServicePort).signup(any(), any(), any());

        mockMvc.perform(post("/signup")
                        .param("email", "ok@example.com")
                        .param("password", "test1234!")
                        .param("confirmPassword", "test1234!"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attribute("error", containsString("이메일 형식")))
                .andExpect(model().attribute("error", containsString("패스워드")));
    }

    @Test
    @DisplayName("POST /signup on email conflict re-renders signup with an error")
    void signupConflictReRenders() throws Exception {
        doThrow(new SignupEmailConflictException("taken"))
                .when(accountServicePort).signup(any(), any(), any());

        mockMvc.perform(post("/signup")
                        .param("email", "dupe@example.com")
                        .param("password", "Str0ng!pass")
                        .param("confirmPassword", "Str0ng!pass"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("error"));
    }
}
