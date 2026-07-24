package com.example.polybets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PolymarketConsensusApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolymarketConsensusApplication.class, args);
    }
}
