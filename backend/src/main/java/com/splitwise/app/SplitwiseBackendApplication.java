package com.splitwise.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SplitwiseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SplitwiseBackendApplication.class, args);
    }
}
