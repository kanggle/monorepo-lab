package com.example.security.domain.detection;

import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.repository.LoginHistoryRepository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ImpossibleTravelRule — fires when consecutive successful logins occur from
 * different countries within a configurable time window (default 1 hour).
 *
 * <p>Unlike {@link GeoAnomalyRule} which uses GeoIP coordinate-based speed
 * calculation, this rule performs a lightweight country-code comparison using
 * data already available in {@code login_history}. The two rules are
 * complementary: GeoAnomalyRule requires MaxMind GeoIP DB, while this rule
 * works with the {@code geoCountry} field from the auth event payload.</p>
 *
 * <p>Score is fixed at {@link DetectionThresholds#impossibleTravelScore()}
 * (default 70 → ALERT only, does not trigger AUTO_LOCK alone).</p>
 */
public class ImpossibleTravelRule implements SuspiciousActivityRule {

    public static final String CODE = "IMPOSSIBLE_TRAVEL";

    private final LoginHistoryRepository loginHistoryRepository;
    private final DetectionThresholds thresholds;

    public ImpossibleTravelRule(LoginHistoryRepository loginHistoryRepository,
                                 DetectionThresholds thresholds) {
        this.loginHistoryRepository = loginHistoryRepository;
        this.thresholds = thresholds;
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.isLoginSucceeded() || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        String currentCountry = ctx.geoCountry();
        if (currentCountry == null || currentCountry.isBlank()) {
            return DetectionResult.NONE;
        }

        Optional<LoginHistoryEntry> maybePrevious =
                loginHistoryRepository.findLatestSuccessByAccountId(ctx.accountId());
        if (maybePrevious.isEmpty()) {
            return DetectionResult.NONE;
        }

        LoginHistoryEntry previous = maybePrevious.get();
        String previousCountry = previous.getGeoCountry();
        if (previousCountry == null || previousCountry.isBlank()) {
            return DetectionResult.NONE;
        }

        // Same country — no impossible travel
        if (currentCountry.equalsIgnoreCase(previousCountry)) {
            return DetectionResult.NONE;
        }

        // Different country — check time window
        Duration timeDelta = Duration.between(previous.getOccurredAt(), ctx.occurredAt());
        long deltaSeconds = Math.abs(timeDelta.getSeconds());
        if (deltaSeconds >= thresholds.impossibleTravelWindowSeconds()) {
            return DetectionResult.NONE;
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", "Login from different country within impossible travel window");
        evidence.put("previousCountry", previousCountry);
        evidence.put("currentCountry", currentCountry);
        evidence.put("timeDeltaSeconds", deltaSeconds);
        evidence.put("windowThresholdSeconds", thresholds.impossibleTravelWindowSeconds());
        return new DetectionResult(CODE, thresholds.impossibleTravelScore(), evidence);
    }
}
