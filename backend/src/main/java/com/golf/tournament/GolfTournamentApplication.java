package com.golf.tournament;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GolfTournamentApplication {

    public static void main(String[] args) {
        SpringApplication.run(GolfTournamentApplication.class, args);
    }
}
