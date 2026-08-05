package com.example.user.infrastructure.config;

import com.example.user.application.service.UserProfileProvisioner;
import com.example.user.presentation.filter.UserProfileProvisioningFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link UserProfileProvisioningFilter} (TASK-BE-575).
 *
 * <p>Explicit registration rather than {@code @Component} scanning — see that filter's class
 * javadoc for why (a scanned {@code Filter} bean is pulled into every {@code @WebMvcTest}
 * slice along with its persistence dependencies).
 *
 * <p>Order is {@code HIGHEST_PRECEDENCE + 10}: after {@code TenantContextFilter}
 * ({@code HIGHEST_PRECEDENCE}), which must have bound the tenant before a profile can be
 * filed under it, and before anything that reads a profile.
 */
@Configuration
public class UserProfileProvisioningFilterConfig {

    @Bean
    public FilterRegistrationBean<UserProfileProvisioningFilter> userProfileProvisioningFilter(
            UserProfileProvisioner provisioner) {
        FilterRegistrationBean<UserProfileProvisioningFilter> registration =
                new FilterRegistrationBean<>(new UserProfileProvisioningFilter(provisioner));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
