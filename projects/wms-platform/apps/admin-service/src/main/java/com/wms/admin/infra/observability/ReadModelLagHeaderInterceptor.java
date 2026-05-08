package com.wms.admin.infra.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sets the {@code X-Read-Model-Lag-Seconds} response header on dashboard /
 * operations endpoints when the worst observed projection lag exceeds the
 * configured threshold (5 s by default, per
 * {@code admin-service-api.md § Headers}).
 *
 * <p>Read-only — the value is sourced from the
 * {@code admin.projection.lag.seconds} timer's max across {@code topic} tags
 * registered by {@link ProjectionMetrics}. If the metric has not been
 * registered (e.g., startup before any event consumed, or {@code standalone}
 * profile with no consumers), the header is silently omitted.
 */
public class ReadModelLagHeaderInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Read-Model-Lag-Seconds";
    static final double DEFAULT_THRESHOLD_SECONDS = 5.0d;

    private final MeterRegistry meterRegistry;
    private final double thresholdSeconds;

    public ReadModelLagHeaderInterceptor(MeterRegistry meterRegistry) {
        this(meterRegistry, DEFAULT_THRESHOLD_SECONDS);
    }

    public ReadModelLagHeaderInterceptor(MeterRegistry meterRegistry, double thresholdSeconds) {
        this.meterRegistry = meterRegistry;
        this.thresholdSeconds = thresholdSeconds;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, org.springframework.web.servlet.ModelAndView mav) {
        if (response.isCommitted() || response.containsHeader(HEADER)) {
            return;
        }
        double worstSeconds = worstLagSeconds();
        if (worstSeconds > thresholdSeconds) {
            response.setHeader(HEADER, formatSeconds(worstSeconds));
        }
    }

    private double worstLagSeconds() {
        Search search = meterRegistry.find(ProjectionMetrics.LAG_METRIC);
        double worst = -1.0d;
        for (Timer timer : search.timers()) {
            double max = timer.max(TimeUnit.SECONDS);
            if (Double.isFinite(max) && max > worst) {
                worst = max;
            }
        }
        return worst;
    }

    private static String formatSeconds(double seconds) {
        // single-decimal precision matches the spec example ("4.8") and keeps
        // the header concise.
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }
}
