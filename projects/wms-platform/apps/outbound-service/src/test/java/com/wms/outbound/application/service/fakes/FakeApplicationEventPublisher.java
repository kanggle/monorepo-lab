package com.wms.outbound.application.service.fakes;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Records published events so tests can assert post-commit triggering.
 */
public class FakeApplicationEventPublisher implements ApplicationEventPublisher {

    public final List<Object> published = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        published.add(event);
    }
}
