package com.pms.leaderboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WsdemoApplication {
	public static void main(String[] args) {
		SpringApplication.run(WsdemoApplication.class, args);
	}

}
