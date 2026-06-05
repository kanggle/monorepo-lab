package com.example.security.infrastructure.config;

import com.example.security.domain.detection.DetectionThresholds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * External configuration for detection rules.
 *
 * <p>Properties live under {@code security.detection.*} in {@code application.yml}.
 * No hard-coded thresholds in rule code per TASK-BE-032 spec. Validation is
 * enforced at application startup so a misconfiguration fails fast rather than
 * silently skewing detection behaviour.</p>
 */
@Validated
@ConfigurationProperties(prefix = "security.detection")
public class DetectionProperties {

    @Valid @NotNull private Velocity velocity = new Velocity();
    @Valid @NotNull private Geo geo = new Geo();
    @Valid @NotNull private Device device = new Device();
    @Valid @NotNull private AutoLock autoLock = new AutoLock();
    @Valid @NotNull private GeoIp geoip = new GeoIp();
    @Valid @NotNull private ImpossibleTravel impossibleTravel = new ImpossibleTravel();
    @Valid @NotNull private IpReputation ipReputation = new IpReputation();

    public DetectionThresholds toThresholds() {
        return new DetectionThresholds(
                velocity.threshold,
                velocity.windowSeconds,
                velocity.scoreWeight,
                geo.speedKmPerHour,
                geo.minScore,
                geo.scoreSlope,
                device.score,
                device.alertOnNew,
                impossibleTravel.timeWindowSeconds,
                impossibleTravel.score,
                ipReputation.score
        );
    }

    public Velocity getVelocity() { return velocity; }
    public void setVelocity(Velocity velocity) { this.velocity = velocity; }
    public Geo getGeo() { return geo; }
    public void setGeo(Geo geo) { this.geo = geo; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public AutoLock getAutoLock() { return autoLock; }
    public void setAutoLock(AutoLock autoLock) { this.autoLock = autoLock; }
    public GeoIp getGeoip() { return geoip; }
    public void setGeoip(GeoIp geoip) { this.geoip = geoip; }
    public ImpossibleTravel getImpossibleTravel() { return impossibleTravel; }
    public void setImpossibleTravel(ImpossibleTravel impossibleTravel) { this.impossibleTravel = impossibleTravel; }
    public IpReputation getIpReputation() { return ipReputation; }
    public void setIpReputation(IpReputation ipReputation) { this.ipReputation = ipReputation; }

    public static class Velocity {
        @Min(1) @Max(10_000)
        private int threshold = 10;
        @Min(1) @Max(86_400)
        private int windowSeconds = 3600;
        @Min(1) @Max(100)
        private int scoreWeight = 80;
        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
        public int getScoreWeight() { return scoreWeight; }
        public void setScoreWeight(int scoreWeight) { this.scoreWeight = scoreWeight; }
    }

    public static class Geo {
        @Min(1) @Max(10_000)
        private int speedKmPerHour = 900;
        @Min(0) @Max(100)
        private int minScore = 85;
        @Min(0) @Max(100)
        private int scoreSlope = 15;
        public int getSpeedKmPerHour() { return speedKmPerHour; }
        public void setSpeedKmPerHour(int speedKmPerHour) { this.speedKmPerHour = speedKmPerHour; }
        public int getMinScore() { return minScore; }
        public void setMinScore(int minScore) { this.minScore = minScore; }
        public int getScoreSlope() { return scoreSlope; }
        public void setScoreSlope(int scoreSlope) { this.scoreSlope = scoreSlope; }
    }

    public static class Device {
        @Min(0) @Max(100)
        private int score = 50;
        private boolean alertOnNew = true;
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public boolean isAlertOnNew() { return alertOnNew; }
        public void setAlertOnNew(boolean alertOnNew) { this.alertOnNew = alertOnNew; }
    }

    public static class AutoLock {
        @NotBlank
        private String accountServiceBaseUrl = "http://localhost:8081";
        @Min(1) @Max(10)
        private int maxAttempts = 3;
        @Min(1) @Max(60_000)
        private long initialBackoffMs = 200L;
        @Min(100) @Max(60_000)
        private int connectTimeoutMs = 3000;
        @Min(100) @Max(120_000)
        private int readTimeoutMs = 10000;
        public String getAccountServiceBaseUrl() { return accountServiceBaseUrl; }
        public void setAccountServiceBaseUrl(String accountServiceBaseUrl) { this.accountServiceBaseUrl = accountServiceBaseUrl; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public void setInitialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    public static class GeoIp {
        @NotNull
        private String dbPath = "";
        public String getDbPath() { return dbPath; }
        public void setDbPath(String dbPath) { this.dbPath = dbPath; }
    }

    public static class ImpossibleTravel {
        @Min(1) @Max(86_400)
        private int timeWindowSeconds = 3600;
        @Min(0) @Max(100)
        private int score = 70;
        public int getTimeWindowSeconds() { return timeWindowSeconds; }
        public void setTimeWindowSeconds(int timeWindowSeconds) { this.timeWindowSeconds = timeWindowSeconds; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
    }

    public static class IpReputation {
        @Min(0) @Max(100)
        private int score = 60;
        @NotNull
        private java.util.List<String> suspiciousCidrs = new java.util.ArrayList<>();
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public java.util.List<String> getSuspiciousCidrs() { return suspiciousCidrs; }
        public void setSuspiciousCidrs(java.util.List<String> suspiciousCidrs) { this.suspiciousCidrs = suspiciousCidrs; }
    }
}
