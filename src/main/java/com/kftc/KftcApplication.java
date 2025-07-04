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
			log.info("🚀 KFTC 오픈뱅킹 센터 시작 완료!");
			log.info("========================================");
			log.info("📍 로컬 서버: http://localhost:8080");
			log.info("📚 Swagger UI: http://localhost:8080/swagger-ui.html");
			log.info("📱 문자인증 테스트: http://localhost:8080/phone-verification");
			log.info("🔐 OAuth 테스트: http://localhost:8080/oauth/test/client");
			log.info("💳 카드사용자 등록: http://localhost:8080/card-user-register");
			log.info("🏦 오픈뱅킹 등록: http://localhost:8080/openbanking-register");
			log.info("❤️ Health Check: http://localhost:8080/health");
			log.info("========================================");
			log.info("🎯 주요 API 엔드포인트:");
			log.info("   • GET  /api/v2.0/user/me - 사용자정보조회");
			log.info("   • GET  /api/v2.0/account/list - 계좌목록조회");
			log.info("   • POST /oauth/2.0/token - 토큰발급");
			log.info("   • POST /api/v2.0/transfer/withdraw/{fintech_use_num} - 출금이체");
			log.info("========================================");
			log.info("🔗 금융기관 서버 연동:");
			log.info("   • 신한은행: http://localhost:8082");
			log.info("   • 국민카드: http://localhost:8083");
			log.info("   • 삼성화재: http://localhost:8084");
			log.info("   • 현대캐피탈: http://localhost:8085");
			log.info("========================================");
			log.info("✨ 멀티 기관 통합 서비스 활성화됨!");
			log.info("========================================");
		};
	}
}
