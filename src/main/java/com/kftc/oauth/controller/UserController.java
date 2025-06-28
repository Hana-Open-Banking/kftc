package com.kftc.oauth.controller;

import com.kftc.oauth.config.CurrentUser;
import com.kftc.oauth.config.JwtAuthenticationFilter;
import com.kftc.oauth.dto.UserInfoResponse;
import com.kftc.oauth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    
    private final UserService userService;
    
    @Operation(summary = "사용자정보조회 API", 
               description = "사용자의 기본 정보를 조회하는 API입니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/user/me")
    public ResponseEntity<UserInfoResponse> getUserInfo(
            @CurrentUser JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser,
            @Parameter(description = "사용자일련번호", required = true) 
            @RequestParam("user_seq_no") String userSeqNo) {
        
        log.info("인증된 사용자 정보: userId={}, clientId={}, scope={}", 
                authenticatedUser.getUserId(), authenticatedUser.getClientId(), authenticatedUser.getScope());
        
        // 사용자 정보 조회
        UserInfoResponse userInfo = userService.getUserInfo(authenticatedUser.getAccessToken(), userSeqNo);
        
        return ResponseEntity.ok(userInfo);
    }
} 