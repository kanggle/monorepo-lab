package com.wms.inbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InboundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InboundServiceApplication.class, args);
    }
}
