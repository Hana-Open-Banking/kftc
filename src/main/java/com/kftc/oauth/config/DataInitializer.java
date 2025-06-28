package com.kftc.oauth.config;

import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.domain.User;
import com.kftc.oauth.repository.OAuthClientRepository;
import com.kftc.oauth.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final OAuthClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserManagementService userManagementService;
    
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
        initializeTestUsers();
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
    
    private void initializeTestUsers() {
        try {
            // 테스트용 사용자 1 - 김철수 (오픈뱅킹 명세서 예시)
            String testUserCi1 = "s1V7bwE4pxqV_K5oy4EdEOGUHUIpv7_2l4kE8l7FOC4HCi-7TUtT9-jaVL9kEj4GB12eKIkfmL49OCtGwI12-C";
            if (!userManagementService.findActiveUserByCi(testUserCi1).isPresent()) {
                User testUser1 = userManagementService.registerUser(
                        testUserCi1,
                        User.UserType.PERSONAL,
                        "김철수",
                        "test@openbanking.or.kr",
                        "19880101",
                        "01012345678",
                        User.UserSexType.M
                );
                log.info("테스트 사용자 1 생성: userSeqNum={}, userName={}", testUser1.getUserSeqNum(), testUser1.getUserName());
            }
            
            // 테스트용 사용자 2 - 홍길동
            String testUserCi2 = "test_ci_hong_gildong_12345678901234567890123456789012345678901234567890";
            if (!userManagementService.findActiveUserByCi(testUserCi2).isPresent()) {
                User testUser2 = userManagementService.registerUser(
                        testUserCi2,
                        User.UserType.PERSONAL,
                        "홍길동",
                        "hong@example.com",
                        "19900315",
                        "01087654321",
                        User.UserSexType.M
                );
                log.info("테스트 사용자 2 생성: userSeqNum={}, userName={}", testUser2.getUserSeqNum(), testUser2.getUserName());
            }
            
            // 테스트용 법인 사용자
            String testUserCi3 = "corp_ci_test_company_12345678901234567890123456789012345678901234567890";
            if (!userManagementService.findActiveUserByCi(testUserCi3).isPresent()) {
                User testUser3 = userManagementService.registerUser(
                        testUserCi3,
                        User.UserType.CORPORATE,
                        "테스트회사",
                        "admin@testcorp.com",
                        "20000101",
                        "0212345678",
                        null
                );
                log.info("테스트 법인 사용자 생성: userSeqNum={}, userName={}", testUser3.getUserSeqNum(), testUser3.getUserName());
            }
            
        } catch (Exception e) {
            log.warn("테스트 사용자 초기화 중 오류 발생: {}", e.getMessage());
        }
    }
} 