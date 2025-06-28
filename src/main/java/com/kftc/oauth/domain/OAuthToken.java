package com.kftc.oauth.domain;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthToken extends DateTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "access_token", unique = true, nullable = false, length = 1000)
    private String accessToken;
    
    @Column(name = "refresh_token", unique = true, nullable = false, length = 1000)
    private String refreshToken;
    
    @Column(name = "client_id", nullable = false)
    private String clientId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "scope")
    private String scope;
    
    @Column(name = "access_token_expires_at", nullable = false)
    private LocalDateTime accessTokenExpiresAt;
    
    @Column(name = "refresh_token_expires_at", nullable = false)
    private LocalDateTime refreshTokenExpiresAt;
    
    @Column(name = "is_revoked")
    private Boolean isRevoked;
    
    @Builder
    public OAuthToken(String accessToken, String refreshToken, String clientId, String userId,
                     String scope, LocalDateTime accessTokenExpiresAt, LocalDateTime refreshTokenExpiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.clientId = clientId;
        this.userId = userId;
        this.scope = scope;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.isRevoked = false;
    }
    
    public void revoke() {
        this.isRevoked = true;
    }
    
    public boolean isAccessTokenExpired() {
        return LocalDateTime.now().isAfter(accessTokenExpiresAt);
    }
    
    public boolean isRefreshTokenExpired() {
        return LocalDateTime.now().isAfter(refreshTokenExpiresAt);
    }
} 