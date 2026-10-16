package com.devmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the Spring application and component scanning from the com.devmate package. */
@SpringBootApplication
public class DevMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevMateApplication.class, args);
    }
}