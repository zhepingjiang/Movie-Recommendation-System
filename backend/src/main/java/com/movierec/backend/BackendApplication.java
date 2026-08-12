package com.movierec.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the movie recommendation backend Spring Boot application.
 */
@SpringBootApplication
public class BackendApplication {

	/**
	 * Boots the Spring application context.
	 *
	 * @param args command-line arguments passed through to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
