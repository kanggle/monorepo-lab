package com.wms.inbound.application.port.out;

import com.wms.inbound.domain.event.InboundDomainEvent;

public interface InboundEventPort {

    void publish(InboundDomainEvent event);
}
