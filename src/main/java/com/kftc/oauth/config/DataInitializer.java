package com.kftc.oauth.config;

import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.repository.OAuthClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final OAuthClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${oauth.client.client-id}")
    private String clientId;
    
    @Value("${oauth.client.client-secret}")
    private String clientSecret;
    
    @Value("${oauth.client.redirect-uri}")
    private String redirectUri;
    
    @Value("${oauth.client.scope}")
    private String scope;
    
    @Value("${oauth.client.client-use-code}")
    private String clientUseCode;
    
    @Override
    public void run(String... args) throws Exception {
        initializeOAuthClients();
        // initializeTestUsers(); // 초기 테스트 사용자 생성 비활성화
        printTestUrls();
    }
    
    @Transactional
    private void initializeOAuthClients() {
        log.info("=== OAuth 클라이언트 초기화 시작 ===");
        log.info("설정값 확인: clientId={}, redirectUri=[{}]", clientId, redirectUri);
        
        // 모든 기존 클라이언트 완전 삭제 (확실한 초기화)
        long totalClients = clientRepository.count();
        log.info("DB에 저장된 전체 클라이언트 수: {}", totalClients);
        
        if (totalClients > 0) {
            log.info("모든 기존 클라이언트 삭제 중...");
            clientRepository.deleteAll();
            clientRepository.flush();
            log.info("모든 기존 클라이언트 삭제 완료");
        }
        
        // 새로운 클라이언트 생성
        OAuthClient newClient = OAuthClient.builder()
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName("금융결제원 오픈뱅킹 테스트 클라이언트")
                .redirectUri(redirectUri.trim()) // 공백 제거
                .scope(scope)
                .isActive(true)
                .clientUseCode(clientUseCode)
                .build();
        
        OAuthClient savedClient = clientRepository.save(newClient);
        clientRepository.flush(); // 강제로 DB에 반영
        
        log.info("새로운 OAuth 클라이언트 생성 완료:");
        log.info("  - id: {}", savedClient.getId());
        log.info("  - clientId: {}", savedClient.getClientId());
        log.info("  - redirectUri: [{}]", savedClient.getRedirectUri());
        log.info("  - scope: {}", savedClient.getScope());
        log.info("=== OAuth 클라이언트 초기화 완료 ===");
    }
    

    
    private void printTestUrls() {
        log.info("");
        log.info("=".repeat(80));
        log.info("🚀 KFTC 오픈뱅킹 서버가 성공적으로 시작되었습니다!");
        log.info("=".repeat(80));
        log.info("");
        
        // 서버 기본 정보
        log.info("📌 서버 정보:");
        log.info("   - 서버 URL: http://34.47.102.221:8080");
        log.info("   - 환경: 개발/테스트");
        log.info("   - OAuth Client ID: {}", clientId);
        log.info("");
        
        // Swagger UI
        log.info("📚 API 문서 (Swagger):");
        log.info("   - Swagger UI: http://34.47.102.221:8080/swagger-ui/index.html");
        log.info("");
        
        // OAuth 테스트 URL들

        log.info("");
        log.info("   직접 OAuth 인증:");
        log.info("      http://34.47.102.221:8080/oauth/2.0/authorize?response_type=code&client_id={}&redirect_uri={}&scope=login|inquiry&state=test123",
                 clientId, java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8));
        log.info("");
    }
} 