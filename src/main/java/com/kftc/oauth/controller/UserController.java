package com.kftc.oauth.controller;

import com.kftc.oauth.dto.UserInfoResponse;
import com.kftc.oauth.service.OAuthUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v2.0")
@RequiredArgsConstructor
@Tag(name = "사용자 정보 API", description = "오픈뱅킹 사용자 정보 조회 API")
public class UserController {
    
    private final OAuthUserService oAuthUserService;
    
    @Operation(summary = "사용자정보조회 API", description = "사용자의 기본 정보를 조회하는 API입니다.")
    @PostMapping("/user/me")
    public ResponseEntity<UserInfoResponse> getUserInfo(
            @Parameter(description = "Bearer 토큰", required = true) 
            @RequestHeader("Authorization") String authorization,
            
            @Parameter(description = "사용자일련번호", required = true) 
            @RequestParam("user_seq_no") String userSeqNo) {
        
        // Authorization 헤더 검증
        if (!authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 Authorization 헤더입니다.");
        }
        
        String accessToken = authorization.substring(7);
        
        // 사용자 정보 조회
        UserInfoResponse userInfo = oAuthUserService.getUserInfo(accessToken, userSeqNo);
        
        return ResponseEntity.ok(userInfo);
    }
} 