package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.user.dto.KftcTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class KftcInternalService {
    
    @Value("${kftc.base-url}")
    private String kftcBaseUrl;
    
    @Value("${kftc.client-id}")
    private String clientId;
    
    @Value("${kftc.client-secret}")
    private String clientSecret;
    
    @Value("${kftc.redirect-uri}")
    private String redirectUri;
    
    private final WebClient webClient;
    
    public KftcInternalService() {
        this.webClient = WebClient.builder().build();
    }
    
    /**
     * KFTC 오픈뱅킹 인증 URL 생성
     */
    public String generateAuthUrl(String userId) {
        return UriComponentsBuilder.fromUriString(kftcBaseUrl)
                .path("/oauth2.0/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "login inquiry transfer")
                .queryParam("state", userId) // 사용자 ID를 state로 전달
                .queryParam("auth_type", "0") // 0: 최초인증
                .build()
                .toUriString();
    }
    
    /**
     * Authorization Code로 Access Token 발급
     */
    public KftcTokenResponse getAccessToken(String authorizationCode) {
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("code", authorizationCode);
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("redirect_uri", redirectUri);
            formData.add("grant_type", "authorization_code");
            
            KftcTokenResponse response = webClient.post()
                    .uri(kftcBaseUrl + "/oauth/2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(KftcTokenResponse.class)
                    .block();
            
            if (response == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "KFTC 토큰 발급 실패");
            }
            
            log.info("KFTC 토큰 발급 성공: userSeqNo={}", response.getUserSeqNo());
            return response;
        } catch (Exception e) {
            log.error("KFTC 토큰 발급 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "KFTC 토큰 발급 실패: " + e.getMessage());
        }
    }
    
    /**
     * Access Token 유효성 검증
     */
    public boolean validateAccessToken(String accessToken) {
        try {
            Boolean isValid = webClient.post()
                    .uri(kftcBaseUrl + "/oauth2.0/introspect")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            
            return Boolean.TRUE.equals(isValid);
        } catch (Exception e) {
            log.error("KFTC 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }
} 