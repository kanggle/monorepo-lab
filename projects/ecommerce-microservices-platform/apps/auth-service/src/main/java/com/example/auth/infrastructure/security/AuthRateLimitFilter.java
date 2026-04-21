package com.example.auth.infrastructure.security;

import com.example.auth.domain.service.RateLimiter;
import com.example.auth.domain.service.AuthMetricsRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_RESPONSE =
        "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Please try again later.\"}";

    private record PathLimit(int maxRequests, long windowSeconds) {}

    private final RateLimiter loginRateLimiter;
    private final AuthMetricsRecorder authMetrics;
    private final Map<String, PathLimit> pathLimits;

    public AuthRateLimitFilter(
            RateLimiter loginRateLimiter,
            AuthMetricsRecorder authMetrics,
            @Value("${app.rate-limit.login.max-requests:20}") int loginMax,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindow,
            @Value("${app.rate-limit.signup.max-requests:10}") int signupMax,
            @Value("${app.rate-limit.signup.window-seconds:3600}") long signupWindow,
            @Value("${app.rate-limit.refresh.max-requests:30}") int refreshMax,
            @Value("${app.rate-limit.refresh.window-seconds:60}") long refreshWindow) {
        this.loginRateLimiter = loginRateLimiter;
        this.authMetrics = authMetrics;
        this.pathLimits = Map.of(
            "/api/auth/login",   new PathLimit(loginMax, loginWindow),
            "/api/auth/signup",  new PathLimit(signupMax, signupWindow),
            "/api/auth/refresh", new PathLimit(refreshMax, refreshWindow)
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        PathLimit limit = pathLimits.get(path);
        if (limit != null) {
            String clientIp = resolveClientIp(request);
            String clientKey = clientIp + ":" + path;
            if (loginRateLimiter.isRateLimited(clientKey, limit.maxRequests(), limit.windowSeconds())) {
                log.warn("Rate limit exceeded: ip={}, path={}", clientIp, path);
                authMetrics.incrementLoginFailure("rate_limited");
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(RATE_LIMIT_RESPONSE);
                response.getWriter().flush();
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
