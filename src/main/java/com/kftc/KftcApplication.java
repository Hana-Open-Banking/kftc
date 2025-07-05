package com.kftc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableFeignClients
@Slf4j
public class KftcApplication {

	public static void main(String[] args) {
		SpringApplication.run(KftcApplication.class, args);
	}

	@Bean
	CommandLineRunner init() {
		return args -> {
			log.info("========================================");
			log.info("📱 휴대폰 인증 링크:");
			log.info("http://localhost:8080/oauth/2.0/authorize?response_type=code&client_id=kftc-openbanking-client&redirect_uri=http%3A%2F%2F34.47.102.221%3A8080%2Foauth%2F2.0%2Fcallback&scope=login%7Cinquiry&state=test123");
			log.info("========================================");
		};
	}
}
