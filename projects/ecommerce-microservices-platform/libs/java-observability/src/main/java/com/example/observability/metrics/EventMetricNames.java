package com.example.observability.metrics;

public final class EventMetricNames {

    private EventMetricNames() {
    }

    public static final String EVENT_PUBLISH_FAILURE_TOTAL = "event_publish_failure_total";
    public static final String EVENT_CONSUME_FAILURE_TOTAL = "event_consume_failure_total";
    public static final String TAG_EVENT_TYPE = "event_type";
    public static final String TAG_SERVICE = "service";
}
