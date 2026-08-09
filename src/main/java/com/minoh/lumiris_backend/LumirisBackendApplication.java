package com.minoh.lumiris_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LumirisBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LumirisBackendApplication.class, args);
	}

}
