package com.bajar.saman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SamanApplication {

	public static void main(String[] args) {
		SpringApplication.run(SamanApplication.class, args);
	}

}
