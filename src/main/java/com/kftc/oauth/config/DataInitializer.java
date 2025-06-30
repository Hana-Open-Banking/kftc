package com.kftc.oauth.config;

import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.repository.OAuthClientRepository;
import com.kftc.user.entity.User;
import com.kftc.user.repository.UserRepository;
import com.kftc.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final OAuthClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService; // 통합된 UserService 사용
    private final UserRepository userRepository; // 테스트 사용자 직접 생성용
    
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
            if (!userRepository.existsByUserCi(testUserCi1)) {
                createTestUser(testUserCi1, "김철수", "test@openbanking.or.kr", "19880101", "PERSONAL");
            }
            
            // 테스트용 사용자 2 - 홍길동
            String testUserCi2 = "test_ci_hong_gildong_12345678901234567890123456789012345678901234567890";
            if (!userRepository.existsByUserCi(testUserCi2)) {
                createTestUser(testUserCi2, "홍길동", "hong@example.com", "19900315", "PERSONAL");
            }
            
            // 테스트용 법인 사용자
            String testUserCi3 = "corp_ci_test_company_12345678901234567890123456789012345678901234567890";
            if (!userRepository.existsByUserCi(testUserCi3)) {
                createTestUser(testUserCi3, "테스트회사", "admin@testcorp.com", "20000101", "CORPORATE");
            }
            
        } catch (Exception e) {
            log.warn("테스트 사용자 초기화 중 오류 발생: {}", e.getMessage());
        }
    }
    
    private void createTestUser(String userCi, String userName, String userEmail, 
                               String userInfo, String userType) {
        try {
            String userSeqNo = generateTestUserSeqNo();
            
            User testUser = User.builder()
                    .userSeqNo(userSeqNo)
                    .userCi(userCi)
                    .userName(userName)
                    .userEmail(userEmail)
                    .userInfo(userInfo)
                    .userStatus("ACTIVE")
                    .userType(userType)
                    .build();
            
            // UserRepository를 통해 직접 저장 (초기화용)
            User savedUser = userRepository.save(testUser);
            log.info("테스트 사용자 생성 완료: userSeqNo={}, userName={}", 
                    savedUser.getUserSeqNo(), savedUser.getUserName());
            
        } catch (Exception e) {
            log.error("테스트 사용자 생성 실패: name={}, error={}", userName, e.getMessage());
        }
    }
    
    private String generateTestUserSeqNo() {
        // 테스트용 일련번호 생성 (실제로는 UserService에서 처리)
        return "1000000" + String.format("%03d", (int)(Math.random() * 900) + 100);
    }
} 