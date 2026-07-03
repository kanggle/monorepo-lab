package com.example.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceView;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * TASK-BE-470 — the signup page controller only maps {@code GET /signup} to the
 * {@code signup} Thymeleaf view. A standalone MockMvc setup verifies the mapping
 * and view name without loading the security filter chains (the page's public
 * access is governed by {@code WebLoginSecurityConfig}, exercised by the
 * form-login integration suite).
 */
class SignupPageControllerTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new SignupPageController())
                    // No real ViewResolver in a standalone setup — resolve every view name
                    // to a no-op InternalResourceView so rendering does not throw; the view
                    // name captured on the ModelAndView is still asserted below.
                    .setViewResolvers((viewName, locale) -> new InternalResourceView(viewName))
                    .build();

    @Test
    @DisplayName("GET /signup renders the signup view with 200")
    void signupPageRendersView() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"));
    }
}
