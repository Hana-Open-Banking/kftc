package com.kftc.oauth.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
public class AccountConsentController {

    @GetMapping("/account-consent.html")
    public ResponseEntity<String> getAccountConsentPage(
            @RequestParam(required = false) String userSeqNo,
            @RequestParam(required = false) String userCi,
            @RequestParam(required = false) String sessionId) throws IOException {
        
        log.info("🌐 ============= 계좌 동의 화면 요청됨 =============");
        log.info("🌐 파라미터: userSeqNo={}, userCi={}..., sessionId={}", 
            userSeqNo, userCi != null ? userCi.substring(0, 10) : "null", sessionId);
        
        try {
            ClassPathResource resource = new ClassPathResource("static/account-consent.html");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            log.info("🌐 account-consent.html 파일 로드 성공, 크기: {} bytes", content.length());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
        } catch (IOException e) {
            log.error("🌐 account-consent.html 파일 로드 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
} 