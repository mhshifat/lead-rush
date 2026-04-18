package com.leadrush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeadRushApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadRushApplication.class, args);
    }
}
