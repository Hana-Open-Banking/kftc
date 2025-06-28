package com.kftc.oauth.config;

import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.repository.OAuthClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
    }
    
    private void initializeOAuthClients() {
        // 기존 클라이언트 완전 삭제
        clientRepository.deleteByClientId(clientId);
        clientRepository.flush(); // 강제로 DB에 반영
        
        // 잠시 대기 후 새로 생성
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        OAuthClient client = OAuthClient.builder()
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName("금융결제원 오픈뱅킹 테스트 클라이언트")
                .redirectUri(redirectUri.trim()) // 공백 제거
                .scope(scope)
                .isActive(true)
                .clientUseCode(clientUseCode)
                .build();
        
        clientRepository.save(client);
        clientRepository.flush(); // 강제로 DB에 반영
        log.info("OAuth 클라이언트가 완전히 새로 생성되었습니다: clientId={}, redirectUri=[{}]", clientId, redirectUri.trim());
    }
} 