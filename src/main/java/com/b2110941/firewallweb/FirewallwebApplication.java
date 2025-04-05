package com.b2110941.firewallweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FirewallwebApplication {
	public static void main(String[] args) {
		SpringApplication.run(FirewallwebApplication.class, args);
	}

}
