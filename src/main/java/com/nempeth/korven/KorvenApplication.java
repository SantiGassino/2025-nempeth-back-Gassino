package com.nempeth.korven;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KorvenApplication {

	public static void main(String[] args) {
		SpringApplication.run(KorvenApplication.class, args);
	}

}
