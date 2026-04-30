package com.example.security.domain.detection;

/**
 * Tunable detection parameters. Injected via Spring
 * {@code @ConfigurationProperties} in the infrastructure layer but the type
 * itself is domain-pure (no framework imports) so rule implementations can
 * depend on it directly.
 *
 * <p>See {@code application.yml} property {@code security.detection.*}.</p>
 */
public final class DetectionThresholds {

    private final int velocityThreshold;       // failures in window before VelocityRule fires
    private final int velocityWindowSeconds;   // rolling window length
    private final int velocityScoreWeight;     // score multiplier at threshold (spec UC-10: 80)
    private final int geoSpeedKmPerHour;       // physically-impossible travel speed (default 900)
    private final int geoMinScore;             // floor for GeoAnomalyRule when it fires
    private final int geoScoreSlope;           // score increment per 1.0x over speed threshold
    private final int deviceChangeScore;       // fixed score for DeviceChangeRule
    private final boolean deviceAlertOnNew;    // emit alert on newly-seen device
    private final int impossibleTravelWindowSeconds; // time window for country change detection
    private final int impossibleTravelScore;         // fixed score for ImpossibleTravelRule
    private final int ipReputationScore;             // fixed score for IpReputationRule

    public DetectionThresholds(int velocityThreshold,
                               int velocityWindowSeconds,
                               int velocityScoreWeight,
                               int geoSpeedKmPerHour,
                               int geoMinScore,
                               int geoScoreSlope,
                               int deviceChangeScore,
                               boolean deviceAlertOnNew) {
        this(velocityThreshold, velocityWindowSeconds, velocityScoreWeight,
                geoSpeedKmPerHour, geoMinScore, geoScoreSlope,
                deviceChangeScore, deviceAlertOnNew,
                3600, 70, 60);
    }

    public DetectionThresholds(int velocityThreshold,
                               int velocityWindowSeconds,
                               int velocityScoreWeight,
                               int geoSpeedKmPerHour,
                               int geoMinScore,
                               int geoScoreSlope,
                               int deviceChangeScore,
                               boolean deviceAlertOnNew,
                               int impossibleTravelWindowSeconds,
                               int impossibleTravelScore,
                               int ipReputationScore) {
        if (velocityThreshold <= 0) {
            throw new IllegalArgumentException("velocityThreshold must be > 0");
        }
        if (velocityWindowSeconds <= 0) {
            throw new IllegalArgumentException("velocityWindowSeconds must be > 0");
        }
        if (velocityScoreWeight <= 0 || velocityScoreWeight > 100) {
            throw new IllegalArgumentException("velocityScoreWeight must be in (0,100]");
        }
        if (geoSpeedKmPerHour <= 0) {
            throw new IllegalArgumentException("geoSpeedKmPerHour must be > 0");
        }
        if (geoMinScore < 0 || geoMinScore > 100) {
            throw new IllegalArgumentException("geoMinScore must be in [0,100]");
        }
        if (geoScoreSlope < 0 || geoScoreSlope > 100) {
            throw new IllegalArgumentException("geoScoreSlope must be in [0,100]");
        }
        if (deviceChangeScore < 0 || deviceChangeScore > 100) {
            throw new IllegalArgumentException("deviceChangeScore must be in [0,100]");
        }
        if (impossibleTravelWindowSeconds <= 0) {
            throw new IllegalArgumentException("impossibleTravelWindowSeconds must be > 0");
        }
        if (impossibleTravelScore < 0 || impossibleTravelScore > 100) {
            throw new IllegalArgumentException("impossibleTravelScore must be in [0,100]");
        }
        if (ipReputationScore < 0 || ipReputationScore > 100) {
            throw new IllegalArgumentException("ipReputationScore must be in [0,100]");
        }
        this.velocityThreshold = velocityThreshold;
        this.velocityWindowSeconds = velocityWindowSeconds;
        this.velocityScoreWeight = velocityScoreWeight;
        this.geoSpeedKmPerHour = geoSpeedKmPerHour;
        this.geoMinScore = geoMinScore;
        this.geoScoreSlope = geoScoreSlope;
        this.deviceChangeScore = deviceChangeScore;
        this.deviceAlertOnNew = deviceAlertOnNew;
        this.impossibleTravelWindowSeconds = impossibleTravelWindowSeconds;
        this.impossibleTravelScore = impossibleTravelScore;
        this.ipReputationScore = ipReputationScore;
    }

    public int velocityThreshold() { return velocityThreshold; }
    public int velocityWindowSeconds() { return velocityWindowSeconds; }
    public int velocityScoreWeight() { return velocityScoreWeight; }
    public int geoSpeedKmPerHour() { return geoSpeedKmPerHour; }
    public int geoMinScore() { return geoMinScore; }
    public int geoScoreSlope() { return geoScoreSlope; }
    public int deviceChangeScore() { return deviceChangeScore; }
    public boolean deviceAlertOnNew() { return deviceAlertOnNew; }
    public int impossibleTravelWindowSeconds() { return impossibleTravelWindowSeconds; }
    public int impossibleTravelScore() { return impossibleTravelScore; }
    public int ipReputationScore() { return ipReputationScore; }

    public static DetectionThresholds defaults() {
        return new DetectionThresholds(10, 3600, 80, 900, 85, 15, 50, true, 3600, 70, 60);
    }
}
