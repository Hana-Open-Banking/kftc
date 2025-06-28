package com.kftc.oauth.config;

import com.kftc.oauth.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Authorization 헤더에서 토큰 추출
            String token = extractTokenFromRequest(request);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 토큰에서 사용자 정보 추출
                String userId = jwtTokenProvider.getUserId(token);
                String clientId = jwtTokenProvider.getClientId(token);
                String scope = jwtTokenProvider.getScope(token);
                
                // SecurityContext에 인증 정보 설정
                Authentication authentication = createAuthentication(userId, clientId, scope, token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                log.debug("JWT 인증 성공: userId={}, clientId={}, scope={}", userId, clientId, scope);
            }
            
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * HTTP 요청에서 Bearer 토큰 추출
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        return null;
    }
    
    /**
     * 인증 객체 생성
     */
    private Authentication createAuthentication(String userId, String clientId, String scope, String token) {
        // 권한 설정 (scope 기반)
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_USER")
        );
        
        // 사용자 정보를 담은 Principal 생성
        JwtAuthenticatedUser principal = new JwtAuthenticatedUser(userId, clientId, scope, token);
        
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }
    
    /**
     * 인증된 사용자 정보를 담는 클래스
     */
    public static class JwtAuthenticatedUser {
        private final String userId;
        private final String clientId;
        private final String scope;
        private final String accessToken;
        
        public JwtAuthenticatedUser(String userId, String clientId, String scope, String accessToken) {
            this.userId = userId;
            this.clientId = clientId;
            this.scope = scope;
            this.accessToken = accessToken;
        }
        
        public String getUserId() { return userId; }
        public String getClientId() { return clientId; }
        public String getScope() { return scope; }
        public String getAccessToken() { return accessToken; }
        
        @Override
        public String toString() {
            return "JwtAuthenticatedUser{" +
                    "userId='" + userId + '\'' +
                    ", clientId='" + clientId + '\'' +
                    ", scope='" + scope + '\'' +
                    '}';
        }
    }
} 