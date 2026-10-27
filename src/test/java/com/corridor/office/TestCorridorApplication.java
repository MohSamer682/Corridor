package com.corridor.office;

import org.springframework.boot.SpringApplication;

public class TestCorridorApplication {

    public static void main(String[] args) {
        SpringApplication.from(CorridorApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
