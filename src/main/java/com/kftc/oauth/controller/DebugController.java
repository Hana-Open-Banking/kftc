package com.kftc.oauth.controller;

import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.repository.OAuthClientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Tag(name = "디버그 API", description = "개발 및 디버깅용 API")
public class DebugController {
    
    private final OAuthClientRepository clientRepository;
    
    @Value("${oauth.client.redirect-uri}")
    private String configuredRedirectUri;
    
    @Value("${oauth.client.client-id}")
    private String configuredClientId;
    
    @Operation(summary = "OAuth 클라이언트 정보 조회", 
               description = "현재 DB에 저장된 OAuth 클라이언트 정보를 확인합니다. (개발용)")
    @GetMapping("/oauth-clients")
    public ResponseEntity<Map<String, Object>> getOAuthClients() {
        List<OAuthClient> clients = clientRepository.findAll();
        
        Map<String, Object> result = new HashMap<>();
        result.put("total_count", clients.size());
        result.put("clients", clients.stream().map(client -> {
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("id", client.getId());
            clientInfo.put("client_id", client.getClientId());
            clientInfo.put("client_name", client.getClientName());
            clientInfo.put("redirect_uri", client.getRedirectUri());
            clientInfo.put("scope", client.getScope());
            clientInfo.put("is_active", client.getIsActive());
            clientInfo.put("client_use_code", client.getClientUseCode());
            clientInfo.put("created_at", client.getCreatedAt());
            clientInfo.put("modified_at", client.getModifiedAt());
            return clientInfo;
        }).toList());
        
        log.info("OAuth 클라이언트 조회 완료: 총 {}개", clients.size());
        return ResponseEntity.ok(result);
    }
    
    @Operation(summary = "OAuth 클라이언트 강제 재초기화", 
               description = "OAuth 클라이언트를 강제로 재초기화합니다. (개발용)")
    @GetMapping("/reset-oauth-clients")
    public ResponseEntity<Map<String, Object>> resetOAuthClients() {
        // 모든 클라이언트 삭제
        long beforeCount = clientRepository.count();
        clientRepository.deleteAll();
        clientRepository.flush();
        long afterCount = clientRepository.count();
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "OAuth 클라이언트 삭제 완료");
        result.put("before_count", beforeCount);
        result.put("after_count", afterCount);
        result.put("note", "애플리케이션을 재시작하면 새로운 클라이언트가 자동 생성됩니다.");
        
        log.info("OAuth 클라이언트 강제 삭제 완료: {}개 → {}개", beforeCount, afterCount);
        return ResponseEntity.ok(result);
    }
    
    @Operation(summary = "현재 설정값 확인", 
               description = "현재 애플리케이션에 로드된 실제 설정값을 확인합니다.")
    @GetMapping("/config-values")
    public ResponseEntity<Map<String, Object>> getConfigValues() {
        Map<String, Object> result = new HashMap<>();
        result.put("configured_client_id", configuredClientId);
        result.put("configured_redirect_uri", configuredRedirectUri);
        result.put("timestamp", System.currentTimeMillis());
        
        log.info("현재 설정값 확인: clientId={}, redirectUri={}", configuredClientId, configuredRedirectUri);
        return ResponseEntity.ok(result);
    }
} 