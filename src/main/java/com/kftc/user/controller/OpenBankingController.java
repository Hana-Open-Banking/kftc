package com.kftc.user.controller;

import com.kftc.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "오픈뱅킹", description = "오픈뱅킹 API")
@RestController
@RequestMapping("/api/openbanking")
@RequiredArgsConstructor
@Slf4j
public class OpenBankingController {
    
    private final UserService userService;
    
    /**
     * 원카에서 CI를 받아 사용자 등록 및 동의 페이지로 리다이렉트
     * 플로우: 원카 → 금결원 (CI 전달)
     */
    @Operation(summary = "원카에서 CI 전달받아 사용자 등록", description = "정보이용기관(원카)에서 본인인증 완료된 CI를 받아 사용자를 등록하고 동의 페이지로 리다이렉트합니다.")
    @PostMapping("/register-from-wonka")
    public ResponseEntity<Void> registerFromWonka(
            @RequestParam("ci") String ci,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("scope") String scope,
            @RequestParam("state") String state) {
        
        log.info("원카에서 CI 전달받음: ci={}, clientId={}", ci.substring(0, 10) + "...", clientId);
        
        // 1. CI로 사용자 생성 또는 조회
        String userSeqNo = userService.createOrGetUserByCi(ci);
        
        // 2. 동의 페이지로 리다이렉트 (사용자 정보 포함) - URL 안전하게 생성
        String consentUrl = org.springframework.web.util.UriComponentsBuilder.fromPath("/oauth/2.0/consent")
                .queryParam("user_seq_no", userSeqNo)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .toUriString();
        
        log.info("동의 페이지로 리다이렉트: userSeqNo={}, consentUrl={}", userSeqNo, consentUrl);
        
        return ResponseEntity.status(302)
                .location(URI.create(consentUrl))
                .build();
    }
} 