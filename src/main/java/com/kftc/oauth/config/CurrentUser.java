package com.kftc.oauth.config;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 인증된 사용자 정보를 자동으로 주입받는 어노테이션
 * 
 * 사용 예시:
 * @PostMapping("/api")
 * public ResponseEntity<?> myApi(@CurrentUser JwtAuthenticatedUser user) {
 *     String userId = user.getUserId();
 *     String accessToken = user.getAccessToken();
 *     // ...
 * }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {
} 