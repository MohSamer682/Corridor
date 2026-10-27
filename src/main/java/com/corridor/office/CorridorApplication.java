package com.corridor.office;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CorridorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorridorApplication.class, args);
    }
}
