package com.apps.deen_sa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PersonalAiApplication {

	public static void main(String[] args) {
		String port = System.getenv("PORT");
		if (port == null) {
			port = "8080"; // fallback for local dev
		}
		System.getProperties().put("server.port", port);
		SpringApplication.run(PersonalAiApplication.class, args);
	}
}
