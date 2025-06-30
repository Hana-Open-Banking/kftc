package com.kftc.oauth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtTokenProvider {
    
    private final SecretKey key;
    
    @Value("${oauth.token.access-token-validity}")
    private long accessTokenValidityInSeconds;
    
    @Value("${oauth.token.refresh-token-validity}")
    private long refreshTokenValidityInSeconds;
    
    public JwtTokenProvider(@Value("${oauth.token.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateAccessToken(String clientId, String userId, String scope) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidityInSeconds * 1000);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("client_id", clientId);
        if (userId != null) {
            claims.put("user_id", userId);
        }
        claims.put("scope", scope);
        claims.put("token_type", "access_token");
        
        return Jwts.builder()
                .claims(claims)
                .subject(userId != null ? userId : clientId) // Client Credentials에서는 subject를 clientId로 사용
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }
    
    public String generateRefreshToken(String clientId, String userId) {
        if (userId == null) {
            return null; // Client Credentials에서는 refresh token 없음
        }
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenValidityInSeconds * 1000);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("client_id", clientId);
        claims.put("user_id", userId);
        claims.put("token_type", "refresh_token");
        
        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }
    
    public Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.error("JWT 토큰 파싱 실패: {}", e.getMessage());
            throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
        }
    }
    
    public String getClientId(String token) {
        Claims claims = extractClaims(token);
        return claims.get("client_id", String.class);
    }
    
    public String getUserId(String token) {
        return extractClaims(token).getSubject();
    }
    
    public String getScope(String token) {
        Claims claims = extractClaims(token);
        return claims.get("scope", String.class);
    }
    
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
    
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("JWT 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }
} 