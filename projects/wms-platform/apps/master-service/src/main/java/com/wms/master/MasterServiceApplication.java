package com.wms.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MasterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterServiceApplication.class, args);
    }
}
