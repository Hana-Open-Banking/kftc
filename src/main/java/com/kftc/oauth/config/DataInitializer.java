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
        log.info("   - API Docs: http://34.47.102.221:8080/v3/api-docs");
        log.info("");
        
        // OAuth 테스트 URL들
        log.info("🔐 OAuth 2.0 테스트:");
        log.info("   1. 테스트 클라이언트 (권장):");
        log.info("      http://34.47.102.221:8080/oauth/test/client");
        log.info("");
        log.info("   2. 직접 OAuth 인증:");
        log.info("      http://34.47.102.221:8080/oauth/pass?response_type=code&client_id={}&redirect_uri={}&scope=login|inquiry&state=test123",
                 clientId, java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8));
        log.info("");
        log.info("   3. 토큰 발급 (cURL):");
        log.info("      curl -X POST http://34.47.102.221:8080/oauth/token \\");
        log.info("        -d \"grant_type=authorization_code\" \\");
        log.info("        -d \"code=[받은_코드]\" \\");
        log.info("        -d \"client_id={}\" \\", clientId);
        log.info("        -d \"client_secret={}\" \\", clientSecret);
        log.info("        -d \"redirect_uri={}\"", redirectUri);
        log.info("");
        
        // 클라이언트 관리
        log.info("🏢 클라이언트 관리:");
        log.info("   - 클라이언트 등록: http://34.47.102.221:8080/oauth/register/client");
        log.info("   - 등록된 클라이언트 조회: http://34.47.102.221:8080/debug/oauth-clients");
        log.info("");
        
        // 오픈뱅킹 API 테스트
        log.info("🏦 오픈뱅킹 API 테스트:");
        log.info("   - 사용자 토큰발급: POST /oauth/token");
        log.info("   - 토큰 검증: POST /oauth/introspect");
        log.info("   - 사용자 정보: GET /v2.0/user/me");
        log.info("   - 계좌 목록: GET /v2.0/account/list");
        log.info("");
        
        // 카드 API 테스트  
        log.info("💳 카드 API 테스트:");
        log.info("   - 카드사 사용자 등록: POST /v1.0/card/user/register");
        log.info("   - 카드 정보 조회: GET /v1.0/card/info");
        log.info("");
        
        // 추가 유틸리티
        log.info("🛠️ 유틸리티:");
        log.info("   - 헬스 체크: GET /health");
        log.info("   - 휴대폰 인증: POST /v1.0/phone/verify");
        log.info("   - 디버그 정보: GET /debug/info");
        log.info("");
        
        // 테스트 시나리오
        log.info("🧪 OAuth 테스트 시나리오:");
        log.info("   📱 간편 테스트 (권장):");
        log.info("     1. http://34.47.102.221:8080/oauth/test/client 접속");
        log.info("     2. '오픈뱅킹 로그인 시작' 버튼 클릭");
        log.info("     3. 휴대폰번호 입력 (아무 번호나 가능)");
        log.info("     4. 인증번호 '123456' 입력");
        log.info("     5. '동의하고 계속' 버튼 클릭");
        log.info("     6. 자동으로 토큰 발급까지 완료!");
        log.info("");
        log.info("   🏢 클라이언트 등록 테스트:");
        log.info("     1. http://34.47.102.221:8080/oauth/register/client 접속");
        log.info("     2. 서비스 정보 입력 (테스트용)");
        log.info("     3. 등록 신청 (승인 대기 상태로 생성)");
        log.info("     4. 로그에서 발급된 Client ID/Secret 확인");
        log.info("");
        
        log.info("=".repeat(80));
        log.info("✨ 테스트를 시작해보세요!");
        log.info("=".repeat(80));
        log.info("");
    }
} 